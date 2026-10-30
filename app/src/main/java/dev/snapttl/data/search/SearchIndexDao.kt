package dev.snapttl.data.search

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SearchIndexDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(item: SearchIndexItem)

    @Query("SELECT screenshotId FROM ocr_label_index ORDER BY screenshotId")
    fun observeIndexedIds(): Flow<List<Long>>

    @Query("SELECT screenshotId FROM ocr_label_index WHERE screenshotId IN (:ids)")
    suspend fun indexedAmong(ids: List<Long>): List<Long>

    @Query(
        """
        SELECT screenshotId FROM ocr_label_index
        WHERE extractedText LIKE '%' || :query || '%'
           OR labels LIKE '%' || :query || '%'
        ORDER BY indexedAtEpochMillis DESC
        """
    )
    suspend fun search(query: String): List<Long>

    @Query(
        """
        SELECT * FROM ocr_label_index
        WHERE extractedText LIKE '%' || :query || '%'
           OR labels LIKE '%' || :query || '%'
        ORDER BY indexedAtEpochMillis DESC
        """
    )
    fun observeSearch(query: String): Flow<List<SearchIndexItem>>

    @Query("DELETE FROM ocr_label_index WHERE screenshotId = :screenshotId")
    suspend fun deleteByScreenshotId(screenshotId: Long)

    @Query("DELETE FROM ocr_label_index")
    suspend fun clear()
}
