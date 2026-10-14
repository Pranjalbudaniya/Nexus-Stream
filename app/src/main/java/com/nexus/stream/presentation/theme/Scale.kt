package com.nexus.stream.presentation.theme

import androidx.compose.ui.unit.Density
import com.nexus.stream.domain.model.TextSize
import com.nexus.stream.domain.model.UiSize

private const val MIN_FONT_SCALE = 0.8f
private const val MAX_FONT_SCALE = 2.0f

fun scaledDensity(
    base: Density,
    ui: UiSize,
    text: TextSize,
): Density {
    val newDensity = base.density * ui.scale
    val newFontScale = (base.fontScale * text.scale).coerceIn(MIN_FONT_SCALE, MAX_FONT_SCALE)
    return Density(density = newDensity, fontScale = newFontScale)
}
