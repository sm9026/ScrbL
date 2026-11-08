package com.scrbl.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.scrbl.app.navigation.AppNavHost
import com.scrbl.app.navigation.ScrbLViewModel
import com.scrbl.app.navigation.WidgetDeepLinkHandler
import com.scrbl.app.platform.audio.AndroidVoiceRecorderController

/**
 * Single Activity entry point for ScrbL (`08_ANDROID_ARCHITECTURE.md`):
 * - Parses widget deep-link intents synchronously before `setContent` so tapping a widget page
 *   opens the Expanded Recall Detail destination directly as the very first visible route
 *   without flashing Main first.
 * - Handles `onNewIntent` when `launchMode="singleTop"` receives a subsequent widget tap.
 */
class MainActivity : ComponentActivity() {

    private var viewModel: ScrbLViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val appContainer = (application as ScrbLApplication).container
        val initialDirective = WidgetDeepLinkHandler.parseIntent(intent)
        val recorderController = AndroidVoiceRecorderController(
            context = applicationContext,
            coroutineScope = lifecycleScope
        )

        val factory = ScrbLViewModel.Factory(
            initialDirective = initialDirective,
            recallRepository = appContainer.recallRepository,
            preferencesRepository = appContainer.preferencesRepository,
            voiceFileStorage = appContainer.voiceFileStorage,
            timeProvider = appContainer.timeProvider,
            getYearMonthDayGroups = appContainer.getYearMonthDayGroupsUseCase,
            toggleAddressedUseCase = appContainer.toggleAddressedUseCase,
            toggleFavouriteUseCase = appContainer.toggleFavouriteUseCase,
            createTextRecallUseCase = appContainer.createTextRecallUseCase,
            finalizeVoiceRecallUseCase = appContainer.finalizeVoiceRecallUseCase,
            cancelCaptureUseCase = appContainer.cancelCaptureUseCase,
            recorderController = recorderController
        )

        val vm = ViewModelProvider(this, factory)[ScrbLViewModel::class.java]
        viewModel = vm

        setContent {
            AppNavHost(
                viewModel = vm,
                onFinishActivity = { finish() }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val directive = WidgetDeepLinkHandler.parseIntent(intent)
        viewModel?.handleNewWidgetIntent(directive)
    }
}
