package com.nexus.stream.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YoutubeLinkTest {
    @Test
    fun `valid ids produce YouTube watch URL`() {
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", youtubeWatchUrlOrNull("dQw4w9WgXcQ"))
        assertEquals("https://www.youtube.com/watch?v=abc-123_XYZ", youtubeWatchUrlOrNull("abc-123_XYZ"))
        assertEquals("https://www.youtube.com/watch?v=123456", youtubeWatchUrlOrNull("123456"))
        assertEquals(
            "https://www.youtube.com/watch?v=12345678901234567890",
            youtubeWatchUrlOrNull("12345678901234567890"),
        )
    }

    @Test
    fun `null or empty or blank gives null`() {
        assertNull(youtubeWatchUrlOrNull(null))
        assertNull(youtubeWatchUrlOrNull(""))
        assertNull(youtubeWatchUrlOrNull("   "))
    }

    @Test
    fun `too short or too long gives null`() {
        assertNull(youtubeWatchUrlOrNull("12345")) // 5 chars
        assertNull(youtubeWatchUrlOrNull("123456789012345678901")) // 21 chars
    }

    @Test
    fun `ids with illegal characters give null`() {
        assertNull(youtubeWatchUrlOrNull("abc&def123"))
        assertNull(youtubeWatchUrlOrNull("abc/def123"))
        assertNull(youtubeWatchUrlOrNull("abc def123"))
        assertNull(youtubeWatchUrlOrNull("abc\"def123"))
        assertNull(youtubeWatchUrlOrNull("abc'def123"))
        assertNull(youtubeWatchUrlOrNull("abc?def123"))
    }
}
