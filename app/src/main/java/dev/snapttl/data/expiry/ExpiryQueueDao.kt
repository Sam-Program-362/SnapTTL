package dev.snapttl.data.expiry

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpiryQueueDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(item: ExpiryQueueItem): Long

    @Query("SELECT * FROM expiry_queue_item ORDER BY createdAtEpochMillis DESC")
    fun observeAll(): Flow<List<ExpiryQueueItem>>

    @Query("SELECT * FROM expiry_queue_item ORDER BY createdAtEpochMillis DESC")
    suspend fun observeAllSnapshot(): List<ExpiryQueueItem>

    @Query("SELECT * FROM expiry_queue_item WHERE id = :id")
    suspend fun findById(id: Long): ExpiryQueueItem?

    @Query("SELECT * FROM expiry_queue_item WHERE workManagerId = :workManagerId LIMIT 1")
    suspend fun findByWorkManagerId(workManagerId: String): ExpiryQueueItem?

    @Query("SELECT * FROM expiry_queue_item WHERE screenshotId = :screenshotId LIMIT 1")
    suspend fun findByScreenshotId(screenshotId: Long): ExpiryQueueItem?

    @Query("DELETE FROM expiry_queue_item WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM expiry_queue_item WHERE screenshotId = :screenshotId")
    suspend fun deleteByScreenshotId(screenshotId: Long)

    @Query("DELETE FROM expiry_queue_item WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)
}
