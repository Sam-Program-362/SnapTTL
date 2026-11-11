package dev.snapttl.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dev.snapttl.R
import dev.snapttl.ui.MainActivity

/**
 * Makes the one silent dead-end in SnapTTL visible: on Android 11+ an app
 * cannot delete screenshots it does not own through MediaStore — it needs
 * the user to connect the Screenshots folder once (SAF tree grant, see
 * Settings). Before this existed, every deletion path (quick options,
 * custom timer, date/time, delete-now, and the purge worker) failed
 * *silently*, which read as "the buttons do nothing".
 *
 * This helper posts a heads-up notification (throttled to once an hour)
 * that deep-links straight into Settings → Screenshots folder.
 */
object FolderAccessNotifier {

    private const val NOTIFICATION_ID = 4711
    private const val THROTTLE_MS = 60L * 60L * 1_000L

    /** Posts the folder-access notification unless one went out recently. */
    fun notifyIfMissing(context: Context) {
        val app = context.applicationContext
        val services = (app as? dev.snapttl.SnapTtlApp)?.services ?: return
        if (!services.prefs.screenshotFolderTreeUri.isNullOrEmpty()) return

        val now = System.currentTimeMillis()
        if (now - services.prefs.folderNudgeLastMs < THROTTLE_MS) return
        services.prefs.folderNudgeLastMs = now

        val intent = Intent(app, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_FOLDER_PICKER, true)
        }
        val pending = PendingIntent.getActivity(
            app,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(app, NotificationChannels.PERMISSION)
            .setSmallIcon(R.drawable.snapttl_logo)
            .setContentTitle(app.getString(R.string.notification_folder_title))
            .setContentText(app.getString(R.string.notification_folder_text))
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(app.getString(R.string.notification_folder_text))
            )
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .build()

        runCatching {
            NotificationManagerCompat.from(app).notify(NOTIFICATION_ID, notification)
        }
    }
}
