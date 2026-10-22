@file:Suppress("MatchingDeclarationName")

package com.nexus.stream.presentation.feature.player

import kotlin.math.round

enum class GestureZone { LEFT, RIGHT }

private const val TOP_DEADZONE_RATIO = 0.12f
private const val BOTTOM_DEADZONE_RATIO = 0.18f
private const val PERCENT_SCALE = 100f
private const val DEFAULT_SEEK_STEP_MS = 10_000L
private const val DEFAULT_SEEK_WINDOW_MS = 800L
private const val MS_PER_SEC = 1000L

fun zoneOf(
    x: Float,
    width: Float,
): GestureZone = if (x < width / 2f) GestureZone.LEFT else GestureZone.RIGHT

fun dragStartAllowed(
    y: Float,
    height: Float,
): Boolean {
    if (height <= 0f) return false
    val topBound = height * TOP_DEADZONE_RATIO
    val bottomBound = height * (1f - BOTTOM_DEADZONE_RATIO)
    return y in topBound..bottomBound
}

fun seekTarget(
    positionMs: Long,
    durationMs: Long,
    deltaMs: Long,
): Long {
    val target = positionMs + deltaMs
    val clampedMin = maxOf(0L, target)
    return if (durationMs > 0L) {
        minOf(durationMs, clampedMin)
    } else {
        clampedMin
    }
}

@Suppress("LongParameterList")
fun accumulatedSeek(
    prevDeltaMs: Long,
    prevTapMs: Long,
    nowMs: Long,
    direction: Int,
    stepMs: Long = DEFAULT_SEEK_STEP_MS,
    windowMs: Long = DEFAULT_SEEK_WINDOW_MS,
): Long {
    val sameDirection = (prevDeltaMs > 0L && direction > 0) || (prevDeltaMs < 0L && direction < 0)
    val withinWindow = (nowMs - prevTapMs) in 0..windowMs
    return if (sameDirection && withinWindow) {
        prevDeltaMs + (stepMs * direction)
    } else {
        stepMs * direction
    }
}

fun adjustLevel(
    startLevel: Float,
    dragDeltaPx: Float,
    heightPx: Float,
    minLevel: Float = 0f,
): Float {
    if (heightPx <= 0f) return startLevel
    val deltaLevel = -dragDeltaPx / heightPx
    return (startLevel + deltaLevel).coerceIn(minLevel, 1f)
}

fun volumeSteps(
    level: Float,
    maxSteps: Int,
): Int {
    if (maxSteps <= 0) return 0
    return round(level.coerceIn(0f, 1f) * maxSteps).toInt().coerceIn(0, maxSteps)
}

fun levelPercent(level: Float): Int = round(level.coerceIn(0f, 1f) * PERCENT_SCALE).toInt()

fun seekChipLabel(deltaMs: Long): String {
    val seconds = deltaMs / MS_PER_SEC
    return if (seconds > 0) {
        "+$seconds s"
    } else {
        "$seconds s"
    }
}
