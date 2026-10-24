package com.nexus.stream.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceFingerprintTest {
    private fun createStream(
        source: StreamSource = StreamSource.Torrent("ABC123DEF", 0, emptyList()),
        addonKeys: List<String> = listOf("b", "a", "a"),
        kind: StreamKind = StreamKind.Torrent,
        match: StreamMatch = StreamMatch.OK,
    ): ParsedStream =
        ParsedStream(
            key = "stream-1",
            stream =
                Stream(
                    source = source,
                    name = "Stream 1",
                    description = null,
                    subtitles = emptyList(),
                    bingeGroup = "g",
                    notWebReady = false,
                    videoSize = 2_500_000_000L,
                    filename = null,
                    videoHash = null,
                ),
            addonNames = listOf("Addon 1"),
            addonKeys = addonKeys,
            resolution = 1080,
            hdr = setOf(HdrKind.HDR, HdrKind.DOLBY_VISION),
            codec = Codec.HEVC,
            sizeBytes = 2_500_000_000L,
            seeders = 10,
            kind = kind,
            needsHeaders = false,
            displayName = "Stream 1",
            match = match,
        )

    @Test
    fun FP1_hashAddonKey_isDeterministic_sixteenHexChars_andHidesTheKey() {
        val key1 = "https://addon-domain.com/manifest.json"
        val key2 = "https://other-addon.com/manifest.json"

        val hash1 = hashAddonKey(key1)
        val hash1Again = hashAddonKey(key1)
        val hash2 = hashAddonKey(key2)

        assertEquals(hash1, hash1Again)
        assertEquals(16, hash1.length)
        assertTrue(hash1.matches(Regex("^[0-9a-f]{16}$")))
        assertTrue(hash2.matches(Regex("^[0-9a-f]{16}$")))
        assertFalse(hash1 == hash2)
        assertFalse(hash1.contains(key1))
        assertFalse(hash1.contains("addon"))
    }

    @Test
    fun FP2_fingerprintOf_torrentStream_fillsEveryField() {
        val stream = createStream()
        val fp = fingerprintOf(stream)

        assertNotNull(fp)
        requireNotNull(fp)

        val expectedAddonHashes = listOf(hashAddonKey("a"), hashAddonKey("b")).sorted()
        assertEquals(expectedAddonHashes, fp.addonKeyHashes)
        assertEquals("Torrent", fp.kind)
        assertEquals("g", fp.bingeGroup)
        assertEquals("abc123def", fp.infoHash)
        assertEquals(1080, fp.resolution)
        assertEquals("HEVC", fp.codec)
        assertEquals(listOf("DOLBY_VISION", "HDR"), fp.hdr)
        assertEquals(2_500_000_000L, fp.sizeBytes)
    }

    @Test
    fun FP3_fingerprintOf_urlStream_hasNoInfoHash() {
        val urlSource = StreamSource.Url("https://example.com/stream.mp4", emptyMap())
        val stream = createStream(source = urlSource, kind = StreamKind.Direct)
        val fp = fingerprintOf(stream)

        assertNotNull(fp)
        requireNotNull(fp)

        assertNull(fp.infoHash)
        assertEquals("Direct", fp.kind)
        assertEquals(1080, fp.resolution)
    }

    @Test
    fun FP4_fingerprintOf_returnsNull_forNoAddonKeys_external_andMismatch() {
        val noAddons = createStream(addonKeys = emptyList())
        assertNull(fingerprintOf(noAddons))

        val external =
            createStream(source = StreamSource.External("https://example.com/v.mp4"), kind = StreamKind.ExternalLink)
        assertNull(fingerprintOf(external))

        val mismatch = createStream(match = StreamMatch.WRONG_SEASON)
        assertNull(fingerprintOf(mismatch))
    }

    @Test
    fun FP5_toString_hidesHashesAndKeys() {
        val stream = createStream()
        val fp = requireNotNull(fingerprintOf(stream))
        val str = fp.toString()

        assertFalse(str.contains(fp.addonKeyHashes.first()))
        assertFalse(str.contains(requireNotNull(fp.infoHash)))
        assertTrue(str.contains("Torrent"))
        assertTrue(str.contains("1080"))
    }

    @Test
    fun FP6_json_roundTrip_ignoresUnknownKeys_andCorruptTextGivesNull() {
        val stream = createStream()
        val fp = requireNotNull(fingerprintOf(stream))

        val encoded = SourceFingerprintJson.encode(fp)
        val decoded = SourceFingerprintJson.decode(encoded)
        assertEquals(fp, decoded)

        val jsonWithExtra =
            """
            {
                "addonKeyHashes": ${encoded.substringAfter("\"addonKeyHashes\":").substringBefore(",\"kind\"")},
                "kind": "Torrent",
                "bingeGroup": "g",
                "infoHash": "abc123def",
                "resolution": 1080,
                "codec": "HEVC",
                "hdr": ["DOLBY_VISION", "HDR"],
                "sizeBytes": 2500000000,
                "unknownField": "should be ignored"
            }
            """.trimIndent()
        val decodedWithExtra = SourceFingerprintJson.decode(jsonWithExtra)
        assertEquals(fp, decodedWithExtra)

        assertNull(SourceFingerprintJson.decode(null))
        assertNull(SourceFingerprintJson.decode(""))
        assertNull(SourceFingerprintJson.decode("   "))
        assertNull(SourceFingerprintJson.decode("{not json"))
    }
}
