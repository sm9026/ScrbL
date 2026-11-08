package com.scrbl.app.domain.repository

import com.scrbl.app.core.result.AppResult
import com.scrbl.app.domain.model.Recall
import com.scrbl.app.domain.model.RecallFilterState
import com.scrbl.app.domain.model.UserPreferences
import kotlinx.coroutines.flow.Flow

/**
 * Single-source-of-truth boundary for Recall records.
 * Notice: No `updateContent`, `updateTitle`, or `deleteRecall` methods exist by design
 * (02_REQUIREMENTS_AND_RULES.md & 08_ANDROID_ARCHITECTURE.md).
 */
interface RecallRepository {
    fun observeAllRecallsNewestFirst(): Flow<List<Recall>>
    fun observeRecallsForYear(yearPrefix: String): Flow<List<Recall>>
    fun observeRecallsForDay(capturedLocalDate: String): Flow<List<Recall>>
    fun observeFavouritesNewestFirst(): Flow<List<Recall>>
    fun observeRecallById(id: String): Flow<Recall?>

    suspend fun getRecallById(id: String): Recall?
    suspend fun getRecallsForDay(capturedLocalDate: String): List<Recall>
    suspend fun getLatestWidgetRecalls(limit: Int = 19): List<Recall>
    suspend fun getDistinctYearsNewestFirst(): List<Int>
    suspend fun getDistinctMonthsForYearNewestFirst(year: Int): List<Int>

    suspend fun insertCommittedRecall(recall: Recall): AppResult<Recall>
    suspend fun toggleAddressed(id: String): AppResult<Boolean>
    suspend fun toggleFavourite(id: String): AppResult<Boolean>
}

/**
 * Repository boundary for Preferences DataStore and independent filter contexts.
 */
interface PreferencesRepository {
    val preferencesFlow: Flow<UserPreferences>
    val mainFilterFlow: Flow<RecallFilterState>
    val archiveFilterFlow: Flow<RecallFilterState>

    suspend fun getSnapshot(): UserPreferences
    suspend fun updateMainFilter(filterState: RecallFilterState)
    suspend fun updateArchiveFilter(filterState: RecallFilterState)

    suspend fun setFontFamily(family: com.scrbl.app.domain.model.FontFamilyOption)
    suspend fun setFontScale(scale: com.scrbl.app.domain.model.FontScaleOption)
    suspend fun setPaperTexture(texture: com.scrbl.app.domain.model.PaperTextureOption)
    suspend fun setPaperEdge(edge: com.scrbl.app.domain.model.PaperEdgeOption)
    suspend fun setStackDepth(depth: Int)
    suspend fun setShadowStyle(shadow: com.scrbl.app.domain.model.ShadowStyleOption)
    suspend fun setWidgetSpiralStyle(spiral: com.scrbl.app.domain.model.WidgetSpiralOption)
    suspend fun setVoiceCharacter(character: com.scrbl.app.domain.model.VoiceCharacterOption)
    suspend fun setAnimationsEnabled(enabled: Boolean)
    suspend fun setTimeFormat(format: com.scrbl.app.core.time.TimeFormatOption)
    suspend fun setDateFormat(format: com.scrbl.app.core.time.DateFormatOption)
    suspend fun setHapticsEnabled(enabled: Boolean)
    suspend fun setSoundEffectsEnabled(enabled: Boolean)
    suspend fun setKeepScreenAwakeDuringRecording(enabled: Boolean)

    /**
     * Resets only user preferences to defaults. Never touches Room recalls or voice files.
     */
    suspend fun resetPreferencesToDefaults()
}

/**
 * Boundary for managing app-private voice recording files (`files/recalls/audio/<uuid>.m4a`)
 * and temporary capture files (`cache/recording_temp/<session>.tmp`).
 */
interface VoiceFileStorage {
    fun createTempRecordingFile(sessionId: String): java.io.File
    fun resolvePermanentFile(relativePath: String): java.io.File
    fun existsAndNonEmpty(relativePath: String): Boolean
    suspend fun promoteTempToPermanent(tempFile: java.io.File, recallId: String): AppResult<String>
    suspend fun deleteUncommittedFile(relativePath: String)
    suspend fun deleteTempFile(tempFile: java.io.File?)
    suspend fun cleanupAbandonedTempFiles(): Int
}

/**
 * Notifies the launcher widget whenever recalls or appearance preferences change.
 */
interface WidgetRefresher {
    fun refreshWidgets()
}
