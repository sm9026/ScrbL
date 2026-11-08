package com.scrbl.app.core.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.scrbl.app.R
import com.scrbl.app.core.time.RecallDateTimeFormatter
import com.scrbl.app.domain.model.DayStackGroup
import com.scrbl.app.domain.model.MonthStackGroup
import com.scrbl.app.domain.model.PaperEdgeOption
import com.scrbl.app.domain.model.PaperTextureOption
import com.scrbl.app.domain.model.Recall
import com.scrbl.app.domain.model.RecallType
import com.scrbl.app.domain.model.ShadowStyleOption
import com.scrbl.app.domain.model.YearStackGroup

/**
 * Full-screen warm notebook background surface (`#E9DDCA` default) with subtle paper grain.
 */
@Composable
fun PaperBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val theme = LocalScrbLTheme.current
    val prefs = LocalUserPreferences.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(theme.background)
    ) {
        if (prefs.paperTexture == PaperTextureOption.SUBTLE_GRAIN) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawSubtlePaperGrain(
                    inkColor = theme.inkNavy,
                    densityStep = 38.dp.toPx()
                )
            }
        }
        content()
    }
}

/**
 * Reusable single physical paper sheet with:
 * - Warm ivory tone (`paper` for unaddressed, `paperAddressed` for addressed)
 * - Optional subtle paper grain or faint ruled lines (`PaperTextureOption`)
 * - Clean or soft hand-drawn ink/paper-edge border (`PaperEdgeOption`)
 * - Subtle dog-ear folded top-right corner when `showFoldedCorner = true` (matching `paper_stack_reference.svg`)
 * - No spiral binding on app cards (spiral binding belongs strictly to the widget notepad)
 */
