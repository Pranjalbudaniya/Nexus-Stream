package com.nexus.stream.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MagnetLinkTest {
    @Test
    fun buildMagnet_valid40HexHash() {
        val hash = "0123456789abcdef0123456789abcdef01234567"
        val magnet =
            MagnetLink.buildMagnet(
                infoHash = hash,
                displayName = "Big Buck Bunny",
                trackers = listOf("udp://tracker.opentrackr.org:1337/announce", "http://tracker.com/announce?x=1&y=2"),
            )

        val expected =
            "magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567" +
                "&dn=Big%20Buck%20Bunny" +
                "&tr=udp%3A%2F%2Ftracker.opentrackr.org%3A1337%2Fannounce" +
                "&tr=http%3A%2F%2Ftracker.com%2Fannounce%3Fx%3D1%26y%3D2"

        assertEquals(expected, magnet)
    }

    @Test
    fun buildMagnet_valid32Base32Hash() {
        val hash = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        val magnet =
            MagnetLink.buildMagnet(
                infoHash = hash,
                displayName = null,
                trackers = emptyList(),
            )

        assertEquals("magnet:?xt=urn:btih:ABCDEFGHIJKLMNOPQRSTUVWXYZ234567", magnet)
    }

    @Test
    fun buildMagnet_invalidHashReturnsNull() {
        assertNull(MagnetLink.buildMagnet("tooShort", "Title", emptyList()))
        // 'G' is invalid in hex
        assertNull(MagnetLink.buildMagnet("0123456789abcdef0123456789abcdef0123456G", "Title", emptyList()))
        // '8' is invalid in base32
        assertNull(MagnetLink.buildMagnet("ABCDEFGHIJKLMNOPQRSTUVWXYZ234568", "Title", emptyList()))
        assertNull(MagnetLink.buildMagnet("", null, emptyList()))
    }

    @Test
    fun buildMagnet_emptyNameAndNoTrackers() {
        val hash = "0123456789abcdef0123456789abcdef01234567"
        val magnet =
            MagnetLink.buildMagnet(
                infoHash = hash,
                displayName = "   ",
                trackers = emptyList(),
            )
        assertEquals("magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567", magnet)
    }
}
