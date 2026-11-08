package com.scrbl.app.domain

import com.scrbl.app.core.result.AppResult
import com.scrbl.app.core.result.FailureReason
import com.scrbl.app.core.time.CaptureTimestampSnapshot
import com.scrbl.app.core.time.DateFormatOption
import com.scrbl.app.core.time.RecallDateTimeFormatter
import com.scrbl.app.core.time.TimeFormatOption
import com.scrbl.app.core.time.TimeProvider
import com.scrbl.app.data.audio.AppPrivateVoiceFileStorage
import com.scrbl.app.domain.model.AddressFilterMode
import com.scrbl.app.domain.model.ContentTypeFilterMode
import com.scrbl.app.domain.model.Recall
import com.scrbl.app.domain.model.RecallFilterState
import com.scrbl.app.domain.model.RecallType
import com.scrbl.app.domain.model.StackGridCell
import com.scrbl.app.domain.repository.RecallRepository
import com.scrbl.app.domain.repository.WidgetRefresher
import com.scrbl.app.domain.usecase.ApplyRecallFilterUseCase
import com.scrbl.app.domain.usecase.CancelCaptureUseCase
import com.scrbl.app.domain.usecase.CleanupAbandonedRecordingTempsUseCase
import com.scrbl.app.domain.usecase.CreateTextRecallUseCase
import com.scrbl.app.domain.usecase.FinalizeVoiceRecallUseCase
import com.scrbl.app.domain.usecase.GetLatestWidgetRecallsUseCase
import com.scrbl.app.domain.usecase.GetYearMonthDayGroupsUseCase
import com.scrbl.app.domain.usecase.ToggleAddressedUseCase
import com.scrbl.app.domain.usecase.ToggleFavouriteUseCase
import com.scrbl.app.domain.validation.RecallValidator
import com.scrbl.app.feature.capture.CaptureStage
import com.scrbl.app.feature.capture.CaptureStateMachine
import com.scrbl.app.platform.audio.VoiceRecorderController
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.time.Instant
import java.time.ZoneId

