package com.scrbl.app.navigation

import android.content.Intent
import com.scrbl.app.widget.WidgetIntents

/**
 * Top-level destinations in ScrbL (`03_INFORMATION_ARCHITECTURE.md` & `diagrams/navigation.mmd`).
 */
sealed interface AppDestination {
    data object MainCurrentYear : AppDestination
    data object ProfileSettings : AppDestination
    data object Archive : AppDestination
    data object Favourites : AppDestination
    data object HelpGuide : AppDestination
    data object AboutLicences : AppDestination

    /**
     * Direct widget-origin detail destination (`06_WIDGET_SPEC.md` section 3):
     * When the user taps a recall page in the launcher widget, this destination is set as the
     * very first visible screen so Main never flashes underneath.
     */
    data class WidgetOriginDetail(val targetRecallId: String) : AppDestination
}

/**
 * Active expanded-page viewer context when opened from Main, Archive, Favourites, or Widget.
 */
sealed interface ExpandedViewerContext {
    data class MainDaySequence(
        val capturedLocalDate: String,
        val initialRecallId: String?
    ) : ExpandedViewerContext

    data class ArchiveDaySequence(
        val year: Int,
        val capturedLocalDate: String,
        val initialRecallId: String?
    ) : ExpandedViewerContext

    data class FavouritesSequence(
        val initialRecallId: String
    ) : ExpandedViewerContext

    data class WidgetRecentSequence(
        val initialRecallId: String
    ) : ExpandedViewerContext
}

/**
 * Parses incoming launcher widget Intents into the initial destination or capture trigger
 * before the first Compose frame renders (`08_ANDROID_ARCHITECTURE.md`).
 */
object WidgetDeepLinkHandler {

    data class InitialLaunchDirective(
        val startDestination: AppDestination = AppDestination.MainCurrentYear,
        val openCaptureImmediately: Boolean = false
    )

    fun parseIntent(intent: Intent?): InitialLaunchDirective {
        if (intent == null) return InitialLaunchDirective()

        return when (intent.action) {
            WidgetIntents.ACTION_OPEN_WIDGET_RECALL -> {
                val recallId = intent.getStringExtra(WidgetIntents.EXTRA_WIDGET_RECALL_ID)
                    ?: intent.data?.lastPathSegment
                if (!recallId.isNullOrBlank()) {
                    InitialLaunchDirective(
                        startDestination = AppDestination.WidgetOriginDetail(recallId),
                        openCaptureImmediately = false
                    )
                } else {
                    InitialLaunchDirective()
                }
            }
            WidgetIntents.ACTION_OPEN_WIDGET_CAPTURE -> {
                InitialLaunchDirective(
                    startDestination = AppDestination.MainCurrentYear,
                    openCaptureImmediately = true
                )
            }
            else -> InitialLaunchDirective()
        }
    }
}
