package dev.snapttl.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.snapttl.SnapTtlApp
import kotlinx.coroutines.launch

/**
 * Handles the fallback-notification actions (delete now / delete in the
 * default time / keep) without opening the app.
 */
class CaptureActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val screenshotId = intent.getLongExtra(EXTRA_SCREENSHOT_ID, -1L)
        if (screenshotId <= 0L) return

        val app = context.applicationContext as SnapTtlApp
        val pending = goAsync()
        app.appScope.launch {
            try {
                when (intent.action) {
                    ACTION_DELETE_NOW -> CaptureActions.deleteNow(app.services, screenshotId)
                    ACTION_DELETE_IN -> app.services.expiryRepo.scheduleIn(
                        screenshotId = screenshotId,
                        durationSeconds = app.services.prefs.autoDeleteDurationSeconds,
                        filePath = ""
                    )
                    // ACTION_KEEP: nothing to do beyond dismissing.
                }
            } finally {
                CaptureNotificationFallback.cancel(context, screenshotId)
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_DELETE_NOW = "dev.snapttl.action.DELETE_NOW"
        const val ACTION_DELETE_IN = "dev.snapttl.action.DELETE_IN"
        const val ACTION_KEEP = "dev.snapttl.action.KEEP"
        const val EXTRA_SCREENSHOT_ID = "snapttl_screenshot_id"
    }
}
