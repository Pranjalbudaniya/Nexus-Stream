package com.nexus.stream.presentation.common

import androidx.compose.ui.graphics.Color
import java.util.Locale
import kotlin.math.roundToInt

fun parseHexColor(input: String): Color? {
    val trimmed = input.trim()
    val clean = if (trimmed.startsWith('#')) trimmed.substring(1) else trimmed

    val hex =
        when (clean.length) {
            3 -> {
                val r = clean[0]
                val g = clean[1]
                val b = clean[2]
                "$r$r$g$g$b$b"
            }

            6 -> {
                clean
            }

            8 -> {
                clean.substring(2)
            }

            else -> {
                null
            }
        }

    if (hex == null || !isValidHex(hex)) {
        return null
    }

    val r = hex.substring(0, 2).toInt(16)
    val g = hex.substring(2, 4).toInt(16)
    val b = hex.substring(4, 6).toInt(16)
    return Color(red = r, green = g, blue = b, alpha = 255)
}

private fun isValidHex(hex: String): Boolean = hex.all { it.isHexDigit() }

private fun Char.isHexDigit(): Boolean = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

fun Color.toHexString(): String {
    val r = (red * 255f).roundToInt().coerceIn(0, 255)
    val g = (green * 255f).roundToInt().coerceIn(0, 255)
    val b = (blue * 255f).roundToInt().coerceIn(0, 255)
    return "#" + String.format(Locale.ROOT, "%02X%02X%02X", r, g, b)
}
