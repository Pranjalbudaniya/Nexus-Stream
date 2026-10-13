package com.nexus.stream.presentation.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import com.nexus.stream.domain.model.ThemeMode
import com.nexus.stream.presentation.theme.tokens.AccentSwatches
import com.nexus.stream.presentation.theme.tokens.AmoledTokens
import com.nexus.stream.presentation.theme.tokens.BrandAccent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeResolverTest {
    @Test
    fun isDarkTheme_resolvesExpectedBoolean() {
        assertTrue(isDarkTheme(ThemeMode.SYSTEM, systemDark = true))
        assertFalse(isDarkTheme(ThemeMode.SYSTEM, systemDark = false))
        assertFalse(isDarkTheme(ThemeMode.LIGHT, systemDark = true))
        assertTrue(isDarkTheme(ThemeMode.DARK, systemDark = false))
    }

    @Test
    fun accentSeed_resolvesBrandAccentForNullAndForcesOpaqueAlpha() {
        assertEquals(BrandAccent, accentSeed(null))

        val transparentViolet = BrandAccent.toArgb().toLong() and 0x00FFFFFFL
        val resolved = accentSeed(transparentViolet)
        assertEquals(1f, resolved.alpha, 0.001f)
        assertEquals(BrandAccent.red, resolved.red, 0.01f)
        assertEquals(BrandAccent.green, resolved.green, 0.01f)
        assertEquals(BrandAccent.blue, resolved.blue, 0.01f)
    }

    @Test
    fun schemeFromAccent_satisfiesWcagContrastForSwatchesAndExtremeSeeds() {
        val testSeeds =
            AccentSwatches.map { it.color } +
                listOf(
                    Color.Black,
                    Color.White,
                    Color.Gray,
                    Color.Red,
                    Color.Green,
                    Color.Blue,
                    Color.Yellow,
                    Color.hsv(60f, 1f, 1f),
                    Color.hsv(240f, 0.05f, 0.5f),
                )

        listOf(false, true).forEach { isDark ->
            testSeeds.forEach { seed ->
                val scheme = schemeFromAccent(seed, isDark)

                assertContrast(scheme.onPrimary, scheme.primary, "onPrimary/primary", isDark, seed)
                assertContrast(
                    scheme.onPrimaryContainer,
                    scheme.primaryContainer,
                    "onPrimaryContainer/primaryContainer",
                    isDark,
                    seed,
                )
                assertContrast(scheme.onSecondary, scheme.secondary, "onSecondary/secondary", isDark, seed)
                assertContrast(scheme.onTertiary, scheme.tertiary, "onTertiary/tertiary", isDark, seed)
                assertContrast(scheme.onSurface, scheme.surface, "onSurface/surface", isDark, seed)
                assertContrast(scheme.onError, scheme.error, "onError/error", isDark, seed)
            }
        }
    }

    @Test
    fun applyAmoled_overridesTenRolesAndLeavesOthersUnchanged() {
        val base = schemeFromAccent(BrandAccent, dark = true)
        val amoled = applyAmoled(base)

        // 10 roles match AmoledTokens
        assertEquals(AmoledTokens.background, amoled.background)
        assertEquals(AmoledTokens.surface, amoled.surface)
        assertEquals(AmoledTokens.surfaceDim, amoled.surfaceDim)
        assertEquals(AmoledTokens.surfaceBright, amoled.surfaceBright)
        assertEquals(AmoledTokens.surfaceVariant, amoled.surfaceVariant)
        assertEquals(AmoledTokens.surfaceContainerLowest, amoled.surfaceContainerLowest)
        assertEquals(AmoledTokens.surfaceContainerLow, amoled.surfaceContainerLow)
        assertEquals(AmoledTokens.surfaceContainer, amoled.surfaceContainer)
        assertEquals(AmoledTokens.surfaceContainerHigh, amoled.surfaceContainerHigh)
        assertEquals(AmoledTokens.surfaceContainerHighest, amoled.surfaceContainerHighest)

        // Other roles are unchanged
        assertEquals(base.primary, amoled.primary)
        assertEquals(base.secondary, amoled.secondary)
        assertEquals(base.tertiary, amoled.tertiary)
        assertEquals(base.error, amoled.error)
        assertEquals(base.primaryContainer, amoled.primaryContainer)
        assertEquals(base.outline, amoled.outline)
        assertEquals(base.outlineVariant, amoled.outlineVariant)
        assertEquals(base.inverseSurface, amoled.inverseSurface)
        assertEquals(base.scrim, amoled.scrim)

        // Applying twice equals applying once
        assertColorSchemeRolesEqual(amoled, applyAmoled(amoled))
    }

    private fun assertColorSchemeRolesEqual(
        expected: ColorScheme,
        actual: ColorScheme,
    ) {
        assertEquals(expected.primary, actual.primary)
        assertEquals(expected.onPrimary, actual.onPrimary)
        assertEquals(expected.primaryContainer, actual.primaryContainer)
        assertEquals(expected.onPrimaryContainer, actual.onPrimaryContainer)
        assertEquals(expected.secondary, actual.secondary)
        assertEquals(expected.onSecondary, actual.onSecondary)
        assertEquals(expected.secondaryContainer, actual.secondaryContainer)
        assertEquals(expected.onSecondaryContainer, actual.onSecondaryContainer)
        assertEquals(expected.tertiary, actual.tertiary)
        assertEquals(expected.onTertiary, actual.onTertiary)
        assertEquals(expected.tertiaryContainer, actual.tertiaryContainer)
        assertEquals(expected.onTertiaryContainer, actual.onTertiaryContainer)
        assertEquals(expected.background, actual.background)
        assertEquals(expected.onBackground, actual.onBackground)
        assertEquals(expected.surface, actual.surface)
        assertEquals(expected.onSurface, actual.onSurface)
        assertEquals(expected.surfaceVariant, actual.surfaceVariant)
        assertEquals(expected.onSurfaceVariant, actual.onSurfaceVariant)
        assertEquals(expected.surfaceDim, actual.surfaceDim)
        assertEquals(expected.surfaceBright, actual.surfaceBright)
        assertEquals(expected.surfaceContainerLowest, actual.surfaceContainerLowest)
        assertEquals(expected.surfaceContainerLow, actual.surfaceContainerLow)
        assertEquals(expected.surfaceContainer, actual.surfaceContainer)
        assertEquals(expected.surfaceContainerHigh, actual.surfaceContainerHigh)
        assertEquals(expected.surfaceContainerHighest, actual.surfaceContainerHighest)
        assertEquals(expected.outline, actual.outline)
        assertEquals(expected.outlineVariant, actual.outlineVariant)
        assertEquals(expected.error, actual.error)
        assertEquals(expected.onError, actual.onError)
        assertEquals(expected.errorContainer, actual.errorContainer)
        assertEquals(expected.onErrorContainer, actual.onErrorContainer)
        assertEquals(expected.scrim, actual.scrim)
    }

    private fun assertContrast(
        fg: Color,
        bg: Color,
        pairName: String,
        isDark: Boolean,
        seed: Color,
    ) {
        val l1 = maxOf(fg.luminance(), bg.luminance())
        val l2 = minOf(fg.luminance(), bg.luminance())
        val ratio = (l1 + 0.05f) / (l2 + 0.05f)
        assertTrue(
            "Expected contrast >= 4.5 for $pairName with isDark=$isDark, seed=$seed, got $ratio",
            ratio >= 4.5f,
        )
    }
}
