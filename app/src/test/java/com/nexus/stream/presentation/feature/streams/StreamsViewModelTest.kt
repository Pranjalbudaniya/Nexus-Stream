package com.nexus.stream.presentation.feature.streams

import com.nexus.stream.R
import com.nexus.stream.domain.model.Addon
import com.nexus.stream.domain.model.AddonManifest
import com.nexus.stream.domain.model.AddonSource
import com.nexus.stream.domain.model.AppError
import com.nexus.stream.domain.model.DebridMark
import com.nexus.stream.domain.model.ManifestHints
import com.nexus.stream.domain.model.ParsedStream
import com.nexus.stream.domain.model.PlaybackCapabilities
import com.nexus.stream.domain.model.PlaybackSource
import com.nexus.stream.domain.model.ResourceSpec
import com.nexus.stream.domain.model.Stream
import com.nexus.stream.domain.model.StreamKind
import com.nexus.stream.domain.model.StreamLanguages
import com.nexus.stream.domain.model.StreamMatch
import com.nexus.stream.domain.model.StreamSort
import com.nexus.stream.domain.model.StreamSource
import com.nexus.stream.domain.model.TypeFilter
import com.nexus.stream.domain.model.hashAddonKey
import com.nexus.stream.domain.repository.AddonFailure
import com.nexus.stream.domain.repository.AddonRepository
import com.nexus.stream.domain.repository.PlayerPrefsRepository
import com.nexus.stream.domain.repository.SourceMemoryRepository
import com.nexus.stream.domain.repository.StreamRepository
import com.nexus.stream.domain.repository.StreamsState
import com.nexus.stream.domain.usecase.cand
import com.nexus.stream.domain.usecase.fp
import com.nexus.stream.domain.usecase.progressKeyFor
import com.nexus.stream.presentation.common.PlaybackRequest
import com.nexus.stream.presentation.feature.player.PlaybackSessionHolder
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
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
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("LargeClass")
class StreamsViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val streamRepository = mockk<StreamRepository>()
    private val addonRepository = mockk<AddonRepository>()
    private val playerPrefsRepository = mockk<PlayerPrefsRepository>()
    private val sourceMemoryRepository = mockk<SourceMemoryRepository>(relaxed = true)
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
        transportUrl: String = "https://$id.com/manifest.json",
        types: List<String> = listOf("movie", "series"),
    ): Addon =
        Addon(
            transportUrl = transportUrl,
            manifest =
                AddonManifest(
                    id = id,
                    version = "1.0.0",
                    name = name,
                    description = "desc",
                    logo = null,
                    background = null,
                    types = types,
                    idPrefixes = null,
                    resources = listOf(ResourceSpec(name = "stream", types = types, idPrefixes = null)),
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
        fileName: String? = null,
        addonKeys: List<String> = listOf("https://addon1.com/manifest.json"),
    ): ParsedStream {
        val source =
            when (kind) {
                StreamKind.Direct -> {
                    StreamSource.Url("https://example.com/video.mp4", emptyMap())
                }

                StreamKind.Torrent -> {
                    StreamSource.Torrent(
                        "0123456789abcdef0123456789abcdef01234567",
                        0,
                        listOf("udp://tracker.opentrackr.org:1337"),
                    )
                }

                StreamKind.ExternalLink -> {
                    StreamSource.External("https://example.com/video.mp4")
                }
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
            addonKeys = addonKeys,
            resolution = resolution,
            hdr = emptySet(),
            codec = null,
            sizeBytes = null,
            seeders = 10,
            kind = kind,
            needsHeaders = false,
            displayName = "Stream $key",
            fileName = fileName,
        )
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every {
            streamRepository.streams(any(), any(), any(), any())
        } returns MutableSharedFlow()
        every { playerPrefsRepository.quickPlay } returns quickPlayFlow
        every { playerPrefsRepository.sort } returns sortFlow
        every { playerPrefsRepository.preferredLanguage } returns preferredLanguageFlow
        every { playerPrefsRepository.preferredQuality } returns preferredQualityFlow
        every { playerPrefsRepository.hideCam } returns hideCamFlow
        coEvery { playerPrefsRepository.setSort(any()) } coAnswers {
            sortFlow.value = firstArg()
        }
        every { addonRepository.observeActive() } returns activeAddonsFlow
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        StreamFilterMemory.reset()
        playbackSessionHolder.clear()
    }

    private fun createViewModel(capabilities: PlaybackCapabilities = defaultCapabilities): StreamsViewModel =
        StreamsViewModel(
            streamRepository = streamRepository,
            addonRepository = addonRepository,
            playerPrefsRepository = playerPrefsRepository,
            playbackCapabilities = capabilities,
            playbackSessionHolder = playbackSessionHolder,
            sourceMemoryRepository = sourceMemoryRepository,
            defaultDispatcher = testDispatcher,
        )

    @Test
    fun loadingState_updatesProgressively() =
        runTest {
            val streamFlow = MutableSharedFlow<StreamsState>()
            every { streamRepository.streams(sampleRequest.type, sampleRequest.videoId, false, null) } returns
                streamFlow

            val viewModel = createViewModel()
            viewModel.onEvent(StreamsEvent.SetRequest(sampleRequest))
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.isLoading)
            assertEquals(sampleRequest, viewModel.uiState.value.request)

            val s1 = createParsedStream("s1", resolution = 1080)
            streamFlow.emit(
                StreamsState(
                    items = listOf(s1),
                    answered = 1,
                    total = 2,
                    failures = emptyList(),
                    done = false,
                ),
            )
            advanceUntilIdle()

            assertEquals(1, viewModel.uiState.value.answered)
            assertEquals(2, viewModel.uiState.value.total)
            assertEquals(1, viewModel.uiState.value.allStreams.size)
            assertFalse(viewModel.uiState.value.isDone)

            streamFlow.emit(
                StreamsState(
                    items = listOf(s1),
                    answered = 2,
                    total = 2,
                    failures = emptyList(),
                    done = true,
                ),
            )
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.isDone)
            assertFalse(viewModel.uiState.value.isLoading)
        }

    @Test
    fun filterSelection_andResetOnNewRequest() =
        runTest {
            val s1 = createParsedStream("s1", resolution = 2160)
            val s2 = createParsedStream("s2", resolution = 1080)
            val streamFlow = MutableStateFlow(StreamsState(listOf(s1, s2), 1, 1, emptyList(), true))

            every { streamRepository.streams(any(), any(), any(), any()) } returns streamFlow

            val viewModel = createViewModel()
            viewModel.onEvent(StreamsEvent.SetRequest(sampleRequest))
            advanceUntilIdle()

            assertEquals(listOf(2160, 1080), viewModel.uiState.value.availableResolutions)
            assertEquals(2, viewModel.uiState.value.filteredStreams.size)

            viewModel.onEvent(StreamsEvent.SelectResolution(2160))
            advanceUntilIdle()

            assertEquals(2160, viewModel.uiState.value.selectedResolution)
            assertEquals(1, viewModel.uiState.value.filteredStreams.size)
            assertEquals(
                "s1",
                viewModel.uiState.value.filteredStreams
                    .first()
                    .key,
            )

            // New request preserves in-session selectedResolution
            val newRequest = sampleRequest.copy(videoId = "tt999999")
            viewModel.onEvent(StreamsEvent.SetRequest(newRequest))
            advanceUntilIdle()

            assertEquals(2160, viewModel.uiState.value.selectedResolution)
        }

    @Test
    fun addonFilter_hiddenWithOneAddon_shownWithTwoAddons() =
        runTest {
            activeAddonsFlow.value = listOf(createTestAddon("a1", "Torrentio"))
            val streamFlow = MutableStateFlow(StreamsState(emptyList(), 1, 1, emptyList(), true))
            every { streamRepository.streams(any(), any(), any(), any()) } returns streamFlow

            val viewModel = createViewModel()
            viewModel.onEvent(StreamsEvent.SetRequest(sampleRequest))
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.showAddonFilter)

            // Add a second active stream addon
            activeAddonsFlow.value =
                listOf(
                    createTestAddon("a1", "Torrentio"),
                    createTestAddon("a2", "MediaFusion"),
                )
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.showAddonFilter)
            assertEquals(2, viewModel.uiState.value.availableAddons.size)
        }

    @Test
    fun duplicateAddonNames_receiveSuffixes() =
        runTest {
            activeAddonsFlow.value =
                listOf(
                    createTestAddon("a1", "Torrentio", "https://a1.com/manifest.json"),
                    createTestAddon("a2", "Torrentio", "https://a2.com/manifest.json"),
                    createTestAddon("a3", "Torrentio", "https://a3.com/manifest.json"),
                )
            val streamFlow = MutableStateFlow(StreamsState(emptyList(), 3, 3, emptyList(), true))
            every { streamRepository.streams(any(), any(), any(), any()) } returns streamFlow

            val viewModel = createViewModel()
            viewModel.onEvent(StreamsEvent.SetRequest(sampleRequest))
            advanceUntilIdle()

            val names =
                viewModel.uiState.value.availableAddons
                    .map { it.displayName }
            assertEquals(listOf("Torrentio", "Torrentio (2)", "Torrentio (3)"), names)
        }

    @Test
    fun addonFilter_resetsWhenAddonDisappears() =
        runTest {
            val a1 = createTestAddon("a1", "Torrentio", "https://a1.com/manifest.json")
            val a2 = createTestAddon("a2", "MediaFusion", "https://a2.com/manifest.json")
            activeAddonsFlow.value = listOf(a1, a2)

            val s1 = createParsedStream("s1", addonKeys = listOf(a1.transportUrl))
            val s2 = createParsedStream("s2", addonKeys = listOf(a2.transportUrl))
            val streamFlow = MutableStateFlow(StreamsState(listOf(s1, s2), 2, 2, emptyList(), true))
            every { streamRepository.streams(any(), any(), any(), any()) } returns streamFlow

            val viewModel = createViewModel()
            viewModel.onEvent(StreamsEvent.SetRequest(sampleRequest))
            advanceUntilIdle()

            viewModel.onEvent(StreamsEvent.SelectAddon(a2.transportUrl))
            advanceUntilIdle()
            assertEquals(a2.transportUrl, viewModel.uiState.value.selectedAddon)
            assertEquals(1, viewModel.uiState.value.filteredStreams.size)

            // a2 is disabled and disappears from active addons
            activeAddonsFlow.value = listOf(a1)
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.selectedAddon)
            assertFalse(viewModel.uiState.value.showAddonFilter)
        }

    @Test
    fun clearFilters_resetsBothResolutionAndAddonFilters() =
        runTest {
            val a1 = createTestAddon("a1", "Torrentio", "https://a1.com/manifest.json")
            val a2 = createTestAddon("a2", "MediaFusion", "https://a2.com/manifest.json")
            activeAddonsFlow.value = listOf(a1, a2)

            val s1 = createParsedStream("s1", resolution = 1080, addonKeys = listOf(a1.transportUrl))
            val s2 = createParsedStream("s2", resolution = 2160, addonKeys = listOf(a2.transportUrl))
            val streamFlow = MutableStateFlow(StreamsState(listOf(s1, s2), 2, 2, emptyList(), true))
            every { streamRepository.streams(any(), any(), any(), any()) } returns streamFlow

            val viewModel = createViewModel()
            viewModel.onEvent(StreamsEvent.SetRequest(sampleRequest))
            advanceUntilIdle()

            viewModel.onEvent(StreamsEvent.SelectResolution(1080))
            viewModel.onEvent(StreamsEvent.SelectAddon(a1.transportUrl))
            advanceUntilIdle()

            assertEquals(1080, viewModel.uiState.value.selectedResolution)
            assertEquals(a1.transportUrl, viewModel.uiState.value.selectedAddon)

            viewModel.onEvent(StreamsEvent.ClearFilters)
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.selectedResolution)
            assertNull(viewModel.uiState.value.selectedAddon)
            assertEquals(2, viewModel.uiState.value.filteredStreams.size)
        }

    @Test
    fun showDetails_populatesDetailsStream_andDismissClearsIt() =
        runTest {
            val stream = createParsedStream("s1", fileName = "test.mkv")
            val viewModel = createViewModel()

            viewModel.onEvent(StreamsEvent.ShowDetails(stream))
            advanceUntilIdle()

            assertEquals(stream, viewModel.uiState.value.detailsStream)

            viewModel.onEvent(StreamsEvent.DismissDetails)
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.detailsStream)
        }

    @Test
    fun copyFileName_emitsCopyEffectAndSnackbar() =
        runTest {
            val viewModel = createViewModel()
            val effects = mutableListOf<StreamsEffect>()
            val job = launch { viewModel.effects.toList(effects) }

            viewModel.onEvent(StreamsEvent.CopyFileName("Movie.2024.1080p.mkv"))
            advanceUntilIdle()

            assertEquals(2, effects.size)
            assertEquals(StreamsEffect.CopyText("Movie.2024.1080p.mkv"), effects[0])
            assertEquals(StreamsEffect.ShowMessage(R.string.streams_filename_copied), effects[1])

            job.cancel()
        }

    @Test
    fun playbackRequest_paramsReachMatcher_andPenalizesMismatch() =
        runTest {
            val seriesRequest =
                PlaybackRequest(
                    type = "series",
                    metaId = "ttSeries",
                    videoId = "ttSeries:1:1",
                    title = "Test Series",
                    season = 1,
                    episode = 1,
                )
            // Stream 1 matches S01E01 (OK)
            val s1 =
                createParsedStream(
                    key = "s1",
                    resolution = 1080,
                    fileName = "Test.Series.S01E01.1080p.mkv",
                )
            // Stream 2 matches S01E02 (WRONG_EPISODE) with 4K resolution
            val s2 =
                createParsedStream(
                    key = "s2",
                    resolution = 2160,
                    fileName = "Test.Series.S01E02.2160p.mkv",
                )

            val streamFlow = MutableStateFlow(StreamsState(listOf(s1, s2), 1, 1, emptyList(), true))
            every { streamRepository.streams(seriesRequest.type, seriesRequest.videoId, false, null) } returns
                streamFlow

            val viewModel = createViewModel()
            viewModel.onEvent(StreamsEvent.SetRequest(seriesRequest))
            advanceUntilIdle()

            val streams = viewModel.uiState.value.allStreams
            assertEquals(2, streams.size)
            // S01E01 (s1) is OK and sorts ahead despite s2 having 2160p resolution
            assertEquals("s1", streams[0].key)
            assertEquals(StreamMatch.OK, streams[0].match)
            assertEquals("s2", streams[1].key)
            assertEquals(StreamMatch.WRONG_EPISODE, streams[1].match)
        }

    @Test
    fun quickPlay_playsOnceWhenDone_andNeverEarlyOrTwice() =
        runTest {
            quickPlayFlow.value = true
            val s1 = createParsedStream("s1", kind = StreamKind.Direct, resolution = 1080)
            val streamFlow = MutableSharedFlow<StreamsState>()
            every { streamRepository.streams(sampleRequest.type, sampleRequest.videoId, false, null) } returns
                streamFlow

            val viewModel = createViewModel()
            val effects = mutableListOf<StreamsEffect>()
            val job = launch { viewModel.effects.toList(effects) }

            viewModel.onEvent(StreamsEvent.SetRequest(sampleRequest))
            advanceUntilIdle()

            // Intermediate emission (not done yet) -> should NOT trigger Quick Play
            streamFlow.emit(StreamsState(listOf(s1), 1, 2, emptyList(), done = false))
            advanceUntilIdle()
            assertTrue(effects.isEmpty())

            // Done emission -> triggers Quick Play once and dismisses sheet
            streamFlow.emit(StreamsState(listOf(s1), 2, 2, emptyList(), done = true))
            advanceUntilIdle()

            assertEquals(1, effects.size)
            assertEquals(StreamsEffect.DismissSheet, effects[0])
            val selection = playbackSessionHolder.current.value
            assertNotNull(selection)
            assertTrue(selection?.source is PlaybackSource.Direct)

            // Repeated done emission -> does not play twice
            streamFlow.emit(StreamsState(listOf(s1), 2, 2, emptyList(), done = true))
            advanceUntilIdle()
            assertEquals(1, effects.size)

            job.cancel()
        }

    @Test
    fun quickPlay_fallsBackToListWhenNothingPlayable() =
        runTest {
            quickPlayFlow.value = true
            val torrentStream = createParsedStream("s1", kind = StreamKind.Torrent)
            val streamFlow = MutableStateFlow(StreamsState(listOf(torrentStream), 1, 1, emptyList(), done = true))
            every { streamRepository.streams(sampleRequest.type, sampleRequest.videoId, false, null) } returns
                streamFlow

            // Capabilities with supportsTorrent = false means Torrent is not playable
            val viewModel = createViewModel(PlaybackCapabilities(supportsTorrent = false))
            val effects = mutableListOf<StreamsEffect>()
            val job = launch { viewModel.effects.toList(effects) }

            viewModel.onEvent(StreamsEvent.SetRequest(sampleRequest))
            advanceUntilIdle()

            // No quick play triggered, no dismiss, list visible
            assertTrue(effects.isEmpty())
            assertTrue(viewModel.uiState.value.quickPlayHandled)
            assertEquals(1, viewModel.uiState.value.filteredStreams.size)

            job.cancel()
        }

    @Test
    fun SV_P1_directStreamWithoutHeaders_startsHolderDirect_andDismissesSheet() =
        runTest {
            val directStream = createParsedStream("s1", kind = StreamKind.Direct)
            val viewModel = createViewModel()
            val effects = mutableListOf<StreamsEffect>()
            val job = launch { viewModel.effects.toList(effects) }

            viewModel.onEvent(StreamsEvent.SetRequest(sampleRequest))
            viewModel.onEvent(StreamsEvent.PlayStream(directStream))
            advanceUntilIdle()

            assertEquals(listOf(StreamsEffect.DismissSheet), effects)
            val current = playbackSessionHolder.current.value
            assertNotNull(current)
            assertEquals("Test Movie", current?.title)
            val source = current?.source as? PlaybackSource.Direct
            assertNotNull(source)
            assertEquals("https://example.com/video.mp4", source?.url)

            job.cancel()
        }

    @Test
    fun SV_P2_directStreamWithHeaders_startsHolderDirectWithHeaders() =
        runTest {
            val directWithHeadersStream =
                createParsedStream("s2", kind = StreamKind.Direct).let {
                    it.copy(
                        stream =
                            it.stream.copy(
                                source =
                                    StreamSource.Url(
                                        "https://example.com/video.mp4",
                                        mapOf("Referer" to "https://example.org"),
                                    ),
                            ),
                    )
                }
            val viewModel = createViewModel()
            val effects = mutableListOf<StreamsEffect>()
            val job = launch { viewModel.effects.toList(effects) }

            viewModel.onEvent(StreamsEvent.SetRequest(sampleRequest))
            viewModel.onEvent(StreamsEvent.PlayStream(directWithHeadersStream))
            advanceUntilIdle()

            assertEquals(listOf(StreamsEffect.DismissSheet), effects)
            val current = playbackSessionHolder.current.value
            assertNotNull(current)
            val source = current?.source as? PlaybackSource.DirectWithHeaders
            assertNotNull(source)
            assertEquals("https://example.com/video.mp4", source?.url)
            assertEquals(mapOf("Referer" to "https://example.org"), source?.headers)

            job.cancel()
        }

    @Test
    fun SV_P3_torrentStreamWithSupportsTorrent_carriesEpisodeHint() =
        runTest {
            val seriesRequest =
                PlaybackRequest(
                    type = "series",
                    metaId = "tt999",
                    videoId = "tt999:2:5",
                    title = "Test Series",
                    season = 2,
                    episode = 5,
                )
            val torrentStream = createParsedStream("s3", kind = StreamKind.Torrent)
            val viewModel = createViewModel(PlaybackCapabilities(supportsTorrent = true))
            val effects = mutableListOf<StreamsEffect>()
            val job = launch { viewModel.effects.toList(effects) }

            viewModel.onEvent(StreamsEvent.SetRequest(seriesRequest))
            viewModel.onEvent(StreamsEvent.PlayStream(torrentStream))
            advanceUntilIdle()

            assertEquals(listOf(StreamsEffect.DismissSheet), effects)
            val current = playbackSessionHolder.current.value
            assertNotNull(current)
            val source = current?.source as? PlaybackSource.Torrent
            assertNotNull(source)
            assertEquals(2, source?.request?.episodeHint?.season)
            assertEquals(5, source?.request?.episodeHint?.episode)

            job.cancel()
        }

    @Test
    fun SV_P4_externalLinkStream_emitsOpenExternalUrl() =
        runTest {
            val extStream = createParsedStream("s4", kind = StreamKind.ExternalLink)
            val viewModel = createViewModel()
            val effects = mutableListOf<StreamsEffect>()
            val job = launch { viewModel.effects.toList(effects) }

            viewModel.onEvent(StreamsEvent.PlayStream(extStream))
            advanceUntilIdle()

            assertEquals(listOf(StreamsEffect.OpenExternalUrl("https://example.com/video.mp4")), effects)
            assertNull(playbackSessionHolder.current.value)

            job.cancel()
        }

    @Test
    fun SV_P5_unsupportedStream_emitsMessage_andNoHolderCall() =
        runTest {
            val torrentStream = createParsedStream("s5", kind = StreamKind.Torrent)
            val viewModel = createViewModel(PlaybackCapabilities(supportsTorrent = false))
            val effects = mutableListOf<StreamsEffect>()
            val job = launch { viewModel.effects.toList(effects) }

            viewModel.onEvent(StreamsEvent.PlayStream(torrentStream))
            advanceUntilIdle()

            assertEquals(1, effects.size)
            assertTrue(effects.first() is StreamsEffect.ShowMessage)
            assertEquals(
                R.string.streams_torrent_engine_notice,
                (effects.first() as StreamsEffect.ShowMessage).messageRes,
            )
            assertNull(playbackSessionHolder.current.value)

            job.cancel()
        }

    @Test
    fun SV_P6_quickPlay_picksStream_andReachesSameHolderCall() =
        runTest {
            quickPlayFlow.value = true
            val s1 = createParsedStream("s1", kind = StreamKind.Direct, resolution = 1080)
            val streamFlow = MutableSharedFlow<StreamsState>()
            every { streamRepository.streams(sampleRequest.type, sampleRequest.videoId, false, null) } returns
                streamFlow

            val viewModel = createViewModel()
            val effects = mutableListOf<StreamsEffect>()
            val job = launch { viewModel.effects.toList(effects) }

            viewModel.onEvent(StreamsEvent.SetRequest(sampleRequest))
            advanceUntilIdle()

            streamFlow.emit(StreamsState(listOf(s1), 2, 2, emptyList(), done = true))
            advanceUntilIdle()

            assertEquals(listOf(StreamsEffect.DismissSheet), effects)
            val current = playbackSessionHolder.current.value
            assertNotNull(current)
            assertEquals("Test Movie", current?.title)
            val source = current?.source as? PlaybackSource.Direct
            assertNotNull(source)
            assertEquals("https://example.com/video.mp4", source?.url)

            job.cancel()
        }

    @Test
    fun SV_P7_playbackSelection_carriesLogoAndBackgroundUrls_fromRequest() =
        runTest {
            val reqWithArt =
                sampleRequest.copy(
                    logoUrl = "https://images.example.com/logo.png",
                    backgroundUrl = "https://images.example.com/background.jpg",
                )
            val stream = createParsedStream("s1", kind = StreamKind.Direct)
            val streamFlow = MutableSharedFlow<StreamsState>()
            every { streamRepository.streams(reqWithArt.type, reqWithArt.videoId, false, null) } returns streamFlow

            val viewModel = createViewModel()
            viewModel.onEvent(StreamsEvent.SetRequest(reqWithArt))
            advanceUntilIdle()

            viewModel.onEvent(StreamsEvent.PlayStream(stream))
            advanceUntilIdle()

            val selection = playbackSessionHolder.current.value
            assertNotNull(selection)
            assertEquals("https://images.example.com/logo.png", selection?.logoUrl)
            assertEquals("https://images.example.com/background.jpg", selection?.backgroundUrl)
        }

    @Test
    fun menuActions_emitExpectedEffects() =
        runTest {
            val directStream =
                createParsedStream("s1", kind = StreamKind.Direct).let {
                    it.copy(stream = it.stream.copy(source = StreamSource.Url("https://direct.url/v.mp4", emptyMap())))
                }
            val torrentStream = createParsedStream("s2", kind = StreamKind.Torrent)
            val invalidTorrent =
                createParsedStream("s3", kind = StreamKind.Torrent).let {
                    it.copy(stream = it.stream.copy(source = StreamSource.Torrent("invalidHash", 0, emptyList())))
                }

            val viewModel = createViewModel()
            val effects = mutableListOf<StreamsEffect>()
            val job = launch { viewModel.effects.toList(effects) }

            viewModel.onEvent(StreamsEvent.CopyLink(directStream))
            advanceUntilIdle()
            assertEquals(1, effects.size)
            assertEquals(StreamsEffect.CopyText("https://direct.url/v.mp4"), effects.last())

            viewModel.onEvent(StreamsEvent.CopyMagnet(torrentStream))
            advanceUntilIdle()
            assertEquals(2, effects.size)
            assertTrue((effects.last() as StreamsEffect.CopyText).text.startsWith("magnet:?xt=urn:btih:"))

            viewModel.onEvent(StreamsEvent.OpenMagnetExternal(torrentStream))
            advanceUntilIdle()
            assertEquals(3, effects.size)
            assertTrue((effects.last() as StreamsEffect.OpenMagnet).magnet.startsWith("magnet:?xt=urn:btih:"))

            // Invalid hash emits no effect
            viewModel.onEvent(StreamsEvent.CopyMagnet(invalidTorrent))
            advanceUntilIdle()
            assertEquals(3, effects.size)

            job.cancel()
        }

    @Test
    fun retryFailed_mergesResultsWithExisting() =
        runTest {
            val s1 = createParsedStream("s1", resolution = 1080)
            val s2 = createParsedStream("s2", resolution = 2160)
            val initialFlow =
                MutableStateFlow(
                    StreamsState(
                        items = listOf(s1),
                        answered = 1,
                        total = 2,
                        failures = listOf(AddonFailure("Addon 2", AppError.Timeout, "https://addon2.stream/")),
                        done = true,
                    ),
                )
            val retryFlow =
                MutableStateFlow(
                    StreamsState(
                        items = listOf(s2),
                        answered = 1,
                        total = 1,
                        failures = emptyList(),
                        done = true,
                    ),
                )

            every { streamRepository.streams(sampleRequest.type, sampleRequest.videoId, false, null) } returns
                initialFlow
            every {
                streamRepository.streams(
                    sampleRequest.type,
                    sampleRequest.videoId,
                    true,
                    setOf("https://addon2.stream/"),
                )
            } returns retryFlow

            val viewModel = createViewModel()
            viewModel.onEvent(StreamsEvent.SetRequest(sampleRequest))
            advanceUntilIdle()

            assertEquals(1, viewModel.uiState.value.allStreams.size)
            assertEquals(1, viewModel.uiState.value.failures.size)

            viewModel.onEvent(StreamsEvent.RetryFailed)
            advanceUntilIdle()

            // Both streams are present and sorted
            assertEquals(2, viewModel.uiState.value.allStreams.size)
            assertEquals(
                2160,
                viewModel.uiState.value.allStreams
                    .first()
                    .resolution,
            )
            assertTrue(
                viewModel.uiState.value.failures
                    .isEmpty(),
            )
        }

    @Test
    fun dismiss_cancelsLoadingAndClearsState() =
        runTest {
            val streamFlow = MutableSharedFlow<StreamsState>()
            every { streamRepository.streams(sampleRequest.type, sampleRequest.videoId, false, null) } returns
                streamFlow

            val viewModel = createViewModel()
            val effects = mutableListOf<StreamsEffect>()
            val job = launch { viewModel.effects.toList(effects) }

            viewModel.onEvent(StreamsEvent.SetRequest(sampleRequest))
            advanceUntilIdle()
            assertNotNull(viewModel.uiState.value.request)

            viewModel.onEvent(StreamsEvent.Dismiss)
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.request)
            assertFalse(viewModel.uiState.value.isLoading)
            assertTrue(effects.contains(StreamsEffect.DismissSheet))

            job.cancel()
        }

    @Test
    fun setRequest_reopeningSameRequest_discardsPreviousStateAndStartsFreshFlow() =
        runTest {
            val s1 = createParsedStream("s1", resolution = 1080)
            val streamFlow1 = MutableStateFlow(StreamsState(listOf(s1), 1, 1, emptyList(), true))
            val streamFlow2 = MutableStateFlow(StreamsState(emptyList(), 0, 1, emptyList(), false))

            every { streamRepository.streams(sampleRequest.type, sampleRequest.videoId, false, null) } returnsMany
                listOf(streamFlow1, streamFlow2)

            val viewModel = createViewModel()
            viewModel.onEvent(StreamsEvent.SetRequest(sampleRequest))
            advanceUntilIdle()

            assertEquals(1, viewModel.uiState.value.allStreams.size)
            assertTrue(viewModel.uiState.value.isDone)

            // Reopen the exact same request
            viewModel.onEvent(StreamsEvent.SetRequest(sampleRequest))
            advanceUntilIdle()

            // Discards previous items, shows fresh loading flow
            assertEquals(0, viewModel.uiState.value.allStreams.size)
            assertFalse(viewModel.uiState.value.isDone)
            assertTrue(viewModel.uiState.value.isLoading)
        }

    @Test
    fun SV_P8_selectionCarriesSubtitleHintsFromChosenStream() =
        runTest {
            val baseStream = createParsedStream("hinted_stream", kind = StreamKind.Direct)
            val streamWithHints =
                baseStream.copy(
                    stream =
                        baseStream.stream.copy(
                            filename = "movie.2024.1080p.mkv",
                            videoHash = "abc123hash",
                            videoSize = 1048576000L,
                        ),
                )

            val viewModel = createViewModel()
            viewModel.onEvent(StreamsEvent.SetRequest(sampleRequest))
            viewModel.onEvent(StreamsEvent.PlayStream(streamWithHints))
            advanceUntilIdle()

            val selection = playbackSessionHolder.current.value
            assertNotNull(selection)
            assertNotNull(selection?.subtitleHints)
            assertEquals("movie.2024.1080p.mkv", selection?.subtitleHints?.filename)
            assertEquals("abc123hash", selection?.subtitleHints?.videoHash)
            assertEquals(1048576000L, selection?.subtitleHints?.videoSize)
        }

    @Test
    fun SX1_choosingAStream_putsItsFingerprintIntoTheSelection() =
        runTest {
            val stream =
                createParsedStream(
                    key = "t1",
                    kind = StreamKind.Torrent,
                    addonKeys = listOf("https://addon1.com/manifest.json"),
                )
            val viewModel = createViewModel(PlaybackCapabilities(supportsTorrent = true))
            viewModel.onEvent(StreamsEvent.SetRequest(sampleRequest))
            viewModel.onEvent(StreamsEvent.PlayStream(stream))
            advanceUntilIdle()

            val selection = playbackSessionHolder.current.value
            assertNotNull(selection)
            assertEquals(
                com.nexus.stream.domain.model
                    .fingerprintOf(stream),
                selection?.sourceFingerprint,
            )
        }

    @Test
    fun SX2_choosingAStreamWithoutAddonKeys_givesNullFingerprint() =
        runTest {
            val stream =
                createParsedStream(
                    key = "t2",
                    kind = StreamKind.Torrent,
                    addonKeys = emptyList(),
                )
            val viewModel = createViewModel(PlaybackCapabilities(supportsTorrent = true))
            viewModel.onEvent(StreamsEvent.SetRequest(sampleRequest))
            viewModel.onEvent(StreamsEvent.PlayStream(stream))
            advanceUntilIdle()

            val selection = playbackSessionHolder.current.value
            assertNotNull(selection)
            assertNull(selection?.sourceFingerprint)
        }

    @Test
    fun AR1_autoResume_matchFound_startsItOnce() =
        runTest {
            activeAddonsFlow.value = listOf(createTestAddon("addon-1", "Addon 1", transportUrl = "addon-1"))
            val f = fp(addons = listOf(hashAddonKey("addon-1")), binge = "g")
            coEvery { sourceMemoryRepository.get(any()) } returns f
            val request = sampleRequest.copy(autoResume = true)
            val streamFlow = MutableSharedFlow<StreamsState>()
            every { streamRepository.streams(any(), any(), any(), any()) } returns streamFlow

            val viewModel = createViewModel(PlaybackCapabilities(supportsTorrent = true))
            val effects = mutableListOf<StreamsEffect>()
            val job = launch { viewModel.effects.toList(effects) }

            viewModel.onEvent(StreamsEvent.SetRequest(request))
            runCurrent()

            val hit = cand(key = "hit", addon = "addon-1", binge = "g")
            streamFlow.emit(
                StreamsState(items = listOf(hit), answered = 1, total = 2, failures = emptyList(), done = false),
            )
            runCurrent()

            assertNotNull(playbackSessionHolder.current.value)
            assertEquals(1, effects.count { it is StreamsEffect.DismissSheet })
            assertEquals(ResumeUi.None, viewModel.uiState.value.resume)
            job.cancel()
        }

    @Test
    fun AR2_autoResume_noFingerprint_behavesAsBefore() =
        runTest {
            coEvery { sourceMemoryRepository.get(any()) } returns null
            quickPlayFlow.value = true
            val request = sampleRequest.copy(autoResume = true)
            val s1 = createParsedStream("s1", resolution = 1080)
            val streamFlow = MutableSharedFlow<StreamsState>()
            every { streamRepository.streams(any(), any(), any(), any()) } returns streamFlow

            val viewModel = createViewModel()
            val effects = mutableListOf<StreamsEffect>()
            val job = launch { viewModel.effects.toList(effects) }

            viewModel.onEvent(StreamsEvent.SetRequest(request))
            advanceUntilIdle()

            streamFlow.emit(
                StreamsState(items = listOf(s1), answered = 1, total = 1, failures = emptyList(), done = true),
            )
            advanceUntilIdle()

            assertNotNull(playbackSessionHolder.current.value)
            assertEquals(ResumeUi.None, viewModel.uiState.value.resume)
            job.cancel()
        }

    @Test
    fun AR3_autoResume_noMatch_afterAddonAnswered_notFound_startsNothing() =
        runTest {
            activeAddonsFlow.value = listOf(createTestAddon("addon-1", "Addon 1", transportUrl = "addon-1"))
            val f = fp(addons = listOf(hashAddonKey("addon-1")), binge = "g")
            coEvery { sourceMemoryRepository.get(any()) } returns f
            val request = sampleRequest.copy(autoResume = true)
            val streamFlow = MutableSharedFlow<StreamsState>()
            every { streamRepository.streams(any(), any(), any(), any()) } returns streamFlow

            val viewModel = createViewModel()
            viewModel.onEvent(StreamsEvent.SetRequest(request))
            runCurrent()

            val miss = cand(key = "miss", addon = "addon-1", binge = "other", res = 720)
            streamFlow.emit(
                StreamsState(items = listOf(miss), answered = 1, total = 1, failures = emptyList(), done = true),
            )
            runCurrent()

            assertNull(playbackSessionHolder.current.value)
            assertEquals(ResumeUi.NotFound, viewModel.uiState.value.resume)
        }

    @Test
    fun AR4_autoResume_noMatch_quickPlayOn_doesNotQuickPlay() =
        runTest {
            quickPlayFlow.value = true
            activeAddonsFlow.value = listOf(createTestAddon("addon-1", "Addon 1", transportUrl = "addon-1"))
            val f = fp(addons = listOf(hashAddonKey("addon-1")), binge = "g")
            coEvery { sourceMemoryRepository.get(any()) } returns f
            val request = sampleRequest.copy(autoResume = true)
            val streamFlow = MutableSharedFlow<StreamsState>()
            every { streamRepository.streams(any(), any(), any(), any()) } returns streamFlow

            val viewModel = createViewModel()
            viewModel.onEvent(StreamsEvent.SetRequest(request))
            runCurrent()

            val miss = cand(key = "miss", addon = "addon-1", binge = "other", res = 720)
            streamFlow.emit(
                StreamsState(items = listOf(miss), answered = 1, total = 1, failures = emptyList(), done = true),
            )
            runCurrent()

            assertNull(playbackSessionHolder.current.value)
            assertEquals(ResumeUi.NotFound, viewModel.uiState.value.resume)
        }

    @Test
    fun AR5_autoResume_fingerprintAddonNotEnabled_notFoundAtOnce() =
        runTest {
            activeAddonsFlow.value = listOf(createTestAddon("addon-1", "Addon 1", transportUrl = "addon-1"))
            val f = fp(addons = listOf(hashAddonKey("disabled-addon")), binge = "g")
            coEvery { sourceMemoryRepository.get(any()) } returns f
            val request = sampleRequest.copy(autoResume = true)
            val streamFlow = MutableSharedFlow<StreamsState>()
            every { streamRepository.streams(any(), any(), any(), any()) } returns streamFlow

            val viewModel = createViewModel()
            viewModel.onEvent(StreamsEvent.SetRequest(request))
            advanceUntilIdle()

            assertEquals(ResumeUi.NotFound, viewModel.uiState.value.resume)
            assertNull(playbackSessionHolder.current.value)
        }

    @Test
    fun AR6_userPicksBeforeAutoPick_noAutoStart() =
        runTest {
            activeAddonsFlow.value = listOf(createTestAddon("addon-1", "Addon 1", transportUrl = "addon-1"))
            val f = fp(addons = listOf(hashAddonKey("addon-1")), binge = "g")
            coEvery { sourceMemoryRepository.get(any()) } returns f
            val request = sampleRequest.copy(autoResume = true)
            val streamFlow = MutableSharedFlow<StreamsState>()
            every { streamRepository.streams(any(), any(), any(), any()) } returns streamFlow

            val viewModel = createViewModel()
            val effects = mutableListOf<StreamsEffect>()
            val job = launch { viewModel.effects.toList(effects) }

            viewModel.onEvent(StreamsEvent.SetRequest(request))
            runCurrent()

            val miss = cand(key = "miss", addon = "addon-1", binge = "other", res = 720)
            streamFlow.emit(
                StreamsState(items = listOf(miss), answered = 0, total = 1, failures = emptyList(), done = false),
            )
            runCurrent()
            assertEquals(ResumeUi.Looking, viewModel.uiState.value.resume)

            val other = cand(key = "other", addon = "addon-1", binge = "diff")
            viewModel.onEvent(StreamsEvent.PlayStream(other))
            runCurrent()
            assertEquals(1, effects.count { it is StreamsEffect.DismissSheet })
            assertEquals(ResumeUi.None, viewModel.uiState.value.resume)

            val hit = cand(key = "hit", addon = "addon-1", binge = "g")
            streamFlow.emit(
                StreamsState(items = listOf(miss, hit), answered = 1, total = 1, failures = emptyList(), done = true),
            )
            runCurrent()

            assertEquals(1, effects.count { it is StreamsEffect.DismissSheet })
            assertEquals(ResumeUi.None, viewModel.uiState.value.resume)
            job.cancel()
        }

    @Test
    fun AR7_autoResume_matchArrivesInALaterEmission_startsThen() =
        runTest {
            activeAddonsFlow.value = listOf(createTestAddon("addon-1", "Addon 1", transportUrl = "addon-1"))
            val f = fp(addons = listOf(hashAddonKey("addon-1")), binge = "g")
            coEvery { sourceMemoryRepository.get(any()) } returns f
            val request = sampleRequest.copy(autoResume = true)
            val streamFlow = MutableSharedFlow<StreamsState>()
            every { streamRepository.streams(any(), any(), any(), any()) } returns streamFlow

            val viewModel = createViewModel(PlaybackCapabilities(supportsTorrent = true))
            val effects = mutableListOf<StreamsEffect>()
            val job = launch { viewModel.effects.toList(effects) }

            viewModel.onEvent(StreamsEvent.SetRequest(request))
            runCurrent()

            val miss = cand(key = "miss", addon = "addon-1", binge = "other", res = 720)
            streamFlow.emit(
                StreamsState(items = listOf(miss), answered = 0, total = 2, failures = emptyList(), done = false),
            )
            runCurrent()
            assertNull(playbackSessionHolder.current.value)
            assertEquals(ResumeUi.Looking, viewModel.uiState.value.resume)

            val hit = cand(key = "hit", addon = "addon-1", binge = "g")
            streamFlow.emit(
                StreamsState(items = listOf(miss, hit), answered = 1, total = 2, failures = emptyList(), done = false),
            )
            runCurrent()

            assertNotNull(playbackSessionHolder.current.value)
            assertEquals(1, effects.count { it is StreamsEffect.DismissSheet })
            job.cancel()
        }

    @Test
    fun AR8_autoResume_waitCapReached_notFound() =
        runTest {
            activeAddonsFlow.value = listOf(createTestAddon("addon-1", "Addon 1", transportUrl = "addon-1"))
            val f = fp(addons = listOf(hashAddonKey("addon-1")), binge = "g")
            coEvery { sourceMemoryRepository.get(any()) } returns f
            val request = sampleRequest.copy(autoResume = true)
            val streamFlow = MutableSharedFlow<StreamsState>()
            every { streamRepository.streams(any(), any(), any(), any()) } returns streamFlow

            val viewModel = createViewModel()
            viewModel.onEvent(StreamsEvent.SetRequest(request))
            runCurrent()

            val miss = cand(key = "miss", addon = "addon-1", binge = "other", res = 720)
            streamFlow.emit(
                StreamsState(items = listOf(miss), answered = 0, total = 1, failures = emptyList(), done = false),
            )
            runCurrent()
            assertEquals(ResumeUi.Looking, viewModel.uiState.value.resume)

            advanceTimeBy(12_000L)
            runCurrent()

            assertEquals(ResumeUi.NotFound, viewModel.uiState.value.resume)
            assertNull(playbackSessionHolder.current.value)
        }

    @Test
    fun AR9_autoResume_isOneShot() =
        runTest {
            activeAddonsFlow.value = listOf(createTestAddon("addon-1", "Addon 1", transportUrl = "addon-1"))
            val f = fp(addons = listOf(hashAddonKey("addon-1")), binge = "g")
            coEvery { sourceMemoryRepository.get(any()) } returns f
            val request = sampleRequest.copy(autoResume = true)
            val streamFlow = MutableSharedFlow<StreamsState>()
            every { streamRepository.streams(any(), any(), any(), any()) } returns streamFlow

            val viewModel = createViewModel(PlaybackCapabilities(supportsTorrent = true))
            val effects = mutableListOf<StreamsEffect>()
            val job = launch { viewModel.effects.toList(effects) }

            viewModel.onEvent(StreamsEvent.SetRequest(request))
            runCurrent()

            val hit1 = cand(key = "hit1", addon = "addon-1", binge = "g")
            streamFlow.emit(
                StreamsState(items = listOf(hit1), answered = 1, total = 2, failures = emptyList(), done = false),
            )
            runCurrent()
            assertEquals(1, effects.count { it is StreamsEffect.DismissSheet })

            val hit2 = cand(key = "hit2", addon = "addon-1", binge = "g")
            streamFlow.emit(
                StreamsState(items = listOf(hit1, hit2), answered = 2, total = 2, failures = emptyList(), done = true),
            )
            runCurrent()
            assertEquals(1, effects.count { it is StreamsEffect.DismissSheet })
            job.cancel()
        }

    @Test
    fun AR10_plainPlay_withFingerprint_noAutoStart_butBadge() =
        runTest {
            activeAddonsFlow.value = listOf(createTestAddon("addon-1", "Addon 1", transportUrl = "addon-1"))
            val f = fp(addons = listOf(hashAddonKey("addon-1")), binge = "g")
            coEvery { sourceMemoryRepository.get(any()) } returns f
            val request = sampleRequest.copy(autoResume = false)
            val streamFlow = MutableSharedFlow<StreamsState>()
            every { streamRepository.streams(any(), any(), any(), any()) } returns streamFlow

            val viewModel = createViewModel(PlaybackCapabilities(supportsTorrent = true))
            viewModel.onEvent(StreamsEvent.SetRequest(request))
            advanceUntilIdle()

            val hit = cand(key = "hit", addon = "addon-1", binge = "g")
            streamFlow.emit(
                StreamsState(items = listOf(hit), answered = 1, total = 1, failures = emptyList(), done = true),
            )
            advanceUntilIdle()

            assertNull(playbackSessionHolder.current.value)
            assertEquals("hit", viewModel.uiState.value.lastUsedKey)
            assertEquals(ResumeUi.None, viewModel.uiState.value.resume)
        }

    @Test
    fun AR11_badge_isOnTheBestMatchOnly() =
        runTest {
            activeAddonsFlow.value = listOf(createTestAddon("addon-1", "Addon 1", transportUrl = "addon-1"))
            val f = fp(addons = listOf(hashAddonKey("addon-1")), binge = "g", res = 1080)
            coEvery { sourceMemoryRepository.get(any()) } returns f
            val request = sampleRequest.copy(autoResume = false)
            val streamFlow = MutableSharedFlow<StreamsState>()
            every { streamRepository.streams(any(), any(), any(), any()) } returns streamFlow

            val viewModel = createViewModel()
            viewModel.onEvent(StreamsEvent.SetRequest(request))
            advanceUntilIdle()

            val bestMatch = cand(key = "best", addon = "addon-1", binge = "g", res = 1080)
            val goodMatch = cand(key = "good", addon = "addon-1", binge = "g", res = 720)
            streamFlow.emit(
                StreamsState(
                    items = listOf(goodMatch, bestMatch),
                    answered = 1,
                    total = 1,
                    failures = emptyList(),
                    done = true,
                ),
            )
            advanceUntilIdle()

            assertEquals("best", viewModel.uiState.value.lastUsedKey)
        }

    @Test
    fun AR12_movieKey_usesMetaIdTwice() =
        runTest {
            coEvery { sourceMemoryRepository.get(any()) } returns null
            val movieRequest =
                PlaybackRequest(
                    type = "movie",
                    metaId = "kitsu:1",
                    videoId = "kitsu:1:1",
                    title = "Movie",
                    autoResume = true,
                )
            val streamFlow = MutableSharedFlow<StreamsState>()
            every { streamRepository.streams(any(), any(), any(), any()) } returns streamFlow

            val viewModel = createViewModel()
            viewModel.onEvent(StreamsEvent.SetRequest(movieRequest))
            advanceUntilIdle()

            coVerify { sourceMemoryRepository.get("kitsu:1|kitsu:1") }
        }

    @Test
    fun AR13_fingerprintReadFails_treatedAsNone_noCrash() =
        runTest {
            coEvery { sourceMemoryRepository.get(any()) } throws IOException("disk read error")
            quickPlayFlow.value = true
            val request = sampleRequest.copy(autoResume = true)
            val s1 = createParsedStream("s1", resolution = 1080)
            val streamFlow = MutableSharedFlow<StreamsState>()
            every { streamRepository.streams(any(), any(), any(), any()) } returns streamFlow

            val viewModel = createViewModel()
            val effects = mutableListOf<StreamsEffect>()
            val job = launch { viewModel.effects.toList(effects) }

            viewModel.onEvent(StreamsEvent.SetRequest(request))
            advanceUntilIdle()

            streamFlow.emit(
                StreamsState(items = listOf(s1), answered = 1, total = 1, failures = emptyList(), done = true),
            )
            advanceUntilIdle()

            assertEquals(ResumeUi.None, viewModel.uiState.value.resume)
            assertNotNull(playbackSessionHolder.current.value)
            job.cancel()
        }

    @Test
    fun AR14_autoResume_torrentUnsupported_sameOutcomeAsATap() =
        runTest {
            activeAddonsFlow.value = listOf(createTestAddon("addon-1", "Addon 1", transportUrl = "addon-1"))
            val f = fp(addons = listOf(hashAddonKey("addon-1")), binge = "g", kind = StreamKind.Torrent.toString())
            coEvery { sourceMemoryRepository.get(any()) } returns f
            val request = sampleRequest.copy(autoResume = true)
            val streamFlow = MutableSharedFlow<StreamsState>()
            every { streamRepository.streams(any(), any(), any(), any()) } returns streamFlow

            val viewModel = createViewModel(PlaybackCapabilities(supportsTorrent = false))
            val effects = mutableListOf<StreamsEffect>()
            val job = launch { viewModel.effects.toList(effects) }

            viewModel.onEvent(StreamsEvent.SetRequest(request))
            runCurrent()

            val hitTorrent =
                cand(
                    key = "hit",
                    addon = "addon-1",
                    kind = StreamKind.Torrent,
                    hash = "0123456789abcdef0123456789abcdef01234567",
                    binge = "g",
                )
            streamFlow.emit(
                StreamsState(items = listOf(hitTorrent), answered = 1, total = 1, failures = emptyList(), done = true),
            )
            runCurrent()

            assertNull(playbackSessionHolder.current.value)
            assertTrue(
                effects.any {
                    it is StreamsEffect.ShowMessage &&
                        it.messageRes == R.string.streams_torrent_engine_notice
                },
            )
            job.cancel()
        }

    @Test
    fun AR15_clearedViewModel_cancelsThePendingWait() =
        runTest {
            activeAddonsFlow.value = listOf(createTestAddon("addon-1", "Addon 1", transportUrl = "addon-1"))
            val f = fp(addons = listOf(hashAddonKey("addon-1")), binge = "g")
            coEvery { sourceMemoryRepository.get(any()) } returns f
            val request = sampleRequest.copy(autoResume = true)
            val streamFlow = MutableSharedFlow<StreamsState>()
            every { streamRepository.streams(any(), any(), any(), any()) } returns streamFlow

            val viewModel = createViewModel()
            viewModel.onEvent(StreamsEvent.SetRequest(request))
            runCurrent()

            val miss = cand(key = "miss", addon = "addon-1", binge = "other", res = 720)
            streamFlow.emit(
                StreamsState(items = listOf(miss), answered = 0, total = 1, failures = emptyList(), done = false),
            )
            runCurrent()
            assertEquals(ResumeUi.Looking, viewModel.uiState.value.resume)

            val onClearedMethod =
                androidx.lifecycle.ViewModel::class.java.getDeclaredMethod("onCleared").apply {
                    isAccessible = true
                }
            onClearedMethod.invoke(viewModel)

            advanceTimeBy(12_000L)
            runCurrent()

            assertNull(playbackSessionHolder.current.value)
        }
}
