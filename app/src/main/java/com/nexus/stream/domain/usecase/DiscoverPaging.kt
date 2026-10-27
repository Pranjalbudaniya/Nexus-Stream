package com.nexus.stream.domain.usecase

import com.nexus.stream.domain.model.DiscoverCatalog
import com.nexus.stream.domain.model.MetaPreview

const val MAX_PAGES = 40

data class PagingState(
    val items: List<MetaPreview>,
    val pagesLoaded: Int,
    val hasMore: Boolean,
)

fun discoverExtra(
    catalog: DiscoverCatalog,
    genre: String?,
    skip: Int,
): Map<String, String> {
    val extra = mutableMapOf<String, String>()
    if (genre != null) {
        extra["genre"] = genre
    }
    if (catalog.supportsSkip && skip > 0) {
        extra["skip"] = skip.toString()
    }
    return extra
}

fun applyPage(
    state: PagingState,
    page: List<MetaPreview>,
    catalog: DiscoverCatalog,
): PagingState {
    val merged = mergeCatalogPage(state.items, page)
    val pagesLoaded = state.pagesLoaded + 1
    val hasMore = catalog.supportsSkip && merged.addedCount > 0 && pagesLoaded < MAX_PAGES
    return PagingState(
        items = merged.items,
        pagesLoaded = pagesLoaded,
        hasMore = hasMore,
    )
}

fun nextSkip(state: PagingState): Int = state.items.size
