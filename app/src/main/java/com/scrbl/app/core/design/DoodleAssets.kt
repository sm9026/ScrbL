package com.scrbl.app.core.design

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.scrbl.app.R
import com.scrbl.app.domain.model.VoiceCharacterOption

/**
 * Registry of ScrbL's custom hand-drawn doodle vector resources converted directly from
 * SVG files under assets/doodles. Never substitutes generic Material icons.
 */
object DoodleAssets {
    @DrawableRes val HeartOutline = R.drawable.ic_doodle_heart_outline
    @DrawableRes val HeartFilled = R.drawable.ic_doodle_heart_filled
    @DrawableRes val Microphone = R.drawable.ic_doodle_microphone
    @DrawableRes val Keyboard = R.drawable.ic_doodle_keyboard
    @DrawableRes val CheckTick = R.drawable.ic_doodle_check
    @DrawableRes val Plus = R.drawable.ic_doodle_plus
    @DrawableRes val Filter = R.drawable.ic_doodle_filter
    @DrawableRes val Archive = R.drawable.ic_doodle_archive
    @DrawableRes val Settings = R.drawable.ic_doodle_settings
    @DrawableRes val StopRecord = R.drawable.ic_doodle_stop
    @DrawableRes val BackArrow = R.drawable.ic_doodle_back
    @DrawableRes val CharacterBoy = R.drawable.ic_voice_character_boy
    @DrawableRes val CharacterGirl = R.drawable.ic_voice_character_girl
}

/**
 * Circular doodle button enforcing:
 * - Every visible button is circular (`07_VISUAL_DESIGN_SYSTEM.md` section 8).
 * - Minimum interactive hit target of 48dp (`05_INTERACTIONS_AND_GESTURES.md` section 2).
 * - Hand-drawn ink-navy outline and warm paper or terracotta accent fill.
 */
@Composable
fun DoodleIconButton(
    @DrawableRes iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    visualDiameter: Dp = 44.dp,
    glyphSize: Dp = 22.dp,
    isAccentFilled: Boolean = false,
    isSelected: Boolean = false,
    enabled: Boolean = true,
    preserveOriginalTint: Boolean = false
) {
    val theme = LocalScrbLTheme.current
    val backgroundColor = when {
        !enabled -> theme.paper.copy(alpha = 0.55f)
        isAccentFilled -> theme.terracotta
        isSelected -> theme.backSheet2
        else -> theme.paper
    }
    val borderColor = if (enabled) theme.inkNavy else theme.inkNavy.copy(alpha = 0.4f)
    val iconTint = when {
        preserveOriginalTint -> Color.Unspecified
        isAccentFilled -> theme.paper
        !enabled -> theme.inkNavy.copy(alpha = 0.4f)
        else -> theme.inkNavy
    }

    // Outer Box guarantees >= 48dp minimum touch target even when visual circle is smaller
    Box(
        modifier = modifier
            .size(maxOf(48.dp, visualDiameter))
            .semantics {
                this.contentDescription = contentDescription
                this.role = Role.Button
                this.selected = isSelected
            }
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(visualDiameter)
                .shadow(elevation = 2.dp, shape = CircleShape, clip = false)
                .clip(CircleShape)
                .background(backgroundColor, CircleShape)
                .border(width = 1.6.dp, color = borderColor, shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(glyphSize)
            )
        }
    }
}

/**
 * Hand-drawn notebook voice character (Boy or Girl variant) with:
 * - Optional comic-style thought bubble above the character for `voiceTitle`.
 * - 4 distinct hand-drawn playback poses (Resting Pose 0, Speaking Pose 1, Speaking Pose 2,
 *   Musical Note / Resonance Pose 3) cycled during audio playback when animations are enabled,
 *   or a clear static playing indicator when animations are disabled (`04_UX_SCREEN_SPEC.md` section F).
 */
