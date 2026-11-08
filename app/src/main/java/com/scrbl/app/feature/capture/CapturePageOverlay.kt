package com.scrbl.app.feature.capture

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.scrbl.app.R
import com.scrbl.app.core.design.DoodleAssets
import com.scrbl.app.core.design.DoodleIconButton
import com.scrbl.app.core.design.LocalScrbLTheme
import com.scrbl.app.core.design.LocalUserPreferences
import com.scrbl.app.core.design.PaperSheet
import com.scrbl.app.core.design.ScrbLTypography
import com.scrbl.app.core.design.VoiceCharacter
import com.scrbl.app.core.time.RecallDateTimeFormatter
import com.scrbl.app.domain.validation.RecallValidator
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Floating Capture Page (`04_UX_SCREEN_SPEC.md` sections C, D, E & `05_INTERACTIONS_AND_GESTURES.md`):
 * - Opens as a floating paper page above the current screen.
 * - Blank page always presents BOTH circular doodle buttons: Keyboard and Microphone (no default mode).
 * - Flicking/dragging the floating page past the dismissal threshold cancels and discards the capture.
 * - System Back also discards the uncommitted session cleanly.
 * - Strictly NO visible X or Cancel button on the page.
 * - Text mode: opens keyboard automatically, resizes above IME (`imePadding()`), centres text from the
 *   first character, displays unobtrusive `12 / 25` word counter, and commits immediately via the top-right doodle tick.
 * - Voice mode: shrinks/repositions for the selected doodle character, records immediately up to 37s,
 *   provides an optional thought-bubble title input before stop, and commits immediately on tapping the stop circle.
 */
@Composable
fun CapturePageOverlay(
    uiState: CaptureUiState,
    onSelectTextMode: () -> Unit,
    onSelectVoiceModeRequestPermission: () -> Unit,
    onOpenAppSettingsForPermission: () -> Unit,
    onTextChanged: (String) -> Unit,
    onCommitText: () -> Unit,
    onVoiceTitleChanged: (String) -> Unit,
    onStopAndCommitVoice: () -> Unit,
    onFlickOrBackDiscard: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!uiState.isVisible) return

    val prefs = LocalUserPreferences.current
    val scope = rememberCoroutineScope()
    val offsetX = remember(uiState.stage) { Animatable(0f) }
    val offsetY = remember(uiState.stage) { Animatable(0f) }

    // System Back during capture behaves identically to flicking the floating page away
    BackHandler(enabled = uiState.isVisible) {
        onFlickOrBackDiscard()
    }

    val enterTransition = if (prefs.animationsEnabled) {
        fadeIn(tween(180)) + scaleIn(tween(180), initialScale = 0.92f)
    } else {
        fadeIn(tween(0))
    }
    val exitTransition = if (prefs.animationsEnabled) {
        fadeOut(tween(150)) + scaleOut(tween(150), targetScale = 0.85f)
    } else {
        fadeOut(tween(0))
    }

    AnimatedVisibility(
        visible = uiState.isVisible,
        enter = enterTransition,
        exit = exitTransition,
        modifier = modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            contentAlignment = Alignment.Center
        ) {
            val cardMaxHeight = when (uiState.stage) {
                CaptureStage.VOICE_RECORDING,
                CaptureStage.FINALIZING_VOICE -> 360.dp
                CaptureStage.TEXT_EDITING -> 340.dp
                else -> 320.dp
            }

            PaperSheet(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 380.dp)
                    .heightIn(min = 240.dp, max = cardMaxHeight)
                    .offset {
                        IntOffset(
                            x = offsetX.value.roundToInt(),
                            y = offsetY.value.roundToInt()
                        )
                    }
                    .pointerInput(uiState.stage) {
                        var totalDragX = 0f
                        var totalDragY = 0f
                        detectDragGestures(
                            onDragStart = {
                                totalDragX = 0f
                                totalDragY = 0f
                            },
                            onDragCancel = {
                                scope.launch {
                                    offsetX.animateTo(0f, tween(150))
                                    offsetY.animateTo(0f, tween(150))
                                }
                            },
                            onDragEnd = {
                                val distance = hypot(totalDragX, totalDragY)
                                if (distance > 135f || abs(totalDragY) > 110f || abs(totalDragX) > 110f) {
                                    onFlickOrBackDiscard()
                                } else {
                                    scope.launch {
                                        offsetX.animateTo(0f, tween(160))
                                        offsetY.animateTo(0f, tween(160))
                                    }
                                }
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                totalDragX += dragAmount.x
                                totalDragY += dragAmount.y
                                scope.launch {
                                    offsetX.snapTo(totalDragX)
                                    offsetY.snapTo(totalDragY)
                                }
                            }
                        )
                    }
                    .semantics {
                        contentDescription = "Floating capture paper page. Flick away to cancel."
                    },
                isAddressedTone = false,
                cornerRadius = 14.dp,
                showFoldedCorner = true,
                contentPadding = 20.dp
            ) {
                when (uiState.stage) {
                    CaptureStage.BLANK_PAGE -> BlankCaptureModePicker(
                        onSelectTextMode = onSelectTextMode,
                        onSelectVoiceMode = onSelectVoiceModeRequestPermission
                    )
                    CaptureStage.VOICE_PERMISSION_EXPLANATION -> PermissionExplanationContent(
                        message = uiState.statusMessage.orEmpty(),
                        isPermanentlyDenied = uiState.isPermissionPermanentlyDenied,
                        onOpenSettings = onOpenAppSettingsForPermission,
                        onSwitchToText = onSelectTextMode
                    )
                    CaptureStage.TEXT_EDITING,
                    CaptureStage.COMMITTING -> TextCaptureContent(
                        uiState = uiState,
                        onTextChanged = onTextChanged,
                        onCommitText = onCommitText
                    )
                    CaptureStage.VOICE_RECORDING,
                    CaptureStage.FINALIZING_VOICE -> VoiceCaptureContent(
                        uiState = uiState,
                        onVoiceTitleChanged = onVoiceTitleChanged,
                        onStopAndCommitVoice = onStopAndCommitVoice
                    )
                    CaptureStage.CLOSED,
                    CaptureStage.SAVED -> Unit
                }
            }
        }
    }
}

