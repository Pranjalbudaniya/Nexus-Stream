package com.nexus.stream.domain.model

data class AddonCapabilities(
    val hasCatalogs: Boolean,
    val hasStreams: Boolean,
)

fun List<Addon>.capabilities(): AddonCapabilities {
    val enabledAddons = filter { it.enabled }
    val hasCatalogs =
        enabledAddons.any { addon ->
            addon.manifest.catalogs.any { it.isBoardCatalog }
        }
    val hasStreams =
        enabledAddons.any { addon ->
            addon.manifest.resources.any { it.name == STREAM_RESOURCE_NAME }
        }
    return AddonCapabilities(
        hasCatalogs = hasCatalogs,
        hasStreams = hasStreams,
    )
}

val AddonManifest.isUsable: Boolean
    get() = resources.any { it.name in USABLE_RESOURCE_NAMES }

private const val STREAM_RESOURCE_NAME = "stream"

private val USABLE_RESOURCE_NAMES =
    setOf(
        "catalog",
        "meta",
        "stream",
        "subtitles",
    )
