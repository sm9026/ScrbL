package com.scrbl.app.navigation

import android.app.Activity
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.scrbl.app.core.design.PaperBackground
import com.scrbl.app.core.design.ScrbLTheme
import com.scrbl.app.domain.model.RecallType
import com.scrbl.app.feature.archive.ArchiveScreen
import com.scrbl.app.feature.capture.CapturePageOverlay
import com.scrbl.app.feature.capture.CaptureStage
import com.scrbl.app.feature.favourites.FavouritesScreen
import com.scrbl.app.feature.help.AboutLicencesScreen
import com.scrbl.app.feature.help.HelpGestureGuideScreen
import com.scrbl.app.feature.home.MainScreen
import com.scrbl.app.feature.recallviewer.ExpandedRecallViewerOverlay
import com.scrbl.app.feature.settings.ProfileSettingsScreen
import com.scrbl.app.platform.audio.AndroidVoicePlayerController
import com.scrbl.app.platform.lifecycle.PlatformFeedbackAndWindowHelper
import com.scrbl.app.platform.permissions.AudioPermissionHelper

/**
 * Root Compose navigation host for ScrbL (`08_ANDROID_ARCHITECTURE.md`):
 * - Renders the active destination (`MainCurrentYear`, `ProfileSettings`, `Archive`, `Favourites`,
 *   `HelpGuide`, `AboutLicences`, or `WidgetOriginDetail`).
 * - When launched via a widget page tap (`WidgetOriginDetail`), renders the Expanded Recall Detail
 *   overlay directly above a clean `PaperBackground` without ever flashing or instantiating `MainScreen` underneath.
 * - Observes `ON_STOP` lifecycle events so leaving the foreground during voice recording immediately
 *   discards the unfinished recording.
 * - Manages `FLAG_KEEP_SCREEN_ON` strictly during active voice recording when the user preference is enabled.
 */
