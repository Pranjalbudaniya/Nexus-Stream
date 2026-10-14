package com.nexus.stream.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.nexus.stream.domain.model.AddonSource

@Entity(
    tableName = "addons",
    indices = [Index(value = ["position"])],
)
data class AddonEntity(
    @PrimaryKey val transportUrl: String,
    val id: String,
    val name: String,
    val version: String,
    val manifestJson: String,
    val enabled: Boolean,
    val position: Int,
    val source: AddonSource,
    val installedAt: Long,
)
