package dev.snapttl.service

import dev.snapttl.data.repository.DeletionOutcome
import dev.snapttl.di.Services

/** Shared capture actions used by the UI and the purge worker. */
object CaptureActions {

    /** Deletes a screenshot immediately and reconciles its expiry row and index entry. */
    suspend fun deleteNow(services: Services, screenshotId: Long): Boolean {
        val media = services.screenshotRepo.findScreenshot(screenshotId) ?: return false
        val outcome = services.screenshotRepo.deleteScreenshot(media)
        if (outcome == DeletionOutcome.DELETED) {
            services.prefs.totalScreenshotsDeleted += 1
            services.prefs.totalMemorySavedBytes += media.sizeBytes
            services.analytics.logEvent("screenshot_deleted_immediately")
        } else {
            services.analytics.logEvent("screenshot_deleted_failed")
        }
        services.searchDao.deleteByScreenshotId(screenshotId)
        services.expiryRepo.cancelByScreenshot(screenshotId)
        return outcome == DeletionOutcome.DELETED
    }
}
