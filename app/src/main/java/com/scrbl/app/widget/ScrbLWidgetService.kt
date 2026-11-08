package com.scrbl.app.widget

import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.scrbl.app.R
import com.scrbl.app.ScrbLApplication
import com.scrbl.app.core.time.RecallDateTimeFormatter
import com.scrbl.app.domain.model.Recall
import com.scrbl.app.domain.model.RecallType
import com.scrbl.app.domain.model.UserPreferences
import com.scrbl.app.domain.model.VoiceCharacterOption
import com.scrbl.app.domain.usecase.GetLatestWidgetRecallsUseCase
import kotlinx.coroutines.runBlocking

/**
 * `RemoteViewsService` + `RemoteViewsFactory` supplying up to the 19 most recent recalls
 * (`GetLatestWidgetRecallsUseCase.WIDGET_MAX_ITEMS = 19`) in newest-first order (`06_WIDGET_SPEC.md`).
 *
 * Rules enforced:
 * - Always re-queries Room on `onDataSetChanged()` so the widget is never backed by stale memory.
 * - Includes all recall statuses (addressed, unaddressed, favourite) and both content types (TEXT, VOICE).
 * - Renders the top-spiral curled notepad sheet (`bg_widget_notepad_card` / `bg_widget_notepad_card_addressed`).
 * - Text recall shows centred body text; Voice recall shows the selected doodle character (`boy`/`girl`)
 *   and optional comic thought-bubble title (never a fake waveform or transcript).
 * - Binds the exact immutable `recall.id` in `setOnClickFillInIntent`.
 */
class ScrbLWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return ScrbLWidgetFactory(applicationContext)
    }
}

class ScrbLWidgetFactory(
    private val context: Context
) : RemoteViewsService.RemoteViewsFactory {

    private var widgetRecalls: List<Recall> = emptyList()
    private var userPreferences: UserPreferences = UserPreferences()

    override fun onCreate() {
        // Initial load occurs in onDataSetChanged()
    }

    override fun onDataSetChanged() {
        val app = context.applicationContext as? ScrbLApplication ?: return
        runBlocking {
            widgetRecalls = app.container.getLatestWidgetRecallsUseCase(
                limit = GetLatestWidgetRecallsUseCase.WIDGET_MAX_ITEMS
            )
            userPreferences = app.container.preferencesRepository.getSnapshot()
        }
    }

    override fun onDestroy() {
        widgetRecalls = emptyList()
    }

    override fun getCount(): Int = widgetRecalls.size.coerceAtMost(GetLatestWidgetRecallsUseCase.WIDGET_MAX_ITEMS)

    override fun getViewAt(position: Int): RemoteViews? {
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

        val views = RemoteViews(context.packageName, R.layout.widget_scrbl_page_item)

        // Apply addressed vs unaddressed shade from the active Warm Notebook theme
        val notepadBgRes = if (recall.isAddressed) {
            R.drawable.bg_widget_notepad_card_addressed
        } else {
            R.drawable.bg_widget_notepad_card
        }
        views.setImageViewResource(R.id.widget_item_notepad_bg, notepadBgRes)

        val primaryTextColor = context.getColor(
            if (recall.isAddressed) R.color.scrbl_addressed_tone else R.color.scrbl_unaddressed_tone
        )

        views.setTextViewText(R.id.widget_item_date, dateLabel)
        views.setTextColor(R.id.widget_item_date, primaryTextColor)

        views.setImageViewResource(
            R.id.widget_item_heart,
            if (recall.isFavourite) R.drawable.ic_doodle_heart_filled else R.drawable.ic_doodle_heart_outline
        )

        if (recall.type == RecallType.TEXT) {
            views.setViewVisibility(R.id.widget_item_text_body, View.VISIBLE)
            views.setViewVisibility(R.id.widget_item_voice_container, View.GONE)
            views.setTextViewText(R.id.widget_item_text_body, recall.textBody.orEmpty())
            views.setTextColor(R.id.widget_item_text_body, primaryTextColor)
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
            } else {
                views.setViewVisibility(R.id.widget_item_voice_title_bubble, View.GONE)
            }
        }

        views.setTextViewText(R.id.widget_item_time, timeLabel)
        views.setTextViewText(R.id.widget_item_position, positionLabel)

        // Attach explicit immutable recall ID to fill-in click intent
        val fillInIntent = WidgetIntents.createItemFillInIntent(recall.id)
        views.setOnClickFillInIntent(R.id.widget_item_root, fillInIntent)

        return views
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 2

    override fun getItemId(position: Int): Long {
        return widgetRecalls.getOrNull(position)?.id?.hashCode()?.toLong() ?: position.toLong()
    }

    override fun hasStableIds(): Boolean = true
}
