package com.nexus.stream.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MagnetLinkTest {
    private val sampleHash = "0123456789abcdef0123456789abcdef01234567"

    @Test
    fun G1_standardMagnet_parsesHashAndTrackers() {
        val input =
            "magnet:?xt=urn:btih:$sampleHash&dn=Name&tr=udp%3A%2F%2Ft.example%3A1337%2Fannounce&tr=http%3A%2F%2Fb.example%2Fa"
        val result = parseMagnetLink(input)
        assertNotNull(result)
        assertEquals(sampleHash, result?.infoHash)
        assertEquals(listOf("udp://t.example:1337/announce", "http://b.example/a"), result?.trackers)
    }

    @Test
    fun G2_uppercaseHashInsideMagnet_lowercased() {
        val upperHash = sampleHash.uppercase()
        val input = "magnet:?xt=urn:btih:$upperHash"
        val result = parseMagnetLink(input)
        assertNotNull(result)
        assertEquals(sampleHash, result?.infoHash)
    }

    @Test
    fun G3_rawHexHash_acceptedWithNoTrackers() {
        val result = parseMagnetLink(sampleHash)
        assertEquals(MagnetLink(infoHash = sampleHash, trackers = emptyList()), result)
    }

    @Test
    fun G4_rawHashWithSpacesAndTrailingNewline_accepted() {
        val input = "  $sampleHash \n"
        val result = parseMagnetLink(input)
        assertEquals(MagnetLink(infoHash = sampleHash, trackers = emptyList()), result)
    }

    @Test
    fun G5_btmhOnly_returnsNull() {
        val btmhHash = "12200123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        val input = "magnet:?xt=urn:btmh:$btmhHash"
        val result = parseMagnetLink(input)
        assertNull(result)
    }

    @Test
    fun G6_hybridBtmhFirstThenBtih_usesBtih() {
        val btmhHash = "12200123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        val input = "magnet:?xt=urn:btmh:$btmhHash&xt=urn:btih:$sampleHash"
        val result = parseMagnetLink(input)
        assertNotNull(result)
        assertEquals(sampleHash, result?.infoHash)
    }

    @Test
    fun G7_base32Btih_returnsNull() {
        val base32Hash = "A42Y7F2G6C4M4N5O6P7Q8R9S0T1U2V3W"
        val input = "magnet:?xt=urn:btih:$base32Hash"
        val result = parseMagnetLink(input)
        assertNull(result)
    }

    @Test
    fun G8_httpUrl_returnsNull() {
        val input = "https://example.com/x.torrent"
        val result = parseMagnetLink(input)
        assertNull(result)
    }

    @Test
    fun G9_blankOrEmpty_returnsNull() {
        assertNull(parseMagnetLink(""))
        assertNull(parseMagnetLink("   \n\t"))
    }

    @Test
    fun G10_uppercaseSchemeAndKeys_acceptedOneTracker() {
        val upperHash = sampleHash.uppercase()
        val input = "MAGNET:?XT=URN:BTIH:$upperHash&TR=udp%3A%2F%2Ft.example%3A1%2Fa"
        val result = parseMagnetLink(input)
        assertNotNull(result)
        assertEquals(sampleHash, result?.infoHash)
        assertEquals(listOf("udp://t.example:1/a"), result?.trackers)
    }

    @Test
    fun G11_invalidHashLength_returnsNull() {
        val raw39 = "0123456789abcdef0123456789abcdef0123456"
        val raw41 = "0123456789abcdef0123456789abcdef012345678"
        assertNull(parseMagnetLink(raw39))
        assertNull(parseMagnetLink(raw41))
        assertNull(parseMagnetLink("magnet:?xt=urn:btih:$raw39"))
        assertNull(parseMagnetLink("magnet:?xt=urn:btih:$raw41"))
    }

    @Test
    fun G12_magnetNoTrAndDnWithEscapes_trackersEmpty() {
        val input = "magnet:?xt=urn:btih:$sampleHash&dn=Some%20Cool+Movie"
        val result = parseMagnetLink(input)
        assertNotNull(result)
        assertEquals(sampleHash, result?.infoHash)
        assertEquals(emptyList<String>(), result?.trackers)
    }

    @Test
    fun G13_invalidEscape_dropsTrackerOnly() {
        val input = "magnet:?xt=urn:btih:$sampleHash&tr=%ZZ&tr=udp%3A%2F%2Ft.example%3A1337%2Fannounce"
        val result = parseMagnetLink(input)
        assertNotNull(result)
        assertEquals(sampleHash, result?.infoHash)
        assertEquals(listOf("udp://t.example:1337/announce"), result?.trackers)
    }

    @Test
    fun G14_wrappedAcrossLines_parsedAsOneLine() {
        val input =
            "magnet:?xt=urn:btih:0123456789\nabcdef0123456789abcdef01234567&tr=udp%3A%2F%2Ft.example%3A1337%2F\nannounce"
        val result = parseMagnetLink(input)
        assertNotNull(result)
        assertEquals(sampleHash, result?.infoHash)
        assertEquals(listOf("udp://t.example:1337/announce"), result?.trackers)
    }
}
