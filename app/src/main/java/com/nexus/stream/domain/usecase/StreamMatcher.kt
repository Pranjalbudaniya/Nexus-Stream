package com.nexus.stream.domain.usecase

import com.nexus.stream.domain.model.ParsedStream
import com.nexus.stream.domain.model.SourceFingerprint
import com.nexus.stream.domain.model.StreamMatch
import com.nexus.stream.domain.model.StreamSource
import com.nexus.stream.domain.model.hashAddonKey
import com.nexus.stream.domain.model.name
import kotlin.math.abs

enum class MatchTier {
    BINGE_GROUP,
    INFO_HASH,
    ATTRIBUTES,
}

data class SourceMatch(
    val stream: ParsedStream,
    val tier: MatchTier,
)

const val SIZE_TOLERANCE_PERCENT = 25L
private const val PERCENT_SCALE = 100L

private data class ScoredCandidate(
    val stream: ParsedStream,
    val tier: MatchTier,
    val sizeDiff: Long,
    val seeders: Int,
    val index: Int,
)

private val candidateComparator =
    compareBy<ScoredCandidate> { it.tier.ordinal }
        .thenBy { it.sizeDiff }
        .thenByDescending { it.seeders }
        .thenBy { it.index }

fun matchSource(
    fingerprint: SourceFingerprint,
    candidates: List<ParsedStream>,
): SourceMatch? {
    if (fingerprint.addonKeyHashes.isEmpty() || candidates.isEmpty()) return null

    val addonHashSet = fingerprint.addonKeyHashes.toSet()
    val best =
        candidates
            .asSequence()
            .mapIndexedNotNull { index, cand ->
                if (!isEligible(cand, addonHashSet)) return@mapIndexedNotNull null
                val tier = determineTier(fingerprint, cand) ?: return@mapIndexedNotNull null
                val sizeDiff = computeSizeDiff(fingerprint, cand)
                val seeders = cand.seeders ?: Int.MIN_VALUE
                ScoredCandidate(cand, tier, sizeDiff, seeders, index)
            }.minWithOrNull(candidateComparator)

    return best?.let { SourceMatch(stream = it.stream, tier = it.tier) }
}

private fun isEligible(
    candidate: ParsedStream,
    addonHashSet: Set<String>,
): Boolean {
    if (candidate.match != StreamMatch.OK || candidate.stream.source is StreamSource.External) {
        return false
    }
    return candidate.addonKeys.any { hashAddonKey(it) in addonHashSet }
}

private fun determineTier(
    fingerprint: SourceFingerprint,
    candidate: ParsedStream,
): MatchTier? =
    when {
        fingerprint.bingeGroup != null && fingerprint.bingeGroup == candidate.stream.bingeGroup -> {
            MatchTier.BINGE_GROUP
        }

        fingerprint.infoHash != null && isTorrentInfoHashMatch(fingerprint.infoHash, candidate) -> {
            MatchTier.INFO_HASH
        }

        matchesAttributes(fingerprint, candidate) -> {
            MatchTier.ATTRIBUTES
        }

        else -> {
            null
        }
    }

private fun isTorrentInfoHashMatch(
    infoHash: String,
    candidate: ParsedStream,
): Boolean {
    val candHash = (candidate.stream.source as? StreamSource.Torrent)?.infoHash
    return candHash != null && infoHash.equals(candHash, ignoreCase = true)
}

private fun computeSizeDiff(
    fingerprint: SourceFingerprint,
    candidate: ParsedStream,
): Long =
    if (fingerprint.sizeBytes != null && candidate.sizeBytes != null) {
        abs(candidate.sizeBytes - fingerprint.sizeBytes)
    } else {
        Long.MAX_VALUE
    }

private fun matchesAttributes(
    fingerprint: SourceFingerprint,
    candidate: ParsedStream,
): Boolean {
    val basicMatch =
        fingerprint.kind == candidate.kind.name &&
            fingerprint.resolution != null &&
            fingerprint.resolution == candidate.resolution &&
            fingerprint.codec == candidate.codec?.name &&
            fingerprint.hdr == candidate.hdr.map { it.name }.sorted()

    val fpSize = fingerprint.sizeBytes
    val candSize = candidate.sizeBytes
    val sizeMatches =
        fpSize != null &&
            candSize != null &&
            abs(candSize - fpSize) * PERCENT_SCALE <= fpSize * SIZE_TOLERANCE_PERCENT

    return basicMatch && sizeMatches
}
