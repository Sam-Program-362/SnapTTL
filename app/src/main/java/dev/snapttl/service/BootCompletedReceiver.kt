package dev.snapttl.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.snapttl.SnapTtlApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Restarts the capture monitor after a device boot when the service was enabled, and
 * re-arms every queued deletion so a reboot can never strand the expiry queue
 * (WorkManager itself persists, this is the safety net).
 */
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_LOCKED_BOOT_COMPLETED) return
        val app = context.applicationContext as? SnapTtlApp ?: return
        if (app.services.prefs.serviceEnabled) {
            CaptureMonitorService.start(context)
        }
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                app.services.expiryRepo.rescheduleAll()
            } finally {
                pending.finish()
            }
        }
    }
}
