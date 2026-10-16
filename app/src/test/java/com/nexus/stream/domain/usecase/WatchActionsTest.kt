package com.nexus.stream.domain.usecase

import com.nexus.stream.domain.model.EpisodeProgress
import com.nexus.stream.domain.model.Video
import org.junit.Assert.assertEquals
import org.junit.Test

class WatchActionsTest {
    private val now = 1_000_000L

    private fun testVideo(
        id: String,
        season: Int? = 1,
        episode: Int? = 1,
        released: String? = null,
    ) = Video(
        id = id,
        title = "Episode $id",
        released = released,
        season = season,
        episode = episode,
        thumbnail = null,
        overview = null,
    )

    @Test
    fun `watchlist toggle flips with inWatchlist state`() {
        val actionsFalse =
            watchActions(
                type = "movie",
                videos = emptyList(),
                progress = emptyMap(),
                metaId = "m1",
                inWatchlist = false,
                selectedSeason = null,
                now = now,
            )
        assertEquals(WatchAction.ToggleWatchlist(add = true), actionsFalse.first())

        val actionsTrue =
            watchActions(
                type = "movie",
                videos = emptyList(),
                progress = emptyMap(),
                metaId = "m1",
                inWatchlist = true,
                selectedSeason = null,
                now = now,
            )
        assertEquals(WatchAction.ToggleWatchlist(add = false), actionsTrue.first())
    }

    @Test
    fun `movie not watched returns SetMovieWatched true`() {
        val actions =
            watchActions(
                type = "movie",
                videos = emptyList(),
                progress = emptyMap(),
                metaId = "m1",
                inWatchlist = false,
                selectedSeason = null,
                now = now,
            )
        assertEquals(2, actions.size)
        assertEquals(WatchAction.ToggleWatchlist(add = true), actions[0])
        assertEquals(WatchAction.SetMovieWatched(watched = true), actions[1])
    }

    @Test
    fun `movie watched returns SetMovieWatched false`() {
        val progress =
            mapOf(
                "m1" to
                    EpisodeProgress(
                        videoId = "m1",
                        positionMs = 0L,
                        durationMs = 0L,
                        watched = true,
                        updatedAt = 100L,
                    ),
            )
        val actions =
            watchActions(
                type = "movie",
                videos = emptyList(),
                progress = progress,
                metaId = "m1",
                inWatchlist = true,
                selectedSeason = null,
                now = now,
            )
        assertEquals(2, actions.size)
        assertEquals(WatchAction.ToggleWatchlist(add = false), actions[0])
        assertEquals(WatchAction.SetMovieWatched(watched = false), actions[1])
    }

    @Test
    fun `movie in progress includes RemoveFromContinue`() {
        val progress =
            mapOf(
                "m1" to
                    EpisodeProgress(
                        videoId = "m1",
                        positionMs = 50_000L,
                        durationMs = 100_000L,
                        watched = false,
                        updatedAt = 100L,
                    ),
            )
        val actions =
            watchActions(
                type = "movie",
                videos = emptyList(),
                progress = progress,
                metaId = "m1",
                inWatchlist = false,
                selectedSeason = null,
                now = now,
            )
        assertEquals(3, actions.size)
        assertEquals(WatchAction.ToggleWatchlist(add = true), actions[0])
        assertEquals(WatchAction.SetMovieWatched(watched = true), actions[1])
        assertEquals(WatchAction.RemoveFromContinue, actions[2])
    }

    @Test
    fun `channel or tv types give watchlist only without watched actions`() {
        val progress =
            mapOf(
                "c1" to
                    EpisodeProgress(
                        videoId = "c1",
                        positionMs = 0L,
                        durationMs = 0L,
                        watched = true,
                        updatedAt = 100L,
                    ),
            )
        val actionsChannel =
            watchActions(
                type = "channel",
                videos = listOf(testVideo("c1")),
                progress = progress,
                metaId = "c1",
                inWatchlist = false,
                selectedSeason = null,
                now = now,
            )
        assertEquals(listOf(WatchAction.ToggleWatchlist(add = true)), actionsChannel)

        val actionsTv =
            watchActions(
                type = "tv",
                videos = listOf(testVideo("t1")),
                progress = emptyMap(),
                metaId = "t1",
                inWatchlist = true,
                selectedSeason = null,
                now = now,
            )
        assertEquals(listOf(WatchAction.ToggleWatchlist(add = false)), actionsTv)
    }

