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
            addonKeys = emptyList(),
            resolution = resolution,
            hdr = emptySet(),
            codec = null,
            sizeBytes = sizeBytes,
            seeders = seeders,
            kind = kind,
            needsHeaders = false,
            displayName = "Test Stream",
            releaseType = null,
            match = com.nexus.stream.domain.model.StreamMatch.OK,
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

    @Test
    fun sort_penalizedItemsSortAfterCleanMatches() {
        val clean1080 = createParsedStream("clean1080", resolution = 1080)
        val clean720 = createParsedStream("clean720", resolution = 720)
        val wrongSeason2160 =
            createParsedStream("wrongSeason2160", resolution = 2160)
                .copy(match = com.nexus.stream.domain.model.StreamMatch.WRONG_SEASON)
        val cam2160 =
            createParsedStream("cam2160", resolution = 2160)
                .copy(releaseType = com.nexus.stream.domain.model.ReleaseType.CAM)

        val sorted = StreamOrdering.sort(listOf(wrongSeason2160, cam2160, clean1080, clean720))

        assertEquals("clean1080", sorted[0].key)
        assertEquals("clean720", sorted[1].key)
        // Next are penalized, sorted by resolution
        assertEquals(2160, sorted[2].resolution)
        assertEquals(2160, sorted[3].resolution)
    }

    @Test
    fun bestPlayable_skipsPenalizedItems_unlessNothingElse() {
        val caps = PlaybackCapabilities(supportsTorrent = true)
        val clean720 = createParsedStream("clean720", resolution = 720)
        val cam1080 =
            createParsedStream("cam1080", resolution = 1080)
                .copy(releaseType = com.nexus.stream.domain.model.ReleaseType.CAM)

        // Given both, clean720 is chosen despite lower resolution
        val bestWithBoth = StreamOrdering.bestPlayable(listOf(cam1080, clean720), caps)
        assertEquals("clean720", bestWithBoth?.key)

        // When only penalized is available, it is chosen as fallback
        val bestOnlyCam = StreamOrdering.bestPlayable(listOf(cam1080), caps)
        assertEquals("cam1080", bestOnlyCam?.key)
    }

    @Test
    fun crossFilterCounts_andFilterByAddon() {
        val s1 = createParsedStream("s1", resolution = 1080).copy(addonKeys = listOf("addon1"))
        val s2 = createParsedStream("s2", resolution = 720).copy(addonKeys = listOf("addon1"))
        val s3 = createParsedStream("s3", resolution = 1080).copy(addonKeys = listOf("addon2"))
        val all = listOf(s1, s2, s3)

        // filterByAddon
        assertEquals(listOf(s1, s2), StreamOrdering.filterByAddon(all, "addon1"))
        assertEquals(listOf(s3), StreamOrdering.filterByAddon(all, "addon2"))
        assertEquals(all, StreamOrdering.filterByAddon(all, null))

        // combined filter
        assertEquals(listOf(s1), StreamOrdering.filter(all, resolution = 1080, addonKey = "addon1"))
        assertEquals(emptyList<ParsedStream>(), StreamOrdering.filter(all, resolution = 720, addonKey = "addon2"))

        // resolutionCounts when addon1 is selected
        val resCounts = StreamFiltering.resolutionCounts(all, selectedAddon = "addon1")
        assertEquals(1, resCounts[1080])
        assertEquals(1, resCounts[720])

        // addonCounts when resolution 1080 is selected
        val addonCounts =
            StreamFiltering.addonCounts(
                list = all,
                selectedResolution = 1080,
                allAddonIdentifiers = listOf("addon1", "addon2"),
            )
        assertEquals(1, addonCounts["addon1"])
        assertEquals(1, addonCounts["addon2"])
    }
}