@Composable
fun VoiceCharacter(
    characterOption: VoiceCharacterOption,
    voiceTitle: String?,
    isPlaying: Boolean,
    animationsEnabled: Boolean,
    inkColor: Color,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    characterSize: Dp = 118.dp
) {
    val theme = LocalScrbLTheme.current
    val prefs = LocalUserPreferences.current

    // Cycle through 4 poses (0..3) during active playback
    val poseIndex: Int = if (!isPlaying) {
        0
    } else if (!animationsEnabled) {
        2 // Distinct static "playing" pose when animations are disabled
    } else {
        val transition = rememberInfiniteTransition(label = "voice_character_pose")
        val rawFrame by transition.animateFloat(
            initialValue = 0f,
            targetValue = 4f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "pose_frame"
        )
        rawFrame.toInt().coerceIn(0, 3)
    }

    val stateLabel = if (isPlaying) {
        "Voice recall character (${characterOption.displayLabel}), playing audio. Tap to pause."
    } else {
        "Voice recall character (${characterOption.displayLabel}), idle. Tap to play audio."
    }

    Column(
        modifier = modifier
            .semantics {
                contentDescription = if (!voiceTitle.isNullOrBlank()) {
                    "$stateLabel Title: $voiceTitle"
                } else {
                    stateLabel
                }
                if (onClick != null) role = Role.Button
            }
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick
                    )
                } else {
                    Modifier
                }
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Optional comic-style thought bubble above the character
        if (!voiceTitle.isNullOrBlank()) {
            ThoughtBubbleTitle(
                title = voiceTitle,
                inkColor = inkColor,
                paperColor = theme.paper,
                fontFamily = prefs.fontFamily,
                fontScale = prefs.fontScale
            )
        }

        // Hand-drawn pen character Canvas with 4 poses
        Canvas(
            modifier = Modifier
                .size(maxOf(characterSize, 56.dp))
                .padding(4.dp)
        ) {
            val w = size.width
            val h = size.height
            val strokeMain = Stroke(
                width = w * 0.032f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
            val strokeThin = Stroke(
                width = w * 0.022f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )

            // Subtle head tilt offset per pose
            val tiltX = when (poseIndex) {
                1 -> -w * 0.02f
                3 -> w * 0.02f
                else -> 0f
            }

            // Hair & head outline
            if (characterOption == VoiceCharacterOption.BOY) {
                val hairPath = Path().apply {
                    moveTo(w * 0.30f + tiltX, h * 0.34f)
                    cubicTo(
                        w * 0.28f + tiltX, h * 0.16f,
                        w * 0.44f + tiltX, h * 0.12f,
                        w * 0.50f + tiltX, h * 0.14f
                    )
                    cubicTo(
                        w * 0.62f + tiltX, h * 0.11f,
                        w * 0.72f + tiltX, h * 0.19f,
                        w * 0.70f + tiltX, h * 0.34f
                    )
                    cubicTo(
                        w * 0.62f + tiltX, h * 0.26f,
                        w * 0.54f + tiltX, h * 0.25f,
                        w * 0.48f + tiltX, h * 0.26f
                    )
                    cubicTo(
                        w * 0.40f + tiltX, h * 0.25f,
                        w * 0.34f + tiltX, h * 0.28f,
                        w * 0.30f + tiltX, h * 0.34f
                    )
                }
                drawPath(hairPath, color = inkColor, style = strokeMain)
                // Tousled tufts
                drawLine(
                    color = inkColor,
                    start = Offset(w * 0.44f + tiltX, h * 0.14f),
                    end = Offset(w * 0.40f + tiltX, h * 0.07f),
                    strokeWidth = strokeThin.width,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = inkColor,
                    start = Offset(w * 0.52f + tiltX, h * 0.13f),
                    end = Offset(w * 0.55f + tiltX, h * 0.06f),
                    strokeWidth = strokeThin.width,
                    cap = StrokeCap.Round
                )
            } else {
                // Girl sketch: wavy bob & side bun lines
                val bobPath = Path().apply {
                    moveTo(w * 0.25f + tiltX, h * 0.52f)
                    cubicTo(
                        w * 0.22f + tiltX, h * 0.24f,
                        w * 0.35f + tiltX, h * 0.13f,
                        w * 0.50f + tiltX, h * 0.13f
                    )
                    cubicTo(
                        w * 0.65f + tiltX, h * 0.13f,
                        w * 0.78f + tiltX, h * 0.24f,
                        w * 0.75f + tiltX, h * 0.52f
                    )
                }
                drawPath(bobPath, color = inkColor, style = strokeMain)
                val fringePath = Path().apply {
                    moveTo(w * 0.31f + tiltX, h * 0.31f)
                    cubicTo(
                        w * 0.40f + tiltX, h * 0.24f,
                        w * 0.48f + tiltX, h * 0.28f,
                        w * 0.54f + tiltX, h * 0.22f
                    )
                    cubicTo(
                        w * 0.60f + tiltX, h * 0.27f,
                        w * 0.65f + tiltX, h * 0.26f,
                        w * 0.69f + tiltX, h * 0.31f
                    )
                }
                drawPath(fringePath, color = inkColor, style = strokeThin)
            }

            // Face contour
            val headPath = Path().apply {
                moveTo(w * 0.32f + tiltX, h * 0.33f)
                cubicTo(
                    w * 0.30f + tiltX, h * 0.49f,
                    w * 0.38f + tiltX, h * 0.60f,
                    w * 0.50f + tiltX, h * 0.60f
                )
                cubicTo(
                    w * 0.62f + tiltX, h * 0.60f,
                    w * 0.70f + tiltX, h * 0.49f,
                    w * 0.68f + tiltX, h * 0.33f
                )
            }
            drawPath(headPath, color = inkColor, style = strokeMain)

            // Eyes (blink on pose 3)
            if (poseIndex == 3) {
                drawLine(
                    color = inkColor,
                    start = Offset(w * 0.40f + tiltX, h * 0.41f),
                    end = Offset(w * 0.45f + tiltX, h * 0.41f),
                    strokeWidth = strokeThin.width,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = inkColor,
                    start = Offset(w * 0.55f + tiltX, h * 0.41f),
                    end = Offset(w * 0.60f + tiltX, h * 0.41f),
                    strokeWidth = strokeThin.width,
                    cap = StrokeCap.Round
                )
            } else {
                drawCircle(
                    color = inkColor,
                    radius = w * 0.022f,
                    center = Offset(w * 0.42f + tiltX, h * 0.41f)
                )
                drawCircle(
                    color = inkColor,
                    radius = w * 0.022f,
                    center = Offset(w * 0.58f + tiltX, h * 0.41f)
                )
            }

            // Mouth / expression varies across poses 0..3
            when (poseIndex) {
                0 -> {
                    val smile = Path().apply {
                        moveTo(w * 0.45f + tiltX, h * 0.50f)
                        quadraticBezierTo(w * 0.50f + tiltX, h * 0.54f, w * 0.55f + tiltX, h * 0.50f)
                    }
                    drawPath(smile, color = inkColor, style = strokeThin)
                }
                1, 3 -> {
                    drawOval(
                        color = inkColor,
                        topLeft = Offset(w * 0.47f + tiltX, h * 0.48f),
                        size = Size(w * 0.06f, h * 0.05f),
                        style = strokeThin
                    )
                }
                2 -> {
                    drawRoundRect(
                        color = inkColor,
                        topLeft = Offset(w * 0.45f + tiltX, h * 0.475f),
                        size = Size(w * 0.10f, h * 0.055f),
                        cornerRadius = CornerRadius(w * 0.03f, w * 0.03f),
                        style = strokeThin
                    )
                }
            }

            // Shoulders & notebook collar
            val shoulderPath = Path().apply {
                moveTo(w * 0.28f, h * 0.88f)
                cubicTo(w * 0.32f, h * 0.68f, w * 0.40f, h * 0.63f, w * 0.50f, h * 0.63f)
                cubicTo(w * 0.60f, h * 0.63f, w * 0.68f, h * 0.68f, w * 0.72f, h * 0.88f)
            }
            drawPath(shoulderPath, color = inkColor, style = strokeMain)

            val collarPath = Path().apply {
                moveTo(w * 0.42f, h * 0.63f)
                lineTo(w * 0.50f, h * 0.72f)
                lineTo(w * 0.58f, h * 0.63f)
            }
            drawPath(
                collarPath,
                color = if (characterOption == VoiceCharacterOption.GIRL) theme.terracotta else inkColor,
                style = strokeThin
            )

            // Hand-drawn acoustic arcs when playing
            if (isPlaying) {
                val waveCount = (poseIndex % 3) + 1
                for (i in 1..waveCount) {
                    val offset = i * (w * 0.055f)
                    val arcPathRight = Path().apply {
                        moveTo(w * 0.73f + offset, h * 0.36f - i * 3f)
                        quadraticBezierTo(
                            w * 0.78f + offset,
                            h * 0.43f,
                            w * 0.73f + offset,
                            h * 0.50f + i * 3f
                        )
                    }
                    drawPath(arcPathRight, color = theme.terracotta, style = strokeThin)

                    val arcPathLeft = Path().apply {
                        moveTo(w * 0.27f - offset, h * 0.36f - i * 3f)
                        quadraticBezierTo(
                            w * 0.22f - offset,
                            h * 0.43f,
                            w * 0.27f - offset,
                            h * 0.50f + i * 3f
                        )
                    }
                    drawPath(arcPathLeft, color = theme.terracotta, style = strokeThin)
                }
            }
        }
    }
}

@Composable
private fun ThoughtBubbleTitle(
    title: String,
    inkColor: Color,
    paperColor: Color,
    fontFamily: com.scrbl.app.domain.model.FontFamilyOption,
    fontScale: com.scrbl.app.domain.model.FontScaleOption
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(bottom = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .widthIn(min = 64.dp, max = 220.dp)
                .background(
                    color = paperColor,
                    shape = RoundedCornerShape(16.dp)
                )
                .border(
                    width = 1.5.dp,
                    color = inkColor,
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(horizontal = 12.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = title,
                style = ScrbLTypography.metadataTextStyle(fontFamily, fontScale, inkColor),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        // Two tiny hand-drawn thought bubble circles leading to character
        Canvas(modifier = Modifier.size(width = 24.dp, height = 14.dp)) {
            drawCircle(
                color = inkColor,
                radius = 3.2.dp.toPx(),
                center = Offset(size.width * 0.52f, size.height * 0.35f),
                style = Stroke(width = 1.4.dp.toPx())
            )
            drawCircle(
                color = inkColor,
                radius = 2.0.dp.toPx(),
                center = Offset(size.width * 0.46f, size.height * 0.80f),
                style = Stroke(width = 1.3.dp.toPx())
            )
        }
    }
}
