package dev.snapttl.data.repository

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dev.snapttl.data.expiry.ExpiryQueueDao
import dev.snapttl.data.expiry.ExpiryQueueItem
import dev.snapttl.domain.TimeChips
import dev.snapttl.worker.ExpiryPurgeWorker
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.TimeUnit as JavaTimeUnit

/**
 * Expiry queue boundary.
 *
 * One screenshot has at most one armed timer:
 * - the Room row is keyed by screenshot (existing rows are replaced),
 * - the WorkManager unique work id is stable (`snapttl_purge_<screenshotId>`),
 * - the purge request carries the **initial delay** up to `deleteTimeEpochMs`,
 * - re-arming uses `ExistingWorkPolicy.REPLACE` so the new delay wins,
 * - cancelling removes both the row and the pending work.
 */
class ExpiryRepository(
    private val dao: ExpiryQueueDao,
    private val workManager: WorkManager
) {

    fun observeQueue(): Flow<List<ExpiryQueueItem>> = dao.observeAll()

    /**
     * Arms (or re-arms) the deletion timer for [screenshotId] at [deleteTimeEpochMs].
     * Any previously armed timer for the same screenshot is cancelled first.
     */
    suspend fun schedule(
        screenshotId: Long,
        filePath: String,
        deleteTimeEpochMs: Long,
        durationSeconds: Long
    ): ExpiryQueueItem {
        cancelByScreenshot(screenshotId)

        val now = System.currentTimeMillis()
        val (value, unit) = TimeChips.unitAndValue(durationSeconds)
        val item = ExpiryQueueItem(
            filePath = filePath,
            deleteTimeEpochMs = deleteTimeEpochMs,
            timeUnit = unit.name,
            workManagerId = workIdFor(screenshotId),
            createdAtEpochMillis = now,
            screenshotId = screenshotId
        )
        dao.insertOrReplace(item)
        enqueuePurge(item)
        return item
    }

    /** Convenience for "delete in [durationSeconds] from now". */
    suspend fun scheduleIn(
        screenshotId: Long,
        durationSeconds: Long,
        filePath: String = ""
    ): ExpiryQueueItem =
        schedule(
            screenshotId = screenshotId,
            filePath = filePath,
            deleteTimeEpochMs = System.currentTimeMillis() + durationSeconds * 1_000L,
            durationSeconds = durationSeconds
        )

    suspend fun cancel(id: Long) {
        dao.findById(id)?.let { cancelByScreenshot(it.screenshotId) }
    }

    suspend fun cancelByScreenshot(screenshotId: Long) {
        workManager.cancelUniqueWork(workIdFor(screenshotId))
        dao.deleteByScreenshotId(screenshotId)
    }

    suspend fun cancelByIds(ids: List<Long>) {
        if (ids.isEmpty()) return
        ids.forEach { id -> cancel(id) }
    }

    suspend fun findById(id: Long): ExpiryQueueItem? = dao.findById(id)

    suspend fun findByScreenshot(screenshotId: Long): ExpiryQueueItem? =
        dao.findByScreenshotId(screenshotId)

    suspend fun findByWorkManagerId(workManagerId: String): ExpiryQueueItem? =
        dao.findByWorkManagerId(workManagerId)

    /**
     * Crash/boot resilience: re-arms the WorkManager request for every queued row.
     * Items already past their delete time run immediately. Called from app start
     * and BOOT_COMPLETED so a lost WorkManager entry can never strand a row.
     */
    suspend fun rescheduleAll() {
        val rows = dao.observeAllSnapshot()
        for (item in rows) {
            enqueuePurge(item)
        }
    }

    /** Removes the queue row for [screenshotId] after the purge succeeded. */
    suspend fun onPurged(screenshotId: Long) {
        dao.deleteByScreenshotId(screenshotId)
    }

    private fun enqueuePurge(item: ExpiryQueueItem) {
        val delayMs = (item.deleteTimeEpochMs - System.currentTimeMillis())
            .coerceAtLeast(0L)
        val request = OneTimeWorkRequestBuilder<ExpiryPurgeWorker>()
            .setInitialDelay(delayMs, JavaTimeUnit.MILLISECONDS)
            .setInputData(
                workDataOf(
                    ExpiryPurgeWorker.KEY_WORK_ID to item.workManagerId,
                    ExpiryPurgeWorker.KEY_ROW_ID to item.id,
                    ExpiryPurgeWorker.KEY_SCREENSHOT_ID to item.screenshotId,
                    ExpiryPurgeWorker.KEY_FILE_PATH to item.filePath
                )
            )
            .build()
        workManager.enqueueUniqueWork(item.workManagerId, ExistingWorkPolicy.REPLACE, request)
    }

    companion object {
        fun workIdFor(screenshotId: Long): String = "snapttl_purge_$screenshotId"
    }
}
