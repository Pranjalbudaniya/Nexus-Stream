package com.nexus.stream.domain.usecase

import com.nexus.stream.domain.model.EpisodeProgress
import com.nexus.stream.domain.model.SeasonGroup
import com.nexus.stream.domain.model.Video
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EpisodeLogicTest {
    private val now = 1_700_000_000_000L // arbitrary fixed timestamp (~Nov 2023)

    private fun createVideo(
        id: String,
        season: Int? = null,
        episode: Int? = null,
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
    fun `groupEpisodes groups numbered seasons ascending with specials last and null in addon order`() {
        val videos =
            listOf(
                createVideo(id = "s2e2", season = 2, episode = 2),
                createVideo(id = "s0e1", season = 0, episode = 1),
                createVideo(id = "null_b", season = null, episode = null),
                createVideo(id = "s1e2", season = 1, episode = 2),
                createVideo(id = "null_a", season = null, episode = null),
                createVideo(id = "s1e1", season = 1, episode = 1),
                createVideo(id = "s1_null_ep", season = 1, episode = null),
                createVideo(id = "s2e1", season = 2, episode = 1),
                createVideo(id = "s0e2", season = 0, episode = 2),
            )

        val groups = groupEpisodes(videos)
        assertEquals(4, groups.size)

        // Group 0: Season 1 (episodes: s1e1, s1e2, s1_null_ep)
        assertEquals(1, groups[0].season)
        assertEquals(listOf("s1e1", "s1e2", "s1_null_ep"), groups[0].episodes.map { it.id })

        // Group 1: Season 2 (episodes: s2e1, s2e2)
        assertEquals(2, groups[1].season)
        assertEquals(listOf("s2e1", "s2e2"), groups[1].episodes.map { it.id })

        // Group 2: Null season (keeps addon order: null_b, null_a)
        assertNull(groups[2].season)
        assertEquals(listOf("null_b", "null_a"), groups[2].episodes.map { it.id })

        // Group 3: Season 0 Specials (episodes: s0e1, s0e2)
        assertEquals(0, groups[3].season)
        assertEquals(listOf("s0e1", "s0e2"), groups[3].episodes.map { it.id })
    }

    @Test
    fun `groupEpisodes returns empty list for empty videos`() {
        assertTrue(groupEpisodes(emptyList()).isEmpty())
    }

    @Test
    fun `defaultSeason returns season of last watched video if present`() {
        val groups =
            listOf(
                SeasonGroup(season = 1, episodes = listOf(createVideo("s1e1", 1, 1))),
                SeasonGroup(season = 2, episodes = listOf(createVideo("s2e1", 2, 1))),
                SeasonGroup(season = 0, episodes = listOf(createVideo("s0e1", 0, 1))),
            )

        assertEquals(2, defaultSeason(groups, lastWatchedVideoId = "s2e1"))
        assertEquals(0, defaultSeason(groups, lastWatchedVideoId = "s0e1"))
    }

    @Test
    fun `defaultSeason returns first non-special group when last watched is not found or null`() {
        val groups =
            listOf(
                SeasonGroup(season = 0, episodes = listOf(createVideo("s0e1", 0, 1))),
                SeasonGroup(season = 1, episodes = listOf(createVideo("s1e1", 1, 1))),
            )

        assertEquals(1, defaultSeason(groups, lastWatchedVideoId = null))
        assertEquals(1, defaultSeason(groups, lastWatchedVideoId = "non_existent"))
    }

    @Test
    fun `defaultSeason falls back to first group when all are specials`() {
        val groups =
            listOf(
                SeasonGroup(season = 0, episodes = listOf(createVideo("s0e1", 0, 1))),
            )

        assertEquals(0, defaultSeason(groups, lastWatchedVideoId = null))
    }

    @Test
    fun `defaultSeason returns null for empty groups`() {
        assertNull(defaultSeason(emptyList(), lastWatchedVideoId = "anything"))
    }

    @Test
    fun `resumeVideoId returns in-progress episode when position greater than 0 and unwatched`() {
        val videos =
            listOf(
                createVideo(id = "s1e1", season = 1, episode = 1, released = "2020-01-01"),
                createVideo(id = "s1e2", season = 1, episode = 2, released = "2020-01-08"),
            )
        val progress =
            mapOf(
                "s1e1" to
                    EpisodeProgress(
                        videoId = "s1e1",
                        positionMs = 120_000L,
                        durationMs = 300_000L,
                        watched = false,
                        updatedAt = 1000L,
                    ),
            )

        assertEquals("s1e1", resumeVideoId(videos, progress, now))
    }

    @Test
    fun `resumeVideoId picks most recently updated in-progress episode`() {
        val videos =
            listOf(
                createVideo(id = "s1e1", season = 1, episode = 1, released = "2020-01-01"),
                createVideo(id = "s1e2", season = 1, episode = 2, released = "2020-01-08"),
            )
        val progress =
            mapOf(
                "s1e1" to
                    EpisodeProgress(
                        videoId = "s1e1",
                        positionMs = 50_000L,
                        durationMs = 300_000L,
                        watched = false,
                        updatedAt = 1000L,
                    ),
                "s1e2" to
                    EpisodeProgress(
                        videoId = "s1e2",
                        positionMs = 20_000L,
                        durationMs = 300_000L,
                        watched = false,
                        updatedAt = 2000L,
                    ),
            )

        assertEquals("s1e2", resumeVideoId(videos, progress, now))
    }

    @Test
    fun `resumeVideoId returns first released episode after last watched episode in sorted order`() {
        val videos =
            listOf(
                createVideo(id = "s1e1", season = 1, episode = 1, released = "2020-01-01"),
                createVideo(id = "s1e2", season = 1, episode = 2, released = "2020-01-08"),
                createVideo(id = "s1e3", season = 1, episode = 3, released = "2020-01-15"),
            )
        val progress =
            mapOf(
                "s1e1" to
                    EpisodeProgress(
                        videoId = "s1e1",
                        positionMs = 0L,
                        durationMs = 0L,
                        watched = true,
                        updatedAt = 1000L,
                    ),
            )

        assertEquals("s1e2", resumeVideoId(videos, progress, now))
    }

    @Test
    fun `resumeVideoId returns first released episode when no progress exists`() {
        val videos =
            listOf(
                createVideo(id = "s1e1", season = 1, episode = 1, released = "2020-01-01"),
                createVideo(id = "s1e2", season = 1, episode = 2, released = "2020-01-08"),
            )

        assertEquals("s1e1", resumeVideoId(videos, emptyMap(), now))
    }

    @Test
    fun `resumeVideoId skips unreleased episodes`() {
        val videos =
            listOf(
                createVideo(id = "s1e1", season = 1, episode = 1, released = "2020-01-01"),
                createVideo(id = "s1e2", season = 1, episode = 2, released = "2099-01-01"),
            )
        val progress =
            mapOf(
                "s1e1" to
                    EpisodeProgress(
                        videoId = "s1e1",
                        positionMs = 0L,
                        durationMs = 0L,
                        watched = true,
                        updatedAt = 1000L,
                    ),
            )

        // s1e2 is unreleased, so there is no released episode after s1e1
        assertNull(resumeVideoId(videos, progress, now))
    }

    @Test
    fun `resumeVideoId returns null for empty video list`() {
        assertNull(resumeVideoId(emptyList(), emptyMap(), now))
    }

    @Test
    fun `isReleased handles past, future, date-time with offset, unparsable and missing`() {
        val pastDate = createVideo("1", released = "2020-01-01")
        assertTrue(pastDate.isReleased(now))

        val futureDate = createVideo("2", released = "2099-01-01")
        assertFalse(futureDate.isReleased(now))

        val pastDateTime = createVideo("3", released = "2020-01-01T12:00:00Z")
        assertTrue(pastDateTime.isReleased(now))

        val futureDateTimeOffset = createVideo("4", released = "2099-01-01T12:00:00+02:00")
        assertFalse(futureDateTimeOffset.isReleased(now))

        val unparsable = createVideo("5", released = "not_a_valid_date")
        assertTrue(unparsable.isReleased(now))

        val nullDate = createVideo("6", released = null)
        assertTrue(nullDate.isReleased(now))

        val blankDate = createVideo("7", released = "   ")
        assertTrue(blankDate.isReleased(now))
    }
}
