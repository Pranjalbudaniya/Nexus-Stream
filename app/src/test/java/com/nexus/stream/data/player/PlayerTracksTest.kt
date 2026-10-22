package com.nexus.stream.data.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerTracksTest {
    @Test
    fun TK1_videoScaleNext_cyclesThroughAllModesInOrder() {
        var scale = VideoScale.FIT
        scale = scale.next()
        assertEquals(VideoScale.CROP, scale)
        scale = scale.next()
        assertEquals(VideoScale.STRETCH, scale)
        scale = scale.next()
        assertEquals(VideoScale.RATIO_16_9, scale)
        scale = scale.next()
        assertEquals(VideoScale.RATIO_4_3, scale)
        scale = scale.next()
        assertEquals(VideoScale.ORIGINAL, scale)
        scale = scale.next()
        assertEquals(VideoScale.FIT, scale)
    }

    @Test
    fun TK2_parseVideoScale_parsesValidAndFallsBackToFit() {
        assertEquals(VideoScale.CROP, parseVideoScale("CROP"))
        assertEquals(VideoScale.STRETCH, parseVideoScale("STRETCH"))
        assertEquals(VideoScale.RATIO_16_9, parseVideoScale("RATIO_16_9"))
        assertEquals(VideoScale.RATIO_4_3, parseVideoScale("RATIO_4_3"))
        assertEquals(VideoScale.ORIGINAL, parseVideoScale("ORIGINAL"))
        assertEquals(VideoScale.FIT, parseVideoScale("FIT"))
        assertEquals(VideoScale.FIT, parseVideoScale(null))
        assertEquals(VideoScale.FIT, parseVideoScale(""))
        assertEquals(VideoScale.FIT, parseVideoScale("junk"))
    }

    @Test
    fun TK3_speedOptions_andFormatSpeed() {
        val expected = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
        assertEquals(expected, SPEED_OPTIONS)

        assertEquals("0.5x", formatSpeed(0.5f))
        assertEquals("0.75x", formatSpeed(0.75f))
        assertEquals("1x", formatSpeed(1.0f))
        assertEquals("1.25x", formatSpeed(1.25f))
        assertEquals("1.5x", formatSpeed(1.5f))
        assertEquals("2x", formatSpeed(2.0f))
    }

    @Test
    fun TK4_trackLabel_blankFallbackAndTrimmedName() {
        val blankTrack = PlayerTrack(id = "1", name = "   ")
        assertEquals("Track 3", trackLabel(blankTrack, index = 2))

        val paddedTrack = PlayerTrack(id = "2", name = "  English Audio  ")
        assertEquals("English Audio", trackLabel(paddedTrack, index = 0))
    }
}
