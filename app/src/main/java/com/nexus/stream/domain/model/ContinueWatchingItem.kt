package com.nexus.stream.domain.model

data class ContinueWatchingItem(
    val metaId: String,
    val type: String,
    val name: String,
    val poster: String?,
    val videoId: String,
    val season: Int?,
    val episode: Int?,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
)

val ContinueWatchingItem.progress: Float?
    get() =
        if (durationMs <= 0L) {
            null
        } else {
            (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        }
