package com.nexus.stream.presentation.theme

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import com.nexus.stream.presentation.theme.tokens.nexusShapes
import org.junit.Assert.assertEquals
import org.junit.Test

class ShapesTest {
    private val density = Density(1f)
    private val size = Size(100f, 100f)

    private fun CornerBasedShape.radiusPx(): Float = topStart.toPx(size, density)

    @Test
    fun nexusShapes_atScaleOne_hasBaseRadii() {
        val shapes = nexusShapes(1f)
        assertEquals(12f, shapes.poster.radiusPx(), 0.001f)
        assertEquals(16f, shapes.card.radiusPx(), 0.001f)
        assertEquals(28f, shapes.sheet.radiusPx(), 0.001f)
        assertEquals(8f, shapes.chip.radiusPx(), 0.001f)
        assertEquals(50f, shapes.button.radiusPx(), 0.001f)
    }

    @Test
    fun nexusShapes_atScaleZero_radiiAreZero() {
        val shapes = nexusShapes(0f)
        assertEquals(0f, shapes.poster.radiusPx(), 0.001f)
        assertEquals(0f, shapes.card.radiusPx(), 0.001f)
        assertEquals(0f, shapes.sheet.radiusPx(), 0.001f)
        assertEquals(0f, shapes.chip.radiusPx(), 0.001f)
        assertEquals(0f, shapes.button.radiusPx(), 0.001f)
    }

    @Test
    fun nexusShapes_atScaleTwo_radiiDouble() {
        val shapes = nexusShapes(2f)
        assertEquals(24f, shapes.poster.radiusPx(), 0.001f)
        assertEquals(32f, shapes.card.radiusPx(), 0.001f)
        assertEquals(56f, shapes.sheet.radiusPx(), 0.001f)
        assertEquals(16f, shapes.chip.radiusPx(), 0.001f)
        assertEquals(50f, shapes.button.radiusPx(), 0.001f)
    }

    @Test
    fun buttonCorner_percentScalesAsExpected() {
        assertEquals(50f, nexusShapes(1f).button.radiusPx(), 0.001f)
        assertEquals(50f, nexusShapes(2f).button.radiusPx(), 0.001f)
        assertEquals(25f, nexusShapes(0.5f).button.radiusPx(), 0.001f)
        assertEquals(0f, nexusShapes(0f).button.radiusPx(), 0.001f)
    }
}
