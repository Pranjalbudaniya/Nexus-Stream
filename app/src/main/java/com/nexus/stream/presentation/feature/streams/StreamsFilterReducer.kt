package com.nexus.stream.presentation.feature.streams

import com.nexus.stream.domain.model.Addon
import com.nexus.stream.domain.model.ParsedStream
import com.nexus.stream.domain.repository.AddonFailure
import com.nexus.stream.domain.repository.StreamsState
import com.nexus.stream.domain.usecase.StreamOrdering

internal object StreamsFilterReducer {
    private const val MIN_ACTIVE_FOR_FILTER = 2

    fun buildEmissionState(
        state: StreamsUiState,
        emission: StreamsState,
        combined: List<ParsedStream>,
        matching: List<Addon>,
    ): StreamsUiState {
        val resolutions = StreamOrdering.availableResolutions(combined)
        val currentRes = state.selectedResolution
        val currentAddon = state.selectedAddon
        val resCounts = StreamOrdering.resolutionCounts(combined, selectedAddon = currentAddon)
        val filtered = StreamOrdering.filter(combined, resolution = currentRes, addonKey = currentAddon)

        val disambiguated = disambiguateAddons(matching)
        val showRow = matching.size >= MIN_ACTIVE_FOR_FILTER
        val addonOptions =
            buildAddonOptions(
                addons = disambiguated,
                streams = combined,
                selectedResolution = currentRes,
                failures = emission.failures,
            )

        return state.copy(
            isLoading = !emission.done,
            answered = emission.answered,
            total = emission.total,
            allStreams = combined,
            filteredStreams = filtered,
            availableResolutions = resolutions,
            resolutionCounts = resCounts,
            availableAddons = addonOptions,
            showAddonFilter = showRow,
            failures = emission.failures,
            isDone = emission.done,
        )
    }

    fun selectResolution(
        state: StreamsUiState,
        resolution: Int?,
    ): StreamsUiState {
        val filtered =
            StreamOrdering.filter(
                state.allStreams,
                resolution = resolution,
                addonKey = state.selectedAddon,
            )
        val options =
            updateAddonCounts(
                options = state.availableAddons,
                streams = state.allStreams,
                selectedResolution = resolution,
            )
        return state.copy(
            selectedResolution = resolution,
            filteredStreams = filtered,
            availableAddons = options,
        )
    }

    fun selectAddon(
        state: StreamsUiState,
        addonKey: String?,
    ): StreamsUiState {
        val filtered =
            StreamOrdering.filter(
                state.allStreams,
                resolution = state.selectedResolution,
                addonKey = addonKey,
            )
        val resCounts =
            StreamOrdering.resolutionCounts(
                state.allStreams,
                selectedAddon = addonKey,
            )
        return state.copy(
            selectedAddon = addonKey,
            filteredStreams = filtered,
            resolutionCounts = resCounts,
        )
    }

    fun clearFilters(state: StreamsUiState): StreamsUiState {
        val resCounts = StreamOrdering.resolutionCounts(state.allStreams, selectedAddon = null)
        val options = updateAddonCounts(state.availableAddons, state.allStreams, selectedResolution = null)
        return state.copy(
            selectedResolution = null,
            selectedAddon = null,
            filteredStreams = state.allStreams,
            resolutionCounts = resCounts,
            availableAddons = options,
        )
    }

    fun updateActiveAddons(
        state: StreamsUiState,
        matching: List<Addon>,
    ): StreamsUiState {
        val disambiguated = disambiguateAddons(matching)
        val showRow = matching.size >= MIN_ACTIVE_FOR_FILTER
        val activeUrls = matching.map { it.transportUrl }.toSet()
        val validSelectedAddon =
            if (state.selectedAddon != null && state.selectedAddon in activeUrls) {
                state.selectedAddon
            } else {
                null
            }

        val options =
            buildAddonOptions(
                addons = disambiguated,
                streams = state.allStreams,
                selectedResolution = state.selectedResolution,
                failures = state.failures,
            )

        val filtered =
            StreamOrdering.filter(
                state.allStreams,
                resolution = state.selectedResolution,
                addonKey = validSelectedAddon,
            )
        val resCounts =
            StreamOrdering.resolutionCounts(
                state.allStreams,
                selectedAddon = validSelectedAddon,
            )

        return state.copy(
            availableAddons = options,
            showAddonFilter = showRow,
            selectedAddon = validSelectedAddon,
            filteredStreams = filtered,
            resolutionCounts = resCounts,
        )
    }

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

    fun buildAddonOptions(
        addons: List<Pair<Addon, String>>,
        streams: List<ParsedStream>,
        selectedResolution: Int?,
        failures: List<AddonFailure>,
    ): List<AddonFilterOption> {
        val failedUrls = failures.map { it.transportUrl }.toSet()
        val identifiers = addons.map { it.first.transportUrl }
        val counts =
            StreamOrdering.addonCounts(
                streams,
                selectedResolution = selectedResolution,
                allAddonIdentifiers = identifiers,
            )

        return addons.map { (addon, label) ->
            val count = counts[addon.transportUrl] ?: 0
            val isFailed = addon.transportUrl in failedUrls
            AddonFilterOption(
                transportUrl = addon.transportUrl,
                displayName = label,
                count = count,
                failed = isFailed,
            )
        }
    }

    fun updateAddonCounts(
        options: List<AddonFilterOption>,
        streams: List<ParsedStream>,
        selectedResolution: Int?,
    ): List<AddonFilterOption> {
        val identifiers = options.map { it.transportUrl }
        val counts =
            StreamOrdering.addonCounts(
                streams,
                selectedResolution = selectedResolution,
                allAddonIdentifiers = identifiers,
            )
        return options.map { opt ->
            opt.copy(count = counts[opt.transportUrl] ?: 0)
        }
    }
}
