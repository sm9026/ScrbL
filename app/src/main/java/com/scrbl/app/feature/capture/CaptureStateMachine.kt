package com.scrbl.app.feature.capture

import com.scrbl.app.core.result.AppResult
import com.scrbl.app.domain.model.Recall
import com.scrbl.app.domain.repository.VoiceFileStorage
import com.scrbl.app.domain.usecase.CancelCaptureUseCase
import com.scrbl.app.domain.usecase.CreateTextRecallUseCase
import com.scrbl.app.domain.usecase.FinalizeVoiceRecallUseCase
import com.scrbl.app.domain.validation.RecallValidator
import com.scrbl.app.platform.audio.VoiceRecorderController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Explicit states for the Capture flow (`05_INTERACTIONS_AND_GESTURES.md` section 3 & `diagrams/capture-state.mmd`):
 * CLOSED -> BLANK_PAGE -> TEXT_EDITING / VOICE_RECORDING -> FINALIZING / COMMITTING -> SAVED / DISCARDED
 */
enum class CaptureStage {
    CLOSED,
    BLANK_PAGE,
    TEXT_EDITING,
    VOICE_PERMISSION_EXPLANATION,
    VOICE_RECORDING,
    FINALIZING_VOICE,
    COMMITTING,
    SAVED
}

data class CaptureUiState(
    val stage: CaptureStage = CaptureStage.CLOSED,
    val textDraft: String = "",
    val wordCount: Int = 0,
    val isAtWordLimit: Boolean = false,
    val voiceTitleDraft: String = "",
    val elapsedVoiceMillis: Long = 0L,
    val isPermissionPermanentlyDenied: Boolean = false,
    val statusMessage: String? = null,
    val lastCommittedRecall: Recall? = null
) {
    val isVisible: Boolean
        get() = stage != CaptureStage.CLOSED

    val canCommitText: Boolean
        get() = stage == CaptureStage.TEXT_EDITING &&
            wordCount in 1..RecallValidator.MAX_TEXT_WORDS &&
            textDraft.trim().isNotEmpty()
}

/**
 * State machine managing Text and Voice capture with strict adherence to `02_REQUIREMENTS_AND_RULES.md`:
 * - Both keyboard and microphone options are always shown on `BLANK_PAGE` (no default mode).
 * - Text recall is capped at 25 words without silent truncation.
 * - Voice recall is capped at 37,000 ms, auto-stopping and committing exactly once via atomic CAS
 *   (`finalizingCas`) so a manual stop tap and the 37s callback never create duplicate records.
 * - Flicking the floating page away, pressing Back, audio focus loss, or app backgrounding
 *   discards any uncommitted text or partial recording without leaving a draft.
 */
