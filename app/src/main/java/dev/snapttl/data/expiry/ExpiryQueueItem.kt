package dev.snapttl.data.expiry

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room v1 reconstructed from Mark's `scheduled_screenshot_entity`
 * (identity hash 8d1e614b7e9237ea47b33ac518cf10fa).
 */
@Entity(
    tableName = "expiry_queue_item",
    indices = [
        Index(value = ["createdAtEpochMillis"]),
        Index(value = ["workManagerId"], unique = true)
    ]
)
data class ExpiryQueueItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val filePath: String,
    val deleteTimeEpochMs: Long,
    val timeUnit: String,
    val workManagerId: String,
    val createdAtEpochMillis: Long,
    val screenshotId: Long
)
