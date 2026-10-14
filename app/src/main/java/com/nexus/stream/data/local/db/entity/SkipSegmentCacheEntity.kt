package com.nexus.stream.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "skip_segment_cache")
data class SkipSegmentCacheEntity(
    @PrimaryKey val key: String,
    val segmentsJson: String,
    val fetchedAt: Long,
)
