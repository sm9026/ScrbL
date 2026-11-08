package com.scrbl.app.widget

import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.scrbl.app.R
import com.scrbl.app.ScrbLApplication
import com.scrbl.app.core.time.RecallDateTimeFormatter
import com.scrbl.app.domain.model.FontFamilyOption
import com.scrbl.app.domain.model.Recall
import com.scrbl.app.domain.model.RecallType
import com.scrbl.app.domain.model.UserPreferences
import com.scrbl.app.domain.model.VoiceCharacterOption
import com.scrbl.app.domain.usecase.GetLatestWidgetRecallsUseCase
import kotlinx.coroutines.runBlocking
import android.util.Log
import android.appwidget.AppWidgetManager

/**
 * `RemoteViewsService` + `RemoteViewsFactory` supplying up to the 19 most recent recalls
 * (`GetLatestWidgetRecallsUseCase.WIDGET_MAX_ITEMS = 19`) in newest-first order
 * (`06_WIDGET_SPEC.md` & `01_WIDGET_REFINEMENT_SPEC.md`).
 *
 * Refinements enforced:
 * 1. Selected app font reflected on the widget page face via resource-backed layout variants
 *    (`widget_scrbl_page_item`, `widget_scrbl_page_item_handwritten`, `widget_scrbl_page_item_sans`)
 *    referencing the app's exact bundled font files (`@font/scrbl_serif_regular`,
 *    `@font/scrbl_handwritten_regular`, `@font/scrbl_sans_regular`), plus `UserPreferences.fontScale`.
 * 2. Heart, date, time, and page-number metadata are strictly contained inside the visible front
 *    paper sheet's proportional interior bounds, away from the top spiral and bottom-right corner curl.
 * 3. Underlying stack layers are stationary plain paper sheets (`bg_widget_notepad_stack_base` in
 *    `widget_scrbl_notepad.xml`) with one continuous top spiral (`bg_widget_notepad_spiral_overlay`),
 *    so only the visible front page carries the metadata treatment.
 * 4. Recall text wraps cleanly inside the usable paper region with uniform auto-sizing and balanced
 *    line breaking (`maxLines="5"`, `ellipsize="end"`), without altering stored recall content.
 * 5. Preserves exact `recall.id` fill-in click intents, 19-item ordering, and all status inclusions.
 */
 private const val TAG = "ScrbLWidget"
class ScrbLWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        Log.d(TAG, "onGetViewFactory called; widgetId=${intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)}")
        return ScrbLWidgetFactory(applicationContext)
    }
}

