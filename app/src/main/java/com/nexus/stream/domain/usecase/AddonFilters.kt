package com.nexus.stream.domain.usecase

import com.nexus.stream.domain.model.Addon
import com.nexus.stream.domain.model.AddonCategory
import com.nexus.stream.domain.model.StoreAddon
import com.nexus.stream.domain.model.categoriesOf
import com.nexus.stream.domain.model.primaryCategory

fun String.toDisplayHost(): String {
    val withoutScheme =
        when {
            startsWith("https://", ignoreCase = true) -> substring(8)
            startsWith("http://", ignoreCase = true) -> substring(7)
            startsWith("stremio://", ignoreCase = true) -> substring(10)
            else -> this
        }
    return withoutScheme.substringBefore('/').substringBefore(':')
}

object AddonFilters {
    fun filterInstalled(
        addons: List<Addon>,
        query: String,
        category: AddonCategory?,
    ): List<Addon> {
        val trimmed = query.trim()
        return addons.filter { addon ->
            val matchesCategory = category == null || category in categoriesOf(addon.manifest)
            val matchesQuery =
                trimmed.isEmpty() ||
                    addon.manifest.name.contains(trimmed, ignoreCase = true) ||
                    addon.manifest.description.contains(trimmed, ignoreCase = true) ||
                    addon.transportUrl.toDisplayHost().contains(trimmed, ignoreCase = true)
            matchesCategory && matchesQuery
        }
    }

    fun groupBySection(addons: List<Addon>): List<Pair<AddonCategory, List<Addon>>> {
        val order =
            listOf(
                AddonCategory.CATALOGS,
                AddonCategory.STREAMS,
                AddonCategory.SUBTITLES,
                AddonCategory.OTHER,
            )
        val grouped = addons.groupBy { primaryCategory(categoriesOf(it.manifest)) }
        return order.mapNotNull { cat ->
            val list = grouped[cat]
            if (!list.isNullOrEmpty()) cat to list else null
        }
    }

    fun categoryCounts(addons: List<Addon>): Map<AddonCategory, Int> {
        val counts = mutableMapOf<AddonCategory, Int>()
        for (addon in addons) {
            for (cat in categoriesOf(addon.manifest)) {
                counts[cat] = (counts[cat] ?: 0) + 1
            }
        }
        return counts
    }

    fun filterStore(
        entries: List<StoreAddon>,
        query: String,
        category: AddonCategory?,
        familyMode: Boolean,
        installedUrls: Set<String> = emptySet(),
    ): List<StoreAddon> {
        val trimmed = query.trim()
        return entries
            .filter { entry ->
                if (familyMode) {
                    if (entry.isAdult) return@filter false
                    if (isAdultText(entry.name) || isAdultText(entry.description)) {
                        return@filter false
                    }
                }
                val matchesCategory = category == null || category in entry.categories
                val matchesQuery =
                    trimmed.isEmpty() ||
                        entry.name.contains(trimmed, ignoreCase = true) ||
                        entry.description.contains(trimmed, ignoreCase = true) ||
                        entry.transportUrl.toDisplayHost().contains(trimmed, ignoreCase = true)
                matchesCategory && matchesQuery
            }.map { entry ->
                val installed = installedUrls.contains(entry.transportUrl)
                if (entry.isInstalled != installed) {
                    entry.copy(isInstalled = installed)
                } else {
                    entry
                }
            }
    }

    fun storeCategoryCounts(entries: List<StoreAddon>): Map<AddonCategory, Int> {
        val counts = mutableMapOf<AddonCategory, Int>()
        for (entry in entries) {
            for (cat in entry.categories) {
                counts[cat] = (counts[cat] ?: 0) + 1
            }
        }
        return counts
    }
}
