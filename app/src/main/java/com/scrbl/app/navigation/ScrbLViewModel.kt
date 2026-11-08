package com.scrbl.app.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.scrbl.app.core.time.DateFormatOption
import com.scrbl.app.core.time.TimeFormatOption
import com.scrbl.app.core.time.TimeProvider
import com.scrbl.app.domain.model.DayStackGroup
import com.scrbl.app.domain.model.FontFamilyOption
import com.scrbl.app.domain.model.FontScaleOption
import com.scrbl.app.domain.model.PaperEdgeOption
import com.scrbl.app.domain.model.PaperTextureOption
import com.scrbl.app.domain.model.Recall
import com.scrbl.app.domain.model.RecallFilterState
import com.scrbl.app.domain.model.ShadowStyleOption
import com.scrbl.app.domain.model.StackGridCell
import com.scrbl.app.domain.model.UserPreferences
import com.scrbl.app.domain.model.VoiceCharacterOption
import com.scrbl.app.domain.model.WidgetSpiralOption
import com.scrbl.app.domain.model.YearStackGroup
import com.scrbl.app.domain.repository.PreferencesRepository
import com.scrbl.app.domain.repository.RecallRepository
import com.scrbl.app.domain.repository.VoiceFileStorage
import com.scrbl.app.domain.usecase.CancelCaptureUseCase
import com.scrbl.app.domain.usecase.CreateTextRecallUseCase
import com.scrbl.app.domain.usecase.FinalizeVoiceRecallUseCase
import com.scrbl.app.domain.usecase.GetLatestWidgetRecallsUseCase
import com.scrbl.app.domain.usecase.GetYearMonthDayGroupsUseCase
import com.scrbl.app.domain.usecase.ToggleAddressedUseCase
import com.scrbl.app.domain.usecase.ToggleFavouriteUseCase
import com.scrbl.app.feature.capture.CaptureStateMachine
import com.scrbl.app.feature.capture.CaptureUiState
import com.scrbl.app.platform.audio.VoiceRecorderController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class NotebookUiState(
    val currentDestination: AppDestination = AppDestination.MainCurrentYear,
    val preferences: UserPreferences = UserPreferences(),
    val mainGridCells: List<StackGridCell> = emptyList(),
    val hasAnyDayStacksInMain: Boolean = false,
    val archiveYearStacks: List<YearStackGroup> = emptyList(),
    val selectedArchiveYear: Int? = null,
    val archiveYearGridCells: List<StackGridCell> = emptyList(),
    val hasExpandedMonthsInSelectedArchiveYear: Boolean = false,
    val favouriteRecallsNewestFirst: List<Recall> = emptyList(),
    val expandedViewerContext: ExpandedViewerContext? = null,
    val expandedSequenceRecalls: List<Recall> = emptyList(),
    val expandedInitialRecallId: String? = null,
    val isExpandedWidgetOrigin: Boolean = false,
    val activePlayingRecallId: String? = null,
    val playbackErrorMessage: String? = null
)

private data class InternalNavState(
    val destinationBackStack: List<AppDestination> = listOf(AppDestination.MainCurrentYear),
    val expandedMonthsInMain: Set<Int> = emptySet(),
    val selectedArchiveYear: Int? = null,
    val expandedMonthsByArchiveYear: Map<Int, Set<Int>> = emptyMap(),
    val expandedViewerContext: ExpandedViewerContext? = null,
    val activePlayingRecallId: String? = null,
    val playbackErrorMessage: String? = null
)

/**
 * Unidirectional Data Flow (UDF) ViewModel coordinating Main, Archive, Favourites, Settings,
 * Expanded Recall Page, Widget deep-link routing, and Capture state (`08_ANDROID_ARCHITECTURE.md`).
 */
