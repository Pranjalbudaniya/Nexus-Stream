package com.nexus.stream.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Test

class ProxyRulesTest {
    @Test
    fun PR1_streamHeadersKeepOrder_cookieRemoved_acceptEncodingAdded() {
        val streamHeaders =
            linkedMapOf(
                "X-Custom" to "one",
                "Referer" to "https://example.org/",
                "Cookie" to "session=abc",
                "cOoKiE" to "other=123",
                "Origin" to "https://origin.org",
            )

        val result =
            buildUpstreamHeaders(
                streamHeaders = streamHeaders,
                playerRange = null,
                playerUserAgent = null,
                defaultUserAgent = "DefaultUA/1.0",
            )

        val expected =
            listOf(
                "X-Custom" to "one",
                "Referer" to "https://example.org/",
                "Origin" to "https://origin.org",
                "Accept-Encoding" to "identity",
                "User-Agent" to "DefaultUA/1.0",
            )
        assertEquals(expected, result)
    }

    @Test
    fun PR2_playerRangeForwardedOrDropped() {
        val validRanges = listOf("bytes=100-", "bytes=0-99", "bytes=-500")
        for (range in validRanges) {
            val result =
                buildUpstreamHeaders(
                    streamHeaders = emptyMap(),
                    playerRange = range,
                    playerUserAgent = null,
                    defaultUserAgent = "DefaultUA",
                )
            val rangeHeader = result.find { it.first == "Range" }
            assertEquals("Range" to range, rangeHeader)
        }

        val invalidRanges = listOf("bytes=0-1,5-9", "bytes=-", "items=1-2", "", null)
        for (range in invalidRanges) {
            val result =
                buildUpstreamHeaders(
                    streamHeaders = emptyMap(),
                    playerRange = range,
                    playerUserAgent = null,
                    defaultUserAgent = "DefaultUA",
                )
            val rangeHeader = result.find { it.first.equals("Range", ignoreCase = true) }
            assertEquals(null, rangeHeader)
        }
    }

    @Test
    fun PR3_userAgentPrecedence() {
        // Stream UA wins in any case
        val streamWithUa = mapOf("user-agent" to "StreamUA/1.0")
        val result1 =
            buildUpstreamHeaders(
                streamHeaders = streamWithUa,
                playerRange = null,
                playerUserAgent = "PlayerUA/1.0",
                defaultUserAgent = "DefaultUA/1.0",
            )
        assertEquals("user-agent" to "StreamUA/1.0", result1.find { it.first.equals("user-agent", ignoreCase = true) })
        assertEquals(1, result1.count { it.first.equals("user-agent", ignoreCase = true) })

        // Else player UA when non-blank
        val result2 =
            buildUpstreamHeaders(
                streamHeaders = emptyMap(),
                playerRange = null,
                playerUserAgent = "PlayerUA/1.0",
                defaultUserAgent = "DefaultUA/1.0",
            )
        assertEquals("User-Agent" to "PlayerUA/1.0", result2.find { it.first == "User-Agent" })

        // Else default UA when player UA is null or blank
        val result3 =
            buildUpstreamHeaders(
                streamHeaders = emptyMap(),
                playerRange = null,
                playerUserAgent = "   ",
                defaultUserAgent = "DefaultUA/1.0",
            )
        assertEquals("User-Agent" to "DefaultUA/1.0", result3.find { it.first == "User-Agent" })
    }

    @Test
    fun PR4_filterResponseHeaders200_allowListOnly() {
        val upstream =
            listOf(
                "server" to "nginx",
                "content-type" to "video/mp4",
                "set-cookie" to "id=123",
                "content-length" to "1048576",
                "content-encoding" to "gzip",
                "accept-ranges" to "bytes",
                "transfer-encoding" to "chunked",
                "etag" to "\"xyz\"",
                "last-modified" to "Sun, 06 Nov 1994 08:49:37 GMT",
            )

        val result = filterResponseHeaders(200, upstream)
        val expected =
            listOf(
                "Content-Type" to "video/mp4",
                "Content-Length" to "1048576",
                "Accept-Ranges" to "bytes",
                "ETag" to "\"xyz\"",
                "Last-Modified" to "Sun, 06 Nov 1994 08:49:37 GMT",
                "Connection" to "close",
                "Cache-Control" to "no-store",
            )
        assertEquals(expected, result)
    }

    @Test
    fun PR5_filterResponseHeadersErrors() {
        val upstream403 =
            listOf(
                "content-type" to "text/html",
                "content-length" to "512",
            )
        val result403 = filterResponseHeaders(403, upstream403)
        assertEquals(
            listOf(
                "Content-Length" to "0",
                "Connection" to "close",
                "Cache-Control" to "no-store",
            ),
            result403,
        )

        val upstream416 =
            listOf(
                "content-type" to "text/html",
                "content-range" to "bytes */1000",
                "content-length" to "512",
            )
        val result416 = filterResponseHeaders(416, upstream416)
        assertEquals(
            listOf(
                "Content-Range" to "bytes */1000",
                "Content-Length" to "0",
                "Connection" to "close",
                "Cache-Control" to "no-store",
            ),
            result416,
        )
    }

    @Test
    fun PR6_parseCookieHeader() {
        val raw = "a=b; c=d; bad; =x; e="
        val result = parseCookieHeader(raw)
        assertEquals(
            listOf(
                "a" to "b",
                "c" to "d",
                "e" to "",
            ),
            result,
        )

        assertEquals(emptyList<Pair<String, String>>(), parseCookieHeader(""))
        assertEquals(emptyList<Pair<String, String>>(), parseCookieHeader("   "))
    }

    @Test
    fun PR7_proxyFileName() {
        assertEquals("movie.mkv", proxyFileName("https://h/p/movie.mkv?x=1"))
        assertEquals("stream", proxyFileName("https://h/"))
        assertEquals("stream", proxyFileName("https://h/a%20b.mp4"))
        val long80 = "a".repeat(76) + ".mp4"
        assertEquals("stream", proxyFileName("https://h/$long80"))
        assertEquals("stream", proxyFileName("https://h/movie"))
        assertEquals("stream", proxyFileName("https://h/x.verylongext"))
    }

    @Test
    fun PR8_mapUpstreamStatus() {
        val unchanged = listOf(200, 206, 204, 403, 404, 416, 503)
        for (status in unchanged) {
            assertEquals(status, mapUpstreamStatus(status))
        }

        val badGateway = listOf(302, 100, 600, 0)
        for (status in badGateway) {
            assertEquals(502, mapUpstreamStatus(status))
        }
    }

    @Test
    fun PR9_filterResponseHeaders_caseInsensitiveMatch() {
        val upstream =
            listOf(
                "CoNtEnT-TyPe" to "video/mkv",
                "CONTENT-LENGTH" to "2048",
                "EtAg" to "\"abc\"",
                "ACCEPT-RANGES" to "bytes",
            )
        val result = filterResponseHeaders(200, upstream)
        assertEquals(
            listOf(
                "Content-Type" to "video/mkv",
                "Content-Length" to "2048",
                "ETag" to "\"abc\"",
                "Accept-Ranges" to "bytes",
                "Connection" to "close",
                "Cache-Control" to "no-store",
            ),
            result,
        )
    }
}
