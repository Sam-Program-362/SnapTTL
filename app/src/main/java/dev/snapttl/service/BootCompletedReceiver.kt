package dev.snapttl.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.snapttl.SnapTtlApp

/** Restarts the capture monitor after a device boot when the service was enabled. */
class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_LOCKED_BOOT_COMPLETED) return
        val app = context.applicationContext as? SnapTtlApp ?: return
        if (app.services.prefs.serviceEnabled) {
            CaptureMonitorService.start(context)
        }
    }
}
