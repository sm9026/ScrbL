package com.scrbl.app.platform.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import com.scrbl.app.domain.validation.RecallValidator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Interface for recording voice recalls so unit tests can deterministically test the 37-second
 * race condition, audio-focus loss discard, and foreground-loss discard without hardware microphones.
 */
interface VoiceRecorderController {
    val isRecording: Boolean
    fun startRecording(
        outputFile: File,
        onProgressMillis: (Long) -> Unit,
        onMaxDurationReached: (Long) -> Unit,
        onRecordingInterruptedOrFailed: (String) -> Unit
    ): Boolean

    /**
     * Stops the active recording and returns the measured monotonic duration in milliseconds,
     * or null if no valid recording was active.
     */
    fun stopAndFinalize(): Long?

    /**
     * Stops and discards any active recording immediately, releasing all platform resources.
     */
    fun discardAndRelease()
}

/**
 * Android `MediaRecorder` + `AudioManager` implementation enforcing `05_INTERACTIONS_AND_GESTURES.md`
 * and `10_ANDROID_PLATFORM_PRIVACY.md`:
 * - Enforces the 37,000 ms maximum via BOTH `MediaRecorder.setMaxDuration(37_000)` and a monotonic
 *   `SystemClock.elapsedRealtime()` timer guard.
 * - Requests `AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE` during recording; if focus is lost (e.g. incoming call
 *   or alarm interruption without needing `READ_PHONE_STATE`), immediately discards the unfinished recording.
 * - Releases `MediaRecorder` and abandons audio focus on every terminal path.
 */
class AndroidVoiceRecorderController(
    private val context: Context,
    private val coroutineScope: CoroutineScope
) : VoiceRecorderController {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var mediaRecorder: MediaRecorder? = null
    private var timerJob: Job? = null
    private var startElapsedRealtimeMs: Long = 0L
    private var currentOutputFile: File? = null
    private val activeFlag = AtomicBoolean(false)
    private var interruptionCallback: ((String) -> Unit)? = null

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        if (focusChange == AudioManager.AUDIOFOCUS_LOSS ||
            focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT ||
            focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK
        ) {
            if (activeFlag.get()) {
                val cb = interruptionCallback
                discardAndRelease()
                cb?.invoke("Recording interrupted by another audio source or call; unfinished audio discarded.")
            }
        }
    }

    private val audioFocusRequest: AudioFocusRequest = AudioFocusRequest.Builder(
        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
    )
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        .setOnAudioFocusChangeListener(focusChangeListener)
        .build()

    override val isRecording: Boolean
        get() = activeFlag.get()

    override fun startRecording(
        outputFile: File,
        onProgressMillis: (Long) -> Unit,
        onMaxDurationReached: (Long) -> Unit,
        onRecordingInterruptedOrFailed: (String) -> Unit
    ): Boolean {
        discardAndRelease()

        val focusResult = audioManager.requestAudioFocus(audioFocusRequest)
        if (focusResult != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            onRecordingInterruptedOrFailed("Audio focus unavailable; could not start voice capture.")
            return false
        }

        return try {
            outputFile.parentFile?.mkdirs()
            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setAudioEncodingBitRate(96_000)
            recorder.setAudioSamplingRate(44_100)
            recorder.setMaxDuration(RecallValidator.MAX_VOICE_DURATION_MS.toInt())
            recorder.setOutputFile(outputFile.absolutePath)

            recorder.setOnInfoListener { _, what, _ ->
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                    onMaxDurationReached(RecallValidator.MAX_VOICE_DURATION_MS)
                }
            }
            recorder.setOnErrorListener { _, _, _ ->
                if (activeFlag.get()) {
                    discardAndRelease()
                    onRecordingInterruptedOrFailed("Recorder error; unfinished audio discarded.")
                }
            }

            recorder.prepare()
            recorder.start()

            mediaRecorder = recorder
            currentOutputFile = outputFile
            interruptionCallback = onRecordingInterruptedOrFailed
            startElapsedRealtimeMs = SystemClock.elapsedRealtime()
            activeFlag.set(true)

            // Monotonic timer guard so wall-clock changes or delayed platform callbacks never exceed 37s
            timerJob = coroutineScope.launch(Dispatchers.Main) {
                while (isActive && activeFlag.get()) {
                    val elapsed = (SystemClock.elapsedRealtime() - startElapsedRealtimeMs)
                        .coerceAtLeast(0L)
                    onProgressMillis(elapsed.coerceAtMost(RecallValidator.MAX_VOICE_DURATION_MS))
                    if (elapsed >= RecallValidator.MAX_VOICE_DURATION_MS) {
                        onMaxDurationReached(RecallValidator.MAX_VOICE_DURATION_MS)
                        break
                    }
                    delay(100L)
                }
            }
            true
        } catch (e: Exception) {
            discardAndRelease()
            onRecordingInterruptedOrFailed(e.message ?: "Unable to start microphone recording.")
            false
        }
    }

    override fun stopAndFinalize(): Long? {
        if (!activeFlag.compareAndSet(true, false)) {
            return null
        }
        timerJob?.cancel()
        timerJob = null
        val measuredDuration = (SystemClock.elapsedRealtime() - startElapsedRealtimeMs)
            .coerceIn(1L, RecallValidator.MAX_VOICE_DURATION_MS)

        val recorder = mediaRecorder
        mediaRecorder = null
        interruptionCallback = null

        return try {
            recorder?.stop()
            measuredDuration
        } catch (_: Exception) {
            // Very short or corrupted recorder stop fails cleanly and deletes partial output
            runCatching { currentOutputFile?.delete() }
            null
        } finally {
            runCatching { recorder?.reset() }
            runCatching { recorder?.release() }
            runCatching { audioManager.abandonAudioFocusRequest(audioFocusRequest) }
        }
    }

    override fun discardAndRelease() {
        activeFlag.set(false)
        timerJob?.cancel()
        timerJob = null
        interruptionCallback = null

        val recorder = mediaRecorder
        mediaRecorder = null
        if (recorder != null) {
            runCatching { recorder.stop() }
            runCatching { recorder.reset() }
            runCatching { recorder.release() }
        }
        runCatching { audioManager.abandonAudioFocusRequest(audioFocusRequest) }
        runCatching { currentOutputFile?.delete() }
        currentOutputFile = null
    }
}

