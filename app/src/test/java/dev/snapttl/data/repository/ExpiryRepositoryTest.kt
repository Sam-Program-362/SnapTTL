package dev.snapttl.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import dev.snapttl.data.expiry.ExpiryDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * TTL pipeline tests: scheduling must arm WorkManager work with the *initial delay*
 * up to the delete time, re-arming must replace (not stack) pending work, and
 * cancelling must remove both the row and the work.
 *
 * Runs against the Android 11 / 14 / 15 Robolectric sandboxes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30, 34, 35])
class ExpiryRepositoryTest {

    private lateinit var context: Context
    private lateinit var database: ExpiryDatabase
    private lateinit var workManager: WorkManager
    private lateinit var repo: ExpiryRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder()
                .setExecutor(SynchronousExecutor())
                .build()
        )
        database = Room.inMemoryDatabaseBuilder(context, ExpiryDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        workManager = WorkManager.getInstance(context)
        repo = ExpiryRepository(database.expiryQueueDao(), workManager)
    }

    @After
    fun tearDown() {
        database.close()
        WorkManager.getInstance(context).cancelAllWork()
    }

    private fun uniqueWorkInfos(screenshotId: Long): List<WorkInfo> =
        workManager.getWorkInfosForUniqueWork(ExpiryRepository.workIdFor(screenshotId)).get()

    @Test
    fun schedule_arms_delayed_work_and_persists_row() = runBlocking {
        repo.scheduleIn(screenshotId = 42, durationSeconds = 900)

        val item = repo.findByScreenshot(42)
        assertNotNull(item)
        assertEquals("snapttl_purge_42", item!!.workManagerId)

        val infos = uniqueWorkInfos(42)
        assertEquals(1, infos.size)
        assertEquals(WorkInfo.State.ENQUEUED, infos[0].state)
        val delay = infos[0].initialDelayMillis
        assertTrue(
            "expected a ~900s initial delay but was ${delay}ms — the purge would run immediately",
            delay in 895_000..905_000
        )
    }

    @Test
    fun schedule_does_not_run_work_before_delay_elapses() = runBlocking {
        repo.scheduleIn(screenshotId = 7, durationSeconds = 3_600)
        val infos = uniqueWorkInfos(7)
        assertEquals(WorkInfo.State.ENQUEUED, infos[0].state)
        assertTrue(infos[0].initialDelayMillis > 3_000_000)
    }

    @Test
    fun reschedule_replaces_pending_work_instead_of_stacking() = runBlocking {
        repo.scheduleIn(screenshotId = 1, durationSeconds = 900)
        repo.scheduleIn(screenshotId = 1, durationSeconds = 3_600)

        assertEquals(1, database.expiryQueueDao().observeAllSnapshot().size)
        val infos = uniqueWorkInfos(1)
        assertEquals(1, infos.size)
        // The re-armed delay wins.
        assertTrue(infos[0].initialDelayMillis > 3_000_000)
    }

    @Test
    fun cancel_removes_row_and_work() = runBlocking {
        repo.scheduleIn(screenshotId = 9, durationSeconds = 900)
        val item = repo.findByScreenshot(9)!!

        repo.cancel(item.id)

        assertNull(repo.findByScreenshot(9))
        val infos = uniqueWorkInfos(9)
        assertEquals(WorkInfo.State.CANCELLED, infos[0].state)
    }

    @Test
    fun cancel_by_screenshot_removes_row_and_work() = runBlocking {
        repo.scheduleIn(screenshotId = 11, durationSeconds = 60)
        repo.cancelByScreenshot(11)
        assertNull(repo.findByScreenshot(11))
        assertEquals(WorkInfo.State.CANCELLED, uniqueWorkInfos(11)[0].state)
    }

    @Test
    fun findByWorkManagerId_resolves_the_row() = runBlocking {
        repo.scheduleIn(screenshotId = 21, durationSeconds = 60)
        val byWorkId = repo.findByWorkManagerId("snapttl_purge_21")
        assertNotNull(byWorkId)
        assertEquals(21L, byWorkId!!.screenshotId)
    }

    @Test
    fun rescheduleAll_re_arms_orphaned_rows() = runBlocking {
        // Simulate a row that lost its WorkManager entry (crash / cleared state).
        val now = System.currentTimeMillis()
        database.expiryQueueDao().insertOrReplace(
            dev.snapttl.data.expiry.ExpiryQueueItem(
                filePath = "",
                deleteTimeEpochMs = now + 60_000,
                timeUnit = "MINUTES",
                workManagerId = ExpiryRepository.workIdFor(77),
                createdAtEpochMillis = now,
                screenshotId = 77
            )
        )
        assertEquals(0, uniqueWorkInfos(77).size)

        repo.rescheduleAll()

        val infos = uniqueWorkInfos(77)
        assertEquals(1, infos.size)
        assertEquals(WorkInfo.State.ENQUEUED, infos[0].state)
        assertTrue(infos[0].initialDelayMillis <= 60_000)
    }

    @Test
    fun onPurged_clears_the_row() = runBlocking {
        repo.scheduleIn(screenshotId = 33, durationSeconds = 60)
        repo.onPurged(33)
        assertEquals(0, database.expiryQueueDao().observeAll().first().size)
    }
}
