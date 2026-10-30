package dev.snapttl.data.search

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [SearchIndexItem::class],
    version = 1,
    exportSchema = true
)
abstract class SearchIndexDatabase : RoomDatabase() {
    abstract fun searchIndexDao(): SearchIndexDao
}
