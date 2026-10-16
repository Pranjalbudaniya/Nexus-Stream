package com.nexus.stream.domain.usecase

import com.nexus.stream.domain.model.ContinueRow
import com.nexus.stream.domain.model.ContinueWatchingItem
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
}
