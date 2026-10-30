package com.nexus.stream.presentation.feature.streams

import com.nexus.stream.domain.model.Addon
import com.nexus.stream.domain.model.ParsedStream
import com.nexus.stream.domain.model.StreamFilters
import com.nexus.stream.domain.model.StreamKind
import com.nexus.stream.domain.model.TypeFilter
import com.nexus.stream.domain.usecase.LanguageCodes
import com.nexus.stream.domain.usecase.StreamOrdering

internal data class CrossCounts(
    val resCounts: Map<Int, Int>,
    val allResCount: Int,
    val addonCounts: Map<String, Int>,
    val allAddonsCount: Int,
    val langCounts: Map<String, Int>,
    val anyLangCount: Int,
    val directCount: Int,
    val torrentCount: Int,
    val allTypeCount: Int,
    val cachedCount: Int,
)

internal data class CrossCountParams(
    val all: List<ParsedStream>,
    val filters: StreamFilters,
    val availableResolutions: List<Int>,
    val activeUrls: Set<String>,
    val presentLanguages: List<String>,
    val hideCam: Boolean,
)

internal data class FilteredStateBundle(
    val filters: StreamFilters,
    val sortedFiltered: List<ParsedStream>,
    val resolutions: List<Int>,
    val crossCounts: CrossCounts,
    val addonOptions: List<AddonFilterOption>,
    val languages: List<String>,
    val showAddon: Boolean,
    val hasDirect: Boolean,
    val hasTorrent: Boolean,
    val hasCached: Boolean,
    val hiddenCount: Int = 0,
    val isDownloadEmpty: Boolean = false,
)

internal object StreamsFilterComputer {
    fun extractActiveUrls(
        matching: List<Addon>,
        availableAddons: List<AddonFilterOption>,
    ): Set<String> =
        if (matching.isNotEmpty()) {
            matching.map { it.transportUrl }.toSet()
        } else {
            availableAddons.map { it.transportUrl }.toSet()
        }

    fun sanitizeFilters(
        state: StreamsUiState,
        all: List<ParsedStream>,
        availableResolutions: List<Int>,
        activeUrls: Set<String>,
        presentLanguages: List<String>,
    ): StreamFilters {
        val validAddon = if (state.selectedAddon in activeUrls) state.selectedAddon else null
        if (all.isEmpty()) {
            return StreamFilters(
                resolution = state.selectedResolution,
                addonKey = validAddon,
                language = state.selectedLanguage,
                type = state.selectedType,
                cachedOnly = state.cachedOnly,
            )
        }
        val validRes = if (state.selectedResolution in availableResolutions) state.selectedResolution else null
        val validLang = if (state.selectedLanguage in presentLanguages) state.selectedLanguage else null

        val hasDirect = all.any { it.kind == StreamKind.Direct }
        val hasTorrent = all.any { it.kind == StreamKind.Torrent }
        val validType =
            when (state.selectedType) {
                TypeFilter.ALL -> TypeFilter.ALL
                TypeFilter.DIRECT -> if (hasDirect) TypeFilter.DIRECT else TypeFilter.ALL
                TypeFilter.TORRENT -> if (hasTorrent) TypeFilter.TORRENT else TypeFilter.ALL
            }

        val hasCached = all.any { it.debrid?.cached == true }
        val validCachedOnly = if (hasCached) state.cachedOnly else false

        return StreamFilters(
            resolution = validRes,
            addonKey = validAddon,
            language = validLang,
            type = validType,
            cachedOnly = validCachedOnly,
        )
    }

    fun computeCrossCounts(params: CrossCountParams): CrossCounts {
        val baseForRes = StreamOrdering.applyFilters(params.all, params.filters.copy(resolution = null), params.hideCam)
        val resCounts = params.availableResolutions.associateWith { r -> baseForRes.count { it.resolution == r } }

        val baseForAddon = StreamOrdering.applyFilters(params.all, params.filters.copy(addonKey = null), params.hideCam)
        val addonCounts =
            params.activeUrls.associateWith { id ->
                baseForAddon.count { id in it.addonKeys || id in it.addonNames }
            }

        val baseForLang = StreamOrdering.applyFilters(params.all, params.filters.copy(language = null), params.hideCam)
        val langCounts =
            params.presentLanguages.associateWith { code ->
                baseForLang.count {
                    code in LanguageCodes.codes(it.languages) || it.languages.multi || it.languages.dualAudio
                }
            }

        val baseForType =
            StreamOrdering.applyFilters(
                params.all,
                params.filters.copy(type = TypeFilter.ALL),
                params.hideCam,
            )
        val baseForCached =
            StreamOrdering.applyFilters(
                params.all,
                params.filters.copy(cachedOnly = false),
                params.hideCam,
            )

        return CrossCounts(
            resCounts = resCounts,
            allResCount = baseForRes.size,
            addonCounts = addonCounts,
            allAddonsCount = baseForAddon.size,
            langCounts = langCounts,
            anyLangCount = baseForLang.size,
            directCount = baseForType.count { it.kind == StreamKind.Direct },
            torrentCount = baseForType.count { it.kind == StreamKind.Torrent },
            allTypeCount = baseForType.size,
            cachedCount = baseForCached.count { it.debrid?.cached == true },
        )
    }

    fun buildAddonOptionsList(
        matching: List<Addon>,
        currentOptions: List<AddonFilterOption>,
        addonCounts: Map<String, Int>,
        failedUrls: Set<String>,
    ): List<AddonFilterOption> =
        if (matching.isNotEmpty()) {
            StreamsFilterReducer.disambiguateAddons(matching).map { (addon, label) ->
                AddonFilterOption(
                    transportUrl = addon.transportUrl,
                    displayName = label,
                    count = addonCounts[addon.transportUrl] ?: 0,
                    failed = addon.transportUrl in failedUrls,
                )
            }
        } else {
            currentOptions.map { opt ->
                opt.copy(
                    count = addonCounts[opt.transportUrl] ?: 0,
                    failed = opt.transportUrl in failedUrls,
                )
            }
        }

    fun assembleState(
        state: StreamsUiState,
        b: FilteredStateBundle,
    ): StreamsUiState =
        state.copy(
            selectedResolution = b.filters.resolution,
            selectedAddon = b.filters.addonKey,
            selectedLanguage = b.filters.language,
            selectedType = b.filters.type,
            cachedOnly = b.filters.cachedOnly,
            filteredStreams = b.sortedFiltered,
            availableResolutions = b.resolutions,
            resolutionCounts = b.crossCounts.resCounts,
            allResolutionsCount = b.crossCounts.allResCount,
            availableAddons = b.addonOptions,
            allAddonsCount = b.crossCounts.allAddonsCount,
            showAddonFilter = b.showAddon,
            availableLanguages = b.languages,
            languageCounts = b.crossCounts.langCounts,
            anyLanguageCount = b.crossCounts.anyLangCount,
            typeDirectCount = b.crossCounts.directCount,
            typeTorrentCount = b.crossCounts.torrentCount,
            allTypesCount = b.crossCounts.allTypeCount,
            cachedCount = b.crossCounts.cachedCount,
            showResolutionFilter = b.resolutions.size >= 2,
            showLanguageFilter = b.languages.size >= 2,
            showTypeFilter = b.hasDirect && b.hasTorrent,
            showCachedFilter = b.hasCached,
            hiddenCount = b.hiddenCount,
            isDownloadEmpty = b.isDownloadEmpty,
        )
}
