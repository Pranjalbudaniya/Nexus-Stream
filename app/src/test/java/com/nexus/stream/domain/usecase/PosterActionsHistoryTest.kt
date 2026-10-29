package com.nexus.stream.domain.usecase

import com.nexus.stream.domain.model.ListStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PosterActionsHistoryTest {
    @Test
    fun PH1_fromHistory_addsRemoveFromHistoryAsTheLastRow() {
        val actions =
            posterActions(
                type = "movie",
                trailer = TrailerState.NONE,
                status = null,
                fromContinue = false,
                fromHistory = true,
            )
        assertEquals(PosterAction.RemoveFromHistory, actions.last())
    }

    @Test
    fun PH2_default_hasNoHistoryAction() {
        val actionsMovie =
            posterActions(
                type = "movie",
                trailer = TrailerState.AVAILABLE,
                status = ListStatus.PLAN_TO_WATCH,
                fromContinue = false,
            )
        assertFalse(actionsMovie.contains(PosterAction.RemoveFromHistory))

        val actionsContinue =
            posterActions(
                type = "movie",
                trailer = TrailerState.NONE,
                status = null,
                fromContinue = true,
            )
        assertFalse(actionsContinue.contains(PosterAction.RemoveFromHistory))
    }

    @Test
    fun PH3_fromHistory_keepsTheOtherRowsInOrder() {
        val resume = PosterAction.Resume(videoId = "v1", season = 1, episode = 2)
        val actions =
            posterActions(
                type = "series",
                trailer = TrailerState.AVAILABLE,
                status = ListStatus.WATCHING,
                fromContinue = false,
                resume = resume,
                fromHistory = true,
            )
        val expected =
            listOf(
                resume,
                PosterAction.Detail,
                PosterAction.Trailer(TrailerState.AVAILABLE),
                PosterAction.Watchlist(ListStatus.WATCHING),
                PosterAction.RemoveFromHistory,
            )
        assertEquals(expected, actions)
    }
}
