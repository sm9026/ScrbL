package com.scrbl.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.scrbl.app.MainActivity
import com.scrbl.app.domain.repository.WidgetRefresher

/**
 * Explicit Intent and PendingIntent contract for the ScrbL Launcher Widget (`06_WIDGET_SPEC.md` sections 3 & 4):
 * - Single tap on the visible/front widget page opens that exact recall by immutable ID (`EXTRA_WIDGET_RECALL_ID`)
 *   directly in the Expanded Recall Detail destination without flashing Main first.
 * - Tapping an empty widget notepad opens the blank Capture page (`ACTION_OPEN_WIDGET_CAPTURE`) without routing through Main.
 * - Uses immutable PendingIntent templates (`FLAG_IMMUTABLE` or `FLAG_MUTABLE` only on the collection template
 *   with an explicit component name as required by Android API 31+ for `setPendingIntentTemplate` + `setOnClickFillInIntent`).
 */
object WidgetIntents {

    const val ACTION_OPEN_WIDGET_RECALL = "com.scrbl.app.action.OPEN_WIDGET_RECALL"
    const val ACTION_OPEN_WIDGET_CAPTURE = "com.scrbl.app.action.OPEN_WIDGET_CAPTURE"
    const val ACTION_REFRESH_WIDGET = "com.scrbl.app.action.REFRESH_WIDGET"

    const val EXTRA_WIDGET_RECALL_ID = "com.scrbl.app.extra.WIDGET_RECALL_ID"
    const val EXTRA_FROM_WIDGET_ORIGIN = "com.scrbl.app.extra.FROM_WIDGET_ORIGIN"

    /**
     * Builds the PendingIntent template attached to the `StackView`.
     * Note: On Android 12+ (API 31+), collection widget templates that accept fill-in intents from
     * `RemoteViewsFactory` must specify `PendingIntent.FLAG_MUTABLE` together with an explicit
     * target component (`MainActivity::class.java`) so the system merges `EXTRA_WIDGET_RECALL_ID`.
     */
    fun createItemClickPendingIntentTemplate(
        context: Context,
        appWidgetId: Int
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_WIDGET_RECALL
            putExtra(EXTRA_FROM_WIDGET_ORIGIN, true)
            data = Uri.parse("scrbl://widget/detail_template/$appWidgetId")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        return PendingIntent.getActivity(context, appWidgetId, intent, flags)
    }

    /**
     * Builds the per-item fill-in Intent bound to each recall sheet inside `ScrbLWidgetFactory`.
     */
    fun createItemFillInIntent(recallId: String): Intent {
        return Intent().apply {
            putExtra(EXTRA_WIDGET_RECALL_ID, recallId)
            putExtra(EXTRA_FROM_WIDGET_ORIGIN, true)
            data = Uri.parse("scrbl://widget/recall/$recallId")
        }
    }

    /**
     * Builds an explicit immutable PendingIntent for the empty-notepad state that opens the blank
     * Capture experience directly.
     */
    fun createEmptyNotepadCapturePendingIntent(
        context: Context,
        appWidgetId: Int
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_WIDGET_CAPTURE
            putExtra(EXTRA_FROM_WIDGET_ORIGIN, true)
            data = Uri.parse("scrbl://widget/capture/$appWidgetId")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(context, appWidgetId + 10_000, intent, flags)
    }
}

class AndroidWidgetRefresher(
    private val appContext: Context
) : WidgetRefresher {
    override fun refreshWidgets() {
        runCatching {
            val manager = AppWidgetManager.getInstance(appContext)
            val component = ComponentName(appContext, ScrbLWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            if (ids != null && ids.isNotEmpty()) {
                manager.notifyAppWidgetViewDataChanged(ids, com.scrbl.app.R.id.widget_stack_view)
                val updateIntent = Intent(appContext, ScrbLWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
                appContext.sendBroadcast(updateIntent)
            }
        }
    }
}
