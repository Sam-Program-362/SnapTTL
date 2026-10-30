package dev.snapttl.data.repository

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Operation
import androidx.work.WorkManager
import androidx.work.workDataOf
import dev.snapttl.data.expiry.ExpiryQueueItem
import dev.snapttl.data.expiry.ExpiryQueueDao
import dev.snapttl.worker.ExpiryPurgeWorker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Expiry queue boundary: persists the scheduled item and enqueues the unique purge
 * worker keyed by [ExpiryQueueItem.workManagerId].
 */
class ExpiryRepository(
    private val dao: ExpiryQueueDao,
    private val workManager: WorkManager
) {
    fun observeQueue(): Flow<List<ExpiryQueueItem>> = dao.observeAll()

    suspend fun upsert(item: ExpiryQueueItem): Long {
        val rowId = dao.insertOrReplace(item)
        enqueuePurge(item)
        return rowId
    }

    suspend fun cancel(id: Long) {
        dao.deleteById(id)
    }

    suspend fun cancelByScreenshot(screenshotId: Long) {
        dao.deleteByScreenshotId(screenshotId)
    }

    suspend fun cancelByIds(ids: List<Long>) {
        if (ids.isEmpty()) return
        dao.deleteByIds(ids)
    }

    suspend fun findById(id: Long): ExpiryQueueItem? = dao.findById(id)

    suspend fun findByScreenshot(screenshotId: Long): ExpiryQueueItem? =
        dao.findByScreenshotId(screenshotId)

    private fun enqueuePurge(item: ExpiryQueueItem) {
        val request = OneTimeWorkRequestBuilder<ExpiryPurgeWorker>()
            .setInputData(
                workDataOf(
                    ExpiryPurgeWorker.KEY_WORK_ID to item.workManagerId,
                    ExpiryPurgeWorker.KEY_ROW_ID to item.id,
                    ExpiryPurgeWorker.KEY_SCREENSHOT_ID to item.screenshotId,
                    ExpiryPurgeWorker.KEY_FILE_PATH to item.filePath
                )
            )
            .build()
        workManager.enqueueUniqueWork(item.workManagerId, ExistingWorkPolicy.KEEP, request)
    }
}
