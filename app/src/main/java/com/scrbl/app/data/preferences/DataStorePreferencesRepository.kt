package com.scrbl.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.scrbl.app.core.time.DateFormatOption
import com.scrbl.app.core.time.TimeFormatOption
import com.scrbl.app.domain.model.AddressFilterMode
import com.scrbl.app.domain.model.ContentTypeFilterMode
import com.scrbl.app.domain.model.FontFamilyOption
import com.scrbl.app.domain.model.FontScaleOption
import com.scrbl.app.domain.model.PaperEdgeOption
import com.scrbl.app.domain.model.PaperTextureOption
import com.scrbl.app.domain.model.RecallFilterState
import com.scrbl.app.domain.model.ShadowStyleOption
import com.scrbl.app.domain.model.UserPreferences
import com.scrbl.app.domain.model.VoiceCharacterOption
import com.scrbl.app.domain.model.WidgetSpiralOption
import com.scrbl.app.domain.repository.PreferencesRepository
import com.scrbl.app.domain.repository.WidgetRefresher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.scrblDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "scrbl_user_preferences"
)

/**
 * Preferences DataStore implementation backing all appearance, recall, accessibility,
 * and independent Main/Archive filter states (09_DATA_MODEL_AND_STORAGE.md section 8).
 *
 * Notice: `resetPreferencesToDefaults()` clears ONLY DataStore keys and NEVER touches Room or voice files.
 */
