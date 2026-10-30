package dev.snapttl.ui.common

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings

object Permissions {

    const val NOTIFICATIONS = "android.permission.POST_NOTIFICATIONS"
    const val READ_MEDIA_IMAGES = "android.permission.READ_MEDIA_IMAGES"
    const val READ_MEDIA_VISUAL_USER_SELECTED = "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"
    const val READ_EXTERNAL_STORAGE = "android.permission.READ_EXTERNAL_STORAGE"
    const val SYSTEM_ALERT_WINDOW = "android.permission.SYSTEM_ALERT_WINDOW"

    fun required(context: Context): List<String> {
        val list = mutableListOf<String>()
        list += NOTIFICATIONS
        list += READ_MEDIA_IMAGES
        list += READ_MEDIA_VISUAL_USER_SELECTED
        if (Build.VERSION.SDK_INT <= 32) list += READ_EXTERNAL_STORAGE
        list += SYSTEM_ALERT_WINDOW
        return list
    }

    fun isGranted(context: Context, permission: String): Boolean {
        return when (permission) {
            SYSTEM_ALERT_WINDOW -> Settings.canDrawOverlays(context)
            else -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        }
    }

    fun allGranted(context: Context, permissions: List<String>): Boolean =
        permissions.all { isGranted(context, it) }
}
