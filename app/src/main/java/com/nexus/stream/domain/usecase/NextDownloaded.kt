package com.nexus.stream.domain.usecase

import com.nexus.stream.domain.model.DownloadItem
import com.nexus.stream.domain.model.DownloadState

fun findNextDownloaded(
    items: List<DownloadItem>,
    metaId: String,
    season: Int?,
    episode: Int?,
): DownloadItem? {
    if (season == null || episode == null) return null

    val candidates =
        items.mapNotNull { item ->
            if (item.metaId != metaId || item.state != DownloadState.COMPLETED) {
                return@mapNotNull null
            }
            val s = item.season ?: return@mapNotNull null
            val e = item.episode ?: return@mapNotNull null
            Triple(item, s, e)
        }

    val sameSeason =
        candidates
            .filter { it.second == season && it.third > episode }
            .minByOrNull { it.third }

    val nextSeason =
        if (sameSeason == null) {
            val future = candidates.filter { it.second > season }
            val minSeason = future.minOfOrNull { it.second }
            if (minSeason != null) {
                future.filter { it.second == minSeason }.minByOrNull { it.third }
            } else {
                null
            }
        } else {
            null
        }

    return (sameSeason ?: nextSeason)?.first
}
