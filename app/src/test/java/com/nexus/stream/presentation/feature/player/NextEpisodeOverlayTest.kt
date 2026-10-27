package com.nexus.stream.presentation.feature.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NextEpisodeOverlayTest {
    @Test
    fun UI1_nextEpisodeLabel_numbersOnly() {
        val season = 1
        val episode = 5
        val label = formatNextEpisodeLabel(season, episode)
        val a11y = formatNextEpisodeA11y(season, episode)

        assertEquals("S1 E5", label)
        assertTrue(a11y.contains("season 1 episode 5"))
        assertFalse(a11y.contains("tt"))
        assertFalse(a11y.contains("http"))
        assertFalse(a11y.contains(":"))
    }
}
