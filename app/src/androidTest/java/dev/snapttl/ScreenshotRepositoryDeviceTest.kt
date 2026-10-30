package dev.snapttl

import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.snapttl.data.repository.DeletionOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Base64

/**
 * Exercises the real MediaStore pipeline on a device: insert a screenshot-looking
 * image the app owns, discover it through [dev.snapttl.data.repository.ScreenshotRepository],
 * then delete it (MediaStore path — the SAF tree is not configured in tests) and
 * verify it is gone. This is the deletion path real users hit on Android 11+.
 */
@RunWith(AndroidJUnit4::class)
class ScreenshotRepositoryDeviceTest {

    private val app get() = InstrumentationRegistry.getInstrumentation()
        .targetContext.applicationContext as SnapTtlApp

    private val pngBytes: ByteArray = Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg=="
    )

    private fun insertScreenshot(): Long {
        val resolver = app.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "Screenshot_${System.currentTimeMillis()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + "/Screenshots")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            } else {
                @Suppress("DEPRECATION")
                put(MediaStore.Images.Media.DATA,
                    Environment.getExternalStorageDirectory().absolutePath +
                        "/Pictures/Screenshots/Screenshot_test_${System.currentTimeMillis()}.png")
            }
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("MediaStore insert failed")
        resolver.openOutputStream(uri)?.use { it.write(pngBytes) }
            ?: error("could not open output stream")
        if (Build.VERSION.SDK_INT >= 29) {
            val clear = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
            resolver.update(uri, clear, null, null)
        }
        return uri.lastPathSegment!!.toLong()
    }

    @Test
    fun insert_discover_and_delete_via_mediaStore() {
        val id = insertScreenshot()

        val found = app.services.screenshotRepo.findScreenshot(id)
        assertNotNull("inserted screenshot should be discoverable", found)
        assertTrue(found!!.displayName.startsWith("Screenshot_"))
        assertEquals(id, found.id)

        val outcome = app.services.screenshotRepo.deleteScreenshot(found)
        assertEquals(DeletionOutcome.DELETED, outcome)
        assertNull("deleted screenshot must vanish from MediaStore",
            app.services.screenshotRepo.findScreenshot(id))
    }

    @Test
    fun query_lists_screenshot_named_items() {
        val id = insertScreenshot()
        try {
            val listed = kotlinx.coroutines.runBlocking {
                app.services.screenshotRepo.queryScreenshots()
            }
            assertTrue(listed.any { it.id == id })
        } finally {
            kotlinx.coroutines.runBlocking {
                app.services.screenshotRepo.findScreenshot(id)?.let {
                    app.services.screenshotRepo.deleteScreenshot(it)
                }
            }
        }
    }
}