    @Test
    fun `series with several seasons partly watched excludes unreleased from counts`() {
        val v1 = testVideo("s1_e1", season = 1, episode = 1)
        val v2 = testVideo("s1_e2", season = 1, episode = 2)
        val v3 = testVideo("s1_e3", season = 1, episode = 3, released = "2099-01-01T00:00:00Z")
        val v4 = testVideo("s2_e1", season = 2, episode = 1)

        val progress =
            mapOf(
                "s1_e1" to
                    EpisodeProgress(
                        videoId = "s1_e1",
                        positionMs = 0L,
                        durationMs = 0L,
                        watched = true,
                        updatedAt = 100L,
                    ),
                "s1_e2" to
                    EpisodeProgress(
                        videoId = "s1_e2",
                        positionMs = 20_000L,
                        durationMs = 60_000L,
                        watched = false,
                        updatedAt = 100L,
                    ),
            )

        val actions =
            watchActions(
                type = "series",
                videos = listOf(v1, v2, v3, v4),
                progress = progress,
                metaId = "series1",
                inWatchlist = false,
                selectedSeason = 1,
                now = now,
            )

        assertEquals(4, actions.size)
        assertEquals(WatchAction.ToggleWatchlist(add = true), actions[0])
        assertEquals(WatchAction.SetSeasonWatched(season = 1, watched = true, count = 1), actions[1])
        assertEquals(WatchAction.SetAllWatched(watched = true, count = 2), actions[2])
        assertEquals(WatchAction.RemoveFromContinue, actions[3])
    }

    @Test
    fun `series with all released watched gives watched false actions`() {
        val v1 = testVideo("s1_e1", season = 1, episode = 1)
        val v2 = testVideo("s1_e2", season = 1, episode = 2)

        val progress =
            mapOf(
                "s1_e1" to
                    EpisodeProgress(
                        videoId = "s1_e1",
                        positionMs = 0L,
                        durationMs = 0L,
                        watched = true,
                        updatedAt = 100L,
                    ),
                "s1_e2" to
                    EpisodeProgress(
                        videoId = "s1_e2",
                        positionMs = 0L,
                        durationMs = 0L,
                        watched = true,
                        updatedAt = 100L,
                    ),
            )

        val actions =
            watchActions(
                type = "series",
                videos = listOf(v1, v2),
                progress = progress,
                metaId = "series1",
                inWatchlist = true,
                selectedSeason = 1,
                now = now,
            )

        assertEquals(3, actions.size)
        assertEquals(WatchAction.ToggleWatchlist(add = false), actions[0])
        assertEquals(WatchAction.SetSeasonWatched(season = 1, watched = false, count = 2), actions[1])
        assertEquals(WatchAction.SetAllWatched(watched = false, count = 2), actions[2])
    }

    @Test
    fun `series with nothing released omits season and all watched actions`() {
        val future = "2099-01-01T00:00:00Z"
        val v1 = testVideo("s1_e1", season = 1, episode = 1, released = future)
        val v2 = testVideo("s1_e2", season = 1, episode = 2, released = future)

        val actions =
            watchActions(
                type = "series",
                videos = listOf(v1, v2),
                progress = emptyMap(),
                metaId = "series1",
                inWatchlist = false,
                selectedSeason = 1,
                now = now,
            )

        assertEquals(1, actions.size)
        assertEquals(WatchAction.ToggleWatchlist(add = true), actions[0])
    }

    @Test
    fun `series with empty season or zero count omits that action`() {
        val v1 = testVideo("s1_e1", season = 1, episode = 1)
        val actions =
            watchActions(
                type = "series",
                videos = listOf(v1),
                progress = emptyMap(),
                metaId = "series1",
                inWatchlist = false,
                selectedSeason = 2,
                now = now,
            )

        assertEquals(2, actions.size)
        assertEquals(WatchAction.ToggleWatchlist(add = true), actions[0])
        assertEquals(WatchAction.SetAllWatched(watched = true, count = 1), actions[1])
    }
}
