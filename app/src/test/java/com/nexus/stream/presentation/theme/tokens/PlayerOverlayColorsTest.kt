package com.nexus.stream.presentation.theme.tokens

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class PlayerOverlayColorsTest {
    private fun channelLuminance(channel: Float): Double =
        if (channel <= 0.04045f) {
            channel.toDouble() / 12.92
        } else {
            ((channel.toDouble() + 0.055) / 1.055).pow(2.4)
        }

    private fun relativeLuminance(color: Color): Double {
        val r = channelLuminance(color.red)
        val g = channelLuminance(color.green)
        val b = channelLuminance(color.blue)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private fun contrastRatio(
        l1: Double,
        l2: Double,
    ): Double {
        val lighter = maxOf(l1, l2)
        val darker = minOf(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun compositeOver(
        foreground: Color,
        background: Color,
    ): Color {
        val alpha = foreground.alpha
        val invAlpha = 1f - alpha
        return Color(
            red = foreground.red * alpha + background.red * invAlpha,
            green = foreground.green * alpha + background.green * invAlpha,
            blue = foreground.blue * alpha + background.blue * invAlpha,
            alpha = 1.0f,
        )
    }

    @Test
    fun PC1_content_onBlack_atLeast15to1() {
        val contentLum = relativeLuminance(PlayerOverlayColors.Content)
        val blackLum = relativeLuminance(Color.Black)
        val ratio = contrastRatio(contentLum, blackLum)
        assertTrue("Content on black ratio $ratio < 15.0", ratio >= 15.0)
    }

    @Test
    fun PC2_contentSecondary_onCardAndOnBlack_atLeast4_5to1() {
        val secLum = relativeLuminance(PlayerOverlayColors.ContentSecondary)
        val blackLum = relativeLuminance(Color.Black)
        val ratioBlack = contrastRatio(secLum, blackLum)
        assertTrue("ContentSecondary on black ratio $ratioBlack < 4.5", ratioBlack >= 4.5)

        val cardOpaque = compositeOver(PlayerOverlayColors.Card, Color.Black)
        val cardLum = relativeLuminance(cardOpaque)
        val ratioCard = contrastRatio(secLum, cardLum)
        assertTrue("ContentSecondary on Card ratio $ratioCard < 4.5", ratioCard >= 4.5)
    }

    @Test
    fun PC3_content_onCardCompositedOverWhite_atLeast4_5to1() {
        val cardOverWhite = compositeOver(PlayerOverlayColors.Card, Color.White)
        val cardLum = relativeLuminance(cardOverWhite)
        val contentLum = relativeLuminance(PlayerOverlayColors.Content)
        val ratio = contrastRatio(contentLum, cardLum)
        assertTrue("Content on Card over white ratio $ratio < 4.5", ratio >= 4.5)
    }

    @Test
    fun PC4_onContent_onContent_atLeast7to1() {
        val onContentLum = relativeLuminance(PlayerOverlayColors.OnContent)
        val contentLum = relativeLuminance(PlayerOverlayColors.Content)
        val ratio = contrastRatio(contentLum, onContentLum)
        assertTrue("OnContent on Content ratio $ratio < 7.0", ratio >= 7.0)
    }
}
