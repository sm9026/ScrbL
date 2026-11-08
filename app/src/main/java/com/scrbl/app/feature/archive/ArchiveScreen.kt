package com.scrbl.app.feature.archive

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.scrbl.app.core.design.YearStackCard
import com.scrbl.app.core.time.RecallDateTimeFormatter
import com.scrbl.app.domain.model.DayStackGroup
import com.scrbl.app.domain.model.RecallFilterState
import com.scrbl.app.domain.model.StackGridCell
import com.scrbl.app.domain.model.YearStackGroup
import com.scrbl.app.feature.home.DealtDayStackCellWrapper
import com.scrbl.app.feature.stackbrowser.FilterSheet

/**
 * Archive screen (`02_REQUIREMENTS_AND_RULES.md` section 7 & `04_UX_SCREEN_SPEC.md` section G):
 * - Reached from Profile/Settings.
 * - Uses the exact same 3-column paper-stack grid and dealing/collapse behaviour as Main.
 * - Root shows Year stacks (`'26`, `'25`, etc.), newest year first.
 * - Tapping a year opens that year's Month stacks (`MM`).
 * - Tapping a month deals its Day stacks (`DD/MM`) into the 3-column grid; original month cell
 *   becomes a flat `MM` page that collapses when tapped.
 * - Exposes the Archive Filter control ONLY when at least one month is expanded into day stacks
 *   (never at the Year-stack or unexpanded Month-stack level).
 * - Archive filter state (`archiveFilterState`) persists independently from `mainFilterState`.
 */
@Composable
fun ArchiveScreen(
    yearStacks: List<YearStackGroup>,
    selectedYear: Int?,
    yearGridCells: List<StackGridCell>,
    hasExpandedMonthsInSelectedYear: Boolean,
    archiveFilterState: RecallFilterState,
    onSelectYear: (Int) -> Unit,
    onExitSelectedYear: () -> Unit,
    onToggleArchiveMonthExpanded: (Int) -> Unit,
    onDayStackClick: (DayStackGroup) -> Unit,
    onUpdateArchiveFilter: (RecallFilterState) -> Unit,
    onNavigateBackToProfile: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalScrbLTheme.current
    val prefs = LocalUserPreferences.current
    var showArchiveFilterSheet by remember { mutableStateOf(false) }

    BackHandler {
        if (showArchiveFilterSheet) {
            showArchiveFilterSheet = false
        } else if (selectedYear != null) {
            onExitSelectedYear()
        } else {
            onNavigateBackToProfile()
        }
    }

    PaperBackground(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar with circular Back button, Archive / 'YY title, and conditional day-stack Filter button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DoodleIconButton(
                        iconRes = DoodleAssets.BackArrow,
                        contentDescription = if (selectedYear != null) {
                            "Back to archive years"
                        } else {
                            "Back to settings"
                        },
                        onClick = {
                            if (selectedYear != null) {
                                onExitSelectedYear()
                            } else {
                                onNavigateBackToProfile()
                            }
                        },
                        visualDiameter = 40.dp,
                        glyphSize = 20.dp
                    )

                    val headerText = if (selectedYear != null) {
                        "Archive ${RecallDateTimeFormatter.formatYearStackFace(selectedYear)}"
                    } else {
                        "Archive"
                    }
                    Text(
                        text = headerText,
                        style = MaterialTheme.typography.headlineMedium,
                        color = theme.inkNavy,
                        modifier = Modifier.semantics { heading() }
                    )
                }

                // Filter control exposed ONLY when browsing day stacks inside an expanded month
                if (selectedYear != null && (hasExpandedMonthsInSelectedYear || archiveFilterState.isActive)) {
                    DoodleIconButton(
                        iconRes = DoodleAssets.Filter,
                        contentDescription = stringResource(id = R.string.cd_filter),
                        onClick = { showArchiveFilterSheet = !showArchiveFilterSheet },
                        visualDiameter = 42.dp,
                        glyphSize = 20.dp,
                        isAccentFilled = archiveFilterState.isActive,
                        isSelected = archiveFilterState.isActive
                    )
                }
            }

            if (selectedYear == null) {
                // Archive Root: Year stacks ('26, '25, ...)
                if (yearStacks.isEmpty()) {
                    EmptyArchiveMessage(message = "No archived years yet.")
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(
                            items = yearStacks,
                            key = { "archive_year_${it.year}" }
                        ) { yearStack ->
                            YearStackCard(
                                yearStack = yearStack,
                                onClick = { onSelectYear(yearStack.year) }
                            )
                        }
                    }
                }
            } else {
                // Selected Year: Month stacks (MM) + dealt Day stacks (DD/MM)
                if (yearGridCells.isEmpty()) {
                    EmptyArchiveMessage(message = "No pages in this year match the filter.")
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(
                            items = yearGridCells,
                            key = { "archive_cell_${it.cellKey}" }
                        ) { cell ->
                            when (cell) {
                                is StackGridCell.MonthStackCell -> {
                                    MonthStackCard(
                                        monthStack = cell.monthStack,
                                        onClick = { onToggleArchiveMonthExpanded(cell.monthStack.month) }
                                    )
                                }
                                is StackGridCell.FlatMonthCollapseCell -> {
                                    FlatMonthCollapsePage(
                                        monthStack = cell.monthStack,
                                        onClick = { onToggleArchiveMonthExpanded(cell.monthStack.month) }
                                    )
                                }
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
                                is StackGridCell.YearStackCell -> Unit
                            }
                        }
                    }
                }
            }
        }

        // Independent Archive Filter sheet
        AnimatedVisibility(
            visible = showArchiveFilterSheet,
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
                        onClick = { showArchiveFilterSheet = false }
                    )
                    .padding(horizontal = 24.dp, vertical = 72.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                FilterSheet(
                    contextTitle = "Archive Day Filter",
                    filterState = archiveFilterState,
                    onUpdateFilter = onUpdateArchiveFilter,
                    onDismiss = { showArchiveFilterSheet = false },
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

@Composable
private fun EmptyArchiveMessage(message: String) {
    val theme = LocalScrbLTheme.current
    val prefs = LocalUserPreferences.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        PaperSheet(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 310.dp)
                .height(180.dp),
            isAddressedTone = false,
            cornerRadius = 14.dp,
            showFoldedCorner = true,
            contentPadding = 22.dp
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = message,
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
}
