package dev.snapttl.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.snapttl.SnapTtlApp
import dev.snapttl.data.repository.DeletionOutcome
import dev.snapttl.service.ToastHelper

/**
 * Executes a scheduled screenshot deletion: resolves the MediaStore item, deletes it
 * (SAF tree first, MediaStore fallback), updates the retained counters and removes the
 * expiry row plus its search-index entry.
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

        val rowId = inputData.getLong(KEY_ROW_ID, -1L)
        val screenshotId = inputData.getLong(KEY_SCREENSHOT_ID, -1L)
        val item = services.expiryRepo.findById(rowId) ?: return Result.success()

        val media = services.screenshotRepo.findScreenshot(item.screenshotId)
        val outcome = if (media != null) {
            services.screenshotRepo.deleteScreenshot(media)
        } else {
            DeletionOutcome.FAILED
        }

        if (outcome == DeletionOutcome.DELETED && media != null) {
            services.prefs.totalScreenshotsDeleted += 1
            services.prefs.totalMemorySavedBytes += media.sizeBytes
            services.analytics.logEvent("screenshot_deleted")
        } else {
            services.analytics.logEvent("screenshot_deleted_failed")
        }

        services.searchDao.deleteByScreenshotId(item.screenshotId)
        services.expiryRepo.cancel(item.id)

        if (outcome != DeletionOutcome.DELETED) {
            ToastHelper.show(appContext, appContext.getString(R.string.toast_delete_failed))
        } else {
            ToastHelper.show(appContext, appContext.getString(R.string.toast_deleted))
        }
        return Result.success()
    }

    companion object {
        const val KEY_WORK_ID = "work_id"
        const val KEY_ROW_ID = "row_id"
        const val KEY_SCREENSHOT_ID = "screenshot_id"
        const val KEY_FILE_PATH = "file_path"
    }
}
