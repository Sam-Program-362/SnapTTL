package dev.snapttl.data.repository

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dev.snapttl.data.search.SearchIndexDao
import dev.snapttl.data.search.SearchIndexItem
import dev.snapttl.vision.ScreenshotVision
import dev.snapttl.worker.OcrIndexWorker
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.TimeUnit as JavaTimeUnit

data class IndexSummary(val total: Int, val indexed: Int)

/**
 * OCR + image-labeling search index. Indexes only unindexed MediaStore screenshots
 * and stores the extracted text / labels exactly as Mark's `ScreenshotIndexWorker` did.
 */
class SearchRepository(
    private val dao: SearchIndexDao,
    private val vision: ScreenshotVision,
    private val screenshotRepo: ScreenshotRepository
) {
    fun observeIndexedIds(): Flow<List<Long>> = dao.observeIndexedIds()

    fun observeSearch(query: String): Flow<List<SearchIndexItem>> = dao.observeSearch(query)

    /** Long-running indexing always goes through WorkManager (repo rule). */
    fun enqueueIndexWork(context: Context) {
        val request = OneTimeWorkRequestBuilder<OcrIndexWorker>()
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, JavaTimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            OcrIndexWorker.UNIQUE_NAME,
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    suspend fun indexAll(): IndexSummary {
        val all = screenshotRepo.queryScreenshots()
        val ids = all.map { it.id }
        val indexed = if (ids.isEmpty()) emptyList() else dao.indexedAmong(ids).toSet()
        var count = 0
        for (media in all) {
            if (media.id in indexed) continue
            if (indexScreenshot(media)) count++
        }
        // Rows for screenshots that no longer exist are dropped so the index
        // never keeps stale entries.
        val stale = indexed - ids.toSet()
        if (stale.isNotEmpty()) stale.forEach { dao.deleteByScreenshotId(it) }
        return IndexSummary(total = all.size, indexed = count)
    }

    /**
     * Returns false when analysis failed so the screenshot is retried on the next
     * indexing pass instead of being locked out by an empty index row.
     */
    suspend fun indexScreenshot(media: ScreenshotMedia): Boolean {
        val result = try {
            vision.analyze(media.uri)
        } catch (_: Exception) {
            return false
        }
        dao.insertOrReplace(
            SearchIndexItem(
                screenshotId = media.id,
                extractedText = result.text,
                labels = result.labels.joinToString(", "),
                indexedAtEpochMillis = System.currentTimeMillis()
            )
        )
        return true
    }
}
