package com.nexus.stream.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CachePolicyTest {
    @Test
    fun CP1_totalBelowLimit_returnsEmpty() {
        val entries =
            listOf(
                CacheEntry("a", 100L, 1000L),
                CacheEntry("b", 200L, 2000L),
            )
        val plan = evictionPlan(entries, limitBytes = 500L, inUse = emptySet())
        assertTrue(plan.isEmpty())
    }

    @Test
    fun CP2_totalExactlyAtLimit_returnsEmpty() {
        val entries =
            listOf(
                CacheEntry("a", 100L, 1000L),
                CacheEntry("b", 200L, 2000L),
            )
        val plan = evictionPlan(entries, limitBytes = 300L, inUse = emptySet())
        assertTrue(plan.isEmpty())
    }

    @Test
    fun CP3_overLimit_oldestFirstStopsAsSoonAsRestFits() {
        val entries =
            listOf(
                CacheEntry("c", 100L, 3000L),
                CacheEntry("a", 150L, 1000L),
                CacheEntry("b", 200L, 2000L),
            )
        // Total = 450, limit = 250.
        // Candidates sorted by lastUsed: a (1000), b (2000), c (3000).
        // Evict a (150) -> remaining = 300 > 250.
        // Evict b (200) -> remaining = 100 <= 250 -> stop.
        val plan = evictionPlan(entries, limitBytes = 250L, inUse = emptySet())
        assertEquals(listOf("a", "b"), plan)
    }

    @Test
    fun CP4_oldestEntryInUse_skippedNeverReturnedStillCountedInTotal() {
        val entries =
            listOf(
                CacheEntry("a", 200L, 1000L), // in use
                CacheEntry("b", 100L, 2000L),
                CacheEntry("c", 100L, 3000L),
            )
        // Total = 400, limit = 250.
        // Candidates not in use: b (2000), c (3000).
        // Evict b (100) -> remaining = 300 > 250.
        // Evict c (100) -> remaining = 200 <= 250 -> stop.
        val plan = evictionPlan(entries, limitBytes = 250L, inUse = setOf("a"))
        assertEquals(listOf("b", "c"), plan)
    }

    @Test
    fun CP5_inUseEntriesAloneExceedLimit_everyOtherEntryReturnedNoException() {
        val entries =
            listOf(
                CacheEntry("inUse1", 500L, 1000L),
                CacheEntry("idle1", 100L, 2000L),
                CacheEntry("idle2", 200L, 3000L),
            )
        // Total = 800, limit = 400. In-use alone is 500 > 400.
        val plan = evictionPlan(entries, limitBytes = 400L, inUse = setOf("inUse1"))
        assertEquals(listOf("idle1", "idle2"), plan)
    }

    @Test
    fun CP6_oneIdleEntryBiggerThanLimit_returnedAfterOlderOnesOrAlone() {
        val entries =
            listOf(
                CacheEntry("smallOld", 50L, 1000L),
                CacheEntry("hugeNew", 1000L, 2000L),
            )
        // Total = 1050, limit = 200.
        // smallOld evicted -> remaining = 1000 > 200.
        // hugeNew evicted -> remaining = 0 <= 200.
        val plan = evictionPlan(entries, limitBytes = 200L, inUse = emptySet())
        assertEquals(listOf("smallOld", "hugeNew"), plan)

        // Lone candidate bigger than limit
        val lone = listOf(CacheEntry("loneHuge", 1000L, 1000L))
        val lonePlan = evictionPlan(lone, limitBytes = 200L, inUse = emptySet())
        assertEquals(listOf("loneHuge"), lonePlan)
    }

    @Test
    fun CP7_equalLastUsedMs_orderByTierNameAscending() {
        val entries =
            listOf(
                CacheEntry("zeta", 100L, 1000L),
                CacheEntry("alpha", 100L, 1000L),
                CacheEntry("beta", 100L, 1000L),
            )
        // Total = 300, limit = 150. Need to evict 2 entries.
        // Tied by lastUsedMs -> alphabetical: alpha, beta, zeta.
        val plan = evictionPlan(entries, limitBytes = 150L, inUse = emptySet())
        assertEquals(listOf("alpha", "beta"), plan)
    }

    @Test
    fun CP8_emptyListReturnsEmpty_limitZeroReturnsEveryIdleEntry() {
        val emptyPlan = evictionPlan(emptyList(), limitBytes = 0L, inUse = emptySet())
        assertTrue(emptyPlan.isEmpty())

        val entries =
            listOf(
                CacheEntry("a", 100L, 1000L),
                CacheEntry("b", 200L, 2000L),
                CacheEntry("inUse", 300L, 500L),
            )
        val allIdlePlan = evictionPlan(entries, limitBytes = 0L, inUse = setOf("inUse"))
        assertEquals(listOf("a", "b"), allIdlePlan)
    }

    @Test
    fun CP9_negativeSizeCountsAsZero_twoSizesNearMaxLongDoNotOverflow() {
        val negative = listOf(CacheEntry("neg", -100L, 1000L))
        val plan = evictionPlan(negative, limitBytes = 100L, inUse = emptySet())
        assertTrue(plan.isEmpty())

        val huge =
            listOf(
                CacheEntry("h1", Long.MAX_VALUE - 100L, 1000L),
                CacheEntry("h2", Long.MAX_VALUE - 100L, 2000L),
            )
        // Should saturate without throwing ArithmeticException or overflowing to negative
        val hugePlan = evictionPlan(huge, limitBytes = 1000L, inUse = emptySet())
        assertEquals(listOf("h1", "h2"), hugePlan)
    }

    @Test
    fun CP10_cacheLimitBytes_computesProperlyWithoutOverflow() {
        val gib = 1024L * 1024L * 1024L
        assertEquals(2L * gib, cacheLimitBytes(2))
        assertEquals(1L * gib, cacheLimitBytes(0))
        assertEquals(1L * gib, cacheLimitBytes(-5))

        val maxResult = cacheLimitBytes(Int.MAX_VALUE)
        assertTrue(maxResult > 0)
        assertEquals(Int.MAX_VALUE.toLong() * gib, maxResult)
    }
}
