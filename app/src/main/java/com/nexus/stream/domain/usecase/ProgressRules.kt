package com.nexus.stream.domain.usecase

import com.nexus.stream.domain.model.ProgressSample
import com.nexus.stream.domain.model.WatchProgress

object ProgressRules {
    const val SAVE_INTERVAL_MS = 5_000L
    const val MIN_SAVE_POSITION_MS = 5_000L
    const val WATCHED_PERCENT = 90
    const val MIN_RESUME_MS = 10_000L
    const val RESUME_END_GUARD_MS = 10_000L
    private const val PERCENT_SCALE = 100L

    fun progressKey(
        metaId: String,
        videoId: String,
    ): String = "$metaId|$videoId"

    fun shouldSave(sample: ProgressSample): Boolean =
        sample.durationMs > 0 && (sample.ended || sample.positionMs >= MIN_SAVE_POSITION_MS)

    fun isWatched(
        positionMs: Long,
        durationMs: Long,
    ): Boolean = durationMs > 0 && positionMs * PERCENT_SCALE >= durationMs * WATCHED_PERCENT

    fun resumePositionMs(
        saved: WatchProgress?,
        currentDurationMs: Long,
    ): Long? =
        when {
            saved == null || saved.watched -> null
            saved.positionMs < MIN_RESUME_MS -> null
            currentDurationMs > 0 && saved.positionMs >= currentDurationMs - RESUME_END_GUARD_MS -> null
            else -> saved.positionMs
        }
}
