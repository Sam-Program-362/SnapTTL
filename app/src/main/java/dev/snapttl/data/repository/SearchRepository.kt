package dev.snapttl.data.repository

import dev.snapttl.data.search.SearchIndexDao
import dev.snapttl.data.search.SearchIndexItem
import dev.snapttl.vision.ScreenshotVision
import kotlinx.coroutines.flow.Flow

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

    suspend fun indexAll(): IndexSummary {
        val all = screenshotRepo.queryScreenshots()
        val ids = all.map { it.id }
        val indexed = if (ids.isEmpty()) emptyList() else dao.indexedAmong(ids).toSet()
        var count = 0
        for (media in all) {
            if (media.id in indexed) continue
            if (indexScreenshot(media)) count++
        }
        return IndexSummary(total = all.size, indexed = count)
    }

    suspend fun indexScreenshot(media: ScreenshotMedia): Boolean {
        val result = vision.analyze(media.uri)
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
