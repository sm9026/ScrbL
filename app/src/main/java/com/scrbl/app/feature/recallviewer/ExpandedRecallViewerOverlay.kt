package com.scrbl.app.feature.recallviewer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.scrbl.app.R
import com.scrbl.app.core.design.DoodleAssets
import com.scrbl.app.core.design.DoodleIconButton
import com.scrbl.app.core.design.LocalScrbLTheme
import com.scrbl.app.core.design.RecallPage
import com.scrbl.app.domain.model.Recall

/**
 * Shared Expanded Recall Viewer used across:
 * - Main day-stack browsing
 * - Archive day-stack browsing
 * - Favourites collection browsing
 * - Widget-origin direct detail (`isWidgetOrigin = true`)
 *
 * Enforces `02_REQUIREMENTS_AND_RULES.md` section 6 & `04_UX_SCREEN_SPEC.md` sections F and J:
 * - Background remains visible but dimmed (`theme.dimmedScrim`).
 * - Vertical paging only (`VerticalPager`): swipe up = older recall (next index in newest-first list),
 *   swipe down = newer recall (previous index in newest-first list). No horizontal paging.
 * - Double-tap page toggles `isAddressed`.
 * - Tap doodle heart toggles `isFavourite` independently.
 * - Tap voice doodle character plays/pauses local audio and cycles through 4 poses.
 * - Widget-origin detail (`isWidgetOrigin = true`) includes a circular `+` button at bottom-right
 *   that opens the shared blank Capture page; app-origin detail does not add an extra `+`.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExpandedRecallViewerOverlay(
    recallsNewestFirst: List<Recall>,
    initialRecallId: String?,
    isWidgetOrigin: Boolean,
    activePlayingRecallId: String?,
    playbackErrorMessage: String?,
    isSystem24Hour: Boolean,
    onToggleAddressed: (String) -> Unit,
    onToggleFavourite: (String) -> Unit,
    onToggleVoicePlayback: (Recall) -> Unit,
    onStopVoicePlayback: () -> Unit,
    onDismiss: () -> Unit,
    onWidgetPlusClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (recallsNewestFirst.isEmpty()) return

    val theme = LocalScrbLTheme.current
    val initialIndex = remember(initialRecallId, recallsNewestFirst.size) {
        val found = recallsNewestFirst.indexOfFirst { it.id == initialRecallId }
        if (found >= 0) found else 0
    }

    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, (recallsNewestFirst.size - 1).coerceAtLeast(0)),
        pageCount = { recallsNewestFirst.size }
    )

    // Stop any active audio playback when swiping to a different recall page or dismissing the viewer
    LaunchedEffect(pagerState.currentPage) {
        val currentRecall = recallsNewestFirst.getOrNull(pagerState.currentPage)
        if (activePlayingRecallId != null && currentRecall?.id != activePlayingRecallId) {
            onStopVoicePlayback()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            onStopVoicePlayback()
        }
    }

    BackHandler {
        onStopVoicePlayback()
        onDismiss()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(theme.dimmedScrim)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    onStopVoicePlayback()
                    onDismiss()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        // VerticalPager: swipe up advances to index + 1 (older recall), swipe down returns to index - 1 (newer recall)
        VerticalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 390.dp)
                .heightIn(min = 380.dp, max = 520.dp)
                .padding(horizontal = 22.dp, vertical = 32.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {} // Prevent background scrim click-through when touching page bounds
                ),
            pageSpacing = 20.dp
        ) { pageIndex ->
            val recall = recallsNewestFirst[pageIndex]
            RecallPage(
                recall = recall,
                currentPageOneBased = pageIndex + 1,
                totalPages = recallsNewestFirst.size,
                isPlayingVoice = (activePlayingRecallId == recall.id),
                isSystem24Hour = isSystem24Hour,
                onToggleAddressed = { onToggleAddressed(recall.id) },
                onToggleFavourite = { onToggleFavourite(recall.id) },
                onToggleVoicePlayback = { onToggleVoicePlayback(recall) },
                playbackErrorMessage = if (activePlayingRecallId == recall.id) playbackErrorMessage else null,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Widget-origin expanded detail page includes a circular `+` action that launches the shared blank capture page
        if (isWidgetOrigin) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(end = 24.dp, bottom = 28.dp),
                contentAlignment = Alignment.BottomEnd
            ) {
                DoodleIconButton(
                    iconRes = DoodleAssets.Plus,
                    contentDescription = stringResource(id = R.string.cd_capture_plus),
                    onClick = onWidgetPlusClick,
                    visualDiameter = 56.dp,
                    glyphSize = 26.dp,
                    isAccentFilled = true
                )
            }
        }
    }
}
