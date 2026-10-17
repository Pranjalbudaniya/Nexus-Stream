package com.nexus.stream.presentation.feature.streams

import com.nexus.stream.domain.model.Addon
import com.nexus.stream.domain.model.AddonManifest
import com.nexus.stream.domain.model.AddonSource
import com.nexus.stream.domain.model.DebridMark
import com.nexus.stream.domain.model.ManifestHints
import com.nexus.stream.domain.model.ParsedStream
import com.nexus.stream.domain.model.Stream
import com.nexus.stream.domain.model.StreamFilters
import com.nexus.stream.domain.model.StreamKind
import com.nexus.stream.domain.model.StreamLanguages
import com.nexus.stream.domain.model.StreamMatch
import com.nexus.stream.domain.model.StreamSource
import com.nexus.stream.domain.model.TypeFilter
import com.nexus.stream.domain.repository.StreamsState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamsFilterReducerTest {
    private fun sampleAddon(
        transportUrl: String,
        name: String,
    ): Addon =
        Addon(
            transportUrl = transportUrl,
            manifest =
                AddonManifest(
                    id = transportUrl,
                    version = "1.0.0",
                    name = name,
                    description = "test",
                    logo = null,
                    background = null,
                    types = listOf("movie", "series"),
                    idPrefixes = null,
                    resources = emptyList(),
                    catalogs = emptyList(),
                    behaviorHints =
                        ManifestHints(
                            adult = false,
                            p2p = true,
                            configurable = false,
                            configurationRequired = false,
                        ),
                ),
            enabled = true,
            position = 0,
            source = AddonSource.LOCAL,
            installedAt = 0L,
        )

    private fun sampleStream(
        key: String,
        resolution: Int? = null,
        addonKey: String = "http://addon1",
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
    fun `counts respect other filters live`() {
        val s1 =
            sampleStream("1", resolution = 1080, addonKey = "http://addon1", kind = StreamKind.Torrent).copy(
                languages = StreamLanguages(names = listOf("English")),
                debrid = DebridMark("RD", cached = true),
            )
        val s2 =
            sampleStream("2", resolution = 720, addonKey = "http://addon1", kind = StreamKind.Torrent).copy(
                languages = StreamLanguages(names = listOf("English")),
            )
        val s3 =
            sampleStream("3", resolution = 1080, addonKey = "http://addon2", kind = StreamKind.Direct).copy(
                languages = StreamLanguages(names = listOf("French")),
            )

        val addons = listOf(sampleAddon("http://addon1", "A1"), sampleAddon("http://addon2", "A2"))
        val state =
            StreamsFilterReducer.buildEmissionState(
                state = StreamsUiState(),
                emission =
                    StreamsState(
                        items = listOf(s1, s2, s3),
                        answered = 2,
                        total = 2,
                        failures = emptyList(),
                        done = true,
                    ),
                combined = listOf(s1, s2, s3),
                matching = addons,
            )

        assertEquals(2, state.resolutionCounts[1080])
        assertEquals(1, state.resolutionCounts[720])
        assertEquals(2, state.availableAddons.first { it.transportUrl == "http://addon1" }.count)
        assertEquals(1, state.availableAddons.first { it.transportUrl == "http://addon2" }.count)

        val stateWith1080 =
            StreamsFilterReducer.updateFilters(
                state = state,
                matching = addons,
                filters = StreamFilters(resolution = 1080),
            )
        assertEquals(1, stateWith1080.availableAddons.first { it.transportUrl == "http://addon1" }.count)
        assertEquals(1, stateWith1080.availableAddons.first { it.transportUrl == "http://addon2" }.count)
        assertEquals(1, stateWith1080.languageCounts["en"])
        assertEquals(1, stateWith1080.languageCounts["fr"])
        assertEquals(1, stateWith1080.typeDirectCount)
        assertEquals(1, stateWith1080.typeTorrentCount)
    }

    @Test
    fun `labels disambiguated for addons with same name`() {
        val a1 = sampleAddon("http://addon1", "Torrentio")
        val a2 = sampleAddon("http://addon2", "Torrentio")
        val a3 = sampleAddon("http://addon3", "TPB")

        val disambiguated = StreamsFilterReducer.disambiguateAddons(listOf(a1, a2, a3))
        assertEquals("Torrentio", disambiguated[0].second)
        assertEquals("Torrentio (2)", disambiguated[1].second)
        assertEquals("TPB", disambiguated[2].second)
    }

    @Test
    fun `stale selections reset to default`() {
        val s1 =
            sampleStream("1", resolution = 1080, addonKey = "http://addon1").copy(
                languages = StreamLanguages(names = listOf("English")),
            )
        val addons = listOf(sampleAddon("http://addon1", "A1"))

        val staleState =
            StreamsUiState(
                selectedResolution = 2160,
                selectedAddon = "http://addonOld",
                selectedLanguage = "es",
                selectedType = TypeFilter.DIRECT,
                cachedOnly = true,
            )

        val updated =
            StreamsFilterReducer.buildEmissionState(
                state = staleState,
                emission =
                    StreamsState(
                        items = listOf(s1),
                        answered = 1,
                        total = 1,
                        failures = emptyList(),
                        done = true,
                    ),
                combined = listOf(s1),
                matching = addons,
            )

        assertNull("Resolution 2160 not present should reset to null", updated.selectedResolution)
        assertNull("Addon http://addonOld not active should reset to null", updated.selectedAddon)
        assertNull("Language es not present should reset to null", updated.selectedLanguage)
        assertEquals("No Direct streams should reset type to ALL", TypeFilter.ALL, updated.selectedType)
        assertFalse("No cached streams should reset cachedOnly to false", updated.cachedOnly)
    }

    @Test
    fun `visibility flags true when multiple options available`() {
        val s1 =
            sampleStream("1", resolution = 1080, kind = StreamKind.Torrent).copy(
                languages = StreamLanguages(names = listOf("English")),
                debrid = DebridMark("RD", cached = true),
            )
        val s2 =
            sampleStream("2", resolution = 720, kind = StreamKind.Direct).copy(
                languages = StreamLanguages(names = listOf("French")),
            )
        val addons = listOf(sampleAddon("http://addon1", "A1"), sampleAddon("http://addon2", "A2"))

        val multiState =
            StreamsFilterReducer.buildEmissionState(
                state = StreamsUiState(),
                emission =
                    StreamsState(
                        items = listOf(s1, s2),
                        answered = 2,
                        total = 2,
                        failures = emptyList(),
                        done = true,
                    ),
                combined = listOf(s1, s2),
                matching = addons,
            )

        assertTrue("Resolution >= 2 should show resolution filter", multiState.showResolutionFilter)
        assertTrue("Addons >= 2 should show addon filter", multiState.showAddonFilter)
        assertTrue("Languages >= 2 options should show language filter", multiState.showLanguageFilter)
        assertTrue("Both Direct and Torrent should show type filter", multiState.showTypeFilter)
        assertTrue("At least one cached should show cached filter", multiState.showCachedFilter)
    }

    @Test
    fun `visibility flags false when single option available`() {
        val singleStream = sampleStream("1", resolution = 1080, kind = StreamKind.Torrent)
        val singleAddon = listOf(sampleAddon("http://addon1", "A1"))

        val singleState =
            StreamsFilterReducer.buildEmissionState(
                state = StreamsUiState(),
                emission =
                    StreamsState(
                        items = listOf(singleStream),
                        answered = 1,
                        total = 1,
                        failures = emptyList(),
                        done = true,
                    ),
                combined = listOf(singleStream),
                matching = singleAddon,
            )

        assertFalse("Resolution < 2 should hide resolution filter", singleState.showResolutionFilter)
        assertFalse("Addons < 2 should hide addon filter", singleState.showAddonFilter)
        assertFalse("No languages should hide language filter", singleState.showLanguageFilter)
        assertFalse("Only Torrent should hide type filter", singleState.showTypeFilter)
        assertFalse("No cached should hide cached filter", singleState.showCachedFilter)
    }
}
