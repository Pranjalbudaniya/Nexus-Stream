package com.nexus.stream.data.player

import com.nexus.stream.domain.model.SubtitleBackground
import com.nexus.stream.domain.model.SubtitleEdge
import com.nexus.stream.domain.model.SubtitleSize
import com.nexus.stream.domain.model.SubtitleStyle
import com.nexus.stream.presentation.theme.tokens.SubtitlePalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleOptionsTest {
    @Test
    fun SO1_defaultStyle_givesExactEightLinesInOrder() {
        val style = SubtitleStyle().toRenderStyle(SubtitlePalette.rgbFor("white"))
        val options = buildSubtitleOptions(style)
        val expected =
            listOf(
                "--freetype-rel-fontsize=16",
                "--freetype-color=16777215",
                "--freetype-opacity=255",
                "--freetype-background-opacity=0",
                "--freetype-shadow-opacity=0",
                "--freetype-outline-thickness=4",
                "--freetype-outline-color=0",
                "--freetype-outline-opacity=255",
            )
        assertEquals(expected, options)
    }

    @Test
    fun SO2_bold_insertsOptionRightAfterSize() {
        val style = SubtitleStyle(bold = true).toRenderStyle(SubtitlePalette.rgbFor("white"))
        val options = buildSubtitleOptions(style)
        assertEquals("--freetype-rel-fontsize=16", options[0])
        assertEquals("--freetype-bold", options[1])
        assertEquals("--freetype-color=16777215", options[2])
    }

    @Test
    fun SO3_background_dimAndSolid() {
        val dimStyle = SubtitleStyle(background = SubtitleBackground.DIM).toRenderStyle(SubtitlePalette.rgbFor("white"))
        val dimOptions = buildSubtitleOptions(dimStyle)
        assertTrue(dimOptions.contains("--freetype-background-color=0"))
        assertTrue(dimOptions.contains("--freetype-background-opacity=128"))
        val dimColorIdx = dimOptions.indexOf("--freetype-background-color=0")
        val dimOpacityIdx = dimOptions.indexOf("--freetype-background-opacity=128")
        assertEquals(dimColorIdx + 1, dimOpacityIdx)

        val solidStyle =
            SubtitleStyle(
                background = SubtitleBackground.SOLID,
            ).toRenderStyle(SubtitlePalette.rgbFor("white"))
        val solidOptions = buildSubtitleOptions(solidStyle)
        assertTrue(solidOptions.contains("--freetype-background-color=0"))
        assertTrue(solidOptions.contains("--freetype-background-opacity=255"))
    }

    @Test
    fun SO4_edge_shadowAndNone() {
        val shadowStyle = SubtitleStyle(edge = SubtitleEdge.SHADOW).toRenderStyle(SubtitlePalette.rgbFor("white"))
        val shadowOptions = buildSubtitleOptions(shadowStyle)
        assertTrue(shadowOptions.contains("--freetype-shadow-color=0"))
        assertTrue(shadowOptions.contains("--freetype-shadow-opacity=128"))
        assertTrue(shadowOptions.contains("--freetype-outline-opacity=0"))
        val shadowColorIdx = shadowOptions.indexOf("--freetype-shadow-color=0")
        val shadowOpacityIdx = shadowOptions.indexOf("--freetype-shadow-opacity=128")
        assertEquals(shadowColorIdx + 1, shadowOpacityIdx)

        val noneStyle = SubtitleStyle(edge = SubtitleEdge.NONE).toRenderStyle(SubtitlePalette.rgbFor("white"))
        val noneOptions = buildSubtitleOptions(noneStyle)
        assertTrue(noneOptions.contains("--freetype-shadow-opacity=0"))
        assertTrue(noneOptions.contains("--freetype-outline-opacity=0"))
    }

    @Test
    fun SO5_subtitleSize_mapsToRelFontSize() {
        val expected =
            mapOf(
                SubtitleSize.NANO to 40,
                SubtitleSize.MICRO to 32,
                SubtitleSize.SMALLEST to 25,
                SubtitleSize.SMALL to 19,
                SubtitleSize.NORMAL to 16,
                SubtitleSize.BIG to 13,
                SubtitleSize.HUGE to 10,
            )
        for ((size, expectedRel) in expected) {
            val style = SubtitleStyle(size = size).toRenderStyle(SubtitlePalette.rgbFor("white"))
            val options = buildSubtitleOptions(style)
            assertEquals("--freetype-rel-fontsize=$expectedRel", options[0])
        }
    }

    @Test
    fun SO6_yellow_givesRgbAsDecimalInteger() {
        val yellowRgb = SubtitlePalette.rgbFor("yellow")
        assertEquals(16776960, yellowRgb)
        val style = SubtitleStyle().toRenderStyle(yellowRgb)
        val options = buildSubtitleOptions(style)
        assertTrue(options.contains("--freetype-color=16776960"))
    }
}
