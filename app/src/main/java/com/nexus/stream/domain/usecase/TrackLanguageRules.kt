package com.nexus.stream.domain.usecase

import com.nexus.stream.data.player.PlayerTrack
import java.util.Locale

object TrackLanguageRules

data class LanguageOption(
    val code: String,
    val name: String,
)

const val SUBTITLES_OFF = "off"

private const val ISO_639_2_CODE_LENGTH = 3

private val PREFERRED_LANGUAGE_CODES =
    listOf(
        "en",
        "es",
        "fr",
        "de",
        "it",
        "pt",
        "ru",
        "ja",
        "ko",
        "zh",
        "ar",
        "hi",
        "tr",
        "nl",
        "pl",
        "sv",
        "no",
        "da",
        "fi",
        "cs",
        "el",
        "he",
        "th",
        "id",
        "vi",
    )

val PREFERRED_LANGUAGE_OPTIONS: List<LanguageOption> =
    PREFERRED_LANGUAGE_CODES.map { code ->
        LanguageOption(code = code, name = languageDisplayName(code))
    }

fun matchTrackByLanguage(
    tracks: List<PlayerTrack>,
    code: String,
): PlayerTrack? {
    if (code.isBlank() || tracks.isEmpty()) return null
    val clean = code.trim().lowercase()
    val patterns = buildMatchPatterns(clean)
    return tracks.firstOrNull { track ->
        patterns.any { pattern -> pattern.containsMatchIn(track.name) }
    }
}

private fun buildMatchPatterns(clean: String): List<Regex> {
    val candidates = mutableSetOf<String>()

    val englishName = languageDisplayName(clean)
    if (englishName.isNotBlank() && !englishName.equals("Unknown", ignoreCase = true)) {
        candidates.add(englishName)
    }

    val ownLocale = Locale.forLanguageTag(clean)
    val ownName = ownLocale.getDisplayLanguage(ownLocale)
    if (ownName.isNotBlank() && !ownName.equals("Unknown", ignoreCase = true)) {
        candidates.add(ownName)
    }

    if (clean.length == ISO_639_2_CODE_LENGTH) {
        candidates.add(clean)
    }
    BIBLIOGRAPHIC_MAP[clean]?.let { candidates.add(it) }
    for ((bib3, lang2) in BIBLIOGRAPHIC_MAP) {
        if (lang2.equals(clean, ignoreCase = true)) {
            candidates.add(bib3)
        }
    }

    try {
        val iso3 = ownLocale.isO3Language
        if (iso3.isNotBlank() && iso3.length == ISO_639_2_CODE_LENGTH) {
            candidates.add(iso3)
        }
    } catch (_: Exception) {
        // Ignore missing ISO-3
    }

    return candidates
        .filter { it.isNotBlank() }
        .map { Regex("\\b${Regex.escape(it)}\\b", RegexOption.IGNORE_CASE) }
}
