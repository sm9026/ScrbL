package com.scrbl.app.domain.usecase

import com.scrbl.app.core.result.AppResult
import com.scrbl.app.core.result.FailureReason
import com.scrbl.app.core.time.CaptureTimestampSnapshot
import com.scrbl.app.core.time.TimeProvider
import com.scrbl.app.domain.model.DayStackGroup
import com.scrbl.app.domain.model.MonthStackGroup
import com.scrbl.app.domain.model.Recall
import com.scrbl.app.domain.model.RecallFilterState
import com.scrbl.app.domain.model.RecallType
import com.scrbl.app.domain.model.StackGridCell
import com.scrbl.app.domain.model.YearStackGroup
import com.scrbl.app.domain.repository.RecallRepository
import com.scrbl.app.domain.repository.VoiceFileStorage
import com.scrbl.app.domain.repository.WidgetRefresher
import com.scrbl.app.domain.validation.RecallValidator
import java.io.File
import java.util.UUID

/**
 * Validates and commits a new Text Recall (max 25 words).
 * Captures the creation timestamp, local date, and zone once at commit and refreshes the widget.
 */
class CreateTextRecallUseCase(
    private val recallRepository: RecallRepository,
    private val timeProvider: TimeProvider,
    private val widgetRefresher: WidgetRefresher
) {
    suspend operator fun invoke(
        rawText: String,
        explicitId: String = UUID.randomUUID().toString()
    ): AppResult<Recall> {
        val validation = RecallValidator.validateTextRecall(rawText)
        if (validation is AppResult.Error) return validation
        val normalizedBody = (validation as AppResult.Success).data

        val timestamp = CaptureTimestampSnapshot.captureNow(timeProvider)
        val recall = Recall(
            id = explicitId,
            type = RecallType.TEXT,
            textBody = normalizedBody,
            voiceRelativePath = null,
            voiceTitle = null,
            createdAtEpochMillis = timestamp.createdAtEpochMillis,
            capturedLocalDate = timestamp.capturedLocalDate,
            capturedZoneId = timestamp.capturedZoneId,
            audioDurationMillis = null,
            isAddressed = false,
            isFavourite = false
        )

        val result = recallRepository.insertCommittedRecall(recall)
        if (result is AppResult.Success) {
            widgetRefresher.refreshWidgets()
        }
        return result
    }
}

/**
 * Finalizes and commits a Voice Recall (max 37,000 ms).
 * Follows the strict commit ordering in 05_INTERACTIONS_AND_GESTURES.md:
 * 1. Validate duration (1..37,000 ms) and optional title.
 * 2. Atomically move temp file to permanent app-private path (`recalls/audio/<uuid>.m4a`).
 * 3. Capture timestamp/date/zone once at successful save.
 * 4. Insert Room row referencing permanent relative path.
 * 5. If DB insertion fails, delete the uncommitted permanent file (rollback cleanup).
 * 6. Trigger widget refresh.
 */