@Composable
fun AppNavHost(
    viewModel: ScrbLViewModel,
    onFinishActivity: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val captureState by viewModel.captureUiState.collectAsState()
    val context = LocalContext.current
    val activity = context as? Activity
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isSystem24Hour = remember(context) { DateFormat.is24HourFormat(context) }

    val voicePlayer = remember(context) { AndroidVoicePlayerController(context) }

    // Keep screen awake ONLY during active voice recording when preference is enabled
    val shouldKeepAwake = uiState.preferences.keepScreenAwakeDuringRecording &&
        captureState.stage == CaptureStage.VOICE_RECORDING
    LaunchedEffect(shouldKeepAwake, activity) {
        PlatformFeedbackAndWindowHelper.updateKeepScreenAwake(activity, shouldKeepAwake)
    }

    // Discard any active voice recording if the app leaves the foreground (ON_STOP)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                viewModel.captureStateMachine.onAppBackgrounded()
                voicePlayer.stopAndRelease()
                viewModel.updatePlaybackState(null, null)
                PlatformFeedbackAndWindowHelper.updateKeepScreenAwake(activity, false)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            voicePlayer.stopAndRelease()
            PlatformFeedbackAndWindowHelper.updateKeepScreenAwake(activity, false)
        }
    }

    // Runtime RECORD_AUDIO permission launcher requested ONLY when microphone is selected
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.captureStateMachine.startVoiceRecordingSession()
        } else {
            val showRationale = activity?.let {
                ActivityCompat.shouldShowRequestPermissionRationale(
                    it,
                    AudioPermissionHelper.RECORD_AUDIO_PERMISSION
                )
            } ?: true
            viewModel.captureStateMachine.onMicrophonePermissionDenied(
                permanentlyDenied = !showRationale
            )
        }
    }

    ScrbLTheme(preferences = uiState.preferences) {
        Box(modifier = modifier.fillMaxSize()) {
            when (uiState.currentDestination) {
                AppDestination.MainCurrentYear -> {
                    MainScreen(
                        gridCells = uiState.mainGridCells,
                        hasAnyDayStacksVisible = uiState.hasAnyDayStacksInMain,
                        mainFilterState = uiState.preferences.mainFilterState,
                        onUpdateMainFilter = viewModel::updateMainFilter,
                        onDayStackClick = { dayStack ->
                            PlatformFeedbackAndWindowHelper.performSubtleHaptic(
                                view,
                                uiState.preferences.hapticsEnabled
                            )
                            viewModel.openMainDayStack(dayStack)
                        },
                        onToggleMonthExpanded = { month ->
                            PlatformFeedbackAndWindowHelper.performSubtleHaptic(
                                view,
                                uiState.preferences.hapticsEnabled
                            )
                            viewModel.toggleMainMonthExpanded(month)
                        },
                        onOpenProfileSettings = {
                            viewModel.navigateTo(AppDestination.ProfileSettings)
                        },
                        onOpenCapture = {
                            PlatformFeedbackAndWindowHelper.performSubtleHaptic(
                                view,
                                uiState.preferences.hapticsEnabled
                            )
                            viewModel.captureStateMachine.openBlankCapturePage()
                        }
                    )
                }

                AppDestination.ProfileSettings -> {
                    ProfileSettingsScreen(
                        onOpenArchive = { viewModel.navigateTo(AppDestination.Archive) },
                        onOpenFavourites = { viewModel.navigateTo(AppDestination.Favourites) },
                        onOpenHelpGuide = { viewModel.navigateTo(AppDestination.HelpGuide) },
                        onOpenAbout = { viewModel.navigateTo(AppDestination.AboutLicences) },
                        onSetFontFamily = viewModel::setFontFamily,
                        onSetFontScale = viewModel::setFontScale,
                        onSetPaperTexture = viewModel::setPaperTexture,
                        onSetPaperEdge = viewModel::setPaperEdge,
                        onSetStackDepth = viewModel::setStackDepth,
                        onSetShadowStyle = viewModel::setShadowStyle,
                        onSetWidgetSpiralStyle = viewModel::setWidgetSpiralStyle,
                        onSetVoiceCharacter = viewModel::setVoiceCharacter,
                        onSetAnimationsEnabled = viewModel::setAnimationsEnabled,
                        onSetTimeFormat = viewModel::setTimeFormat,
                        onSetDateFormat = viewModel::setDateFormat,
                        onSetHapticsEnabled = viewModel::setHapticsEnabled,
                        onSetSoundEffectsEnabled = viewModel::setSoundEffectsEnabled,
                        onSetKeepScreenAwake = viewModel::setKeepScreenAwake,
                        onResetPreferencesOnly = viewModel::resetPreferencesOnly,
                        onNavigateBack = { viewModel.navigateBack() }
                    )
                }

                AppDestination.Archive -> {
                    ArchiveScreen(
                        yearStacks = uiState.archiveYearStacks,
                        selectedYear = uiState.selectedArchiveYear,
                        yearGridCells = uiState.archiveYearGridCells,
                        hasExpandedMonthsInSelectedYear = uiState.hasExpandedMonthsInSelectedArchiveYear,
                        archiveFilterState = uiState.preferences.archiveFilterState,
                        onSelectYear = viewModel::selectArchiveYear,
                        onExitSelectedYear = viewModel::exitSelectedArchiveYear,
                        onToggleArchiveMonthExpanded = viewModel::toggleArchiveMonthExpanded,
                        onDayStackClick = viewModel::openArchiveDayStack,
                        onUpdateArchiveFilter = viewModel::updateArchiveFilter,
                        onNavigateBackToProfile = { viewModel.navigateBack() }
                    )
                }

                AppDestination.Favourites -> {
                    FavouritesScreen(
                        favouriteRecallsNewestFirst = uiState.favouriteRecallsNewestFirst,
                        onFavouriteRecallClick = viewModel::openFavouriteRecall,
                        onNavigateBack = { viewModel.navigateBack() }
                    )
                }

                AppDestination.HelpGuide -> {
                    HelpGestureGuideScreen(
                        onNavigateBack = { viewModel.navigateBack() }
                    )
                }

                AppDestination.AboutLicences -> {
                    AboutLicencesScreen(
                        onNavigateBack = { viewModel.navigateBack() }
                    )
                }

                is AppDestination.WidgetOriginDetail -> {
                    // Do not render MainScreen underneath when opened directly from the launcher widget
                    PaperBackground {}
                }
            }

            // Shared Expanded Recall Page overlay (Main day stack, Archive day stack, Favourites, or Widget detail)
            if (uiState.expandedViewerContext != null && uiState.expandedSequenceRecalls.isNotEmpty()) {
                ExpandedRecallViewerOverlay(
                    recallsNewestFirst = uiState.expandedSequenceRecalls,
                    initialRecallId = uiState.expandedInitialRecallId,
                    isWidgetOrigin = uiState.isExpandedWidgetOrigin,
                    activePlayingRecallId = uiState.activePlayingRecallId,
                    playbackErrorMessage = uiState.playbackErrorMessage,
                    isSystem24Hour = isSystem24Hour,
                    onToggleAddressed = { recallId ->
                        PlatformFeedbackAndWindowHelper.performSubtleHaptic(
                            view,
                            uiState.preferences.hapticsEnabled
                        )
                        PlatformFeedbackAndWindowHelper.performSubtleClickSound(
                            context,
                            uiState.preferences.soundEffectsEnabled
                        )
                        viewModel.toggleAddressed(recallId)
                    },
                    onToggleFavourite = { recallId ->
                        PlatformFeedbackAndWindowHelper.performSubtleHaptic(
                            view,
                            uiState.preferences.hapticsEnabled
                        )
                        PlatformFeedbackAndWindowHelper.performSubtleClickSound(
                            context,
                            uiState.preferences.soundEffectsEnabled
                        )
                        viewModel.toggleFavourite(recallId)
                    },
                    onToggleVoicePlayback = { recall ->
                        if (recall.type == RecallType.VOICE) {
                            val file = viewModel.resolveAudioFile(recall.voiceRelativePath)
                            if (file == null) {
                                viewModel.updatePlaybackState(recall.id, "Audio file unavailable.")
                            } else {
                                voicePlayer.togglePlayback(
                                    recallId = recall.id,
                                    audioFile = file,
                                    onPlaybackStateChanged = { isPlaying, err ->
                                        viewModel.updatePlaybackState(
                                            recallId = if (isPlaying || err != null) recall.id else null,
                                            errorMessage = err
                                        )
                                    }
                                )
                            }
                        }
                    },
                    onStopVoicePlayback = {
                        voicePlayer.stopAndRelease()
                        viewModel.updatePlaybackState(null, null)
                    },
                    onDismiss = {
                        viewModel.closeExpandedViewer(onFinishActivityIfWidgetOrigin = onFinishActivity)
                    },
                    onWidgetPlusClick = {
                        voicePlayer.stopAndRelease()
                        viewModel.updatePlaybackState(null, null)
                        viewModel.captureStateMachine.openBlankCapturePage()
                    }
                )
            }

            // Floating Capture Page overlay (blank page -> text / voice capture)
            CapturePageOverlay(
                uiState = captureState,
                onSelectTextMode = {
                    PlatformFeedbackAndWindowHelper.performSubtleHaptic(
                        view,
                        uiState.preferences.hapticsEnabled
                    )
                    viewModel.captureStateMachine.selectTextMode()
                },
                onSelectVoiceModeRequestPermission = {
                    PlatformFeedbackAndWindowHelper.performSubtleHaptic(
                        view,
                        uiState.preferences.hapticsEnabled
                    )
                    if (AudioPermissionHelper.hasRecordAudioPermission(context)) {
                        viewModel.captureStateMachine.startVoiceRecordingSession()
                    } else {
                        micPermissionLauncher.launch(AudioPermissionHelper.RECORD_AUDIO_PERMISSION)
                    }
                },
                onOpenAppSettingsForPermission = {
                    AudioPermissionHelper.openAppSystemSettings(context)
                },
                onTextChanged = viewModel.captureStateMachine::onTextChanged,
                onCommitText = {
                    PlatformFeedbackAndWindowHelper.performSubtleHaptic(
                        view,
                        uiState.preferences.hapticsEnabled
                    )
                    PlatformFeedbackAndWindowHelper.performSubtleClickSound(
                        context,
                        uiState.preferences.soundEffectsEnabled
                    )
                    viewModel.captureStateMachine.commitTextRecall()
                },
                onVoiceTitleChanged = viewModel.captureStateMachine::onVoiceTitleChanged,
                onStopAndCommitVoice = {
                    PlatformFeedbackAndWindowHelper.performSubtleHaptic(
                        view,
                        uiState.preferences.hapticsEnabled
                    )
                    PlatformFeedbackAndWindowHelper.performSubtleClickSound(
                        context,
                        uiState.preferences.soundEffectsEnabled
                    )
                    viewModel.captureStateMachine.stopAndCommitVoiceRecall()
                },
                onFlickOrBackDiscard = {
                    viewModel.captureStateMachine.discardCapture()
                }
            )
        }
    }
}
