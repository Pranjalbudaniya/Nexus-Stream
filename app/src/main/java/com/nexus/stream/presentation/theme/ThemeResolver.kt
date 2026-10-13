package com.nexus.stream.presentation.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import com.nexus.stream.domain.model.ThemeMode
import com.nexus.stream.domain.model.ThemeSettings
import com.nexus.stream.presentation.theme.tokens.AmoledTokens
import com.nexus.stream.presentation.theme.tokens.BrandAccent

fun isDarkTheme(
    mode: ThemeMode,
    systemDark: Boolean,
): Boolean =
    when (mode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

fun accentSeed(accent: Long?): Color {
    if (accent == null) return BrandAccent
    val alphaOpaque = (255L shl 24) or (accent and 0x00FFFFFFL)
    return Color(alphaOpaque.toInt()).copy(alpha = 1f)
}

fun schemeFromAccent(
    seed: Color,
    dark: Boolean,
): ColorScheme =
    dynamicColorScheme(
        seedColor = normalizeSeed(seed),
        isDark = dark,
        isAmoled = false,
        style = PaletteStyle.Fidelity,
    )

fun applyAmoled(base: ColorScheme): ColorScheme =
    base.copy(
        background = AmoledTokens.background,
        surface = AmoledTokens.surface,
        surfaceDim = AmoledTokens.surfaceDim,
        surfaceBright = AmoledTokens.surfaceBright,
        surfaceVariant = AmoledTokens.surfaceVariant,
        surfaceContainerLowest = AmoledTokens.surfaceContainerLowest,
        surfaceContainerLow = AmoledTokens.surfaceContainerLow,
        surfaceContainer = AmoledTokens.surfaceContainer,
        surfaceContainerHigh = AmoledTokens.surfaceContainerHigh,
        surfaceContainerHighest = AmoledTokens.surfaceContainerHighest,
    )

@Composable
fun resolveColorScheme(
    settings: ThemeSettings,
    dark: Boolean,
): ColorScheme {
    val context = LocalContext.current
    return remember(settings.accentColor, dark, settings.dynamicColor, settings.amoled) {
        val base =
            if (settings.dynamicColor) {
                if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else {
                schemeFromAccent(accentSeed(settings.accentColor), dark)
            }
        if (dark && settings.amoled) applyAmoled(base) else base
    }
}
