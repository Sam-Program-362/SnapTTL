package dev.snapttl.data.expiry

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ExpiryQueueDaoTest {

    private lateinit var database: ExpiryDatabase
    private lateinit var dao: ExpiryQueueDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, ExpiryDatabase::class.java).build()
        dao = database.expiryQueueDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun item(id: Long, workId: String, screenshotId: Long) =
        ExpiryQueueItem(
            id = 0,
            filePath = "",
            deleteTimeEpochMs = System.currentTimeMillis() + 60_000,
            timeUnit = "MINUTES",
            workManagerId = workId,
            createdAtEpochMillis = System.currentTimeMillis(),
            screenshotId = screenshotId
        )

    @Test
    fun upsert_replaces_by_work_manager_id() = runBlocking {
        dao.insertOrReplace(item(0, "work-1", 11))
        dao.insertOrReplace(item(0, "work-1", 11))
        assertEquals(1, dao.observeAll().first().size)
    }

    @Test
    fun observe_orders_newest_first() = runBlocking {
        dao.insertOrReplace(item(0, "w-1", 1))
        Thread.sleep(5)
        dao.insertOrReplace(item(0, "w-2", 2))
        val rows = dao.observeAll().first()
        assertEquals(2, rows.size)
        assertEquals("w-2", rows[0].workManagerId)
    }

    @Test
    fun delete_by_screenshot_id_removes_row() = runBlocking {
        dao.insertOrReplace(item(0, "w-1", 42))
        dao.deleteByScreenshotId(42)
        assertNull(dao.findByScreenshotId(42))
    }
}
