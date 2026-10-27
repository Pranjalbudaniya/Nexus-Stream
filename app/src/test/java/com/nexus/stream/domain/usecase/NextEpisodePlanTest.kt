package com.nexus.stream.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NextEpisodePlanTest {
    private fun input(
        durationMs: Long = 600_000L,
        positionMs: Long = 500_000L,
        prefetch: PrefetchState = PrefetchState.FOUND,
    ) = NextPlanInput(
        hasNext = true,
        durationMs = durationMs,
        positionMs = positionMs,
        ended = false,
        dismissed = false,
        prefetch = prefetch,
    )

    @Test
    fun NP1_noNextEpisode_isNone() {
        val i = input(positionMs = 590_000L).copy(hasNext = false)
        assertEquals(NextUi.None, nextUi(i))
    }

    @Test
    fun NP2_durationBelowFiveMinutes_isNone_evenAtTheEnd() {
        val duration = 299_999L
        val i1 = input(durationMs = duration, positionMs = 280_000L)
        val i2 = input(durationMs = duration, positionMs = 299_999L).copy(ended = true)
        assertEquals(NextUi.None, nextUi(i1))
        assertEquals(NextUi.None, nextUi(i2))
    }

    @Test
    fun NP3_beforeTheWindow_isNone_atThirtySeconds_isShown() {
        val duration = 600_000L
        val beforeWindow = input(durationMs = duration, positionMs = duration - 30_001L)
        val inWindow = input(durationMs = duration, positionMs = duration - 30_000L)

        assertEquals(NextUi.None, nextUi(beforeWindow))
        assertNotEquals(NextUi.None, nextUi(inWindow))
    }

    @Test
    fun NP4_prefetchStartsAtNinetySeconds_once() {
        val duration = 600_000L
        val beforePrefetch =
            input(durationMs = duration, positionMs = duration - 90_001L, prefetch = PrefetchState.IDLE)
        val atPrefetchIdle =
            input(durationMs = duration, positionMs = duration - 90_000L, prefetch = PrefetchState.IDLE)
        val atPrefetchSearching =
            input(durationMs = duration, positionMs = duration - 90_000L, prefetch = PrefetchState.SEARCHING)
        val atPrefetchFound =
            input(durationMs = duration, positionMs = duration - 90_000L, prefetch = PrefetchState.FOUND)
        val atPrefetchNotFound =
            input(durationMs = duration, positionMs = duration - 90_000L, prefetch = PrefetchState.NOT_FOUND)

        assertFalse(shouldPrefetch(beforePrefetch))
        assertTrue(shouldPrefetch(atPrefetchIdle))
        assertFalse(shouldPrefetch(atPrefetchSearching))
        assertFalse(shouldPrefetch(atPrefetchFound))
        assertFalse(shouldPrefetch(atPrefetchNotFound))
    }

    @Test
    fun NP5_prefetchStateMapsToTheUi() {
        val duration = 600_000L
        val position = duration - 20_000L

        val foundInput = input(durationMs = duration, positionMs = position, prefetch = PrefetchState.FOUND)
        val idleInput = input(durationMs = duration, positionMs = position, prefetch = PrefetchState.IDLE)
        val searchingInput = input(durationMs = duration, positionMs = position, prefetch = PrefetchState.SEARCHING)
        val notFoundInput = input(durationMs = duration, positionMs = position, prefetch = PrefetchState.NOT_FOUND)

        assertEquals(NextUi.Ready, nextUi(foundInput))
        assertEquals(NextUi.Searching, nextUi(idleInput))
        assertEquals(NextUi.Searching, nextUi(searchingInput))
        assertEquals(NextUi.ChooseSource, nextUi(notFoundInput))
    }

    @Test
    fun NP6_dismissed_isNone() {
        val duration = 600_000L
        val position = duration - 20_000L
        val dismissedInput =
            input(durationMs = duration, positionMs = position, prefetch = PrefetchState.FOUND)
                .copy(dismissed = true)

        assertEquals(NextUi.None, nextUi(dismissedInput))
    }

    @Test
    fun NP7_rearm_onlyAboveSixtySeconds() {
        assertTrue(shouldRearm(dismissed = true, remainingMs = 60_001L))
        assertFalse(shouldRearm(dismissed = true, remainingMs = 60_000L))
        assertFalse(shouldRearm(dismissed = false, remainingMs = 100_000L))
    }

    @Test
    fun NP8_ended_showsEvenWithZeroRemaining() {
        val duration = 600_000L
        val endedInput =
            input(durationMs = duration, positionMs = duration, prefetch = PrefetchState.FOUND)
                .copy(ended = true)

        assertEquals(NextUi.Ready, nextUi(endedInput))
    }

    @Test
    fun NP9_unknownDuration_isNone() {
        val zeroDuration = input(durationMs = 0L, positionMs = 0L, prefetch = PrefetchState.IDLE)
        val negDuration = input(durationMs = -1L, positionMs = 0L, prefetch = PrefetchState.IDLE)

        assertEquals(NextUi.None, nextUi(zeroDuration))
        assertEquals(NextUi.None, nextUi(negDuration))
        assertFalse(shouldPrefetch(zeroDuration))
        assertFalse(shouldPrefetch(negDuration))
    }

    @Test
    fun NP10_positionAboveDuration_remainingIsZero() {
        assertEquals(0L, remainingMs(100L, 500L))
    }

    @Test
    fun NP11_exactlyFiveMinutes_isEligible() {
        val duration = 300_000L
        val atThirtySec = input(durationMs = duration, positionMs = duration - 30_000L, prefetch = PrefetchState.FOUND)

        assertNotEquals(NextUi.None, nextUi(atThirtySec))
    }
}
