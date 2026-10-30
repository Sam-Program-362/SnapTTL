package dev.snapttl.data.expiry

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [ExpiryQueueItem::class],
    version = 1,
    exportSchema = true
)
abstract class ExpiryDatabase : RoomDatabase() {
    abstract fun expiryQueueDao(): ExpiryQueueDao
}
