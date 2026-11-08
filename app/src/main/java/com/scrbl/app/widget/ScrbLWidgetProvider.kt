package com.scrbl.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import com.scrbl.app.R
import com.scrbl.app.ScrbLApplication
import com.scrbl.app.domain.model.FontFamilyOption
import com.scrbl.app.domain.model.UserPreferences
import com.scrbl.app.domain.model.WidgetSpiralOption
import kotlinx.coroutines.runBlocking

/**
 * Classic collection App Widget provider (`06_WIDGET_SPEC.md` section 4 & `01_WIDGET_REFINEMENT_SPEC.md`):
 * - Binds `StackView` (`R.id.widget_stack_view`) to `ScrbLWidgetService` so users can flick the
 *   front notepad sheet up (older) or down (newer) across up to the 19 most recent recalls.
 * - Renders one stationary plain-paper stack base (`R.id.widget_notepad_stack_base`) and one
 *   continuous top-spiral binding overlay (`R.id.widget_notepad_spiral_overlay`) so the widget
 *   reads as one cohesive notebook rather than separately bound mini-notebooks.
 * - Applies the user's selected bundled app font (`FontFamilyOption`) to the empty-notepad state.
 * - Binds `R.id.widget_empty_view` as the empty view; tapping an empty notepad opens the blank Capture page.
 * - Strictly NO plus button on the widget surface itself.
 */
class ScrbLWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateSingleWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == WidgetIntents.ACTION_REFRESH_WIDGET ||
            intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE
        ) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, ScrbLWidgetProvider::class.java))
            if (ids != null && ids.isNotEmpty()) {
                manager.notifyAppWidgetViewDataChanged(ids, R.id.widget_stack_view)
                for (id in ids) {
                    updateSingleWidget(context, manager, id)
                }
            }
        }
    }

    companion object {
        fun updateSingleWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val userPreferences = readPreferencesSnapshotSafely(context)

            val serviceIntent = Intent(context, ScrbLWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }

            val views = RemoteViews(context.packageName, R.layout.widget_scrbl_notepad).apply {
                setRemoteAdapter(R.id.widget_stack_view, serviceIntent)
                setEmptyView(R.id.widget_stack_view, R.id.widget_empty_view)

                // Apply continuous top-spiral style from UserPreferences
                val spiralRes = if (userPreferences.widgetSpiralStyle == WidgetSpiralOption.SUBTLE) {
                    R.drawable.bg_widget_notepad_spiral_overlay_subtle
                } else {
                    R.drawable.bg_widget_notepad_spiral_overlay
                }
                setImageViewResource(R.id.widget_notepad_spiral_overlay, spiralRes)

                // Reflect selected app font on the empty notepad face
                applyEmptyStateFontSelection(this, userPreferences.fontFamily)

                val itemClickTemplate = WidgetIntents.createItemClickPendingIntentTemplate(
                    context = context,
                    appWidgetId = appWidgetId
                )
                setPendingIntentTemplate(R.id.widget_stack_view, itemClickTemplate)

                val emptyClickIntent = WidgetIntents.createEmptyNotepadCapturePendingIntent(
                    context = context,
                    appWidgetId = appWidgetId
                )
                setOnClickPendingIntent(R.id.widget_empty_view, emptyClickIntent)
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun readPreferencesSnapshotSafely(context: Context): UserPreferences {
            val app = context.applicationContext as? ScrbLApplication ?: return UserPreferences()
            return runCatching {
                runBlocking {
                    app.container.preferencesRepository.getSnapshot()
                }
            }.getOrDefault(UserPreferences())
        }

        private fun applyEmptyStateFontSelection(
            views: RemoteViews,
            fontFamily: FontFamilyOption
        ) {
            val showSerif = fontFamily == FontFamilyOption.SERIF
            val showHandwritten = fontFamily == FontFamilyOption.HANDWRITTEN
            val showSans = fontFamily == FontFamilyOption.SANS

            views.setViewVisibility(
                R.id.widget_empty_title,
                if (showSerif) View.VISIBLE else View.GONE
            )
            views.setViewVisibility(
                R.id.widget_empty_subtitle,
                if (showSerif) View.VISIBLE else View.GONE
            )
            views.setViewVisibility(
                R.id.widget_empty_title_handwritten,
                if (showHandwritten) View.VISIBLE else View.GONE
            )
            views.setViewVisibility(
                R.id.widget_empty_subtitle_handwritten,
                if (showHandwritten) View.VISIBLE else View.GONE
            )
            views.setViewVisibility(
                R.id.widget_empty_title_sans,
                if (showSans) View.VISIBLE else View.GONE
            )
            views.setViewVisibility(
                R.id.widget_empty_subtitle_sans,
                if (showSans) View.VISIBLE else View.GONE
            )
        }
    }
}