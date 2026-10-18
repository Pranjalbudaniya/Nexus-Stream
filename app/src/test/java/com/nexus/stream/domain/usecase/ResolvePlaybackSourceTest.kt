package com.nexus.stream.domain.usecase

import com.nexus.stream.domain.model.EpisodeHint
import com.nexus.stream.domain.model.PlaybackSource
import com.nexus.stream.domain.model.StreamSource
import com.nexus.stream.domain.model.TorrentRequest
import com.nexus.stream.domain.model.UnsupportedReason
import org.junit.Assert.assertEquals
import org.junit.Test

class ResolvePlaybackSourceTest {
    private val valid40Hex = "0123456789abcdef0123456789abcdef01234567"

    @Test
    fun R1_directUrlNoHeaders_returnsDirect() {
        val source = StreamSource.Url("https://cdn.example/a.mkv", emptyMap())
        val result = resolvePlaybackSource(source, null)
        assertEquals(PlaybackSource.Direct("https://cdn.example/a.mkv"), result)
    }

    @Test
    fun R2_directUrlWithHeaders_returnsDirectWithHeaders() {
        val headers = mapOf("Referer" to "https://x/")
        val source = StreamSource.Url("https://cdn.example/a.mkv", headers)
        val result = resolvePlaybackSource(source, null)
        assertEquals(PlaybackSource.DirectWithHeaders("https://cdn.example/a.mkv", headers), result)
    }

    @Test
    fun R3_directUrlOnlyDeniedHeaders_returnsDirect() {
        val headers = mapOf("Host" to "x", "Range" to "bytes=0-1")
        val source = StreamSource.Url("https://cdn.example/a.mkv", headers)
        val result = resolvePlaybackSource(source, null)
        assertEquals(PlaybackSource.Direct("https://cdn.example/a.mkv"), result)
    }

    @Test
    fun R4_directUrlWithInnerSpaces_encodedAsPercent20() {
        val source = StreamSource.Url("https://x/My Movie.mkv", emptyMap())
        val result = resolvePlaybackSource(source, null)
        assertEquals(PlaybackSource.Direct("https://x/My%20Movie.mkv"), result)
    }

    @Test
    fun R5_unsupportedSchemes_returnsBadScheme() {
        val schemes =
            listOf(
                "file:///sdcard/x.mkv",
                "content://a/b",
                "intent://x",
                "javascript:alert(1)",
                "data:text/plain,hi",
            )
        schemes.forEachIndexed { index, url ->
            val result = resolvePlaybackSource(StreamSource.Url(url, emptyMap()), null)
            assertEquals("Failed at index $index", PlaybackSource.Unsupported(UnsupportedReason.BAD_SCHEME), result)
        }
    }

    @Test
    fun R6_ftpRtmpAndRelativeUrls_returnsBadScheme() {
        val urls =
            listOf(
                "ftp://x/y",
                "rtmp://x/y",
                "//x/y",
                "x/y",
            )
        urls.forEachIndexed { index, url ->
            val result = resolvePlaybackSource(StreamSource.Url(url, emptyMap()), null)
            assertEquals("Failed at index $index", PlaybackSource.Unsupported(UnsupportedReason.BAD_SCHEME), result)
        }
    }

    @Test
    fun R7_uppercaseScheme_schemeLowercasedInResult() {
        val source = StreamSource.Url("HTTPS://X.example/a", emptyMap())
        val result = resolvePlaybackSource(source, null)
        assertEquals(PlaybackSource.Direct("https://X.example/a"), result)
    }

    @Test
    fun R8_blankUrl_returnsNoSource() {
        val source = StreamSource.Url("   ", emptyMap())
        val result = resolvePlaybackSource(source, null)
        assertEquals(PlaybackSource.Unsupported(UnsupportedReason.NO_SOURCE), result)
    }

