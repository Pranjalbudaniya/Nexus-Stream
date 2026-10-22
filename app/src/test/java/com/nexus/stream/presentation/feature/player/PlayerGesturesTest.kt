package com.nexus.stream.presentation.feature.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerGesturesTest {
    @Test
    fun PG1_seekTarget_clampsCorrectly() {
        // 5 s minus 10 s -> 0
        assertEquals(0L, seekTarget(positionMs = 5_000L, durationMs = 100_000L, deltaMs = -10_000L))

        // 100 s plus 10 s with 105 s duration -> 105 s
        assertEquals(105_000L, seekTarget(positionMs = 100_000L, durationMs = 105_000L, deltaMs = 10_000L))

        // duration 0 -> no upper clamp
        assertEquals(110_000L, seekTarget(positionMs = 100_000L, durationMs = 0L, deltaMs = 10_000L))
        assertEquals(150_000L, seekTarget(positionMs = 100_000L, durationMs = -1L, deltaMs = 50_000L))
    }

    @Test
    fun PG2_accumulatedSeek_sameDirectionWithinWindow_addsStep() {
        val step = 10_000L
        val window = 800L

        // First tap right (direction = +1)
        val s1 =
            accumulatedSeek(
                prevDeltaMs = 0L,
                prevTapMs = 0L,
                nowMs = 1000L,
                direction = 1,
                stepMs = step,
                windowMs = window,
            )
        assertEquals(10_000L, s1)

        // Second tap right at +300 ms
        val s2 =
            accumulatedSeek(
                prevDeltaMs = s1,
                prevTapMs = 1000L,
                nowMs = 1300L,
                direction = 1,
                stepMs = step,
                windowMs = window,
            )
        assertEquals(20_000L, s2)

        // Third tap right at +600 ms from previous
        val s3 =
            accumulatedSeek(
                prevDeltaMs = s2,
                prevTapMs = 1300L,
                nowMs = 1900L,
                direction = 1,
                stepMs = step,
                windowMs = window,
            )
        assertEquals(30_000L, s3)

        // Tap 1 s later (1000 ms > 800 ms window) -> starts at +10 s again
        val s4 =
            accumulatedSeek(
                prevDeltaMs = s3,
                prevTapMs = 1900L,
                nowMs = 2900L,
                direction = 1,
                stepMs = step,
                windowMs = window,
            )
        assertEquals(10_000L, s4)
    }

    @Test
    fun PG3_accumulatedSeek_oppositeDirectionWithinWindow_resets() {
        val s1 = 10_000L
        // tap left (direction = -1) at +200 ms -> -10 s
        val s2 = accumulatedSeek(prevDeltaMs = s1, prevTapMs = 1000L, nowMs = 1200L, direction = -1)
        assertEquals(-10_000L, s2)
    }

    @Test
    fun PG4_adjustLevel_dragUpRaises_dragDownLowers_clamps() {
        val h = 1000f

        // 0.2, -0.5 * h -> 0.7
        val lvl1 = adjustLevel(startLevel = 0.2f, dragDeltaPx = -500f, heightPx = h)
        assertEquals(0.7f, lvl1, 0.001f)

        // drag down past bottom -> 0
        val lvl2 = adjustLevel(startLevel = 0.2f, dragDeltaPx = 800f, heightPx = h)
        assertEquals(0f, lvl2, 0.001f)

        // up past top -> 1
        val lvl3 = adjustLevel(startLevel = 0.8f, dragDeltaPx = -500f, heightPx = h)
        assertEquals(1f, lvl3, 0.001f)
    }

    @Test
    fun PG5_adjustLevel_minLevelAndZeroHeight() {
        val h = 1000f

        // minLevel = 0.02 -> never below 0.02
        val lvl = adjustLevel(startLevel = 0.1f, dragDeltaPx = 500f, heightPx = h, minLevel = 0.02f)
        assertEquals(0.02f, lvl, 0.001f)

        // heightPx = 0 returns start level
        val lvlZeroH = adjustLevel(startLevel = 0.5f, dragDeltaPx = -200f, heightPx = 0f)
        assertEquals(0.5f, lvlZeroH, 0.001f)
    }

    @Test
    fun PG6_zoneOf_leftBelowMiddle_rightAtOrAboveMiddle() {
        val width = 1080f
        val mid = width / 2f // 540f

        assertEquals(GestureZone.LEFT, zoneOf(mid - 0.1f, width))
        assertEquals(GestureZone.RIGHT, zoneOf(mid, width))
        assertEquals(GestureZone.RIGHT, zoneOf(mid + 10f, width))
    }

    @Test
    fun PG7_dragStartAllowed_deadzones() {
        val height = 1000f // top 12% is < 120, bottom 18% is > 820

        assertFalse(dragStartAllowed(0f, height))
        assertFalse(dragStartAllowed(119f, height))
        assertTrue(dragStartAllowed(120f, height))
        assertTrue(dragStartAllowed(500f, height))
        assertTrue(dragStartAllowed(820f, height))
        assertFalse(dragStartAllowed(821f, height))
        assertFalse(dragStartAllowed(1000f, height))
        assertFalse(dragStartAllowed(500f, 0f))
    }

    @Test
    fun PG8_volumeSteps_scaleAndRounding() {
        assertEquals(8, volumeSteps(0.5f, 15))
        assertEquals(0, volumeSteps(0f, 15))
        assertEquals(15, volumeSteps(1f, 15))
        assertEquals(0, volumeSteps(0.5f, 0))
        assertEquals(0, volumeSteps(0.5f, -5))
    }

    @Test
    fun PG9_levelPercent_roundsAndClamps() {
        assertEquals(46, levelPercent(0.456f))
        assertEquals(0, levelPercent(-0.1f))
        assertEquals(100, levelPercent(1.2f))
    }

    @Test
    fun PG10_seekChipLabel_formatsSignedSeconds() {
        assertEquals("+30 s", seekChipLabel(30_000L))
        assertEquals("-20 s", seekChipLabel(-20_000L))
        assertEquals("+10 s", seekChipLabel(10_000L))
    }
}
