package dev.snapttl.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import dev.snapttl.R

object NotificationChannels {
    const val CAPTURE = "capture_monitor"
    const val RELAUNCH = "service_relaunch_reminder"
    const val PERMISSION = "permission_error"

    fun create(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channels = listOf(
            NotificationChannel(
                CAPTURE,
                context.getString(R.string.channel_capture),
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = context.getString(R.string.channel_capture_desc) },
            NotificationChannel(
                RELAUNCH,
                context.getString(R.string.channel_relaunch),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = context.getString(R.string.channel_relaunch_desc) },
            NotificationChannel(
                PERMISSION,
                context.getString(R.string.channel_permission),
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = context.getString(R.string.channel_permission_desc) }
        )
        manager.createNotificationChannels(channels)
    }

    fun captureNotification(context: Context): NotificationCompat.Builder =
        NotificationCompat.Builder(context, CAPTURE)
            .setContentTitle(context.getString(R.string.notification_capture_title))
            .setContentText(context.getString(R.string.notification_capture_text))
            .setSmallIcon(R.drawable.snapttl_logo)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
}
