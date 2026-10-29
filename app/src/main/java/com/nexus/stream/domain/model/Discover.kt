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

enum class FilterControl {
    Hidden,
    Text,
    Dropdown,
}

fun filterControlOf(entryCount: Int): FilterControl =
    when {
        entryCount <= 0 -> FilterControl.Hidden
        entryCount == 1 -> FilterControl.Text
        else -> FilterControl.Dropdown
    }