@Composable
fun PaperSheet(
    modifier: Modifier = Modifier,
    isAddressedTone: Boolean = false,
    cornerRadius: Dp = 14.dp,
    showFoldedCorner: Boolean = false,
    contentPadding: Dp = 16.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val theme = LocalScrbLTheme.current
    val prefs = LocalUserPreferences.current
    val sheetFill = if (isAddressedTone) theme.paperAddressed else theme.paper
    val shadowElevation = when (prefs.shadowStyle) {
        ShadowStyleOption.SUBTLE -> 3.dp
        ShadowStyleOption.SOFT -> 6.dp
    }
    val shape = RoundedCornerShape(cornerRadius)

    Box(
        modifier = modifier
            .shadow(
                elevation = shadowElevation,
                shape = shape,
                ambientColor = theme.inkNavy.copy(alpha = 0.25f),
                spotColor = theme.inkNavy.copy(alpha = 0.25f)
            )
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val r = cornerRadius.toPx()
            val w = size.width
            val h = size.height
            val fold = if (showFoldedCorner) minOf(w, h) * 0.13f else 0f

            // Front sheet body path
            val sheetPath = Path().apply {
                moveTo(0f, r)
                quadraticBezierTo(0f, 0f, r, 0f)
                if (showFoldedCorner) {
                    lineTo(w - fold, 0f)
                    lineTo(w, fold)
                } else {
                    lineTo(w - r, 0f)
                    quadraticBezierTo(w, 0f, w, r)
                }
                lineTo(w, h - r)
                quadraticBezierTo(w, h, w - r, h)
                lineTo(r, h)
                quadraticBezierTo(0f, h, 0f, h - r)
                close()
            }

            drawPath(path = sheetPath, color = sheetFill)

            // Paper texture (subtle grain or faint ruled lines)
            when (prefs.paperTexture) {
                PaperTextureOption.SUBTLE_GRAIN -> {
                    drawSubtlePaperGrain(
                        inkColor = theme.inkNavy,
                        densityStep = 26.dp.toPx()
                    )
                }
                PaperTextureOption.RULED -> {
                    val lineSpacing = 28.dp.toPx()
                    var y = lineSpacing * 1.6f
                    while (y < h - lineSpacing) {
                        drawLine(
                            color = theme.paperEdge.copy(alpha = 0.45f),
                            start = Offset(16.dp.toPx(), y),
                            end = Offset(w - 16.dp.toPx(), y),
                            strokeWidth = 1.dp.toPx()
                        )
                        y += lineSpacing
                    }
                }
                PaperTextureOption.PLAIN -> Unit
            }

            // Border stroke (soft hand-drawn double-pass feel vs clean cut)
            val strokeWidth = if (prefs.paperEdge == PaperEdgeOption.SOFT_HANDDRAWN) {
                2.0.dp.toPx()
            } else {
                1.4.dp.toPx()
            }
            drawPath(
                path = sheetPath,
                color = theme.paperEdge,
                style = Stroke(
                    width = strokeWidth,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
            if (prefs.paperEdge == PaperEdgeOption.SOFT_HANDDRAWN) {
                drawRoundRect(
                    color = theme.inkNavy.copy(alpha = 0.14f),
                    topLeft = Offset(1.2f, 1.2f),
                    size = Size(w - 2.4f, h - 2.4f),
                    cornerRadius = CornerRadius(r, r),
                    style = Stroke(width = 1.dp.toPx())
                )
            }

            // Folded corner triangle matching paper_stack_reference.svg
            if (showFoldedCorner) {
                val flapPath = Path().apply {
                    moveTo(w - fold, 0f)
                    lineTo(w, fold)
                    lineTo(w - fold, fold)
                    close()
                }
                drawPath(path = flapPath, color = theme.background)
                drawPath(
                    path = flapPath,
                    color = theme.paperEdge,
                    style = Stroke(width = strokeWidth, join = StrokeJoin.Round)
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            content = content
        )
    }
}

/**
 * Layered paper stack card matching `assets/references/paper_stack_reference.svg`:
 * - 2, 3, or 4 visible sheets according to `UserPreferences.stackDepth`.
 * - Front sheet shows ONLY the compact date/month/year label (no snippet, no count badge, no status label).
 * - Uses theme-derived shade pair (`isAddressedTone`) for addressed vs unaddressed state.
 */
@Composable
fun PaperStackCard(
    faceLabel: String,
    contentDescriptionLabel: String,
    isAddressedTone: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isMonthOrYearStack: Boolean = false
) {
    val theme = LocalScrbLTheme.current
    val prefs = LocalUserPreferences.current
    val stackLayers = prefs.stackDepth.coerceIn(2, 4)
    val textColor = if (isAddressedTone) theme.addressedTone else theme.unaddressedTone
    val frontFill = if (isAddressedTone) theme.paperAddressed else theme.paper

    Box(
        modifier = modifier
            .aspectRatio(0.78f)
            .semantics {
                contentDescription = contentDescriptionLabel
                role = Role.Button
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val pad = w * 0.06f
            val cardW = w - pad * 2f
            val cardH = h - pad * 2f
            val r = 11.dp.toPx()

            // Back layer 3 (deepest, if stackLayers >= 4)
            if (stackLayers >= 4) {
                rotate(degrees = 4.2f, pivot = Offset(w / 2f, h / 2f)) {
                    drawRoundRect(
                        color = theme.backSheet2.copy(alpha = 0.85f),
                        topLeft = Offset(pad + 3.dp.toPx(), pad + 4.dp.toPx()),
                        size = Size(cardW, cardH),
                        cornerRadius = CornerRadius(r, r)
                    )
                    drawRoundRect(
                        color = theme.paperEdge,
                        topLeft = Offset(pad + 3.dp.toPx(), pad + 4.dp.toPx()),
                        size = Size(cardW, cardH),
                        cornerRadius = CornerRadius(r, r),
                        style = Stroke(width = 1.4.dp.toPx())
                    )
                }
            }

            // Back layer 2 (if stackLayers >= 3)
            if (stackLayers >= 3) {
                rotate(degrees = -3.6f, pivot = Offset(w / 2f, h / 2f)) {
                    drawRoundRect(
                        color = theme.backSheet2,
                        topLeft = Offset(pad - 2.dp.toPx(), pad + 3.dp.toPx()),
                        size = Size(cardW, cardH),
                        cornerRadius = CornerRadius(r, r)
                    )
                    drawRoundRect(
                        color = theme.paperEdge,
                        topLeft = Offset(pad - 2.dp.toPx(), pad + 3.dp.toPx()),
                        size = Size(cardW, cardH),
                        cornerRadius = CornerRadius(r, r),
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }
            }

            // Back layer 1 (always visible on stacks)
            rotate(degrees = if (isMonthOrYearStack) 2.6f else 1.8f, pivot = Offset(w / 2f, h / 2f)) {
                drawRoundRect(
                    color = theme.backSheet1,
                    topLeft = Offset(pad + 2.dp.toPx(), pad + 1.5.dp.toPx()),
                    size = Size(cardW, cardH),
                    cornerRadius = CornerRadius(r, r)
                )
                drawRoundRect(
                    color = theme.paperEdge,
                    topLeft = Offset(pad + 2.dp.toPx(), pad + 1.5.dp.toPx()),
                    size = Size(cardW, cardH),
                    cornerRadius = CornerRadius(r, r),
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }

            // Front sheet with subtle folded corner
            val fold = minOf(cardW, cardH) * 0.14f
            val frontPath = Path().apply {
                moveTo(pad, pad + r)
                quadraticBezierTo(pad, pad, pad + r, pad)
                lineTo(pad + cardW - fold, pad)
                lineTo(pad + cardW, pad + fold)
                lineTo(pad + cardW, pad + cardH - r)
                quadraticBezierTo(pad + cardW, pad + cardH, pad + cardW - r, pad + cardH)
                lineTo(pad + r, pad + cardH)
                quadraticBezierTo(pad, pad + cardH, pad, pad + cardH - r)
                close()
            }
            drawPath(path = frontPath, color = frontFill)
            drawPath(
                path = frontPath,
                color = if (isMonthOrYearStack) theme.inkNavy.copy(alpha = 0.55f) else theme.paperEdge,
                style = Stroke(width = 1.9.dp.toPx(), join = StrokeJoin.Round)
            )

            // Folded dog-ear corner
            val flap = Path().apply {
                moveTo(pad + cardW - fold, pad)
                lineTo(pad + cardW, pad + fold)
                lineTo(pad + cardW - fold, pad + fold)
                close()
            }
            drawPath(path = flap, color = theme.background)
            drawPath(
                path = flap,
                color = theme.paperEdge,
                style = Stroke(width = 1.5.dp.toPx(), join = StrokeJoin.Round)
            )
        }

        // Front face displays ONLY the compact date / MM / 'YY label
        Text(
            text = faceLabel,
            style = ScrbLTypography.stackLabelTextStyle(
                familyOption = prefs.fontFamily,
                scaleOption = prefs.fontScale,
                color = textColor
            ),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun DayStackCard(
    dayStack: DayStackGroup,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val prefs = LocalUserPreferences.current
    val label = RecallDateTimeFormatter.formatDayStackFace(
        capturedLocalDateIso = dayStack.capturedLocalDate,
        patternPref = prefs.dateFormat
    )
    PaperStackCard(
        faceLabel = label,
        contentDescriptionLabel = "Day stack $label",
        isAddressedTone = dayStack.isAllAddressed,
        onClick = onClick,
        modifier = modifier,
        isMonthOrYearStack = false
    )
}

@Composable
fun MonthStackCard(
    monthStack: MonthStackGroup,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val label = RecallDateTimeFormatter.formatMonthStackFace(monthStack.month)
    PaperStackCard(
        faceLabel = label,
        contentDescriptionLabel = "Month stack $label. Tap to deal day stacks.",
        isAddressedTone = monthStack.isAllAddressed,
        onClick = onClick,
        modifier = modifier,
        isMonthOrYearStack = true
    )
}

@Composable
fun YearStackCard(
    yearStack: YearStackGroup,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val label = RecallDateTimeFormatter.formatYearStackFace(yearStack.year)
    PaperStackCard(
        faceLabel = label,
        contentDescriptionLabel = "Archive year stack $label. Tap to open months.",
        isAddressedTone = yearStack.isAllAddressed,
        onClick = onClick,
        modifier = modifier,
        isMonthOrYearStack = true
    )
}

/**
 * When a completed month stack is tapped and dealt into the grid, its original grid cell
 * remains as a single flat page labelled `MM`. Tapping that flat page collapses/reassembles the month.
 */
@Composable
fun FlatMonthCollapsePage(
    monthStack: MonthStackGroup,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalScrbLTheme.current
    val prefs = LocalUserPreferences.current
    val label = RecallDateTimeFormatter.formatMonthStackFace(monthStack.month)

    Box(
        modifier = modifier
            .aspectRatio(0.78f)
            .semantics {
                contentDescription = "Expanded month $label flat page. Tap to collapse day stacks."
                role = Role.Button
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(6.dp)
    ) {
        PaperSheet(
            modifier = Modifier.fillMaxSize(),
            isAddressedTone = true,
            cornerRadius = 11.dp,
            showFoldedCorner = false,
            contentPadding = 8.dp
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = ScrbLTypography.stackLabelTextStyle(
                        familyOption = prefs.fontFamily,
                        scaleOption = prefs.fontScale,
                        color = theme.addressedTone
                    )
                )
            }
        }
    }
}

/**
 * Shared Expanded Recall Page used identically across Main, Archive, Favourites, and Widget-origin detail
 * (`02_REQUIREMENTS_AND_RULES.md` section 6 & `04_UX_SCREEN_SPEC.md` section F):
 * - Top-left: date (`DD MMM` default, no year)
 * - Top-right: hand-drawn heart (empty or filled according to `isFavourite`)
 * - Centre: centred text body OR voice doodle character + optional title thought bubble
 * - Bottom-left: creation time (`12h` / `24h` in captured zone)
 * - Bottom-right: current / total page index (`03 / 12`)
 * - Double-tap page = toggle addressed (`isAddressed`); also exposed as a custom accessibility action for TalkBack
 * - Tap heart = toggle favourite independently without triggering double-tap addressed
 * - Tap voice character = play/pause local audio without triggering double-tap addressed
 * - Strictly NO content-type badge, swipe hint, edit button, or delete button.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecallPage(
    recall: Recall,
    currentPageOneBased: Int,
    totalPages: Int,
    isPlayingVoice: Boolean,
    isSystem24Hour: Boolean,
    onToggleAddressed: () -> Unit,
    onToggleFavourite: () -> Unit,
    onToggleVoicePlayback: () -> Unit,
    playbackErrorMessage: String? = null,
    modifier: Modifier = Modifier
) {
    val theme = LocalScrbLTheme.current
    val prefs = LocalUserPreferences.current
    val toggleAddressedLabel = stringResource(id = R.string.action_toggle_addressed)

    val dateLabel = RecallDateTimeFormatter.formatExpandedPageDate(
        capturedLocalDateIso = recall.capturedLocalDate,
        patternPref = prefs.dateFormat
    )
    val timeLabel = RecallDateTimeFormatter.formatCreationTime(
        createdAtEpochMillis = recall.createdAtEpochMillis,
        capturedZoneId = recall.capturedZoneId,
        timeFormatOption = prefs.timeFormat,
        isSystem24Hour = isSystem24Hour
    )
    val pageCounterLabel = RecallDateTimeFormatter.formatPageIndex(
        oneBasedIndex = currentPageOneBased,
        totalCount = totalPages
    )
    val primaryTone = if (recall.isAddressed) theme.addressedTone else theme.unaddressedTone

    PaperSheet(
        modifier = modifier
            .semantics {
                contentDescription = buildString {
                    append("Recall page $pageCounterLabel, captured $dateLabel at $timeLabel. ")
                    append(if (recall.isAddressed) "Status: Addressed. " else "Status: Unaddressed. ")
                    append(if (recall.isFavourite) "Favourite." else "Not favourite.")
                }
                customActions = listOf(
                    CustomAccessibilityAction(label = toggleAddressedLabel) {
                        onToggleAddressed()
                        true
                    }
                )
            }
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
                onDoubleClick = onToggleAddressed
            ),
        isAddressedTone = recall.isAddressed,
        cornerRadius = 14.dp,
        showFoldedCorner = true,
        contentPadding = 22.dp
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top bar: Date top-left, hand-drawn Heart top-right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = dateLabel,
                    style = ScrbLTypography.metadataTextStyle(
                        familyOption = prefs.fontFamily,
                        scaleOption = prefs.fontScale,
                        color = primaryTone
                    )
                )

                // Independent 48dp hit target for the doodle heart so tapping it never toggles addressed
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .semantics {
                            contentDescription = if (recall.isFavourite) {
                                "Favourite recall, selected. Tap to remove from favourites."
                            } else {
                                "Not favourite. Tap to mark as favourite."
                            }
                            role = Role.Button
                        }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onToggleFavourite
                        ),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(
                            id = if (recall.isFavourite) {
                                DoodleAssets.HeartFilled
                            } else {
                                DoodleAssets.HeartOutline
                            }
                        ),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            // Centre content: either centred text body or voice doodle character
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                if (recall.type == RecallType.TEXT) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = recall.textBody.orEmpty(),
                            style = ScrbLTypography.recallBodyTextStyle(
                                familyOption = prefs.fontFamily,
                                scaleOption = prefs.fontScale,
                                color = primaryTone
                            ),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        VoiceCharacter(
                            characterOption = prefs.voiceCharacter,
                            voiceTitle = recall.voiceTitle,
                            isPlaying = isPlayingVoice,
                            animationsEnabled = prefs.animationsEnabled,
                            inkColor = primaryTone,
                            onClick = onToggleVoicePlayback,
                            characterSize = 136.dp
                        )
                        if (!playbackErrorMessage.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = playbackErrorMessage,
                                style = ScrbLTypography.metadataTextStyle(
                                    familyOption = prefs.fontFamily,
                                    scaleOption = prefs.fontScale,
                                    color = theme.terracotta
                                ),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // Bottom metadata row: creation time bottom-left, 03 / 12 index bottom-right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = timeLabel,
                    style = ScrbLTypography.metadataTextStyle(
                        familyOption = prefs.fontFamily,
                        scaleOption = prefs.fontScale,
                        color = theme.addressedTone
                    )
                )
                Text(
                    text = pageCounterLabel,
                    style = ScrbLTypography.metadataTextStyle(
                        familyOption = prefs.fontFamily,
                        scaleOption = prefs.fontScale,
                        color = theme.addressedTone
                    )
                )
            }
        }
    }
}

private fun DrawScope.drawSubtlePaperGrain(inkColor: Color, densityStep: Float) {
    val step = densityStep.coerceAtLeast(18f)
    var y = step * 0.5f
    var row = 0
    while (y < size.height) {
        var x = if (row % 2 == 0) step * 0.4f else step * 0.9f
        var col = 0
        while (x < size.width) {
            val hash = ((row * 31 + col * 17) % 11)
            val dx = (hash - 5) * 1.3f
            val dy = ((hash * 7) % 9 - 4) * 1.3f
            val alpha = if (hash % 3 == 0) 0.032f else 0.018f
            drawCircle(
                color = inkColor.copy(alpha = alpha),
                radius = if (hash % 4 == 0) 1.3f else 0.9f,
                center = Offset(
                    (x + dx).coerceIn(0f, size.width),
                    (y + dy).coerceIn(0f, size.height)
                )
            )
            x += step
            col++
        }
        y += step
        row++
    }
}
