package com.nexus.stream.domain.usecase

import com.nexus.stream.domain.model.EpisodeProgress
import com.nexus.stream.domain.model.SeasonGroup
import com.nexus.stream.domain.model.Video
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

fun groupEpisodes(videos: List<Video>): List<SeasonGroup> {
    if (videos.isEmpty()) return emptyList()

    val numberedSeasons = mutableMapOf<Int, MutableList<Video>>()
    val nullSeasonEpisodes = mutableListOf<Video>()
    val specialsEpisodes = mutableListOf<Video>()

    for (video in videos) {
        when (val s = video.season) {
            null -> nullSeasonEpisodes.add(video)
            0 -> specialsEpisodes.add(video)
            else -> numberedSeasons.getOrPut(s) { mutableListOf() }.add(video)
        }
    }

    val result = mutableListOf<SeasonGroup>()

    val sortedSeasonNumbers = numberedSeasons.keys.sorted()
    for (s in sortedSeasonNumbers) {
        val eps =
            numberedSeasons.getValue(s).sortedWith(
                compareBy<Video, Int?>(nullsLast()) { it.episode },
            )
        result.add(SeasonGroup(season = s, episodes = eps))
    }

    if (nullSeasonEpisodes.isNotEmpty()) {
        result.add(SeasonGroup(season = null, episodes = nullSeasonEpisodes))
    }

    if (specialsEpisodes.isNotEmpty()) {
        val eps =
            specialsEpisodes.sortedWith(
                compareBy<Video, Int?>(nullsLast()) { it.episode },
            )
        result.add(SeasonGroup(season = 0, episodes = eps))
    }

    return result
}

fun defaultSeason(
    groups: List<SeasonGroup>,
    lastWatchedVideoId: String?,
): Int? {
    if (groups.isEmpty()) return null
    val matchedSeason =
        lastWatchedVideoId?.let { id ->
            groups.firstOrNull { g -> g.episodes.any { it.id == id } }?.season
        }
    val nonSpecialGroup = groups.firstOrNull { it.season != 0 }
    return matchedSeason ?: (nonSpecialGroup ?: groups.first()).season
}

fun resumeVideoId(
    videos: List<Video>,
    progress: Map<String, EpisodeProgress>,
    now: Long,
): String? {
    if (videos.isEmpty()) return null

    val inProgressId =
        progress.values
            .filter { it.positionMs > 0 && !it.watched }
            .sortedByDescending { it.updatedAt }
            .firstNotNullOfOrNull { item ->
                videos.find { it.id == item.videoId }?.takeIf { it.isReleased(now) }?.id
            }

    val fallbackId = {
        val sortedVideos = groupEpisodes(videos).flatMap { it.episodes }
        val lastWatchedIndex = sortedVideos.indexOfLast { progress[it.id]?.watched == true }
        if (lastWatchedIndex >= 0) {
            sortedVideos
                .subList(lastWatchedIndex + 1, sortedVideos.size)
                .firstOrNull { it.isReleased(now) }
                ?.id
        } else {
            sortedVideos.firstOrNull { it.isReleased(now) }?.id
        }
    }

    return inProgressId ?: fallbackId()
}

private fun parseReleaseDateToEpoch(dateStr: String): Long? =
    runCatching { Instant.parse(dateStr).toEpochMilli() }
        .recoverCatching { OffsetDateTime.parse(dateStr).toInstant().toEpochMilli() }
        .recoverCatching {
            LocalDate
                .parse(dateStr)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli()
        }.getOrNull()

fun Video.isReleased(now: Long): Boolean {
    val dateStr = released?.trim().orEmpty()
    if (dateStr.isEmpty()) return true
    val epochMs = parseReleaseDateToEpoch(dateStr)
    return epochMs == null || epochMs <= now
}