    @Test
    fun R9_urlLengthLimits_8192Ok8193BadUrl() {
        val prefix = "https://x.example/"
        val exact8192 = prefix + "a".repeat(8192 - prefix.length)
        val over8193 = prefix + "a".repeat(8193 - prefix.length)

        val okResult = resolvePlaybackSource(StreamSource.Url(exact8192, emptyMap()), null)
        assertEquals(PlaybackSource.Direct(exact8192), okResult)

        val badResult = resolvePlaybackSource(StreamSource.Url(over8193, emptyMap()), null)
        assertEquals(PlaybackSource.Unsupported(UnsupportedReason.BAD_URL), badResult)
    }

    @Test
    fun R10_urlWithWhitespaceOrNewline_trimmedOkControlInMiddleBadUrl() {
        val trimmedOk = StreamSource.Url("  https://x/a\n", emptyMap())
        assertEquals(PlaybackSource.Direct("https://x/a"), resolvePlaybackSource(trimmedOk, null))

        val innerNewline = StreamSource.Url("https://x/a\nb", emptyMap())
        assertEquals(PlaybackSource.Unsupported(UnsupportedReason.BAD_URL), resolvePlaybackSource(innerNewline, null))
    }

    @Test
    fun R11_missingOrEmptyAuthority_returnsBadUrl() {
        val badUrls =
            listOf(
                "https://",
                "http:///nohost",
                "https://:8080/a",
                "https://user@/a",
            )
        badUrls.forEachIndexed { index, url ->
            val result = resolvePlaybackSource(StreamSource.Url(url, emptyMap()), null)
            assertEquals("Failed at index $index", PlaybackSource.Unsupported(UnsupportedReason.BAD_URL), result)
        }
    }

    @Test
    fun R12_magnetUrl_parsedAsTorrentWithNullFileIdxAndNullHint() {
        val magnet = "magnet:?xt=urn:btih:$valid40Hex&tr=udp%3A%2F%2Ft.example%3A1%2Fa"
        val source = StreamSource.Url(magnet, emptyMap())
        val result = resolvePlaybackSource(source, null)
        val expected =
            PlaybackSource.Torrent(
                TorrentRequest(
                    infoHash = valid40Hex,
                    trackers = listOf("udp://t.example:1/a"),
                    fileIdx = null,
                    episodeHint = null,
                ),
            )
        assertEquals(expected, result)
    }

    @Test
    fun R13_magnetUrlWithHint_hintPassedToTorrentRequest() {
        val magnet = "magnet:?xt=urn:btih:$valid40Hex&tr=udp%3A%2F%2Ft.example%3A1%2Fa"
        val source = StreamSource.Url(magnet, emptyMap())
        val hint = EpisodeHint(season = 1, episode = 2)
        val result = resolvePlaybackSource(source, hint)
        val expected =
            PlaybackSource.Torrent(
                TorrentRequest(
                    infoHash = valid40Hex,
                    trackers = listOf("udp://t.example:1/a"),
                    fileIdx = null,
                    episodeHint = hint,
                ),
            )
        assertEquals(expected, result)
    }

    @Test
    fun R14_magnetUrlBtmhOnly_returnsInvalidHash() {
        val btmhHash = "12200123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        val source = StreamSource.Url("magnet:?xt=urn:btmh:$btmhHash", emptyMap())
        val result = resolvePlaybackSource(source, null)
        assertEquals(PlaybackSource.Unsupported(UnsupportedReason.INVALID_HASH), result)
    }

    @Test
    fun R15_magnetUrlWithHeaders_headersIgnored() {
        val magnet = "magnet:?xt=urn:btih:$valid40Hex"
        val source = StreamSource.Url(magnet, mapOf("Referer" to "https://x/"))
        val result = resolvePlaybackSource(source, null)
        val expected =
            PlaybackSource.Torrent(
                TorrentRequest(
                    infoHash = valid40Hex,
                    trackers = emptyList(),
                    fileIdx = null,
                    episodeHint = null,
                ),
            )
        assertEquals(expected, result)
    }