/**
 * Local-only speech audio player for Expanded Recall views (`05_INTERACTIONS_AND_GESTURES.md` section 8).
 * - Uses speech `AudioAttributes` and audio focus.
 * - Never auto-plays when opening a page; only plays when the user taps the doodle character.
 * - Stops and releases player resources when leaving the recall or changing pages.
 * - Playback failure never mutates or deletes the committed recall row.
 */
class AndroidVoicePlayerController(
    context: Context
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var mediaPlayer: MediaPlayer? = null
    private var currentPlayingId: String? = null
    private var onStateChanged: ((Boolean, String?) -> Unit)? = null

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        if (focusChange == AudioManager.AUDIOFOCUS_LOSS ||
            focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
        ) {
            stopAndRelease()
        }
    }

    private val audioFocusRequest: AudioFocusRequest = AudioFocusRequest.Builder(
        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
    )
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        .setOnAudioFocusChangeListener(focusChangeListener)
        .build()

    fun togglePlayback(
        recallId: String,
        audioFile: File,
        onPlaybackStateChanged: (isPlaying: Boolean, errorMessage: String?) -> Unit
    ) {
        if (currentPlayingId == recallId && mediaPlayer?.isPlaying == true) {
            stopAndRelease()
            onPlaybackStateChanged(false, null)
            return
        }

        stopAndRelease()
        onStateChanged = onPlaybackStateChanged

        if (!audioFile.exists() || !audioFile.isFile || audioFile.length() <= 0L) {
            onPlaybackStateChanged(false, "Audio file unavailable on device.")
            return
        }

        val focusGranted = audioManager.requestAudioFocus(audioFocusRequest) ==
            AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (!focusGranted) {
            onPlaybackStateChanged(false, "Audio focus busy.")
            return
        }

        try {
            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setDataSource(audioFile.absolutePath)
                setOnCompletionListener {
                    stopAndRelease()
                }
                setOnErrorListener { _, _, _ ->
                    val cb = onStateChanged
                    stopAndRelease()
                    cb?.invoke(false, "Unable to play recording.")
                    true
                }
                prepare()
                start()
            }
            mediaPlayer = player
            currentPlayingId = recallId
            onPlaybackStateChanged(true, null)
        } catch (_: Exception) {
            stopAndRelease()
            onPlaybackStateChanged(false, "Unable to play recording.")
        }
    }

    fun stopAndRelease() {
        val cb = onStateChanged
        onStateChanged = null
        currentPlayingId = null
        val player = mediaPlayer
        mediaPlayer = null
        if (player != null) {
            runCatching {
                if (player.isPlaying) player.stop()
            }
            runCatching { player.reset() }
            runCatching { player.release() }
        }
        runCatching { audioManager.abandonAudioFocusRequest(audioFocusRequest) }
        cb?.invoke(false, null)
    }
}
