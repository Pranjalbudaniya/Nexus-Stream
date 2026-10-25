package com.nexus.stream.presentation.feature.streams

import com.nexus.stream.domain.model.Addon
import com.nexus.stream.domain.model.AddonManifest
import com.nexus.stream.domain.model.AddonSource
import com.nexus.stream.domain.model.DebridMark
import com.nexus.stream.domain.model.ManifestHints
import com.nexus.stream.domain.model.ParsedStream
import com.nexus.stream.domain.model.PlaybackCapabilities
import com.nexus.stream.domain.model.PlaybackSource
import com.nexus.stream.domain.model.ResourceSpec
import com.nexus.stream.domain.model.Stream
import com.nexus.stream.domain.model.StreamKind
import com.nexus.stream.domain.model.StreamLanguages
import com.nexus.stream.domain.model.StreamSort
import com.nexus.stream.domain.model.StreamSource
import com.nexus.stream.domain.model.TypeFilter
import com.nexus.stream.domain.repository.AddonRepository
import com.nexus.stream.domain.repository.PlayerPrefsRepository
import com.nexus.stream.domain.repository.StreamRepository
import com.nexus.stream.domain.repository.StreamsState
import com.nexus.stream.presentation.common.PlaybackRequest
import com.nexus.stream.presentation.feature.player.PlaybackSessionHolder
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StreamsFilterViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val streamRepository = mockk<StreamRepository>()
    private val addonRepository = mockk<AddonRepository>()
    private val playerPrefsRepository = mockk<PlayerPrefsRepository>()
    private val playbackSessionHolder = PlaybackSessionHolder()

    private val quickPlayFlow = MutableStateFlow(false)
    private val sortFlow = MutableStateFlow(StreamSort.BEST)
    private val preferredLanguageFlow = MutableStateFlow<String?>(null)
    private val preferredQualityFlow = MutableStateFlow<Int?>(null)
    private val hideCamFlow = MutableStateFlow(false)
    private val activeAddonsFlow = MutableStateFlow<List<Addon>>(emptyList())
    private val defaultCapabilities = PlaybackCapabilities(supportsTorrent = false)

    private val sampleRequest =
        PlaybackRequest(
            type = "movie",
            metaId = "tt123456",
            videoId = "tt123456",
            title = "Test Movie",
        )

    private fun createTestAddon(
        id: String,
        name: String,
    ): Addon =
        Addon(
            transportUrl = "https://$id.com/manifest.json",
            manifest =
                AddonManifest(
                    id = id,
                    version = "1.0.0",
                    name = name,
                    description = "desc",
                    logo = null,
                    background = null,
                    types = listOf("movie", "series"),
                    idPrefixes = null,
                    resources =
                        listOf(
                            ResourceSpec(name = "stream", types = listOf("movie", "series"), idPrefixes = null),
                        ),
                    catalogs = emptyList(),
                    behaviorHints =
                        ManifestHints(
                            adult = false,
                            p2p = false,
                            configurable = false,
                            configurationRequired = false,
                        ),
                ),
            enabled = true,
            position = 0,
            source = AddonSource.LOCAL,
            installedAt = 0L,
        )

    private fun createParsedStream(
        key: String,
        kind: StreamKind = StreamKind.Direct,
        resolution: Int? = 1080,
        videoUrl: String = "https://example.com/video.mp4",
    ): ParsedStream {
        val source =
            when (kind) {
                StreamKind.Direct -> StreamSource.Url(videoUrl, emptyMap())
                StreamKind.Torrent -> StreamSource.Torrent("0123456789abcdef0123456789abcdef01234567", 0, emptyList())
                StreamKind.ExternalLink -> StreamSource.External(videoUrl)
            }
        return ParsedStream(
            key = key,
            stream =
                Stream(
                    source = source,
                    name = "Stream $key",
                    description = null,
                    subtitles = emptyList(),
                    bingeGroup = null,
                    notWebReady = false,
                    videoSize = null,
                    filename = null,
                    videoHash = null,
                ),
            addonNames = listOf("Addon 1"),
            addonKeys = listOf("https://addon1.com/manifest.json"),
            resolution = resolution,
            hdr = emptySet(),
            codec = null,
            sizeBytes = null,
            seeders = 10,
            kind = kind,
            needsHeaders = false,
            displayName = "Stream $key",
            fileName = null,
        )
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { playerPrefsRepository.quickPlay } returns quickPlayFlow
        every { playerPrefsRepository.sort } returns sortFlow
        every { playerPrefsRepository.preferredLanguage } returns preferredLanguageFlow
        every { playerPrefsRepository.preferredQuality } returns preferredQualityFlow
        every { playerPrefsRepository.hideCam } returns hideCamFlow
        coEvery { playerPrefsRepository.setSort(any()) } coAnswers { sortFlow.value = firstArg() }
        every { addonRepository.observeActive() } returns activeAddonsFlow
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        StreamFilterMemory.reset()
    }

    private fun createViewModel(): StreamsViewModel =
        StreamsViewModel(
            streamRepository = streamRepository,
            addonRepository = addonRepository,
            playerPrefsRepository = playerPrefsRepository,
            playbackCapabilities = defaultCapabilities,
            playbackSessionHolder = playbackSessionHolder,
            sourceMemoryRepository = mockk(relaxed = true),
            defaultDispatcher = testDispatcher,
        )

    @Test
    fun filterChipsVisibility_dependsOnOptionCounts() =
        runTest {
            val a1 = createTestAddon("a1", "Addon 1")
            val a2 = createTestAddon("a2", "Addon 2")
            activeAddonsFlow.value = listOf(a1, a2)

            val s1 =
                createParsedStream("s1", kind = StreamKind.Direct, resolution = 1080).copy(
                    languages = StreamLanguages(names = listOf("English")),
                    debrid = DebridMark("RD", cached = true),
                )
            val s2 =
                createParsedStream("s2", kind = StreamKind.Torrent, resolution = 720).copy(
                    languages = StreamLanguages(names = listOf("French")),
                )

            val streamFlow = MutableStateFlow(StreamsState(listOf(s1, s2), 1, 1, emptyList(), true))
            every { streamRepository.streams(sampleRequest.type, sampleRequest.videoId, false, null) } returns
                streamFlow

            val viewModel = createViewModel()
            viewModel.onEvent(StreamsEvent.SetRequest(sampleRequest))
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertTrue(state.showResolutionFilter)
            assertTrue(state.showAddonFilter)
            assertTrue(state.showLanguageFilter)
            assertTrue(state.showTypeFilter)
            assertTrue(state.showCachedFilter)
        }

    @Test
    fun inSessionFilters_surviveNewRequest_whileAddonFilterResets() =
        runTest {
            val s1 =
                createParsedStream("s1", kind = StreamKind.Direct, resolution = 1080).copy(
                    languages = StreamLanguages(names = listOf("French")),
                )
            val streamFlow1 = MutableStateFlow(StreamsState(listOf(s1), 1, 1, emptyList(), true))
            val streamFlow2 = MutableStateFlow(StreamsState(emptyList(), 0, 1, emptyList(), false))

            val req1 = sampleRequest
            val req2 = sampleRequest.copy(videoId = "tt999999")
            every { streamRepository.streams(req1.type, req1.videoId, false, null) } returns streamFlow1
            every { streamRepository.streams(req2.type, req2.videoId, false, null) } returns streamFlow2

            val viewModel = createViewModel()
            viewModel.onEvent(StreamsEvent.SetRequest(req1))
            advanceUntilIdle()

            viewModel.onEvent(StreamsEvent.SelectResolution(1080))
            viewModel.onEvent(StreamsEvent.SelectLanguage("fr"))
            viewModel.onEvent(StreamsEvent.SelectType(TypeFilter.DIRECT))
            viewModel.onEvent(StreamsEvent.ToggleCached)
            viewModel.onEvent(StreamsEvent.SelectAddon("https://addon1.com/manifest.json"))
            advanceUntilIdle()

            viewModel.onEvent(StreamsEvent.SetRequest(req2))
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(1080, state.selectedResolution)
            assertEquals("fr", state.selectedLanguage)
            assertEquals(TypeFilter.DIRECT, state.selectedType)
            assertTrue(state.cachedOnly)
            assertNull(state.selectedAddon)
        }

    @Test
    fun sort_writesToPrefs() =
        runTest {
            val viewModel = createViewModel()
            viewModel.onEvent(StreamsEvent.SelectSort(StreamSort.QUALITY))
            advanceUntilIdle()

            coVerify { playerPrefsRepository.setSort(StreamSort.QUALITY) }
            assertEquals(StreamSort.QUALITY, viewModel.uiState.value.sort)
        }

    @Test
    fun clearFilters_resetsFiltersButKeepsSort() =
        runTest {
            val viewModel = createViewModel()
            viewModel.onEvent(StreamsEvent.SelectSort(StreamSort.SEEDERS))
            viewModel.onEvent(StreamsEvent.SelectResolution(1080))
            viewModel.onEvent(StreamsEvent.SelectType(TypeFilter.TORRENT))
            viewModel.onEvent(StreamsEvent.ToggleCached)
            advanceUntilIdle()

            viewModel.onEvent(StreamsEvent.ClearFilters)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertNull(state.selectedResolution)
            assertNull(state.selectedLanguage)
            assertEquals(TypeFilter.ALL, state.selectedType)
            assertFalse(state.cachedOnly)
            assertNull(state.selectedAddon)
            assertEquals(StreamSort.SEEDERS, state.sort)

            assertNull(StreamFilterMemory.resolution)
            assertNull(StreamFilterMemory.language)
            assertEquals(TypeFilter.ALL, StreamFilterMemory.type)
            assertFalse(StreamFilterMemory.cachedOnly)
        }

    @Test
    fun quickPlay_ignoresOnScreenFiltersAndSort() =
        runTest {
            quickPlayFlow.value = true
            val s1 =
                createParsedStream(
                    "s1",
                    kind = StreamKind.Direct,
                    resolution = 720,
                    videoUrl = "https://example.com/s1.mp4",
                )
            val s2 =
                createParsedStream(
                    "s2",
                    kind = StreamKind.Direct,
                    resolution = 1080,
                    videoUrl = "https://example.com/s2.mp4",
                )

            val streamFlow = MutableStateFlow(StreamsState(listOf(s1, s2), 1, 1, emptyList(), true))
            every { streamRepository.streams(sampleRequest.type, sampleRequest.videoId, false, null) } returns
                streamFlow

            val viewModel = createViewModel()
            val effects = mutableListOf<StreamsEffect>()
            val job = launch { viewModel.effects.toList(effects) }

            viewModel.onEvent(StreamsEvent.SelectResolution(720))
            viewModel.onEvent(StreamsEvent.SetRequest(sampleRequest))
            advanceUntilIdle()

            val dismiss = effects.filterIsInstance<StreamsEffect.DismissSheet>().firstOrNull()
            assertNotNull(dismiss)
            val current = playbackSessionHolder.current.value
            assertNotNull(current)
            val source = current?.source as? PlaybackSource.Direct
            assertEquals("https://example.com/s2.mp4", source?.url)

            job.cancel()
        }

    @Test
    fun statusLineNumbers_reflectFilteredAndTotalCounts() =
        runTest {
            val s1 = createParsedStream("s1", kind = StreamKind.Direct, resolution = 1080)
            val s2 = createParsedStream("s2", kind = StreamKind.Direct, resolution = 1080)
            val s3 = createParsedStream("s3", kind = StreamKind.Direct, resolution = 720)

            val streamFlow = MutableStateFlow(StreamsState(listOf(s1, s2, s3), 1, 1, emptyList(), true))
            every { streamRepository.streams(sampleRequest.type, sampleRequest.videoId, false, null) } returns
                streamFlow

            val viewModel = createViewModel()
            viewModel.onEvent(StreamsEvent.SetRequest(sampleRequest))
            advanceUntilIdle()

            assertEquals(3, viewModel.uiState.value.allStreams.size)
            assertEquals(3, viewModel.uiState.value.filteredStreams.size)

            viewModel.onEvent(StreamsEvent.SelectResolution(720))
            advanceUntilIdle()

            assertEquals(3, viewModel.uiState.value.allStreams.size)
            assertEquals(1, viewModel.uiState.value.filteredStreams.size)

            viewModel.onEvent(StreamsEvent.ClearFilters)
            advanceUntilIdle()

            assertEquals(3, viewModel.uiState.value.allStreams.size)
            assertEquals(3, viewModel.uiState.value.filteredStreams.size)
        }
}
