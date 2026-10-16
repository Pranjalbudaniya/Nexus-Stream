package com.nexus.stream.domain.usecase

import com.nexus.stream.domain.model.AppError
import com.nexus.stream.domain.model.MetaPreview
import com.nexus.stream.domain.model.PosterShape
import com.nexus.stream.domain.model.RowState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildHeroTest {
    private fun createPreview(
        id: String,
        background: String? = "https://example.com/bg_$id.jpg",
    ): MetaPreview =
        MetaPreview(
            id = id,
            type = "movie",
            name = "Title $id",
            poster = "https://example.com/poster_$id.jpg",
            posterShape = PosterShape.POSTER,
            background = background,
            logo = null,
            description = "Desc $id",
            releaseInfo = "2024",
            imdbRating = "8.0",
            genres = listOf("Action"),
        )

    @Test
    fun `the first qualifying row wins even if a later row has more items`() {
        val row1 =
            RowState.Loaded(
                items =
                    listOf(
                        createPreview("1"),
                        createPreview("2"),
                        createPreview("3"),
                    ),
            )
        val row2 =
            RowState.Loaded(
                items = (4..10).map { createPreview(it.toString()) },
            )

        val result = BuildHero(listOf(row1, row2))

        assertEquals(3, result.size)
        assertEquals("1", result[0].id)
        assertEquals("2", result[1].id)
        assertEquals("3", result[2].id)
    }

    @Test
    fun `at most 5 items are returned`() {
        val row =
            RowState.Loaded(
                items = (1..8).map { createPreview(it.toString()) },
            )

        val result = BuildHero(listOf(row))

        assertEquals(5, result.size)
        assertEquals(listOf("1", "2", "3", "4", "5"), result.map { it.id })
    }

    @Test
    fun `items without a background are skipped`() {
        val row =
            RowState.Loaded(
                items =
                    listOf(
                        createPreview("1", background = "https://example.com/bg1.jpg"),
                        createPreview("2", background = null),
                        createPreview("3", background = ""),
                        createPreview("4", background = "   "),
                        createPreview("5", background = "https://example.com/bg5.jpg"),
                        createPreview("6", background = "https://example.com/bg6.jpg"),
                    ),
            )

        val result = BuildHero(listOf(row))

        assertEquals(3, result.size)
        assertEquals(listOf("1", "5", "6"), result.map { it.id })
    }

    @Test
    fun `a row with only 2 background items does not qualify and the next row is tried`() {
        val row1 =
            RowState.Loaded(
                items =
                    listOf(
                        createPreview("1", background = "https://example.com/bg1.jpg"),
                        createPreview("2", background = "https://example.com/bg2.jpg"),
                        createPreview("3", background = null),
                    ),
            )
        val row2 =
            RowState.Loaded(
                items =
                    listOf(
                        createPreview("4", background = "https://example.com/bg4.jpg"),
                        createPreview("5", background = "https://example.com/bg5.jpg"),
                        createPreview("6", background = "https://example.com/bg6.jpg"),
                    ),
            )

        val result = BuildHero(listOf(row1, row2))

        assertEquals(3, result.size)
        assertEquals(listOf("4", "5", "6"), result.map { it.id })
    }

    @Test
    fun `a Loading or Failed first row is skipped`() {
        val loadingRow = RowState.Loading
        val failedRow = RowState.Failed(AppError.Network)
        val row3 =
            RowState.Loaded(
                items =
                    listOf(
                        createPreview("1"),
                        createPreview("2"),
                        createPreview("3"),
                    ),
            )

        val result = BuildHero(listOf(loadingRow, failedRow, row3))

        assertEquals(3, result.size)
        assertEquals(listOf("1", "2", "3"), result.map { it.id })
    }

    @Test
    fun `none qualify gives an empty list`() {
        val row1 = RowState.Loading
        val row2 = RowState.Failed()
        val row3 =
            RowState.Loaded(
                items =
                    listOf(
                        createPreview("1", background = null),
                        createPreview("2", background = null),
                    ),
            )

        val result = BuildHero(listOf(row1, row2, row3))

        assertTrue(result.isEmpty())
    }

    @Test
    fun `order is preserved`() {
        val row =
            RowState.Loaded(
                items =
                    listOf(
                        createPreview("gamma"),
                        createPreview("alpha"),
                        createPreview("beta"),
                        createPreview("delta"),
                    ),
            )

        val result = BuildHero(listOf(row))

        assertEquals(listOf("gamma", "alpha", "beta", "delta"), result.map { it.id })
    }
}
