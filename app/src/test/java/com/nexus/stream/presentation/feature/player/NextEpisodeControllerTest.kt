package com.nexus.stream.presentation.feature.player

import com.nexus.stream.domain.model.Codec
import com.nexus.stream.domain.model.ParsedStream
import com.nexus.stream.domain.model.PlaybackSource
import com.nexus.stream.domain.model.SourceFingerprint
import com.nexus.stream.domain.model.Stream
import com.nexus.stream.domain.model.StreamKind
import com.nexus.stream.domain.model.StreamSource
import com.nexus.stream.domain.repository.SourceMemoryRepository
import com.nexus.stream.domain.usecase.NextEpisode
import com.nexus.stream.domain.usecase.NextEpisodeFinder
import com.nexus.stream.domain.usecase.NextUi
import com.nexus.stream.domain.usecase.SameSourceFinder
import com.nexus.stream.domain.usecase.SameSourceReason
import com.nexus.stream.domain.usecase.SameSourceResult
import com.nexus.stream.domain.usecase.StartPlayback
import com.nexus.stream.domain.usecase.StartResult
import com.nexus.stream.domain.usecase.progressKeyFor
import com.nexus.stream.presentation.common.PlaybackRequest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NextEpisodeControllerTest {
    private val finder = mockk<NextEpisodeFinder>(relaxed = true)
    private val sameSource = mockk<SameSourceFinder>(relaxed = true)
    private val sourceMemory = mockk<SourceMemoryRepository>(relaxed = true)
    private val startPlayback = mockk<StartPlayback>(relaxed = true)
    private val autoPlayNextFlow = MutableStateFlow(true)

    private val sampleFingerprint =
        SourceFingerprint(
            addonKeyHashes = listOf("addon1"),
            kind = "Direct",
            bingeGroup = "group1",
        )

    private val sampleRequest =
        PlaybackRequest(
            type = "series",
            metaId = "tt123",
            videoId = "tt123:1:4",
            title = "Sample Series - Episode 4",
            season = 1,
            episode = 4,
            metaName = "Sample Series",
        )

    private val sampleSelection =
        PlaybackSelection(
            title = "Sample Series - Episode 4",
            source = PlaybackSource.Direct("https://example.com/stream.mp4"),
            type = "series",
            metaId = "tt123",
            videoId = "tt123:1:4",
            season = 1,
            episode = 4,
            metaName = "Sample Series",
            sourceFingerprint = sampleFingerprint,
            request = sampleRequest,
        )

    private val nextEpisode =
        NextEpisode(
            videoId = "tt123:1:5",
            season = 1,
            episode = 5,
            episodeTitle = "Episode 5",
        )

    private val sampleFoundStream =
        ParsedStream(
            key = "stream_next",
            stream =
                Stream(
                    source = StreamSource.Url("https://example.com/next.mp4", emptyMap()),
                    name = "Next Stream",
                    description = null,
                    subtitles = emptyList(),
                    bingeGroup = "group1",
                    notWebReady = false,
                    videoSize = 1000L,
                    filename = null,
                    videoHash = null,
                ),
            addonNames = listOf("Test Addon"),
            addonKeys = listOf("addon1"),
            resolution = 1080,
            hdr = emptySet(),
            codec = Codec.AVC,
            sizeBytes = 1000L,
            seeders = 10,
            kind = StreamKind.Direct,
            needsHeaders = false,
            displayName = "Next Stream 1080p",
        )

    @Before
    fun setUp() {
        coEvery { finder.find("series", "tt123", "tt123:1:4") } returns nextEpisode
        coEvery { sourceMemory.get(any()) } returns sampleFingerprint
        coEvery { sameSource.find(any(), any()) } returns SameSourceResult.Found(sampleFoundStream)
        every { startPlayback(any(), any()) } returns StartResult.Started(sampleSelection)
    }

    private fun createController(scope: kotlinx.coroutines.CoroutineScope) =
        NextEpisodeController(
            finder = finder,
            sameSource = sameSource,
            sourceMemory = sourceMemory,
            startPlayback = startPlayback,
            autoPlayNext = autoPlayNextFlow,
            scope = scope,
        )

    @Test
    fun NV1_series_resolvesTheNextEpisodeOnce_movieNever() =
        runTest {
            val controller = createController(backgroundScope)
            controller.onPlaybackStarted(sampleSelection)
            controller.onPlaybackStarted(sampleSelection)
            runCurrent()

            coVerify(exactly = 1) { finder.find("series", "tt123", "tt123:1:4") }

            val movieSelection = sampleSelection.copy(type = "movie", videoId = "m1", metaId = "m1")
            val movieController = createController(backgroundScope)
            movieController.onPlaybackStarted(movieSelection)
            runCurrent()

            coVerify(exactly = 0) { finder.find("movie", any(), any()) }
        }

    @Test
    fun NV2_selectionWithoutRequest_featureOff() =
        runTest {
            val controller = createController(backgroundScope)
            controller.onPlaybackStarted(sampleSelection.copy(request = null))
            runCurrent()

            controller.onSnapshot(600_000L, 590_000L, false)
            assertEquals(NextUi.None, controller.state.value.nextUi)
            coVerify(exactly = 0) { finder.find(any(), any(), any()) }
        }

    @Test
    fun NV3_noNextEpisode_featureOff() =
        runTest {
            coEvery { finder.find("series", "tt123", "tt123:1:4") } returns null
            val controller = createController(backgroundScope)
            controller.onPlaybackStarted(sampleSelection)
            runCurrent()

            controller.onSnapshot(600_000L, 590_000L, false)
            assertEquals(NextUi.None, controller.state.value.nextUi)
            coVerify(exactly = 0) { sameSource.find(any(), any()) }
        }

    @Test
    fun NV4_prefetchAtNinetySeconds_usesTheNextRequestAndTheCurrentFingerprint() =
        runTest {
            val controller = createController(backgroundScope)
            controller.onPlaybackStarted(sampleSelection)
            runCurrent()

            // At 90s remaining (600_000 - 90_000 = 510_000)
            controller.onSnapshot(600_000L, 510_000L, false)
            runCurrent()

            val expectedHintKey = progressKeyFor("series", "tt123", "tt123:1:4")
            coVerify(exactly = 1) {
                sameSource.find(
                    match {
                        it.videoId == "tt123:1:5" &&
                            it.season == 1 &&
                            it.episode == 5 &&
                            !it.autoResume &&
                            it.sourceHintKey == expectedHintKey
                    },
                    sampleFingerprint,
                )
            }
        }

    @Test
    fun NV5_noFingerprintAnywhere_notFound_withoutCallingTheFinder() =
        runTest {
            coEvery { sourceMemory.get(any()) } returns null
            val controller = createController(backgroundScope)
            controller.onPlaybackStarted(sampleSelection.copy(sourceFingerprint = null))
            runCurrent()

            // Snapshot into prefetch window
            controller.onSnapshot(600_000L, 510_000L, false)
            runCurrent()

            // Snapshot into offer window (last 30s)
            controller.onSnapshot(600_000L, 580_000L, false)
            runCurrent()

            assertEquals(NextUi.ChooseSource, controller.state.value.nextUi)
            coVerify(exactly = 0) { sameSource.find(any(), any()) }
        }

    @Test
    fun NV6_found_tapNext_startsPlaybackAndSwitchesOnce() =
        runTest {
            val controller = createController(backgroundScope)
            controller.onPlaybackStarted(sampleSelection)
            runCurrent()

            controller.onSnapshot(600_000L, 510_000L, false)
            runCurrent()

            val effects = mutableListOf<NextEpisodeEffect>()
            val job = backgroundScope.launch { controller.effects.collect { effects.add(it) } }

            controller.onNextTapped()
            controller.onNextTapped()
            runCurrent()

            assertEquals(listOf(NextEpisodeEffect.SwitchToNext), effects)
            io.mockk.verify(exactly = 1) { startPlayback(match { it.videoId == "tt123:1:5" }, sampleFoundStream) }
            job.cancel()
        }

    @Test
    fun NV7_searching_tap_switchesWhenFound() =
        runTest {
            val finderFlow = MutableSharedFlow<SameSourceResult>()
            coEvery { sameSource.find(any(), any()) } coAnswers {
                finderFlow.first()
            }

            val controller = createController(backgroundScope)
            controller.onPlaybackStarted(sampleSelection)
            runCurrent()

            controller.onSnapshot(600_000L, 510_000L, false)
            runCurrent()

            val effects = mutableListOf<NextEpisodeEffect>()
            val job = backgroundScope.launch { controller.effects.collect { effects.add(it) } }

            // Tap while SEARCHING
            controller.onNextTapped()
            runCurrent()
            assertTrue(effects.isEmpty())

            // Later finder succeeds
            finderFlow.emit(SameSourceResult.Found(sampleFoundStream))
            runCurrent()

            assertEquals(listOf(NextEpisodeEffect.SwitchToNext), effects)
            job.cancel()
        }

    @Test
    fun NV8_searching_tap_thenNotFound_chooseSource() =
        runTest {
            val answer: SameSourceResult = SameSourceResult.NotFound(SameSourceReason.TIMEOUT)
            coEvery { sameSource.find(any(), any()) } coAnswers {
                kotlinx.coroutines.delay(100L)
                answer
            }

            val controller = createController(backgroundScope)
            controller.onPlaybackStarted(sampleSelection)
            runCurrent()

            controller.onSnapshot(600_000L, 510_000L, false)
            runCurrent()

            val effects = mutableListOf<NextEpisodeEffect>()
            val job = backgroundScope.launch { controller.effects.collect { effects.add(it) } }

            controller.onNextTapped()
            runCurrent()
            assertTrue(effects.isEmpty())

            advanceTimeBy(150L)
            runCurrent()

            assertEquals(1, effects.size)
            assertTrue(effects.first() is NextEpisodeEffect.ChooseNextSource)
            assertEquals("tt123:1:5", (effects.first() as NextEpisodeEffect.ChooseNextSource).request.videoId)
            job.cancel()
        }

    @Test
    fun NV9_chooseSource_tap_emitsChooseNextSource() =
        runTest {
            coEvery { sameSource.find(any(), any()) } returns SameSourceResult.NotFound(SameSourceReason.NO_MATCH)
            val controller = createController(backgroundScope)
            controller.onPlaybackStarted(sampleSelection)
            runCurrent()

            controller.onSnapshot(600_000L, 510_000L, false)
            runCurrent()
            controller.onSnapshot(600_000L, 580_000L, false)
            runCurrent()

            assertEquals(NextUi.ChooseSource, controller.state.value.nextUi)

            val effects = mutableListOf<NextEpisodeEffect>()
            val job = backgroundScope.launch { controller.effects.collect { effects.add(it) } }

            controller.onNextTapped()
            runCurrent()

            assertEquals(1, effects.size)
            assertTrue(effects.first() is NextEpisodeEffect.ChooseNextSource)
            job.cancel()
        }

    @Test
    fun NV10_startPlaybackNotStarted_fallsBackToChooseSource() =
        runTest {
            every { startPlayback(any(), any()) } returns StartResult.Unsupported()
            val controller = createController(backgroundScope)
            controller.onPlaybackStarted(sampleSelection)
            runCurrent()

            controller.onSnapshot(600_000L, 510_000L, false)
            runCurrent()

            val effects = mutableListOf<NextEpisodeEffect>()
            val job = backgroundScope.launch { controller.effects.collect { effects.add(it) } }

            controller.onNextTapped()
            runCurrent()

            assertEquals(1, effects.size)
            assertTrue(effects.first() is NextEpisodeEffect.ChooseNextSource)
            job.cancel()
        }

    @Test
    fun NV11_autoPlayOn_endedAndReady_switchesAfterFiveSeconds() =
        runTest {
            val controller = createController(backgroundScope)
            controller.onPlaybackStarted(sampleSelection)
            runCurrent()

            controller.onSnapshot(600_000L, 510_000L, false)
            runCurrent()

            val effects = mutableListOf<NextEpisodeEffect>()
            val job = backgroundScope.launch { controller.effects.collect { effects.add(it) } }

            controller.onSnapshot(600_000L, 600_000L, ended = true)
            runCurrent()

            assertEquals(5, controller.state.value.autoPlayCountdownSeconds)
            advanceTimeBy(1000L)
            runCurrent()
            assertEquals(4, controller.state.value.autoPlayCountdownSeconds)
            advanceTimeBy(1000L)
            runCurrent()
            assertEquals(3, controller.state.value.autoPlayCountdownSeconds)
            advanceTimeBy(1000L)
            runCurrent()
            assertEquals(2, controller.state.value.autoPlayCountdownSeconds)
            advanceTimeBy(1000L)
            runCurrent()
            assertEquals(1, controller.state.value.autoPlayCountdownSeconds)
            advanceTimeBy(1000L)
            runCurrent()

            assertEquals(listOf(NextEpisodeEffect.SwitchToNext), effects)
            job.cancel()
        }

    @Test
    fun NV12_autoPlayOn_dismissBeforeZero_noSwitch() =
        runTest {
            val controller = createController(backgroundScope)
            controller.onPlaybackStarted(sampleSelection)
            runCurrent()

            controller.onSnapshot(600_000L, 510_000L, false)
            runCurrent()

            val effects = mutableListOf<NextEpisodeEffect>()
            val job = backgroundScope.launch { controller.effects.collect { effects.add(it) } }

            controller.onSnapshot(600_000L, 600_000L, ended = true)
            runCurrent()

            advanceTimeBy(2000L)
            controller.onDismiss()
            runCurrent()

            advanceTimeBy(5000L)
            runCurrent()

            assertTrue(effects.isEmpty())
            assertEquals(NextUi.None, controller.state.value.nextUi)
            job.cancel()
        }

    @Test
    fun NV13_autoPlayOff_ended_noCountdown_noSwitch() =
        runTest {
            autoPlayNextFlow.value = false
            val controller = createController(backgroundScope)
            controller.onPlaybackStarted(sampleSelection)
            runCurrent()

            controller.onSnapshot(600_000L, 510_000L, false)
            runCurrent()

            val effects = mutableListOf<NextEpisodeEffect>()
            val job = backgroundScope.launch { controller.effects.collect { effects.add(it) } }

            controller.onSnapshot(600_000L, 600_000L, ended = true)
            runCurrent()

            assertNull(controller.state.value.autoPlayCountdownSeconds)
            advanceTimeBy(6000L)
            runCurrent()

            assertTrue(effects.isEmpty())
            assertEquals(NextUi.Ready, controller.state.value.nextUi)
            job.cancel()
        }

    @Test
    fun NV14_autoPlayOn_endedButNotFound_noAutoSwitch() =
        runTest {
            coEvery { sameSource.find(any(), any()) } returns SameSourceResult.NotFound(SameSourceReason.NO_MATCH)
            val controller = createController(backgroundScope)
            controller.onPlaybackStarted(sampleSelection)
            runCurrent()

            controller.onSnapshot(600_000L, 510_000L, false)
            runCurrent()

            val effects = mutableListOf<NextEpisodeEffect>()
            val job = backgroundScope.launch { controller.effects.collect { effects.add(it) } }

            controller.onSnapshot(600_000L, 600_000L, ended = true)
            runCurrent()

            assertNull(controller.state.value.autoPlayCountdownSeconds)
            advanceTimeBy(6000L)
            runCurrent()

            assertTrue(effects.isEmpty())
            assertEquals(NextUi.ChooseSource, controller.state.value.nextUi)
            job.cancel()
        }

    @Test
    fun NV15_autoPlayOn_endedWhileSearching_switchesWhenFoundThenCountdown() =
        runTest {
            var answer: SameSourceResult = SameSourceResult.NotFound(SameSourceReason.TIMEOUT)
            coEvery { sameSource.find(any(), any()) } coAnswers {
                kotlinx.coroutines.delay(200L)
                answer
            }

            val controller = createController(backgroundScope)
            controller.onPlaybackStarted(sampleSelection)
            runCurrent()

            controller.onSnapshot(600_000L, 510_000L, false)
            runCurrent()

            // Ended while SEARCHING
            controller.onSnapshot(600_000L, 600_000L, ended = true)
            runCurrent()
            assertNull(controller.state.value.autoPlayCountdownSeconds)

            val effects = mutableListOf<NextEpisodeEffect>()
            val job = backgroundScope.launch { controller.effects.collect { effects.add(it) } }

            // Found arrives
            answer = SameSourceResult.Found(sampleFoundStream)
            advanceTimeBy(250L)
            runCurrent()

            // Countdown starts
            assertEquals(5, controller.state.value.autoPlayCountdownSeconds)
            advanceTimeBy(5000L)
            runCurrent()

            assertEquals(listOf(NextEpisodeEffect.SwitchToNext), effects)
            job.cancel()
        }

    @Test
    fun NV16_seekBackAndForward_rearmsAfterDismiss() =
        runTest {
            val controller = createController(backgroundScope)
            controller.onPlaybackStarted(sampleSelection)
            runCurrent()

            controller.onSnapshot(600_000L, 510_000L, false)
            runCurrent()
            controller.onSnapshot(600_000L, 580_000L, false)
            runCurrent()

            assertEquals(NextUi.Ready, controller.state.value.nextUi)
            controller.onDismiss()
            assertEquals(NextUi.None, controller.state.value.nextUi)

            // Seek back to remaining 61s (600_000 - 61_000 = 539_000)
            controller.onSnapshot(600_000L, 539_000L, false)
            runCurrent()

            // Seek forward into window (remaining 20s = 580_000)
            controller.onSnapshot(600_000L, 580_000L, false)
            runCurrent()

            assertEquals(NextUi.Ready, controller.state.value.nextUi)
        }

    @Test
    fun NV17_clear_cancelsEverything() =
        runTest {
            coEvery { sameSource.find(any(), any()) } coAnswers {
                kotlinx.coroutines.delay(10_000L)
                SameSourceResult.Found(sampleFoundStream)
            }
            val controller = createController(backgroundScope)
            controller.onPlaybackStarted(sampleSelection)
            runCurrent()

            controller.onSnapshot(600_000L, 510_000L, false)
            runCurrent()

            controller.clear()
            advanceTimeBy(15_000L)
            runCurrent()

            io.mockk.verify(exactly = 0) { startPlayback(any(), any()) }
        }

    @Test
    fun NV18_reopenAfterFailure_doesNotResolveOrPrefetchTwice() =
        runTest {
            val controller = createController(backgroundScope)
            controller.onPlaybackStarted(sampleSelection)
            runCurrent()

            controller.onSnapshot(600_000L, 510_000L, false)
            runCurrent()

            // Reopen after failure with same selection
            controller.onPlaybackStarted(sampleSelection)
            runCurrent()

            coVerify(exactly = 1) { finder.find("series", "tt123", "tt123:1:4") }
            coVerify(exactly = 1) { sameSource.find(any(), any()) }
        }

    @Test
    fun NV19_switchHappensAtMostOnce() =
        runTest {
            val controller = createController(backgroundScope)
            controller.onPlaybackStarted(sampleSelection)
            runCurrent()

            controller.onSnapshot(600_000L, 510_000L, false)
            runCurrent()

            val effects = mutableListOf<NextEpisodeEffect>()
            val job = backgroundScope.launch { controller.effects.collect { effects.add(it) } }

            controller.onSnapshot(600_000L, 600_000L, ended = true)
            controller.onNextTapped()
            advanceTimeBy(5000L)
            runCurrent()

            assertEquals(1, effects.size)
            assertEquals(NextEpisodeEffect.SwitchToNext, effects.first())
            io.mockk.verify(exactly = 1) { startPlayback(any(), any()) }
            job.cancel()
        }

    @Test
    fun NV20_stateAndToString_haveNoIdsOrUrls() =
        runTest {
            val controller = createController(backgroundScope)
            controller.onPlaybackStarted(sampleSelection)
            runCurrent()

            controller.onSnapshot(600_000L, 510_000L, false)
            runCurrent()
            controller.onSnapshot(600_000L, 580_000L, false)
            runCurrent()

            val st = controller.state.value
            assertEquals(Pair(1, 5), st.nextEpisodeLabel)

            val str = st.toString()
            assertFalse(str.contains("tt123"))
            assertFalse(str.contains("http"))
            assertFalse(str.contains("group1"))
            assertTrue(str.contains("1") && str.contains("5"))
        }
}
