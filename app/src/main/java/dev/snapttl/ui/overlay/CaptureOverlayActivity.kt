package dev.snapttl.ui.overlay

import android.content.Intent
import android.graphics.PixelFormat
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dev.snapttl.SnapTtlApp
import dev.snapttl.language.LocaleHelper
import dev.snapttl.ui.theme.SnapTtlTheme

/**
 * Floating delete-timer overlay shown above other apps when a new screenshot is
 * captured. Rendered as an overlay activity so it gets a real lifecycle without
 * manual LifecycleRegistry plumbing.
 */
class CaptureOverlayActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installOverlayWindow()
        val screenshotId = intent.getLongExtra(EXTRA_SCREENSHOT_ID, -1L)
        val displayName = intent.getStringExtra(EXTRA_DISPLAY_NAME).orEmpty()
        setContent {
            SnapTtlTheme {
                CaptureOverlay(
                    screenshotId = screenshotId,
                    displayName = displayName,
                    onDismiss = { finish() }
                )
            }
        }
    }

    private fun installOverlayWindow() {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 120
        }
        window.attributes = params
    }

    companion object {
        const val EXTRA_SCREENSHOT_ID = "snapttl_screenshot_id"
        const val EXTRA_DISPLAY_NAME = "snapttl_display_name"

        fun start(context: android.content.Context, screenshotId: Long, displayName: String) {
            val intent = Intent(context, CaptureOverlayActivity::class.java).apply {
                putExtra(EXTRA_SCREENSHOT_ID, screenshotId)
                putExtra(EXTRA_DISPLAY_NAME, displayName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}
