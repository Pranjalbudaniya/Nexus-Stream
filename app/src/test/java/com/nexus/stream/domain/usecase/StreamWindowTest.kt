package com.nexus.stream.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamWindowTest {
    private val mib = 1024L * 1024L

    @Test
    fun WP1_piecesForRange_firstPieceExact() {
        val result = piecesForRange(fileOffset = 0L, pieceLength = 1L * mib, position = 0L, length = 1L * mib)
        assertEquals(listOf(0), result)
    }

    @Test
    fun WP2_piecesForRange_spanningPieceBoundary() {
        val result = piecesForRange(fileOffset = 0L, pieceLength = 1L * mib, position = 1L * mib - 1L, length = 2L)
        assertEquals(listOf(0, 1), result)
    }

    @Test
    fun WP3_piecesForRange_withOffsetAndPosition() {
        val offset = (10.5 * mib).toLong()
        val result =
            piecesForRange(
                fileOffset = offset,
                pieceLength = 2L * mib,
                position = 1L * mib,
                length = 1L * mib,
            )
        assertEquals(listOf(5, 6), result)
    }

    @Test
    fun WP4_piecesForRange_singleMiddlePiece() {
        val offset = (10.5 * mib).toLong()
        val pos = (1.5 * mib).toLong()
        val result =
            piecesForRange(
                fileOffset = offset,
                pieceLength = 2L * mib,
                position = pos,
                length = 1L * mib,
            )
        assertEquals(listOf(6), result)
    }

    @Test
    fun WP5_piecesForRange_invalidInputs_returnsEmpty() {
        assertEquals(
            emptyList<Int>(),
            piecesForRange(fileOffset = 0L, pieceLength = 1L * mib, position = 0L, length = 0L),
        )
        assertEquals(
            emptyList<Int>(),
            piecesForRange(fileOffset = 0L, pieceLength = 1L * mib, position = 0L, length = -1L),
        )
        assertEquals(
            emptyList<Int>(),
            piecesForRange(
                fileOffset = 0L,
                pieceLength = 0L,
                position = 0L,
                length =
                    1L * mib,
            ),
        )
        assertEquals(
            emptyList<Int>(),
            piecesForRange(
                fileOffset = 0L,
                pieceLength = 1L * mib,
                position = -1L,
                length =
                    1L * mib,
            ),
        )
        assertEquals(
            emptyList<Int>(),
            piecesForRange(
                fileOffset = -1L,
                pieceLength = 1L * mib,
                position = 0L,
                length =
                    1L * mib,
            ),
        )
    }

    @Test
    fun WP6_windowPieces_fromStart() {
        val result =
            windowPieces(
                fileOffset = 0L,
                fileSize = 100L * mib,
                pieceLength = 2L * mib,
                position = 0L,
                windowBytes = 16L * mib,
            )
        assertEquals((0..7).toList(), result)
    }

    @Test
    fun WP7_windowPieces_clampedAtEnd() {
        val result =
            windowPieces(
                fileOffset = 0L,
                fileSize = 10L * mib,
                pieceLength = 2L * mib,
                position = 8L * mib,
                windowBytes = 16L * mib,
            )
        assertEquals(listOf(4), result)
    }

    @Test
    fun WP8_windowPieces_boundaryAndZeroInputs_returnsEmpty() {
        assertEquals(
            emptyList<Int>(),
            windowPieces(
                fileOffset = 0L,
                fileSize = 10L * mib,
                pieceLength = 2L * mib,
                position = 10L * mib,
                windowBytes =
                    16L * mib,
            ),
        )
        assertEquals(
            emptyList<Int>(),
            windowPieces(
                fileOffset = 0L,
                fileSize = 0L,
                pieceLength = 2L * mib,
                position = 0L,
                windowBytes = 16L * mib,
            ),
        )
        assertEquals(
            emptyList<Int>(),
            windowPieces(
                fileOffset = 0L,
                fileSize = 10L * mib,
                pieceLength = 2L * mib,
                position = 0L,
                windowBytes = 0L,
            ),
        )
    }

    @Test
    fun WP9_windowPieces_withOffsetAndWindow() {
        val offset = (10.5 * mib).toLong()
        val result =
            windowPieces(
                fileOffset = offset,
                fileSize = 100L * mib,
                pieceLength = 2L * mib,
                position = 3L * mib,
                windowBytes = 4L * mib,
            )
        assertEquals(listOf(6, 7, 8), result)
    }

    @Test
    fun WP10_needsReprioritize_cases() {
        val window = 16L * mib
        val start = 10L * mib

        assertTrue(needsReprioritize(position = start, windowStart = null, windowBytes = window))
        assertFalse(needsReprioritize(position = start, windowStart = start, windowBytes = window))
        assertFalse(needsReprioritize(position = start + 8L * mib - 1L, windowStart = start, windowBytes = window))
        assertTrue(needsReprioritize(position = start + 8L * mib, windowStart = start, windowBytes = window))
        assertTrue(needsReprioritize(position = start - 1L, windowStart = start, windowBytes = window))
        assertTrue(needsReprioritize(position = start, windowStart = start, windowBytes = 0L))
    }

    @Test
    fun DL1_positionZero_givesStep() {
        assertEquals(100, deadlineMsFor(position = 0, stepMs = 100L))
    }

    @Test
    fun DL2_positionNine_givesTenSteps() {
        assertEquals(1000, deadlineMsFor(position = 9, stepMs = 100L))
    }

    @Test
    fun DL3_largeInputs_clampedToIntMaxWithoutOverflow() {
        assertEquals(
            Int.MAX_VALUE,
            deadlineMsFor(position = Int.MAX_VALUE, stepMs = Long.MAX_VALUE / 2L),
        )
        assertEquals(
            Int.MAX_VALUE,
            deadlineMsFor(position = 1_000_000, stepMs = 1_000_000_000L),
        )
    }

    @Test
    fun DL4_negativePositionOrNonPositiveStep_givesOne() {
        assertEquals(1, deadlineMsFor(position = -5, stepMs = 100L))
        assertEquals(1, deadlineMsFor(position = 0, stepMs = 0L))
        assertEquals(1, deadlineMsFor(position = 0, stepMs = -7L))
        assertEquals(1, deadlineMsFor(position = -5, stepMs = -7L))
    }
}