class ScrbLWidgetFactory(
    private val context: Context
) : RemoteViewsService.RemoteViewsFactory {

    private var widgetRecalls: List<Recall> = emptyList()
    private var userPreferences: UserPreferences = UserPreferences()

    override fun onCreate() {
        Log.d(TAG, "Factory onCreate()")
    }

    override fun onDataSetChanged() {
        val app = context.applicationContext as? ScrbLApplication ?: return

        try {
            runBlocking {
                widgetRecalls = app.container.getLatestWidgetRecallsUseCase(
                    limit = GetLatestWidgetRecallsUseCase.WIDGET_MAX_ITEMS
                )
                Log.d(TAG, "Recall query completed: ${widgetRecalls.size} recalls")

                userPreferences = app.container.preferencesRepository.getSnapshot()
                Log.d(TAG, "Preferences loaded: fontFamily=${userPreferences.fontFamily}")
            }

            Log.d(TAG, "onDataSetChanged() completed successfully")
        } catch (error: Exception) {
            Log.e(TAG, "onDataSetChanged() FAILED", error)
            throw error
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "Factory onDestroy(); clearing ${widgetRecalls.size} recalls")
        widgetRecalls = emptyList()
    }

    override fun getCount(): Int {
        val count = widgetRecalls.size.coerceAtMost(
            GetLatestWidgetRecallsUseCase.WIDGET_MAX_ITEMS
        )
        Log.d(TAG, "getCount() = $count")
        return count
    }

    override fun getViewAt(position: Int): RemoteViews? {
        Log.d(TAG, "getViewAt($position) called; recalls=${widgetRecalls.size}")
        if (position < 0 || position >= widgetRecalls.size) return null
        val recall = widgetRecalls[position]
        val total = widgetRecalls.size
        val is24Hour = DateFormat.is24HourFormat(context)

        val dateLabel = RecallDateTimeFormatter.formatExpandedPageDate(
            capturedLocalDateIso = recall.capturedLocalDate,
            patternPref = userPreferences.dateFormat
        )
        val timeLabel = RecallDateTimeFormatter.formatCreationTime(
            createdAtEpochMillis = recall.createdAtEpochMillis,
            capturedZoneId = recall.capturedZoneId,
            timeFormatOption = userPreferences.timeFormat,
            isSystem24Hour = is24Hour
        )
        val positionLabel = RecallDateTimeFormatter.formatPageIndex(
            oneBasedIndex = position + 1,
            totalCount = total
        )

        val layoutRes = resolveItemLayoutForFont(userPreferences.fontFamily)
        Log.d(TAG, "Creating page RemoteViews: layoutRes=$layoutRes, type=${recall.type}")
        val views = RemoteViews(context.packageName, layoutRes)

        // Apply addressed vs unaddressed front-sheet shade from the Warm Notebook theme
        val notepadBgRes = if (recall.isAddressed) {
            R.drawable.bg_widget_notepad_card_addressed
        } else {
            R.drawable.bg_widget_notepad_card
        }
        views.setImageViewResource(R.id.widget_item_notepad_bg, notepadBgRes)

        val primaryTextColor = context.getColor(
            if (recall.isAddressed) R.color.scrbl_addressed_tone else R.color.scrbl_unaddressed_tone
        )

        // Scale text sizes gently within safe bounds so text remains legible and never overflows paper
        val scale = userPreferences.fontScale.scaleFactor.coerceIn(0.90f, 1.15f)
        val baseBodySp = when (userPreferences.fontFamily) {
            FontFamilyOption.HANDWRITTEN -> 14.5f
            FontFamilyOption.SERIF -> 13.0f
            FontFamilyOption.SANS -> 12.5f
        }
        val bodySp = (baseBodySp * scale).coerceIn(11f, 16f)
        val metaSp = (10.5f * scale.coerceAtMost(1.08f)).coerceIn(9.5f, 11.5f)

        views.setTextViewText(R.id.widget_item_date, dateLabel)
        views.setTextColor(R.id.widget_item_date, primaryTextColor)
        views.setTextViewTextSize(R.id.widget_item_date, TypedValue.COMPLEX_UNIT_SP, metaSp)

        views.setImageViewResource(
            R.id.widget_item_heart,
            if (recall.isFavourite) R.drawable.ic_doodle_heart_filled else R.drawable.ic_doodle_heart_outline
        )

        if (recall.type == RecallType.TEXT) {
            views.setViewVisibility(R.id.widget_item_text_body, View.VISIBLE)
            views.setViewVisibility(R.id.widget_item_voice_container, View.GONE)
            views.setTextViewText(R.id.widget_item_text_body, recall.textBody.orEmpty())
            views.setTextColor(R.id.widget_item_text_body, primaryTextColor)
            views.setTextViewTextSize(R.id.widget_item_text_body, TypedValue.COMPLEX_UNIT_SP, bodySp)
        } else {
            views.setViewVisibility(R.id.widget_item_text_body, View.GONE)
            views.setViewVisibility(R.id.widget_item_voice_container, View.VISIBLE)

            val charRes = if (userPreferences.voiceCharacter == VoiceCharacterOption.GIRL) {
                R.drawable.ic_voice_character_girl
            } else {
                R.drawable.ic_voice_character_boy
            }
            views.setImageViewResource(R.id.widget_item_voice_character, charRes)

            if (!recall.voiceTitle.isNullOrBlank()) {
                views.setViewVisibility(R.id.widget_item_voice_title_bubble, View.VISIBLE)
                views.setTextViewText(R.id.widget_item_voice_title_bubble, recall.voiceTitle)
                views.setTextColor(R.id.widget_item_voice_title_bubble, primaryTextColor)
                views.setTextViewTextSize(
                    R.id.widget_item_voice_title_bubble,
                    TypedValue.COMPLEX_UNIT_SP,
                    metaSp
                )
            } else {
                views.setViewVisibility(R.id.widget_item_voice_title_bubble, View.GONE)
            }
        }

        views.setTextViewText(R.id.widget_item_time, timeLabel)
        views.setTextViewTextSize(R.id.widget_item_time, TypedValue.COMPLEX_UNIT_SP, metaSp - 0.5f)

        views.setTextViewText(R.id.widget_item_position, positionLabel)
        views.setTextViewTextSize(R.id.widget_item_position, TypedValue.COMPLEX_UNIT_SP, metaSp - 0.5f)

        // Attach explicit immutable recall ID to fill-in click intent
        val fillInIntent = WidgetIntents.createItemFillInIntent(recall.id)
        views.setOnClickFillInIntent(R.id.widget_item_root, fillInIntent)

        Log.d(TAG, "getViewAt($position) completed successfully")
        return views
    }

    override fun getLoadingView(): RemoteViews? = null

    /**
     * 3 layout variants corresponding to the 3 supported FontFamilyOption values
     * (SERIF, HANDWRITTEN, SANS).
     */
    override fun getViewTypeCount(): Int = 3

    override fun getItemId(position: Int): Long {
        return widgetRecalls.getOrNull(position)?.id?.hashCode()?.toLong() ?: position.toLong()
    }

    override fun hasStableIds(): Boolean = true

    companion object {
        fun resolveItemLayoutForFont(fontFamily: FontFamilyOption): Int {
            return when (fontFamily) {
                FontFamilyOption.SERIF -> R.layout.widget_scrbl_page_item
                FontFamilyOption.HANDWRITTEN -> R.layout.widget_scrbl_page_item_handwritten
                FontFamilyOption.SANS -> R.layout.widget_scrbl_page_item_sans
            }
        }
    }
}