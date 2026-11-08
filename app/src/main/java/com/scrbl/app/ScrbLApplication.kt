package com.scrbl.app

import android.app.Application
import com.scrbl.app.core.time.SystemTimeProvider
import com.scrbl.app.core.time.TimeProvider
import com.scrbl.app.data.audio.AppPrivateVoiceFileStorage
import com.scrbl.app.data.local.room.ScrbLDatabase
import com.scrbl.app.data.preferences.DataStorePreferencesRepository
import com.scrbl.app.data.repository.RoomRecallRepository
import com.scrbl.app.domain.repository.PreferencesRepository
import com.scrbl.app.domain.repository.RecallRepository
import com.scrbl.app.domain.repository.VoiceFileStorage
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
import com.scrbl.app.widget.AndroidWidgetRefresher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Application-scoped dependency container for ScrbL.
 * On startup (`onCreate`), cleans up any abandoned `.tmp` voice files left behind by a prior
 * process death before commit, and refreshes any active home-screen widgets (`10_ANDROID_PLATFORM_PRIVACY.md` section 7).
 */
class ScrbLApplication : Application() {

    lateinit var container: AppContainer
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        applicationScope.launch {
            container.cleanupAbandonedRecordingTempsUseCase()
            container.widgetRefresher.refreshWidgets()
        }
    }
}

class AppContainer(application: Application) {

    val timeProvider: TimeProvider = SystemTimeProvider()

    val database: ScrbLDatabase = ScrbLDatabase.getInstance(application)

    val widgetRefresher: WidgetRefresher = AndroidWidgetRefresher(application)

    val recallRepository: RecallRepository = RoomRecallRepository(
        recallDao = database.recallDao()
    )

    val preferencesRepository: PreferencesRepository = DataStorePreferencesRepository(
        context = application,
        widgetRefresher = widgetRefresher
    )

    val voiceFileStorage: VoiceFileStorage = AppPrivateVoiceFileStorage(
        appFilesDir = application.filesDir,
        appCacheDir = application.cacheDir
    )

    val applyRecallFilterUseCase = ApplyRecallFilterUseCase()

    val getYearMonthDayGroupsUseCase = GetYearMonthDayGroupsUseCase(
        applyRecallFilter = applyRecallFilterUseCase
    )

    val getLatestWidgetRecallsUseCase = GetLatestWidgetRecallsUseCase(
        recallRepository = recallRepository
    )

    val createTextRecallUseCase = CreateTextRecallUseCase(
        recallRepository = recallRepository,
        timeProvider = timeProvider,
        widgetRefresher = widgetRefresher
    )

    val finalizeVoiceRecallUseCase = FinalizeVoiceRecallUseCase(
        recallRepository = recallRepository,
        voiceFileStorage = voiceFileStorage,
        timeProvider = timeProvider,
        widgetRefresher = widgetRefresher
    )

    val cancelCaptureUseCase = CancelCaptureUseCase(
        voiceFileStorage = voiceFileStorage
    )

    val cleanupAbandonedRecordingTempsUseCase = CleanupAbandonedRecordingTempsUseCase(
        voiceFileStorage = voiceFileStorage
    )

    val toggleAddressedUseCase = ToggleAddressedUseCase(
        recallRepository = recallRepository,
        widgetRefresher = widgetRefresher
    )

    val toggleFavouriteUseCase = ToggleFavouriteUseCase(
        recallRepository = recallRepository,
        widgetRefresher = widgetRefresher
    )
}
