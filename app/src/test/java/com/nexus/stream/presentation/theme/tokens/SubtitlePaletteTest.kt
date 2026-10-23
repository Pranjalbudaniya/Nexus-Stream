package com.nexus.stream.presentation.theme.tokens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class SubtitlePaletteTest {
    @Test
    fun SP1_fiveUniqueIds_fallbackToWhite() {
        assertEquals(5, SubtitlePalette.colors.size)
        val ids = SubtitlePalette.colors.map { it.id }
        assertEquals(5, ids.distinct().size)
        assertEquals(SubtitlePalette.rgbFor("white"), SubtitlePalette.rgbFor("nope"))
    }

    @Test
    fun SP2_everyColorHasAtLeast7To1ContrastAgainstBlack() {
        for (color in SubtitlePalette.colors) {
            val r = ((color.rgb shr 16) and 0xFF) / 255.0
            val g = ((color.rgb shr 8) and 0xFF) / 255.0
            val b = (color.rgb and 0xFF) / 255.0

            fun toLinear(c: Double): Double = if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)

            val rLin = toLinear(r)
            val gLin = toLinear(g)
            val bLin = toLinear(b)

            val lColor = 0.2126 * rLin + 0.7152 * gLin + 0.0722 * bLin
            val lBlack = 0.0
            val contrast = (lColor + 0.05) / (lBlack + 0.05)

            assertTrue(
                "Color ${color.id} contrast $contrast must be >= 7.0",
                contrast >= 7.0,
            )
        }
    }
}
