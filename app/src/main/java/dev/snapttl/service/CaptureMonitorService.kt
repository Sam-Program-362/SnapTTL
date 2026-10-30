package dev.snapttl.service

import android.app.Service
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import androidx.core.app.NotificationManagerCompat
import dev.snapttl.di.Services
import dev.snapttl.domain.EntitlementState
import dev.snapttl.domain.ScreenshotFilter
import dev.snapttl.domain.TimeUnit
import dev.snapttl.domain.TimeUtils
import dev.snapttl.ui.overlay.CaptureOverlayActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service that watches MediaStore for new screenshots, validates them
 * with [ScreenshotFilter] and either shows the floating delete-timer overlay or
 * (for silent auto-delete) schedules deletion immediately.
 *
 * Clean-room equivalent of Mark's `ScreenshotWatcherForegroundService`.
 */
class CaptureMonitorService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var services: Services
    private var observer: ContentObserver? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        services = (application as dev.snapttl.SnapTtlApp).services
        NotificationChannels.create(this)
        startWatching()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForegroundCompat()
        return START_STICKY
    }

    override fun onDestroy() {
        observer?.let { contentResolver.unregisterContentObserver(it) }
        observer = null
        isRunning = false
        services.analytics.logEvent("foreground_service_stopped")
        scope.cancel()
        super.onDestroy()
    }

    private fun startForegroundCompat() {
        val notification = NotificationChannels.captureNotification(this).build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        isRunning = true
        services.analytics.logEvent("foreground_service_started")
    }

    private fun startWatching() {
        if (observer != null) return
        val obs = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                if (uri != null) handler.post { handleChange(uri) }
            }
        }
        contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            obs
        )
        observer = obs
    }

    private fun handleChange(uri: Uri) {
        val info = queryScreenshotInfo(uri) ?: return
        if (!ScreenshotFilter.isValidScreenshotEvent(uri.toString(), info.displayName, info.path)) return
        services.analytics.logEvent("screenshot_taken_no_filter", mapOf("full_file_path_no_filter" to info.path))

        val state = services.commonState.snapshot()
        val now = System.currentTimeMillis()
        val ent = EntitlementState(
            premiumPurchased = state.premiumPurchased || services.prefs.havePremiumPurchased,
            adsRemoved = state.adsRemoved || services.prefs.haveAdsRemoved,
            refillExpiryEpochMs = services.prefs.refillExpiryEpochMs
        )

        if (services.prefs.noDialogAutoDeleteEnabled && ent.hasUnlockedFeatures(now)) {
            val minutes = state.timeChips.firstOrNull() ?: EntitlementState.FREE_TIME_CHIPS.first()
            scheduleDeletion(info.id, minutes)
        } else {
            CaptureOverlayActivity.start(this, info.id, info.displayName)
        }
    }

    private fun scheduleDeletion(screenshotId: Long, minutes: Int) {
        scope.launch {
            val now = System.currentTimeMillis()
            val deleteTime = now + minutes * 60_000L
            val item = dev.snapttl.data.expiry.ExpiryQueueItem(
                filePath = "",
                deleteTimeEpochMs = deleteTime,
                timeUnit = TimeUnit.MINUTES.name,
                workManagerId = "snapttl_${screenshotId}_${now}",
                createdAtEpochMillis = now,
                screenshotId = screenshotId
            )
            services.expiryRepo.upsert(item)
            services.analytics.logEvent("alert_dialog_action_schedule_delete", mapOf("selected_time" to minutes))
            ToastHelper.show(
                this@CaptureMonitorService,
                getString(R.string.overlay_delete_in, TimeUtils.formatCountdown(deleteTime - now))
            )
        }
    }

    private data class ScreenshotInfo(val id: Long, val displayName: String, val path: String)

    private fun queryScreenshotInfo(uri: Uri): ScreenshotInfo? {
        val resolver: ContentResolver = contentResolver
        return resolver.query(
            uri,
            arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.DATA),
            null,
            null,
            null
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return null
            ScreenshotInfo(
                id = cursor.getLong(0),
                displayName = cursor.getString(1) ?: "",
                path = cursor.getString(2) ?: ""
            )
        }
    }

    companion object {
        const val NOTIFICATION_ID = 1
        const val ACTION_STOP = "dev.snapttl.action.STOP"

        @Volatile
        var isRunning: Boolean = false

        fun start(context: Context) {
            val intent = Intent(context, CaptureMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, CaptureMonitorService::class.java).apply { action = ACTION_STOP }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
