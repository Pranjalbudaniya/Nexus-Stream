package com.nexus.stream.presentation.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.materialkolor.hct.Hct
import com.nexus.stream.presentation.theme.tokens.AccentSwatches
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeedColorTest {
    @Test
    fun normalizeSeed_clampsToneAndMaintainsOpaqueAlpha() {
        val normBlack = normalizeSeed(Color.Black)
        val blackTone = Hct.fromInt(normBlack.toArgb()).tone
        assertTrue("Normalized black tone should be >= 30, was $blackTone", blackTone >= 30.0 - 0.1)
        assertTrue("Normalized black tone should be <= 80, was $blackTone", blackTone <= 80.0 + 0.1)
        assertEquals(1f, normBlack.alpha, 0.001f)

        val normWhite = normalizeSeed(Color.White)
        val whiteTone = Hct.fromInt(normWhite.toArgb()).tone
        assertTrue("Normalized white tone should be >= 30, was $whiteTone", whiteTone >= 30.0 - 0.1)
        assertTrue("Normalized white tone should be <= 80, was $whiteTone", whiteTone <= 80.0 + 0.1)
        assertEquals(1f, normWhite.alpha, 0.001f)

        val blueSwatch = AccentSwatches.first { it.id == "blue" }.color
        val normBlue = normalizeSeed(blueSwatch)
        val origBlueTone = Hct.fromInt(blueSwatch.toArgb()).tone
        val normBlueTone = Hct.fromInt(normBlue.toArgb()).tone
        assertEquals(origBlueTone, normBlueTone, 1.0)
        assertEquals(1f, normBlue.alpha, 0.001f)
    }

    @Test
    fun wasAdjusted_trueForBlackAndWhite_falseForSwatches() {
        val normBlack = normalizeSeed(Color.Black)
        assertTrue(wasAdjusted(Color.Black, normBlack))

        val normWhite = normalizeSeed(Color.White)
        assertTrue(wasAdjusted(Color.White, normWhite))

        AccentSwatches.forEach { swatch ->
            val norm = normalizeSeed(swatch.color)
            assertFalse("Swatch ${swatch.id} should not be adjusted", wasAdjusted(swatch.color, norm))
        }
    }

    @Test
    fun isLowChroma_trueForNeutrals_falseForRed() {
        assertTrue(isLowChroma(Color.Gray))
        assertTrue(isLowChroma(Color.Black))
        assertTrue(isLowChroma(Color.White))
        assertFalse(isLowChroma(Color.Red))
    }
}