class FinalizeVoiceRecallUseCase(
    private val recallRepository: RecallRepository,
    private val voiceFileStorage: VoiceFileStorage,
    private val timeProvider: TimeProvider,
    private val widgetRefresher: WidgetRefresher
) {
    suspend operator fun invoke(
        tempAudioFile: File,
        durationMillis: Long,
        optionalVoiceTitle: String?,
        explicitId: String = UUID.randomUUID().toString()
    ): AppResult<Recall> {
        if (!tempAudioFile.exists() || tempAudioFile.length() <= 0L) {
            voiceFileStorage.deleteTempFile(tempAudioFile)
            return AppResult.Error(
                FailureReason.VOICE_FILE_MISSING_OR_EMPTY,
                "Recorded voice file is empty or missing."
            )
        }

        val cappedDuration = if (durationMillis in 37_001L..37_400L) {
            // Platform MediaRecorder callback jitter guard when auto-stopping at 37,000 ms
            RecallValidator.MAX_VOICE_DURATION_MS
        } else {
            durationMillis
        }

        val promoteResult = voiceFileStorage.promoteTempToPermanent(tempAudioFile, explicitId)
        if (promoteResult is AppResult.Error) {
            voiceFileStorage.deleteTempFile(tempAudioFile)
            return promoteResult
        }
        val permanentRelativePath = (promoteResult as AppResult.Success).data
        val existsInPermanent = voiceFileStorage.existsAndNonEmpty(permanentRelativePath)

        val validation = RecallValidator.validateVoiceRecall(
            relativePath = permanentRelativePath,
            fileExistsAndNonEmpty = existsInPermanent,
            durationMillis = cappedDuration,
            rawTitle = optionalVoiceTitle
        )
        if (validation is AppResult.Error) {
            voiceFileStorage.deleteUncommittedFile(permanentRelativePath)
            return validation
        }
        val validatedInput = (validation as AppResult.Success).data

        val timestamp = CaptureTimestampSnapshot.captureNow(timeProvider)
        val recall = Recall(
            id = explicitId,
            type = RecallType.VOICE,
            textBody = null,
            voiceRelativePath = validatedInput.voiceRelativePath,
            voiceTitle = validatedInput.voiceTitle,
            createdAtEpochMillis = timestamp.createdAtEpochMillis,
            capturedLocalDate = timestamp.capturedLocalDate,
            capturedZoneId = timestamp.capturedZoneId,
            audioDurationMillis = validatedInput.audioDurationMillis,
            isAddressed = false,
            isFavourite = false
        )

        val insertResult = recallRepository.insertCommittedRecall(recall)
        if (insertResult is AppResult.Error) {
            // Rollback cleanup so no orphaned permanent file remains on DB error
            voiceFileStorage.deleteUncommittedFile(permanentRelativePath)
            return insertResult
        }

        widgetRefresher.refreshWidgets()
        return insertResult
    }
}

/**
 * Discards an uncommitted capture session (flick-away, system Back, audio interruption, backgrounding)
 * and deletes any partial temporary audio file so no draft ever persists.
 */
class CancelCaptureUseCase(
    private val voiceFileStorage: VoiceFileStorage
) {
    suspend operator fun invoke(tempAudioFile: File?) {
        voiceFileStorage.deleteTempFile(tempAudioFile)
    }
}

/**
 * Cleans up abandoned `.tmp` voice files left behind by process death before commit.
 * Never touches committed voice files referenced by Room rows.
 */
class CleanupAbandonedRecordingTempsUseCase(
    private val voiceFileStorage: VoiceFileStorage
) {
    suspend operator fun invoke(): Int = voiceFileStorage.cleanupAbandonedTempFiles()
}

/**
 * Toggles ONLY `isAddressed` on a committed recall (double-tap on expanded page or TalkBack action).
 */
class ToggleAddressedUseCase(
    private val recallRepository: RecallRepository,
    private val widgetRefresher: WidgetRefresher
) {
    suspend operator fun invoke(recallId: String): AppResult<Boolean> {
        val result = recallRepository.toggleAddressed(recallId)
        if (result is AppResult.Success) {
            widgetRefresher.refreshWidgets()
        }
        return result
    }
}

/**
 * Toggles ONLY `isFavourite` on a committed recall (tapping the doodle heart).
 */
class ToggleFavouriteUseCase(
    private val recallRepository: RecallRepository,
    private val widgetRefresher: WidgetRefresher
) {
    suspend operator fun invoke(recallId: String): AppResult<Boolean> {
        val result = recallRepository.toggleFavourite(recallId)
        if (result is AppResult.Success) {
            widgetRefresher.refreshWidgets()
        }
        return result
    }
}

/**
 * Applies a RecallFilterState without mutating underlying records or regrouping dates.
 */
class ApplyRecallFilterUseCase {
    operator fun invoke(recalls: List<Recall>, filterState: RecallFilterState): List<Recall> {
        if (!filterState.isActive) return recalls
        return recalls.filter { filterState.matches(it) }
    }
}

/**
 * Returns the latest N (default 19) recalls in newest-first order for the launcher widget.
 * Per 06_WIDGET_SPEC.md:
 * - Includes all types (TEXT and VOICE) and all statuses (addressed, unaddressed, favourite).
 * - Never deletes or mutates older recalls when the 20th recall pushes the oldest out of the 19-item window.
 */
