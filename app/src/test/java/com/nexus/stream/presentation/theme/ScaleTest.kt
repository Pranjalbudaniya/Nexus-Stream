package com.nexus.stream.presentation.theme

import androidx.compose.ui.unit.Density
import com.nexus.stream.domain.model.TextSize
import com.nexus.stream.domain.model.UiSize
import org.junit.Assert.assertEquals
import org.junit.Test

class ScaleTest {
    @Test
    fun scaledDensity_normalNormal_returnsBaseValues() {
        val base = Density(density = 2.5f, fontScale = 1.0f)
        val scaled = scaledDensity(base, UiSize.NORMAL, TextSize.NORMAL)

        assertEquals(2.5f, scaled.density, 0.001f)
        assertEquals(1.0f, scaled.fontScale, 0.001f)
    }

    @Test
    fun scaledDensity_multipliesDensityByUiScaleAndFontScaleByTextScale() {
        val base = Density(density = 2.0f, fontScale = 1.0f)
        val scaled = scaledDensity(base, UiSize.LARGE, TextSize.LARGE)

        assertEquals(2.0f * UiSize.LARGE.scale, scaled.density, 0.001f)
        assertEquals(1.0f * TextSize.LARGE.scale, scaled.fontScale, 0.001f)
    }

    @Test
    fun scaledDensity_fontScaleClampedToPointEightAndTwo() {
        val baseHigh = Density(density = 1.0f, fontScale = 1.8f)
        val scaledHigh = scaledDensity(baseHigh, UiSize.NORMAL, TextSize.LARGEST)
        assertEquals(2.0f, scaledHigh.fontScale, 0.001f)

        val baseLow = Density(density = 1.0f, fontScale = 0.5f)
        val scaledLow = scaledDensity(baseLow, UiSize.NORMAL, TextSize.SMALL)
        assertEquals(0.8f, scaledLow.fontScale, 0.001f)
    }
}