@Composable
private fun BlankCaptureModePicker(
    onSelectTextMode: () -> Unit,
    onSelectVoiceMode: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(36.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DoodleIconButton(
                iconRes = DoodleAssets.Keyboard,
                contentDescription = stringResource(id = R.string.cd_keyboard_mode),
                onClick = onSelectTextMode,
                visualDiameter = 62.dp,
                glyphSize = 30.dp
            )
            DoodleIconButton(
                iconRes = DoodleAssets.Microphone,
                contentDescription = stringResource(id = R.string.cd_microphone_mode),
                onClick = onSelectVoiceMode,
                visualDiameter = 62.dp,
                glyphSize = 30.dp
            )
        }
    }
}

@Composable
private fun TextCaptureContent(
    uiState: CaptureUiState,
    onTextChanged: (String) -> Unit,
    onCommitText: () -> Unit
) {
    val theme = LocalScrbLTheme.current
    val prefs = LocalUserPreferences.current
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top-right small doodle tick commits immediately
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            DoodleIconButton(
                iconRes = DoodleAssets.CheckTick,
                contentDescription = stringResource(id = R.string.cd_save_tick),
                onClick = {
                    keyboardController?.hide()
                    onCommitText()
                },
                enabled = uiState.canCommitText,
                visualDiameter = 40.dp,
                glyphSize = 20.dp,
                isAccentFilled = uiState.canCommitText
            )
        }

        // Centre-aligned text field from the very first character
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            val bodyStyle = ScrbLTypography.recallBodyTextStyle(
                familyOption = prefs.fontFamily,
                scaleOption = prefs.fontScale,
                color = theme.inkNavy
            )
            BasicTextField(
                value = uiState.textDraft,
                onValueChange = onTextChanged,
                textStyle = bodyStyle,
                cursorBrush = SolidColor(theme.inkNavy),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .semantics {
                        contentDescription = "Text recall input, centre-aligned, maximum 25 words"
                    },
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (uiState.textDraft.isEmpty()) {
                            Text(
                                text = "…",
                                style = bodyStyle.copy(color = theme.addressedTone.copy(alpha = 0.55f)),
                                textAlign = TextAlign.Center
                            )
                        }
                        innerTextField()
                    }
                }
            )
        }

        // Unobtrusive bottom word counter (e.g. 12 / 25)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = uiState.statusMessage.orEmpty(),
                style = ScrbLTypography.metadataTextStyle(
                    familyOption = prefs.fontFamily,
                    scaleOption = prefs.fontScale,
                    color = theme.terracotta
                )
            )
            Text(
                text = "${uiState.wordCount} / ${RecallValidator.MAX_TEXT_WORDS}",
                style = ScrbLTypography.metadataTextStyle(
                    familyOption = prefs.fontFamily,
                    scaleOption = prefs.fontScale,
                    color = if (uiState.isAtWordLimit) theme.terracotta else theme.addressedTone
                )
            )
        }
    }
}

