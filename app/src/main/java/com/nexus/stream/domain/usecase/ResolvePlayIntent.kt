@file:Suppress("MatchingDeclarationName")

package com.nexus.stream.domain.usecase

import com.nexus.stream.domain.model.EpisodeProgress
import com.nexus.stream.domain.model.PlayIntent
import com.nexus.stream.domain.model.Video
import kotlin.math.ceil
import kotlin.math.max

data class TimeLeft(
    val hours: Int,
    val minutes: Int,
)

fun timeLeftParts(
    positionMs: Long,
    durationMs: Long,
): TimeLeft {
    if (durationMs <= 0L) return TimeLeft(0, 0)
    val remaining = max(0L, durationMs - positionMs)
    val minutes = max(1L, ceil(remaining.toDouble() / 60_000.0).toLong())
    return TimeLeft(
        hours = (minutes / 60).toInt(),
        minutes = (minutes % 60).toInt(),
    )
}

fun isResumable(
    watched: Boolean,
    positionMs: Long,
    durationMs: Long,
): Boolean =
    !watched &&
        positionMs >= ProgressRules.MIN_RESUME_MS &&
        (durationMs <= 0L || positionMs < durationMs - ProgressRules.RESUME_END_GUARD_MS)

fun isResumable(row: EpisodeProgress): Boolean = isResumable(row.watched, row.positionMs, row.durationMs)

fun isResumable(row: com.nexus.stream.domain.model.WatchProgress): Boolean =
    isResumable(row.watched, row.positionMs, row.durationMs)

private data class EpisodeOrderKey(
    val season: Int,
    val episode: Int,
    val originalIndex: Int,
) : Comparable<EpisodeOrderKey> {
    override fun compareTo(other: EpisodeOrderKey): Int =
        compareValuesBy(this, other, { it.season }, { it.episode }, { it.originalIndex })
}

private data class VideoWithIndex(
    val video: Video,
    val orderKey: EpisodeOrderKey,
)

private data class Candidate(
    val progress: EpisodeProgress,
    val videoWithIndex: VideoWithIndex,
)

fun resolvePlayIntent(
    type: String,
    metaId: String,
    videos: List<Video>,
    progress: Map<String, EpisodeProgress>,
    now: Long,
): PlayIntent {
    val isMovie = type.equals("movie", ignoreCase = true) || videos.isEmpty()
    return if (isMovie) {
        resolveMoviePlayIntent(metaId, progress)
    } else {
        resolveSeriesPlayIntent(videos, progress, now)
    }
}

private fun resolveMoviePlayIntent(
    metaId: String,
    progress: Map<String, EpisodeProgress>,
): PlayIntent {
    val row = progress[metaId]
    return if (row != null && isResumable(row)) {
        PlayIntent.Resume(
            videoId = metaId,
            season = null,
            episode = null,
            positionMs = row.positionMs,
            durationMs = row.durationMs,
        )
    } else {
        PlayIntent.Start
    }
}

private fun resolveSeriesPlayIntent(
    videos: List<Video>,
    progress: Map<String, EpisodeProgress>,
    now: Long,
): PlayIntent {
    val videosWithIndex =
        videos.mapIndexed { index, video ->
            VideoWithIndex(
                video = video,
                orderKey =
                    EpisodeOrderKey(
                        season = video.season ?: Int.MAX_VALUE,
                        episode = video.episode ?: Int.MAX_VALUE,
                        originalIndex = index,
                    ),
            )
        }

    val videoMap = videosWithIndex.associateBy { it.video.id }

    val candidates =
        progress.values
            .mapNotNull { row ->
                val v = videoMap[row.videoId] ?: return@mapNotNull null
                Candidate(progress = row, videoWithIndex = v)
            }.sortedWith(
                compareByDescending<Candidate> { it.progress.updatedAt }
                    .thenByDescending { it.videoWithIndex.orderKey },
            )

    val regularEpisodesInOrder =
        videosWithIndex
            .filter { it.video.season != 0 }
            .sortedBy { it.orderKey }

    return candidates.firstNotNullOfOrNull { decideCandidate(it, regularEpisodesInOrder, now) }
        ?: PlayIntent.Start
}

private fun decideCandidate(
    candidate: Candidate,
    regularEpisodesInOrder: List<VideoWithIndex>,
    now: Long,
): PlayIntent? {
    val row = candidate.progress
    val video = candidate.videoWithIndex.video
    return when {
        isResumable(row) -> {
            PlayIntent.Resume(
                videoId = video.id,
                season = video.season,
                episode = video.episode,
                positionMs = row.positionMs,
                durationMs = row.durationMs,
            )
        }

        row.watched && video.season != 0 -> {
            val candidateOrderKey = candidate.videoWithIndex.orderKey
            val nextEpisode =
                regularEpisodesInOrder
                    .firstOrNull { it.orderKey > candidateOrderKey && it.video.isReleased(now) }
            if (nextEpisode != null) {
                PlayIntent.Next(
                    videoId = nextEpisode.video.id,
                    season = nextEpisode.video.season,
                    episode = nextEpisode.video.episode,
                )
            } else {
                PlayIntent.Start
            }
        }

        else -> {
            null
        }
    }
}
