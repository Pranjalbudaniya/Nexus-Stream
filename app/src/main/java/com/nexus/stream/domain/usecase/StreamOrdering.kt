package com.nexus.stream.domain.usecase

import com.nexus.stream.domain.model.ParsedStream
import com.nexus.stream.domain.model.PlaybackCapabilities
import com.nexus.stream.domain.model.Stream
import com.nexus.stream.domain.model.StreamKind

object StreamOrdering {
    fun dedupe(list: List<ParsedStream>): List<ParsedStream> {
        val map = linkedMapOf<String, ParsedStream>()
        for (item in list) {
            val existing = map[item.key]
            if (existing == null) {
                map[item.key] = item
            } else {
                val mergedAddons = (existing.addonNames + item.addonNames).distinct()
                map[item.key] = existing.copy(addonNames = mergedAddons)
            }
        }
        return map.values.toList()
    }

    fun sort(list: List<ParsedStream>): List<ParsedStream> =
        list.sortedWith { a, b ->
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

    fun availableResolutions(list: List<ParsedStream>): List<Int> =
        list
            .mapNotNull { it.resolution }
            .distinct()
            .sortedDescending()

    fun bestPlayable(
        list: List<ParsedStream>,
        capabilities: PlaybackCapabilities,
    ): ParsedStream? =
        list.firstOrNull { item ->
            when (item.kind) {
                StreamKind.Direct -> true
                StreamKind.Torrent -> capabilities.supportsTorrent
                StreamKind.ExternalLink -> false
            }
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
}
