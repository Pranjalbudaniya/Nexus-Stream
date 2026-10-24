package com.nexus.stream.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PosterActionsGapTest {
    private val trailer = TrailerState.NONE

    @Test
    fun AG1_movieInProgress_resumeReplacesPlay() {
        val resume = PosterAction.Resume(videoId = "tt1")
        val actions = posterActions("movie", trailer, null, false, resume = resume)
        assertEquals(
            listOf(resume, PosterAction.Detail, PosterAction.Trailer(trailer), PosterAction.Watchlist(null)),
            actions,
        )
        assertFalse(actions.contains(PosterAction.Play))
    }

    @Test
    fun AG2_movieWithoutResume_isTheOldList_forBothFromContinueValues() {
        assertEquals(
            listOf(PosterAction.Play, PosterAction.Detail, PosterAction.Trailer(trailer), PosterAction.Watchlist(null)),
            posterActions("movie", trailer, null, false),
        )
        assertEquals(
            listOf(
                PosterAction.Play,
                PosterAction.Detail,
                PosterAction.Trailer(trailer),
                PosterAction.Watchlist(null),
                PosterAction.RemoveFromContinue,
            ),
            posterActions("movie", trailer, null, true),
        )
    }

    @Test
    fun AG3_seriesFromContinueWithResume_hasResumeAndNoPlay() {
        val resume = PosterAction.Resume(videoId = "tt1:1:4", season = 1, episode = 4)
        val actions = posterActions("series", trailer, null, true, resume = resume)
        assertEquals(
            listOf(
                resume,
                PosterAction.Detail,
                PosterAction.Trailer(trailer),
                PosterAction.Watchlist(null),
                PosterAction.RemoveFromContinue,
            ),
            actions,
        )
        assertFalse(actions.contains(PosterAction.Play))
    }

    @Test
    fun AG4_seriesWithoutResume_isTheOldList_andTypeIsCaseInsensitive() {
        val expectedFalse = listOf(PosterAction.Detail, PosterAction.Trailer(trailer), PosterAction.Watchlist(null))
        assertEquals(expectedFalse, posterActions("series", trailer, null, false))
        assertEquals(expectedFalse, posterActions("Series", trailer, null, false))
        assertEquals(
            expectedFalse + PosterAction.RemoveFromContinue,
            posterActions("series", trailer, null, true),
        )
    }
}
