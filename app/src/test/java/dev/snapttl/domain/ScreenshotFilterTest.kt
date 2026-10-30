package dev.snapttl.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenshotFilterTest {

    @Test
    fun accepts_valid_screenshot_event() {
        assertTrue(
            ScreenshotFilter.isValidScreenshotEvent(
                "content://media/external/images/media/10492",
                "Screenshot_20261027-013000.png",
                "/storage/emulated/0/Pictures/Screenshots/Screenshot_20261027-013000.png"
            )
        )
    }

    @Test
    fun accepts_event_matched_by_path_only() {
        assertTrue(
            ScreenshotFilter.isValidScreenshotEvent(
                "content://media/external/images/media/77",
                "IMG_0001.png",
                "/storage/emulated/0/Pictures/Screenshots/IMG_0001.png"
            )
        )
    }

    @Test
    fun rejects_non_media_store_uri() {
        assertFalse(
            ScreenshotFilter.isValidScreenshotEvent(
                "content://media/external/images/media/abc",
                "Screenshot.png",
                "/storage/emulated/0/Pictures/Screenshots/Screenshot.png"
            )
        )
    }

    @Test
    fun rejects_pending_and_trashed_files() {
        assertFalse(
            ScreenshotFilter.isValidScreenshotEvent(
                "content://media/external/images/media/10493",
                ".pending-172999-Screenshot_2026.png",
                "/storage/emulated/0/Pictures/Screenshots/.pending-172999-Screenshot_2026.png"
            )
        )
        assertFalse(
            ScreenshotFilter.isValidScreenshotEvent(
                "content://media/external/images/media/10494",
                "Screenshot.png",
                "/storage/emulated/0/Pictures/Screenshots/.trashed_Screenshot.png"
            )
        )
    }

    @Test
    fun rejects_hidden_dot_files() {
        assertFalse(
            ScreenshotFilter.isValidScreenshotEvent(
                "content://media/external/images/media/10495",
                ".Screenshot_2026.png",
                "/storage/emulated/0/Pictures/Screenshots/.Screenshot_2026.png"
            )
        )
    }
}
