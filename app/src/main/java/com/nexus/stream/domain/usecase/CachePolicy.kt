@file:Suppress("MatchingDeclarationName")

package com.nexus.stream.domain.usecase

data class CacheEntry(
    val name: String,
    val sizeBytes: Long,
    val lastUsedMs: Long,
)

private const val BYTES_PER_GIB: Long = 1024L * 1024L * 1024L

fun cacheLimitBytes(gb: Int): Long {
    val clampedGb = gb.toLong().coerceAtLeast(1L)
    val maxGb = Long.MAX_VALUE / BYTES_PER_GIB
    return if (clampedGb > maxGb) {
        Long.MAX_VALUE
    } else {
        clampedGb * BYTES_PER_GIB
    }
}

private fun saturatingAdd(
    a: Long,
    b: Long,
): Long {
    val nonNegA = a.coerceAtLeast(0L)
    val nonNegB = b.coerceAtLeast(0L)
    return if (Long.MAX_VALUE - nonNegA < nonNegB) {
        Long.MAX_VALUE
    } else {
        nonNegA + nonNegB
    }
}

fun evictionPlan(
    entries: List<CacheEntry>,
    limitBytes: Long,
    inUse: Set<String>,
): List<String> {
    val nonNegativeEntries =
        entries.map {
            if (it.sizeBytes < 0L) it.copy(sizeBytes = 0L) else it
        }

    var total = 0L
    for (entry in nonNegativeEntries) {
        total = saturatingAdd(total, entry.sizeBytes)
    }

    if (total <= limitBytes) {
        return emptyList()
    }

    val candidates =
        nonNegativeEntries
            .filter { it.name !in inUse }
            .sortedWith(compareBy({ it.lastUsedMs }, { it.name }))

    var inUseSum = 0L
    for (entry in nonNegativeEntries) {
        if (entry.name in inUse) {
            inUseSum = saturatingAdd(inUseSum, entry.sizeBytes)
        }
    }

    val suffixCandidateSum = LongArray(candidates.size + 1)
    for (i in candidates.indices.reversed()) {
        suffixCandidateSum[i] = saturatingAdd(candidates[i].sizeBytes, suffixCandidateSum[i + 1])
    }

    val toEvict = mutableListOf<String>()
    for (i in candidates.indices) {
        toEvict.add(candidates[i].name)
        val remaining = saturatingAdd(inUseSum, suffixCandidateSum[i + 1])
        if (remaining <= limitBytes) {
            break
        }
    }

    return toEvict
}
