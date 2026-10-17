package com.nexus.stream.domain.usecase

import com.nexus.stream.domain.model.ParsedStream
import com.nexus.stream.domain.model.PlaybackCapabilities
import com.nexus.stream.domain.model.StreamKind

private const val RANK_EXACT = 0
private const val RANK_LOWER = 1
private const val RANK_HIGHER = 2
private const val RANK_NONE = 3

internal object StreamSorting {
    fun compareBest(
        a: ParsedStream,
        b: ParsedStream,
        preferredLanguage: String?,
        preferredQuality: Int?,
    ): Int {
        var diff = 0
        if (preferredLanguage != null) {
            val aLangMatch = matchesLanguage(a, preferredLanguage)
            val bLangMatch = matchesLanguage(b, preferredLanguage)
            diff = (if (aLangMatch) 0 else 1).compareTo(if (bLangMatch) 0 else 1)
        }
        if (diff == 0 && preferredQuality != null) {
            diff = comparePreferredQuality(a.resolution, b.resolution, preferredQuality)
        }
        if (diff == 0) {
            diff = compareNullableDescending(a.resolution, b.resolution)
        }
        if (diff == 0) {
            diff = compareNullableDescending(a.seeders, b.seeders)
        }
        if (diff == 0) {
            diff = compareNullableDescending(a.sizeBytes, b.sizeBytes)
        }
        return diff
    }

    fun compareQuality(
        a: ParsedStream,
        b: ParsedStream,
    ): Int {
        var diff = compareNullableDescending(a.resolution, b.resolution)
        if (diff == 0) diff = compareNullableDescending(a.seeders, b.seeders)
        if (diff == 0) diff = compareNullableDescending(a.sizeBytes, b.sizeBytes)
        return diff
    }

    fun compareSeeders(
        a: ParsedStream,
        b: ParsedStream,
    ): Int {
        var diff = compareNullableDescending(a.seeders, b.seeders)
        if (diff == 0) diff = compareNullableDescending(a.resolution, b.resolution)
        return diff
    }

    fun compareSize(
        a: ParsedStream,
        b: ParsedStream,
    ): Int {
        var diff = compareNullableDescending(a.sizeBytes, b.sizeBytes)
        if (diff == 0) diff = compareNullableDescending(a.resolution, b.resolution)
        return diff
    }

    fun canPlay(
        item: ParsedStream,
        capabilities: PlaybackCapabilities,
    ): Boolean =
        when (item.kind) {
            StreamKind.Direct -> true
            StreamKind.Torrent -> capabilities.supportsTorrent
            StreamKind.ExternalLink -> false
        }

    fun <T : Comparable<T>> compareNullableDescending(
        a: T?,
        b: T?,
    ): Int =
        when {
            a != null && b != null -> b.compareTo(a)
            a != null -> -1
            b != null -> 1
            else -> 0
        }

    private fun matchesLanguage(
        item: ParsedStream,
        preferredLanguage: String,
    ): Boolean {
        if (item.languages.multi || item.languages.dualAudio) return true
        return preferredLanguage in LanguageCodes.codes(item.languages)
    }

    private fun comparePreferredQuality(
        resA: Int?,
        resB: Int?,
        target: Int,
    ): Int {
        val rankA = qualityRank(resA, target)
        val rankB = qualityRank(resB, target)
        return if (rankA != rankB) {
            rankA.compareTo(rankB)
        } else {
            compareNullableDescending(resA, resB)
        }
    }

    private fun qualityRank(
        res: Int?,
        target: Int,
    ): Int =
        when {
            res == null -> RANK_NONE
            res == target -> RANK_EXACT
            res < target -> RANK_LOWER
            else -> RANK_HIGHER
        }
}
