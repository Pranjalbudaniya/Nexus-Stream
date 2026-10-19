package com.nexus.stream.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Test

class HttpRangeTest {
    @Test
    fun RG1_explicitRangeWithinLength() {
        assertEquals(RangeSpec.Partial(0, 99), parseRange("bytes=0-99", 1000))
    }

    @Test
    fun RG2_openEndedRangeWithinLength() {
        assertEquals(RangeSpec.Partial(500, 999), parseRange("bytes=500-", 1000))
    }

    @Test
    fun RG3_suffixRangeWithinLength() {
        assertEquals(RangeSpec.Partial(900, 999), parseRange("bytes=-100", 1000))
    }

    @Test
    fun RG4_explicitRangeExceedingLength_clampedToLengthMinusOne() {
        assertEquals(RangeSpec.Partial(0, 999), parseRange("bytes=0-99999", 1000))
    }

    @Test
    fun RG5_openEndedRangeAtOrBeyondLength_unsatisfiable() {
        assertEquals(RangeSpec.Unsatisfiable, parseRange("bytes=1000-", 1000))
    }

    @Test
    fun RG6_explicitRangeStartingAtOrBeyondLength_unsatisfiable() {
        assertEquals(RangeSpec.Unsatisfiable, parseRange("bytes=1000-2000", 1000))
    }

    @Test
    fun RG7_suffixRangeZero_unsatisfiable() {
        assertEquals(RangeSpec.Unsatisfiable, parseRange("bytes=-0", 1000))
    }

    @Test
    fun RG8_suffixRangeLargerThanLength_clampedToZero() {
        assertEquals(RangeSpec.Partial(0, 999), parseRange("bytes=-5000", 1000))
    }

    @Test
    fun RG9_multiRange_isFull() {
        assertEquals(RangeSpec.Full, parseRange("bytes=0-1,5-6", 1000))
    }

    @Test
    fun RG10_nullOrBlank_isFull() {
        assertEquals(RangeSpec.Full, parseRange(null, 1000))
        assertEquals(RangeSpec.Full, parseRange("", 1000))
        assertEquals(RangeSpec.Full, parseRange("   ", 1000))
    }

    @Test
    fun RG11_unsupportedUnit_isFull() {
        assertEquals(RangeSpec.Full, parseRange("items=0-9", 1000))
    }

    @Test
    fun RG12_malformedSyntax_isFull() {
        val bad =
            listOf(
                "bytes=abc",
                "bytes=",
                "bytes=-",
                "bytes=1",
                "bytes=1-x",
                "bytes= 0-9",
                "bytes=0 -9",
            )
        bad.forEachIndexed { index, header ->
            assertEquals("Failed at index $index", RangeSpec.Full, parseRange(header, 1000))
        }
    }

    @Test
    fun RG13_endBeforeStart_isFull() {
        assertEquals(RangeSpec.Full, parseRange("bytes=99-0", 1000))
    }

    @Test
    fun RG14_caseInsensitiveAndOuterSpaces() {
        assertEquals(RangeSpec.Partial(0, 9), parseRange("BYTES=0-9", 1000))
        assertEquals(RangeSpec.Partial(0, 9), parseRange(" bytes=0-9 ", 1000))
    }

    @Test
    fun RG15_singleByteRange() {
        assertEquals(RangeSpec.Partial(0, 0), parseRange("bytes=0-0", 1000))
    }

    @Test
    fun RG16_zeroLengthResource() {
        assertEquals(RangeSpec.Unsatisfiable, parseRange("bytes=0-", 0))
        assertEquals(RangeSpec.Full, parseRange(null, 0))
    }

    @Test
    fun RG17_overflowLong_isFull() {
        assertEquals(RangeSpec.Full, parseRange("bytes=99999999999999999999-", 1000))
    }
}
