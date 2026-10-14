package com.nexus.stream.presentation.theme.tokens

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily

fun nexusTypography(family: FontFamily = FontFamily.Default): Typography {
    val default = Typography()

    fun TextStyle.withFamily(): TextStyle = copy(fontFamily = family)
    return Typography(
        displayLarge = default.displayLarge.withFamily(),
        displayMedium = default.displayMedium.withFamily(),
        displaySmall = default.displaySmall.withFamily(),
        headlineLarge = default.headlineLarge.withFamily(),
        headlineMedium = default.headlineMedium.withFamily(),
        headlineSmall = default.headlineSmall.withFamily(),
        titleLarge = default.titleLarge.withFamily(),
        titleMedium = default.titleMedium.withFamily(),
        titleSmall = default.titleSmall.withFamily(),
        bodyLarge = default.bodyLarge.withFamily(),
        bodyMedium = default.bodyMedium.withFamily(),
        bodySmall = default.bodySmall.withFamily(),
        labelLarge = default.labelLarge.withFamily(),
        labelMedium = default.labelMedium.withFamily(),
        labelSmall = default.labelSmall.withFamily(),
    )
}
