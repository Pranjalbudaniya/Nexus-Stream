package com.nexus.stream.presentation.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.materialkolor.hct.Hct

const val MIN_SEED_TONE = 30.0
const val MAX_SEED_TONE = 80.0
const val LOW_CHROMA_THRESHOLD = 8.0

fun normalizeSeed(color: Color): Color {
    val opaqueColor = color.copy(alpha = 1f)
    val hct = Hct.fromInt(opaqueColor.toArgb())
    val clampedTone = hct.tone.coerceIn(MIN_SEED_TONE, MAX_SEED_TONE)
    val normalizedHct = hct.withTone(clampedTone)
    return Color(normalizedHct.toInt()).copy(alpha = 1f)
}

fun wasAdjusted(
    original: Color,
    normalized: Color,
): Boolean {
    val origHct = Hct.fromInt(original.toArgb())
    val normHct = Hct.fromInt(normalized.toArgb())
    return kotlin.math.abs(origHct.tone - normHct.tone) > 1.0
}

fun isLowChroma(color: Color): Boolean {
    val hct = Hct.fromInt(color.toArgb())
    return hct.chroma < LOW_CHROMA_THRESHOLD
}
