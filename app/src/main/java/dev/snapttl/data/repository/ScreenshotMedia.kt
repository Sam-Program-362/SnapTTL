package dev.snapttl.data.repository

import android.content.Context
import android.provider.MediaStore

/** A screenshot resolved from MediaStore. */
data class ScreenshotMedia(
    val id: Long,
    val displayName: String,
    val path: String,
    val uri: String,
    val sizeBytes: Long,
    val dateTakenMs: Long
) {
    companion object {
        private val PROJECTION = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_TAKEN
        )

        fun isScreenshotName(displayName: String): Boolean =
            displayName.lowercase().contains("screenshot")

        fun isScreenshotPath(path: String): Boolean =
            path.lowercase().contains("screenshots/")
    }
}
