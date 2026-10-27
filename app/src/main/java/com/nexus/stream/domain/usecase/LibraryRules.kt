package com.nexus.stream.domain.usecase

import com.nexus.stream.domain.model.FacetCounts
import com.nexus.stream.domain.model.ListStatus
import com.nexus.stream.domain.model.WatchlistEntry
import com.nexus.stream.domain.model.WatchlistSort

fun filterWatchlist(
    entries: List<WatchlistEntry>,
    status: ListStatus?,
    type: String?,
): List<WatchlistEntry> =
    entries.filter { entry ->
        (status == null || entry.status == status) &&
            (type == null || entry.type.equals(type, ignoreCase = true))
    }

fun sortWatchlist(
    entries: List<WatchlistEntry>,
    sort: WatchlistSort,
): List<WatchlistEntry> =
    when (sort) {
        WatchlistSort.RECENTLY_ADDED -> {
            entries.sortedWith(
                compareByDescending<WatchlistEntry> { it.addedAt }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
                    .thenBy { it.metaId },
            )
        }

        WatchlistSort.TITLE_AZ -> {
            entries.sortedWith(
                compareBy<WatchlistEntry, String>(String.CASE_INSENSITIVE_ORDER) { it.name }
                    .thenBy { it.metaId },
            )
        }

        WatchlistSort.TITLE_ZA -> {
            entries.sortedWith(
                compareBy<WatchlistEntry, String>(String.CASE_INSENSITIVE_ORDER.reversed()) { it.name }
                    .thenByDescending { it.metaId },
            )
        }
    }

fun facetCounts(
    entries: List<WatchlistEntry>,
    statusFilter: ListStatus? = null,
    typeFilter: String? = null,
): FacetCounts {
    val typeFiltered =
        if (typeFilter != null) {
            entries.filter { it.type.equals(typeFilter, ignoreCase = true) }
        } else {
            entries
        }

    val statusFiltered =
        if (statusFilter != null) {
            entries.filter { it.status == statusFilter }
        } else {
            entries
        }

    val byStatus = ListStatus.entries.associateWith { 0 }.toMutableMap()
    for (entry in typeFiltered) {
        byStatus[entry.status] = (byStatus[entry.status] ?: 0) + 1
    }

    val byType = mutableMapOf<String, Int>()
    for (entry in statusFiltered) {
        val count = byType[entry.type] ?: 0
        byType[entry.type] = count + 1
    }

    val all =
        when {
            typeFilter != null -> typeFiltered.size
            statusFilter != null -> statusFiltered.size
            else -> entries.size
        }

    return FacetCounts(
        all = all,
        byStatus = byStatus,
        byType = byType,
    )
}

fun resolveTypeFilter(
    selected: String?,
    available: List<String>,
): String? =
    if (selected != null && available.any { it.equals(selected, ignoreCase = true) }) {
        available.first { it.equals(selected, ignoreCase = true) }
    } else {
        null
    }

fun availableTypes(types: List<String>): List<String> = orderTypes(types)

fun filterFamily(
    entries: List<WatchlistEntry>,
    familyMode: Boolean,
): List<WatchlistEntry> =
    if (!familyMode) {
        entries
    } else {
        entries.filterNot { isAdultText(it.name) }
    }
