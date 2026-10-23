package com.nexus.stream.presentation.theme.tokens

data class SubtitleColor(
    val id: String,
    val rgb: Int,
)

object SubtitlePalette {
    private const val WHITE_RGB = 0xFFFFFF
    private const val YELLOW_RGB = 0xFFFF00
    private const val CYAN_RGB = 0x00FFFF
    private const val GREEN_RGB = 0x00FF00
    private const val ORANGE_RGB = 0xFFA500

    val colors: List<SubtitleColor> =
        listOf(
            SubtitleColor("white", WHITE_RGB),
            SubtitleColor("yellow", YELLOW_RGB),
            SubtitleColor("cyan", CYAN_RGB),
            SubtitleColor("green", GREEN_RGB),
            SubtitleColor("orange", ORANGE_RGB),
        )

    fun rgbFor(id: String): Int = colors.firstOrNull { it.id.equals(id, ignoreCase = true) }?.rgb ?: WHITE_RGB

    fun colorFor(id: String): androidx.compose.ui.graphics.Color =
        androidx.compose.ui.graphics
            .Color(0xFF000000.toInt() or rgbFor(id))
}