class GetLatestWidgetRecallsUseCase(
    private val recallRepository: RecallRepository
) {
    companion object {
        const val WIDGET_MAX_ITEMS = 19
    }

    suspend operator fun invoke(limit: Int = WIDGET_MAX_ITEMS): List<Recall> {
        return recallRepository.getLatestWidgetRecalls(limit.coerceIn(1, WIDGET_MAX_ITEMS))
    }
}

/**
 * Centralised hierarchy builder that derives Year -> Month -> Day stack projections from
 * the immutable `capturedLocalDate` field without creating duplicate archive rows.
 *
 * Rules from 02_REQUIREMENTS_AND_RULES.md, 03_INFORMATION_ARCHITECTURE.md, and 14_IMPLEMENTATION_ASSUMPTIONS.md:
 * - Main screen shows only the current calendar year (`currentYear`).
 * - Active current month (`currentMonth` in `currentYear`) is represented directly by its day stacks.
 * - Completed months (`month < currentMonth` in `currentYear`, or any month in prior years) are represented
 *   by a month stack labelled `MM`.
 * - Tapping a month stack deals its day stacks into the 3-column grid one by one; the original month cell
 *   becomes a single flat page labelled `MM` (`FlatMonthCollapseCell`). Tapping that flat page collapses it.
 * - Tapping a day stack opens its newest recall; vertical page swipes browse that day's records
 *   (isolated in `resolveDayStackEntrySequence` so it can be changed easily if the owner revises it).
 */
