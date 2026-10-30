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
import dev.snapttl.R
import dev.snapttl.di.Services
import dev.snapttl.domain.EntitlementState
import dev.snapttl.domain.ScreenshotFilter
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
 * (for silent auto-delete) arms the deletion timer immediately.
 *
 * Clean-room equivalent of Mark's `ScreenshotWatcherForegroundService`.
 */
class CaptureMonitorService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var services: Services
    private var observer: ContentObserver? = null

    /** Debounce: (screenshotId, lastSeenMs). */
    private var lastScreenshotId = -1L
    private var lastScreenshotAt = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        services = (application as dev.snapttl.SnapTtlApp).services
        NotificationChannels.create(this)
        startWatching()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            // startForegroundService() was used to deliver the stop intent, so the
            // startForeground() contract must be satisfied before stopping.
            startForegroundCompat()
            stopForeground(STOP_FOREGROUND_REMOVE)
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

        // MediaStore notifies insert + metadata updates separately; collapse them.
        val now = System.currentTimeMillis()
        if (info.id == lastScreenshotId && now - lastScreenshotAt < DEBOUNCE_MS) return
        lastScreenshotId = info.id
        lastScreenshotAt = now

        services.analytics.logEvent("screenshot_taken_no_filter", mapOf("full_file_path_no_filter" to info.path))

        val state = services.commonState.snapshot()
        val ent = EntitlementState(
            premiumPurchased = state.premiumPurchased || services.prefs.havePremiumPurchased,
            adsRemoved = state.adsRemoved || services.prefs.haveAdsRemoved,
            refillExpiryEpochMs = services.prefs.refillExpiryEpochMs
        )

        if (services.prefs.noDialogAutoDeleteEnabled && ent.hasUnlockedFeatures(now)) {
            val seconds = state.timeChips.firstOrNull()
                ?: services.prefs.timeChips.firstOrNull()
                ?: dev.snapttl.domain.TimeChips.FREE.first()
            if (ent.isValidDeletionScheduleSeconds(seconds, now)) {
                scheduleDeletion(info.id, info.path, seconds)
            } else {
                CaptureOverlayActivity.start(this, info.id, info.displayName)
            }
        } else {
            CaptureOverlayActivity.start(this, info.id, info.displayName)
        }
    }

    private fun scheduleDeletion(screenshotId: Long, path: String, seconds: Long) {
        scope.launch {
            services.expiryRepo.scheduleIn(
                screenshotId = screenshotId,
                durationSeconds = seconds,
                filePath = path
            )
            services.analytics.logEvent("alert_dialog_action_schedule_delete", mapOf("selected_time" to seconds))
            ToastHelper.show(
                this@CaptureMonitorService,
                getString(R.string.overlay_delete_in, TimeUtils.formatCountdown(seconds * 1000L))
            )
        }
    }

    private data class ScreenshotInfo(val id: Long, val displayName: String, val path: String)

    private fun queryScreenshotInfo(uri: Uri): ScreenshotInfo? {
        val resolver: ContentResolver = contentResolver
        return try {
            resolver.query(
                uri,
                arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.DATA
                ),
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
        } catch (_: Exception) {
            null // item vanished between notification and query
        }
    }

    companion object {
        const val NOTIFICATION_ID = 1
        const val ACTION_STOP = "dev.snapttl.action.STOP"
        private const val DEBOUNCE_MS = 5_000L

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
