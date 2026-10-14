package com.nexus.stream.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class AddonReorderTest {
    @Test
    fun reorder_moveDown() {
        val list = listOf("a", "b", "c")
        val result = reorder(list, "a", 1)
        assertEquals(listOf("b", "a", "c"), result)
    }

    @Test
    fun reorder_moveUp() {
        val list = listOf("a", "b", "c")
        val result = reorder(list, "c", 1)
        assertEquals(listOf("a", "c", "b"), result)
    }

    @Test
    fun reorder_toZero() {
        val list = listOf("a", "b", "c")
        val result = reorder(list, "c", 0)
        assertEquals(listOf("c", "a", "b"), result)
    }

    @Test
    fun reorder_toLast() {
        val list = listOf("a", "b", "c")
        val result = reorder(list, "a", 2)
        assertEquals(listOf("b", "c", "a"), result)
    }

    @Test
    fun reorder_toPositionPastEndClamps() {
        val list = listOf("a", "b", "c")
        val result = reorder(list, "a", 10)
        assertEquals(listOf("b", "c", "a"), result)
    }

    @Test
    fun reorder_negativeToPositionClampsToZero() {
        val list = listOf("a", "b", "c")
        val result = reorder(list, "c", -5)
        assertEquals(listOf("c", "a", "b"), result)
    }

    @Test
    fun reorder_samePositionIsNoOp() {
        val list = listOf("a", "b", "c")
        val result = reorder(list, "b", 1)
        assertEquals(listOf("a", "b", "c"), result)
    }

    @Test
    fun reorder_unknownUrlReturnsListUnchanged() {
        val list = listOf("a", "b", "c")
        val result = reorder(list, "unknown", 1)
        assertEquals(listOf("a", "b", "c"), result)
    }

    @Test
    fun reorder_singleItemList() {
        val list = listOf("a")
        val result = reorder(list, "a", 0)
        assertEquals(listOf("a"), result)
    }
}
