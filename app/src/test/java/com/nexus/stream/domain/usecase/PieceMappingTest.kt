package com.nexus.stream.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PieceMappingTest {
    private val mib = 1024L * 1024L

    @Test
    fun M1_standardFileOffsetsAndSizes() {
        val result =
            targetPieces(
                fileOffset = 10L * mib,
                fileSize = 700L * mib,
                pieceLength = 2L * mib,
                headBytes = 8L * mib,
                tailBytes = 2L * mib,
            )
        assertEquals(listOf(5, 6, 7, 8), result.head)
        assertEquals(listOf(354), result.tail)
        assertEquals(listOf(5, 6, 7, 8, 354), result.all)
    }

    @Test
    fun M2_nonAlignedOffsetSpansExtraPiece() {
        val result =
            targetPieces(
                fileOffset = (10.5 * mib).toLong(),
                fileSize = 700L * mib,
                pieceLength = 2L * mib,
                headBytes = 8L * mib,
                tailBytes = 2L * mib,
            )
        assertEquals(listOf(5, 6, 7, 8, 9), result.head)
        assertEquals(listOf(354, 355), result.tail)
    }

    @Test
    fun M3_smallFileDropsTailPiecesInHead() {
        val result =
            targetPieces(
                fileOffset = 0L,
                fileSize = 3L * mib,
                pieceLength = 1L * mib,
                headBytes = 8L * mib,
                tailBytes = 2L * mib,
            )
        assertEquals(listOf(0, 1, 2), result.head)
        assertTrue(result.tail.isEmpty())
    }

    @Test
    fun M4_exactBoundaryDoesNotIncludeExtraPiece() {
        val result =
            targetPieces(
                fileOffset = 0L,
                fileSize = 100L * mib,
                pieceLength = 2L * mib,
                headBytes = 8L * mib,
                tailBytes = 2L * mib,
            )
        assertEquals(listOf(0, 1, 2, 3), result.head)
        assertEquals(listOf(49), result.tail)
    }

    @Test
    fun M5_zeroFileSizeReturnsEmpty() {
        val result =
            targetPieces(
                fileOffset = 0L,
                fileSize = 0L,
                pieceLength = 2L * mib,
                headBytes = 8L * mib,
                tailBytes = 2L * mib,
            )
        assertTrue(result.head.isEmpty())
        assertTrue(result.tail.isEmpty())
    }

    @Test
    fun M6_zeroPieceLengthReturnsEmptyWithoutDivideByZero() {
        val result =
            targetPieces(
                fileOffset = 0L,
                fileSize = 100L * mib,
                pieceLength = 0L,
                headBytes = 8L * mib,
                tailBytes = 2L * mib,
            )
        assertTrue(result.head.isEmpty())
        assertTrue(result.tail.isEmpty())
    }

    @Test
    fun M7_negativeOffsetReturnsEmpty() {
        val result =
            targetPieces(
                fileOffset = -1L,
                fileSize = 100L * mib,
                pieceLength = 2L * mib,
                headBytes = 8L * mib,
                tailBytes = 2L * mib,
            )
        assertTrue(result.head.isEmpty())
        assertTrue(result.tail.isEmpty())
    }

    @Test
    fun M8_partiallyOverlappingTailExcludesHeadPieces() {
        val result =
            targetPieces(
                fileOffset = 0L,
                fileSize = 9L * mib,
                pieceLength = 2L * mib,
                headBytes = 8L * mib,
                tailBytes = 2L * mib,
            )
        assertEquals(listOf(0, 1, 2, 3), result.head)
        assertEquals(listOf(4), result.tail)
    }

    @Test
    fun M9_bufferingPercentCalculations() {
        assertEquals(0, bufferingPercent(have = 0, total = 4))
        assertEquals(50, bufferingPercent(have = 2, total = 4))
        assertEquals(100, bufferingPercent(have = 4, total = 4))
        assertEquals(100, bufferingPercent(have = 5, total = 4))
        assertEquals(0, bufferingPercent(have = 1, total = 0))
        assertEquals(0, bufferingPercent(have = -1, total = 4))
    }
}
