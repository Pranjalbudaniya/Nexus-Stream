package com.nexus.stream.domain.usecase

import com.nexus.stream.domain.model.Addon
import com.nexus.stream.domain.model.CatalogSpec
import com.nexus.stream.domain.model.DiscoverCatalog
import com.nexus.stream.domain.model.supportsSearch

const val SEARCH_MIN_CHARS = 2
const val SEARCH_DEBOUNCE_MS = 400L
const val SEARCH_ROW_LIMIT = 40
const val SEARCH_MAX_PARALLEL = 4
const val HISTORY_LIMIT = 20

enum class DiscoverBodyMode {
    Browse,
    Recents,
    Search,
}

fun normalizeSearchQuery(raw: String): String = raw.trim().replace(Regex("\\s+"), " ")

fun isSearchable(q: String): Boolean {
    val count = q.codePointCount(0, q.length)
    return count >= 2 || (count == 1 && isCjkScript(Character.UnicodeScript.of(q.codePointAt(0))))
}

private fun isCjkScript(script: Character.UnicodeScript): Boolean =
    script == Character.UnicodeScript.HAN ||
        script == Character.UnicodeScript.HIRAGANA ||
        script == Character.UnicodeScript.KATAKANA ||
        script == Character.UnicodeScript.HANGUL

fun buildSearchCatalogs(addons: List<Addon>): List<DiscoverCatalog> =
    buildCatalogsPipeline(addons) { addon, spec ->
        extractSearchCandidate(addon, spec)
    }

private fun extractSearchCandidate(
    addon: Addon,
    spec: CatalogSpec,
): RawCatalogCandidate? {
    if (!spec.supportsSearch || spec.extras.any { it.isRequired && it.name != "search" }) {
        return null
    }

    return RawCatalogCandidate(
        addonName = addon.manifest.name,
        transportUrl = addon.transportUrl,
        spec = spec,
        baseTitle = spec.name.ifBlank { spec.id },
        key = "${addon.transportUrl}|${spec.type}|${spec.id}",
        genre = null,
        supportsSkip = false,
    )
}

fun discoverBodyMode(
    rawQuery: String,
    isFocused: Boolean,
    hasHistory: Boolean,
): DiscoverBodyMode {
    val normalized = normalizeSearchQuery(rawQuery)
    return when {
        normalized.isNotEmpty() -> DiscoverBodyMode.Search
        isFocused && hasHistory -> DiscoverBodyMode.Recents
        else -> DiscoverBodyMode.Browse
    }
}
