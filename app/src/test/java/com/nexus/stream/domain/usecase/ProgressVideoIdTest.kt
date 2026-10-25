package com.nexus.stream.domain.usecase

import com.nexus.stream.domain.model.PlaybackSource
import com.nexus.stream.presentation.feature.player.PlaybackSelection
import com.nexus.stream.presentation.feature.player.toProgressTarget
import org.junit.Assert.assertEquals
import org.junit.Test

class ProgressVideoIdTest {
    @Test
    fun PK1_movieWithDifferentVideoId_usesMetaId() {
        assertEquals("kitsu:1", progressVideoId("movie", "kitsu:1", "kitsu:1:1"))
    }

    @Test
    fun PK2_movieTypeIsCaseInsensitive() {
        assertEquals("kitsu:1", progressVideoId("Movie", "kitsu:1", "kitsu:1:1"))
        assertEquals("kitsu:1", progressVideoId("MOVIE", "kitsu:1", "kitsu:1:1"))
    }

    @Test
    fun PK3_seriesKeepsTheVideoId() {
        assertEquals("tt1:1:4", progressVideoId("series", "tt1", "tt1:1:4"))
    }

    @Test
    fun PK4_otherTypesKeepTheVideoId() {
        assertEquals("v_tv", progressVideoId("tv", "m_tv", "v_tv"))
        assertEquals("v_anime", progressVideoId("anime", "m_anime", "v_anime"))
        assertEquals("v_channel", progressVideoId("channel", "m_channel", "v_channel"))
    }

    @Test
    fun PK5_progressKeyFor_equalsProgressKeyOfTheMapping() {
        assertEquals(
            ProgressRules.progressKey("kitsu:1", progressVideoId("movie", "kitsu:1", "kitsu:1:1")),
            progressKeyFor("movie", "kitsu:1", "kitsu:1:1"),
        )
        assertEquals("kitsu:1|kitsu:1", progressKeyFor("movie", "kitsu:1", "kitsu:1:1"))
        assertEquals(
            ProgressRules.progressKey("tt1", progressVideoId("series", "tt1", "tt1:1:4")),
            progressKeyFor("series", "tt1", "tt1:1:4"),
        )
    }

    @Test
    fun PK6_toProgressTarget_andTheStreamsKey_agree() {
        val movieSelection =
            PlaybackSelection(
                title = "Movie",
                source = PlaybackSource.Direct("https://example.com/v.mp4"),
                type = "movie",
                metaId = "kitsu:1",
                videoId = "kitsu:1:1",
            )
        val movieTarget = movieSelection.toProgressTarget()!!
        assertEquals(
            progressKeyFor(movieSelection.type, movieSelection.metaId, movieSelection.videoId),
            ProgressRules.progressKey(movieTarget.metaId, movieTarget.videoId),
        )

        val seriesSelection =
            PlaybackSelection(
                title = "Series",
                source = PlaybackSource.Direct("https://example.com/v.mp4"),
                type = "series",
                metaId = "tt1",
                videoId = "tt1:1:4",
            )
        val seriesTarget = seriesSelection.toProgressTarget()!!
        assertEquals(
            progressKeyFor(seriesSelection.type, seriesSelection.metaId, seriesSelection.videoId),
            ProgressRules.progressKey(seriesTarget.metaId, seriesTarget.videoId),
        )
    }
}
