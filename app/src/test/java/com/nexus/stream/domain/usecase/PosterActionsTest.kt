package com.nexus.stream.domain.usecase

import com.nexus.stream.domain.model.ListStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class PosterActionsTest {
    @Test
    fun `movie poster actions includes Play and correct order without continue`() {
        val actions =
            posterActions(
                type = "movie",
                trailer = TrailerState.AVAILABLE,
                status = ListStatus.WATCHING,
                fromContinue = false,
            )
        val expected =
            listOf(
                PosterAction.Play,
                PosterAction.Detail,
                PosterAction.Trailer(TrailerState.AVAILABLE),
                PosterAction.Watchlist(ListStatus.WATCHING),
            )
        assertEquals(expected, actions)
    }

    @Test
    fun `series poster actions omits Play`() {
        val actions =
            posterActions(
                type = "series",
                trailer = TrailerState.CHECKING,
                status = ListStatus.PLAN_TO_WATCH,
                fromContinue = false,
            )
        val expected =
            listOf(
                PosterAction.Detail,
                PosterAction.Trailer(TrailerState.CHECKING),
                PosterAction.Watchlist(ListStatus.PLAN_TO_WATCH),
            )
        assertEquals(expected, actions)
    }

    @Test
    fun `tv or channel poster actions includes Play`() {
        val tvActions =
            posterActions(
                type = "tv",
                trailer = TrailerState.NONE,
                status = null,
                fromContinue = false,
            )
        assertEquals(PosterAction.Play, tvActions[0])

        val channelActions =
            posterActions(
                type = "channel",
                trailer = TrailerState.NONE,
                status = null,
                fromContinue = false,
            )
        assertEquals(PosterAction.Play, channelActions[0])
    }

    @Test
    fun `actions fromContinue includes RemoveFromContinue at the end`() {
        val actions =
            posterActions(
                type = "series",
                trailer = TrailerState.NONE,
                status = ListStatus.COMPLETED,
                fromContinue = true,
            )
        val expected =
            listOf(
                PosterAction.Detail,
                PosterAction.Trailer(TrailerState.NONE),
                PosterAction.Watchlist(ListStatus.COMPLETED),
                PosterAction.RemoveFromContinue,
            )
        assertEquals(expected, actions)
    }
}