class CaptureStateMachine(
    private val scope: CoroutineScope,
    private val createTextRecall: CreateTextRecallUseCase,
    private val finalizeVoiceRecall: FinalizeVoiceRecallUseCase,
    private val cancelCapture: CancelCaptureUseCase,
    private val voiceFileStorage: VoiceFileStorage,
    private val recorderController: VoiceRecorderController
) {
    private val _uiState = MutableStateFlow(CaptureUiState())
    val uiState: StateFlow<CaptureUiState> = _uiState.asStateFlow()

    private val finalizingCas = AtomicBoolean(false)
    private var activeTempFile: File? = null

    fun openBlankCapturePage() {
        discardActiveSessionInternal()
        finalizingCas.set(false)
        _uiState.value = CaptureUiState(stage = CaptureStage.BLANK_PAGE)
    }

    fun selectTextMode() {
        val current = _uiState.value.stage
        if (current != CaptureStage.BLANK_PAGE && current != CaptureStage.VOICE_PERMISSION_EXPLANATION) {
            return
        }
        _uiState.value = CaptureUiState(
            stage = CaptureStage.TEXT_EDITING,
            textDraft = "",
            wordCount = 0,
            isAtWordLimit = false
        )
    }

    /**
     * Updates text composition while enforcing the 25-word limit without silent truncation.
     */
    fun onTextChanged(candidateText: String) {
        val current = _uiState.value
        if (current.stage != CaptureStage.TEXT_EDITING) return

        if (RecallValidator.canAcceptTextEdit(current.textDraft, candidateText)) {
            val words = RecallValidator.countWords(candidateText)
            _uiState.update {
                it.copy(
                    textDraft = candidateText,
                    wordCount = words,
                    isAtWordLimit = words >= RecallValidator.MAX_TEXT_WORDS,
                    statusMessage = null
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    isAtWordLimit = true,
                    statusMessage = "25-word maximum reached"
                )
            }
        }
    }

    /**
     * Commits a Text Recall immediately when the top-right doodle tick is tapped.
     */
    fun commitTextRecall(onCommitted: (Recall) -> Unit = {}) {
        val current = _uiState.value
        if (current.stage != CaptureStage.TEXT_EDITING) return
        if (!finalizingCas.compareAndSet(false, true)) return

        _uiState.update { it.copy(stage = CaptureStage.COMMITTING, statusMessage = null) }

        scope.launch {
            when (val result = createTextRecall(current.textDraft)) {
                is AppResult.Success -> {
                    _uiState.value = CaptureUiState(
                        stage = CaptureStage.CLOSED,
                        lastCommittedRecall = result.data
                    )
                    finalizingCas.set(false)
                    onCommitted(result.data)
                }
                is AppResult.Error -> {
                    finalizingCas.set(false)
                    _uiState.update {
                        it.copy(
                            stage = CaptureStage.TEXT_EDITING,
                            statusMessage = result.message
                        )
                    }
                }
            }
        }
    }

    /**
     * Called when `RECORD_AUDIO` permission is denied. Shows a clear explanation and allows
     * the user to return to the blank page or choose text mode explicitly (never silently switches to text).
     */
    fun onMicrophonePermissionDenied(permanentlyDenied: Boolean) {
        _uiState.update {
            it.copy(
                stage = CaptureStage.VOICE_PERMISSION_EXPLANATION,
                isPermissionPermanentlyDenied = permanentlyDenied,
                statusMessage = if (permanentlyDenied) {
                    "Microphone permission is disabled in Android Settings. Enable it there to record voice thoughts, or choose the keyboard button."
                } else {
                    "Microphone permission is required to record a voice recall."
                }
            )
        }
    }

    /**
     * Starts voice recording immediately once microphone is selected and permission is granted.
     */
    fun startVoiceRecordingSession() {
        val stage = _uiState.value.stage
        if (stage != CaptureStage.BLANK_PAGE && stage != CaptureStage.VOICE_PERMISSION_EXPLANATION) {
            return
        }
        finalizingCas.set(false)
        val sessionId = UUID.randomUUID().toString()
        val tempFile = voiceFileStorage.createTempRecordingFile(sessionId)
        activeTempFile = tempFile

        _uiState.value = CaptureUiState(
            stage = CaptureStage.VOICE_RECORDING,
            voiceTitleDraft = "",
            elapsedVoiceMillis = 0L
        )

        val started = recorderController.startRecording(
            outputFile = tempFile,
            onProgressMillis = { elapsed ->
                if (_uiState.value.stage == CaptureStage.VOICE_RECORDING) {
                    _uiState.update { it.copy(elapsedVoiceMillis = elapsed) }
                }
            },
            onMaxDurationReached = { maxDuration ->
                finalizeAndCommitVoiceInternal(maxDuration)
            },
            onRecordingInterruptedOrFailed = { _ ->
                discardCapture()
            }
        )

        if (!started) {
            discardCapture()
        }
    }

    fun onVoiceTitleChanged(candidateTitle: String) {
        if (_uiState.value.stage != CaptureStage.VOICE_RECORDING) return
        if (candidateTitle.length <= RecallValidator.MAX_VOICE_TITLE_CHARS) {
            _uiState.update { it.copy(voiceTitleDraft = candidateTitle) }
        }
    }

    /**
     * Stops and commits the active voice recording immediately when the circular stop control is tapped.
     */
    fun stopAndCommitVoiceRecall(onCommitted: (Recall) -> Unit = {}) {
        finalizeAndCommitVoiceInternal(explicitDurationOverride = null, onCommitted = onCommitted)
    }

    /**
     * Atomic compare-and-set transition (`VOICE_RECORDING` -> `FINALIZING_VOICE`) so a stop tap
     * and the 37-second callback racing against each other commit the voice recall at most once.
     */
    private fun finalizeAndCommitVoiceInternal(
        explicitDurationOverride: Long? = null,
        onCommitted: (Recall) -> Unit = {}
    ) {
        if (_uiState.value.stage != CaptureStage.VOICE_RECORDING) return
        if (!finalizingCas.compareAndSet(false, true)) return

        val titleSnapshot = _uiState.value.voiceTitleDraft
        val tempFile = activeTempFile
        activeTempFile = null

        _uiState.update { it.copy(stage = CaptureStage.FINALIZING_VOICE) }

        val measuredDuration = recorderController.stopAndFinalize() ?: explicitDurationOverride
        if (tempFile == null || measuredDuration == null || measuredDuration <= 0L) {
            scope.launch {
                cancelCapture(tempFile)
                finalizingCas.set(false)
                _uiState.value = CaptureUiState(stage = CaptureStage.CLOSED)
            }
            return
        }

        _uiState.update { it.copy(stage = CaptureStage.COMMITTING) }

        scope.launch {
            val finalDuration = explicitDurationOverride
                ?.coerceAtMost(RecallValidator.MAX_VOICE_DURATION_MS)
                ?: measuredDuration.coerceAtMost(RecallValidator.MAX_VOICE_DURATION_MS)

            when (val result = finalizeVoiceRecall(
                tempAudioFile = tempFile,
                durationMillis = finalDuration,
                optionalVoiceTitle = titleSnapshot
            )) {
                is AppResult.Success -> {
                    finalizingCas.set(false)
                    _uiState.value = CaptureUiState(
                        stage = CaptureStage.CLOSED,
                        lastCommittedRecall = result.data
                    )
                    onCommitted(result.data)
                }
                is AppResult.Error -> {
                    cancelCapture(tempFile)
                    finalizingCas.set(false)
                    _uiState.value = CaptureUiState(stage = CaptureStage.CLOSED)
                }
            }
        }
    }

    /**
     * Flicking the floating capture page away, pressing Back, or losing foreground/audio focus
     * discards any uncommitted draft or temporary audio immediately.
     */
    fun discardCapture() {
        discardActiveSessionInternal()
        finalizingCas.set(false)
        _uiState.value = CaptureUiState(stage = CaptureStage.CLOSED)
    }

    fun onAppBackgrounded() {
        if (_uiState.value.stage == CaptureStage.VOICE_RECORDING ||
            _uiState.value.stage == CaptureStage.FINALIZING_VOICE
        ) {
            discardCapture()
        }
    }

    private fun discardActiveSessionInternal() {
        recorderController.discardAndRelease()
        val temp = activeTempFile
        activeTempFile = null
        if (temp != null) {
            scope.launch {
                cancelCapture(temp)
            }
        }
    }
}
