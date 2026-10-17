package com.nexus.stream.domain.usecase

import com.nexus.stream.domain.model.ParsedStream
import com.nexus.stream.domain.model.PlaybackCapabilities
import com.nexus.stream.domain.model.Stream
import com.nexus.stream.domain.model.StreamKind
import com.nexus.stream.domain.model.StreamSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamOrderingTest {
    private fun createParsedStream(
        key: String,
        resolution: Int? = null,
        seeders: Int? = null,
        sizeBytes: Long? = null,
        kind: StreamKind = StreamKind.Direct,
    ): ParsedStream =
        ParsedStream(
            key = key,
            stream =
                Stream(
                    source = StreamSource.Url("https://example.com/test", emptyMap()),
                    name = "Test",
                    description = null,
                    subtitles = emptyList(),
                    bingeGroup = null,
                    notWebReady = false,
                    videoSize = null,
                    filename = null,
                    videoHash = null,
                ),
            addonNames = listOf("A"),
            resolution = resolution,
            hdr = emptySet(),
            codec = null,
            sizeBytes = sizeBytes,
            seeders = seeders,
            kind = kind,
            needsHeaders = false,
            displayName = "Test Stream",
        )

    @Test
    fun dedupe_mergesAddonNamesAndKeepsFirstWinner() {
        val stream1 = createParsedStream("t:hash123:0", resolution = 1080).copy(addonNames = listOf("Addon A"))
        val stream2 = createParsedStream("t:hash123:0", resolution = 720).copy(addonNames = listOf("Addon B"))
        val stream3 = createParsedStream("t:hash123:-1", resolution = 1080).copy(addonNames = listOf("Addon C"))
        val stream4 = createParsedStream("u:https://direct.url").copy(addonNames = listOf("Addon D"))
        val stream5 = createParsedStream("u:https://direct.url").copy(addonNames = listOf("Addon E"))

        val deduped = StreamOrdering.dedupe(listOf(stream1, stream2, stream3, stream4, stream5))

        assertEquals(3, deduped.size)
        assertEquals(listOf("Addon A", "Addon B"), deduped[0].addonNames)
        assertEquals(1080, deduped[0].resolution) // first wins for attributes
        assertEquals("t:hash123:-1", deduped[1].key)
        assertEquals(listOf("Addon D", "Addon E"), deduped[2].addonNames)
    }

    @Test
    fun sort_resolutionDescending_thenSeeders_thenSize_nullsLast() {
        val s1 = createParsedStream("k1", resolution = 1080, seeders = 50, sizeBytes = 2000L)
        val s2 = createParsedStream("k2", resolution = 2160, seeders = 10, sizeBytes = 5000L)
        val s3 = createParsedStream("k3", resolution = 1080, seeders = 100, sizeBytes = 1500L)
        val s4 = createParsedStream("k4", resolution = 1080, seeders = 100, sizeBytes = 3000L)
        val s5 = createParsedStream("k5", resolution = null, seeders = 200, sizeBytes = 1000L)
        val s6 = createParsedStream("k6", resolution = 720, seeders = null, sizeBytes = 500L)
        val s7 = createParsedStream("k7", resolution = 720, seeders = null, sizeBytes = null)

        val sorted = StreamOrdering.sort(listOf(s1, s2, s3, s4, s5, s6, s7))

        // Order:
        // 1. 2160 (s2)
        // 2. 1080 with 100 seeders and 3000L size (s4)
        // 3. 1080 with 100 seeders and 1500L size (s3)
        // 4. 1080 with 50 seeders (s1)
        // 5. 720 with size 500L (s6)
        // 6. 720 with size null (s7)
        // 7. null resolution (s5)
        assertEquals("k2", sorted[0].key)
        assertEquals("k4", sorted[1].key)
        assertEquals("k3", sorted[2].key)
        assertEquals("k1", sorted[3].key)
        assertEquals("k6", sorted[4].key)
        assertEquals("k7", sorted[5].key)
        assertEquals("k5", sorted[6].key)
    }

    @Test
    fun filterByResolution_andAvailableResolutions() {
        val s1 = createParsedStream("k1", resolution = 1080)
        val s2 = createParsedStream("k2", resolution = 2160)
        val s3 = createParsedStream("k3", resolution = 720)
        val s4 = createParsedStream("k4", resolution = 1080)
        val s5 = createParsedStream("k5", resolution = null)

        val list = listOf(s1, s2, s3, s4, s5)
        val available = StreamOrdering.availableResolutions(list)
        assertEquals(listOf(2160, 1080, 720), available)

        val filtered1080 = StreamOrdering.filterByResolution(list, 1080)
        assertEquals(listOf(s1, s4), filtered1080)

        val filteredAll = StreamOrdering.filterByResolution(list, null)
        assertEquals(list, filteredAll)
    }

    @Test
    fun bestPlayable_respectsCapabilitiesAndKind() {
        val direct = createParsedStream("k1", kind = StreamKind.Direct)
        val torrent = createParsedStream("k2", kind = StreamKind.Torrent)
        val external = createParsedStream("k3", kind = StreamKind.ExternalLink)

        // Without torrent support:
        val noTorrentCaps = PlaybackCapabilities(supportsTorrent = false)
        val best1 = StreamOrdering.bestPlayable(listOf(external, torrent, direct), noTorrentCaps)
        assertNotNull(best1)
        assertEquals("k1", best1!!.key)

        val bestOnlyTorrent = StreamOrdering.bestPlayable(listOf(external, torrent), noTorrentCaps)
        assertNull(bestOnlyTorrent)

        // With torrent support:
        val withTorrentCaps = PlaybackCapabilities(supportsTorrent = true)
        val bestTorrent = StreamOrdering.bestPlayable(listOf(external, torrent, direct), withTorrentCaps)
        assertNotNull(bestTorrent)
        assertEquals("k2", bestTorrent!!.key)

        // Only external links:
        val bestOnlyExternal = StreamOrdering.bestPlayable(listOf(external), withTorrentCaps)
        assertNull(bestOnlyExternal)
    }

    @Test
    fun isFamilySafe_detectsAdultKeywords() {
        val safeStream =
            Stream(
                source = StreamSource.Url("https://safe.url", emptyMap()),
                name = "Big Buck Bunny",
                description = "Family animation",
                subtitles = emptyList(),
                bingeGroup = null,
                notWebReady = false,
                videoSize = null,
                filename = "bbb.mp4",
                videoHash = null,
            )
        assertTrue(StreamOrdering.isFamilySafe(safeStream))

        val adultStream = safeStream.copy(name = "Adult XXX Video 1080p")
        assertFalse(StreamOrdering.isFamilySafe(adultStream))
    }
}