class GetYearMonthDayGroupsUseCase(
    private val applyRecallFilter: ApplyRecallFilterUseCase = ApplyRecallFilterUseCase()
) {

    /**
     * Builds the Main screen 3-column grid cells for the current calendar year.
     */
    fun buildMainScreenGridCells(
        allRecalls: List<Recall>,
        currentYear: Int,
        currentMonth: Int,
        expandedMonthsInMain: Set<Int>,
        activeDayFilter: RecallFilterState
    ): List<StackGridCell> {
        val currentYearRecalls = allRecalls
            .filter { it.year == currentYear }
            .sortedByDescending { it.createdAtEpochMillis }

        val monthGroups = groupRecallsIntoMonths(
            recalls = currentYearRecalls,
            currentYear = currentYear,
            currentMonth = currentMonth
        )

        val cells = mutableListOf<StackGridCell>()
        for (monthGroup in monthGroups) {
            if (monthGroup.isCurrentActiveMonth) {
                // Active month shows day stacks directly; apply day-stack filter projection
                val filteredDays = filterDayStacks(monthGroup.dayStacksNewestFirst, activeDayFilter)
                filteredDays.forEachIndexed { index, day ->
                    cells.add(
                        StackGridCell.DayStackCell(
                            dayStack = day,
                            parentExpandedMonth = null,
                            dealOrderIndex = index
                        )
                    )
                }
            } else if (expandedMonthsInMain.contains(monthGroup.month)) {
                // Completed month is expanded: original cell stays as flat MM page, followed by dealt day stacks
                cells.add(StackGridCell.FlatMonthCollapseCell(monthGroup))
                val filteredDays = filterDayStacks(monthGroup.dayStacksNewestFirst, activeDayFilter)
                filteredDays.forEachIndexed { index, day ->
                    cells.add(
                        StackGridCell.DayStackCell(
                            dayStack = day,
                            parentExpandedMonth = monthGroup.month,
                            dealOrderIndex = index + 1
                        )
                    )
                }
            } else {
                // Completed month is collapsed: show layered MM stack (unfiltered at month level per rule)
                cells.add(StackGridCell.MonthStackCell(monthGroup))
            }
        }
        return cells
    }

    /**
     * Builds the Archive Year stacks (`'26`, `'25`, etc.), newest year first.
     */
    fun buildArchiveYearStacks(
        allRecalls: List<Recall>,
        currentYear: Int,
        currentMonth: Int
    ): List<YearStackGroup> {
        val byYear = allRecalls
            .sortedByDescending { it.createdAtEpochMillis }
            .groupBy { it.year }

        return byYear.entries
            .sortedByDescending { it.key }
            .map { (year, yearRecalls) ->
                YearStackGroup(
                    year = year,
                    monthStacksNewestFirst = groupRecallsIntoMonths(
                        recalls = yearRecalls,
                        currentYear = currentYear,
                        currentMonth = currentMonth
                    )
                )
            }
    }

    /**
     * Builds the 3-column grid cells inside a selected Archive Year (`MM` stacks + dealt day stacks).
     */
    fun buildArchiveYearMonthGridCells(
        yearStack: YearStackGroup,
        expandedMonthsInArchiveYear: Set<Int>,
        archiveDayFilter: RecallFilterState
    ): List<StackGridCell> {
        val cells = mutableListOf<StackGridCell>()
        for (monthGroup in yearStack.monthStacksNewestFirst) {
            if (expandedMonthsInArchiveYear.contains(monthGroup.month)) {
                cells.add(StackGridCell.FlatMonthCollapseCell(monthGroup))
                val filteredDays = filterDayStacks(monthGroup.dayStacksNewestFirst, archiveDayFilter)
                filteredDays.forEachIndexed { index, day ->
                    cells.add(
                        StackGridCell.DayStackCell(
                            dayStack = day,
                            parentExpandedMonth = monthGroup.month,
                            dealOrderIndex = index + 1
                        )
                    )
                }
            } else {
                cells.add(StackGridCell.MonthStackCell(monthGroup))
            }
        }
        return cells
    }

    /**
     * Isolated day-stack entry rule (14_IMPLEMENTATION_ASSUMPTIONS.md):
     * Tapping a day stack opens its newest recall (index 0 in newest-first order),
     * and vertical page swipes browse that day's filtered records (swipe up = older, swipe down = newer).
     */
    fun resolveDayStackEntrySequence(
        dayRecalls: List<Recall>,
        activeFilter: RecallFilterState
    ): DayStackEntrySequence {
        val sortedNewestFirst = applyRecallFilter(dayRecalls, activeFilter)
            .sortedByDescending { it.createdAtEpochMillis }
        return DayStackEntrySequence(
            recallsNewestFirst = sortedNewestFirst,
            initialRecallId = sortedNewestFirst.firstOrNull()?.id,
            initialIndex = 0
        )
    }

    private fun groupRecallsIntoMonths(
        recalls: List<Recall>,
        currentYear: Int,
        currentMonth: Int
    ): List<MonthStackGroup> {
        val byMonth = recalls.groupBy { it.month }
        return byMonth.entries
            .sortedByDescending { it.key }
            .mapNotNull { (month, monthRecalls) ->
                val dayStacks = groupRecallsIntoDays(monthRecalls)
                if (dayStacks.isEmpty()) {
                    null
                } else {
                    val year = monthRecalls.first().year
                    MonthStackGroup(
                        year = year,
                        month = month,
                        isCurrentActiveMonth = (year == currentYear && month == currentMonth),
                        dayStacksNewestFirst = dayStacks
                    )
                }
            }
    }

    fun groupRecallsIntoDays(recalls: List<Recall>): List<DayStackGroup> {
        val byDate = recalls.groupBy { it.capturedLocalDate }
        return byDate.entries
            .sortedByDescending { it.key }
            .mapNotNull { (dateIso, dayRecalls) ->
                val sorted = dayRecalls.sortedByDescending { it.createdAtEpochMillis }
                val sample = sorted.firstOrNull() ?: return@mapNotNull null
                DayStackGroup(
                    capturedLocalDate = dateIso,
                    year = sample.year,
                    month = sample.month,
                    dayOfMonth = sample.dayOfMonth,
                    recallsNewestFirst = sorted
                )
            }
    }

    /**
     * Per 14_IMPLEMENTATION_ASSUMPTIONS.md:
     * "Hide a day card when no recall within that day matches the active filter; preserve the underlying
     * day/month/year grouping and all data. Clearing the filter restores the card."
     */
    fun filterDayStacks(
        dayStacks: List<DayStackGroup>,
        filterState: RecallFilterState
    ): List<DayStackGroup> {
        if (!filterState.isActive) return dayStacks
        return dayStacks.mapNotNull { dayStack ->
            val matching = applyRecallFilter(dayStack.recallsNewestFirst, filterState)
            if (matching.isEmpty()) {
                null
            } else {
                dayStack.copy(recallsNewestFirst = matching)
            }
        }
    }
}

data class DayStackEntrySequence(
    val recallsNewestFirst: List<Recall>,
    val initialRecallId: String?,
    val initialIndex: Int
)
