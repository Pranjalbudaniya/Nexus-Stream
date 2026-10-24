package com.nexus.stream.domain.usecase

import com.nexus.stream.domain.model.EpisodeProgress
import com.nexus.stream.domain.model.PlayIntent
import com.nexus.stream.domain.model.Video
import org.junit.Assert.assertEquals
import org.junit.Test

class ResolvePlayIntentTest {
    private fun video(
        id: String,
        title: String = "Episode",
        released: String? = "2020-01-01",
        season: Int? = 1,
        episode: Int? = 1,
    ) = Video(
        id = id,
        title = title,
        released = released,
        season = season,
        episode = episode,
        thumbnail = null,
        overview = null,
    )

    private fun progress(
        videoId: String,
        positionMs: Long = 0L,
        durationMs: Long = 0L,
        watched: Boolean = false,
        updatedAt: Long = 1000L,
    ) = videoId to
        EpisodeProgress(
            videoId = videoId,
            positionMs = positionMs,
            durationMs = durationMs,
            watched = watched,
            updatedAt = updatedAt,
        )

    private val now = 1_700_000_000_000L

    @Test
    fun PI1_movie_noRow_returnsStart() {
        val result =
            resolvePlayIntent(
                type = "movie",
                metaId = "tt001",
                videos = emptyList(),
                progress = emptyMap(),
                now = now,
            )
        assertEquals(PlayIntent.Start, result)
    }

    @Test
    fun PI2_movie_thresholdTransition_startThenResume() {
        val duration = 2 * 3600 * 1000L
        val subThreshold =
            mapOf(
                progress("tt001", positionMs = 9_999L, durationMs = duration),
            )
        val atThreshold =
            mapOf(
                progress("tt001", positionMs = 10_000L, durationMs = duration),
            )

        assertEquals(
            PlayIntent.Start,
            resolvePlayIntent("movie", "tt001", emptyList(), subThreshold, now),
        )
        assertEquals(
            PlayIntent.Resume("tt001", null, null, 10_000L, duration),
            resolvePlayIntent("movie", "tt001", emptyList(), atThreshold, now),
        )
    }

    @Test
    fun PI3_movie_watchedRow_returnsStart() {
        val duration = 7200_000L
        val p =
            mapOf(
                progress("tt001", positionMs = 7000_000L, durationMs = duration, watched = true),
            )
        val result = resolvePlayIntent("movie", "tt001", emptyList(), p, now)
        assertEquals(PlayIntent.Start, result)
    }

    @Test
    fun PI4_movie_unwatchedWithinTenSecondsOfEnd_returnsStart() {
        val duration = 7200_000L
        val p =
            mapOf(
                progress("tt001", positionMs = duration - 5_000L, durationMs = duration, watched = false),
            )
        val result = resolvePlayIntent("movie", "tt001", emptyList(), p, now)
        assertEquals(PlayIntent.Start, result)
    }

    @Test
    fun PI5_movie_durationZero_positionFiveMinutes_returnsResume() {
        val pos = 5 * 60 * 1000L
        val p =
            mapOf(
                progress("tt001", positionMs = pos, durationMs = 0L, watched = false),
            )
        val result = resolvePlayIntent("movie", "tt001", emptyList(), p, now)
        assertEquals(PlayIntent.Resume("tt001", null, null, pos, 0L), result)
    }

    @Test
    fun PI6_series_noRows_returnsStart() {
        val videos =
            listOf(
                video("e1", season = 1, episode = 1),
                video("e2", season = 1, episode = 2),
            )
        val result = resolvePlayIntent("series", "series1", videos, emptyMap(), now)
        assertEquals(PlayIntent.Start, result)
    }

