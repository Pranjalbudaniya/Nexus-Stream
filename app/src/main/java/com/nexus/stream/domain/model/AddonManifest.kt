package com.nexus.stream.domain.model

data class AddonManifest(
    val id: String,
    val version: String,
    val name: String,
    val description: String,
    val logo: String?,
    val background: String?,
    val types: List<String>,
    val idPrefixes: List<String>?,
    val resources: List<ResourceSpec>,
    val catalogs: List<CatalogSpec>,
    val behaviorHints: ManifestHints,
    val addonCatalogs: List<CatalogSpec> = emptyList(),
) {
    val isAdult: Boolean get() = behaviorHints.adult
}

data class ResourceSpec(
    val name: String,
    val types: List<String>?,
    val idPrefixes: List<String>?,
)

data class CatalogSpec(
    val type: String,
    val id: String,
    val name: String,
    val extras: List<ExtraSpec>,
)

data class ExtraSpec(
    val name: String,
    val isRequired: Boolean,
    val options: List<String>,
)

data class ManifestHints(
    val adult: Boolean,
    val p2p: Boolean,
    val configurable: Boolean,
    val configurationRequired: Boolean,
)

fun AddonManifest.supports(
    resource: String,
    type: String,
    id: String,
): Boolean {
    if (resource == "catalog") {
        return catalogs.isNotEmpty()
    }
    val spec = resources.find { it.name == resource }
    val effectiveTypes = spec?.types ?: types
    val effectivePrefixes = spec?.idPrefixes ?: idPrefixes
    return spec != null && type in effectiveTypes &&
        (effectivePrefixes == null || effectivePrefixes.any { id.startsWith(it) })
}

val CatalogSpec.isBoardCatalog: Boolean
    get() = extras.none { it.isRequired }

val CatalogSpec.supportsSearch: Boolean
    get() = extras.any { it.name == "search" }

val CatalogSpec.isSearchOnly: Boolean
    get() = extras.any { it.name == "search" && it.isRequired }

val CatalogSpec.supportsSkip: Boolean
    get() = extras.any { it.name == "skip" }

val CatalogSpec.genreOptions: List<String>
    get() = extras.find { it.name == "genre" }?.options.orEmpty()
