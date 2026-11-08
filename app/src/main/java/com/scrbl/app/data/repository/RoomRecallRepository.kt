package com.scrbl.app.data.repository

import com.scrbl.app.core.result.AppResult
import com.scrbl.app.core.result.FailureReason
import com.scrbl.app.data.local.room.RecallDao
import com.scrbl.app.data.local.room.RecallEntity
import com.scrbl.app.domain.model.Recall
import com.scrbl.app.domain.repository.RecallRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Room-backed implementation of `RecallRepository`.
 * Enforces immutability of core fields and exposes only narrow toggle operations for
 * `isAddressed` and `isFavourite`.
 */
class RoomRecallRepository(
    private val recallDao: RecallDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : RecallRepository {

    override fun observeAllRecallsNewestFirst(): Flow<List<Recall>> =
        recallDao.observeAllNewestFirst().map { list -> list.map { it.toDomainModel() } }

    override fun observeRecallsForYear(yearPrefix: String): Flow<List<Recall>> =
        recallDao.observeRecallsForYear(yearPrefix).map { list -> list.map { it.toDomainModel() } }

    override fun observeRecallsForDay(capturedLocalDate: String): Flow<List<Recall>> =
        recallDao.observeRecallsForDay(capturedLocalDate).map { list -> list.map { it.toDomainModel() } }

    override fun observeFavouritesNewestFirst(): Flow<List<Recall>> =
        recallDao.observeFavouritesNewestFirst().map { list -> list.map { it.toDomainModel() } }

    override fun observeRecallById(id: String): Flow<Recall?> =
        recallDao.observeRecallById(id).map { it?.toDomainModel() }

    override suspend fun getRecallById(id: String): Recall? = withContext(ioDispatcher) {
        recallDao.getRecallById(id)?.toDomainModel()
    }

    override suspend fun getRecallsForDay(capturedLocalDate: String): List<Recall> =
        withContext(ioDispatcher) {
            recallDao.getRecallsForDay(capturedLocalDate).map { it.toDomainModel() }
        }

    override suspend fun getLatestWidgetRecalls(limit: Int): List<Recall> =
        withContext(ioDispatcher) {
            recallDao.getLatestRecalls(limit.coerceIn(1, 19)).map { it.toDomainModel() }
        }

    override suspend fun getDistinctYearsNewestFirst(): List<Int> =
        withContext(ioDispatcher) {
            recallDao.getDistinctYearsNewestFirst()
        }

    override suspend fun getDistinctMonthsForYearNewestFirst(year: Int): List<Int> =
        withContext(ioDispatcher) {
            recallDao.getDistinctMonthsForYearNewestFirst(String.format(Locale.US, "%04d", year))
        }

    override suspend fun insertCommittedRecall(recall: Recall): AppResult<Recall> =
        withContext(ioDispatcher) {
            try {
                val entity = RecallEntity.fromDomainModel(recall)
                recallDao.insertRecall(entity)
                AppResult.Success(recall)
            } catch (e: Exception) {
                AppResult.Error(
                    FailureReason.DATABASE_ERROR,
                    e.message ?: "Failed to insert recall into Room database."
                )
            }
        }

    override suspend fun toggleAddressed(id: String): AppResult<Boolean> =
        withContext(ioDispatcher) {
            try {
                val current = recallDao.getRecallById(id)
                    ?: return@withContext AppResult.Error(
                        FailureReason.RECORD_NOT_FOUND,
                        "Recall $id not found."
                    )
                val nextValue = !current.isAddressed
                recallDao.updateAddressedStatus(id, nextValue)
                AppResult.Success(nextValue)
            } catch (e: Exception) {
                AppResult.Error(
                    FailureReason.DATABASE_ERROR,
                    e.message ?: "Failed to toggle addressed state."
                )
            }
        }

    override suspend fun toggleFavourite(id: String): AppResult<Boolean> =
        withContext(ioDispatcher) {
            try {
                val current = recallDao.getRecallById(id)
                    ?: return@withContext AppResult.Error(
                        FailureReason.RECORD_NOT_FOUND,
                        "Recall $id not found."
                    )
                val nextValue = !current.isFavourite
                recallDao.updateFavouriteStatus(id, nextValue)
                AppResult.Success(nextValue)
            } catch (e: Exception) {
                AppResult.Error(
                    FailureReason.DATABASE_ERROR,
                    e.message ?: "Failed to toggle favourite state."
                )
            }
        }
}