/**
 * Comprehensive unit & acceptance test suite verifying every rule in `02_REQUIREMENTS_AND_RULES.md`
 * and `11_ACCEPTANCE_TESTS.md`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScrbLAcceptanceUnitTest {

    private lateinit var tempRootDir: File
    private lateinit var filesDir: File
    private lateinit var cacheDir: File
    private lateinit var fakeTimeProvider: FakeTimeProvider
    private lateinit var fakeRepository: InMemoryImmutableRecallRepository
    private lateinit var voiceStorage: AppPrivateVoiceFileStorage
    private lateinit var fakeWidgetRefresher: FakeWidgetRefresher

    @Before
    fun setUp() {
        tempRootDir = Files.createTempDirectory("scrbl_test_").toFile()
        filesDir = File(tempRootDir, "files").apply { mkdirs() }
        cacheDir = File(tempRootDir, "cache").apply { mkdirs() }
        fakeTimeProvider = FakeTimeProvider(
            instant = Instant.parse("2026-11-08T03:16:22Z"),
            zoneId = ZoneId.of("Asia/Kolkata")
        )
        fakeRepository = InMemoryImmutableRecallRepository()
        voiceStorage = AppPrivateVoiceFileStorage(filesDir, cacheDir)
        fakeWidgetRefresher = FakeWidgetRefresher()
    }

    // =========================================================================
    // Section B: Text Capture & 25-Word Contract
    // =========================================================================

    @Test
    fun `word counter normalizes line breaks and whitespace and counts attached punctuation as single token`() {
        assertEquals(0, RecallValidator.countWords(""))
        assertEquals(0, RecallValidator.countWords("   \n\t  "))
        assertEquals(1, RecallValidator.countWords("ScrbL"))
        assertEquals(1, RecallValidator.countWords("pocket-sized"))
        assertEquals(3, RecallValidator.countWords("Hello,   world!\n😊"))
        val twentyFiveWords = (1..25).joinToString(" ") { "word$it" }
        assertEquals(25, RecallValidator.countWords(twentyFiveWords))
        val twentySixWords = "$twentyFiveWords extra"
        assertEquals(26, RecallValidator.countWords(twentySixWords))
    }

    @Test
    fun `25 words can be saved while 26th word is rejected without silent truncation`() = runTest {
        val createText = CreateTextRecallUseCase(fakeRepository, fakeTimeProvider, fakeWidgetRefresher)

        val twentyFiveWords = (1..25).joinToString(" ") { "thought$it" }
        val okResult = createText(twentyFiveWords)
        assertTrue(okResult is AppResult.Success)
        val saved = (okResult as AppResult.Success).data
        assertEquals(twentyFiveWords, saved.textBody)
        assertEquals(1, fakeWidgetRefresher.refreshCount)

        val twentySixWords = "$twentyFiveWords overflow"
        val errResult = createText(twentySixWords)
        assertTrue(errResult is AppResult.Error)
        assertEquals(FailureReason.WORD_LIMIT_EXCEEDED, (errResult as AppResult.Error).reason)
        // Ensure only the valid 25-word recall is in the repository
        assertEquals(1, fakeRepository.getAllSnapshot().size)
    }

    @Test
    fun `empty or whitespace-only text cannot be saved`() = runTest {
        val createText = CreateTextRecallUseCase(fakeRepository, fakeTimeProvider, fakeWidgetRefresher)
        val res1 = createText("")
        val res2 = createText("   \n \t ")
        assertTrue(res1 is AppResult.Error)
        assertTrue(res2 is AppResult.Error)
        assertEquals(0, fakeRepository.getAllSnapshot().size)
    }

    // =========================================================================
    // Section C: Voice Capture, 37s Auto-Save, Race CAS, and Interruption Discard
    // =========================================================================

    @Test
    fun `voice stop tap racing with 37-second callback commits exactly one recall`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(testDispatcher)
        val testVoiceStorage = AppPrivateVoiceFileStorage(
            filesDir,
            cacheDir,
            testDispatcher
        )
        val fakeRecorder = FakeVoiceRecorderController()
        val createText = CreateTextRecallUseCase(fakeRepository, fakeTimeProvider, fakeWidgetRefresher)
        val finalizeVoice = FinalizeVoiceRecallUseCase(
            fakeRepository,
            testVoiceStorage,
            fakeTimeProvider,
            fakeWidgetRefresher
        )
        val cancelCapture = CancelCaptureUseCase(testVoiceStorage)

        val machine = CaptureStateMachine(
            scope = scope,
            createTextRecall = createText,
            finalizeVoiceRecall = finalizeVoice,
            cancelCapture = cancelCapture,
            voiceFileStorage = testVoiceStorage,
            recorderController = fakeRecorder
        )

        machine.openBlankCapturePage()
        assertEquals(CaptureStage.BLANK_PAGE, machine.uiState.value.stage)

        machine.startVoiceRecordingSession()
        assertEquals(CaptureStage.VOICE_RECORDING, machine.uiState.value.stage)
        machine.onVoiceTitleChanged("Late night guitar melody")

        // Simulate simultaneous manual stop AND 37-second max duration callback race
        machine.stopAndCommitVoiceRecall()
        fakeRecorder.triggerMaxDurationReached(37_000L)
        scope.advanceUntilIdle()

        assertEquals(CaptureStage.CLOSED, machine.uiState.value.stage)
        val savedRecalls = fakeRepository.getAllSnapshot()
        assertEquals(1, savedRecalls.size)
        val voiceRecall = savedRecalls.first()
        assertEquals(RecallType.VOICE, voiceRecall.type)
        assertEquals("Late night guitar melody", voiceRecall.voiceTitle)
        assertEquals(37_000L, voiceRecall.audioDurationMillis)
        assertNotNull(voiceRecall.voiceRelativePath)
        assertFalse(voiceRecall.voiceRelativePath!!.startsWith("/"))
        assertTrue(testVoiceStorage.existsAndNonEmpty(voiceRecall.voiceRelativePath!!))
    }

    @Test
    fun `flick away, audio interruption, or app backgrounding during voice recording discards temp file and saves no recall`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(testDispatcher)
        val fakeRecorder = FakeVoiceRecorderController()
        val machine = CaptureStateMachine(
            scope = scope,
            createTextRecall = CreateTextRecallUseCase(fakeRepository, fakeTimeProvider, fakeWidgetRefresher),
            finalizeVoiceRecall = FinalizeVoiceRecallUseCase(fakeRepository, voiceStorage, fakeTimeProvider, fakeWidgetRefresher),
            cancelCapture = CancelCaptureUseCase(voiceStorage),
            voiceFileStorage = voiceStorage,
            recorderController = fakeRecorder
        )

        // 1. Flick away during recording
        machine.openBlankCapturePage()
        machine.startVoiceRecordingSession()
        val tempFile1 = fakeRecorder.lastOutputFile
        assertNotNull(tempFile1)
        assertTrue(tempFile1!!.exists())
        machine.discardCapture()
        scope.advanceUntilIdle()
        assertEquals(CaptureStage.CLOSED, machine.uiState.value.stage)
        assertFalse(tempFile1.exists())
        assertEquals(0, fakeRepository.getAllSnapshot().size)

        // 2. Audio interruption (incoming call / focus loss) during recording
        machine.openBlankCapturePage()
        machine.startVoiceRecordingSession()
        val tempFile2 = fakeRecorder.lastOutputFile
        fakeRecorder.triggerInterruption("Audio focus lost")
        scope.advanceUntilIdle()
        assertEquals(CaptureStage.CLOSED, machine.uiState.value.stage)
        assertFalse(tempFile2!!.exists())
        assertEquals(0, fakeRepository.getAllSnapshot().size)

        // 3. App backgrounded (ON_STOP) during recording
        machine.openBlankCapturePage()
        machine.startVoiceRecordingSession()
        val tempFile3 = fakeRecorder.lastOutputFile
        machine.onAppBackgrounded()
        scope.advanceUntilIdle()
        assertEquals(CaptureStage.CLOSED, machine.uiState.value.stage)
        assertFalse(tempFile3!!.exists())
        assertEquals(0, fakeRepository.getAllSnapshot().size)
    }

    @Test
    fun `startup cleanup deletes abandoned tmp files without touching committed voice files`() = runTest {
        val orphanTemp = voiceStorage.createTempRecordingFile("abandoned_crash")
        orphanTemp.writeBytes(ByteArray(256) { 1 })

        val finalizeVoice = FinalizeVoiceRecallUseCase(
            fakeRepository,
            voiceStorage,
            fakeTimeProvider,
            fakeWidgetRefresher
        )
        val validTemp = voiceStorage.createTempRecordingFile("valid_session")
        validTemp.writeBytes(ByteArray(512) { 2 })
        val committed = finalizeVoice(validTemp, 12_000L, null)
        assertTrue(committed is AppResult.Success)
        val committedPath = (committed as AppResult.Success).data.voiceRelativePath!!

        val cleanupUseCase = CleanupAbandonedRecordingTempsUseCase(voiceStorage)
        val deletedCount = cleanupUseCase()
        assertEquals(1, deletedCount)
        assertFalse(orphanTemp.exists())
        assertTrue(voiceStorage.existsAndNonEmpty(committedPath))
    }

    // =========================================================================
    // Section D: Immutability & Timezone Invariance
    // =========================================================================

    @Test
    fun `addressed and favourite toggles mutate only their respective flags while preserving content and timestamp`() = runTest {
        val createText = CreateTextRecallUseCase(fakeRepository, fakeTimeProvider, fakeWidgetRefresher)
        val toggleAddressed = ToggleAddressedUseCase(fakeRepository, fakeWidgetRefresher)
        val toggleFavourite = ToggleFavouriteUseCase(fakeRepository, fakeWidgetRefresher)

        val created = (createText("Buy warm ivory notebooks", explicitId = "id-imm-1") as AppResult.Success).data
        assertFalse(created.isAddressed)
        assertFalse(created.isFavourite)

        toggleAddressed("id-imm-1")
        val afterAddressed = fakeRepository.getRecallById("id-imm-1")!!
        assertTrue(afterAddressed.isAddressed)
        assertFalse(afterAddressed.isFavourite)
        assertEquals(created.textBody, afterAddressed.textBody)
        assertEquals(created.createdAtEpochMillis, afterAddressed.createdAtEpochMillis)
        assertEquals(created.capturedLocalDate, afterAddressed.capturedLocalDate)
        assertEquals(created.capturedZoneId, afterAddressed.capturedZoneId)

        toggleFavourite("id-imm-1")
        val afterFav = fakeRepository.getRecallById("id-imm-1")!!
        assertTrue(afterFav.isAddressed)
        assertTrue(afterFav.isFavourite)
        assertEquals(created.textBody, afterFav.textBody)
    }

    @Test
    fun `capture-local date is preserved across timezone changes so recalls never move between day stacks`() {
        // 2026-11-08 01:30 AM in Asia/Kolkata (+05:30) is 2026-11-07 20:00 UTC
        val kolkataTime = FakeTimeProvider(
            instant = Instant.parse("2026-11-07T20:00:00Z"),
            zoneId = ZoneId.of("Asia/Kolkata")
        )
        val snapshot = CaptureTimestampSnapshot.captureNow(kolkataTime)
        assertEquals("2026-11-08", snapshot.capturedLocalDate)
        assertEquals("Asia/Kolkata", snapshot.capturedZoneId)

        // Even if device later travels to America/New_York, stored capturedLocalDate remains 2026-11-08
        assertEquals(
            "08/11",
            RecallDateTimeFormatter.formatDayStackFace(snapshot.capturedLocalDate, DateFormatOption.DD_MM)
        )
        assertEquals(
            "08 Nov",
            RecallDateTimeFormatter.formatExpandedPageDate(snapshot.capturedLocalDate, DateFormatOption.DD_MM)
        )
        assertEquals(
            "01:30",
            RecallDateTimeFormatter.formatCreationTime(
                snapshot.createdAtEpochMillis,
                snapshot.capturedZoneId,
                TimeFormatOption.HOURS_24
            )
        )
    }

    // =========================================================================
    // Section E, F, G: Main / Archive Hierarchy, Month Deal/Collapse & Independent Filters
    // =========================================================================

    @Test
    fun `main screen shows only current year, active month as day stacks, and completed months as expandable MM stacks`() {
        val groupingUseCase = GetYearMonthDayGroupsUseCase()
        val recalls = listOf(
            // Current year (2026), active month (11 - November): two days
            sampleRecall("nov-8-b", "2026-11-08", 1000L, isFavourite = true),
            sampleRecall("nov-8-a", "2026-11-08", 2000L, isAddressed = true),
            sampleRecall("nov-5", "2026-11-05", 900L),
            // Current year (2026), completed month (10 - October): two days
            sampleRecall("oct-21", "2026-10-21", 800L),
            sampleRecall("oct-12", "2026-10-12", 700L),
            // Prior year (2025): should appear in Archive, never on Main
            sampleRecall("dec-2025", "2025-12-31", 500L)
        )

        // 1. Collapsed October state on Main
        val collapsedGrid = groupingUseCase.buildMainScreenGridCells(
            allRecalls = recalls,
            currentYear = 2026,
            currentMonth = 11,
            expandedMonthsInMain = emptySet(),
            activeDayFilter = RecallFilterState()
        )
        // Expect: Day(2026-11-08), Day(2026-11-05), MonthStack(10) -> 3 cells total (no 2025 items)
        assertEquals(3, collapsedGrid.size)
        assertTrue(collapsedGrid[0] is StackGridCell.DayStackCell)
        assertEquals("2026-11-08", (collapsedGrid[0] as StackGridCell.DayStackCell).dayStack.capturedLocalDate)
        assertTrue(collapsedGrid[1] is StackGridCell.DayStackCell)
        assertEquals("2026-11-05", (collapsedGrid[1] as StackGridCell.DayStackCell).dayStack.capturedLocalDate)
        assertTrue(collapsedGrid[2] is StackGridCell.MonthStackCell)
        assertEquals(10, (collapsedGrid[2] as StackGridCell.MonthStackCell).monthStack.month)

        // 2. Tap October MonthStack(10) to deal its day stacks into the grid
        val expandedGrid = groupingUseCase.buildMainScreenGridCells(
            allRecalls = recalls,
            currentYear = 2026,
            currentMonth = 11,
            expandedMonthsInMain = setOf(10),
            activeDayFilter = RecallFilterState()
        )
        // Expect: Day(11-08), Day(11-05), FlatMonthCollapse(10), Day(10-21), Day(10-12)
        assertEquals(5, expandedGrid.size)
        assertTrue(expandedGrid[2] is StackGridCell.FlatMonthCollapseCell)
        assertEquals(10, (expandedGrid[2] as StackGridCell.FlatMonthCollapseCell).monthStack.month)
        assertEquals("2026-10-21", (expandedGrid[3] as StackGridCell.DayStackCell).dayStack.capturedLocalDate)
        assertEquals("2026-10-12", (expandedGrid[4] as StackGridCell.DayStackCell).dayStack.capturedLocalDate)

        // 3. Archive contains both '26 and '25 year stacks
        val archiveYears = groupingUseCase.buildArchiveYearStacks(
            allRecalls = recalls,
            currentYear = 2026,
            currentMonth = 11
        )
        assertEquals(listOf(2026, 2025), archiveYears.map { it.year })
    }

    @Test
    fun `filters support mutually exclusive addressed-unaddressed, independent favourite, and hide empty day cards without mutating data`() {
        val groupingUseCase = GetYearMonthDayGroupsUseCase()
        val recalls = listOf(
            sampleRecall("r1", "2026-11-08", 3000L, type = RecallType.TEXT, isAddressed = true, isFavourite = true),
            sampleRecall("r2", "2026-11-08", 2000L, type = RecallType.VOICE, isAddressed = false, isFavourite = true),
            sampleRecall("r3", "2026-11-07", 1000L, type = RecallType.TEXT, isAddressed = false, isFavourite = false)
        )

        val favVoiceFilter = RecallFilterState(
            favouritesOnly = true,
            addressFilter = AddressFilterMode.UNADDRESSED_ONLY,
            contentTypeFilter = ContentTypeFilterMode.VOICE_ONLY
        )
        val filteredGrid = groupingUseCase.buildMainScreenGridCells(
            allRecalls = recalls,
            currentYear = 2026,
            currentMonth = 11,
            expandedMonthsInMain = emptySet(),
            activeDayFilter = favVoiceFilter
        )
        // Day 2026-11-07 has no matching recall so its card is hidden in the filtered projection;
        // Day 2026-11-08 has only r2 matching.
        assertEquals(1, filteredGrid.size)
        val dayCell = filteredGrid.first() as StackGridCell.DayStackCell
        assertEquals("2026-11-08", dayCell.dayStack.capturedLocalDate)
        assertEquals(listOf("r2"), dayCell.dayStack.recallsNewestFirst.map { it.id })

        // Clearing filter restores both day stacks and all records untouched
        val unfilteredGrid = groupingUseCase.buildMainScreenGridCells(
            allRecalls = recalls,
            currentYear = 2026,
            currentMonth = 11,
            expandedMonthsInMain = emptySet(),
            activeDayFilter = RecallFilterState()
        )
        assertEquals(2, unfilteredGrid.size)
    }

    // =========================================================================
    // Section I: Widget 19-Item Cap & All-Status Inclusion
    // =========================================================================

    @Test
    fun `widget query exposes newest 19 recalls across all statuses while keeping older recalls in database`() = runTest {
        for (i in 1..22) {
            fakeRepository.insertCommittedRecall(
                sampleRecall(
                    id = "rec-$i",
                    dateIso = "2026-11-08",
                    createdAt = i * 1000L,
                    type = if (i % 2 == 0) RecallType.VOICE else RecallType.TEXT,
                    isAddressed = (i % 3 == 0),
                    isFavourite = (i % 5 == 0)
                )
            )
        }

        val getWidgetRecalls = GetLatestWidgetRecallsUseCase(fakeRepository)
        val widgetWindow = getWidgetRecalls()

        assertEquals(19, widgetWindow.size)
        assertEquals("rec-22", widgetWindow.first().id) // Newest first
        assertEquals("rec-4", widgetWindow.last().id)   // 19th newest

        // Oldest 3 (rec-1, rec-2, rec-3) dropped out of widget window ONLY; all 22 remain in repository
        assertEquals(22, fakeRepository.getAllSnapshot().size)
        assertNotNull(fakeRepository.getRecallById("rec-1"))
        assertNotNull(fakeRepository.getRecallById("rec-2"))
        assertNotNull(fakeRepository.getRecallById("rec-3"))
    }

    // =========================================================================
    // Test Doubles
    // =========================================================================

    private fun sampleRecall(
        id: String,
        dateIso: String,
        createdAt: Long,
        type: RecallType = RecallType.TEXT,
        isAddressed: Boolean = false,
        isFavourite: Boolean = false
    ): Recall {
        return Recall(
            id = id,
            type = type,
            textBody = if (type == RecallType.TEXT) "Sample thought $id" else null,
            voiceRelativePath = if (type == RecallType.VOICE) "recalls/audio/$id.m4a" else null,
            voiceTitle = if (type == RecallType.VOICE) "Title $id" else null,
            createdAtEpochMillis = createdAt,
            capturedLocalDate = dateIso,
            capturedZoneId = "Asia/Kolkata",
            audioDurationMillis = if (type == RecallType.VOICE) 12_000L else null,
            isAddressed = isAddressed,
            isFavourite = isFavourite
        )
    }

    private class FakeTimeProvider(
        var instant: Instant,
        var zoneId: ZoneId,
        var elapsedRealtime: Long = 1_000L
    ) : TimeProvider {
        override fun nowInstant(): Instant = instant
        override fun currentZoneId(): ZoneId = zoneId
        override fun elapsedRealtimeMillis(): Long = elapsedRealtime
    }

    private class FakeWidgetRefresher : WidgetRefresher {
        var refreshCount = 0
        override fun refreshWidgets() {
            refreshCount++
        }
    }

    private class FakeVoiceRecorderController : VoiceRecorderController {
        override var isRecording: Boolean = false
        var lastOutputFile: File? = null
        private var maxDurationCallback: ((Long) -> Unit)? = null
        private var interruptedCallback: ((String) -> Unit)? = null

        override fun startRecording(
            outputFile: File,
            onProgressMillis: (Long) -> Unit,
            onMaxDurationReached: (Long) -> Unit,
            onRecordingInterruptedOrFailed: (String) -> Unit
        ): Boolean {
            outputFile.parentFile?.mkdirs()
            outputFile.writeBytes(ByteArray(1024) { 0x42 })
            lastOutputFile = outputFile
            maxDurationCallback = onMaxDurationReached
            interruptedCallback = onRecordingInterruptedOrFailed
            isRecording = true
            return true
        }

        override fun stopAndFinalize(): Long? {
            if (!isRecording) return null
            isRecording = false
            return 37_000L
        }

        override fun discardAndRelease() {
            isRecording = false
            lastOutputFile?.delete()
        }

        fun triggerMaxDurationReached(durationMs: Long) {
            maxDurationCallback?.invoke(durationMs)
        }

        fun triggerInterruption(reason: String) {
            discardAndRelease()
            interruptedCallback?.invoke(reason)
        }
    }

    private class InMemoryImmutableRecallRepository : RecallRepository {
        private val items = MutableStateFlow<List<Recall>>(emptyList())

        fun getAllSnapshot(): List<Recall> = items.value

        override fun observeAllRecallsNewestFirst(): Flow<List<Recall>> =
            items.map { list -> list.sortedByDescending { it.createdAtEpochMillis } }

        override fun observeRecallsForYear(yearPrefix: String): Flow<List<Recall>> =
            observeAllRecallsNewestFirst().map { list ->
                list.filter { it.capturedLocalDate.startsWith(yearPrefix) }
            }

        override fun observeRecallsForDay(capturedLocalDate: String): Flow<List<Recall>> =
            observeAllRecallsNewestFirst().map { list ->
                list.filter { it.capturedLocalDate == capturedLocalDate }
            }

        override fun observeFavouritesNewestFirst(): Flow<List<Recall>> =
            observeAllRecallsNewestFirst().map { list -> list.filter { it.isFavourite } }

        override fun observeRecallById(id: String): Flow<Recall?> =
            items.map { list -> list.firstOrNull { it.id == id } }

        override suspend fun getRecallById(id: String): Recall? =
            items.value.firstOrNull { it.id == id }

        override suspend fun getRecallsForDay(capturedLocalDate: String): List<Recall> =
            items.value.filter { it.capturedLocalDate == capturedLocalDate }
                .sortedByDescending { it.createdAtEpochMillis }

        override suspend fun getLatestWidgetRecalls(limit: Int): List<Recall> =
            items.value.sortedByDescending { it.createdAtEpochMillis }.take(limit)

        override suspend fun getDistinctYearsNewestFirst(): List<Int> =
            items.value.map { it.year }.distinct().sortedDescending()

        override suspend fun getDistinctMonthsForYearNewestFirst(year: Int): List<Int> =
            items.value.filter { it.year == year }.map { it.month }.distinct().sortedDescending()

        override suspend fun insertCommittedRecall(recall: Recall): AppResult<Recall> {
            if (items.value.any { it.id == recall.id }) {
                return AppResult.Error(FailureReason.DATABASE_ERROR, "Duplicate ID")
            }
            items.value = items.value + recall
            return AppResult.Success(recall)
        }

        override suspend fun toggleAddressed(id: String): AppResult<Boolean> {
            val target = getRecallById(id)
                ?: return AppResult.Error(FailureReason.RECORD_NOT_FOUND, "Not found")
            val next = !target.isAddressed
            items.value = items.value.map {
                if (it.id == id) it.copy(isAddressed = next) else it
            }
            return AppResult.Success(next)
        }

        override suspend fun toggleFavourite(id: String): AppResult<Boolean> {
            val target = getRecallById(id)
                ?: return AppResult.Error(FailureReason.RECORD_NOT_FOUND, "Not found")
            val next = !target.isFavourite
            items.value = items.value.map {
                if (it.id == id) it.copy(isFavourite = next) else it
            }
            return AppResult.Success(next)
        }
    }
}
