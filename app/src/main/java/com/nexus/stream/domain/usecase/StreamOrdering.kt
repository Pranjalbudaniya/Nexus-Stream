package com.nexus.stream.domain.usecase

import com.nexus.stream.domain.model.ParsedStream
import com.nexus.stream.domain.model.PlaybackCapabilities
import com.nexus.stream.domain.model.ReleaseType
import com.nexus.stream.domain.model.Stream
import com.nexus.stream.domain.model.StreamKind
import com.nexus.stream.domain.model.StreamMatch

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

    fun sort(list: List<ParsedStream>): List<ParsedStream> =
        list.sortedWith { a, b ->
            val penA = if (isPenalized(a)) 1 else 0
            val penB = if (isPenalized(b)) 1 else 0
            val penCompare = penA.compareTo(penB)
            if (penCompare != 0) return@sortedWith penCompare

            val resCompare = compareNullableDescending(a.resolution, b.resolution)
            if (resCompare != 0) return@sortedWith resCompare

            val seedCompare = compareNullableDescending(a.seeders, b.seeders)
            if (seedCompare != 0) return@sortedWith seedCompare

            val sizeCompare = compareNullableDescending(a.sizeBytes, b.sizeBytes)
            if (sizeCompare != 0) return@sortedWith sizeCompare

            0
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

    fun resolutionCounts(
        list: List<ParsedStream>,
        selectedAddon: String?,
    ): Map<Int, Int> {
        val filteredByAddon = filterByAddon(list, selectedAddon)
        val resolutions = availableResolutions(list)
        return resolutions.associateWith { res ->
            filteredByAddon.count { it.resolution == res }
        }
    }

    fun addonCounts(
        list: List<ParsedStream>,
        selectedResolution: Int?,
        allAddonIdentifiers: List<String>,
    ): Map<String, Int> {
        val filteredByRes = filterByResolution(list, selectedResolution)
        return allAddonIdentifiers.associateWith { id ->
            filteredByRes.count { id in it.addonKeys || id in it.addonNames }
        }
    }

    fun bestPlayable(
        list: List<ParsedStream>,
        capabilities: PlaybackCapabilities,
    ): ParsedStream? {
        val nonPenalized = list.filterNot { isPenalized(it) }
        val candidate = nonPenalized.firstOrNull { canPlay(it, capabilities) }
        if (candidate != null) return candidate
        return list.firstOrNull { canPlay(it, capabilities) }
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

private fun canPlay(
    item: ParsedStream,
    capabilities: PlaybackCapabilities,
): Boolean =
    when (item.kind) {
        StreamKind.Direct -> true
        StreamKind.Torrent -> capabilities.supportsTorrent
        StreamKind.ExternalLink -> false
    }

private fun <T : Comparable<T>> compareNullableDescending(
    a: T?,
    b: T?,
): Int =
    when {
        a != null && b != null -> b.compareTo(a)
        a != null -> -1
        b != null -> 1
        else -> 0
    }
