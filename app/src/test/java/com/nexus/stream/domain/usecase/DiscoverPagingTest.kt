package com.nexus.stream.domain.usecase

import com.nexus.stream.domain.model.DiscoverCatalog
import com.nexus.stream.domain.model.MetaPreview
import com.nexus.stream.domain.model.PosterShape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoverPagingTest {
    @Test
    fun PG1_extra_firstPage_hasNoSkip() {
        val catalog = createCatalog(supportsSkip = true)
        val extra = discoverExtra(catalog = catalog, genre = null, skip = 0)

        assertTrue(extra.isEmpty())
    }

    @Test
    fun PG2_extra_genreAndSkip_areBothSent() {
        val catalog = createCatalog(supportsSkip = true)
        val extra = discoverExtra(catalog = catalog, genre = "Action", skip = 100)

        assertEquals(mapOf("genre" to "Action", "skip" to "100"), extra)
    }

    @Test
    fun PG3_extra_catalogWithoutSkip_neverSendsSkip() {
        val catalog = createCatalog(supportsSkip = false)
        val extra = discoverExtra(catalog = catalog, genre = "Action", skip = 100)

        assertEquals(mapOf("genre" to "Action"), extra)
    }

    @Test
    fun PG4_extra_nullGenre_isNotSent() {
        val catalog = createCatalog(supportsSkip = true)
        val extra = discoverExtra(catalog = catalog, genre = null, skip = 100)

        assertEquals(mapOf("skip" to "100"), extra)
    }

    @Test
    fun PG5_nextSkip_isTheNumberOfItemsLoaded() {
        val catalog = createCatalog(supportsSkip = true)
        val initialState = PagingState(items = emptyList(), pagesLoaded = 0, hasMore = true)

        val page1 = (1..100).map { createMeta("id_$it") }
        val state1 = applyPage(initialState, page1, catalog)

        val page2 = (99..198).map { createMeta("id_$it") } // 2 existing (99, 100) + 98 new
        val state2 = applyPage(state1, page2, catalog)

        assertEquals(198, state2.items.size)
        assertEquals(198, nextSkip(state2))
    }

    @Test
    fun PG6_pageAddingNothing_stopsPaging() {
        val catalog = createCatalog(supportsSkip = true)
        val page1 = listOf(createMeta("id_1"), createMeta("id_2"))
        val state1 = applyPage(PagingState(emptyList(), 0, true), page1, catalog)

        val page2 = listOf(createMeta("id_1"), createMeta("id_2"))
        val state2 = applyPage(state1, page2, catalog)

        assertFalse(state2.hasMore)
        assertEquals(2, state2.items.size)
    }

    @Test
    fun PG7_emptyPage_stopsPaging() {
        val catalog = createCatalog(supportsSkip = true)
        val initialItems = listOf(createMeta("id_1"))
        val state1 = PagingState(items = initialItems, pagesLoaded = 1, hasMore = true)

        val state2 = applyPage(state1, emptyList(), catalog)

        assertFalse(state2.hasMore)
        assertEquals(initialItems, state2.items)
    }

    @Test
    fun PG8_catalogWithoutSkip_hasMoreIsFalseAfterTheFirstPage() {
        val catalog = createCatalog(supportsSkip = false)
        val page = (1..100).map { createMeta("id_$it") }

        val state = applyPage(PagingState(emptyList(), 0, true), page, catalog)

        assertFalse(state.hasMore)
        assertEquals(100, state.items.size)
    }

    @Test
    fun PG9_pageCap_stopsAtMaxPages() {
        val catalog = createCatalog(supportsSkip = true)
        var state = PagingState(emptyList(), 0, true)

        for (i in 1..MAX_PAGES) {
            val page = listOf(createMeta("id_page_${i}_item_1"))
            state = applyPage(state, page, catalog)
            if (i < MAX_PAGES) {
                assertTrue("Expected hasMore to be true at page $i", state.hasMore)
            }
        }

        assertFalse("Expected hasMore to be false at MAX_PAGES ($MAX_PAGES)", state.hasMore)
        assertEquals(MAX_PAGES, state.pagesLoaded)
    }

    @Test
    fun PG10_partialDuplicates_keepsGoing() {
        val catalog = createCatalog(supportsSkip = true)
        val page1 = (1..100).map { createMeta("id_$it") }
        val state1 = applyPage(PagingState(emptyList(), 0, true), page1, catalog)

        // 95 known ids (6..100) + 5 new ids (101..105)
        val page2 = (6..105).map { createMeta("id_$it") }
        val state2 = applyPage(state1, page2, catalog)

        assertTrue(state2.hasMore)
        assertEquals(105, state2.items.size)
    }

    @Test
    fun PG11_orderIsKept_newItemsAreAppended() {
        val catalog = createCatalog(supportsSkip = true)
        val itemA = createMeta("a")
        val itemB = createMeta("b")
        val itemC = createMeta("c")

        val state1 = applyPage(PagingState(emptyList(), 0, true), listOf(itemA, itemB), catalog)
        val state2 = applyPage(state1, listOf(itemB, itemC), catalog)

        assertEquals(listOf(itemA, itemB, itemC), state2.items)
    }

    private fun createCatalog(supportsSkip: Boolean): DiscoverCatalog =
        DiscoverCatalog(
            key = "https://example.com/manifest.json|movie|top",
            type = "movie",
            catalogId = "top",
            transportUrl = "https://example.com/manifest.json",
            title = "Top",
            genre = null,
            supportsSkip = supportsSkip,
        )

    private fun createMeta(id: String): MetaPreview =
        MetaPreview(
            id = id,
            type = "movie",
            name = "Title $id",
            poster = null,
            posterShape = PosterShape.POSTER,
            background = null,
            logo = null,
            description = null,
            releaseInfo = null,
            imdbRating = null,
            genres = emptyList(),
        )
}
