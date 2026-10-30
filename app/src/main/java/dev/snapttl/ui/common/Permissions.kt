package dev.snapttl.ui.common

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings

/**
 * Runtime permission model.
 *
 * The photos permission is a *group*: on API 33+ full access (`READ_MEDIA_IMAGES`) or
 * partial access (`READ_MEDIA_VISUAL_USER_SELECTED`, granted only when the user picks
 * "Select photos") both let the app see screenshots, so either counts as granted.
 */
@android.annotation.SuppressLint("InlinedApi") // permission String constants are compile-time inlined
object Permissions {

    const val NOTIFICATIONS = Manifest.permission.POST_NOTIFICATIONS
    const val READ_MEDIA_IMAGES = Manifest.permission.READ_MEDIA_IMAGES
    const val READ_MEDIA_VISUAL_USER_SELECTED = Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
    const val READ_EXTERNAL_STORAGE = Manifest.permission.READ_EXTERNAL_STORAGE
    const val SYSTEM_ALERT_WINDOW = android.Manifest.permission.SYSTEM_ALERT_WINDOW

    /** Runtime permissions the app asks for directly (overlay handled separately). */
    fun runtimeRequestList(): List<String> =
        if (Build.VERSION.SDK_INT >= 33) {
            listOf(NOTIFICATIONS, READ_MEDIA_IMAGES, READ_MEDIA_VISUAL_USER_SELECTED)
        } else {
            listOf(READ_EXTERNAL_STORAGE)
        }

    fun mediaPermissionsForSdk(): List<String> =
        if (Build.VERSION.SDK_INT >= 33) {
            listOf(READ_MEDIA_IMAGES, READ_MEDIA_VISUAL_USER_SELECTED)
        } else {
            listOf(READ_EXTERNAL_STORAGE)
        }

    fun required(context: Context): List<String> {
        val list = mutableListOf<String>()
        list += NOTIFICATIONS
        list += mediaPermissionsForSdk()
        list += SYSTEM_ALERT_WINDOW
        return list
    }

    fun isGranted(context: Context, permission: String): Boolean = when (permission) {
        SYSTEM_ALERT_WINDOW -> Settings.canDrawOverlays(context)
        // Full access also satisfies the partial-access row (and vice versa).
        READ_MEDIA_IMAGES, READ_MEDIA_VISUAL_USER_SELECTED ->
            hasMediaAccess(context)
        READ_EXTERNAL_STORAGE ->
            if (Build.VERSION.SDK_INT >= 33) true else hasPermission(context, permission)
        NOTIFICATIONS ->
            if (Build.VERSION.SDK_INT >= 33) hasPermission(context, permission) else true
        else -> hasPermission(context, permission)
    }

    /** Any of the media permissions lets the app read MediaStore screenshots. */
    fun hasMediaAccess(context: Context): Boolean =
        mediaPermissionsForSdk().any { hasPermission(context, it) } ||
            (Build.VERSION.SDK_INT <= 32 && hasPermission(context, READ_EXTERNAL_STORAGE))

    fun allGranted(context: Context, permissions: List<String>): Boolean =
        permissions.all { isGranted(context, it) }

    private fun hasPermission(context: Context, permission: String): Boolean =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
}
