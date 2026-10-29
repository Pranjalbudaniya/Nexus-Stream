package com.nexus.stream.domain.usecase

import com.nexus.stream.domain.model.ListStatus
import com.nexus.stream.domain.model.WatchlistEntry
import com.nexus.stream.domain.model.WatchlistSort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LibraryRulesTest {
    private fun createEntry(
        metaId: String,
        name: String = "Title $metaId",
        type: String = "movie",
        status: ListStatus = ListStatus.PLAN_TO_WATCH,
        addedAt: Long = 1000L,
    ): WatchlistEntry =
        WatchlistEntry(
            metaId = metaId,
            type = type,
            name = name,
            poster = "https://example.com/poster.jpg",
            background = null,
            status = status,
            addedAt = addedAt,
        )

    @Test
    fun WL1_filterByStatus_keepsOnlyThatStatus() {
        val entries =
            listOf(
                createEntry("1", status = ListStatus.PLAN_TO_WATCH),
                createEntry("2", status = ListStatus.WATCHING),
                createEntry("3", status = ListStatus.COMPLETED),
            )
        val filtered = filterWatchlist(entries, status = ListStatus.WATCHING, type = null)
        assertEquals(listOf("2"), filtered.map { it.metaId })
    }

    @Test
    fun WL2_filterByType_keepsOnlyThatType() {
        val entries =
            listOf(
                createEntry("1", type = "movie"),
                createEntry("2", type = "series"),
                createEntry("3", type = "movie"),
            )
        val filtered = filterWatchlist(entries, status = null, type = "series")
        assertEquals(listOf("2"), filtered.map { it.metaId })
    }

    @Test
    fun WL3_filterByBoth_isTheIntersection() {
        val entries =
            listOf(
                createEntry("1", type = "movie", status = ListStatus.WATCHING),
                createEntry("2", type = "series", status = ListStatus.WATCHING),
                createEntry("3", type = "movie", status = ListStatus.COMPLETED),
            )
        val filtered = filterWatchlist(entries, status = ListStatus.WATCHING, type = "movie")
        assertEquals(listOf("1"), filtered.map { it.metaId })
    }

    @Test
    fun WL4_nullFilters_returnEverything() {
        val entries =
            listOf(
                createEntry("1", type = "movie", status = ListStatus.PLAN_TO_WATCH),
                createEntry("2", type = "series", status = ListStatus.WATCHING),
                createEntry("3", type = "movie", status = ListStatus.COMPLETED),
            )
        val filtered = filterWatchlist(entries, status = null, type = null)
        assertEquals(entries, filtered)
    }

    @Test
    fun WL5_sortRecentlyAdded_newestFirst_tiesByTitle() {
        val entries =
            listOf(
                createEntry("1", name = "B title", addedAt = 100L),
                createEntry("2", name = "A title", addedAt = 100L),
                createEntry("3", name = "C title", addedAt = 200L),
            )
        val sorted = sortWatchlist(entries, WatchlistSort.RECENTLY_ADDED)
        assertEquals(listOf("3", "2", "1"), sorted.map { it.metaId })
    }

    @Test
    fun WL6_sortTitleAz_isCaseInsensitive_tiesByMetaId() {
        val entries =
            listOf(
                createEntry("id2", name = "alien"),
                createEntry("id3", name = "Brazil"),
                createEntry("id1", name = "alien"),
            )
        val sorted = sortWatchlist(entries, WatchlistSort.TITLE_AZ)
        assertEquals(listOf("id1", "id2", "id3"), sorted.map { it.metaId })
    }

    @Test
    fun WL7_sortTitleZa_isTheReverseOfAz() {
        val entries =
            listOf(
                createEntry("id2", name = "alien"),
                createEntry("id3", name = "Brazil"),
                createEntry("id1", name = "alien"),
            )
        val sorted = sortWatchlist(entries, WatchlistSort.TITLE_ZA)
        assertEquals(listOf("id3", "id2", "id1"), sorted.map { it.metaId })
    }

    @Test
    fun WL8_facetCounts_statusCountsFollowTheTypeFilter() {
        val entries =
            listOf(
                createEntry("1", type = "movie", status = ListStatus.WATCHING),
                createEntry("2", type = "movie", status = ListStatus.WATCHING),
                createEntry("3", type = "movie", status = ListStatus.COMPLETED),
                createEntry("4", type = "movie", status = ListStatus.PLAN_TO_WATCH),
                createEntry("5", type = "series", status = ListStatus.WATCHING),
                createEntry("6", type = "series", status = ListStatus.COMPLETED),
            )
        val facets = facetCounts(entries, statusFilter = null, typeFilter = "series")
        assertEquals(2, facets.all)
        assertEquals(1, facets.byStatus[ListStatus.WATCHING])
        assertEquals(1, facets.byStatus[ListStatus.COMPLETED])
        assertEquals(0, facets.byStatus[ListStatus.PLAN_TO_WATCH])
    }

    @Test
    fun WL9_facetCounts_typeCountsFollowTheStatusFilter() {
        val entries =
            listOf(
                createEntry("1", type = "movie", status = ListStatus.WATCHING),
                createEntry("2", type = "movie", status = ListStatus.WATCHING),
                createEntry("3", type = "movie", status = ListStatus.COMPLETED),
                createEntry("4", type = "movie", status = ListStatus.PLAN_TO_WATCH),
                createEntry("5", type = "series", status = ListStatus.WATCHING),
                createEntry("6", type = "series", status = ListStatus.COMPLETED),
            )
        val facets = facetCounts(entries, statusFilter = ListStatus.WATCHING, typeFilter = null)
        assertEquals(2, facets.byType["movie"])
        assertEquals(1, facets.byType["series"])
    }

    @Test
    fun WL10_facetCounts_zeroStatusesAreListed() {
        val entries =
            listOf(
                createEntry("1", status = ListStatus.PLAN_TO_WATCH),
                createEntry("2", status = ListStatus.PLAN_TO_WATCH),
            )
        val facets = facetCounts(entries)
        assertEquals(5, facets.byStatus.size)
        assertEquals(2, facets.byStatus[ListStatus.PLAN_TO_WATCH])
        assertEquals(0, facets.byStatus[ListStatus.WATCHING])
        assertEquals(0, facets.byStatus[ListStatus.COMPLETED])
        assertEquals(0, facets.byStatus[ListStatus.ON_HOLD])
        assertEquals(0, facets.byStatus[ListStatus.DROPPED])
    }

    @Test
    fun WL11_resolveType_missingType_fallsBackToNull() {
        val resolved = resolveTypeFilter(selected = "series", available = listOf("movie"))
        assertNull(resolved)
    }

    @Test
    fun WL12_resolveType_presentType_isKept() {
        val resolved = resolveTypeFilter(selected = "series", available = listOf("movie", "series"))
        assertEquals("series", resolved)
    }

    @Test
    fun WL13_availableTypes_areMovieSeriesThenAlphabetical() {
        val types = listOf("tv", "series", "", "channel", "movie", "series")
        val available = availableTypes(types)
        assertEquals(listOf("movie", "series", "channel", "tv"), available)
    }

    @Test
    fun WL14_familyFilter_hidesAdultNames() {
        val entries =
            listOf(
                createEntry("1", name = "Dune"),
                createEntry("2", name = "Hot XXX Video"),
            )
        val filtered = filterFamily(entries, familyMode = true)
        assertEquals(listOf("Dune"), filtered.map { it.name })
    }

    @Test
    fun WL15_familyFilter_offKeepsEverything() {
        val entries =
            listOf(
                createEntry("1", name = "Dune"),
                createEntry("2", name = "Hot XXX Video"),
            )
        val filtered = filterFamily(entries, familyMode = false)
        assertEquals(listOf("Dune", "Hot XXX Video"), filtered.map { it.name })
    }
}
