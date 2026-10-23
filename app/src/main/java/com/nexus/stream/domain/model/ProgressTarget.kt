package com.nexus.stream.domain.model

data class ProgressTarget(
    val metaId: String,
    val videoId: String,
    val type: String,
    val isMovie: Boolean,
    val season: Int?,
    val episode: Int?,
    val name: String,
    val poster: String?,
    val background: String?,
)

data class ProgressSample(
    val positionMs: Long,
    val durationMs: Long,
    val ended: Boolean,
)

data class WatchProgress(
    val key: String,
    val metaId: String,
    val videoId: String,
    val type: String,
    val season: Int? = null,
    val episode: Int? = null,
    val positionMs: Long,
    val durationMs: Long,
    val watched: Boolean,
    val updatedAt: Long,
)
