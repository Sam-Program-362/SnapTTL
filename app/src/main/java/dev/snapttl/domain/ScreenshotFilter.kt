package dev.snapttl.domain

import java.util.regex.Pattern

/**
 * Clean-room reconstruction of Mark's two-stage screenshot ContentObserver filter.
 *
 * Stage A: the changed URI must be a MediaStore image item and the display name or
 * file path must look like a screenshot.
 * Stage B: temporary / trashed / cached files are rejected before the overlay is shown.
 */
object ScreenshotFilter {

    private val MEDIA_ITEM_URI: Pattern =
        Pattern.compile("^content://media/external/images/media/[0-9]+$")

    private val EXCLUDED_TOKENS = listOf(
        ".tmp", ".pending", ".trashed", ".deleted", ".delete", ".temp",
        ".cache", "cache", ".nomedia", ".Trash", ".thumbnails",
        ".thumbnail", ".partial", ".part"
    )

    fun isValidScreenshotEvent(uri: String, displayName: String, filePath: String): Boolean {
        if (!MEDIA_ITEM_URI.matcher(uri).matches()) return false

        val dn = displayName.lowercase()
        val fp = filePath.lowercase()
        if ("screenshot" !in dn && "screenshots/" !in fp) return false
        if (displayName.startsWith(".")) return false

        for (token in EXCLUDED_TOKENS) {
            val lower = token.lowercase()
            if (lower in fp || lower in dn) return false
        }
        return true
    }
}
