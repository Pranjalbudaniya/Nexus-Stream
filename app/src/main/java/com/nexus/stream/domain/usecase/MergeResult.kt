package com.nexus.stream.domain.usecase

import com.nexus.stream.domain.model.MetaPreview

data class MergeResult(
    val items: List<MetaPreview>,
    val addedCount: Int,
)

fun mergeCatalogPage(
    existing: List<MetaPreview>,
    page: List<MetaPreview>,
): MergeResult {
    val seenIds = existing.mapTo(mutableSetOf()) { it.id }
    val newItems = mutableListOf<MetaPreview>()
    for (item in page) {
        if (seenIds.add(item.id)) {
            newItems.add(item)
        }
    }
    return MergeResult(
        items = existing + newItems,
        addedCount = newItems.size,
    )
}