    @Test
    fun PI7_series_latestRowS1E4At12MinUnwatched_returnsResume() {
        val videos =
            listOf(
                video("e1", season = 1, episode = 1),
                video("e2", season = 1, episode = 2),
                video("e3", season = 1, episode = 3),
                video("e4", season = 1, episode = 4),
            )
        val pos = 12 * 60 * 1000L
        val duration = 45 * 60 * 1000L
        val p =
            mapOf(
                progress("e4", positionMs = pos, durationMs = duration, watched = false, updatedAt = 2000L),
            )
        val result = resolvePlayIntent("series", "series1", videos, p, now)
        assertEquals(PlayIntent.Resume("e4", 1, 4, pos, duration), result)
    }

    @Test
    fun PI8_series_latestRowS1E4Watched_S1E5Released_returnsNext() {
        val videos =
            listOf(
                video("e4", season = 1, episode = 4),
                video("e5", season = 1, episode = 5, released = "2020-01-01"),
            )
        val p =
            mapOf(
                progress("e4", positionMs = 40_000L, durationMs = 45_000L, watched = true, updatedAt = 2000L),
            )
        val result = resolvePlayIntent("series", "series1", videos, p, now)
        assertEquals(PlayIntent.Next("e5", 1, 5), result)
    }

    @Test
    fun PI9_series_latestRowWatchedLastOfSeason1_S2E1Released_returnsNext() {
        val videos =
            listOf(
                video("s1e10", season = 1, episode = 10),
                video("s2e1", season = 2, episode = 1, released = "2021-01-01"),
            )
        val p =
            mapOf(
                progress("s1e10", watched = true, updatedAt = 2000L),
            )
        val result = resolvePlayIntent("series", "series1", videos, p, now)
        assertEquals(PlayIntent.Next("s2e1", 2, 1), result)
    }

    @Test
    fun PI10_series_latestRowS1E4Watched_S1E5FutureRelease_returnsStart() {
        val future = "2099-01-01"
        val videos =
            listOf(
                video("e4", season = 1, episode = 4),
                video("e5", season = 1, episode = 5, released = future),
            )
        val p =
            mapOf(
                progress("e4", watched = true, updatedAt = 2000L),
            )
        val result = resolvePlayIntent("series", "series1", videos, p, now)
        assertEquals(PlayIntent.Start, result)
    }

    @Test
    fun PI11_series_latestRowLastReleasedEpisodeWatched_returnsStart() {
        val videos =
            listOf(
                video("e1", season = 1, episode = 1),
                video("e2", season = 1, episode = 2),
            )
        val p =
            mapOf(
                progress("e2", watched = true, updatedAt = 2000L),
            )
        val result = resolvePlayIntent("series", "series1", videos, p, now)
        assertEquals(PlayIntent.Start, result)
    }

    @Test
    fun PI12_series_olderWatched_newerUnwatchedAt7s_skipsPartialAndReturnsNext() {
        val videos =
            listOf(
                video("e4", season = 1, episode = 4),
                video("e5", season = 1, episode = 5),
                video("e6", season = 1, episode = 6),
            )
        val p =
            mapOf(
                progress("e4", watched = true, updatedAt = 1000L),
                progress("e5", positionMs = 7_000L, durationMs = 45_000L, watched = false, updatedAt = 2000L),
            )
        val result = resolvePlayIntent("series", "series1", videos, p, now)
        assertEquals(PlayIntent.Next("e5", 1, 5), result)
    }

    @Test
    fun PI13_series_newestRowUnknownVideoId_olderWatched_returnsNext() {
        val videos =
            listOf(
                video("e3", season = 1, episode = 3),
                video("e4", season = 1, episode = 4),
            )
        val p =
            mapOf(
                progress("unknown_id", positionMs = 20_000L, durationMs = 50_000L, watched = false, updatedAt = 3000L),
                progress("e3", watched = true, updatedAt = 1000L),
            )
        val result = resolvePlayIntent("series", "series1", videos, p, now)
        assertEquals(PlayIntent.Next("e4", 1, 4), result)
    }

