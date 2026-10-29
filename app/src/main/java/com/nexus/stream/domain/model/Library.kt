package com.nexus.stream.domain.model

const val HISTORY_ROW_LIMIT = 500

data class WatchlistEntry(
    val metaId: String,
    val type: String,
    val name: String,
    val poster: String?,
    val background: String?,
    val status: ListStatus,
    val addedAt: Long,
)

enum class WatchlistSort {
    RECENTLY_ADDED,
    TITLE_AZ,
    TITLE_ZA,
}

data class HistoryEntry(
    val metaId: String,
    val type: String,
    val name: String?,
    val poster: String?,
    val season: Int?,
    val episode: Int?,
    val videoId: String,
    val progress: Float?,
    val watched: Boolean,
    val updatedAt: Long,
)

enum class HistoryBucket {
    TODAY,
    YESTERDAY,
    LAST_7_DAYS,
    LAST_30_DAYS,
    EARLIER,
}

sealed interface EpisodeLabel {
    data class SeasonEpisode(
        val season: Int,
        val episode: Int,
    ) : EpisodeLabel

    data class EpisodeOnly(
        val episode: Int,
    ) : EpisodeLabel

    data class SeasonOnly(
        val season: Int,
    ) : EpisodeLabel
}

data class FacetCounts(
    val all: Int,
    val byStatus: Map<ListStatus, Int>,
    val byType: Map<String, Int>,
)
