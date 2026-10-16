package com.nexus.stream.presentation.feature.detail

import org.junit.Assert.assertEquals
import org.junit.Test

class HeaderHeightTest {
    @Test
    fun `headerHeightDp clamps to 220 for small screens`() {
        assertEquals(220f, headerHeightDp(400f), 0.001f)
        assertEquals(220f, headerHeightDp(500f), 0.001f)
    }

    @Test
    fun `headerHeightDp calculates 40 percent in normal range`() {
        assertEquals(320f, headerHeightDp(800f), 0.001f)
        assertEquals(400f, headerHeightDp(1000f), 0.001f)
    }

    @Test
    fun `headerHeightDp clamps to 480 for large screens`() {
        assertEquals(480f, headerHeightDp(1500f), 0.001f)
        assertEquals(480f, headerHeightDp(2000f), 0.001f)
    }
}
