package com.nexus.stream.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.nexus.stream.data.local.db.dao.AddonDao
import com.nexus.stream.data.local.db.dao.LibraryDao
import com.nexus.stream.data.local.db.dao.MetaCacheDao
import com.nexus.stream.data.local.db.dao.ProgressDao
import com.nexus.stream.data.local.db.dao.SearchHistoryDao
import com.nexus.stream.data.local.db.entity.AddonEntity
import com.nexus.stream.data.local.db.entity.LibraryItemEntity
import com.nexus.stream.data.local.db.entity.MetaCacheEntity
import com.nexus.stream.data.local.db.entity.SearchHistoryEntity
import com.nexus.stream.data.local.db.entity.SkipSegmentCacheEntity
import com.nexus.stream.data.local.db.entity.WatchProgressEntity

@Database(
    entities = [
        AddonEntity::class,
        WatchProgressEntity::class,
        LibraryItemEntity::class,
        SearchHistoryEntity::class,
        SkipSegmentCacheEntity::class,
        MetaCacheEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class NexusDatabase : RoomDatabase() {
    abstract fun addonDao(): AddonDao

    abstract fun libraryDao(): LibraryDao

    abstract fun progressDao(): ProgressDao

    abstract fun metaCacheDao(): MetaCacheDao

    abstract fun searchHistoryDao(): SearchHistoryDao
}
