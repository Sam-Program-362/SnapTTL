package dev.snapttl.domain

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TimeUtils {

    fun formatDateTime(epochMs: Long, locale: Locale = Locale.US): String =
        SimpleDateFormat("dd MMM yyyy, hh:mm a", locale).format(Date(epochMs))

    fun formatCountdown(millis: Long): String {
        val s = (millis / 1000).coerceAtLeast(0)
        return when {
            s < 60 -> "few seconds"
            s < 3600 -> "%d min".format(s / 60)
            s < 86400 -> "%d h".format(s / 3600)
            else -> "%d d".format(s / 86400)
        }
    }

    fun formatFileSize(bytes: Long): String {
        val mb = bytes / (1024.0 * 1024.0)
        return when {
            mb >= 1024 -> "%.2f GB".format(mb / 1024.0)
            mb >= 1 -> "%.2f MB".format(mb)
            else -> "%d KB".format(bytes / 1024)
        }
    }
}
