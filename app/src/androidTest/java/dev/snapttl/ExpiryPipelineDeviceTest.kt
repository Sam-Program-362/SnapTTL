package dev.snapttl

import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Base64

/**
 * End-to-end TTL test on a real device/emulator:
 *
 * insert screenshot → schedule its deletion a few seconds out → WorkManager fires
 * [dev.snapttl.worker.ExpiryPurgeWorker] after the delay → the file is deleted and
 * the expiry row is cleared.
 *
 * This is the regression test for the "scheduled deletion deleted instantly / never"
 * class of bugs, executed against the real scheduler on Android 11 and 14+ images.
 */
@RunWith(AndroidJUnit4::class)
class ExpiryPipelineDeviceTest {

    private val app get() = InstrumentationRegistry.getInstrumentation()
        .targetContext.applicationContext as SnapTtlApp

    private val pngBytes: ByteArray = Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg=="
    )

    private fun insertScreenshot(): Long {
        val resolver = app.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "Screenshot_ttl_${System.currentTimeMillis()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + "/Screenshots")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("MediaStore insert failed")
        resolver.openOutputStream(uri)?.use { it.write(pngBytes) }
        if (Build.VERSION.SDK_INT >= 29) {
            val clear = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
            resolver.update(uri, clear, null, null)
        }
        return uri.lastPathSegment!!.toLong()
    }

    @Test
    fun scheduled_deletion_fires_after_delay_and_cleans_up() {
        val id = insertScreenshot()
        assertNotNull(kotlinx.coroutines.runBlocking {
            app.services.screenshotRepo.findScreenshot(id)
        })

        // Arm a 6-second timer (shortest reliable delay for the device scheduler).
        kotlinx.coroutines.runBlocking {
            app.services.expiryRepo.scheduleIn(screenshotId = id, durationSeconds = 6)
        }

        // Not deleted before the delay elapses.
        Thread.sleep(2_000)
        assertNotNull(
            "screenshot must still exist before the scheduled time",
            kotlinx.coroutines.runBlocking {
                app.services.screenshotRepo.findScreenshot(id)
            }
        )

        // ... and gone a little after it.
        val deadline = System.currentTimeMillis() + 60_000
        var purged = false
        while (System.currentTimeMillis() < deadline) {
            val stillThere = kotlinx.coroutines.runBlocking {
                app.services.screenshotRepo.findScreenshot(id)
            }
            val row = kotlinx.coroutines.runBlocking {
                app.services.expiryRepo.findByScreenshot(id)
            }
            if (stillThere == null && row == null) {
                purged = true
                break
            }
            Thread.sleep(1_000)
        }
        assertTrue("worker did not purge the screenshot within 60s of the scheduled time", purged)
    }

    @Test
    fun cancelled_timer_does_not_delete() {
        val id = insertScreenshot()
        kotlinx.coroutines.runBlocking {
            val item = app.services.expiryRepo.scheduleIn(screenshotId = id, durationSeconds = 5)
            app.services.expiryRepo.cancel(item.id)
        }

        Thread.sleep(12_000)
        val stillThere = kotlinx.coroutines.runBlocking {
            app.services.screenshotRepo.findScreenshot(id)
        }
        assertNotNull("cancelled timer must not delete the screenshot", stillThere)

        // Clean up: delete manually.
        kotlinx.coroutines.runBlocking {
            app.services.screenshotRepo.deleteScreenshot(stillThere!!)
        }
    }
}
