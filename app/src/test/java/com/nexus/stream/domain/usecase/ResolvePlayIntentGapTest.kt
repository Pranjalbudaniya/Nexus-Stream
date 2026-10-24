package com.nexus.stream.domain.usecase

import com.nexus.stream.domain.model.EpisodeProgress
import com.nexus.stream.domain.model.PlayIntent
import com.nexus.stream.domain.model.Video
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResolvePlayIntentGapTest {
    private val now = 1_800_000_000_000L
    private val past = "2020-01-01T00:00:00.000Z"
    private val future = "2999-01-01T00:00:00.000Z"

    private fun video(
        season: Int?,
        episode: Int?,
        released: String? = past,
        id: String = "tt1:$season:$episode",
    ) = Video(
        id = id,
        title = "E",
        released = released,
        season = season,
        episode = episode,
        thumbnail = null,
        overview = null,
    )

    private fun row(
        videoId: String,
        updatedAt: Long,
        positionMs: Long = 0L,
        durationMs: Long = 0L,
        watched: Boolean = false,
    ) = EpisodeProgress(
        videoId = videoId,
        positionMs = positionMs,
        durationMs = durationMs,
        watched = watched,
        updatedAt = updatedAt,
    )

    private fun resolve(
        videos: List<Video>,
        rows: List<EpisodeProgress>,
        type: String = "series",
    ): PlayIntent =
        resolvePlayIntent(
            type = type,
            metaId = "tt1",
            videos = videos,
            progress = rows.associateBy { it.videoId },
            now = now,
        )

    private val season1 = (1..8).map { video(1, it) }

    @Test
    fun PG0_releaseDatesBehaveAsAssumed() {
        assertTrue(video(1, 1, past).isReleased(now))
        assertFalse(video(1, 1, future).isReleased(now))
        assertTrue(video(1, 1, null).isReleased(now))
        assertTrue(video(1, 1, "").isReleased(now))
    }

    @Test
    fun PG1_gapInWatchedEpisodes_nextFollowsTheNewestWatchedOne() {
        val rows =
            listOf(
                row("tt1:1:1", 1, watched = true),
                row("tt1:1:2", 2, watched = true),
                row("tt1:1:3", 3, watched = true),
                row("tt1:1:7", 10, watched = true),
            )
        assertEquals(PlayIntent.Next("tt1:1:8", 1, 8), resolve(season1, rows))
    }

    @Test
    fun PG2_nullSeasonAndEpisode_orderedByListPosition_nextCarriesNulls() {
        val videos =
            listOf(
                video(null, null, id = "a"),
                video(null, null, id = "b"),
                video(null, null, id = "c"),
            )
        val rows = listOf(row("b", 5, watched = true))
        assertEquals(PlayIntent.Next("c", null, null), resolve(videos, rows))
    }

    @Test
    fun PG3_shuffledInput_givesTheSameResult_asSortedInput() {
        val videos = season1 + (1..3).map { video(2, it) }
        val rows = (1..8).map { row("tt1:1:$it", 5, watched = true) }
        val sorted = resolve(videos, rows)
        val shuffled = resolve(videos.reversed(), rows.reversed())
        assertEquals(PlayIntent.Next("tt1:2:1", 2, 1), sorted)
        assertEquals(sorted, shuffled)
    }

    @Test
    fun PG3b_shuffledInput_inProgressNewestWins() {
        val rows =
            listOf(
                row("tt1:1:3", 1, 600_000, 2_400_000),
                row("tt1:1:4", 2, 300_000, 2_400_000),
            )
        val expected = PlayIntent.Resume("tt1:1:4", 1, 4, 300_000, 2_400_000)
        assertEquals(expected, resolve(season1, rows))
        assertEquals(expected, resolve(season1.reversed(), rows.reversed()))
    }

    @Test
    fun PG4_nextEpisodeWithNullOrBlankRelease_countsAsReleased() {
        val rows = listOf(row("tt1:1:4", 1, watched = true))
        val nullRelease = (1..4).map { video(1, it) } + video(1, 5, released = null)
        val blankRelease = (1..4).map { video(1, it) } + video(1, 5, released = "")
        assertEquals(PlayIntent.Next("tt1:1:5", 1, 5), resolve(nullRelease, rows))
        assertEquals(PlayIntent.Next("tt1:1:5", 1, 5), resolve(blankRelease, rows))
    }

    @Test
    fun PG4b_nextEpisodeInTheFuture_givesStart() {
        val videos = (1..4).map { video(1, it) } + video(1, 5, released = future)
        val rows = listOf(row("tt1:1:4", 1, watched = true))
        assertEquals(PlayIntent.Start, resolve(videos, rows))
    }

    @Test
    fun PG5_typeMovieWithVideos_usesTheMovieRule() {
        val intent =
            resolve(
                videos = listOf(video(1, 1)),
                rows = listOf(row("tt1", 1, 600_000, 7_200_000)),
                type = "movie",
            )
        assertEquals(PlayIntent.Resume("tt1", null, null, 600_000, 7_200_000), intent)
    }

    @Test
    fun PG6_typeTvWithVideos_usesTheSeriesRule() {
        val rows = listOf(row("tt1:1:1", 1, watched = true))
        assertEquals(PlayIntent.Next("tt1:1:2", 1, 2), resolve(season1, rows, type = "tv"))
    }

    @Test
    fun PG6b_typeIsCaseInsensitive_forMovie() {
        val intent =
            resolve(
                videos = listOf(video(1, 1)),
                rows = listOf(row("tt1", 1, 600_000, 7_200_000)),
                type = "MOVIE",
            )
        assertEquals(PlayIntent.Resume("tt1", null, null, 600_000, 7_200_000), intent)
    }

    @Test
    fun PG7_onlyAWatchedSpecial_givesStart() {
        val videos = listOf(video(0, 1), video(1, 1))
        val rows = listOf(row("tt1:0:1", 1, watched = true))
        assertEquals(PlayIntent.Start, resolve(videos, rows))
    }

    @Test
    fun PG8_tieOnUpdatedAt_inProgressHigherEpisodeBeatsWatchedLowerEpisode() {
        val rows =
            listOf(
                row("tt1:1:3", 7, watched = true),
                row("tt1:1:4", 7, 600_000, 2_400_000),
            )
        assertEquals(PlayIntent.Resume("tt1:1:4", 1, 4, 600_000, 2_400_000), resolve(season1, rows))
    }

    @Test
    fun PG9_duplicateVideoIds_doNotCrash_andGiveNext() {
        val videos = listOf(video(1, 1), video(1, 1), video(1, 2))
        val rows = listOf(row("tt1:1:1", 1, watched = true))
        assertEquals(PlayIntent.Next("tt1:1:2", 1, 2), resolve(videos, rows))
    }

    @Test
    fun PG10_unreleasedEpisodeWithResumableRow_stillResumes() {
        val videos = (1..4).map { video(1, it) } + video(1, 5, released = future)
        val rows = listOf(row("tt1:1:5", 1, 600_000, 2_400_000))
        assertEquals(PlayIntent.Resume("tt1:1:5", 1, 5, 600_000, 2_400_000), resolve(videos, rows))
    }

    @Test
    fun TG1_oneHourFiveMinutesOneSecondLeft_isOneHourSixMinutes() {
        assertEquals(TimeLeft(1, 6), timeLeftParts(positionMs = 3_299_000L, durationMs = 7_200_000L))
    }

    @Test
    fun TG2_positionAboveDuration_isOneMinute() {
        assertEquals(TimeLeft(0, 1), timeLeftParts(positionMs = 8_000_000L, durationMs = 7_200_000L))
    }

    @Test
    fun TG3_sixtyMinutesAndOneMillisecondLeft_isOneHourOneMinute() {
        assertEquals(TimeLeft(1, 1), timeLeftParts(positionMs = 3_599_999L, durationMs = 7_200_000L))
    }
}
