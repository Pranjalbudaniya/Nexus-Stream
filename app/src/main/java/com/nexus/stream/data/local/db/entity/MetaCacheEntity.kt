package com.nexus.stream.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "meta_cache")
data class MetaCacheEntity(
    @PrimaryKey val metaId: String,
    val json: String,
    val fetchedAt: Long,
)
