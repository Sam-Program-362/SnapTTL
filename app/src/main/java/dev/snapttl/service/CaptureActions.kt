package dev.snapttl.service

import dev.snapttl.R
import dev.snapttl.data.repository.DeletionOutcome
import dev.snapttl.di.Services

/**
 * Shared capture actions used by the UI and the purge worker.
 *
 * Every caller gets feedback: a success toast on deletion, and on failure a
 * clear toast plus the folder-access heads-up notification — a deletion that
 * silently does nothing is the worst possible outcome for trust.
 */
object CaptureActions {

    /** Deletes a screenshot immediately and reconciles its expiry row and index entry. */
    suspend fun deleteNow(services: Services, screenshotId: Long): Boolean {
        val media = services.screenshotRepo.findScreenshot(screenshotId) ?: return false
        val outcome = services.screenshotRepo.deleteScreenshot(media)
        if (outcome == DeletionOutcome.DELETED) {
            services.prefs.totalScreenshotsDeleted += 1
            services.prefs.totalMemorySavedBytes += media.sizeBytes
            services.analytics.logEvent("screenshot_deleted_immediately")
            ToastHelper.show(services.context, services.context.getString(R.string.toast_deleted))
        } else {
            services.analytics.logEvent("screenshot_deleted_failed")
            ToastHelper.show(services.context, services.context.getString(R.string.toast_delete_failed))
            // Most likely cause on Android 11+: the Screenshots folder was never
            // connected, so MediaStore refuses to delete system-owned shots.
            FolderAccessNotifier.notifyIfMissing(services.context)
        }
        services.searchDao.deleteByScreenshotId(screenshotId)
        services.expiryRepo.cancelByScreenshot(screenshotId)
        return outcome == DeletionOutcome.DELETED
    }
}
