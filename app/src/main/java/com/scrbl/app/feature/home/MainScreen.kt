package com.scrbl.app.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.scrbl.app.R
import com.scrbl.app.core.design.DayStackCard
import com.scrbl.app.core.design.DoodleAssets
import com.scrbl.app.core.design.DoodleIconButton
import com.scrbl.app.core.design.FlatMonthCollapsePage
import com.scrbl.app.core.design.LocalScrbLTheme
import com.scrbl.app.core.design.LocalUserPreferences
import com.scrbl.app.core.design.MonthStackCard
import com.scrbl.app.core.design.PaperBackground
import com.scrbl.app.core.design.PaperSheet
import com.scrbl.app.core.design.ScrbLTypography
import com.scrbl.app.domain.model.DayStackGroup
import com.scrbl.app.domain.model.RecallFilterState
import com.scrbl.app.domain.model.StackGridCell
import com.scrbl.app.feature.stackbrowser.FilterSheet
import kotlinx.coroutines.delay

/**
 * Main / Current Year screen (`02_REQUIREMENTS_AND_RULES.md` section 5 & `04_UX_SCREEN_SPEC.md` section A):
 * - Shows ONLY the current calendar year.
 * - Top-left: `ScrbL` wordmark/title.
 * - Top-right: circular profile/avatar doodle button (plus circular filter button ONLY when day stacks are visible).
 * - Exactly 3 columns (`GridCells.Fixed(3)`), unlimited scrollable rows (never a 12-item cap).
 * - Active current month is represented by day stacks (`DD/MM`).
 * - Completed months are represented by month stacks (`MM`).
 * - Tapping a month stack deals its day stacks one-by-one into the grid; the original month cell stays
 *   as a single flat page labelled `MM`; tapping that flat page collapses/reassembles the month.
 * - Bottom-right: circular doodle `+` action floating above the grid.
 * - No bottom navigation bar, dashboard cards, statistics, or conventional feed.
 */
@Composable
fun MainScreen(
    gridCells: List<StackGridCell>,
    hasAnyDayStacksVisible: Boolean,
    mainFilterState: RecallFilterState,
    onUpdateMainFilter: (RecallFilterState) -> Unit,
    onDayStackClick: (DayStackGroup) -> Unit,
    onToggleMonthExpanded: (Int) -> Unit,
    onOpenProfileSettings: () -> Unit,
    onOpenCapture: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalScrbLTheme.current
    val prefs = LocalUserPreferences.current
    var showFilterPanel by remember { mutableStateOf(false) }

    PaperBackground(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar: ScrbL wordmark at top-left, circular filter (only in day-stack context) & circular profile at top-right
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(id = R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    color = theme.inkNavy,
                    modifier = Modifier.semantics { heading() }
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (hasAnyDayStacksVisible || mainFilterState.isActive) {
                        DoodleIconButton(
                            iconRes = DoodleAssets.Filter,
                            contentDescription = stringResource(id = R.string.cd_filter),
                            onClick = { showFilterPanel = !showFilterPanel },
                            visualDiameter = 42.dp,
                            glyphSize = 20.dp,
                            isAccentFilled = mainFilterState.isActive,
                            isSelected = mainFilterState.isActive
                        )
                    }

                    DoodleIconButton(
                        iconRes = DoodleAssets.Settings,
                        contentDescription = stringResource(id = R.string.cd_profile_settings),
                        onClick = onOpenProfileSettings,
                        visualDiameter = 42.dp,
                        glyphSize = 21.dp
                    )
                }
            }

            if (gridCells.isEmpty()) {
                // Quiet empty paper state with a single gentle prompt to capture the first thought
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    PaperSheet(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 320.dp)
                            .height(210.dp),
                        isAddressedTone = false,
                        cornerRadius = 14.dp,
                        showFoldedCorner = true,
                        contentPadding = 24.dp
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = if (mainFilterState.isActive) {
                                    "No pages match the active filter."
                                } else {
                                    "A quiet notebook for this year.\nTap + to catch a fleeting thought."
                                },
                                style = ScrbLTypography.recallBodyTextStyle(
                                    familyOption = prefs.fontFamily,
                                    scaleOption = prefs.fontScale,
                                    color = theme.addressedTone
                                ),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                // 3-column lazy scrollable paper-stack grid
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = 104.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(
                        items = gridCells,
                        key = { it.cellKey }
                    ) { cell ->
                        when (cell) {
                            is StackGridCell.DayStackCell -> {
                                DealtDayStackCellWrapper(
                                    dealOrderIndex = cell.dealOrderIndex,
                                    isFromExpandedMonth = cell.parentExpandedMonth != null,
                                    animationsEnabled = prefs.animationsEnabled
                                ) {
                                    DayStackCard(
                                        dayStack = cell.dayStack,
                                        onClick = { onDayStackClick(cell.dayStack) }
                                    )
                                }
                            }
                            is StackGridCell.MonthStackCell -> {
                                MonthStackCard(
                                    monthStack = cell.monthStack,
                                    onClick = { onToggleMonthExpanded(cell.monthStack.month) }
                                )
                            }
                            is StackGridCell.FlatMonthCollapseCell -> {
                                FlatMonthCollapsePage(
                                    monthStack = cell.monthStack,
                                    onClick = { onToggleMonthExpanded(cell.monthStack.month) }
                                )
                            }
                            is StackGridCell.YearStackCell -> Unit
                        }
                    }
                }
            }
        }

        // Floating circular doodle `+` action at bottom-right
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(end = 24.dp, bottom = 28.dp),
            contentAlignment = Alignment.BottomEnd
        ) {
            DoodleIconButton(
                iconRes = DoodleAssets.Plus,
                contentDescription = stringResource(id = R.string.cd_capture_plus),
                onClick = onOpenCapture,
                visualDiameter = 58.dp,
                glyphSize = 28.dp,
                isAccentFilled = true
            )
        }

        // Day-stack filter panel overlay
        AnimatedVisibility(
            visible = showFilterPanel,
            enter = fadeIn(tween(if (prefs.animationsEnabled) 160 else 0)),
            exit = fadeOut(tween(if (prefs.animationsEnabled) 140 else 0))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(theme.dimmedScrim)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { showFilterPanel = false }
                    )
                    .padding(horizontal = 24.dp, vertical = 72.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                FilterSheet(
                    contextTitle = "Main Day Filter",
                    filterState = mainFilterState,
                    onUpdateFilter = onUpdateMainFilter,
                    onDismiss = { showFilterPanel = false },
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
                )
            }
        }
    }
}

/**
 * Sequential card-dealing entrance when a month stack expands into day stacks (`05_INTERACTIONS_AND_GESTURES.md` section 6).
 * When `animationsEnabled = false`, jumps immediately to final state without motion.
 */
@Composable
fun DealtDayStackCellWrapper(
    dealOrderIndex: Int,
    isFromExpandedMonth: Boolean,
    animationsEnabled: Boolean,
    content: @Composable () -> Unit
) {
    if (!isFromExpandedMonth || !animationsEnabled) {
        content()
        return
    }

    val progress = remember { Animatable(0f) }
    LaunchedEffect(dealOrderIndex) {
        delay((dealOrderIndex.coerceAtMost(12) * 35L))
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 190)
        )
    }

    Box(
        modifier = Modifier.graphicsLayer {
            alpha = progress.value
            val scale = 0.86f + (0.14f * progress.value)
            scaleX = scale
            scaleY = scale
            translationY = (1f - progress.value) * -18f
        }
    ) {
        content()
    }
}