    @Test
    fun R16_torrentSourceValid_returnsTorrentWithLowercaseHash() {
        val upperHash = valid40Hex.uppercase()
        val trackers = listOf("udp://tracker/announce")
        val hint = EpisodeHint(season = 1, episode = 2)
        val source = StreamSource.Torrent(infoHash = upperHash, fileIdx = 3, trackers = trackers)
        val result = resolvePlaybackSource(source, hint)
        val expected =
            PlaybackSource.Torrent(
                TorrentRequest(
                    infoHash = valid40Hex,
                    trackers = trackers,
                    fileIdx = 3,
                    episodeHint = hint,
                ),
            )
        assertEquals(expected, result)
    }

    @Test
    fun R17_torrentSourceInvalidLength_returnsInvalidHash() {
        val raw39 = "0123456789abcdef0123456789abcdef0123456"
        val base32 = "A42Y7F2G6C4M4N5O6P7Q8R9S0T1U2V3W"

        assertEquals(
            PlaybackSource.Unsupported(UnsupportedReason.INVALID_HASH),
            resolvePlaybackSource(StreamSource.Torrent(raw39, null, emptyList()), null),
        )
        assertEquals(
            PlaybackSource.Unsupported(UnsupportedReason.INVALID_HASH),
            resolvePlaybackSource(StreamSource.Torrent(base32, null, emptyList()), null),
        )
    }

    @Test
    fun R18_torrentSourceBlankHash_returnsInvalidHash() {
        val source = StreamSource.Torrent("   ", null, emptyList())
        assertEquals(PlaybackSource.Unsupported(UnsupportedReason.INVALID_HASH), resolvePlaybackSource(source, null))
    }

    @Test
    fun R19_torrentSourceNegativeOrNullFileIdx_resultsInNullFileIdx() {
        val neg1 = StreamSource.Torrent(valid40Hex, fileIdx = -1, trackers = emptyList())
        val nullIdx = StreamSource.Torrent(valid40Hex, fileIdx = null, trackers = emptyList())

        val res1 = resolvePlaybackSource(neg1, null) as PlaybackSource.Torrent
        val res2 = resolvePlaybackSource(nullIdx, null) as PlaybackSource.Torrent

        assertEquals(null, res1.request.fileIdx)
        assertEquals(null, res2.request.fileIdx)
    }

    @Test
    fun R20_externalUrlValid_returnsExternal() {
        val source = StreamSource.External("https://site/watch")
        val result = resolvePlaybackSource(source, null)
        assertEquals(PlaybackSource.External("https://site/watch"), result)
    }

    @Test
    fun R21_externalUrlInvalid_returnsBadSchemeOrNoSource() {
        val badScheme = StreamSource.External("javascript:x")
        assertEquals(PlaybackSource.Unsupported(UnsupportedReason.BAD_SCHEME), resolvePlaybackSource(badScheme, null))

        val blank = StreamSource.External("  ")
        assertEquals(PlaybackSource.Unsupported(UnsupportedReason.NO_SOURCE), resolvePlaybackSource(blank, null))
    }

    @Test
    fun R22_directUrlWithHint_hintIgnoredInDirect() {
        val source = StreamSource.Url("https://cdn.example/video.mp4", emptyMap())
        val hint = EpisodeHint(season = 1, episode = 2)
        val result = resolvePlaybackSource(source, hint)
        assertEquals(PlaybackSource.Direct("https://cdn.example/video.mp4"), result)
    }

    @Test
    fun R23_youtubeSource_returnsNoSource() {
        val source = StreamSource.YouTube("abc")
        assertEquals(PlaybackSource.Unsupported(UnsupportedReason.NO_SOURCE), resolvePlaybackSource(source, null))
    }

    @Test
    fun R24_unsupportedSource_returnsNoSource() {
        val source = StreamSource.Unsupported("rar")
        assertEquals(PlaybackSource.Unsupported(UnsupportedReason.NO_SOURCE), resolvePlaybackSource(source, null))
    }
}
