package dev.snapttl.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class TimeUtilsTest {

    @Test
    fun formats_file_size_in_kb_mb_and_gb() {
        assertTrue(TimeUtils.formatFileSize(10_000).endsWith("KB"))
        assertTrue(TimeUtils.formatFileSize(5_000_000).endsWith("MB"))
        assertTrue(TimeUtils.formatFileSize(2_000_000_000L).endsWith("GB"))
    }

    @Test
    fun formats_countdown_buckets() {
        assertEquals("few seconds", TimeUtils.formatCountdown(5_000))
        assertEquals("5 min", TimeUtils.formatCountdown(5 * 60_000L))
        assertEquals("2 h", TimeUtils.formatCountdown(2 * 3_600_000L))
        assertEquals("1 d", TimeUtils.formatCountdown(86_400_000L))
    }

    @Test
    fun formats_date_without_throwing() {
        val formatted = TimeUtils.formatDateTime(1_700_000_000_000L, Locale.US)
        assertTrue(formatted.contains("2023"))
    }
}
