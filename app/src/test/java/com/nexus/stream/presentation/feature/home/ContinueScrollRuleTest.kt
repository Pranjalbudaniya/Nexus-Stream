package com.nexus.stream.presentation.feature.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinueScrollRuleTest {
    @Test
    fun CS1_headChanged_scrollsToStart() {
        assertTrue(shouldScrollContinueToStart(previousHeadKey = "a", newHeadKey = "b", isUserScrolling = false))
    }

    @Test
    fun CS2_sameHead_doesNotScroll() {
        assertFalse(shouldScrollContinueToStart(previousHeadKey = "a", newHeadKey = "a", isUserScrolling = false))
    }

    @Test
    fun CS3_noPrevious_doesNotScroll() {
        assertFalse(shouldScrollContinueToStart(previousHeadKey = null, newHeadKey = "a", isUserScrolling = false))
    }

    @Test
    fun CS4_userIsScrolling_doesNotScroll() {
        assertFalse(shouldScrollContinueToStart(previousHeadKey = "a", newHeadKey = "b", isUserScrolling = true))
    }

    @Test
    fun CS5_emptyList_doesNotScroll() {
        assertFalse(shouldScrollContinueToStart(previousHeadKey = "a", newHeadKey = null, isUserScrolling = false))
    }
}
