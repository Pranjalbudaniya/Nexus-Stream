package com.nexus.stream.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Test

class NameListsTest {
    @Test
    fun `cleanNames trims whitespace, drops blanks, removes case-insensitive duplicates and preserves order`() {
        val input = listOf(" Action ", "Comedy", "action", "", "  ", "COMEDY", "Drama", "\tThriller\n", "drama")
        val expected = listOf("Action", "Comedy", "Drama", "Thriller")
        assertEquals(expected, cleanNames(input))
    }

    @Test
    fun `cleanNames on empty list returns empty list`() {
        assertEquals(emptyList<String>(), cleanNames(emptyList()))
    }

    @Test
    fun `cleanNames preserves the first spelling encountered`() {
        val input = listOf("Sci-Fi", "sci-fi", "SCI-FI")
        assertEquals(listOf("Sci-Fi"), cleanNames(input))
    }
}
