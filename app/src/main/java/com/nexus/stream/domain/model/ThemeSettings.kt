@file:Suppress("MagicNumber")

package com.nexus.stream.domain.model

const val MAX_RECENT_ACCENTS = 5

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class AppFont {
    SYSTEM,
    INTER,
    MANROPE,
    DM_SANS,
    OUTFIT,
    PLUS_JAKARTA_SANS,
    LEXEND,
    NUNITO,
    SPACE_GROTESK,
    POPPINS,
    FIGTREE,
}

enum class UiSize(
    val scale: Float,
) {
    SMALL(0.9f),
    NORMAL(1f),
    LARGE(1.1f),
    LARGER(1.2f),
    LARGEST(1.3f),
}

enum class TextSize(
    val scale: Float,
) {
    SMALL(0.9f),
    NORMAL(1f),
    LARGE(1.15f),
    LARGER(1.3f),
    LARGEST(1.5f),
}

enum class Roundness(
    val scale: Float,
) {
    SHARP(0f),
    SOFT(0.5f),
    NORMAL(1f),
    ROUND(1.5f),
    EXTRA_ROUND(2f),
}

enum class CardStyle {
    FILLED,
    OUTLINED,
    ELEVATED,
}

enum class ContrastLevel {
    STANDARD,
    MEDIUM,
    HIGH,
}

data class ThemeSettings(
    val mode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val amoled: Boolean = false,
    val accentColor: Long? = null,
    val recentAccents: List<Long> = emptyList(),
    val font: AppFont = AppFont.INTER,
    val uiSize: UiSize = UiSize.NORMAL,
    val textSize: TextSize = TextSize.NORMAL,
    val roundness: Roundness = Roundness.NORMAL,
    val cardStyle: CardStyle = CardStyle.FILLED,
    val contrast: ContrastLevel = ContrastLevel.STANDARD,
)
