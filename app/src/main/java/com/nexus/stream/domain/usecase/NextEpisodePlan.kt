package com.nexus.stream.domain.usecase

import kotlin.math.max

const val NEXT_MIN_DURATION_MS = 300_000L
const val NEXT_PREFETCH_REMAINING_MS = 90_000L
const val NEXT_OFFER_REMAINING_MS = 30_000L
const val NEXT_REARM_REMAINING_MS = 60_000L
const val AUTOPLAY_COUNTDOWN_MS = 5_000L

enum class PrefetchState {
    IDLE,
    SEARCHING,
    FOUND,
    NOT_FOUND,
}

sealed interface NextUi {
    data object None : NextUi

    data object Searching : NextUi

    data object Ready : NextUi

    data object ChooseSource : NextUi
}

data class NextPlanInput(
    val hasNext: Boolean,
    val durationMs: Long,
    val positionMs: Long,
    val ended: Boolean,
    val dismissed: Boolean,
    val prefetch: PrefetchState,
)

fun remainingMs(
    durationMs: Long,
    positionMs: Long,
): Long = max(0L, durationMs - positionMs)

fun shouldPrefetch(i: NextPlanInput): Boolean =
    i.hasNext &&
        i.durationMs >= NEXT_MIN_DURATION_MS &&
        remainingMs(i.durationMs, i.positionMs) <= NEXT_PREFETCH_REMAINING_MS &&
        i.prefetch == PrefetchState.IDLE

fun nextUi(i: NextPlanInput): NextUi {
    val notEligible =
        !i.hasNext ||
            i.durationMs < NEXT_MIN_DURATION_MS ||
            i.durationMs <= 0L ||
            i.dismissed ||
            (remainingMs(i.durationMs, i.positionMs) > NEXT_OFFER_REMAINING_MS && !i.ended)
    if (notEligible) return NextUi.None
    return when (i.prefetch) {
        PrefetchState.FOUND -> NextUi.Ready

        PrefetchState.IDLE,
        PrefetchState.SEARCHING,
        -> NextUi.Searching

        PrefetchState.NOT_FOUND -> NextUi.ChooseSource
    }
}

fun shouldRearm(
    dismissed: Boolean,
    remainingMs: Long,
): Boolean = dismissed && remainingMs > NEXT_REARM_REMAINING_MS