class DataStorePreferencesRepository(
    private val context: Context,
    private val widgetRefresher: WidgetRefresher
) : PreferencesRepository {

    private object Keys {
        val THEME_ID = stringPreferencesKey("appearance.themeId")
        val FONT_FAMILY = stringPreferencesKey("appearance.fontFamily")
        val FONT_SCALE = stringPreferencesKey("appearance.fontScale")
        val PAPER_TEXTURE = stringPreferencesKey("appearance.paperTexture")
        val PAPER_EDGE = stringPreferencesKey("appearance.paperEdge")
        val STACK_DEPTH = intPreferencesKey("appearance.stackDepth")
        val SHADOW_STYLE = stringPreferencesKey("appearance.shadowStyle")
        val WIDGET_SPIRAL = stringPreferencesKey("appearance.widgetSpiralStyle")
        val VOICE_CHARACTER = stringPreferencesKey("appearance.voiceCharacter")
        val ANIMATIONS_ENABLED = booleanPreferencesKey("appearance.animationsEnabled")

        val TIME_FORMAT = stringPreferencesKey("recall.timeFormat")
        val DATE_FORMAT = stringPreferencesKey("recall.dateFormat")

        val HAPTICS_ENABLED = booleanPreferencesKey("accessibility.hapticsEnabled")
        val SOUND_EFFECTS_ENABLED = booleanPreferencesKey("accessibility.soundEffectsEnabled")
        val KEEP_SCREEN_AWAKE = booleanPreferencesKey("recording.keepScreenAwake")

        // Independent Main filter keys
        val MAIN_FILTER_FAV = booleanPreferencesKey("filters.main.favouritesOnly")
        val MAIN_FILTER_ADDRESS = stringPreferencesKey("filters.main.addressMode")
        val MAIN_FILTER_TYPE = stringPreferencesKey("filters.main.contentType")

        // Independent Archive filter keys
        val ARCHIVE_FILTER_FAV = booleanPreferencesKey("filters.archive.favouritesOnly")
        val ARCHIVE_FILTER_ADDRESS = stringPreferencesKey("filters.archive.addressMode")
        val ARCHIVE_FILTER_TYPE = stringPreferencesKey("filters.archive.contentType")
    }

    override val preferencesFlow: Flow<UserPreferences> = context.scrblDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs -> mapPreferences(prefs) }

    override val mainFilterFlow: Flow<RecallFilterState> =
        preferencesFlow.map { it.mainFilterState }

    override val archiveFilterFlow: Flow<RecallFilterState> =
        preferencesFlow.map { it.archiveFilterState }

    override suspend fun getSnapshot(): UserPreferences = preferencesFlow.first()

    override suspend fun updateMainFilter(filterState: RecallFilterState) {
        context.scrblDataStore.edit { prefs ->
            prefs[Keys.MAIN_FILTER_FAV] = filterState.favouritesOnly
            prefs[Keys.MAIN_FILTER_ADDRESS] = filterState.addressFilter.storageKey
            prefs[Keys.MAIN_FILTER_TYPE] = filterState.contentTypeFilter.storageKey
        }
    }

    override suspend fun updateArchiveFilter(filterState: RecallFilterState) {
        context.scrblDataStore.edit { prefs ->
            prefs[Keys.ARCHIVE_FILTER_FAV] = filterState.favouritesOnly
            prefs[Keys.ARCHIVE_FILTER_ADDRESS] = filterState.addressFilter.storageKey
            prefs[Keys.ARCHIVE_FILTER_TYPE] = filterState.contentTypeFilter.storageKey
        }
    }

    override suspend fun setFontFamily(family: FontFamilyOption) {
        context.scrblDataStore.edit { it[Keys.FONT_FAMILY] = family.storageKey }
        widgetRefresher.refreshWidgets()
    }

    override suspend fun setFontScale(scale: FontScaleOption) {
        context.scrblDataStore.edit { it[Keys.FONT_SCALE] = scale.storageKey }
        widgetRefresher.refreshWidgets()
    }

    override suspend fun setPaperTexture(texture: PaperTextureOption) {
        context.scrblDataStore.edit { it[Keys.PAPER_TEXTURE] = texture.storageKey }
        widgetRefresher.refreshWidgets()
    }

    override suspend fun setPaperEdge(edge: PaperEdgeOption) {
        context.scrblDataStore.edit { it[Keys.PAPER_EDGE] = edge.storageKey }
        widgetRefresher.refreshWidgets()
    }

    override suspend fun setStackDepth(depth: Int) {
        context.scrblDataStore.edit { it[Keys.STACK_DEPTH] = depth.coerceIn(2, 4) }
    }

    override suspend fun setShadowStyle(shadow: ShadowStyleOption) {
        context.scrblDataStore.edit { it[Keys.SHADOW_STYLE] = shadow.storageKey }
    }

    override suspend fun setWidgetSpiralStyle(spiral: WidgetSpiralOption) {
        context.scrblDataStore.edit { it[Keys.WIDGET_SPIRAL] = spiral.storageKey }
        widgetRefresher.refreshWidgets()
    }

    override suspend fun setVoiceCharacter(character: VoiceCharacterOption) {
        context.scrblDataStore.edit { it[Keys.VOICE_CHARACTER] = character.storageKey }
        widgetRefresher.refreshWidgets()
    }

    override suspend fun setAnimationsEnabled(enabled: Boolean) {
        context.scrblDataStore.edit { it[Keys.ANIMATIONS_ENABLED] = enabled }
    }

    override suspend fun setTimeFormat(format: TimeFormatOption) {
        context.scrblDataStore.edit { it[Keys.TIME_FORMAT] = format.storageKey }
        widgetRefresher.refreshWidgets()
    }

    override suspend fun setDateFormat(format: DateFormatOption) {
        context.scrblDataStore.edit { it[Keys.DATE_FORMAT] = format.storageKey }
        widgetRefresher.refreshWidgets()
    }

    override suspend fun setHapticsEnabled(enabled: Boolean) {
        context.scrblDataStore.edit { it[Keys.HAPTICS_ENABLED] = enabled }
    }

    override suspend fun setSoundEffectsEnabled(enabled: Boolean) {
        context.scrblDataStore.edit { it[Keys.SOUND_EFFECTS_ENABLED] = enabled }
    }

    override suspend fun setKeepScreenAwakeDuringRecording(enabled: Boolean) {
        context.scrblDataStore.edit { it[Keys.KEEP_SCREEN_AWAKE] = enabled }
    }

    override suspend fun resetPreferencesToDefaults() {
        context.scrblDataStore.edit { prefs ->
            prefs.clear()
        }
        widgetRefresher.refreshWidgets()
    }

    private fun mapPreferences(prefs: Preferences): UserPreferences {
        val mainFilter = RecallFilterState(
            favouritesOnly = prefs[Keys.MAIN_FILTER_FAV] ?: false,
            addressFilter = AddressFilterMode.fromKey(prefs[Keys.MAIN_FILTER_ADDRESS]),
            contentTypeFilter = ContentTypeFilterMode.fromKey(prefs[Keys.MAIN_FILTER_TYPE])
        )
        val archiveFilter = RecallFilterState(
            favouritesOnly = prefs[Keys.ARCHIVE_FILTER_FAV] ?: false,
            addressFilter = AddressFilterMode.fromKey(prefs[Keys.ARCHIVE_FILTER_ADDRESS]),
            contentTypeFilter = ContentTypeFilterMode.fromKey(prefs[Keys.ARCHIVE_FILTER_TYPE])
        )
        return UserPreferences(
            themeId = prefs[Keys.THEME_ID] ?: UserPreferences.DEFAULT_THEME_ID,
            fontFamily = FontFamilyOption.fromKey(prefs[Keys.FONT_FAMILY]),
            fontScale = FontScaleOption.fromKey(prefs[Keys.FONT_SCALE]),
            paperTexture = PaperTextureOption.fromKey(prefs[Keys.PAPER_TEXTURE]),
            paperEdge = PaperEdgeOption.fromKey(prefs[Keys.PAPER_EDGE]),
            stackDepth = (prefs[Keys.STACK_DEPTH] ?: 3).coerceIn(2, 4),
            shadowStyle = ShadowStyleOption.fromKey(prefs[Keys.SHADOW_STYLE]),
            widgetSpiralStyle = WidgetSpiralOption.fromKey(prefs[Keys.WIDGET_SPIRAL]),
            voiceCharacter = VoiceCharacterOption.fromKey(prefs[Keys.VOICE_CHARACTER]),
            animationsEnabled = prefs[Keys.ANIMATIONS_ENABLED] ?: true,
            timeFormat = TimeFormatOption.fromKey(prefs[Keys.TIME_FORMAT]),
            dateFormat = DateFormatOption.fromKey(prefs[Keys.DATE_FORMAT]),
            hapticsEnabled = prefs[Keys.HAPTICS_ENABLED] ?: true,
            soundEffectsEnabled = prefs[Keys.SOUND_EFFECTS_ENABLED] ?: false,
            keepScreenAwakeDuringRecording = prefs[Keys.KEEP_SCREEN_AWAKE] ?: false,
            mainFilterState = mainFilter,
            archiveFilterState = archiveFilter
        )
    }
}
