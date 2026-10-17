package com.nexus.stream.presentation.feature.streams

import com.nexus.stream.domain.model.Addon
import com.nexus.stream.domain.model.ParsedStream
import com.nexus.stream.domain.model.StreamFilters
import com.nexus.stream.domain.model.StreamKind
import com.nexus.stream.domain.model.StreamSort
import com.nexus.stream.domain.model.TypeFilter
import com.nexus.stream.domain.repository.StreamsState
import com.nexus.stream.domain.usecase.LanguageCodes
import com.nexus.stream.domain.usecase.StreamOrdering

internal object StreamsFilterReducer {
    private const val MIN_ACTIVE_FOR_FILTER = 2

    fun buildEmissionState(
        state: StreamsUiState,
        emission: StreamsState,
        combined: List<ParsedStream>,
        matching: List<Addon>,
    ): StreamsUiState {
        val baseState =
            state.copy(
                isLoading = !emission.done,
                answered = emission.answered,
                total = emission.total,
                allStreams = combined,
                failures = emission.failures,
                isDone = emission.done,
            )
        return recalculate(baseState, matching)
    }

    fun updateFilters(
        state: StreamsUiState,
        matching: List<Addon>,
        filters: StreamFilters,
    ): StreamsUiState {
        val baseState =
            state.copy(
                selectedResolution = filters.resolution,
                selectedAddon = filters.addonKey,
                selectedLanguage = filters.language,
                selectedType = filters.type,
                cachedOnly = filters.cachedOnly,
            )
        return recalculate(baseState, matching)
    }

    fun updateSort(
        state: StreamsUiState,
        matching: List<Addon>,
        sort: StreamSort,
    ): StreamsUiState {
        val baseState = state.copy(sort = sort)
        return recalculate(baseState, matching)
    }

    fun updatePreferences(
        state: StreamsUiState,
        matching: List<Addon>,
        preferredLanguage: String?,
        preferredQuality: Int?,
        hideCam: Boolean,
    ): StreamsUiState {
        val baseState =
            state.copy(
                preferredLanguage = preferredLanguage,
                preferredQuality = preferredQuality,
                hideCam = hideCam,
            )
        return recalculate(baseState, matching)
    }

    fun clearFilters(
        state: StreamsUiState,
        matching: List<Addon> = emptyList(),
    ): StreamsUiState {
        val baseState =
            state.copy(
                selectedResolution = null,
                selectedAddon = null,
                selectedLanguage = null,
                selectedType = TypeFilter.ALL,
                cachedOnly = false,
            )
        return recalculate(baseState, matching)
    }

    fun updateActiveAddons(
        state: StreamsUiState,
        matching: List<Addon>,
    ): StreamsUiState = recalculate(state, matching)

    fun disambiguateAddons(addons: List<Addon>): List<Pair<Addon, String>> {
        val counts = mutableMapOf<String, Int>()
        val totals = addons.groupingBy { it.manifest.name }.eachCount()
        val result = mutableListOf<Pair<Addon, String>>()

        for (addon in addons) {
            val name = addon.manifest.name
            val occurrence = counts.getOrDefault(name, 0) + 1
            counts[name] = occurrence
            val label =
                if ((totals[name] ?: 0) > 1 && occurrence > 1) {
                    "$name ($occurrence)"
                } else {
                    name
                }
            result.add(addon to label)
        }
        return result
    }

    private fun recalculate(
        state: StreamsUiState,
        matching: List<Addon>,
    ): StreamsUiState {
        val all = state.allStreams
        val availableResolutions = StreamOrdering.availableResolutions(all)
        val activeUrls = StreamsFilterComputer.extractActiveUrls(matching, state.availableAddons)
        val presentLanguages = all.flatMap { LanguageCodes.codes(it.languages) }.distinct()
        val currentFilters =
            StreamsFilterComputer.sanitizeFilters(
                state,
                all,
                availableResolutions,
                activeUrls,
                presentLanguages,
            )

        val params =
            CrossCountParams(all, currentFilters, availableResolutions, activeUrls, presentLanguages, state.hideCam)
        val crossCounts = StreamsFilterComputer.computeCrossCounts(params)
        val failedUrls = state.failures.map { it.transportUrl }.toSet()
        val addonOptions =
            StreamsFilterComputer.buildAddonOptionsList(
                matching,
                state.availableAddons,
                crossCounts.addonCounts,
                failedUrls,
            )

        val sortedLanguages =
            presentLanguages.sortedWith(
                compareByDescending<String> { crossCounts.langCounts[it] ?: 0 }
                    .thenBy { LanguageCodes.languageDisplayName(it) },
            )

        val filtered = StreamOrdering.applyFilters(all, currentFilters, state.hideCam)
        val sortedFiltered = StreamOrdering.sort(filtered, state.sort, state.preferredLanguage, state.preferredQuality)
        val showAddon = if (matching.isNotEmpty()) matching.size >= MIN_ACTIVE_FOR_FILTER else state.showAddonFilter

        val bundle =
            FilteredStateBundle(
                filters = currentFilters,
                sortedFiltered = sortedFiltered,
                resolutions = availableResolutions,
                crossCounts = crossCounts,
                addonOptions = addonOptions,
                languages = sortedLanguages,
                showAddon = showAddon,
                hasDirect = all.any { it.kind == StreamKind.Direct },
                hasTorrent = all.any { it.kind == StreamKind.Torrent },
                hasCached = all.any { it.debrid?.cached == true },
            )
        return StreamsFilterComputer.assembleState(state, bundle)
    }
}
