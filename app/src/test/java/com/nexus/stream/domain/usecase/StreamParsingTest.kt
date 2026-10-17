package com.nexus.stream.domain.usecase

import com.nexus.stream.domain.model.Codec
import com.nexus.stream.domain.model.HdrKind
import com.nexus.stream.domain.model.Stream
import com.nexus.stream.domain.model.StreamKind
import com.nexus.stream.domain.model.StreamSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamParsingTest {
    @Test
    fun parseResolution_matchesExpectedResolutions() {
        assertEquals(2160, StreamParsing.parseResolution("Movie 2160p Remux"))
        assertEquals(2160, StreamParsing.parseResolution("Movie 4k HDR"))
        assertEquals(2160, StreamParsing.parseResolution("Movie UHD"))
        assertEquals(1080, StreamParsing.parseResolution("Movie 1080p BluRay"))
        assertEquals(720, StreamParsing.parseResolution("Movie 720p WEB-DL"))
        assertEquals(480, StreamParsing.parseResolution("Movie 480p DVD"))
        assertEquals(480, StreamParsing.parseResolution("Movie 360p"))
        assertEquals(480, StreamParsing.parseResolution("Movie SD"))

        // Word boundary: 1720p is not 720p
        assertNull(StreamParsing.parseResolution("Movie 1720p test"))
        assertNull(StreamParsing.parseResolution("Movie unknown quality"))
    }

    @Test
    fun parseHdr_matchesHdrVariations() {
        val hdr10Plus = StreamParsing.parseHdr("Movie 4k HDR10+ DV")
        assertTrue(hdr10Plus.contains(HdrKind.HDR10_PLUS))
        assertTrue(hdr10Plus.contains(HdrKind.DOLBY_VISION))
        assertFalse(hdr10Plus.contains(HdrKind.HDR10))

        val hdr10 = StreamParsing.parseHdr("Movie 1080p HDR10")
        assertTrue(hdr10.contains(HdrKind.HDR10))
        assertFalse(hdr10.contains(HdrKind.HDR10_PLUS))

        val hdr = StreamParsing.parseHdr("Movie 4K HDR Dolby Vision")
        assertTrue(hdr.contains(HdrKind.HDR))
        assertTrue(hdr.contains(HdrKind.DOLBY_VISION))

        val empty = StreamParsing.parseHdr("Standard SDR release")
        assertTrue(empty.isEmpty())
    }

    @Test
    fun parseCodec_matchesExpectedCodecs() {
        assertEquals(Codec.HEVC, StreamParsing.parseCodec("Movie x265"))
        assertEquals(Codec.HEVC, StreamParsing.parseCodec("Movie HEVC"))
        assertEquals(Codec.HEVC, StreamParsing.parseCodec("Movie H.265"))
        assertEquals(Codec.HEVC, StreamParsing.parseCodec("Movie H265"))

        assertEquals(Codec.AVC, StreamParsing.parseCodec("Movie x264"))
        assertEquals(Codec.AVC, StreamParsing.parseCodec("Movie H.264"))
        assertEquals(Codec.AVC, StreamParsing.parseCodec("Movie H264"))
        assertEquals(Codec.AVC, StreamParsing.parseCodec("Movie AVC"))

        assertEquals(Codec.AV1, StreamParsing.parseCodec("Movie AV1"))
        assertNull(StreamParsing.parseCodec("Movie MPEG2"))
    }

    @Test
    fun parseSeeders_matchesEmojiAndTextFormats() {
        assertEquals(12, StreamParsing.parseSeeders("Torrent stream\n👤 12 | 2.5 GB"))
        assertEquals(34, StreamParsing.parseSeeders("Torrent stream\nSeeders: 34"))
        assertEquals(5, StreamParsing.parseSeeders("Torrent stream\nseeds=5"))
        assertEquals(100, StreamParsing.parseSeeders("Torrent stream\nseeder 100"))
        assertNull(StreamParsing.parseSeeders("No seeders info here"))
    }

    @Test
    fun parseSize_handlesVideoSizeAndTextWithDecimals() {
        val dummyStream =
            Stream(
                source = StreamSource.Url("https://nexus.stream/video.mp4", emptyMap()),
                name = null,
                description = null,
                subtitles = emptyList(),
                bingeGroup = null,
                notWebReady = false,
                videoSize = 5_000_000_000L,
                filename = null,
                videoHash = null,
            )

        // videoSize takes precedence
        assertEquals(5_000_000_000L, StreamParsing.parseSize(dummyStream, "1.5 GB"))

        val streamWithoutVideoSize = dummyStream.copy(videoSize = null)
        val gbBytes = (2.1 * 1024L * 1024L * 1024L).toLong()
        assertEquals(gbBytes, StreamParsing.parseSize(streamWithoutVideoSize, "2.1 GB"))

        val commaGbBytes = (1.5 * 1024L * 1024L * 1024L).toLong()
        assertEquals(commaGbBytes, StreamParsing.parseSize(streamWithoutVideoSize, "1,5 GiB"))

        val mbBytes = (700L * 1024L * 1024L)
        assertEquals(mbBytes, StreamParsing.parseSize(streamWithoutVideoSize, "700 MiB"))
        assertEquals(mbBytes, StreamParsing.parseSize(streamWithoutVideoSize, "700 MB"))

        assertNull(StreamParsing.parseSize(streamWithoutVideoSize, "No size info"))
    }

    @Test
    fun resolveDisplayName_picksFirstNonBlankInOrder() {
        val stream1 =
            Stream(
                source = StreamSource.Url("https://nexus.stream/video.mp4", emptyMap()),
                name = "\n  Torrent Name \n Extra",
                description = "Description line",
                subtitles = emptyList(),
                bingeGroup = null,
                notWebReady = false,
                videoSize = null,
                filename = "movie.mkv",
                videoHash = null,
            )
        assertEquals("Torrent Name", StreamParsing.resolveDisplayName(stream1))

        val stream2 = stream1.copy(name = "   \n")
        assertEquals("Description line", StreamParsing.resolveDisplayName(stream2))

        val stream3 = stream2.copy(description = "")
        assertEquals("movie.mkv", StreamParsing.resolveDisplayName(stream3))

        val stream4 = stream3.copy(filename = null)
        assertEquals("Stream", StreamParsing.resolveDisplayName(stream4))
    }

    @Test
    fun parse_fullStreamMapping() {
        val stream =
            Stream(
                source =
                    StreamSource.Torrent(
                        infoHash = "0123456789ABCDEF0123456789ABCDEF01234567",
                        fileIdx = 0,
                        trackers = listOf("udp://tracker.opentrackr.org:1337"),
                    ),
                name = "Big Buck Bunny 1080p HEVC HDR",
                description = "👤 45 | 1.8 GB",
                subtitles = emptyList(),
                bingeGroup = null,
                notWebReady = false,
                videoSize = null,
                filename = "bbb.mkv",
                videoHash = null,
            )

        val parsed = StreamParsing.parse(stream, "Torrent Addon")
        assertNotNull(parsed)
        assertEquals("t:0123456789abcdef0123456789abcdef01234567:0", parsed!!.key)
        assertEquals(listOf("Torrent Addon"), parsed.addonNames)
        assertEquals(1080, parsed.resolution)
        assertEquals(Codec.HEVC, parsed.codec)
        assertTrue(parsed.hdr.contains(HdrKind.HDR))
        assertEquals(45, parsed.seeders)
        assertEquals((1.8 * 1024L * 1024L * 1024L).toLong(), parsed.sizeBytes)
        assertEquals(StreamKind.Torrent, parsed.kind)
        assertFalse(parsed.needsHeaders)
        assertEquals("Big Buck Bunny 1080p HEVC HDR", parsed.displayName)
    }

    @Test
    fun parse_unsupportedSourcesReturnNull() {
        val ytStream =
            Stream(
                source = StreamSource.YouTube("dQw4w9WgXcQ"),
                name = "Rick Roll",
                description = null,
                subtitles = emptyList(),
                bingeGroup = null,
                notWebReady = false,
                videoSize = null,
                filename = null,
                videoHash = null,
            )
        assertNull(StreamParsing.parse(ytStream, "YT Addon"))

        val unsupportedStream = ytStream.copy(source = StreamSource.Unsupported("nzb"))
        assertNull(StreamParsing.parse(unsupportedStream, "Usenet Addon"))
    }
}