    @Test
    fun PI14_series_allSeason1BatchWatchedSameUpdatedAt_returnsNextS2E1() {
        val videos =
            listOf(
                video("s1e1", season = 1, episode = 1),
                video("s1e2", season = 1, episode = 2),
                video("s1e3", season = 1, episode = 3),
                video("s2e1", season = 2, episode = 1),
            )
        val batchTime = 5000L
        val p =
            mapOf(
                progress("s1e1", watched = true, updatedAt = batchTime),
                progress("s1e2", watched = true, updatedAt = batchTime),
                progress("s1e3", watched = true, updatedAt = batchTime),
            )
        val result = resolvePlayIntent("series", "series1", videos, p, now)
        assertEquals(PlayIntent.Next("s2e1", 2, 1), result)
    }

    @Test
    fun PI15_series_newestRowWatchedSpecial_olderRowWatchedS1E4_returnsNextS1E5() {
        val videos =
            listOf(
                video("sp1", season = 0, episode = 1),
                video("e4", season = 1, episode = 4),
                video("e5", season = 1, episode = 5),
            )
        val p =
            mapOf(
                progress("e4", watched = true, updatedAt = 1000L),
                progress("sp1", watched = true, updatedAt = 2000L),
            )
        val result = resolvePlayIntent("series", "series1", videos, p, now)
        assertEquals(PlayIntent.Next("e5", 1, 5), result)
    }

    @Test
    fun PI16_series_newestRowSpecialInProgress_returnsResumeSpecial() {
        val videos =
            listOf(
                video("sp1", season = 0, episode = 1),
                video("e1", season = 1, episode = 1),
            )
        val pos = 15_000L
        val dur = 50_000L
        val p =
            mapOf(
                progress("sp1", positionMs = pos, durationMs = dur, watched = false, updatedAt = 2000L),
            )
        val result = resolvePlayIntent("series", "series1", videos, p, now)
        assertEquals(PlayIntent.Resume("sp1", 0, 1, pos, dur), result)
    }

    @Test
    fun PI17_series_s1e1to3Watched_thenS1E7WatchedNewest_returnsNextS1E8() {
        val videos =
            (1..10).map { ep ->
                video("e$ep", season = 1, episode = ep)
            }
        val p =
            mapOf(
                progress("e1", watched = true, updatedAt = 1000L),
                progress("e2", watched = true, updatedAt = 1000L),
                progress("e3", watched = true, updatedAt = 1000L),
                progress("e7", watched = true, updatedAt = 2000L),
            )
        val result = resolvePlayIntent("series", "series1", videos, p, now)
        assertEquals(PlayIntent.Next("e8", 1, 8), result)
    }

    @Test
    fun PI18_series_videosWithNullSeasonAndEpisode_orderByListPosition_nextCarriesNull() {
        val videos =
            listOf(
                video("part1", season = null, episode = null),
                video("part2", season = null, episode = null),
                video("part3", season = null, episode = null),
            )
        val p =
            mapOf(
                progress("part1", watched = true, updatedAt = 1000L),
            )
        val result = resolvePlayIntent("series", "series1", videos, p, now)
        assertEquals(PlayIntent.Next("part2", null, null), result)
    }

    @Test
    fun PI19_series_twoUnwatchedRows_olderFurtherAlong_newerWins() {
        val videos =
            listOf(
                video("e1", season = 1, episode = 1),
                video("e2", season = 1, episode = 2),
            )
        val p =
            mapOf(
                progress("e1", positionMs = 30_000L, durationMs = 45_000L, watched = false, updatedAt = 1000L),
                progress("e2", positionMs = 15_000L, durationMs = 45_000L, watched = false, updatedAt = 2000L),
            )
        val result = resolvePlayIntent("series", "series1", videos, p, now)
        assertEquals(PlayIntent.Resume("e2", 1, 2, 15_000L, 45_000L), result)
    }

