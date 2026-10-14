package com.nexus.stream.domain.usecase

import com.nexus.stream.domain.model.MetaPreview
import com.nexus.stream.domain.model.PosterShape
import org.junit.Assert.assertEquals
import org.junit.Test

class MergeCatalogPageTest {
    private fun createPreview(
        id: String,
        name: String = id,
    ): MetaPreview =
        MetaPreview(
            id = id,
            type = "movie",
            name = name,
            poster = null,
            posterShape = PosterShape.POSTER,
            background = null,
            logo = null,
            description = null,
            releaseInfo = null,
            imdbRating = null,
            genres = emptyList(),
        )

    @Test
    fun mergeCatalogPage_appendsOnlyNewIdsAndKeepsOrder() {
        val existing = listOf(createPreview("1"), createPreview("2"))
        val page = listOf(createPreview("3"), createPreview("4"))

        val result = mergeCatalogPage(existing, page)

        assertEquals(4, result.items.size)
        assertEquals(listOf("1", "2", "3", "4"), result.items.map { it.id })
        assertEquals(2, result.addedCount)
    }

    @Test
    fun mergeCatalogPage_emptyPage_givesZeroAddedCount() {
        val existing = listOf(createPreview("1"), createPreview("2"))
        val result = mergeCatalogPage(existing, emptyList())

        assertEquals(2, result.items.size)
        assertEquals(0, result.addedCount)
    }

    @Test
    fun mergeCatalogPage_repeatingExistingIds_givesZeroAddedCount() {
        val existing = listOf(createPreview("1"), createPreview("2"))
        val page = listOf(createPreview("1"), createPreview("2"))

        val result = mergeCatalogPage(existing, page)

        assertEquals(2, result.items.size)
        assertEquals(listOf("1", "2"), result.items.map { it.id })
        assertEquals(0, result.addedCount)
    }

    @Test
    fun mergeCatalogPage_partialOverlap_addsOnlyNewOnes() {
        val existing = listOf(createPreview("1"), createPreview("2"))
        val page = listOf(createPreview("2"), createPreview("3"), createPreview("1"), createPreview("4"))

        val result = mergeCatalogPage(existing, page)

        assertEquals(4, result.items.size)
        assertEquals(listOf("1", "2", "3", "4"), result.items.map { it.id })
        assertEquals(2, result.addedCount)
    }
}
