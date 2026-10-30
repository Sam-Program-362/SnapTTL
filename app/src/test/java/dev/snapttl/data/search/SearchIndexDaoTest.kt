package dev.snapttl.data.search

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30, 34, 35])
class SearchIndexDaoTest {

    private lateinit var database: SearchIndexDatabase
    private lateinit var dao: SearchIndexDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, SearchIndexDatabase::class.java).build()
        dao = database.searchIndexDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun search_matches_text_and_labels_and_orders_by_index_time() = runBlocking {
        dao.insertOrReplace(SearchIndexItem(101, "Invoice #4829 Total $129.00", "Receipt, Paper", 1_000))
        dao.insertOrReplace(SearchIndexItem(102, "Flight Boarding Pass Gate B12", "QR Code, Ticket", 2_000))

        assertEquals(listOf(102L), dao.search("Boarding Pass"))
        assertEquals(listOf(101L), dao.search("Receipt"))
        assertEquals(emptyList<Long>(), dao.search("nothing"))
        assertEquals(listOf(101L, 102L), dao.indexedAmong(listOf(101L, 102L, 103L)).sorted())
    }

    @Test
    fun observe_indexed_ids_reflects_inserts() = runBlocking {
        dao.insertOrReplace(SearchIndexItem(5, "hello", "greeting", 10))
        assertEquals(listOf(5L), dao.observeIndexedIds().first())
    }

    @Test
    fun delete_by_screenshot_removes_only_that_row() = runBlocking {
        dao.insertOrReplace(SearchIndexItem(1, "a", "x", 1))
        dao.insertOrReplace(SearchIndexItem(2, "b", "y", 2))
        dao.deleteByScreenshotId(1)
        assertEquals(listOf(2L), dao.observeIndexedIds().first())
    }
}
