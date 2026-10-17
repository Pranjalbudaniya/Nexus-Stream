package com.nexus.stream.domain.model

enum class AddonCategory {
    CATALOGS,
    STREAMS,
    SUBTITLES,
    OTHER,
}

fun categoriesOf(manifest: AddonManifest): Set<AddonCategory> {
    val categories = mutableSetOf<AddonCategory>()
    if (manifest.catalogs.isNotEmpty()) {
        categories.add(AddonCategory.CATALOGS)
    }
    if (manifest.resources.any { it.name == "stream" }) {
        categories.add(AddonCategory.STREAMS)
    }
    if (manifest.resources.any { it.name == "subtitles" }) {
        categories.add(AddonCategory.SUBTITLES)
    }
    if (categories.isEmpty()) {
        categories.add(AddonCategory.OTHER)
    }
    return categories
}

fun primaryCategory(categories: Set<AddonCategory>): AddonCategory =
    when {
        AddonCategory.CATALOGS in categories -> AddonCategory.CATALOGS
        AddonCategory.STREAMS in categories -> AddonCategory.STREAMS
        AddonCategory.SUBTITLES in categories -> AddonCategory.SUBTITLES
        else -> AddonCategory.OTHER
    }
