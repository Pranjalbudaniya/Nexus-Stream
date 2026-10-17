package com.nexus.stream.domain.usecase

import com.nexus.stream.domain.model.ParsedStream
import com.nexus.stream.domain.model.ReleaseType
import com.nexus.stream.domain.model.Stream
import com.nexus.stream.domain.model.StreamKind
import com.nexus.stream.domain.model.StreamLanguages
import com.nexus.stream.domain.model.StreamMatch
import com.nexus.stream.domain.model.StreamSort
import com.nexus.stream.domain.model.StreamSource
import org.junit.Assert.assertEquals
import org.junit.Test

class StreamSortTest {
    private fun sampleStream(
        key: String,
        resolution: Int? = null,
        seeders: Int? = null,
        sizeBytes: Long? = null,
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
            addonKeys = listOf("addon1"),
            resolution = resolution,
            hdr = emptySet(),
            codec = null,
            sizeBytes = sizeBytes,
            seeders = seeders,
            kind = StreamKind.Torrent,
            needsHeaders = false,
            displayName = key,
            match = StreamMatch.OK,
        )

    @Test
    fun `penalty rows sort last in every mode`() {
        val clean1 = sampleStream("clean1", resolution = 720, seeders = 10, sizeBytes = 1000)
        val clean2 = sampleStream("clean2", resolution = 1080, seeders = 5, sizeBytes = 2000)
        val cam =
            sampleStream(
                "cam",
                resolution = 2160,
                seeders = 100,
                sizeBytes = 5000,
            ).copy(releaseType = ReleaseType.CAM)
        val wrongSeason =
            sampleStream(
                "wrongSeason",
                resolution = 2160,
                seeders = 100,
                sizeBytes = 5000,
            ).copy(match = StreamMatch.WRONG_SEASON)
        val wrongEpisode =
            sampleStream(
                "wrongEp",
                resolution = 2160,
                seeders = 100,
                sizeBytes = 5000,
            ).copy(match = StreamMatch.WRONG_EPISODE)
        val wrongYear =
            sampleStream(
                "wrongYear",
                resolution = 2160,
                seeders = 100,
                sizeBytes = 5000,
            ).copy(match = StreamMatch.WRONG_YEAR)

        val all = listOf(cam, wrongSeason, clean1, wrongEpisode, clean2, wrongYear)

        for (mode in StreamSort.entries) {
            val sorted = StreamOrdering.sort(all, sort = mode)
            val nonPenalized = sorted.take(2).toSet()
            val penalized = sorted.drop(2).toSet()

            assertEquals("Clean items must be first in $mode", setOf(clean1, clean2), nonPenalized)
            assertEquals(
                "Penalized items must be last in $mode",
                setOf(cam, wrongSeason, wrongEpisode, wrongYear),
                penalized,
            )
        }
    }

    @Test
    fun `BEST sort with preferred language and quality boosts`() {
        val s2160En =
            sampleStream(
                "2160En",
                resolution = 2160,
            ).copy(languages = StreamLanguages(names = listOf("English")))
        val s1080En =
            sampleStream(
                "1080En",
                resolution = 1080,
            ).copy(languages = StreamLanguages(names = listOf("English")))
        val s720En =
            sampleStream(
                "720En",
                resolution = 720,
            ).copy(languages = StreamLanguages(names = listOf("English")))
        val s1080Fr =
            sampleStream(
                "1080Fr",
                resolution = 1080,
            ).copy(languages = StreamLanguages(names = listOf("French")))
        val s2160Fr =
            sampleStream(
                "2160Fr",
                resolution = 2160,
            ).copy(languages = StreamLanguages(names = listOf("French")))

        val list = listOf(s2160Fr, s720En, s1080Fr, s2160En, s1080En)

        val sorted =
            StreamOrdering.sort(
                list,
                sort = StreamSort.BEST,
                preferredLanguage = "en",
                preferredQuality = 1080,
            )
        assertEquals(listOf(s1080En, s720En, s2160En, s1080Fr, s2160Fr), sorted)
    }

    @Test
    fun `preferred boosts apply only in BEST`() {
        val s1080Fr =
            sampleStream(
                "1080Fr",
                resolution = 1080,
                seeders = 50,
            ).copy(languages = StreamLanguages(names = listOf("French")))
        val s720En =
            sampleStream(
                "720En",
                resolution = 720,
                seeders = 10,
            ).copy(languages = StreamLanguages(names = listOf("English")))
        val list = listOf(s720En, s1080Fr)

        val qualitySorted =
            StreamOrdering.sort(
                list,
                sort = StreamSort.QUALITY,
                preferredLanguage = "en",
                preferredQuality = 720,
            )
        assertEquals(listOf(s1080Fr, s720En), qualitySorted)

        val seederSorted =
            StreamOrdering.sort(
                list,
                sort = StreamSort.SEEDERS,
                preferredLanguage = "en",
                preferredQuality = 720,
            )
        assertEquals(listOf(s1080Fr, s720En), seederSorted)
    }

    @Test
    fun `QUALITY sort resolution desc, seeders desc, size desc, stable`() {
        val s1080HighSeeds = sampleStream("1080High", resolution = 1080, seeders = 100, sizeBytes = 1000)
        val s1080LowSeeds = sampleStream("1080Low", resolution = 1080, seeders = 10, sizeBytes = 2000)
        val s720 = sampleStream("720", resolution = 720, seeders = 500, sizeBytes = 500)
        val sNull = sampleStream("null", resolution = null, seeders = 1000, sizeBytes = 500)

        val sorted = StreamOrdering.sort(listOf(sNull, s720, s1080LowSeeds, s1080HighSeeds), sort = StreamSort.QUALITY)
        assertEquals(listOf(s1080HighSeeds, s1080LowSeeds, s720, sNull), sorted)
    }

    @Test
    fun `SEEDERS sort seeders desc with null last, then resolution desc`() {
        val s100 = sampleStream("100", seeders = 100, resolution = 720)
        val s50Res1080 = sampleStream("50_1080", seeders = 50, resolution = 1080)
        val s50Res720 = sampleStream("50_720", seeders = 50, resolution = 720)
        val sNull = sampleStream("nullSeeds", seeders = null, resolution = 2160)

        val sorted = StreamOrdering.sort(listOf(sNull, s50Res720, s100, s50Res1080), sort = StreamSort.SEEDERS)
        assertEquals(listOf(s100, s50Res1080, s50Res720, sNull), sorted)
    }

    @Test
    fun `SIZE sort size desc with null last, then resolution desc`() {
        val sBig = sampleStream("big", sizeBytes = 10_000, resolution = 720)
        val sMed1080 = sampleStream("med1080", sizeBytes = 5_000, resolution = 1080)
        val sMed720 = sampleStream("med720", sizeBytes = 5_000, resolution = 720)
        val sNull = sampleStream("nullSize", sizeBytes = null, resolution = 2160)

        val sorted = StreamOrdering.sort(listOf(sNull, sMed720, sBig, sMed1080), sort = StreamSort.SIZE)
        assertEquals(listOf(sBig, sMed1080, sMed720, sNull), sorted)
    }
}
