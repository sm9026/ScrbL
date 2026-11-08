package com.scrbl.app.data.local.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO for `recalls`.
 *
 * Strict immutability enforcement (02_REQUIREMENTS_AND_RULES.md & 09_DATA_MODEL_AND_STORAGE.md):
 * - Insert uses `OnConflictStrategy.ABORT` so an existing recall ID can never be overwritten.
 * - No `@Delete` or `@Update` on full entities exists.
 * - Only narrow column-specific `UPDATE` statements for `is_addressed` and `is_favourite` exist.
 */
@Dao
interface RecallDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRecall(entity: RecallEntity)

    @Query("SELECT * FROM recalls ORDER BY created_at_epoch_millis DESC")
    fun observeAllNewestFirst(): Flow<List<RecallEntity>>

    @Query("SELECT * FROM recalls WHERE substr(captured_local_date, 1, 4) = :yearPrefix ORDER BY created_at_epoch_millis DESC")
    fun observeRecallsForYear(yearPrefix: String): Flow<List<RecallEntity>>

    @Query("SELECT * FROM recalls WHERE captured_local_date = :capturedLocalDate ORDER BY created_at_epoch_millis DESC")
    fun observeRecallsForDay(capturedLocalDate: String): Flow<List<RecallEntity>>

    @Query("SELECT * FROM recalls WHERE is_favourite = 1 ORDER BY created_at_epoch_millis DESC")
    fun observeFavouritesNewestFirst(): Flow<List<RecallEntity>>

    @Query("SELECT * FROM recalls WHERE id = :id LIMIT 1")
    fun observeRecallById(id: String): Flow<RecallEntity?>

    @Query("SELECT * FROM recalls WHERE id = :id LIMIT 1")
    suspend fun getRecallById(id: String): RecallEntity?

    @Query("SELECT * FROM recalls WHERE captured_local_date = :capturedLocalDate ORDER BY created_at_epoch_millis DESC")
    suspend fun getRecallsForDay(capturedLocalDate: String): List<RecallEntity>

    /**
     * Widget query: latest N (up to 19) recalls newest first, across all types and statuses.
     */
    @Query("SELECT * FROM recalls ORDER BY created_at_epoch_millis DESC LIMIT :limit")
    suspend fun getLatestRecalls(limit: Int = 19): List<RecallEntity>

    @Query("SELECT DISTINCT CAST(substr(captured_local_date, 1, 4) AS INTEGER) AS yr FROM recalls ORDER BY yr DESC")
    suspend fun getDistinctYearsNewestFirst(): List<Int>

    @Query("SELECT DISTINCT CAST(substr(captured_local_date, 6, 2) AS INTEGER) AS mo FROM recalls WHERE substr(captured_local_date, 1, 4) = :yearPrefix ORDER BY mo DESC")
    suspend fun getDistinctMonthsForYearNewestFirst(yearPrefix: String): List<Int>

    /**
     * Mutates ONLY `is_addressed`. Content, title, timestamp, and zone remain untouched.
     */
    @Query("UPDATE recalls SET is_addressed = :isAddressed WHERE id = :id")
    suspend fun updateAddressedStatus(id: String, isAddressed: Boolean): Int

    /**
     * Mutates ONLY `is_favourite`. Content, title, timestamp, and zone remain untouched.
     */
    @Query("UPDATE recalls SET is_favourite = :isFavourite WHERE id = :id")
    suspend fun updateFavouriteStatus(id: String, isFavourite: Boolean): Int
}
