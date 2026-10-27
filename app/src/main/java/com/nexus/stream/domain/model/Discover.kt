package com.nexus.stream.domain.model

data class DiscoverCatalog(
    val key: String,
    val type: String,
    val catalogId: String,
    val transportUrl: String,
    val title: String,
    val genre: GenreSpec?,
    val supportsSkip: Boolean,
)

data class GenreSpec(
    val options: List<String>,
    val required: Boolean,
)

data class DiscoverSelection(
    val type: String,
    val catalogKey: String,
    val genre: String?,
)

enum class GenreKind {
    GENRE,
    YEAR,
}

sealed interface TypeDisplay {
    data object Movie : TypeDisplay

    data object Series : TypeDisplay

    data class Custom(
        val label: String,
    ) : TypeDisplay
}
