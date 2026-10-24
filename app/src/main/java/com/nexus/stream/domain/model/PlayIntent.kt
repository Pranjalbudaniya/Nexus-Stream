package com.nexus.stream.domain.model

sealed interface PlayIntent {
    data object Start : PlayIntent

    data class Resume(
        val videoId: String,
        val season: Int?,
        val episode: Int?,
        val positionMs: Long,
        val durationMs: Long,
    ) : PlayIntent

    data class Next(
        val videoId: String,
        val season: Int?,
        val episode: Int?,
    ) : PlayIntent
}
