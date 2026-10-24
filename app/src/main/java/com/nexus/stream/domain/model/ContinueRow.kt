package com.nexus.stream.domain.model

data class ContinueRow(
    val metaId: String,
    val type: String,
    val name: String?,
    val poster: String?,
    val videoId: String,
    val season: Int?,
    val episode: Int?,
    val positionMs: Long,
    val durationMs: Long,
    val watched: Boolean,
    val updatedAt: Long,
    val inWatchlist: Boolean = false,
    val listStatus: ListStatus? = null,
)
