package com.nexus.stream.presentation.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TopLevelDestinationTest {
    @Test
    fun TN1_order_isHomeDiscoverLibraryDownloads() {
        val routes = TopLevelDestination.entries.map { it.route }
        assertEquals(listOf(Home, Discover, Library, Downloads), routes)
    }

    @Test
    fun TN2_exactlyFourEntries_noSearch() {
        assertEquals(4, TopLevelDestination.entries.size)
        assertFalse(TopLevelDestination.entries.any { it.name == "SEARCH" })
    }

    @Test
    fun TN3_routesAreDistinct() {
        val routeClasses = TopLevelDestination.entries.map { it.route::class }
        assertEquals(4, routeClasses.distinct().size)
        assertTrue(TopLevelDestination.entries.any { it.route == Downloads })
    }
}
