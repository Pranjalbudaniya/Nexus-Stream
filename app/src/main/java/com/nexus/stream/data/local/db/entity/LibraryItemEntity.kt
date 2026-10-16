package com.nexus.stream.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "library_items")
data class LibraryItemEntity(
    @PrimaryKey val metaId: String,
    val type: String,
    val name: String,
    val poster: String?,
    val background: String?,
    val inWatchlist: Boolean,
    val addedAt: Long,
    @ColumnInfo(name = "listStatus", defaultValue = "PLAN_TO_WATCH")
    val listStatus: String = "PLAN_TO_WATCH",
)
