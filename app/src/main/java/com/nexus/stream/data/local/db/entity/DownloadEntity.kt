package com.nexus.stream.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "downloads",
    indices = [
        Index(value = ["metaId", "videoId"]),
        Index(value = ["state"]),
    ],
)
data class DownloadEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val url: String?,
    val headersJson: String?,
    val infoHash: String?,
    val trackersJson: String?,
    val fileIdx: Int?,
    val metaId: String,
    val type: String,
    val title: String,
    val metaName: String?,
    val posterUrl: String?,
    val videoId: String,
    val season: Int?,
    val episode: Int?,
    val fileNameHint: String?,
    val fileName: String,
    val totalBytes: Long,
    val downloadedBytes: Long,
    val state: String,
    val pauseReason: String?,
    val failure: String?,
    val filePath: String?,
    val supportsResume: Boolean,
    val etag: String?,
    val lastModified: String?,
    val allowMetered: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val completedAt: Long?,
    val galleryUri: String? = null,
    val galleryState: String = "NONE",
)
