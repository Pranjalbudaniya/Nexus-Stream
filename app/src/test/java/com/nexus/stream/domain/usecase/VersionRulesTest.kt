package com.nexus.stream.domain.usecase

import com.nexus.stream.domain.model.ParsedVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionRulesTest {
    @Test
    fun VR1_parse_plainVersion() {
        val parsed = parseVersion("1.0.1")
        assertEquals(ParsedVersion(listOf(1, 0, 1), null), parsed)
    }

    @Test
    fun VR2_parse_leadingV() {
        val parsedLower = parseVersion("v1.0.1")
        val parsedUpper = parseVersion("V1.0.1")
        assertEquals(ParsedVersion(listOf(1, 0, 1), null), parsedLower)
        assertEquals(ParsedVersion(listOf(1, 0, 1), null), parsedUpper)
    }

    @Test
    fun VR3_parse_twoParts() {
        val parsed = parseVersion("1.2")
        assertEquals(ParsedVersion(listOf(1, 2), null), parsed)
    }

    @Test
    fun VR4_parse_fourParts() {
        val parsedFour = parseVersion("1.0.0.1")
        val parsedFive = parseVersion("1.0.0.0.1")
        assertEquals(ParsedVersion(listOf(1, 0, 0, 1), null), parsedFour)
        assertNull(parsedFive)
    }

    @Test
    fun VR5_parse_prereleaseSuffix() {
        val parsed = parseVersion("v1.1.0-beta.2")
        assertEquals(ParsedVersion(listOf(1, 1, 0), "beta.2"), parsed)
    }

    @Test
    fun VR6_parse_garbage_isNull() {
        val garbage = listOf("latest", "", "v", "1..2", "1.x", "1.0.0.0.0")
        for (input in garbage) {
            assertNull("Expected null for '$input'", parseVersion(input))
        }
        val withSpaces = parseVersion(" v1.0.1 ")
        assertEquals(ParsedVersion(listOf(1, 0, 1), null), withSpaces)
    }

    @Test
    fun VR7_compare_isNumeric_notTextual() {
        val a = parseVersion("1.10.0")!!
        val b = parseVersion("1.9.0")!!
        assertTrue("1.10.0 should be greater than 1.9.0", compareVersions(a, b) > 0)
    }

    @Test
    fun VR8_compare_missingPartsAreZero() {
        val a = parseVersion("1.0")!!
        val b = parseVersion("1.0.0")!!
        assertEquals(0, compareVersions(a, b))
    }

    @Test
    fun VR9_compare_prereleaseIsLowerThanRelease() {
        val pre = parseVersion("1.1.0-beta")!!
        val rel = parseVersion("1.1.0")!!
        assertTrue("Pre-release should be lower than release", compareVersions(pre, rel) < 0)
    }

    @Test
    fun VR10_compare_prereleaseOrder() {
        val beta2 = parseVersion("1.1.0-beta.2")!!
        val beta1 = parseVersion("1.1.0-beta.1")!!
        assertTrue(compareVersions(beta2, beta1) > 0)

        val beta10 = parseVersion("1.1.0-beta.10")!!
        val beta9 = parseVersion("1.1.0-beta.9")!!
        assertTrue(compareVersions(beta10, beta9) > 0)
    }

    @Test
    fun VR11_isNewer_sameOrOlder_isFalse() {
        assertFalse(isNewerVersion("v1.0.0", "1.0.0"))
        assertFalse(isNewerVersion("v0.9.9", "1.0.0"))
    }

    @Test
    fun VR12_isNewer_newer_isTrue() {
        assertTrue(isNewerVersion("v1.0.1", "1.0.0"))
    }

    @Test
    fun VR13_isNewer_unparseableCandidate_isFalse() {
        assertFalse(isNewerVersion("nightly", "1.0.0"))
    }

    @Test
    fun VR14_isNewer_unparseableCurrent_isFalse() {
        assertFalse(isNewerVersion("v1.0.1", ""))
    }
}