    @Test
    fun PI20_series_unsortedVideosAndUnsortedRows_sameResultAsSorted() {
        val sortedVideos =
            listOf(
                video("e1", season = 1, episode = 1),
                video("e2", season = 1, episode = 2),
                video("e3", season = 1, episode = 3),
            )
        val unsortedVideos =
            listOf(
                video("e3", season = 1, episode = 3),
                video("e1", season = 1, episode = 1),
                video("e2", season = 1, episode = 2),
            )
        val p =
            mapOf(
                progress("e1", watched = true, updatedAt = 1000L),
            )

        val resultSorted = resolvePlayIntent("series", "series1", sortedVideos, p, now)
        val resultUnsorted = resolvePlayIntent("series", "series1", unsortedVideos, p, now)

        assertEquals(PlayIntent.Next("e2", 1, 2), resultSorted)
        assertEquals(resultSorted, resultUnsorted)
    }

    @Test
    fun PI21_series_nextEpisodeReleasedNull_treatedAsReleasedPerExistingFunction() {
        val videos =
            listOf(
                video("e1", season = 1, episode = 1),
                video("e2", season = 1, episode = 2, released = null),
            )
        val p =
            mapOf(
                progress("e1", watched = true, updatedAt = 1000L),
            )
        val result = resolvePlayIntent("series", "series1", videos, p, now)
        assertEquals(PlayIntent.Next("e2", 1, 2), result)
    }

    @Test
    fun PI22_typeMovieWithVideos_usesMovieRule_typeTvWithVideos_usesSeriesRule() {
        val videos =
            listOf(
                video("v1", season = 1, episode = 1),
            )
        val p =
            mapOf(
                progress("movieMeta", positionMs = 15_000L, durationMs = 50_000L, watched = false, updatedAt = 1000L),
                progress("v1", positionMs = 20_000L, durationMs = 50_000L, watched = false, updatedAt = 2000L),
            )

        val movieResult = resolvePlayIntent("movie", "movieMeta", videos, p, now)
        assertEquals(PlayIntent.Resume("movieMeta", null, null, 15_000L, 50_000L), movieResult)

        val tvResult = resolvePlayIntent("tv", "tvMeta", videos, p, now)
        assertEquals(PlayIntent.Resume("v1", 1, 1, 20_000L, 50_000L), tvResult)
    }

    @Test
    fun TL1_fortyTwoMinutesLeft() {
        assertEquals(TimeLeft(0, 42), timeLeftParts(positionMs = 0L, durationMs = 42 * 60_000L))
    }

    @Test
    fun TL2_thirtySecondsLeft() {
        assertEquals(TimeLeft(0, 1), timeLeftParts(positionMs = 30_000L, durationMs = 60_000L))
    }

    @Test
    fun TL3_exactlySixtyMinutesLeft() {
        assertEquals(TimeLeft(1, 0), timeLeftParts(positionMs = 0L, durationMs = 60 * 60_000L))
    }

    @Test
    fun TL4_oneHourFiveMinOneSecLeft() {
        val remaining = (65 * 60 + 1) * 1000L
        assertEquals(TimeLeft(1, 6), timeLeftParts(positionMs = 0L, durationMs = remaining))
    }

    @Test
    fun TL5_positionAboveDuration() {
        assertEquals(TimeLeft(0, 1), timeLeftParts(positionMs = 70_000L, durationMs = 60_000L))
    }

    @Test
    fun TL6_durationZero() {
        assertEquals(TimeLeft(0, 0), timeLeftParts(positionMs = 10_000L, durationMs = 0L))
        assertEquals(TimeLeft(0, 0), timeLeftParts(positionMs = 10_000L, durationMs = -100L))
    }

