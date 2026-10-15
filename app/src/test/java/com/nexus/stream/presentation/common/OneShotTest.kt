package com.nexus.stream.presentation.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OneShotTest {
    @Test
    fun consume_returnsValueOnceThenNull() {
        val oneShot = OneShot<String>()
        oneShot.set("test_value")

        assertEquals("test_value", oneShot.consume())
        assertNull(oneShot.consume())
    }

    @Test
    fun set_replacesUnconsumedValue() {
        val oneShot = OneShot<String>()
        oneShot.set("first_value")
        oneShot.set("second_value")

        assertEquals("second_value", oneShot.consume())
        assertNull(oneShot.consume())
    }

    @Test
    fun consume_onEmptyReturnsNull() {
        val oneShot = OneShot<String>()
        assertNull(oneShot.consume())
    }
}
