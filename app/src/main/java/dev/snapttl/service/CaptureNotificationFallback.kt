package dev.snapttl.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dev.snapttl.R
import dev.snapttl.domain.DeletionDuration

/**
 * Fallback UI when the floating overlay cannot be shown (no
 * SYSTEM_ALERT_WINDOW, or the window could not be added): a heads-up
 * notification with direct actions so a screenshot decision never silently
 * disappears.
 */
object CaptureNotificationFallback {

    private const val EXTRA_SCREENSHOT_ID = "snapttl_screenshot_id"

    fun show(context: Context, screenshotId: Long, displayName: String, defaultSeconds: Long) {
        val manager = NotificationManagerCompat.from(context)
        val id = notificationId(screenshotId)

        val deleteNow = action(
            context, CaptureActionReceiver.ACTION_DELETE_NOW,
            context.getString(R.string.notif_action_delete_now), screenshotId
        )
        val deleteIn = action(
            context, CaptureActionReceiver.ACTION_DELETE_IN,
            context.getString(
                R.string.overlay_delete_in_fmt,
                DeletionDuration.label(sanitize(defaultSeconds))
            ),
            screenshotId
        )
        val keep = action(
            context, CaptureActionReceiver.ACTION_KEEP,
            context.getString(R.string.notif_action_keep), screenshotId
        )

        val notification = NotificationCompat.Builder(context, NotificationChannels.ACTIONS)
            .setSmallIcon(R.drawable.snapttl_logo)
            .setContentTitle(context.getString(R.string.notif_fallback_title))
            .setContentText(
                context.getString(
                    R.string.notif_fallback_text,
                    displayName.ifBlank { context.getString(R.string.overlay_new_screenshot) }
                )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .addAction(0, context.getString(R.string.notif_action_delete_now), deleteNow)
            .addAction(0, context.getString(R.string.overlay_delete_in_fmt, DeletionDuration.label(sanitize(defaultSeconds))), deleteIn)
            .addAction(0, context.getString(R.string.notif_action_keep), keep)
            .build()

        // POST_NOTIFICATIONS may be revoked on 13+; a missing notification is
        // strictly better than crashing the monitor.
        runCatching { manager.notify(id, notification) }
    }

    fun cancel(context: Context, screenshotId: Long) {
        NotificationManagerCompat.from(context).cancel(notificationId(screenshotId))
    }

    private fun action(context: Context, action: String, title: String, screenshotId: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            (action.hashCode() * 31 + screenshotId.hashCode()),
            Intent(context, CaptureActionReceiver::class.java).apply {
                this.action = action
                putExtra(EXTRA_SCREENSHOT_ID, screenshotId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun sanitize(seconds: Long): Long =
        if (DeletionDuration.isValidSeconds(seconds)) seconds else DeletionDuration.DEFAULT_SECONDS

    private fun notificationId(screenshotId: Long): Int = (screenshotId and 0x7FFFFFFF).toInt()
}
