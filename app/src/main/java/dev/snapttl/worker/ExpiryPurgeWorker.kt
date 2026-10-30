package dev.snapttl.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.snapttl.R
import dev.snapttl.SnapTtlApp
import dev.snapttl.data.repository.DeletionOutcome
import dev.snapttl.service.ToastHelper

/**
 * Executes a scheduled screenshot deletion: resolves the MediaStore item, deletes it
 * (SAF tree first, MediaStore fallback), updates the retained counters and removes the
 * expiry row plus its search-index entry.
 *
 * Failure policy:
 * - the media item is already gone ⇒ success (nothing left to do),
 * - the deletion itself failed ⇒ `retry()` with the queue row kept, so the timer is
 *   not lost; after [MAX_ATTEMPTS] the row stays for the user to act on in Scheduled.
 *
 * Clean-room equivalent of Mark's `DeleteScreenshotWorkManager`.
 */
class ExpiryPurgeWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as SnapTtlApp
        val services = app.services

        val workId = inputData.getString(KEY_WORK_ID)
        val item = when {
            !workId.isNullOrBlank() -> services.expiryRepo.findByWorkManagerId(workId)
            else -> services.expiryRepo.findById(inputData.getLong(KEY_ROW_ID, -1L))
        } ?: return Result.success() // cancelled while queued — nothing to do

        val media = services.screenshotRepo.findScreenshot(item.screenshotId)
        if (media == null) {
            // Already deleted elsewhere (manual delete, other tool). Clean up only.
            services.searchDao.deleteByScreenshotId(item.screenshotId)
            services.expiryRepo.onPurged(item.screenshotId)
            return Result.success()
        }

        val outcome = services.screenshotRepo.deleteScreenshot(media)

        return when (outcome) {
            DeletionOutcome.DELETED -> {
                services.prefs.totalScreenshotsDeleted += 1
                services.prefs.totalMemorySavedBytes += media.sizeBytes
                services.analytics.logEvent("screenshot_deleted")
                services.searchDao.deleteByScreenshotId(item.screenshotId)
                services.expiryRepo.onPurged(item.screenshotId)
                ToastHelper.show(applicationContext, applicationContext.getString(R.string.toast_deleted))
                Result.success()
            }
            DeletionOutcome.FAILED -> {
                services.analytics.logEvent("screenshot_deleted_failed")
                if (runAttemptCount >= MAX_ATTEMPTS) {
                    // Keep the row so it stays visible under Scheduled; stop retrying.
                    ToastHelper.show(
                        applicationContext,
                        applicationContext.getString(R.string.toast_delete_failed)
                    )
                    Result.success()
                } else {
                    Result.retry()
                }
            }
        }
    }

    companion object {
        const val KEY_WORK_ID = "work_id"
        const val KEY_ROW_ID = "row_id"
        const val KEY_SCREENSHOT_ID = "screenshot_id"
        const val KEY_FILE_PATH = "file_path"

        /** Deletion retries before giving up (exponential backoff between attempts). */
        const val MAX_ATTEMPTS = 3
    }
}
