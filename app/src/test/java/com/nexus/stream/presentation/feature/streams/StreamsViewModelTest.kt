package com.nexus.stream.presentation.feature.streams

import com.nexus.stream.R
import com.nexus.stream.domain.model.AppError
import com.nexus.stream.domain.model.ParsedStream
import com.nexus.stream.domain.model.PlaybackCapabilities
import com.nexus.stream.domain.model.Stream
import com.nexus.stream.domain.model.StreamKind
import com.nexus.stream.domain.model.StreamSource
import com.nexus.stream.domain.repository.AddonFailure
import com.nexus.stream.domain.repository.PlayerPrefsRepository
import com.nexus.stream.domain.repository.StreamRepository
import com.nexus.stream.domain.repository.StreamsState
import com.nexus.stream.presentation.common.PlaybackRequest
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
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
class StreamsViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val streamRepository = mockk<StreamRepository>()
    private val playerPrefsRepository = mockk<PlayerPrefsRepository>()

    private val quickPlayFlow = MutableStateFlow(false)
    private val defaultCapabilities = PlaybackCapabilities(supportsTorrent = false)

    private val sampleRequest =
        PlaybackRequest(
            type = "movie",
            metaId = "tt123456",
            videoId = "tt123456",
            title = "Test Movie",
        )

    private fun createParsedStream(
        key: String,
        kind: StreamKind = StreamKind.Direct,
        resolution: Int? = 1080,
        url: String = "https://example.com/video.mp4",
        infoHash: String = "0123456789abcdef0123456789abcdef01234567",
    ): ParsedStream {
        val source =
            when (kind) {
                StreamKind.Direct -> StreamSource.Url(url, emptyMap())
                StreamKind.Torrent -> StreamSource.Torrent(infoHash, 0, listOf("udp://tracker.opentrackr.org:1337"))
                StreamKind.ExternalLink -> StreamSource.External(url)
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
            resolution = resolution,
            hdr = emptySet(),
            codec = null,
            sizeBytes = null,
            seeders = 10,
            kind = kind,
            needsHeaders = false,
            displayName = "Stream $key",
        )
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { playerPrefsRepository.quickPlay } returns quickPlayFlow
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(capabilities: PlaybackCapabilities = defaultCapabilities): StreamsViewModel =
        StreamsViewModel(
            streamRepository = streamRepository,
            playerPrefsRepository = playerPrefsRepository,
            playbackCapabilities = capabilities,
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

            // New request resets selectedResolution
            val newRequest = sampleRequest.copy(videoId = "tt999999")
            viewModel.onEvent(StreamsEvent.SetRequest(newRequest))
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.selectedResolution)
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

            assertEquals(2, effects.size)
            assertTrue(effects[0] is StreamsEffect.OpenVideo)
            assertEquals(StreamsEffect.DismissSheet, effects[1])

            // Repeated done emission -> does not play twice
            streamFlow.emit(StreamsState(listOf(s1), 2, 2, emptyList(), done = true))
            advanceUntilIdle()
            assertEquals(2, effects.size)

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
    fun torrentTap_emitsExplanationEffectWhenTorrentUnsupported() =
        runTest {
            val torrentStream = createParsedStream("s1", kind = StreamKind.Torrent)
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

            job.cancel()
        }

    @Test
    fun menuActions_emitExpectedEffects() =
        runTest {
            val directStream = createParsedStream("s1", kind = StreamKind.Direct, url = "https://direct.url/v.mp4")
            val torrentStream = createParsedStream("s2", kind = StreamKind.Torrent)
            val invalidTorrent = createParsedStream("s3", kind = StreamKind.Torrent, infoHash = "invalidHash")

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
}
