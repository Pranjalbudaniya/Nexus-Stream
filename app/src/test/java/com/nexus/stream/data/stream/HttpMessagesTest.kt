package com.nexus.stream.data.stream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class HttpMessagesTest {
    @Test
    fun HM1_standardGetWithHeaders() {
        val head = "GET /t/abc/stream.mkv HTTP/1.1\r\nRange:  bytes=0-9 \r\nHost: 127.0.0.1:1"
        val req = parseRequestHead(head)
        assertNotNull(req)
        assertEquals("GET", req?.method)
        assertEquals("/t/abc/stream.mkv", req?.path)
        assertEquals("bytes=0-9", req?.headers?.get("range"))
        assertEquals("127.0.0.1:1", req?.headers?.get("host"))
    }

    @Test
    fun HM2_targetWithQueryAndFragment_cutAtQuery() {
        val head = "GET /t/abc/stream.mkv?x=1#f HTTP/1.1"
        val req = parseRequestHead(head)
        assertNotNull(req)
        assertEquals("/t/abc/stream.mkv", req?.path)
    }

    @Test
    fun HM3_percentEncodedPath_decodedUtf8OrNullIfInvalid() {
        val valid = parseRequestHead("GET /t/a%20b HTTP/1.1")
        assertNotNull(valid)
        assertEquals("/t/a b", valid?.path)

        val invalid = parseRequestHead("GET /t/a%zz HTTP/1.1")
        assertNull(invalid)
    }

    @Test
    fun HM4_httpVersions_10And11OkOthersNull() {
        assertNotNull(parseRequestHead("GET /a HTTP/1.0"))
        assertNotNull(parseRequestHead("GET /a HTTP/1.1"))
        assertNull(parseRequestHead("GET /a HTTP/2.0"))
        assertNull(parseRequestHead("GET /a"))
    }

    @Test
    fun HM5_methodMustBeUppercaseLettersOnly() {
        assertEquals("GET", parseRequestHead("GET /a HTTP/1.1")?.method)
        assertEquals("HEAD", parseRequestHead("HEAD /a HTTP/1.1")?.method)
        assertEquals("POST", parseRequestHead("POST /a HTTP/1.1")?.method)
        assertEquals("CUSTOM", parseRequestHead("CUSTOM /a HTTP/1.1")?.method)
        assertNull(parseRequestHead("get /a HTTP/1.1"))
        assertNull(parseRequestHead("G3T /a HTTP/1.1"))
        assertNull(parseRequestHead("GET-IT /a HTTP/1.1"))
    }

    @Test
    fun HM6_targetMustStartWithSlash() {
        val targets = listOf("http://x/y", "*", "x")
        targets.forEachIndexed { index, target ->
            val req = parseRequestHead("GET $target HTTP/1.1")
            assertNull("Failed at index $index", req)
        }
    }

    @Test
    fun HM7_malformedHeaders_returnsNull() {
        val headers =
            listOf(
                "NoColonHere",
                " FoldedHeader: value",
                "\tFoldedTab: value",
                ": emptyName",
            )
        headers.forEachIndexed { index, header ->
            val req = parseRequestHead("GET / HTTP/1.1\r\n$header")
            assertNull("Failed at index $index", req)
        }
    }

    @Test
    fun HM8_duplicateHeaders_firstWins() {
        val head = "GET / HTTP/1.1\r\nRange: a\r\nrange: b\r\nRANGE: c"
        val req = parseRequestHead(head)
        assertNotNull(req)
        assertEquals("a", req?.headers?.get("range"))
    }

    @Test
    fun HM9_linesSeparatedByBareNewline() {
        val head = "GET /test HTTP/1.1\nHost: localhost\nAccept: */*"
        val req = parseRequestHead(head)
        assertNotNull(req)
        assertEquals("/test", req?.path)
        assertEquals("localhost", req?.headers?.get("host"))
        assertEquals("*/*", req?.headers?.get("accept"))
    }

    @Test
    fun HM10_emptyOrWhitespaceHead_returnsNull() {
        assertNull(parseRequestHead(""))
        assertNull(parseRequestHead("   \n  "))
    }

    @Test
    fun HM11_moreThanThreePartsInRequestLine_returnsNull() {
        assertNull(parseRequestHead("GET /a b HTTP/1.1"))
    }

    @Test
    fun HM12_formatResponseHead206() {
        val formatted =
            formatResponseHead(
                206,
                listOf(
                    "Content-Range" to "bytes 0-9/100",
                    "Content-Length" to "10",
                ),
            )
        val expected = "HTTP/1.1 206 Partial Content\r\nContent-Range: bytes 0-9/100\r\nContent-Length: 10\r\n\r\n"
        assertEquals(expected, formatted)
    }

    @Test
    fun HM13_formatResponseHeadUnknownStatus() {
        val formatted = formatResponseHead(299, emptyList())
        assertEquals("HTTP/1.1 299 Unknown\r\n\r\n", formatted)
    }

    @Test
    fun HM14_crlfInsideHeader_replacedBySpace() {
        val formatted = formatResponseHead(200, listOf("X-Custom" to "a\r\nX-Evil: 1"))
        assertEquals("HTTP/1.1 200 OK\r\nX-Custom: a  X-Evil: 1\r\n\r\n", formatted)
    }

    @Test
    fun HM15_colonInHeaderValue_preserved() {
        val head = "GET / HTTP/1.1\r\nReferer: http://x:80/a"
        val req = parseRequestHead(head)
        assertNotNull(req)
        assertEquals("http://x:80/a", req?.headers?.get("referer"))
    }
}
