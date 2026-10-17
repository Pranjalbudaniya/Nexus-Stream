package com.nexus.stream.domain.model

enum class TypeFilter {
    ALL,
    DIRECT,
    TORRENT,
}

data class StreamFilters(
    val resolution: Int? = null,
    val addonKey: String? = null,
    val language: String? = null,
    val type: TypeFilter = TypeFilter.ALL,
    val cachedOnly: Boolean = false,
)
