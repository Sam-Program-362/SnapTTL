package dev.snapttl.data.search

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room v1 reconstructed from Mark's `screenshot_index_entity`
 * (identity hash a92f236565005f4a6655f106d57ed6bb).
 */
@Entity(tableName = "ocr_label_index")
data class SearchIndexItem(
    @PrimaryKey val screenshotId: Long,
    val extractedText: String,
    val labels: String,
    val indexedAtEpochMillis: Long
)
