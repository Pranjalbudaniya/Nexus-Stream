package com.nexus.stream.presentation.theme.tokens

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.nexus.stream.R

val BrandAccent = Color(0xFF6D5EF5)

data class AccentSwatch(
    val id: String,
    @StringRes val nameRes: Int,
    val color: Color,
)

val AccentSwatches =
    listOf(
        AccentSwatch("violet", R.string.accent_violet, BrandAccent),
        AccentSwatch("indigo", R.string.accent_indigo, Color(0xFF3F51B5)),
        AccentSwatch("blue", R.string.accent_blue, Color(0xFF2979C9)),
        AccentSwatch("cyan", R.string.accent_cyan, Color(0xFF00ACC1)),
        AccentSwatch("teal", R.string.accent_teal, Color(0xFF00897B)),
        AccentSwatch("green", R.string.accent_green, Color(0xFF2E8B57)),
        AccentSwatch("lime", R.string.accent_lime, Color(0xFF8BC34A)),
        AccentSwatch("amber", R.string.accent_amber, Color(0xFFFFB300)),
        AccentSwatch("orange", R.string.accent_orange, Color(0xFFF57C00)),
        AccentSwatch("red", R.string.accent_red, Color(0xFFD32F4A)),
        AccentSwatch("pink", R.string.accent_pink, Color(0xFFE91E8C)),
        AccentSwatch("neutral", R.string.accent_neutral, Color(0xFF808080)),
    )
