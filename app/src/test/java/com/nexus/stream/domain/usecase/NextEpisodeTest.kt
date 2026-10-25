package com.nexus.stream.domain.usecase

import com.nexus.stream.domain.model.EpisodeProgress
import com.nexus.stream.domain.model.PlayIntent
import com.nexus.stream.domain.model.Video
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NextEpisodeTest {
    private val now = 1_800_000_000_000L
    private val past = "2020-01-01T00:00:00.000Z"
    private val future = "2999-01-01T00:00:00.000Z"

    private fun video(
        season: Int?,
        episode: Int?,
        released: String? = past,
        id: String = if (season != null && episode != null) "tt1:$season:$episode" else "tt1:null:$episode",
        title: String = "Ep",
    ) = Video(
        id = id,
        title = title,
        released = released,
        season = season,
        episode = episode,
        thumbnail = null,
        overview = null,
    )

    @Test
    fun NE1_sameSeason_nextEpisode() {
        val videos = (1..8).map { video(1, it) }
        val next = nextRegularEpisode(videos, "tt1:1:4", now)
        assertNotNull(next)
        assertEquals("tt1:1:5", next?.id)
        assertEquals(1, next?.season)
        assertEquals(5, next?.episode)
    }

    @Test
    fun NE2_lastOfSeason_firstOfNextSeason() {
        val videos = (1..8).map { video(1, it) } + (1..3).map { video(2, it) }
        val next = nextRegularEpisode(videos, "tt1:1:8", now)
        assertNotNull(next)
        assertEquals("tt1:2:1", next?.id)
        assertEquals(2, next?.season)
        assertEquals(1, next?.episode)
    }

    @Test
    fun NE3_lastEpisodeOverall_null() {
        val videos = (1..8).map { video(1, it) }
        val next = nextRegularEpisode(videos, "tt1:1:8", now)
        assertNull(next)
    }

    @Test
    fun NE4_specialsAreSkipped() {
        val s0e1 = video(0, 1, id = "tt1:0:1")
        val s1e1 = video(1, 1, id = "tt1:1:1")
        val s1e2 = video(1, 2, id = "tt1:1:2")
        val videos = listOf(s0e1, s1e1, s1e2)

        val nextFromRegular = nextRegularEpisode(videos, "tt1:1:1", now)
        assertNotNull(nextFromRegular)
        assertEquals("tt1:1:2", nextFromRegular?.id)

        val nextFromSpecial = nextRegularEpisode(videos, "tt1:0:1", now)
        assertNull(nextFromSpecial)
    }

    @Test
    fun NE5_unreleasedNext_nullWhenNothingLaterIsReleased() {
        val videos = (1..4).map { video(1, it) } + listOf(video(1, 5, released = future))
        val next = nextRegularEpisode(videos, "tt1:1:4", now)
        assertNull(next)
    }

    @Test
    fun NE6_unreleasedInBetween_skippedWhenALaterOneIsReleased() {
        val videos =
            (1..4).map { video(1, it) } +
                listOf(
                    video(1, 5, released = future),
                    video(1, 6, released = past),
                )
        val next = nextRegularEpisode(videos, "tt1:1:4", now)
        assertNotNull(next)
        assertEquals("tt1:1:6", next?.id)
        assertEquals(6, next?.episode)
    }

    @Test
    fun NE7_unknownCurrentId_null() {
        val videos = (1..8).map { video(1, it) }
        val next = nextRegularEpisode(videos, "unknown_id", now)
        assertNull(next)
    }

    @Test
    fun NE8_nullSeasonAndEpisode_useListOrder() {
        val v1 = Video("v1", "First", past, null, null, null, null)
        val v2 = Video("v2", "Second", past, null, null, null, null)
        val v3 = Video("v3", "Third", past, null, null, null, null)
        val videos = listOf(v1, v2, v3)

        val next = nextRegularEpisode(videos, "v2", now)
        assertNotNull(next)
        assertEquals("v3", next?.id)
        assertNull(next?.season)
        assertNull(next?.episode)
    }

    @Test
    fun NE9_shuffledInput_sameResultAsSorted() {
        val sorted = (1..8).map { video(1, it) } + (1..3).map { video(2, it) }
        val reversed = sorted.reversed()

        val expected = nextRegularEpisode(sorted, "tt1:1:4", now)
        val actual = nextRegularEpisode(reversed, "tt1:1:4", now)
        assertEquals(expected?.id, actual?.id)
        assertEquals("tt1:1:5", actual?.id)
    }

    @Test
    fun NE10_blankOrUnparseableReleased_countsAsReleased() {
        val v1 = video(1, 1, id = "tt1:1:1")
        val v2 = video(1, 2, released = null, id = "tt1:1:2")
        val v3 = video(1, 3, released = "", id = "tt1:1:3")
        val v4 = video(1, 4, released = "not-a-date", id = "tt1:1:4")
        val videos = listOf(v1, v2, v3, v4)

        assertEquals("tt1:1:2", nextRegularEpisode(videos, "tt1:1:1", now)?.id)
        assertEquals("tt1:1:3", nextRegularEpisode(videos, "tt1:1:2", now)?.id)
        assertEquals("tt1:1:4", nextRegularEpisode(videos, "tt1:1:3", now)?.id)
    }

    @Test
    fun NE11_duplicateIds_noCrash() {
        val v1 = video(1, 1, id = "dup:1")
        val v2 = video(1, 2, id = "dup:1")
        val v3 = video(1, 3, id = "dup:3")
        val videos = listOf(v1, v2, v3)

        val next = nextRegularEpisode(videos, "dup:1", now)
        assertNotNull(next)
        assertEquals("dup:1", next?.id)
        assertEquals(2, next?.episode)
    }

    @Test
    fun NE12_resolverAndNextShareTheSameOrdering() {
        val videos =
            listOf(
                video(2, 1),
                video(1, 3),
                video(1, 1),
                video(1, 2),
            )
        val progressWatchedE2 =
            mapOf(
                "tt1:1:2" to
                    EpisodeProgress(
                        videoId = "tt1:1:2",
                        positionMs = 60_000L,
                        durationMs = 60_000L,
                        watched = true,
                        updatedAt = 1000L,
                    ),
            )

        val resolveIntent = resolvePlayIntent("series", "tt1", videos, progressWatchedE2, now)
        assertTrue(resolveIntent is PlayIntent.Next)
        val nextFromResolver = (resolveIntent as PlayIntent.Next).videoId

        val nextFromFinder = nextRegularEpisode(videos, "tt1:1:2", now)?.id
        assertEquals("tt1:1:3", nextFromResolver)
        assertEquals(nextFromResolver, nextFromFinder)
    }
}
