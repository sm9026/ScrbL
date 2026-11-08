package com.scrbl.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import com.scrbl.app.R

/**
 * Classic collection App Widget provider (`06_WIDGET_SPEC.md` section 4):
 * - Binds `StackView` (`R.id.widget_stack_view`) to `ScrbLWidgetService` so users can flick the
 *   front notepad sheet up (older) or down (newer) across up to the 19 most recent recalls.
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
            val serviceIntent = Intent(context, ScrbLWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }

            val views = RemoteViews(context.packageName, R.layout.widget_scrbl_notepad).apply {
                setRemoteAdapter(R.id.widget_stack_view, serviceIntent)
                setEmptyView(R.id.widget_stack_view, R.id.widget_empty_view)

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
    }
}
