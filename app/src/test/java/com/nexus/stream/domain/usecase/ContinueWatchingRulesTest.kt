package com.nexus.stream.domain.usecase

import com.nexus.stream.domain.model.ContinueKind
import com.nexus.stream.domain.model.ContinueRow
import com.nexus.stream.domain.model.ContinueWatchingItem
import com.nexus.stream.domain.model.ListStatus
import com.nexus.stream.domain.model.progress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinueWatchingRulesTest {
    private fun createRow(
        metaId: String,
        positionMs: Long = 120_000L,
        watched: Boolean = false,
        updatedAt: Long = 1000L,
        type: String = "movie",
    ): ContinueRow =
        ContinueRow(
            metaId = metaId,
            type = type,
            name = "Title $metaId",
            poster = "https://example.com/poster_$metaId.jpg",
            videoId = metaId,
            season = null,
            episode = null,
            positionMs = positionMs,
            durationMs = 600_000L,
            watched = watched,
            updatedAt = updatedAt,
        )

    @Test
    fun `movie under 60 s hidden, exactly 60 s hidden, 61 s shown`() {
        val rowUnder60 = createRow(metaId = "m1", positionMs = 59_999L)
        val rowExact60 = createRow(metaId = "m2", positionMs = 60_000L)
        val rowOver60 = createRow(metaId = "m3", positionMs = 60_001L)

        val result = ContinueWatchingRules(listOf(rowUnder60, rowExact60, rowOver60))

        assertEquals(1, result.size)
        assertEquals("m3", result[0].metaId)
    }

    @Test
    fun `watched movie is hidden`() {
        val watchedMovie = createRow(metaId = "m1", watched = true)
        val unwatchedMovie = createRow(metaId = "m2", watched = false)

        val result = ContinueWatchingRules(listOf(watchedMovie, unwatchedMovie))

        assertEquals(1, result.size)
        assertEquals("m2", result[0].metaId)
    }

    @Test
    fun `a series latest unwatched episode is shown with season and episode`() {
        val seriesRow =
            createRow(
                metaId = "s1",
                positionMs = 30_000L,
                type = "series",
            ).copy(season = 2, episode = 5)

        val result = ContinueWatchingRules(listOf(seriesRow))

        assertEquals(1, result.size)
        assertEquals("s1", result[0].metaId)
        assertEquals(2, result[0].season)
        assertEquals(5, result[0].episode)
    }

    @Test
    fun `a series whose latest row is watched is hidden`() {
        val seriesWatched =
            createRow(
                metaId = "s1",
                watched = true,
                updatedAt = 2000L,
                type = "series",
            ).copy(season = 1, episode = 1)
        val seriesOlderUnwatched =
            createRow(
                metaId = "s1",
                watched = false,
                updatedAt = 1000L,
                type = "series",
            ).copy(season = 1, episode = 1)

        val result = ContinueWatchingRules(listOf(seriesWatched, seriesOlderUnwatched))

        assertTrue(result.isEmpty())
    }

    @Test
    fun `the latest row per title wins when older rows differ`() {
        val olderRow =
            createRow(
                metaId = "s1",
                positionMs = 50_000L,
                updatedAt = 1000L,
                type = "series",
            ).copy(season = 1, episode = 1)
        val newerRow =
            createRow(
                metaId = "s1",
                positionMs = 80_000L,
                updatedAt = 2000L,
                type = "series",
            ).copy(season = 1, episode = 2)

        val result = ContinueWatchingRules(listOf(olderRow, newerRow))

        assertEquals(1, result.size)
        assertEquals(2, result[0].episode)
        assertEquals(80_000L, result[0].positionMs)
    }

    @Test
    fun `a row without a snapshot name is skipped`() {
        val rowNullName = createRow(metaId = "m1").copy(name = null)
        val rowEmptyName = createRow(metaId = "m2").copy(name = "")
        val rowBlankName = createRow(metaId = "m3").copy(name = "   ")
        val validRow = createRow(metaId = "m4").copy(name = "Valid Movie")

        val result = ContinueWatchingRules(listOf(rowNullName, rowEmptyName, rowBlankName, validRow))

        assertEquals(1, result.size)
        assertEquals("m4", result[0].metaId)
    }

    @Test
    fun `sorted by updatedAt descending and capped at 20`() {
        val rows =
            (1..30).map { i ->
                createRow(
                    metaId = "m$i",
                    updatedAt = (i * 100).toLong(),
                )
            }

        val result = ContinueWatchingRules(rows)

        assertEquals(20, result.size)
        assertEquals("m30", result[0].metaId)
        assertEquals("m11", result[19].metaId)
    }

    @Test
    fun `progress is clamped to 0 to 1 and null for durationMs less than or equal to 0`() {
        val normalItem =
            ContinueWatchingItem(
                metaId = "1",
                type = "movie",
                name = "Test",
                poster = null,
                videoId = "1",
                season = null,
                episode = null,
                positionMs = 50L,
                durationMs = 100L,
                updatedAt = 100L,
            )
        assertEquals(0.5f, normalItem.progress)

        val overItem = normalItem.copy(positionMs = 150L, durationMs = 100L)
        assertEquals(1.0f, overItem.progress)

        val underItem = normalItem.copy(positionMs = -10L, durationMs = 100L)
        assertEquals(0.0f, underItem.progress)

        val zeroDuration = normalItem.copy(durationMs = 0L)
        assertNull(zeroDuration.progress)

        val negativeDuration = normalItem.copy(durationMs = -50L)
        assertNull(negativeDuration.progress)
    }

    @Test
    fun UN1_seriesLatestRowWatched_statusWatching_giveUpNextItem() {
        val now = 100_000_000L
        val updatedAt = now - 5L * 24 * 60 * 60 * 1000L
        val row =
            createRow(
                metaId = "s1",
                watched = true,
                updatedAt = updatedAt,
                type = "series",
            ).copy(
                videoId = "s1:1:4",
                season = 1,
                episode = 4,
                inWatchlist = true,
                listStatus = ListStatus.WATCHING,
            )

        val result = ContinueWatchingRules(listOf(row), now)

        assertEquals(1, result.size)
        val item = result[0]
        assertEquals(ContinueKind.UP_NEXT, item.kind)
        assertEquals("s1:1:4", item.videoId)
        assertEquals(1, item.season)
        assertEquals(4, item.episode)
        assertEquals(0L, item.positionMs)
        assertEquals(0L, item.durationMs)
        assertNull(item.progress)
    }

    @Test
    fun UN2_otherStatusesAndNotInWatchlist_giveNothing() {
        val now = 100_000_000L
        val updatedAt = now - 5L * 24 * 60 * 60 * 1000L
        val otherStatuses =
            listOf(
                ListStatus.PLAN_TO_WATCH,
                ListStatus.ON_HOLD,
                ListStatus.DROPPED,
                ListStatus.COMPLETED,
            )

        for (status in otherStatuses) {
            val row =
                createRow(
                    metaId = "s1",
                    watched = true,
                    updatedAt = updatedAt,
                    type = "series",
                ).copy(
                    season = 1,
                    episode = 1,
                    inWatchlist = true,
                    listStatus = status,
                )
            val result = ContinueWatchingRules(listOf(row), now)
            assertTrue("Expected empty for status $status", result.isEmpty())
        }

        val notInWatchlistRow =
            createRow(
                metaId = "s1",
                watched = true,
                updatedAt = updatedAt,
                type = "series",
            ).copy(
                season = 1,
                episode = 1,
                inWatchlist = false,
                listStatus = ListStatus.WATCHING,
            )
        assertTrue(ContinueWatchingRules(listOf(notInWatchlistRow), now).isEmpty())

        val nullStatusRow =
            createRow(
                metaId = "s1",
                watched = true,
                updatedAt = updatedAt,
                type = "series",
            ).copy(
                season = 1,
                episode = 1,
                inWatchlist = true,
                listStatus = null,
            )
        assertTrue(ContinueWatchingRules(listOf(nullStatusRow), now).isEmpty())
    }

    @Test
    fun UN3_ageBoundary_sixtyDaysIn_sixtyDaysAndOneMsOut() {
        val now = 200_000_000_000L
        val rowAtLimit =
            createRow(
                metaId = "s1",
                watched = true,
                updatedAt = now - ContinueWatchingRules.UP_NEXT_MAX_AGE_MS,
                type = "series",
            ).copy(
                season = 1,
                episode = 1,
                inWatchlist = true,
                listStatus = ListStatus.WATCHING,
            )
        val resultAtLimit = ContinueWatchingRules(listOf(rowAtLimit), now)
        assertEquals(1, resultAtLimit.size)
        assertEquals(ContinueKind.UP_NEXT, resultAtLimit[0].kind)

        val rowPastLimit =
            createRow(
                metaId = "s2",
                watched = true,
                updatedAt = now - (ContinueWatchingRules.UP_NEXT_MAX_AGE_MS + 1L),
                type = "series",
            ).copy(
                season = 1,
                episode = 1,
                inWatchlist = true,
                listStatus = ListStatus.WATCHING,
            )
        val resultPastLimit = ContinueWatchingRules(listOf(rowPastLimit), now)
        assertTrue(resultPastLimit.isEmpty())
    }

    @Test
    fun UN4_watchedMovie_givesNothing() {
        val now = 100_000_000L
        val movieRow =
            createRow(
                metaId = "m1",
                watched = true,
                updatedAt = now - 1000L,
                type = "movie",
            ).copy(
                inWatchlist = true,
                listStatus = ListStatus.WATCHING,
            )
        val result = ContinueWatchingRules(listOf(movieRow), now)
        assertTrue(result.isEmpty())
    }

    @Test
    fun UN5_latestRowInProgress_givesResumeItem_notUpNext() {
        val now = 100_000_000L
        val olderWatched =
            createRow(
                metaId = "s1",
                watched = true,
                updatedAt = now - 10_000L,
                type = "series",
            ).copy(
                season = 1,
                episode = 1,
                inWatchlist = true,
                listStatus = ListStatus.WATCHING,
            )
        val newerInProgress =
            createRow(
                metaId = "s1",
                positionMs = 50_000L,
                watched = false,
                updatedAt = now - 5_000L,
                type = "series",
            ).copy(
                season = 1,
                episode = 2,
                inWatchlist = true,
                listStatus = ListStatus.WATCHING,
            )
        val result = ContinueWatchingRules(listOf(olderWatched, newerInProgress), now)
        assertEquals(1, result.size)
        assertEquals(ContinueKind.RESUME, result[0].kind)
        assertEquals(2, result[0].episode)
    }

    @Test
    fun UN6_mixedList_sortedByUpdatedAtDescending() {
        val now = 100_000_000L
        val resumeSeries =
            createRow(
                metaId = "s1",
                positionMs = 30_000L,
                watched = false,
                updatedAt = now - 1000L,
                type = "series",
            ).copy(
                season = 1,
                episode = 1,
            )
        val upNextSeries =
            createRow(
                metaId = "s2",
                watched = true,
                updatedAt = now - 2000L,
                type = "series",
            ).copy(
                season = 1,
                episode = 3,
                inWatchlist = true,
                listStatus = ListStatus.WATCHING,
            )
        val resumeMovie =
            createRow(
                metaId = "m1",
                positionMs = 120_000L,
                watched = false,
                updatedAt = now - 500L,
                type = "movie",
            )
        val result = ContinueWatchingRules(listOf(resumeSeries, upNextSeries, resumeMovie), now)
        assertEquals(3, result.size)
        assertEquals("m1", result[0].metaId)
        assertEquals(ContinueKind.RESUME, result[0].kind)
        assertEquals("s1", result[1].metaId)
        assertEquals(ContinueKind.RESUME, result[1].kind)
        assertEquals("s2", result[2].metaId)
        assertEquals(ContinueKind.UP_NEXT, result[2].kind)
    }

    @Test
    fun UN7_latestRowIsWatchedSpecial_givesNothing() {
        val now = 100_000_000L
        val specialRow =
            createRow(
                metaId = "s1",
                watched = true,
                updatedAt = now - 1000L,
                type = "series",
            ).copy(
                season = 0,
                episode = 1,
                inWatchlist = true,
                listStatus = ListStatus.WATCHING,
            )
        val result = ContinueWatchingRules(listOf(specialRow), now)
        assertTrue(result.isEmpty())
    }

    @Test
    fun UN8_blankName_givesNothing() {
        val now = 100_000_000L
        val nullNameRow =
            createRow(
                metaId = "s1",
                watched = true,
                updatedAt = now - 1000L,
                type = "series",
            ).copy(
                name = null,
                season = 1,
                episode = 1,
                inWatchlist = true,
                listStatus = ListStatus.WATCHING,
            )
        val blankNameRow =
            createRow(
                metaId = "s2",
                watched = true,
                updatedAt = now - 1000L,
                type = "series",
            ).copy(
                name = "   ",
                season = 1,
                episode = 1,
                inWatchlist = true,
                listStatus = ListStatus.WATCHING,
            )
        assertTrue(ContinueWatchingRules(listOf(nullNameRow), now).isEmpty())
        assertTrue(ContinueWatchingRules(listOf(blankNameRow), now).isEmpty())
    }

    @Test
    fun UN9_nullSeason_countsAsRegular() {
        val now = 100_000_000L
        val nullSeasonRow =
            createRow(
                metaId = "s1",
                watched = true,
                updatedAt = now - 1000L,
                type = "series",
            ).copy(
                season = null,
                episode = 1,
                inWatchlist = true,
                listStatus = ListStatus.WATCHING,
            )
        val result = ContinueWatchingRules(listOf(nullSeasonRow), now)
        assertEquals(1, result.size)
        assertEquals(ContinueKind.UP_NEXT, result[0].kind)
    }

    @Test
    fun UN10_capOfTwenty_appliesToTheMergedList() {
        val now = 100_000_000L
        val resumeSeries =
            (1..15).map { i ->
                createRow(
                    metaId = "rs$i",
                    positionMs = 10_000L,
                    watched = false,
                    updatedAt = now - (i * 1000L),
                    type = "series",
                ).copy(
                    season = 1,
                    episode = i,
                )
            }
        val upNextSeries =
            (1..10).map { i ->
                createRow(
                    metaId = "un$i",
                    watched = true,
                    updatedAt = now - (15_000L + i * 1000L),
                    type = "series",
                ).copy(
                    season = 1,
                    episode = i,
                    inWatchlist = true,
                    listStatus = ListStatus.WATCHING,
                )
            }
        val result = ContinueWatchingRules(resumeSeries + upNextSeries, now)
        assertEquals(20, result.size)
        assertEquals("rs1", result[0].metaId)
        assertEquals("un5", result[19].metaId)
    }

    @Test
    fun UN11_existingRules_unchanged() {
        val rowUnder60 = createRow(metaId = "m1", positionMs = 59_999L)
        val rowOver60 = createRow(metaId = "m2", positionMs = 60_001L)
        val seriesUnwatched = createRow(metaId = "s1", positionMs = 10L, type = "series")
        val blankName = createRow(metaId = "s2", type = "series").copy(name = "  ")

        val result = ContinueWatchingRules(listOf(rowUnder60, rowOver60, seriesUnwatched, blankName), now = 1000L)
        assertEquals(2, result.size)
        assertEquals(setOf("m2", "s1"), result.map { it.metaId }.toSet())
    }
}
