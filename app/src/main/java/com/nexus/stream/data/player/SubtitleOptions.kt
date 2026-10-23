package com.nexus.stream.data.player

import com.nexus.stream.domain.model.SubtitleBackground
import com.nexus.stream.domain.model.SubtitleEdge
import com.nexus.stream.domain.model.SubtitleStyle

private const val OPACITY_FULL = 255
private const val OPACITY_DIM = 128
private const val OPACITY_OFF = 0
private const val OUTLINE_THICKNESS_DEFAULT = 4
private const val BLACK_COLOR_DECIMAL = 0

data class SubtitleRenderStyle(
    val relFontSize: Int,
    val rgb: Int,
    val bold: Boolean,
    val backgroundOpacity: Int,
    val outlineOpacity: Int,
    val shadowOpacity: Int,
)

fun SubtitleStyle.toRenderStyle(rgb: Int): SubtitleRenderStyle {
    val bgOpacity =
        when (background) {
            SubtitleBackground.NONE -> OPACITY_OFF
            SubtitleBackground.DIM -> OPACITY_DIM
            SubtitleBackground.SOLID -> OPACITY_FULL
        }
    val (outlineOp, shadowOp) =
        when (edge) {
            SubtitleEdge.NONE -> OPACITY_OFF to OPACITY_OFF
            SubtitleEdge.OUTLINE -> OPACITY_FULL to OPACITY_OFF
            SubtitleEdge.SHADOW -> OPACITY_OFF to OPACITY_DIM
        }
    return SubtitleRenderStyle(
        relFontSize = size.relFontSize,
        rgb = rgb,
        bold = bold,
        backgroundOpacity = bgOpacity,
        outlineOpacity = outlineOp,
        shadowOpacity = shadowOp,
    )
}

fun buildSubtitleOptions(style: SubtitleRenderStyle): List<String> =
    buildList {
        add("--freetype-rel-fontsize=${style.relFontSize}")
        if (style.bold) {
            add("--freetype-bold")
        }
        add("--freetype-color=${style.rgb}")
        add("--freetype-opacity=$OPACITY_FULL")

        if (style.backgroundOpacity > OPACITY_OFF) {
            add("--freetype-background-color=$BLACK_COLOR_DECIMAL")
        }
        add("--freetype-background-opacity=${style.backgroundOpacity}")

        if (style.shadowOpacity > OPACITY_OFF) {
            add("--freetype-shadow-color=$BLACK_COLOR_DECIMAL")
        }
        add("--freetype-shadow-opacity=${style.shadowOpacity}")

        if (style.outlineOpacity > OPACITY_OFF) {
            add("--freetype-outline-thickness=$OUTLINE_THICKNESS_DEFAULT")
            add("--freetype-outline-color=$BLACK_COLOR_DECIMAL")
            add("--freetype-outline-opacity=${style.outlineOpacity}")
        } else {
            add("--freetype-outline-opacity=$OPACITY_OFF")
        }
    }
