package com.nexus.stream.domain.usecase

import com.nexus.stream.domain.model.ParsedStream
import com.nexus.stream.domain.model.StreamFilters
import com.nexus.stream.domain.model.StreamKind
import com.nexus.stream.domain.model.TypeFilter

internal object StreamFiltering {
    fun matchesFilters(
        item: ParsedStream,
        filters: StreamFilters,
    ): Boolean {
        val resMatch = filters.resolution == null || item.resolution == filters.resolution
        val addonMatch =
            filters.addonKey == null ||
                filters.addonKey in item.addonKeys ||
                filters.addonKey in item.addonNames
        val typeMatch = matchesTypeFilter(item, filters.type)
        val cachedMatch = !filters.cachedOnly || item.debrid?.cached == true
        val langMatch = matchesLanguageFilter(item, filters.language)
        return resMatch && addonMatch && typeMatch && cachedMatch && langMatch
    }

    fun resolutionCounts(
        list: List<ParsedStream>,
        selectedAddon: String?,
    ): Map<Int, Int> {
        val filteredByAddon = StreamOrdering.filterByAddon(list, selectedAddon)
        val resolutions = StreamOrdering.availableResolutions(list)
        return resolutions.associateWith { res ->
            filteredByAddon.count { it.resolution == res }
        }
    }

    fun addonCounts(
        list: List<ParsedStream>,
        selectedResolution: Int?,
        allAddonIdentifiers: List<String>,
    ): Map<String, Int> {
        val filteredByRes = StreamOrdering.filterByResolution(list, selectedResolution)
        return allAddonIdentifiers.associateWith { id ->
            filteredByRes.count { id in it.addonKeys || id in it.addonNames }
        }
    }

    private fun matchesTypeFilter(
        item: ParsedStream,
        type: TypeFilter,
    ): Boolean =
        when (type) {
            TypeFilter.ALL -> true
            TypeFilter.DIRECT -> item.kind == StreamKind.Direct
            TypeFilter.TORRENT -> item.kind == StreamKind.Torrent
        }

    private fun matchesLanguageFilter(
        item: ParsedStream,
        language: String?,
    ): Boolean {
        if (language == null) return true
        val matchesLang = language in LanguageCodes.codes(item.languages)
        val isMultiOrDual = item.languages.multi || item.languages.dualAudio
        return matchesLang || isMultiOrDual
    }
}
