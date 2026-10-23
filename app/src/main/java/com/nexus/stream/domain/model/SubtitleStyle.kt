package com.nexus.stream.domain.model

private const val REL_FONT_SIZE_NANO = 40
private const val REL_FONT_SIZE_MICRO = 32
private const val REL_FONT_SIZE_SMALLEST = 25
private const val REL_FONT_SIZE_SMALL = 19
private const val REL_FONT_SIZE_NORMAL = 16
private const val REL_FONT_SIZE_BIG = 13
private const val REL_FONT_SIZE_HUGE = 10

enum class SubtitleSize(
    val relFontSize: Int,
) {
    NANO(REL_FONT_SIZE_NANO),
    MICRO(REL_FONT_SIZE_MICRO),
    SMALLEST(REL_FONT_SIZE_SMALLEST),
    SMALL(REL_FONT_SIZE_SMALL),
    NORMAL(REL_FONT_SIZE_NORMAL),
    BIG(REL_FONT_SIZE_BIG),
    HUGE(REL_FONT_SIZE_HUGE),
}

enum class SubtitleBackground {
    NONE,
    DIM,
    SOLID,
}

enum class SubtitleEdge {
    NONE,
    OUTLINE,
    SHADOW,
}

data class SubtitleStyle(
    val size: SubtitleSize = SubtitleSize.NORMAL,
    val colorId: String = "white",
    val background: SubtitleBackground = SubtitleBackground.NONE,
    val edge: SubtitleEdge = SubtitleEdge.OUTLINE,
    val bold: Boolean = false,
)
