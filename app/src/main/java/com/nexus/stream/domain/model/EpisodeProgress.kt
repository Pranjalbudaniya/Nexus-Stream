package com.nexus.stream.domain.model

data class EpisodeProgress(
    val videoId: String,
    val positionMs: Long,
    val durationMs: Long,
    val watched: Boolean,
    val updatedAt: Long,
)

data class EpisodeRef(
    val videoId: String,
    val type: String,
    val season: Int? = null,
    val episode: Int? = null,
)
