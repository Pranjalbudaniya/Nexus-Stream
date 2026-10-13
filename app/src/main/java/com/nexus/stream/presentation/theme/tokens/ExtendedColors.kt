package com.nexus.stream.presentation.theme.tokens

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class ExtendedColors(
    val ratingStar: Color,
    val success: Color,
    val onSuccess: Color,
    val warning: Color,
    val onWarning: Color,
)

val LightExtendedColors =
    ExtendedColors(
        ratingStar = Color(0xFFC27C00),
        success = Color(0xFF1B7F3B),
        onSuccess = Color(0xFFFFFFFF),
        warning = Color(0xFFB25E00),
        onWarning = Color(0xFFFFFFFF),
    )

val DarkExtendedColors =
    ExtendedColors(
        ratingStar = Color(0xFFFFC857),
        success = Color(0xFF7BD88F),
        onSuccess = Color(0xFF003915),
        warning = Color(0xFFFFB86B),
        onWarning = Color(0xFF4A2800),
    )

val LocalExtendedColors = staticCompositionLocalOf { LightExtendedColors }
