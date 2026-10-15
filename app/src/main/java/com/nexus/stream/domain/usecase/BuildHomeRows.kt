package com.nexus.stream.domain.usecase

import com.nexus.stream.domain.model.Addon
import com.nexus.stream.domain.model.HomeRowSpec
import com.nexus.stream.domain.model.isBoardCatalog
import java.util.Locale
import javax.inject.Inject

class BuildHomeRows
    @Inject
    constructor() {
        operator fun invoke(addons: List<Addon>): List<HomeRowSpec> {
            val enabledAddons = addons.filter { it.enabled }.sortedBy { it.position }
            val seenKeys = mutableSetOf<String>()
            val candidates = mutableListOf<CandidateSpec>()

            for (addon in enabledAddons) {
                val boardCatalogs = addon.manifest.catalogs.filter { it.isBoardCatalog }
                for (catalog in boardCatalogs) {
                    val key = "${addon.transportUrl}|${catalog.type}|${catalog.id}"
                    if (seenKeys.add(key)) {
                        val catalogName = catalog.name.ifBlank { catalog.id }
                        val typeLabel = formatTypeLabel(catalog.type)
                        val baseTitle = "$catalogName - $typeLabel"
                        candidates.add(
                            CandidateSpec(
                                key = key,
                                baseTitle = baseTitle,
                                transportUrl = addon.transportUrl,
                                type = catalog.type,
                                catalogId = catalog.id,
                                addonName = addon.manifest.name,
                            ),
                        )
                    }
                }
            }

            val titleCounts = candidates.groupingBy { it.baseTitle }.eachCount()

            return candidates.map { candidate ->
                val finalTitle =
                    if ((titleCounts[candidate.baseTitle] ?: 0) > 1) {
                        "${candidate.baseTitle} (${candidate.addonName})"
                    } else {
                        candidate.baseTitle
                    }
                HomeRowSpec(
                    key = candidate.key,
                    title = finalTitle,
                    transportUrl = candidate.transportUrl,
                    type = candidate.type,
                    catalogId = candidate.catalogId,
                )
            }
        }

        private fun formatTypeLabel(type: String): String =
            when (type.lowercase(Locale.ROOT)) {
                "movie" -> {
                    "Movies"
                }

                "series" -> {
                    "Series"
                }

                "channel" -> {
                    "Channels"
                }

                "tv" -> {
                    "TV"
                }

                else -> {
                    type.replaceFirstChar {
                        if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString()
                    }
                }
            }

        private data class CandidateSpec(
            val key: String,
            val baseTitle: String,
            val transportUrl: String,
            val type: String,
            val catalogId: String,
            val addonName: String,
        )
    }
