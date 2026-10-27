package com.nexus.stream.domain.usecase

import com.nexus.stream.domain.model.Addon
import com.nexus.stream.domain.model.CatalogSpec
import com.nexus.stream.domain.model.DiscoverCatalog
import com.nexus.stream.domain.model.DiscoverSelection
import com.nexus.stream.domain.model.GenreKind
import com.nexus.stream.domain.model.GenreSpec
import com.nexus.stream.domain.model.MetaPreview
import com.nexus.stream.domain.model.PosterShape
import com.nexus.stream.domain.model.TypeDisplay
import com.nexus.stream.domain.model.isSearchOnly
import com.nexus.stream.domain.model.supportsSkip

private const val YEAR_DIGIT_COUNT = 4

private data class RawDiscoverCandidate(
    val addonName: String,
    val transportUrl: String,
    val spec: CatalogSpec,
    val baseTitle: String,
    val key: String,
    val genre: GenreSpec?,
    val supportsSkip: Boolean,
)

fun buildDiscoverCatalogs(addons: List<Addon>): List<DiscoverCatalog> {
    val candidates = mutableListOf<RawDiscoverCandidate>()
    val seenKeys = mutableSetOf<String>()

    val sortedAddons = addons.filter { it.enabled }.sortedBy { it.position }
    for (addon in sortedAddons) {
        for (spec in addon.manifest.catalogs) {
            val candidate = extractCandidate(addon, spec) ?: continue
            if (seenKeys.add(candidate.key)) {
                candidates.add(candidate)
            }
        }
    }

    val typeAndTitleCounts = candidates.groupingBy { it.spec.type to it.baseTitle }.eachCount()

    return candidates.map { candidate ->
        val duplicateCount = typeAndTitleCounts[candidate.spec.type to candidate.baseTitle] ?: 0
        val finalTitle =
            if (duplicateCount > 1) {
                "${candidate.baseTitle} (${candidate.addonName})"
            } else {
                candidate.baseTitle
            }

        DiscoverCatalog(
            key = candidate.key,
            type = candidate.spec.type,
            catalogId = candidate.spec.id,
            transportUrl = candidate.transportUrl,
            title = finalTitle,
            genre = candidate.genre,
            supportsSkip = candidate.supportsSkip,
        )
    }
}

private fun extractCandidate(
    addon: Addon,
    spec: CatalogSpec,
): RawDiscoverCandidate? {
    val genreExtra = spec.extras.find { it.name == "genre" }
    val options =
        genreExtra
            ?.options
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.distinct()
            .orEmpty()

    val isInvalid =
        spec.isSearchOnly ||
            spec.extras.any { it.isRequired && it.name != "genre" } ||
            (genreExtra?.isRequired == true && options.isEmpty())

    if (isInvalid) {
        return null
    }

    val genreSpec =
        if (genreExtra != null && options.isNotEmpty()) {
            GenreSpec(options = options, required = genreExtra.isRequired)
        } else {
            null
        }

    return RawDiscoverCandidate(
        addonName = addon.manifest.name,
        transportUrl = addon.transportUrl,
        spec = spec,
        baseTitle = spec.name.ifBlank { spec.id },
        key = "${addon.transportUrl}|${spec.type}|${spec.id}",
        genre = genreSpec,
        supportsSkip = spec.supportsSkip,
    )
}

fun discoverTypes(catalogs: List<DiscoverCatalog>): List<String> {
    val rawTypes = catalogs.map { it.type.trim() }.filter { it.isNotBlank() }.distinct()
    val hasMovie = rawTypes.any { it.equals("movie", ignoreCase = true) }
    val hasSeries = rawTypes.any { it.equals("series", ignoreCase = true) }
    val others =
        rawTypes
            .filterNot { it.equals("movie", ignoreCase = true) || it.equals("series", ignoreCase = true) }
            .sortedBy { it.lowercase() }

    val result = mutableListOf<String>()
    if (hasMovie) result.add("movie")
    if (hasSeries) result.add("series")
    result.addAll(others)
    return result
}

fun resolveDiscoverSelection(
    catalogs: List<DiscoverCatalog>,
    wanted: DiscoverSelection?,
    lastCatalogByType: Map<String, String> = emptyMap(),
): DiscoverSelection? {
    val types = discoverTypes(catalogs)
    val type = types.find { it.equals(wanted?.type, ignoreCase = true) } ?: types.firstOrNull()
    val catalogsForType =
        if (type != null) {
            catalogs.filter { it.type.equals(type, ignoreCase = true) }
        } else {
            emptyList()
        }
    val catalog =
        catalogsForType.find { it.key == wanted?.catalogKey }
            ?: lastCatalogByType[type]?.let { rememberedKey ->
                catalogsForType.find { it.key == rememberedKey }
            }
            ?: catalogsForType.firstOrNull()

    return if (type != null && catalog != null) {
        DiscoverSelection(
            type = type,
            catalogKey = catalog.key,
            genre = resolveGenre(catalog, wanted?.genre),
        )
    } else {
        null
    }
}

private fun resolveGenre(
    catalog: DiscoverCatalog,
    wantedGenre: String?,
): String? {
    val genreSpec = catalog.genre ?: return null
    val options = genreSpec.options
    return if (wantedGenre != null && options.contains(wantedGenre)) {
        wantedGenre
    } else if (genreSpec.required) {
        options.firstOrNull()
    } else {
        null
    }
}

fun genreKind(options: List<String>): GenreKind =
    if (options.isNotEmpty() && options.all { it.length == YEAR_DIGIT_COUNT && it.all { c -> c.isDigit() } }) {
        GenreKind.YEAR
    } else {
        GenreKind.GENRE
    }

fun gridShapeOf(items: List<MetaPreview>): PosterShape =
    if (items.any { it.posterShape == PosterShape.LANDSCAPE }) {
        PosterShape.LANDSCAPE
    } else {
        PosterShape.POSTER
    }

fun typeDisplay(type: String): TypeDisplay =
    when (type.lowercase()) {
        "movie" -> {
            TypeDisplay.Movie
        }

        "series" -> {
            TypeDisplay.Series
        }

        else -> {
            val capitalized =
                type.replaceFirstChar {
                    if (it.isLowerCase()) it.titlecase() else it.toString()
                }
            TypeDisplay.Custom(capitalized)
        }
    }
