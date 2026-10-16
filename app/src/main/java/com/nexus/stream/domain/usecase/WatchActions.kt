package com.nexus.stream.domain.usecase

import com.nexus.stream.domain.model.EpisodeProgress
import com.nexus.stream.domain.model.Video

sealed interface WatchAction {
    data class ToggleWatchlist(
        val add: Boolean,
    ) : WatchAction

    data class SetMovieWatched(
        val watched: Boolean,
    ) : WatchAction

    data class SetSeasonWatched(
        val season: Int?,
        val watched: Boolean,
        val count: Int,
    ) : WatchAction

    data class SetAllWatched(
        val watched: Boolean,
        val count: Int,
    ) : WatchAction

    data object RemoveFromContinue : WatchAction
}

class WatchActions private constructor()

@Suppress("LongParameterList")
fun watchActions(
    type: String,
    videos: List<Video>,
    progress: Map<String, EpisodeProgress>,
    metaId: String,
    inWatchlist: Boolean,
    selectedSeason: Int?,
    now: Long,
): List<WatchAction> {
    val actions = mutableListOf<WatchAction>()

    // 1. Always ToggleWatchlist(add = !inWatchlist)
    actions.add(WatchAction.ToggleWatchlist(add = !inWatchlist))

    // 2. type == "movie"
    if (type.equals("movie", ignoreCase = true)) {
        val watchedNow = progress[metaId]?.watched == true
        actions.add(WatchAction.SetMovieWatched(watched = !watchedNow))
    } else if (type.equals("series", ignoreCase = true)) {
        // 3. type == "series" with at least one released episode
        addSeriesWatchActions(
            videos = videos,
            progress = progress,
            selectedSeason = selectedSeason,
            now = now,
            actions = actions,
        )
    }

    // 5. RemoveFromContinue last, only when some progress row of this title has watched == false and positionMs > 0
    val hasInProgress = progress.values.any { !it.watched && it.positionMs > 0 }
    if (hasInProgress) {
        actions.add(WatchAction.RemoveFromContinue)
    }

    return actions
}

private fun addSeriesWatchActions(
    videos: List<Video>,
    progress: Map<String, EpisodeProgress>,
    selectedSeason: Int?,
    now: Long,
    actions: MutableList<WatchAction>,
) {
    val releasedVideos = videos.filter { it.isReleased(now) }
    if (releasedVideos.isEmpty()) return

    val seasonReleased = releasedVideos.filter { it.season == selectedSeason }
    if (seasonReleased.isNotEmpty()) {
        val allReleasedInSeasonWatched = seasonReleased.all { progress[it.id]?.watched == true }
        val targetSeasonWatched = !allReleasedInSeasonWatched
        val count =
            if (targetSeasonWatched) {
                seasonReleased.count { progress[it.id]?.watched != true }
            } else {
                seasonReleased.count { progress[it.id]?.watched == true }
            }
        if (count > 0) {
            actions.add(
                WatchAction.SetSeasonWatched(
                    season = selectedSeason,
                    watched = targetSeasonWatched,
                    count = count,
                ),
            )
        }
    }

    val allReleasedWatched = releasedVideos.all { progress[it.id]?.watched == true }
    val targetAllWatched = !allReleasedWatched
    val allCount =
        if (targetAllWatched) {
            releasedVideos.count { progress[it.id]?.watched != true }
        } else {
            releasedVideos.count { progress[it.id]?.watched == true }
        }
    if (allCount > 0) {
        actions.add(
            WatchAction.SetAllWatched(
                watched = targetAllWatched,
                count = allCount,
            ),
        )
    }
}