class ScrbLViewModel(
    initialDirective: WidgetDeepLinkHandler.InitialLaunchDirective,
    private val recallRepository: RecallRepository,
    private val preferencesRepository: PreferencesRepository,
    private val voiceFileStorage: VoiceFileStorage,
    private val timeProvider: TimeProvider,
    private val getYearMonthDayGroups: GetYearMonthDayGroupsUseCase,
    private val toggleAddressedUseCase: ToggleAddressedUseCase,
    private val toggleFavouriteUseCase: ToggleFavouriteUseCase,
    createTextRecallUseCase: CreateTextRecallUseCase,
    finalizeVoiceRecallUseCase: FinalizeVoiceRecallUseCase,
    cancelCaptureUseCase: CancelCaptureUseCase,
    recorderController: VoiceRecorderController
) : ViewModel() {

    val captureStateMachine = CaptureStateMachine(
        scope = viewModelScope,
        createTextRecall = createTextRecallUseCase,
        finalizeVoiceRecall = finalizeVoiceRecallUseCase,
        cancelCapture = cancelCaptureUseCase,
        voiceFileStorage = voiceFileStorage,
        recorderController = recorderController
    )

    val captureUiState: StateFlow<CaptureUiState> = captureStateMachine.uiState

    private val internalNavState = MutableStateFlow(
        InternalNavState(
            destinationBackStack = listOf(initialDirective.startDestination),
            expandedViewerContext = when (val start = initialDirective.startDestination) {
                is AppDestination.WidgetOriginDetail ->
                    ExpandedViewerContext.WidgetRecentSequence(start.targetRecallId)
                else -> null
            }
        )
    )

    private val _playbackState = MutableStateFlow<Pair<String?, String?>>(null to null)
    val playbackState: StateFlow<Pair<String?, String?>> = _playbackState.asStateFlow()

    init {
        if (initialDirective.openCaptureImmediately) {
            captureStateMachine.openBlankCapturePage()
        }
    }

    val uiState: StateFlow<NotebookUiState> = combine(
        recallRepository.observeAllRecallsNewestFirst(),
        preferencesRepository.preferencesFlow,
        internalNavState,
        _playbackState
    ) { allRecalls, prefs, nav, playback ->
        val today = timeProvider.currentLocalDate()
        val currentYear = today.year
        val currentMonth = today.monthValue

        val mainGridCells = getYearMonthDayGroups.buildMainScreenGridCells(
            allRecalls = allRecalls,
            currentYear = currentYear,
            currentMonth = currentMonth,
            expandedMonthsInMain = nav.expandedMonthsInMain,
            activeDayFilter = prefs.mainFilterState
        )
        val hasDayStacksInMain = mainGridCells.any { it is StackGridCell.DayStackCell }

        val archiveYears = getYearMonthDayGroups.buildArchiveYearStacks(
            allRecalls = allRecalls,
            currentYear = currentYear,
            currentMonth = currentMonth
        )
        val selectedYearGroup = nav.selectedArchiveYear?.let { yr ->
            archiveYears.firstOrNull { it.year == yr }
        }
        val expandedInArchiveYear = nav.selectedArchiveYear?.let { yr ->
            nav.expandedMonthsByArchiveYear[yr].orEmpty()
        }.orEmpty()

        val archiveGridCells = if (selectedYearGroup != null) {
            getYearMonthDayGroups.buildArchiveYearMonthGridCells(
                yearStack = selectedYearGroup,
                expandedMonthsInArchiveYear = expandedInArchiveYear,
                archiveDayFilter = prefs.archiveFilterState
            )
        } else {
            emptyList()
        }

        val favourites = allRecalls.filter { it.isFavourite }

        // Resolve expanded viewer sequence dynamically from the single source of truth
        val (expandedList, initialId, isWidgetOrigin) = resolveExpandedViewerSequence(
            viewerContext = nav.expandedViewerContext,
            allRecalls = allRecalls,
            favourites = favourites,
            prefs = prefs
        )

        NotebookUiState(
            currentDestination = nav.destinationBackStack.lastOrNull() ?: AppDestination.MainCurrentYear,
            preferences = prefs,
            mainGridCells = mainGridCells,
            hasAnyDayStacksInMain = hasDayStacksInMain,
            archiveYearStacks = archiveYears,
            selectedArchiveYear = nav.selectedArchiveYear,
            archiveYearGridCells = archiveGridCells,
            hasExpandedMonthsInSelectedArchiveYear = expandedInArchiveYear.isNotEmpty(),
            favouriteRecallsNewestFirst = favourites,
            expandedViewerContext = nav.expandedViewerContext,
            expandedSequenceRecalls = expandedList,
            expandedInitialRecallId = initialId,
            isExpandedWidgetOrigin = isWidgetOrigin,
            activePlayingRecallId = playback.first,
            playbackErrorMessage = playback.second
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = NotebookUiState(
            currentDestination = initialDirective.startDestination,
            isExpandedWidgetOrigin = initialDirective.startDestination is AppDestination.WidgetOriginDetail
        )
    )

    fun handleNewWidgetIntent(directive: WidgetDeepLinkHandler.InitialLaunchDirective) {
        if (directive.startDestination is AppDestination.WidgetOriginDetail) {
            internalNavState.update {
                it.copy(
                    destinationBackStack = listOf(directive.startDestination),
                    expandedViewerContext = ExpandedViewerContext.WidgetRecentSequence(
                        directive.startDestination.targetRecallId
                    )
                )
            }
        }
        if (directive.openCaptureImmediately) {
            captureStateMachine.openBlankCapturePage()
        }
    }

    fun navigateTo(destination: AppDestination) {
        internalNavState.update { state ->
            state.copy(
                destinationBackStack = state.destinationBackStack + destination,
                expandedViewerContext = null
            )
        }
    }

    fun navigateBack(): Boolean {
        val current = internalNavState.value
        if (current.expandedViewerContext != null) {
            if (current.expandedViewerContext is ExpandedViewerContext.WidgetRecentSequence) {
                // Closing a direct widget-origin detail view finishes the activity back to launcher
                return false
            }
            internalNavState.update { it.copy(expandedViewerContext = null) }
            return true
        }
        if (current.destinationBackStack.size > 1) {
            internalNavState.update {
                it.copy(destinationBackStack = it.destinationBackStack.dropLast(1))
            }
            return true
        }
        return false
    }

    fun toggleMainMonthExpanded(month: Int) {
        internalNavState.update { state ->
            val next = if (state.expandedMonthsInMain.contains(month)) {
                state.expandedMonthsInMain - month
            } else {
                state.expandedMonthsInMain + month
            }
            state.copy(expandedMonthsInMain = next)
        }
    }

    fun selectArchiveYear(year: Int) {
        internalNavState.update { it.copy(selectedArchiveYear = year) }
    }

    fun exitSelectedArchiveYear() {
        internalNavState.update { it.copy(selectedArchiveYear = null) }
    }

    fun toggleArchiveMonthExpanded(month: Int) {
        internalNavState.update { state ->
            val yr = state.selectedArchiveYear ?: return@update state
            val currentSet = state.expandedMonthsByArchiveYear[yr].orEmpty()
            val nextSet = if (currentSet.contains(month)) currentSet - month else currentSet + month
            state.copy(
                expandedMonthsByArchiveYear = state.expandedMonthsByArchiveYear + (yr to nextSet)
            )
        }
    }

    /**
     * Tapping a day stack opens its newest recall; vertical swipes browse that day's filtered records
     * (`14_IMPLEMENTATION_ASSUMPTIONS.md`).
     */
    fun openMainDayStack(dayStack: DayStackGroup) {
        val entry = getYearMonthDayGroups.resolveDayStackEntrySequence(
            dayRecalls = dayStack.recallsNewestFirst,
            activeFilter = uiState.value.preferences.mainFilterState
        )
        if (entry.recallsNewestFirst.isNotEmpty()) {
            internalNavState.update {
                it.copy(
                    expandedViewerContext = ExpandedViewerContext.MainDaySequence(
                        capturedLocalDate = dayStack.capturedLocalDate,
                        initialRecallId = entry.initialRecallId
                    )
                )
            }
        }
    }

    fun openArchiveDayStack(dayStack: DayStackGroup) {
        val yr = internalNavState.value.selectedArchiveYear ?: dayStack.year
        val entry = getYearMonthDayGroups.resolveDayStackEntrySequence(
            dayRecalls = dayStack.recallsNewestFirst,
            activeFilter = uiState.value.preferences.archiveFilterState
        )
        if (entry.recallsNewestFirst.isNotEmpty()) {
            internalNavState.update {
                it.copy(
                    expandedViewerContext = ExpandedViewerContext.ArchiveDaySequence(
                        year = yr,
                        capturedLocalDate = dayStack.capturedLocalDate,
                        initialRecallId = entry.initialRecallId
                    )
                )
            }
        }
    }

    fun openFavouriteRecall(recall: Recall) {
        internalNavState.update {
            it.copy(
                expandedViewerContext = ExpandedViewerContext.FavouritesSequence(
                    initialRecallId = recall.id
                )
            )
        }
    }

    fun closeExpandedViewer(onFinishActivityIfWidgetOrigin: () -> Unit) {
        val ctx = internalNavState.value.expandedViewerContext
        if (ctx is ExpandedViewerContext.WidgetRecentSequence) {
            onFinishActivityIfWidgetOrigin()
        } else {
            internalNavState.update { it.copy(expandedViewerContext = null) }
        }
    }

    fun toggleAddressed(recallId: String) {
        viewModelScope.launch {
            toggleAddressedUseCase(recallId)
        }
    }

    fun toggleFavourite(recallId: String) {
        viewModelScope.launch {
            toggleFavouriteUseCase(recallId)
        }
    }

    fun resolveAudioFile(relativePath: String?): File? {
        if (relativePath.isNullOrBlank()) return null
        return voiceFileStorage.resolvePermanentFile(relativePath)
    }

    fun updatePlaybackState(recallId: String?, errorMessage: String?) {
        _playbackState.value = recallId to errorMessage
    }

    // Filter & Preference mutators
    fun updateMainFilter(filterState: RecallFilterState) {
        viewModelScope.launch { preferencesRepository.updateMainFilter(filterState) }
    }

    fun updateArchiveFilter(filterState: RecallFilterState) {
        viewModelScope.launch { preferencesRepository.updateArchiveFilter(filterState) }
    }

    fun setFontFamily(family: FontFamilyOption) {
        viewModelScope.launch { preferencesRepository.setFontFamily(family) }
    }

    fun setFontScale(scale: FontScaleOption) {
        viewModelScope.launch { preferencesRepository.setFontScale(scale) }
    }

    fun setPaperTexture(texture: PaperTextureOption) {
        viewModelScope.launch { preferencesRepository.setPaperTexture(texture) }
    }

    fun setPaperEdge(edge: PaperEdgeOption) {
        viewModelScope.launch { preferencesRepository.setPaperEdge(edge) }
    }

    fun setStackDepth(depth: Int) {
        viewModelScope.launch { preferencesRepository.setStackDepth(depth) }
    }

    fun setShadowStyle(shadow: ShadowStyleOption) {
        viewModelScope.launch { preferencesRepository.setShadowStyle(shadow) }
    }

    fun setWidgetSpiralStyle(spiral: WidgetSpiralOption) {
        viewModelScope.launch { preferencesRepository.setWidgetSpiralStyle(spiral) }
    }

    fun setVoiceCharacter(character: VoiceCharacterOption) {
        viewModelScope.launch { preferencesRepository.setVoiceCharacter(character) }
    }

    fun setAnimationsEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setAnimationsEnabled(enabled) }
    }

    fun setTimeFormat(format: TimeFormatOption) {
        viewModelScope.launch { preferencesRepository.setTimeFormat(format) }
    }

    fun setDateFormat(format: DateFormatOption) {
        viewModelScope.launch { preferencesRepository.setDateFormat(format) }
    }

    fun setHapticsEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setHapticsEnabled(enabled) }
    }

    fun setSoundEffectsEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setSoundEffectsEnabled(enabled) }
    }

    fun setKeepScreenAwake(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setKeepScreenAwakeDuringRecording(enabled) }
    }

    fun resetPreferencesOnly() {
        viewModelScope.launch { preferencesRepository.resetPreferencesToDefaults() }
    }

    private fun resolveExpandedViewerSequence(
        viewerContext: ExpandedViewerContext?,
        allRecalls: List<Recall>,
        favourites: List<Recall>,
        prefs: UserPreferences
    ): Triple<List<Recall>, String?, Boolean> {
        return when (viewerContext) {
            null -> Triple(emptyList(), null, false)
            is ExpandedViewerContext.MainDaySequence -> {
                val dayRecalls = allRecalls.filter { it.capturedLocalDate == viewerContext.capturedLocalDate }
                val entry = getYearMonthDayGroups.resolveDayStackEntrySequence(
                    dayRecalls = dayRecalls,
                    activeFilter = prefs.mainFilterState
                )
                Triple(
                    entry.recallsNewestFirst,
                    viewerContext.initialRecallId ?: entry.initialRecallId,
                    false
                )
            }
            is ExpandedViewerContext.ArchiveDaySequence -> {
                val dayRecalls = allRecalls.filter { it.capturedLocalDate == viewerContext.capturedLocalDate }
                val entry = getYearMonthDayGroups.resolveDayStackEntrySequence(
                    dayRecalls = dayRecalls,
                    activeFilter = prefs.archiveFilterState
                )
                Triple(
                    entry.recallsNewestFirst,
                    viewerContext.initialRecallId ?: entry.initialRecallId,
                    false
                )
            }
            is ExpandedViewerContext.FavouritesSequence -> {
                Triple(favourites, viewerContext.initialRecallId, false)
            }
            is ExpandedViewerContext.WidgetRecentSequence -> {
                val top19 = allRecalls.take(GetLatestWidgetRecallsUseCase.WIDGET_MAX_ITEMS)
                // Ensure the tapped recall is included even if a newer recall arrived concurrently
                val sequence = if (top19.any { it.id == viewerContext.initialRecallId }) {
                    top19
                } else {
                    val target = allRecalls.firstOrNull { it.id == viewerContext.initialRecallId }
                    if (target != null) listOf(target) + top19.take(18) else top19
                }
                Triple(sequence, viewerContext.initialRecallId, true)
            }
        }
    }

    class Factory(
        private val initialDirective: WidgetDeepLinkHandler.InitialLaunchDirective,
        private val recallRepository: RecallRepository,
        private val preferencesRepository: PreferencesRepository,
        private val voiceFileStorage: VoiceFileStorage,
        private val timeProvider: TimeProvider,
        private val getYearMonthDayGroups: GetYearMonthDayGroupsUseCase,
        private val toggleAddressedUseCase: ToggleAddressedUseCase,
        private val toggleFavouriteUseCase: ToggleFavouriteUseCase,
        private val createTextRecallUseCase: CreateTextRecallUseCase,
        private val finalizeVoiceRecallUseCase: FinalizeVoiceRecallUseCase,
        private val cancelCaptureUseCase: CancelCaptureUseCase,
        private val recorderController: VoiceRecorderController
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ScrbLViewModel(
                initialDirective = initialDirective,
                recallRepository = recallRepository,
                preferencesRepository = preferencesRepository,
                voiceFileStorage = voiceFileStorage,
                timeProvider = timeProvider,
                getYearMonthDayGroups = getYearMonthDayGroups,
                toggleAddressedUseCase = toggleAddressedUseCase,
                toggleFavouriteUseCase = toggleFavouriteUseCase,
                createTextRecallUseCase = createTextRecallUseCase,
                finalizeVoiceRecallUseCase = finalizeVoiceRecallUseCase,
                cancelCaptureUseCase = cancelCaptureUseCase,
                recorderController = recorderController
            ) as T
        }
    }
}
