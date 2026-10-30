package dev.snapttl.ui.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dev.snapttl.language.LocaleHelper
import dev.snapttl.ui.theme.SnapTtlTheme

/**
 * Lifecycle plumbing for a Compose [ComposeView] hosted outside an Activity
 * (a WindowManager overlay window owned by the capture service).
 */
private class OverlayLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    fun performRestore(savedState: Bundle?) {
        savedStateRegistryController.performRestore(savedState)
    }

    fun handleLifecycleEvent(event: Lifecycle.Event) {
        lifecycleRegistry.handleLifecycleEvent(event)
    }
}

/**
 * Shows the floating delete-timer card as a **WindowManager overlay window**
 * (TYPE_APPLICATION_OVERLAY), not by launching an Activity.
 *
 * Why not an Activity: starting activities from the background is restricted
 * on modern Android (Android 10+ background-activity-launch rules, narrowed
 * further on 15/16). Blocked launches fail *silently*, which is exactly what
 * users saw on Android 16: detection works (toasts appear, silent auto-delete
 * schedules) but the popup never shows. Adding a window only requires the
 * SYSTEM_ALERT_WINDOW permission — no activity launch is involved.
 */
object CaptureOverlayWindows {

    private var current: ActiveOverlay? = null

    private class ActiveOverlay(
        val owner: OverlayLifecycleOwner,
        val view: ComposeView,
        val windowManager: WindowManager
    )

    /** Returns true when the overlay window is up. */
    fun show(
        context: Context,
        screenshotId: Long,
        displayName: String,
        quickOptions: List<Long>
    ): Boolean {
        if (!android.provider.Settings.canDrawOverlays(context)) return false
        dismiss()

        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val owner = OverlayLifecycleOwner()
        owner.performRestore(null)
        owner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        val themed = LocaleHelper.wrap(context)
        val view = ComposeView(themed).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent {
                SnapTtlTheme {
                    CaptureOverlay(
                        screenshotId = screenshotId,
                        displayName = displayName,
                        quickOptions = quickOptions,
                        onDismiss = { dismiss() }
                    )
                }
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        }

        return try {
            windowManager.addView(view, params)
            owner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
            current = ActiveOverlay(owner, view, windowManager)
            true
        } catch (_: Exception) {
            // Window could not be added (token/permission revoked mid-flight).
            runCatching { owner.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY) }
            false
        }
    }

    /** Removes the overlay window if one is up. */
    fun dismiss() {
        val active = current ?: return
        current = null
        runCatching { active.owner.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY) }
        runCatching { active.windowManager.removeView(active.view) }
    }
}
