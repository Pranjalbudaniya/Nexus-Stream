package com.nexus.stream.domain.usecase

import com.nexus.stream.domain.model.ParsedStream
import com.nexus.stream.domain.model.PlaybackCapabilities
import com.nexus.stream.domain.model.ReleaseType
import com.nexus.stream.domain.model.Stream
import com.nexus.stream.domain.model.StreamFilters
import com.nexus.stream.domain.model.StreamMatch
import com.nexus.stream.domain.model.StreamSort

object StreamOrdering {
    fun dedupe(list: List<ParsedStream>): List<ParsedStream> {
        val map = linkedMapOf<String, ParsedStream>()
        for (item in list) {
            val existing = map[item.key]
            if (existing == null) {
                map[item.key] = item
            } else {
                val mergedAddons = (existing.addonNames + item.addonNames).distinct()
                val mergedKeys = (existing.addonKeys + item.addonKeys).distinct()
                map[item.key] = existing.copy(addonNames = mergedAddons, addonKeys = mergedKeys)
            }
        }
        return map.values.toList()
    }

    fun isPenalized(item: ParsedStream): Boolean =
        item.match in setOf(StreamMatch.WRONG_SEASON, StreamMatch.WRONG_EPISODE, StreamMatch.WRONG_YEAR) ||
            item.releaseType == ReleaseType.CAM

    fun sort(
        list: List<ParsedStream>,
        sort: StreamSort = StreamSort.BEST,
        preferredLanguage: String? = null,
        preferredQuality: Int? = null,
    ): List<ParsedStream> =
        list.sortedWith { a, b ->
            val penA = if (isPenalized(a)) 1 else 0
            val penB = if (isPenalized(b)) 1 else 0
            val penCompare = penA.compareTo(penB)
            if (penCompare != 0) return@sortedWith penCompare

            when (sort) {
                StreamSort.BEST -> StreamSorting.compareBest(a, b, preferredLanguage, preferredQuality)
                StreamSort.QUALITY -> StreamSorting.compareQuality(a, b)
                StreamSort.SEEDERS -> StreamSorting.compareSeeders(a, b)
                StreamSort.SIZE -> StreamSorting.compareSize(a, b)
            }
        }

    fun sortWith(
        list: List<ParsedStream>,
        sort: StreamSort = StreamSort.BEST,
        preferredLanguage: String? = null,
        preferredQuality: Int? = null,
    ): List<ParsedStream> = sort(list, sort, preferredLanguage, preferredQuality)

    fun applyFilters(
        items: List<ParsedStream>,
        filters: StreamFilters,
        hideCam: Boolean = false,
    ): List<ParsedStream> =
        items.filter { item ->
            if (hideCam && item.releaseType == ReleaseType.CAM) {
                false
            } else {
                StreamFiltering.matchesFilters(item, filters)
            }
        }

    fun filterByResolution(
        list: List<ParsedStream>,
        resolution: Int?,
    ): List<ParsedStream> =
        if (resolution == null) {
            list
        } else {
            list.filter { it.resolution == resolution }
        }

    fun filterByAddon(
        list: List<ParsedStream>,
        addonKey: String?,
    ): List<ParsedStream> =
        if (addonKey == null) {
            list
        } else {
            list.filter { addonKey in it.addonKeys || addonKey in it.addonNames }
        }

    fun filter(
        list: List<ParsedStream>,
        resolution: Int?,
        addonKey: String?,
    ): List<ParsedStream> =
        list.filter { item ->
            (resolution == null || item.resolution == resolution) &&
                (addonKey == null || addonKey in item.addonKeys || addonKey in item.addonNames)
        }

    fun availableResolutions(list: List<ParsedStream>): List<Int> =
        list
            .mapNotNull { it.resolution }
            .distinct()
            .sortedDescending()

    fun bestPlayable(
        list: List<ParsedStream>,
        capabilities: PlaybackCapabilities,
        preferredLanguage: String? = null,
        preferredQuality: Int? = null,
    ): ParsedStream? {
        val sorted = sort(list, StreamSort.BEST, preferredLanguage, preferredQuality)
        val nonPenalized = sorted.filterNot { isPenalized(it) }
        val candidate = nonPenalized.firstOrNull { StreamSorting.canPlay(it, capabilities) }
        return candidate ?: sorted.firstOrNull { StreamSorting.canPlay(it, capabilities) }
    }

    fun isFamilySafe(stream: Stream): Boolean {
        val text =
            buildString {
                append(stream.name.orEmpty())
                append(' ')
                append(stream.description.orEmpty())
                append(' ')
                append(stream.filename.orEmpty())
            }
        return !isAdultText(text)
    }
}
