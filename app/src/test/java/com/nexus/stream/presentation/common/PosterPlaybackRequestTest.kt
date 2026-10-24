package com.nexus.stream.presentation.common

import com.nexus.stream.domain.model.MetaPreview
import com.nexus.stream.domain.model.PosterShape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PosterPlaybackRequestTest {
    private val basePreview =
        MetaPreview(
            id = "m1",
            type = "movie",
            name = "N",
            poster = null,
            posterShape = PosterShape.POSTER,
            background = null,
            logo = null,
            description = null,
            releaseInfo = null,
            imdbRating = null,
            genres = emptyList(),
        )

    @Test
    fun AG5_moviePlay_carriesPosterNameBackgroundAndLogo() {
        val item =
            basePreview.copy(
                id = "m1",
                type = "movie",
                name = "N",
                poster = "P",
                background = "B",
                logo = "L",
                releaseInfo = "1999–",
            )
        val req = posterPlaybackRequest(item, videoId = null, season = null, episode = null)
        assertEquals("m1", req.videoId)
        assertEquals("P", req.posterUrl)
        assertEquals("B", req.backgroundUrl)
        assertEquals("L", req.logoUrl)
        assertEquals("N", req.metaName)
        assertEquals("N", req.title)
        assertEquals(1999, req.year)
        assertNull(req.season)
        assertNull(req.episode)
    }

    @Test
    fun AG6_seriesResume_carriesTheEpisodeIdAndNumbers() {
        val item =
            basePreview.copy(
                id = "s1",
                type = "series",
                name = "N",
                poster = "P",
                background = "B",
                logo = "L",
                releaseInfo = "1999–",
            )
        val req = posterPlaybackRequest(item, videoId = "tt1:1:4", season = 1, episode = 4)
        assertEquals("tt1:1:4", req.videoId)
        assertEquals(1, req.season)
        assertEquals(4, req.episode)
        assertEquals("P", req.posterUrl)
        assertEquals("B", req.backgroundUrl)
        assertEquals("L", req.logoUrl)
        assertEquals("N", req.metaName)
        assertEquals("N", req.title)
        assertEquals(1999, req.year)
    }

    @Test
    fun AG7_nullFields_staysNull_noCrash() {
        val item =
            basePreview.copy(
                id = "m2",
                type = "movie",
                name = "N",
                poster = null,
                background = null,
                logo = null,
                releaseInfo = null,
            )
        val req = posterPlaybackRequest(item, videoId = null, season = null, episode = null)
        assertNull(req.posterUrl)
        assertNull(req.backgroundUrl)
        assertNull(req.logoUrl)
        assertNull(req.year)
    }
}
