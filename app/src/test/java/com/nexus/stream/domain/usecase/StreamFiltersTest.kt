package com.nexus.stream.domain.usecase

import com.nexus.stream.domain.model.DebridMark
import com.nexus.stream.domain.model.ParsedStream
import com.nexus.stream.domain.model.ReleaseType
import com.nexus.stream.domain.model.Stream
import com.nexus.stream.domain.model.StreamFilters
import com.nexus.stream.domain.model.StreamKind
import com.nexus.stream.domain.model.StreamLanguages
import com.nexus.stream.domain.model.StreamMatch
import com.nexus.stream.domain.model.StreamSource
import com.nexus.stream.domain.model.TypeFilter
import org.junit.Assert.assertEquals
import org.junit.Test

class StreamFiltersTest {
    private fun sampleStream(
        key: String,
        resolution: Int? = null,
        addonKey: String = "addon1",
        kind: StreamKind = StreamKind.Torrent,
    ): ParsedStream =
        ParsedStream(
            key = key,
            stream =
                Stream(
                    source = StreamSource.Url("https://example.com/test", emptyMap()),
                    name = key,
                    description = null,
                    subtitles = emptyList(),
                    bingeGroup = null,
                    notWebReady = false,
                    videoSize = null,
                    filename = null,
                    videoHash = null,
                ),
            addonNames = listOf("Addon 1"),
            addonKeys = listOf(addonKey),
            resolution = resolution,
            hdr = emptySet(),
            codec = null,
            sizeBytes = 1000L,
            seeders = 10,
            kind = kind,
            needsHeaders = false,
            displayName = key,
            match = StreamMatch.OK,
        )

    @Test
    fun `resolution filter alone`() {
        val s1 = sampleStream("1", resolution = 1080)
        val s2 = sampleStream("2", resolution = 720)
        val s3 = sampleStream("3", resolution = null)
        val list = listOf(s1, s2, s3)

        val result1080 = StreamOrdering.applyFilters(list, StreamFilters(resolution = 1080))
        assertEquals(listOf(s1), result1080)

        val resultAll = StreamOrdering.applyFilters(list, StreamFilters(resolution = null))
        assertEquals(list, resultAll)
    }

    @Test
    fun `addon filter alone`() {
        val s1 = sampleStream("1", addonKey = "addonA")
        val s2 = sampleStream("2", addonKey = "addonB")
        val list = listOf(s1, s2)

        val resultA = StreamOrdering.applyFilters(list, StreamFilters(addonKey = "addonA"))
        assertEquals(listOf(s1), resultA)
    }

    @Test
    fun `language filter rule - match, multi, dualAudio, unknown`() {
        val enStream = sampleStream("en").copy(languages = StreamLanguages(names = listOf("English")))
        val frStream = sampleStream("fr").copy(languages = StreamLanguages(names = listOf("French")))
        val multiStream = sampleStream("multi").copy(languages = StreamLanguages(multi = true))
        val dualStream = sampleStream("dual").copy(languages = StreamLanguages(dualAudio = true))
        val unknownStream = sampleStream("unknown")
        val list = listOf(enStream, frStream, multiStream, dualStream, unknownStream)

        val resultEn = StreamOrdering.applyFilters(list, StreamFilters(language = "en"))
        assertEquals(listOf(enStream, multiStream, dualStream), resultEn)

        val resultAny = StreamOrdering.applyFilters(list, StreamFilters(language = null))
        assertEquals(list, resultAny)
    }

    @Test
    fun `type filter rule - Direct, Torrent, ExternalLink only under ALL`() {
        val direct = sampleStream("direct", kind = StreamKind.Direct)
        val torrent = sampleStream("torrent", kind = StreamKind.Torrent)
        val link = sampleStream("link", kind = StreamKind.ExternalLink)
        val list = listOf(direct, torrent, link)

        val all = StreamOrdering.applyFilters(list, StreamFilters(type = TypeFilter.ALL))
        assertEquals(listOf(direct, torrent, link), all)

        val directOnly = StreamOrdering.applyFilters(list, StreamFilters(type = TypeFilter.DIRECT))
        assertEquals(listOf(direct), directOnly)

        val torrentOnly = StreamOrdering.applyFilters(list, StreamFilters(type = TypeFilter.TORRENT))
        assertEquals(listOf(torrent), torrentOnly)
    }

    @Test
    fun `cachedOnly filter`() {
        val cached = sampleStream("cached").copy(debrid = DebridMark("RD", cached = true))
        val uncached = sampleStream("uncached")
        val list = listOf(cached, uncached)

        val result = StreamOrdering.applyFilters(list, StreamFilters(cachedOnly = true))
        assertEquals(listOf(cached), result)

        val all = StreamOrdering.applyFilters(list, StreamFilters(cachedOnly = false))
        assertEquals(list, all)
    }

    @Test
    fun `hideCam filter`() {
        val cam = sampleStream("cam").copy(releaseType = ReleaseType.CAM)
        val bluray = sampleStream("bluray").copy(releaseType = ReleaseType.BLURAY)
        val list = listOf(cam, bluray)

        val hidden = StreamOrdering.applyFilters(list, StreamFilters(), hideCam = true)
        assertEquals(listOf(bluray), hidden)

        val shown = StreamOrdering.applyFilters(list, StreamFilters(), hideCam = false)
        assertEquals(list, shown)
    }

    @Test
    fun `all filters combined`() {
        val match =
            sampleStream("match", resolution = 1080, addonKey = "addon1", kind = StreamKind.Torrent).copy(
                languages = StreamLanguages(names = listOf("English")),
                debrid = DebridMark("RD", cached = true),
                releaseType = ReleaseType.WEB_DL,
            )
        val wrongRes = match.copy(key = "wrongRes", resolution = 720)
        val wrongAddon = match.copy(key = "wrongAddon", addonKeys = listOf("addon2"), addonNames = listOf("Addon 2"))
        val wrongType = match.copy(key = "wrongType", kind = StreamKind.Direct)
        val wrongLang = match.copy(key = "wrongLang", languages = StreamLanguages(names = listOf("French")))
        val uncached = match.copy(key = "uncached", debrid = null)
        val cam = match.copy(key = "cam", releaseType = ReleaseType.CAM)

        val list = listOf(match, wrongRes, wrongAddon, wrongType, wrongLang, uncached, cam)

        val filters =
            StreamFilters(
                resolution = 1080,
                addonKey = "addon1",
                language = "en",
                type = TypeFilter.TORRENT,
                cachedOnly = true,
            )
        val result = StreamOrdering.applyFilters(list, filters, hideCam = true)
        assertEquals(listOf(match), result)
    }
}