@Composable
private fun VoiceCaptureContent(
    uiState: CaptureUiState,
    onVoiceTitleChanged: (String) -> Unit,
    onStopAndCommitVoice: () -> Unit
) {
    val theme = LocalScrbLTheme.current
    val prefs = LocalUserPreferences.current
    var showTitleBubbleInput by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Optional comic-style thought bubble above the character for pre-commit voice title
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            if (showTitleBubbleInput || uiState.voiceTitleDraft.isNotEmpty()) {
                BasicTextField(
                    value = uiState.voiceTitleDraft,
                    onValueChange = onVoiceTitleChanged,
                    singleLine = true,
                    textStyle = ScrbLTypography.metadataTextStyle(
                        familyOption = prefs.fontFamily,
                        scaleOption = prefs.fontScale,
                        color = theme.inkNavy
                    ).copy(textAlign = TextAlign.Center),
                    cursorBrush = SolidColor(theme.inkNavy),
                    modifier = Modifier
                        .widthIn(min = 120.dp, max = 230.dp)
                        .background(theme.paper, RoundedCornerShape(16.dp))
                        .border(1.4.dp, theme.inkNavy, RoundedCornerShape(16.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .semantics {
                            contentDescription = "Optional voice recall title bubble"
                        },
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.Center) {
                            if (uiState.voiceTitleDraft.isEmpty()) {
                                Text(
                                    text = "optional title…",
                                    style = ScrbLTypography.metadataTextStyle(
                                        familyOption = prefs.fontFamily,
                                        scaleOption = prefs.fontScale,
                                        color = theme.addressedTone.copy(alpha = 0.6f)
                                    )
                                )
                            }
                            inner()
                        }
                    }
                )
            } else {
                DoodleIconButton(
                    iconRes = DoodleAssets.Keyboard,
                    contentDescription = "Add optional voice title bubble",
                    onClick = { showTitleBubbleInput = true },
                    visualDiameter = 34.dp,
                    glyphSize = 17.dp
                )
            }
        }

        // Selected voice doodle character in centre
        VoiceCharacter(
            characterOption = prefs.voiceCharacter,
            voiceTitle = null,
            isPlaying = uiState.stage == CaptureStage.VOICE_RECORDING,
            animationsEnabled = prefs.animationsEnabled,
            inkColor = theme.inkNavy,
            characterSize = 108.dp
        )

        // Elapsed time (0:00 / 0:37) and single circular stop control (tapping commits immediately)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = RecallDateTimeFormatter.formatDurationSeconds(uiState.elapsedVoiceMillis),
                style = ScrbLTypography.metadataTextStyle(
                    familyOption = prefs.fontFamily,
                    scaleOption = prefs.fontScale,
                    color = theme.inkNavy
                )
            )

            DoodleIconButton(
                iconRes = DoodleAssets.StopRecord,
                contentDescription = stringResource(id = R.string.cd_stop_voice),
                onClick = onStopAndCommitVoice,
                enabled = uiState.stage == CaptureStage.VOICE_RECORDING,
                visualDiameter = 48.dp,
                glyphSize = 24.dp,
                preserveOriginalTint = true
            )
        }
    }
}

@Composable
private fun PermissionExplanationContent(
    message: String,
    isPermanentlyDenied: Boolean,
    onOpenSettings: () -> Unit,
    onSwitchToText: () -> Unit
) {
    val theme = LocalScrbLTheme.current
    val prefs = LocalUserPreferences.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = message,
            style = ScrbLTypography.metadataTextStyle(
                familyOption = prefs.fontFamily,
                scaleOption = prefs.fontScale,
                color = theme.inkNavy
            ),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isPermanentlyDenied) {
                DoodleIconButton(
                    iconRes = DoodleAssets.Settings,
                    contentDescription = "Open Android app settings to enable microphone",
                    onClick = onOpenSettings,
                    visualDiameter = 48.dp,
                    glyphSize = 22.dp
                )
            }
            DoodleIconButton(
                iconRes = DoodleAssets.Keyboard,
                contentDescription = "Switch to text capture instead",
                onClick = onSwitchToText,
                visualDiameter = 48.dp,
                glyphSize = 22.dp
            )
        }
    }
}
