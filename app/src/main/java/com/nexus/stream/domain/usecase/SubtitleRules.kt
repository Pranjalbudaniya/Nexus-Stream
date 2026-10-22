package com.nexus.stream.domain.usecase

import com.nexus.stream.domain.model.OnlineSubtitle
import java.util.Locale

const val MAX_SUBTITLE_BYTES = 5L * 1024 * 1024
private const val MIN_SUBTITLE_DELAY_MS = -30_000L
private const val MAX_SUBTITLE_DELAY_MS = 30_000L
private const val MILLIS_PER_SECOND = 1000.0

private val ALLOWED_EXTENSIONS = setOf("srt", "ass", "ssa", "vtt", "sub", "smi")

private val BIBLIOGRAPHIC_MAP: Map<String, String> =
    mapOf(
        "fre" to "fr",
        "ger" to "de",
        "chi" to "zh",
        "dut" to "nl",
        "gre" to "el",
        "cze" to "cs",
        "slo" to "sk",
        "rum" to "ro",
        "per" to "fa",
        "ice" to "is",
        "mac" to "mk",
        "may" to "ms",
        "wel" to "cy",
        "bur" to "my",
        "arm" to "hy",
        "baq" to "eu",
        "geo" to "ka",
        "alb" to "sq",
    )

private val ISO3_TO_LOCALE: Map<String, Locale> by lazy {
    Locale
        .getAvailableLocales()
        .mapNotNull { loc ->
            try {
                val iso3 = loc.isO3Language.lowercase()
                iso3 to loc
            } catch (_: Exception) {
                null
            }
        }.toMap()
}

private fun resolveCompoundTag(clean: String): String? {
    if (!clean.contains('-') && !clean.contains('_')) return null
    val tag = clean.replace('_', '-')
    val loc = Locale.forLanguageTag(tag)
    val name = loc.getDisplayName(Locale.ENGLISH)
    return name.takeIf { it.isNotBlank() && !it.equals(clean, ignoreCase = true) }
}

private fun resolveCodeName(clean: String): String? {
    val lower = clean.lowercase()
    val bibCode = BIBLIOGRAPHIC_MAP[lower]
    val bibName = bibCode?.let { Locale.forLanguageTag(it).getDisplayLanguage(Locale.ENGLISH) }
    val isoName = ISO3_TO_LOCALE[lower]?.getDisplayLanguage(Locale.ENGLISH)
    val loc = Locale.forLanguageTag(clean)
    val directName = loc.getDisplayLanguage(Locale.ENGLISH).takeIf { !it.equals(clean, ignoreCase = true) }
    val candidate = bibName ?: isoName ?: directName
    return candidate?.takeIf { it.isNotBlank() }?.replaceFirstChar { it.uppercase() }
}

fun languageDisplayName(code: String?): String {
    if (code.isNullOrBlank()) return "Unknown"
    val clean = code.trim()
    return resolveCompoundTag(clean) ?: resolveCodeName(clean) ?: clean.uppercase()
}

data class LanguageGroup(
    val language: String,
    val items: List<OnlineSubtitle>,
)

fun groupByLanguage(
    items: List<OnlineSubtitle>,
    limit: Int = 60,
): List<LanguageGroup> {
    val distinct = items.distinctBy { it.subtitle.url }
    val grouped = distinct.groupBy { languageDisplayName(it.subtitle.lang) }
    var remaining = limit
    val result = mutableListOf<LanguageGroup>()

    for ((lang, groupItems) in grouped.entries.sortedBy { it.key }) {
        if (remaining <= 0) break
        val sortedItems =
            groupItems.sortedWith(
                compareBy<OnlineSubtitle> { it.subtitle.label.orEmpty() }
                    .thenBy { it.addonName },
            )
        val taken = sortedItems.take(remaining)
        if (taken.isNotEmpty()) {
            result.add(LanguageGroup(language = lang, items = taken))
            remaining -= taken.size
        }
    }
    return result
}

fun isAllowedSubtitleFile(displayName: String?): Boolean {
    if (displayName.isNullOrBlank()) return false
    val lastDotIndex = displayName.lastIndexOf('.')
    val hasValidExtension = lastDotIndex > 0 && lastDotIndex < displayName.length - 1
    return hasValidExtension && displayName.substring(lastDotIndex + 1).lowercase() in ALLOWED_EXTENSIONS
}

fun clampSubtitleDelay(ms: Long): Long = ms.coerceIn(MIN_SUBTITLE_DELAY_MS, MAX_SUBTITLE_DELAY_MS)

fun formatSubtitleDelay(ms: Long): String {
    if (ms == 0L) return "0.0 s"
    val seconds = ms.toDouble() / MILLIS_PER_SECOND
    val sign = if (seconds > 0) "+" else ""
    return "$sign${String.format(Locale.US, "%.1f", seconds)} s"
}