    @Test
    fun PX1_gapWatchedSeries_nextPointsAfterNewestWatched() {
        val videos =
            (1..8).map { ep ->
                video("e$ep", season = 1, episode = ep, released = "2020-01-01")
            }
        val p =
            mapOf(
                progress("e1", watched = true, updatedAt = 1000L),
                progress("e2", watched = true, updatedAt = 1001L),
                progress("e3", watched = true, updatedAt = 1002L),
                progress("e7", watched = true, updatedAt = 5000L),
            )
        val result = resolvePlayIntent("series", "meta", videos, p, now)
        assertEquals(PlayIntent.Next(videoId = "e8", season = 1, episode = 8), result)
    }

    @Test
    fun PX2_nullSeasonAndEpisode_nextCarriesNullsInListOrder() {
        val videos =
            listOf(
                video("a", season = null, episode = null),
                video("b", season = null, episode = null),
                video("c", season = null, episode = null),
            )
        val p = mapOf(progress("b", watched = true, updatedAt = 1000L))
        val result = resolvePlayIntent("series", "meta", videos, p, now)
        assertEquals(PlayIntent.Next(videoId = "c", season = null, episode = null), result)
    }

    @Test
    fun PX3_unsortedVideosAndProgress_identicalResultToSorted() {
        // PI8 case
        val pi8Videos =
            listOf(
                video("e4", season = 1, episode = 4),
                video("e5", season = 1, episode = 5),
            )
        val pi8Progress = mapOf(progress("e4", watched = true, updatedAt = 1000L))
        val pi8Sorted = resolvePlayIntent("series", "meta", pi8Videos, pi8Progress, now)
        val pi8Shuffled =
            resolvePlayIntent(
                "series",
                "meta",
                pi8Videos.reversed(),
                pi8Progress,
                now,
            )
        assertEquals(pi8Sorted, pi8Shuffled)
        assertEquals(PlayIntent.Next("e5", 1, 5), pi8Sorted)

        // PI14 case
        val pi14Videos =
            listOf(
                video("s1e1", season = 1, episode = 1),
                video("s1e2", season = 1, episode = 2),
                video("s2e1", season = 2, episode = 1),
            )
        val pi14Progress =
            mapOf(
                progress("s1e1", watched = true, updatedAt = 1000L),
                progress("s1e2", watched = true, updatedAt = 1000L),
            )
        val pi14Sorted = resolvePlayIntent("series", "meta", pi14Videos, pi14Progress, now)
        val pi14Shuffled =
            resolvePlayIntent(
                "series",
                "meta",
                listOf(pi14Videos[1], pi14Videos[2], pi14Videos[0]),
                mapOf(
                    progress("s1e2", watched = true, updatedAt = 1000L),
                    progress("s1e1", watched = true, updatedAt = 1000L),
                ),
                now,
            )
        assertEquals(pi14Sorted, pi14Shuffled)
        assertEquals(PlayIntent.Next("s2e1", 2, 1), pi14Sorted)
    }

    @Test
    fun PX4_nullReleasedDate_followsVideoIsReleasedRule() {
        // Real Video.isReleased(now) rule from EpisodeLogic.kt:
        // fun Video.isReleased(now: Long): Boolean {
        //     val dateStr = released?.trim().orEmpty()
        //     if (dateStr.isEmpty()) return true
        //     val epochMs = parseReleaseDateToEpoch(dateStr)
        //     return epochMs == null || epochMs <= now
        // }
        // For released = null, dateStr is empty, returning true (released).
        val videos =
            listOf(
                video("e4", season = 1, episode = 4, released = "2020-01-01"),
                video("e5", season = 1, episode = 5, released = null),
            )
        val p = mapOf(progress("e4", watched = true, updatedAt = 1000L))
        val result = resolvePlayIntent("series", "meta", videos, p, now)
        assertEquals(PlayIntent.Next(videoId = "e5", season = 1, episode = 5), result)
    }

