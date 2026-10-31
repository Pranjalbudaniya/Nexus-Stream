package com.nexus.stream.presentation.feature.settings

import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.graphics.toArgb
import com.nexus.stream.domain.model.AppFont
import com.nexus.stream.domain.model.ThemeMode
import com.nexus.stream.domain.model.ThemeSettings
import com.nexus.stream.domain.usecase.LanguageCodes
import com.nexus.stream.presentation.theme.tokens.AccentSwatches
import com.nexus.stream.presentation.theme.tokens.BrandAccent

enum class SettingsSection {
    APPEARANCE,
    CONTENT,
    HOME_ROWS,
    PLAYER,
    SUBTITLES,
    TORRENT,
    DOWNLOADS,
    ABOUT,
}

data class SettingsSectionState(
    val section: SettingsSection,
    val isExpanded: Boolean,
)

private val MODE_NAMES: Map<ThemeMode, String> =
    mapOf(
        ThemeMode.SYSTEM to "System",
        ThemeMode.LIGHT to "Light",
        ThemeMode.DARK to "Dark",
    )

private val FONT_NAMES: Map<AppFont, String> =
    mapOf(
        AppFont.SYSTEM to "System",
        AppFont.INTER to "Inter",
        AppFont.MANROPE to "Manrope",
        AppFont.DM_SANS to "DM Sans",
        AppFont.OUTFIT to "Outfit",
        AppFont.PLUS_JAKARTA_SANS to "Plus Jakarta Sans",
        AppFont.LEXEND to "Lexend",
        AppFont.NUNITO to "Nunito",
        AppFont.SPACE_GROTESK to "Space Grotesk",
        AppFont.POPPINS to "Poppins",
        AppFont.FIGTREE to "Figtree",
    )

fun Set<SettingsSection>.toggle(section: SettingsSection): Set<SettingsSection> =
    if (contains(section)) this - section else this + section

fun initialSettingsSections(): Set<SettingsSection> = emptySet()

val SettingsSectionSaver: Saver<Set<SettingsSection>, List<String>> =
    Saver(
        save = { sections -> sections.map { it.name } },
        restore = { list ->
            list
                .mapNotNull { name ->
                    runCatching { SettingsSection.valueOf(name) }.getOrNull()
                }.toSet()
        },
    )

fun appearanceSummary(settings: ThemeSettings): String {
    val modeStr = MODE_NAMES[settings.mode] ?: "System"
    val accentStr =
        if (settings.dynamicColor) {
            "Dynamic color"
        } else {
            val currentArgb =
                settings.accentColor
                    ?: ((255L shl 24) or (BrandAccent.toArgb().toLong() and 0x00FFFFFFL))
            val swatch =
                AccentSwatches.find {
                    (it.color.toArgb().toLong() and 0x00FFFFFFL) == (currentArgb and 0x00FFFFFFL)
                }
            swatch?.id?.replaceFirstChar { it.uppercase() } ?: "Custom"
        }
    val fontStr = FONT_NAMES[settings.font] ?: "Inter"
    return "$modeStr · $accentStr · $fontStr"
}

fun contentSummary(familyMode: Boolean): String = if (familyMode) "Family mode on" else "Family mode off"

fun playerSummary(
    preferredQuality: Int?,
    skipLengthSeconds: Int,
): String {
    val qualityStr = if (preferredQuality != null) "${preferredQuality}p" else "Auto"
    return "$qualityStr · Skip $skipLengthSeconds s"
}

fun subtitlesSummary(
    preferredAudio: String?,
    preferredSubtitle: String?,
): String {
    val audioStr =
        if (preferredAudio.isNullOrBlank()) {
            "Not set"
        } else {
            LanguageCodes.languageDisplayName(preferredAudio)
        }
    val subStr =
        if (preferredSubtitle.isNullOrBlank()) {
            "Not set"
        } else {
            LanguageCodes.languageDisplayName(preferredSubtitle)
        }
    return "Audio: $audioStr · Subtitles: $subStr"
}

fun torrentSummary(
    wifiOnly: Boolean,
    cacheLimitGb: Int,
): String {
    val netStr = if (wifiOnly) "Wi-Fi only" else "Any network"
    return "$netStr · $cacheLimitGb GB cache"
}

fun downloadsSummary(
    wifiOnly: Boolean,
    maxConcurrent: Int,
): String {
    val netStr = if (wifiOnly) "Wi-Fi only" else "Any network"
    return "$netStr · $maxConcurrent at a time"
}

fun aboutSummary(
    versionName: String?,
    versionCode: Long,
    updateAvailable: Boolean = false,
    updateSuffix: String = "Update available",
): String {
    val base =
        if (!versionName.isNullOrBlank()) {
            "Version $versionName"
        } else {
            "$versionCode"
        }
    return if (updateAvailable) "$base · $updateSuffix" else base
}

fun sectionSummary(
    expanded: Boolean,
    summary: String,
): String? = if (expanded) null else summary

fun homeRowsSummary(
    totalCount: Int,
    hiddenCount: Int,
): String =
    if (totalCount == 0) {
        "No rows yet"
    } else if (hiddenCount > 0) {
        "$totalCount rows · $hiddenCount hidden"
    } else {
        "$totalCount rows"
    }
