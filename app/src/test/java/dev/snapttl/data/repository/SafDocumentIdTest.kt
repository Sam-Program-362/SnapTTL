package dev.snapttl.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The SAF direct-path mapping: converting a MediaStore file path into a
 * documents-provider document id under the granted tree. This is the fix for
 * the "says deleted but the file is still there" bug — the old code asked the
 * provider to filter children by name, but child-document queries ignore SQL
 * selections, so the first file in the folder got deleted instead of the
 * screenshot.
 */
class SafDocumentIdTest {

    @Test
    fun maps_files_inside_the_granted_folder() {
        assertEquals(
            "0:Pictures/Screenshots/S_1.jpg",
            ScreenshotRepository.safDocumentIdFor(
                "0:Pictures",
                "/storage/emulated/0/Pictures/Screenshots/S_1.jpg"
            )
        )
        assertEquals(
            "11:Pictures/Screenshot_2026.png",
            ScreenshotRepository.safDocumentIdFor(
                "11:Pictures",
                "/storage/emulated/0/Pictures/Screenshot_2026.png"
            )
        )
    }

    @Test
    fun maps_files_when_the_whole_storage_is_granted() {
        assertEquals(
            "3:Pictures/Screenshots/S_2.png",
            ScreenshotRepository.safDocumentIdFor(
                "3:",
                "/storage/emulated/0/Pictures/Screenshots/S_2.png"
            )
        )
    }

    @Test
    fun rejects_files_outside_the_granted_folder() {
        assertNull(
            ScreenshotRepository.safDocumentIdFor(
                "0:Pictures",
                "/storage/emulated/0/DCIM/Camera/IMG_1.jpg"
            )
        )
        assertNull(
            ScreenshotRepository.safDocumentIdFor(
                "0:Pictures/Screenshots",
                "/storage/emulated/0/Pictures/Other/S.jpg"
            )
        )
    }

    @Test
    fun rejects_folder_prefixes_that_only_partially_match() {
        assertNull(
            ScreenshotRepository.safDocumentIdFor(
                "0:Pictures",
                "/storage/emulated/0/PicturesOld/S.jpg"
            )
        )
    }

    @Test
    fun rejects_removable_storage_paths() {
        assertNull(
            ScreenshotRepository.safDocumentIdFor(
                "0:Pictures",
                "/storage/0000-0000/Pictures/S.jpg"
            )
        )
    }

    @Test
    fun rejects_malformed_inputs() {
        assertNull(ScreenshotRepository.safDocumentIdFor("Pictures", "/storage/emulated/0/Pictures/S.jpg"))
        assertNull(ScreenshotRepository.safDocumentIdFor("", "/storage/emulated/0/Pictures/S.jpg"))
        assertNull(ScreenshotRepository.safDocumentIdFor("0:Pictures", ""))
        assertNull(ScreenshotRepository.safDocumentIdFor("0:Pictures", "/storage/emulated/0/Pictures/"))
        assertNull(ScreenshotRepository.safDocumentIdFor("3:", "/storage/other/S.jpg"))
    }
}
