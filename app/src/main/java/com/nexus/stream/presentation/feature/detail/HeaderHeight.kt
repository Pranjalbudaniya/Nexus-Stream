package com.nexus.stream.presentation.feature.detail

private const val HEIGHT_FRACTION = 0.40f
private const val MIN_HEADER_HEIGHT_DP = 220f
private const val MAX_HEADER_HEIGHT_DP = 480f

fun headerHeightDp(screenHeightDp: Float): Float =
    (screenHeightDp * HEIGHT_FRACTION).coerceIn(MIN_HEADER_HEIGHT_DP, MAX_HEADER_HEIGHT_DP)
