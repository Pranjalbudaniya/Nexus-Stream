package com.nexus.stream.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeaderPolicyTest {
    @Test
    fun H1_standardHeaders_bothKeptInOrder() {
        val input =
            linkedMapOf(
                "Referer" to "https://a.example/",
                "User-Agent" to "X/1",
            )
        val result = sanitizeRequestHeaders(input)
        assertEquals(input, result)
        assertEquals(listOf("Referer", "User-Agent"), result.keys.toList())
    }

    @Test
    fun H2_caseInsensitiveDuplicate_firstWins() {
        val input =
            linkedMapOf(
                "Referer" to "a",
                "referer" to "b",
            )
        val result = sanitizeRequestHeaders(input)
        assertEquals(mapOf("Referer" to "a"), result)
    }

    @Test
    fun H3_deniedHopByHopHeaders_allDropped() {
        val denied =
            listOf(
                "hOsT",
                "Connection",
                "Content-Length",
                "Transfer-Encoding",
                "Range",
                "If-Range",
                "Accept-Encoding",
                "Upgrade",
                "TE",
                "Keep-Alive",
                "Expect",
                "Proxy-Authorization",
                "proxy-connection",
                "Proxy-Authenticate",
                "trailer",
            )

        denied.forEachIndexed { index, name ->
            val result = sanitizeRequestHeaders(mapOf(name to "val"))
            assertTrue("Failed at index $index", result.isEmpty())
        }
    }

    @Test
    fun H4_crlfOrNulInValue_dropsEntry() {
        val badValues =
            listOf(
                "a\r\nX-Evil: 1",
                "value\nwith-newline",
                "value\u0000with-nul",
                "value\rwith-cr",
            )

        badValues.forEachIndexed { index, value ->
            val result = sanitizeRequestHeaders(mapOf("X-Test" to value))
            assertTrue("Failed at index $index", result.isEmpty())
        }
    }

    @Test
    fun H5_invalidNames_dropped() {
        val badNames =
            listOf(
                "Bad Name",
                "A:B",
                "",
                "   ",
                "Na\nme",
                "Header@Name",
                "Header[0]",
            )

        badNames.forEachIndexed { index, name ->
            val result = sanitizeRequestHeaders(mapOf(name to "value"))
            assertTrue("Failed at index $index", result.isEmpty())
        }
    }

    @Test
    fun H6_printableAsciiAndTab_tabKeptDelAndNonAsciiDropped() {
        val withTab = sanitizeRequestHeaders(mapOf("X-Tab" to "a\tb"))
        assertEquals("a\tb", withTab["X-Tab"])

        val withDel = sanitizeRequestHeaders(mapOf("X-Del" to "a\u007Fb"))
        assertTrue(withDel.isEmpty())

        val withNonAscii = sanitizeRequestHeaders(mapOf("X-Acc" to "café"))
        assertTrue(withNonAscii.isEmpty())
    }

    @Test
    fun H7_whitespaceTrim_valueTrimmedOrDroppedIfBlank() {
        val result =
            sanitizeRequestHeaders(
                linkedMapOf(
                    "X-Trim" to "  x  ",
                    "X-Blank" to "   ",
                ),
            )
        assertEquals(mapOf("X-Trim" to "x"), result)
    }

    @Test
    fun H8_valueMaxLength_4096Kept4097Dropped() {
        val v4096 = "a".repeat(4096)
        val v4097 = "a".repeat(4097)

        val kept = sanitizeRequestHeaders(mapOf("X-Len" to v4096))
        assertEquals(v4096, kept["X-Len"])

        val dropped = sanitizeRequestHeaders(mapOf("X-Len" to v4097))
        assertTrue(dropped.isEmpty())
    }

    @Test
    fun H9_maxHeaderCount_first20Kept() {
        val input = LinkedHashMap<String, String>()
        for (i in 1..25) {
            input["X-Header-$i"] = "val-$i"
        }

        val result = sanitizeRequestHeaders(input)
        assertEquals(20, result.size)
        assertEquals("val-1", result["X-Header-1"])
        assertEquals("val-20", result["X-Header-20"])
        assertTrue(!result.containsKey("X-Header-21"))
    }

    @Test
    fun H10_maxTotalLength_stopsAt16384Cap() {
        val input = LinkedHashMap<String, String>()
        // 4 headers with length 4000 = 16000
        for (i in 1..4) {
            input["X-H$i"] = "v".repeat(4000 - 4) // name + value = 4 + 3996 = 4000
        }
        // 5th header: length 1000 would bring total to 17000 > 16384 -> stopped
        input["X-H5"] = "v".repeat(996)

        val result = sanitizeRequestHeaders(input)
        assertEquals(4, result.size)
        assertTrue(result.containsKey("X-H1"))
        assertTrue(result.containsKey("X-H4"))
        assertTrue(!result.containsKey("X-H5"))
    }

    @Test
    fun H11_emptyMap_returnsEmpty() {
        val result = sanitizeRequestHeaders(emptyMap())
        assertTrue(result.isEmpty())
    }

    @Test
    fun H12_authCookieAndCustom_allKept() {
        val input =
            linkedMapOf(
                "Authorization" to "Bearer x",
                "Cookie" to "a=b",
                "X-Custom_1.2" to "v",
            )
        val result = sanitizeRequestHeaders(input)
        assertEquals(input, result)
    }
}
