package com.nexus.stream.presentation.feature.home

import org.junit.Assert.assertEquals
import org.junit.Test

class HeroPagingTest {
    @Test
    fun `nextHeroPage advances to next page`() {
        assertEquals(1, nextHeroPage(current = 0, size = 5))
        assertEquals(2, nextHeroPage(current = 1, size = 5))
        assertEquals(3, nextHeroPage(current = 2, size = 5))
        assertEquals(4, nextHeroPage(current = 3, size = 5))
    }

    @Test
    fun `nextHeroPage wraps from last page to first`() {
        assertEquals(0, nextHeroPage(current = 4, size = 5))
    }

    @Test
    fun `nextHeroPage returns current when size is 1 or less`() {
        assertEquals(0, nextHeroPage(current = 0, size = 1))
        assertEquals(0, nextHeroPage(current = 0, size = 0))
        assertEquals(2, nextHeroPage(current = 2, size = 1))
    }

    @Test
    fun `formatHeroInfo joins all parts when present`() {
        val result =
            formatHeroInfo(
                releaseInfo = "2024",
                imdbRating = "8.6",
                genres = listOf("Action", "Adventure", "Sci-Fi", "Drama"),
            )
        assertEquals("2024 • ★ 8.6 • Action, Adventure, Sci-Fi", result)
    }

    @Test
    fun `formatHeroInfo omits missing parts without stray separators`() {
        // Missing releaseInfo
        val noYear =
            formatHeroInfo(
                releaseInfo = null,
                imdbRating = "8.0",
                genres = listOf("Comedy"),
            )
        assertEquals("★ 8.0 • Comedy", noYear)

        // Missing rating
        val noRating =
            formatHeroInfo(
                releaseInfo = "2023",
                imdbRating = null,
                genres = listOf("Horror"),
            )
        assertEquals("2023 • Horror", noRating)

        // Missing genres
        val noGenres =
            formatHeroInfo(
                releaseInfo = "2022",
                imdbRating = "7.5",
                genres = emptyList(),
            )
        assertEquals("2022 • ★ 7.5", noGenres)

        // All missing
        val allMissing =
            formatHeroInfo(
                releaseInfo = null,
                imdbRating = "",
                genres = listOf("", "   "),
            )
        assertEquals("", allMissing)
    }
}