    @Test
    fun PX5_movieWithNonEmptyVideos_usesMovieRule() {
        val videos = listOf(video("v1", season = 1, episode = 1))
        val p =
            mapOf(
                progress("m1", positionMs = 50_000L, durationMs = 100_000L, watched = false, updatedAt = 1000L),
            )
        val result = resolvePlayIntent("movie", "m1", videos, p, now)
        assertEquals(
            PlayIntent.Resume(
                videoId = "m1",
                season = null,
                episode = null,
                positionMs = 50_000L,
                durationMs = 100_000L,
            ),
            result,
        )
    }

    @Test
    fun PX6_typeTvWithVideos_usesSeriesRule() {
        val videos =
            listOf(
                video("e1", season = 1, episode = 1, released = "2020-01-01"),
                video("e2", season = 1, episode = 2, released = "2020-01-01"),
            )
        val p = mapOf(progress("e1", watched = true, updatedAt = 1000L))
        val result = resolvePlayIntent("tv", "tv1", videos, p, now)
        assertEquals(PlayIntent.Next(videoId = "e2", season = 1, episode = 2), result)
    }

    @Test
    fun PX7_newestRowWatchedSpecial_noRegularRow_returnsStart() {
        val videos =
            listOf(
                video("s0e1", season = 0, episode = 1),
                video("s1e1", season = 1, episode = 1),
            )
        val p = mapOf(progress("s0e1", watched = true, updatedAt = 1000L))
        val result = resolvePlayIntent("series", "meta", videos, p, now)
        assertEquals(PlayIntent.Start, result)
    }

    @Test
    fun PX8_sameUpdatedAt_orderKeyDescendingDecidesTie() {
        val videos =
            listOf(
                video("e3", season = 1, episode = 3),
                video("e4", season = 1, episode = 4),
            )
        val p =
            mapOf(
                progress("e3", watched = true, updatedAt = 1000L),
                progress("e4", positionMs = 20_000L, durationMs = 60_000L, watched = false, updatedAt = 1000L),
            )
        val result = resolvePlayIntent("series", "meta", videos, p, now)
        assertEquals(
            PlayIntent.Resume(
                videoId = "e4",
                season = 1,
                episode = 4,
                positionMs = 20_000L,
                durationMs = 60_000L,
            ),
            result,
        )
    }

    @Test
    fun PX9_duplicateVideoId_deterministicResultNoCrash() {
        val videos =
            listOf(
                video("dup", season = 1, episode = 1),
                video("dup", season = 1, episode = 2),
                video("e3", season = 1, episode = 3),
            )
        val p = mapOf(progress("dup", watched = true, updatedAt = 1000L))
        val result = resolvePlayIntent("series", "meta", videos, p, now)
        assertEquals(PlayIntent.Next(videoId = "e3", season = 1, episode = 3), result)
    }

    @Test
    fun PX10_unreleasedEpisodeWithResumableRow_returnsResume() {
        val videos =
            listOf(
                video("e5", season = 1, episode = 5, released = "2099-01-01"),
            )
        val p =
            mapOf(
                progress("e5", positionMs = 30_000L, durationMs = 60_000L, watched = false, updatedAt = 1000L),
            )
        val result = resolvePlayIntent("series", "meta", videos, p, now)
        assertEquals(
            PlayIntent.Resume(
                videoId = "e5",
                season = 1,
                episode = 5,
                positionMs = 30_000L,
                durationMs = 60_000L,
            ),
            result,
        )
    }

    @Test
    fun TX1_oneHourFiveMinOneSecLeft() {
        val remaining = (65 * 60 + 1) * 1000L
        assertEquals(TimeLeft(1, 6), timeLeftParts(positionMs = 0L, durationMs = remaining))
    }

    @Test
    fun TX2_positionAboveDuration() {
        assertEquals(TimeLeft(0, 1), timeLeftParts(positionMs = 70_000L, durationMs = 60_000L))
    }

    @Test
    fun TX3_sixtyMinAndOneMsLeft() {
        val durationMs = 60 * 60 * 1000L + 1L
        assertEquals(TimeLeft(1, 1), timeLeftParts(positionMs = 0L, durationMs = durationMs))
    }
}
