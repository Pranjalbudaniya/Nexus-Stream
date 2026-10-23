package com.nexus.stream.domain.usecase

import com.nexus.stream.domain.model.ProgressSample
import com.nexus.stream.domain.model.WatchProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressRulesTest {
    @Test
    fun PR1_durationZeroOrNegative_positionSixtySeconds_notEnded_shouldSaveFalse() {
        assertFalse(ProgressRules.shouldSave(ProgressSample(positionMs = 60_000L, durationMs = 0L, ended = false)))
        assertFalse(ProgressRules.shouldSave(ProgressSample(positionMs = 60_000L, durationMs = -100L, ended = false)))
    }

    @Test
    fun PR2_positionThreshold_durationHundredMinutes() {
        val duration = 100L * 60L * 1000L
        assertFalse(ProgressRules.shouldSave(ProgressSample(positionMs = 4_999L, durationMs = duration, ended = false)))
        assertTrue(ProgressRules.shouldSave(ProgressSample(positionMs = 5_000L, durationMs = duration, ended = false)))
    }

    @Test
    fun PR3_ended_durationHundredMinutes_positionZero_shouldSaveTrue() {
        val duration = 100L * 60L * 1000L
        assertTrue(ProgressRules.shouldSave(ProgressSample(positionMs = 0L, durationMs = duration, ended = true)))
    }

    @Test
    fun PR4_ended_durationZero_shouldSaveFalse() {
        assertFalse(ProgressRules.shouldSave(ProgressSample(positionMs = 0L, durationMs = 0L, ended = true)))
    }

    @Test
    fun PR5_isWatched_percentCalculations() {
        // 89.9%: 899 / 1000 -> false
        assertFalse(ProgressRules.isWatched(positionMs = 899L, durationMs = 1000L))
        // Exactly 90%: 900 / 1000 -> true
        assertTrue(ProgressRules.isWatched(positionMs = 900L, durationMs = 1000L))
        // Position above duration: 1100 / 1000 -> true
        assertTrue(ProgressRules.isWatched(positionMs = 1100L, durationMs = 1000L))
        // Duration 0 -> false
        assertFalse(ProgressRules.isWatched(positionMs = 100L, durationMs = 0L))
    }

    @Test
    fun PR6_resumePositionMs_variousSavedRows() {
        // null row -> null
        assertNull(ProgressRules.resumePositionMs(saved = null, currentDurationMs = 100_000L))

        // watched row -> null
        val watchedRow =
            WatchProgress(
                key = "m|v",
                metaId = "m",
                videoId = "v",
                type = "movie",
                positionMs = 50_000L,
                durationMs = 100_000L,
                watched = true,
                updatedAt = 1000L,
            )
        assertNull(ProgressRules.resumePositionMs(saved = watchedRow, currentDurationMs = 100_000L))

        // position 9,999 ms -> null
        val earlyRow = watchedRow.copy(positionMs = 9_999L, watched = false)
        assertNull(ProgressRules.resumePositionMs(saved = earlyRow, currentDurationMs = 100_000L))

        // position 10,000 ms with duration 0 -> 10,000
        val tenSecRow = watchedRow.copy(positionMs = 10_000L, watched = false)
        assertEquals(10_000L, ProgressRules.resumePositionMs(saved = tenSecRow, currentDurationMs = 0L))
    }

    @Test
    fun PR7_resumePositionMs_endGuardBoundary() {
        val duration = 3_600_000L
        val savedAtGuard =
            WatchProgress(
                key = "m|v",
                metaId = "m",
                videoId = "v",
                type = "movie",
                positionMs = 3_590_000L,
                durationMs = duration,
                watched = false,
                updatedAt = 1000L,
            )
        // saved 3,590,000, duration 3,600,000 -> null
        assertNull(ProgressRules.resumePositionMs(saved = savedAtGuard, currentDurationMs = duration))

        // saved 3,589,999, duration 3,600,000 -> 3,589,999
        val savedBeforeGuard = savedAtGuard.copy(positionMs = 3_589_999L)
        assertEquals(3_589_999L, ProgressRules.resumePositionMs(saved = savedBeforeGuard, currentDurationMs = duration))
    }
}
