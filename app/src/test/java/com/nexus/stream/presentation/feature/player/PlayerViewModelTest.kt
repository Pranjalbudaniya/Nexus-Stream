package com.nexus.stream.presentation.feature.player

import android.content.Context
import android.view.View
import androidx.lifecycle.ViewModel
import com.nexus.stream.data.player.AudioFocusController
import com.nexus.stream.data.player.AudioFocusEvent
import com.nexus.stream.data.player.ImportResult
import com.nexus.stream.data.player.PlayerController
import com.nexus.stream.data.player.PlayerControllerFactory
import com.nexus.stream.data.player.PlayerSnapshot
import com.nexus.stream.data.player.PlayerTrack
import com.nexus.stream.data.player.PlayerTracks
import com.nexus.stream.data.player.SubtitleFileImporter
import com.nexus.stream.data.player.SubtitleRenderStyle
import com.nexus.stream.data.player.VideoScale
import com.nexus.stream.data.player.toRenderStyle
import com.nexus.stream.domain.model.AppError
import com.nexus.stream.domain.model.PlaybackSource
import com.nexus.stream.domain.model.StreamSort
import com.nexus.stream.domain.model.Subtitle
import com.nexus.stream.domain.model.SubtitleBackground
import com.nexus.stream.domain.model.SubtitleEdge
import com.nexus.stream.domain.model.SubtitleSize
import com.nexus.stream.domain.model.SubtitleStyle
import com.nexus.stream.domain.model.TorrentFailReason
import com.nexus.stream.domain.model.TorrentFile
import com.nexus.stream.domain.model.TorrentRequest
import com.nexus.stream.domain.model.TorrentState
import com.nexus.stream.domain.model.WatchProgress
import com.nexus.stream.domain.repository.PlayerPrefsRepository
import com.nexus.stream.domain.repository.ProgressRepository
import com.nexus.stream.domain.repository.ProxyDiagnostics
import com.nexus.stream.domain.repository.ProxySession
import com.nexus.stream.domain.repository.StreamProxy
import com.nexus.stream.domain.repository.TorrentEngine
import com.nexus.stream.domain.usecase.LoadOnlineSubtitles
import com.nexus.stream.domain.usecase.PlaybackProgressTracker
import com.nexus.stream.domain.usecase.ProgressRules
import com.nexus.stream.domain.usecase.SUBTITLES_OFF
import com.nexus.stream.domain.usecase.SaveOutcome
import com.nexus.stream.domain.usecase.SaveProgress
import com.nexus.stream.domain.usecase.SubtitleHints
import com.nexus.stream.domain.usecase.progressKeyFor
import com.nexus.stream.presentation.common.PlaybackRequest
import com.nexus.stream.presentation.theme.tokens.SubtitlePalette
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verifyOrder
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
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

@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("LargeClass")
class PlayerViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val holder = PlaybackSessionHolder()
    private val fakeFactory = FakePlayerControllerFactory()
    private val fakeProxy = FakeStreamProxy()
    private val fakeEngine = FakeTorrentEngine()
    private val fakeAudioFocus = FakeAudioFocusController()
    private val fakePrefs = FakePlayerPrefsRepository()
    private val loadOnlineSubtitlesUseCase = mockk<LoadOnlineSubtitles>(relaxed = true)
    private val fakeSubtitleImporter = FakeSubtitleFileImporter()
    private var fakeSaveProgress = mockk<SaveProgress>(relaxed = true)
    private var fakeProgressRepository = mockk<ProgressRepository>(relaxed = true)
    private val statsHub =
        com.nexus.stream.data.torrent
            .StreamStatsHub()

    private val createdViewModels = mutableListOf<PlayerViewModel>()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeSaveProgress = mockk(relaxed = true)
        fakeProgressRepository = mockk(relaxed = true)
        coEvery { fakeProgressRepository.get(any()) } returns null
        coEvery { fakeSaveProgress(any(), any(), any(), any()) } returns SaveOutcome.Skipped
    }

    @After
    fun tearDown() {
        createdViewModels.forEach { it.onCleared() }
        createdViewModels.clear()
        Dispatchers.resetMain()
    }

    private fun createViewModel(
        h: PlaybackSessionHolder = holder,
        loader: LoadOnlineSubtitles = loadOnlineSubtitlesUseCase,
        importer: SubtitleFileImporter = fakeSubtitleImporter,
        nextEpisodeController: NextEpisodeController? = null,
    ): PlayerViewModel {
        val nextFactory =
            object : NextEpisodeController.Factory(
                finder = mockk(relaxed = true),
                sameSource = mockk(relaxed = true),
                sourceMemory = mockk(relaxed = true),
                startPlayback = mockk(relaxed = true),
                playerPrefsRepository = fakePrefs,
            ) {
                override fun create(scope: CoroutineScope): NextEpisodeController =
                    nextEpisodeController ?: super.create(scope)
            }
        val vm =
            PlayerViewModel(
                holder = h,
                controllerFactory = fakeFactory,
                streamProxy = fakeProxy,
                torrentEngine = fakeEngine,
                statsHub = statsHub,
                audioFocusController = fakeAudioFocus,
                playerPrefsRepository = fakePrefs,
                loadOnlineSubtitlesUseCase = loader,
                subtitleFileImporter = importer,
                saveProgress = fakeSaveProgress,
                progressRepository = fakeProgressRepository,
                nextEpisodeControllerFactory = nextFactory,
                ioDispatcher = Dispatchers.Unconfined,
                mainDispatcher = testDispatcher,
            )
        createdViewModels.add(vm)
        return vm
    }

    @Test
    fun PL1_directUrl_callsPlayOnce_snapshotPlayingSetsPhasePlaying() =
        runTest(testDispatcher) {
            val url = "https://example.com/video.mp4"
            holder.start(
                PlaybackSelection("Movie", PlaybackSource.Direct(url), "movie", "m1", "v1"),
            )
            val vm = createViewModel()
            advanceUntilIdle()

            val ctrl = fakeFactory.createdControllers.single()
            assertEquals(listOf(url), ctrl.playedUrls)
            assertEquals(PlayerPhase.Preparing(PreparingLabel.OPENING), vm.uiState.value.phase)

            ctrl.snapshotFlow.value = PlayerSnapshot(isPlaying = true)
            advanceUntilIdle()

            assertEquals(PlayerPhase.Playing, vm.uiState.value.phase)
            assertTrue(vm.uiState.value.isPlaying)
        }

    @Test
    fun PL2_directWithHeaders_callsProxyOpen_playsSessionUrl_onClearedClosesSession() =
        runTest(testDispatcher) {
            val url = "https://example.com/video.mp4"
            val headers = mapOf("Authorization" to "Bearer token123")
            fakeProxy.nextSessionUrl = "http://127.0.0.1:1234/p/tok/stream.mp4"

            holder.start(
                PlaybackSelection("Movie", PlaybackSource.DirectWithHeaders(url, headers), "movie", "m1", "v1"),
            )
            val vm = createViewModel()
            advanceUntilIdle()

            assertEquals(1, fakeProxy.openCalls.size)
            assertEquals(url to headers, fakeProxy.openCalls.single())

            val ctrl = fakeFactory.createdControllers.single()
            assertEquals(listOf("http://127.0.0.1:1234/p/tok/stream.mp4"), ctrl.playedUrls)

            val clearMethod = ViewModel::class.java.getDeclaredMethod("onCleared")
            clearMethod.isAccessible = true
            clearMethod.invoke(vm)
            advanceUntilIdle()

            assertEquals(1, fakeProxy.closedSessionsCount)
        }

    @Test
    fun PL3_openThrowsIllegalArgumentException_setsErrorUnplayable_playNeverCalled() =
        runTest(testDispatcher) {
            fakeProxy.throwOnOpen = IllegalArgumentException("Invalid URL")
            holder.start(
                PlaybackSelection(
                    "Movie",
                    PlaybackSource.DirectWithHeaders("bad-url", emptyMap()),
                    "movie",
                    "m1",
                    "v1",
                ),
            )
            val vm = createViewModel()
            advanceUntilIdle()

            assertEquals(PlayerPhase.Error(PlayerError.UNPLAYABLE), vm.uiState.value.phase)
            assertTrue(fakeFactory.createdControllers.isEmpty())
        }

    @Test
    fun PL4_torrentFlow_preparingLabelsPercent_playsLocalUrlOnce_nullUrlGivesErrorOther() =
        runTest(testDispatcher) {
            val req = TorrentRequest(infoHash = "0123456789abcdef0123456789abcdef01234567")
            holder.start(
                PlaybackSelection("Torrent Movie", PlaybackSource.Torrent(req), "movie", "m1", "v1"),
            )
            val vm = createViewModel()
            advanceUntilIdle()

            assertEquals(PlayerPhase.Preparing(PreparingLabel.FETCHING_METADATA), vm.uiState.value.phase)

            fakeEngine.prepareFlow.emit(TorrentState.Buffering(percent = 42, speedBps = 1000L, peers = 3))
            advanceUntilIdle()

            assertEquals(
                PlayerPhase.Preparing(PreparingLabel.BUFFERING, percent = 42, peers = 3),
                vm.uiState.value.phase,
            )

            val streamUrl = "http://127.0.0.1:9999/t/hash/stream.mkv"
            fakeEngine.prepareFlow.emit(TorrentState.Ready(1, "stream.mkv", 1024L, streamUrl))
            advanceUntilIdle()

            val ctrl = fakeFactory.createdControllers.single()
            assertEquals(listOf(streamUrl), ctrl.playedUrls)

            // Second ready emission should not call play again
            fakeEngine.prepareFlow.emit(TorrentState.Ready(1, "stream.mkv", 1024L, streamUrl))
            advanceUntilIdle()
            assertEquals(1, ctrl.playedUrls.size)

            // Test ready with null localUrl
            val nullUrlHolder = PlaybackSessionHolder()
            nullUrlHolder.start(
                PlaybackSelection("Movie", PlaybackSource.Torrent(req), "movie", "m1", "v1"),
            )
            val nullVm = createViewModel(nullUrlHolder)
            advanceUntilIdle()
            fakeEngine.prepareFlow.emit(TorrentState.Ready(1, "stream.mkv", 1024L, null))
            advanceUntilIdle()
            assertEquals(PlayerPhase.Error(PlayerError.OTHER), nullVm.uiState.value.phase)
        }

    @Test
    fun PL5_needsFilePick_setsPickFile_pickFileEventSelectsFile() =
        runTest(testDispatcher) {
            val hash = "0123456789abcdef0123456789abcdef01234567"
            val req = TorrentRequest(infoHash = hash)
            holder.start(
                PlaybackSelection("Movie", PlaybackSource.Torrent(req), "movie", "m1", "v1"),
            )
            val vm = createViewModel()
            advanceUntilIdle()

            val files =
                listOf(
                    TorrentFile(0, "sample.mp4", 100L),
                    TorrentFile(2, "main.mkv", 1000L),
                )
            fakeEngine.prepareFlow.emit(TorrentState.NeedsFilePick(files))
            advanceUntilIdle()

            val phase = vm.uiState.value.phase as PlayerPhase.PickFile
            assertEquals(2, phase.files.size)
            assertEquals("main.mkv", phase.files[1].name)
            assertEquals(2, phase.files[1].index)

            vm.onEvent(PlayerEvent.PickFile(2))
            advanceUntilIdle()

            assertEquals(listOf(hash to 2), fakeEngine.selectFileCalls)
        }

    @Test
    fun PL6_torrentFailures_mapToCorrectPlayerErrors() =
        runTest(testDispatcher) {
            val req = TorrentRequest(infoHash = "0123456789abcdef0123456789abcdef01234567")
            val reasons =
                listOf(
                    TorrentFailReason.METADATA_TIMEOUT to PlayerError.TORRENT_TIMEOUT,
                    TorrentFailReason.NO_VIDEO_FILE to PlayerError.TORRENT_NO_VIDEO,
                    TorrentFailReason.STORAGE to PlayerError.TORRENT_STORAGE,
                    TorrentFailReason.ENGINE_UNAVAILABLE to PlayerError.TORRENT_UNAVAILABLE,
                )

            for ((reason, expected) in reasons) {
                holder.start(PlaybackSelection("Movie", PlaybackSource.Torrent(req), "movie", "m1", "v1"))
                val vm = createViewModel()
                advanceUntilIdle()

                fakeEngine.prepareFlow.emit(TorrentState.Failed(AppError.Torrent(reason)))
                advanceUntilIdle()

                assertEquals(PlayerPhase.Error(expected), vm.uiState.value.phase)
            }
        }

    @Test
    fun PL7_meteredBlocked_allowMeteredOnceCancelsOldAndPreparesWithAllowMeteredTrue() =
        runTest(testDispatcher) {
            val hash = "0123456789abcdef0123456789abcdef01234567"
            val req = TorrentRequest(infoHash = hash, allowMetered = false)
            holder.start(
                PlaybackSelection("Movie", PlaybackSource.Torrent(req), "movie", "m1", "v1"),
            )
            val vm = createViewModel()
            advanceUntilIdle()

            fakeEngine.prepareFlow.emit(TorrentState.Failed(AppError.MeteredBlocked))
            advanceUntilIdle()

            assertEquals(PlayerPhase.Error(PlayerError.METERED_BLOCKED), vm.uiState.value.phase)
            assertEquals(1, fakeEngine.prepareCalls.size)
            assertFalse(fakeEngine.prepareCalls[0].allowMetered)

            vm.onEvent(PlayerEvent.AllowMeteredOnce)
            advanceUntilIdle()

            assertTrue(fakeEngine.isCancelled)
            assertEquals(2, fakeEngine.prepareCalls.size)
            assertTrue(fakeEngine.prepareCalls[1].allowMetered)
            assertEquals(hash, fakeEngine.prepareCalls[1].infoHash)
        }

    @Test
    fun PL8_emptyHolder_setsPhaseEmpty_controllerFactoryNeverCalled() =
        runTest(testDispatcher) {
            holder.clear()
            val vm = createViewModel()
            advanceUntilIdle()

            assertEquals(PlayerPhase.Empty, vm.uiState.value.phase)
            assertTrue(fakeFactory.createdControllers.isEmpty())
            assertNull(vm.getController())
        }

    @Test
    fun PL9_onCleared_cancelsTorrent_releasesController_closesSession_clearsHolder() =
        runTest(testDispatcher) {
            val req = TorrentRequest(infoHash = "0123456789abcdef0123456789abcdef01234567")
            holder.start(
                PlaybackSelection("Movie", PlaybackSource.Torrent(req), "movie", "m1", "v1"),
            )
            val vm = createViewModel()
            advanceUntilIdle()

            fakeEngine.prepareFlow.emit(TorrentState.Ready(0, "video.mp4", 1000L, "http://127.0.0.1:1234/stream.mp4"))
            advanceUntilIdle()

            val ctrl = fakeFactory.createdControllers.single()
            assertEquals(0, ctrl.releaseCallCount)
            assertFalse(fakeEngine.isCancelled)

            val clearMethod = ViewModel::class.java.getDeclaredMethod("onCleared")
            clearMethod.isAccessible = true
            clearMethod.invoke(vm)
            advanceUntilIdle()

            assertTrue(fakeEngine.isCancelled)
            assertEquals(1, ctrl.releaseCallCount)
            assertNull(holder.current.value)
        }

    @Test
    fun PL10_snapshotFailed_setsErrorPlaybackFailed() =
        runTest(testDispatcher) {
            holder.start(
                PlaybackSelection("Movie", PlaybackSource.Direct("https://example.com/v.mp4"), "movie", "m1", "v1"),
            )
            val vm = createViewModel()
            advanceUntilIdle()

            val ctrl = fakeFactory.createdControllers.single()
            ctrl.snapshotFlow.value = PlayerSnapshot(failed = true)
            advanceUntilIdle()

            assertEquals(PlayerPhase.Error(PlayerError.PLAYBACK_FAILED), vm.uiState.value.phase)
        }

    @Test
    fun PL11_seekToOnlyWhenSeekable_togglePlayPausesOrResumes() =
        runTest(testDispatcher) {
            holder.start(
                PlaybackSelection("Movie", PlaybackSource.Direct("https://example.com/v.mp4"), "movie", "m1", "v1"),
            )
            val vm = createViewModel()
            advanceUntilIdle()

            val ctrl = fakeFactory.createdControllers.single()
            ctrl.snapshotFlow.value = PlayerSnapshot(isSeekable = false)
            advanceUntilIdle()

            vm.onEvent(PlayerEvent.SeekTo(5000L))
            advanceUntilIdle()
            assertTrue(ctrl.seekPositions.isEmpty())

            ctrl.snapshotFlow.value = PlayerSnapshot(isSeekable = true)
            advanceUntilIdle()

            vm.onEvent(PlayerEvent.SeekTo(5000L))
            advanceUntilIdle()
            assertEquals(listOf(5000L), ctrl.seekPositions)

            ctrl.snapshotFlow.value = PlayerSnapshot(isPlaying = true)
            advanceUntilIdle()
            vm.onEvent(PlayerEvent.TogglePlay)
            advanceUntilIdle()
            assertEquals(1, ctrl.pauseCallCount)

            ctrl.snapshotFlow.value = PlayerSnapshot(isPlaying = false)
            advanceUntilIdle()
            vm.onEvent(PlayerEvent.TogglePlay)
            advanceUntilIdle()
            assertEquals(1, ctrl.resumeCallCount)
        }

    @Test
    fun PL12_controlsVisibleAtStart_hideAfter3sPlaying_visibleWhenPausedBuffering_toggleRestarts() =
        runTest(testDispatcher) {
            holder.start(
                PlaybackSelection("Movie", PlaybackSource.Direct("https://example.com/v.mp4"), "movie", "m1", "v1"),
            )
            val vm = createViewModel()
            advanceUntilIdle()

            val ctrl = fakeFactory.createdControllers.single()
            assertTrue(vm.uiState.value.controlsVisible)

            // Start playing and not buffering
            ctrl.snapshotFlow.value = PlayerSnapshot(isPlaying = true, isBuffering = false)
            runCurrent()
            assertTrue(vm.uiState.value.controlsVisible)

            // Advance 2 seconds - should still be visible
            advanceTimeBy(2_000L)
            runCurrent()
            assertTrue(vm.uiState.value.controlsVisible)

            // Advance 1 more second (total 3s) - controls should hide
            advanceTimeBy(1_000L)
            runCurrent()
            assertFalse(vm.uiState.value.controlsVisible)

            // Toggle controls turns them on and restarts 3s timer
            vm.onEvent(PlayerEvent.ToggleControls)
            runCurrent()
            assertTrue(vm.uiState.value.controlsVisible)

            advanceTimeBy(2_000L)
            runCurrent()
            assertTrue(vm.uiState.value.controlsVisible)

            advanceTimeBy(1_000L)
            runCurrent()
            assertFalse(vm.uiState.value.controlsVisible)

            // Paused forces controls visible
            ctrl.snapshotFlow.value = PlayerSnapshot(isPlaying = false, isBuffering = false)
            runCurrent()
            assertTrue(vm.uiState.value.controlsVisible)
            advanceTimeBy(5_000L)
            runCurrent()
            assertTrue(vm.uiState.value.controlsVisible)

            // Buffering forces controls visible
            ctrl.snapshotFlow.value = PlayerSnapshot(isPlaying = true, isBuffering = true)
            runCurrent()
            assertTrue(vm.uiState.value.controlsVisible)
            advanceTimeBy(5_000L)
            runCurrent()
            assertTrue(vm.uiState.value.controlsVisible)
        }

    @Test
    fun PL13_uiStateToStringContainsNoUrlHashOrHeaderValue() =
        runTest(testDispatcher) {
            val secretUrl = "https://secret.stream.example/media.mkv"
            val secretHeader = "secret_auth_abc123"
            fakeProxy.nextSessionUrl = "http://127.0.0.1:9090/p/secret_token_456/stream.mp4"

            holder.start(
                PlaybackSelection(
                    title = "Public Title",
                    source = PlaybackSource.DirectWithHeaders(secretUrl, mapOf("Authorization" to secretHeader)),
                    type = "movie",
                    metaId = "tt0001",
                    videoId = "tt0001",
                ),
            )
            val vm = createViewModel()
            advanceUntilIdle()

            val stateStr = vm.uiState.value.toString()
            assertFalse(stateStr.contains("secret", ignoreCase = true))
            assertFalse(stateStr.contains("stream.example"))
            assertFalse(stateStr.contains(secretHeader))
            assertFalse(stateStr.contains("9090"))
            assertFalse(stateStr.contains("http://"))
            assertTrue(stateStr.contains("Public Title"))
        }

    @Test
    fun PL14_appStopped_pausesWhenPlaying_doesNothingWhenPaused() =
        runTest(testDispatcher) {
            holder.start(
                PlaybackSelection("Movie", PlaybackSource.Direct("https://example.com/v.mp4"), "movie", "m1", "v1"),
            )
            val vm = createViewModel()
            advanceUntilIdle()

            val ctrl = fakeFactory.createdControllers.single()
            ctrl.snapshotFlow.value = PlayerSnapshot(isPlaying = false)
            advanceUntilIdle()

            vm.onEvent(PlayerEvent.AppStopped)
            advanceUntilIdle()
            assertEquals(0, ctrl.pauseCallCount)

            ctrl.snapshotFlow.value = PlayerSnapshot(isPlaying = true)
            advanceUntilIdle()

            vm.onEvent(PlayerEvent.AppStopped)
            advanceUntilIdle()
            assertEquals(1, ctrl.pauseCallCount)
        }

    @Test
    fun PL15_snapshotEnded_setsEnded_andControlsVisible() =
        runTest(testDispatcher) {
            holder.start(
                PlaybackSelection("Movie", PlaybackSource.Direct("https://example.com/v.mp4"), "movie", "m1", "v1"),
            )
            val vm = createViewModel()
            advanceUntilIdle()

            val ctrl = fakeFactory.createdControllers.single()
            ctrl.snapshotFlow.value = PlayerSnapshot(isPlaying = true)
            advanceTimeBy(3_000L)
            runCurrent()
            assertFalse(vm.uiState.value.controlsVisible)

            ctrl.snapshotFlow.value = PlayerSnapshot(ended = true, isPlaying = false)
            runCurrent()
            assertEquals(PlayerPhase.Ended, vm.uiState.value.phase)
            assertTrue(vm.uiState.value.controlsVisible)
        }

    @Test
    fun PL16_torrentPlaying_snapshotFailed_autoRecoversAndSeeksToOldPosition() =
        runTest(testDispatcher) {
            val req = TorrentRequest("0123456789abcdef0123456789abcdef01234567")
            holder.start(
                PlaybackSelection("Movie", PlaybackSource.Torrent(req), "movie", "m1", "v1"),
            )
            val vm = createViewModel()
            advanceUntilIdle()

            val streamUrl = "http://127.0.0.1:1234/t/tok/stream.mkv"
            fakeEngine.prepareFlow.emit(TorrentState.Ready(1, "video.mkv", 1000L, streamUrl))
            advanceUntilIdle()

            val ctrl = fakeFactory.createdControllers.single()
            assertEquals(listOf(streamUrl), ctrl.playedUrls)

            // Playing at 42_000ms
            ctrl.snapshotFlow.value = PlayerSnapshot(isPlaying = true, positionMs = 42_000L, isSeekable = true)
            advanceUntilIdle()
            assertEquals(PlayerPhase.Playing, vm.uiState.value.phase)

            // Fails while playing -> triggers auto-recovery
            ctrl.snapshotFlow.value = PlayerSnapshot(failed = true)
            advanceUntilIdle()

            assertEquals(PlayerPhase.Preparing(PreparingLabel.OPENING), vm.uiState.value.phase)
            assertEquals(listOf(streamUrl, streamUrl), ctrl.playedUrls)

            // Resumes playing -> seeks to 42_000ms
            ctrl.snapshotFlow.value = PlayerSnapshot(isPlaying = true, isSeekable = true)
            advanceUntilIdle()

            assertEquals(listOf(42_000L), ctrl.seekPositions)
            assertEquals(PlayerPhase.Playing, vm.uiState.value.phase)
        }

    @Test
    fun PL17_secondFailureWithin60s_errors_after60s_recovers() =
        runTest(testDispatcher) {
            var clock = 100_000L
            val req = TorrentRequest("0123456789abcdef0123456789abcdef01234567")
            holder.start(
                PlaybackSelection("Movie", PlaybackSource.Torrent(req), "movie", "m1", "v1"),
            )
            val vm = createViewModel()
            vm.nowMs = { clock }
            advanceUntilIdle()

            val streamUrl = "http://127.0.0.1:1234/t/tok/stream.mkv"
            fakeEngine.prepareFlow.emit(TorrentState.Ready(1, "video.mkv", 1000L, streamUrl))
            advanceUntilIdle()

            val ctrl = fakeFactory.createdControllers.single()
            ctrl.snapshotFlow.value = PlayerSnapshot(isPlaying = true, positionMs = 10_000L)
            advanceUntilIdle()

            // 1st failure -> recovers
            ctrl.snapshotFlow.value = PlayerSnapshot(failed = true)
            advanceUntilIdle()
            assertTrue(vm.uiState.value.phase is PlayerPhase.Preparing)

            // First playing snapshot after recovery
            ctrl.snapshotFlow.value = PlayerSnapshot(isPlaying = true, positionMs = 10_000L)
            advanceUntilIdle()
            assertEquals(PlayerPhase.Playing, vm.uiState.value.phase)

            // 2nd failure at clock + 30s (< 60s) -> Error(PLAYBACK_FAILED)
            clock += 30_000L
            ctrl.snapshotFlow.value = PlayerSnapshot(failed = true)
            advanceUntilIdle()
            assertEquals(PlayerPhase.Error(PlayerError.PLAYBACK_FAILED), vm.uiState.value.phase)

            // Now test after 60s: recover via retry, then fail after 61s
            vm.onEvent(PlayerEvent.Retry)
            advanceUntilIdle()
            ctrl.snapshotFlow.value = PlayerSnapshot(isPlaying = true, positionMs = 20_000L)
            advanceUntilIdle()
            assertEquals(PlayerPhase.Playing, vm.uiState.value.phase)

            clock += 65_000L
            ctrl.snapshotFlow.value = PlayerSnapshot(failed = true)
            advanceUntilIdle()
            assertTrue(vm.uiState.value.phase is PlayerPhase.Preparing)
        }

    @Test
    fun PL18_directStreamWithoutProxy_failsWithoutAutoRecovery() =
        runTest(testDispatcher) {
            holder.start(
                PlaybackSelection("Movie", PlaybackSource.Direct("https://example.com/v.mp4"), "movie", "m1", "v1"),
            )
            val vm = createViewModel()
            advanceUntilIdle()

            val ctrl = fakeFactory.createdControllers.single()
            ctrl.snapshotFlow.value = PlayerSnapshot(isPlaying = true, positionMs = 15_000L)
            advanceUntilIdle()
            assertEquals(PlayerPhase.Playing, vm.uiState.value.phase)

            ctrl.snapshotFlow.value = PlayerSnapshot(failed = true)
            advanceUntilIdle()

            assertEquals(PlayerPhase.Error(PlayerError.PLAYBACK_FAILED), vm.uiState.value.phase)
            assertEquals(1, ctrl.playedUrls.size)
        }

    @Test
    fun PL19_retryFromErrorState_reopensOnce() =
        runTest(testDispatcher) {
            val url = "https://example.com/video.mp4"
            holder.start(
                PlaybackSelection("Movie", PlaybackSource.Direct(url), "movie", "m1", "v1"),
            )
            val vm = createViewModel()
            advanceUntilIdle()

            val ctrl = fakeFactory.createdControllers.single()
            ctrl.snapshotFlow.value = PlayerSnapshot(failed = true)
            advanceUntilIdle()
            assertEquals(PlayerPhase.Error(PlayerError.PLAYBACK_FAILED), vm.uiState.value.phase)

            vm.onEvent(PlayerEvent.Retry)
            advanceUntilIdle()

            assertEquals(PlayerPhase.Preparing(PreparingLabel.OPENING), vm.uiState.value.phase)
            assertEquals(listOf(url, url), ctrl.playedUrls)
        }

    @Test
    fun PL20_directWithHeaders_diagnosticFailureMapping() =
        runTest(testDispatcher) {
            fun assertDiagnosticMapping(
                diag: ProxyDiagnostics,
                expectedError: PlayerError,
                expectedStatus: Int?,
            ) {
                fakeProxy.nextDiagnostics = MutableStateFlow(diag)
                val testHolder = PlaybackSessionHolder()
                testHolder.start(
                    PlaybackSelection(
                        "Stream",
                        PlaybackSource.DirectWithHeaders("https://ex.com/s", mapOf("A" to "B")),
                        "movie",
                        "m",
                        "v",
                    ),
                )
                val testVm = createViewModel(testHolder)
                advanceUntilIdle()
                val ctrl = fakeFactory.createdControllers.last()
                ctrl.snapshotFlow.value = PlayerSnapshot(failed = true)
                advanceUntilIdle()
                assertEquals(PlayerPhase.Error(expectedError, expectedStatus), testVm.uiState.value.phase)
            }

            // 403 -> SOURCE_REFUSED
            assertDiagnosticMapping(ProxyDiagnostics(lastUpstreamStatus = 403), PlayerError.SOURCE_REFUSED, 403)

            // 404 -> SOURCE_NOT_FOUND
            assertDiagnosticMapping(ProxyDiagnostics(lastUpstreamStatus = 404), PlayerError.SOURCE_NOT_FOUND, 404)

            // 502 -> SOURCE_UNREACHABLE
            assertDiagnosticMapping(ProxyDiagnostics(lastUpstreamStatus = 502), PlayerError.SOURCE_UNREACHABLE, 502)

            // unsupportedPathRequests > 0 -> SOURCE_UNSUPPORTED_PATH (wins over status)
            assertDiagnosticMapping(
                ProxyDiagnostics(lastUpstreamStatus = 403, unsupportedPathRequests = 2),
                PlayerError.SOURCE_UNSUPPORTED_PATH,
                null,
            )
        }

    @Test
    fun PL21_privacyCheck_noUrlHeaderOrHashInToString() =
        runTest(testDispatcher) {
            val secretUrl = "https://secret.stream.provider.com/playlist.m3u8?token=xyz123"
            val secretHeaderVal = "Bearer secret_jwt_token_987654"
            val secretHash = "4a5b6c7d8e9f0123456789abcdef0123456789ab"
            holder.start(
                PlaybackSelection(
                    title = "Secret Movie",
                    source = PlaybackSource.DirectWithHeaders(secretUrl, mapOf("Authorization" to secretHeaderVal)),
                    type = "movie",
                    metaId = "tt123",
                    videoId = "tt123",
                ),
            )
            val vm = createViewModel()
            advanceUntilIdle()

            val str = vm.uiState.value.toString()
            assertFalse(str.contains(secretUrl))
            assertFalse(str.contains(secretHeaderVal))
            assertFalse(str.contains(secretHash))
        }

    @Test
    fun PL22_statsHub_exposedOnlyForTorrentSource() =
        runTest(testDispatcher) {
            val dummyStats =
                com.nexus.stream.domain.model.StreamStats(
                    speedBps = 1000L,
                    peers = 5,
                    aheadBytes = 2000L,
                    downloadedBytes = 5000L,
                    fileSizeBytes = 10000L,
                    waitMs = 0L,
                )
            statsHub.publish(dummyStats)

            // 1. Direct stream -> stats is null
            holder.start(
                PlaybackSelection("Direct", PlaybackSource.Direct("https://ex.com/v.mp4"), "movie", "m", "v"),
            )
            val vmDirect = createViewModel()
            advanceUntilIdle()
            assertNull(vmDirect.uiState.value.stats)

            // 2. Torrent stream -> stats is visible
            val req = TorrentRequest("0123456789abcdef0123456789abcdef01234567")
            holder.start(
                PlaybackSelection("Torrent", PlaybackSource.Torrent(req), "movie", "m", "v"),
            )
            val vmTorrent = createViewModel()
            advanceUntilIdle()
            assertEquals(dummyStats, vmTorrent.uiState.value.stats)
        }

    @Test
    fun PL23_firstPlayingSnapshot_requestsFocusOnce_deniedCallsPause() =
        runTest(testDispatcher) {
            val url = "https://example.com/video.mp4"
            holder.start(PlaybackSelection("Movie", PlaybackSource.Direct(url), "movie", "m1", "v1"))
            fakeAudioFocus.requestResult = false
            createViewModel()
            advanceUntilIdle()

            val ctrl = fakeFactory.createdControllers.single()
            assertEquals(0, fakeAudioFocus.requestCount)
            assertEquals(0, ctrl.pauseCallCount)

            // Emit first playing snapshot
            ctrl.snapshotFlow.value = PlayerSnapshot(isPlaying = true)
            advanceUntilIdle()

            assertEquals(1, fakeAudioFocus.requestCount)
            assertEquals(1, ctrl.pauseCallCount)

            // Subsequent playing snapshot does not request again
            ctrl.snapshotFlow.value = PlayerSnapshot(isPlaying = true, positionMs = 5000L)
            advanceUntilIdle()
            assertEquals(1, fakeAudioFocus.requestCount)
        }

    @Test
    fun PL24_focusLostWhilePlaying_pausesAndAbandons_laterGainedDoesNotResume() =
        runTest(testDispatcher) {
            val url = "https://example.com/video.mp4"
            holder.start(PlaybackSelection("Movie", PlaybackSource.Direct(url), "movie", "m1", "v1"))
            fakeAudioFocus.requestResult = true
            createViewModel()
            advanceUntilIdle()

            val ctrl = fakeFactory.createdControllers.single()
            ctrl.snapshotFlow.value = PlayerSnapshot(isPlaying = true)
            advanceUntilIdle()

            val pauseBefore = ctrl.pauseCallCount
            val abandonBefore = fakeAudioFocus.abandonCount
            val resumeBefore = ctrl.resumeCallCount

            fakeAudioFocus.eventsFlow.emit(AudioFocusEvent.LOST)
            advanceUntilIdle()

            assertEquals(pauseBefore + 1, ctrl.pauseCallCount)
            assertEquals(abandonBefore + 1, fakeAudioFocus.abandonCount)

            // Later GAINED does not resume
            fakeAudioFocus.eventsFlow.emit(AudioFocusEvent.GAINED)
            advanceUntilIdle()
            assertEquals(resumeBefore, ctrl.resumeCallCount)
        }

    @Test
    fun PL25_focusLostTransientWhilePlaying_resumesOnGained_staysPausedIfUserPaused() =
        runTest(testDispatcher) {
            val url = "https://example.com/video.mp4"
            holder.start(PlaybackSelection("Movie", PlaybackSource.Direct(url), "movie", "m1", "v1"))
            fakeAudioFocus.requestResult = true
            createViewModel()
            advanceUntilIdle()

            val ctrl = fakeFactory.createdControllers.single()
            ctrl.snapshotFlow.value = PlayerSnapshot(isPlaying = true)
            advanceUntilIdle()

            val pauseBefore = ctrl.pauseCallCount
            val resumeBefore = ctrl.resumeCallCount

            // 1. Lost transient while playing
            fakeAudioFocus.eventsFlow.emit(AudioFocusEvent.LOST_TRANSIENT)
            advanceUntilIdle()
            assertEquals(pauseBefore + 1, ctrl.pauseCallCount)

            // Gained -> resumes
            fakeAudioFocus.eventsFlow.emit(AudioFocusEvent.GAINED)
            advanceUntilIdle()
            assertEquals(resumeBefore + 1, ctrl.resumeCallCount)

            // 2. While user-paused (isPlaying is false)
            ctrl.snapshotFlow.value = PlayerSnapshot(isPlaying = false)
            advanceUntilIdle()

            val resumeCountBefore = ctrl.resumeCallCount
            fakeAudioFocus.eventsFlow.emit(AudioFocusEvent.LOST_TRANSIENT)
            advanceUntilIdle()

            fakeAudioFocus.eventsFlow.emit(AudioFocusEvent.GAINED)
            advanceUntilIdle()
            assertEquals(resumeCountBefore, ctrl.resumeCallCount)
        }

    @Test
    fun PL26_canDuck_noCalls() =
        runTest(testDispatcher) {
            val url = "https://example.com/video.mp4"
            holder.start(PlaybackSelection("Movie", PlaybackSource.Direct(url), "movie", "m1", "v1"))
            fakeAudioFocus.requestResult = true
            createViewModel()
            advanceUntilIdle()

            val ctrl = fakeFactory.createdControllers.single()
            ctrl.snapshotFlow.value = PlayerSnapshot(isPlaying = true)
            advanceUntilIdle()

            val pauseBefore = ctrl.pauseCallCount
            val resumeBefore = ctrl.resumeCallCount
            val abandonBefore = fakeAudioFocus.abandonCount

            fakeAudioFocus.eventsFlow.emit(AudioFocusEvent.CAN_DUCK)
            advanceUntilIdle()

            assertEquals(pauseBefore, ctrl.pauseCallCount)
            assertEquals(resumeBefore, ctrl.resumeCallCount)
            assertEquals(abandonBefore, fakeAudioFocus.abandonCount)
        }

    @Test
    fun PL27_becomingNoisyWhilePlaying_pauses_noResumeOnGain_whilePausedNothing() =
        runTest(testDispatcher) {
            val url = "https://example.com/video.mp4"
            holder.start(PlaybackSelection("Movie", PlaybackSource.Direct(url), "movie", "m1", "v1"))
            fakeAudioFocus.requestResult = true
            createViewModel()
            advanceUntilIdle()

            val ctrl = fakeFactory.createdControllers.single()
            ctrl.snapshotFlow.value = PlayerSnapshot(isPlaying = true)
            advanceUntilIdle()

            val pauseBefore = ctrl.pauseCallCount
            val resumeBefore = ctrl.resumeCallCount

            // Becoming noisy while playing -> pauses
            fakeAudioFocus.eventsFlow.emit(AudioFocusEvent.BECOMING_NOISY)
            advanceUntilIdle()
            assertEquals(pauseBefore + 1, ctrl.pauseCallCount)

            // Later GAINED does NOT resume
            fakeAudioFocus.eventsFlow.emit(AudioFocusEvent.GAINED)
            advanceUntilIdle()
            assertEquals(resumeBefore, ctrl.resumeCallCount)

            // While paused (isPlaying is false) -> nothing
            ctrl.snapshotFlow.value = PlayerSnapshot(isPlaying = false)
            advanceUntilIdle()
            val pauseCount = ctrl.pauseCallCount
            fakeAudioFocus.eventsFlow.emit(AudioFocusEvent.BECOMING_NOISY)
            advanceUntilIdle()
            assertEquals(pauseCount, ctrl.pauseCallCount)
        }

    @Test
    fun PL28_togglePlayToResume_requestsFocusFirst_deniedStaysPaused() =
        runTest(testDispatcher) {
            val url = "https://example.com/video.mp4"
            holder.start(PlaybackSelection("Movie", PlaybackSource.Direct(url), "movie", "m1", "v1"))
            fakeAudioFocus.requestResult = true
            val vm = createViewModel()
            advanceUntilIdle()

            val ctrl = fakeFactory.createdControllers.single()
            ctrl.snapshotFlow.value = PlayerSnapshot(isPlaying = true)
            advanceUntilIdle()

            // User pauses
            vm.onEvent(PlayerEvent.TogglePlay)
            advanceUntilIdle()
            assertEquals(1, ctrl.pauseCallCount)
            ctrl.snapshotFlow.value = PlayerSnapshot(isPlaying = false)
            advanceUntilIdle()

            // Now deny focus on resume attempt
            fakeAudioFocus.requestResult = false
            val reqBefore = fakeAudioFocus.requestCount
            val resumeBefore = ctrl.resumeCallCount

            vm.onEvent(PlayerEvent.TogglePlay)
            advanceUntilIdle()

            assertEquals(reqBefore + 1, fakeAudioFocus.requestCount)
            assertEquals(resumeBefore, ctrl.resumeCallCount) // Stays paused

            // When focus granted -> resumes
            fakeAudioFocus.requestResult = true
            vm.onEvent(PlayerEvent.TogglePlay)
            advanceUntilIdle()
            assertEquals(resumeBefore + 1, ctrl.resumeCallCount)
        }

    @Test
    fun PL29_onCleared_abandonsFocusOnce() =
        runTest(testDispatcher) {
            val url = "https://example.com/video.mp4"
            holder.start(PlaybackSelection("Movie", PlaybackSource.Direct(url), "movie", "m1", "v1"))
            val vm = createViewModel()
            advanceUntilIdle()

            val abandonBefore = fakeAudioFocus.abandonCount
            vm.onCleared()
            assertEquals(abandonBefore + 1, fakeAudioFocus.abandonCount)
        }

    @Test
    fun PL30_seekBy_seeksWhenSeekableAndPlaying_ignoredOtherwise() =
        runTest(testDispatcher) {
            val url = "https://example.com/video.mp4"
            holder.start(PlaybackSelection("Movie", PlaybackSource.Direct(url), "movie", "m1", "v1"))
            val vm = createViewModel()
            advanceUntilIdle()

            val ctrl = fakeFactory.createdControllers.single()
            // 1. In Preparing: ignored
            vm.onEvent(PlayerEvent.SeekBy(10_000L))
            advanceUntilIdle()
            assertTrue(ctrl.seekPositions.isEmpty())

            // 2. In Playing but not seekable: ignored
            ctrl.snapshotFlow.value =
                PlayerSnapshot(isPlaying = true, isSeekable = false, positionMs = 20_000L, durationMs = 100_000L)
            advanceUntilIdle()
            vm.onEvent(PlayerEvent.SeekBy(10_000L))
            advanceUntilIdle()
            assertTrue(ctrl.seekPositions.isEmpty())

            // 3. In Playing and seekable: seeks
            ctrl.snapshotFlow.value =
                PlayerSnapshot(isPlaying = true, isSeekable = true, positionMs = 20_000L, durationMs = 100_000L)
            advanceUntilIdle()
            vm.onEvent(PlayerEvent.SeekBy(10_000L))
            advanceUntilIdle()
            assertEquals(listOf(30_000L), ctrl.seekPositions)

            // 4. In Error: ignored
            ctrl.snapshotFlow.value = PlayerSnapshot(failed = true)
            advanceUntilIdle()
            val countBefore = ctrl.seekPositions.size
            vm.onEvent(PlayerEvent.SeekBy(10_000L))
            advanceUntilIdle()
            assertEquals(countBefore, ctrl.seekPositions.size)
        }

    @Test
    fun PL31_seekBy_clampsToZeroAndDuration() =
        runTest(testDispatcher) {
            val url = "https://example.com/video.mp4"
            holder.start(PlaybackSelection("Movie", PlaybackSource.Direct(url), "movie", "m1", "v1"))
            val vm = createViewModel()
            advanceUntilIdle()

            val ctrl = fakeFactory.createdControllers.single()
            ctrl.snapshotFlow.value =
                PlayerSnapshot(isPlaying = true, isSeekable = true, positionMs = 5_000L, durationMs = 100_000L)
            advanceUntilIdle()

            // Seek backward past 0 -> clamps to 0
            vm.onEvent(PlayerEvent.SeekBy(-10_000L))
            advanceUntilIdle()
            assertEquals(listOf(0L), ctrl.seekPositions)

            // Seek forward past duration -> clamps to duration
            ctrl.snapshotFlow.value =
                PlayerSnapshot(isPlaying = true, isSeekable = true, positionMs = 95_000L, durationMs = 100_000L)
            advanceUntilIdle()
            vm.onEvent(PlayerEvent.SeekBy(10_000L))
            advanceUntilIdle()
            assertEquals(listOf(0L, 100_000L), ctrl.seekPositions)
        }

    @Test
    fun PL32_privacyCheck_uiStateToStringContainsNoUrlHashOrHeader() =
        runTest(testDispatcher) {
            val secretUrl = "https://secret.stream.provider.com/playlist.m3u8?token=xyz123"
            val secretHeaderVal = "Bearer secret_jwt_token_987654"
            val secretHash = "4a5b6c7d8e9f0123456789abcdef0123456789ab"
            holder.start(
                PlaybackSelection(
                    title = "Secret Movie",
                    source = PlaybackSource.DirectWithHeaders(secretUrl, mapOf("Authorization" to secretHeaderVal)),
                    type = "movie",
                    metaId = "tt123",
                    videoId = "tt123",
                ),
            )
            val vm = createViewModel()
            advanceUntilIdle()

            val str = vm.uiState.value.toString()
            assertFalse(str.contains(secretUrl))
            assertFalse(str.contains(secretHeaderVal))
            assertFalse(str.contains(secretHash))
        }

    @Test
    fun PL33_holdSpeed_whilePlaying_startsAndReleases() =
        runTest(testDispatcher) {
            holder.start(
                PlaybackSelection("Movie", PlaybackSource.Direct("https://example.com/v.mp4"), "movie", "m1", "v1"),
            )
            val viewModel = createViewModel()
            advanceUntilIdle()
            val ctrl = fakeFactory.createdControllers.first()
            ctrl.snapshotFlow.value = PlayerSnapshot(isPlaying = true)
            advanceUntilIdle()

            viewModel.onEvent(PlayerEvent.HoldSpeed(true))
            advanceUntilIdle()
            assertEquals(listOf(2.0f), ctrl.rateCalls)
            assertTrue(viewModel.uiState.value.holdSpeed)

            viewModel.onEvent(PlayerEvent.HoldSpeed(false))
            advanceUntilIdle()
            assertEquals(listOf(2.0f, 1.0f), ctrl.rateCalls)
            assertFalse(viewModel.uiState.value.holdSpeed)
        }

    @Test
    fun PL34_holdSpeed_whileNotPlaying_ignored() =
        runTest(testDispatcher) {
            holder.start(
                PlaybackSelection("Movie", PlaybackSource.Direct("https://example.com/v.mp4"), "movie", "m1", "v1"),
            )
            val viewModel = createViewModel()
            advanceUntilIdle()
            val ctrl = fakeFactory.createdControllers.first()

            // 1. Preparing
            viewModel.onEvent(PlayerEvent.HoldSpeed(true))
            advanceUntilIdle()
            assertTrue(ctrl.rateCalls.isEmpty())
            assertFalse(viewModel.uiState.value.holdSpeed)

            // 2. Buffering before play (isPlaying = false, isBuffering = true)
            ctrl.snapshotFlow.value = PlayerSnapshot(isPlaying = false, isBuffering = true)
            advanceUntilIdle()
            viewModel.onEvent(PlayerEvent.HoldSpeed(true))
            advanceUntilIdle()
            assertTrue(ctrl.rateCalls.isEmpty())
            assertFalse(viewModel.uiState.value.holdSpeed)

            // 3. Paused (phase Playing, but isPlaying = false)
            ctrl.snapshotFlow.value = PlayerSnapshot(isPlaying = true)
            advanceUntilIdle()
            ctrl.snapshotFlow.value = PlayerSnapshot(isPlaying = false)
            advanceUntilIdle()
            viewModel.onEvent(PlayerEvent.HoldSpeed(true))
            advanceUntilIdle()
            assertTrue(ctrl.rateCalls.isEmpty())
            assertFalse(viewModel.uiState.value.holdSpeed)

            // 4. Ended
            ctrl.snapshotFlow.value = PlayerSnapshot(ended = true, isPlaying = false)
            advanceUntilIdle()
            viewModel.onEvent(PlayerEvent.HoldSpeed(true))
            advanceUntilIdle()
            assertTrue(ctrl.rateCalls.isEmpty())
            assertFalse(viewModel.uiState.value.holdSpeed)
        }

    @Test
    fun PL35_holdActive_thenPauseOrEndOrFailure_resetsRate() =
        runTest(testDispatcher) {
            holder.start(
                PlaybackSelection("Movie", PlaybackSource.Direct("https://example.com/v.mp4"), "movie", "m1", "v1"),
            )
            val viewModel = createViewModel()
            advanceUntilIdle()
            val ctrl = fakeFactory.createdControllers.first()
            ctrl.snapshotFlow.value = PlayerSnapshot(isPlaying = true)
            advanceUntilIdle()

            // Active hold
            viewModel.onEvent(PlayerEvent.HoldSpeed(true))
            advanceUntilIdle()
            assertEquals(listOf(2.0f), ctrl.rateCalls)
            assertTrue(viewModel.uiState.value.holdSpeed)

            // Pause via TogglePlay
            viewModel.onEvent(PlayerEvent.TogglePlay)
            advanceUntilIdle()
            assertEquals(listOf(2.0f, 1.0f), ctrl.rateCalls)
            assertFalse(viewModel.uiState.value.holdSpeed)

            // Resume, hold again, then snapshot ended
            ctrl.snapshotFlow.value = PlayerSnapshot(isPlaying = true)
            advanceUntilIdle()
            viewModel.onEvent(PlayerEvent.HoldSpeed(true))
            advanceUntilIdle()
            assertEquals(listOf(2.0f, 1.0f, 2.0f), ctrl.rateCalls)
            assertTrue(viewModel.uiState.value.holdSpeed)

            ctrl.snapshotFlow.value = PlayerSnapshot(ended = true, isPlaying = false)
            advanceUntilIdle()
            assertEquals(listOf(2.0f, 1.0f, 2.0f, 1.0f), ctrl.rateCalls)
            assertFalse(viewModel.uiState.value.holdSpeed)
        }

    @Test
    fun PL36_holdSpeed_quickCycles_finalRateOne() =
        runTest(testDispatcher) {
            holder.start(
                PlaybackSelection("Movie", PlaybackSource.Direct("https://example.com/v.mp4"), "movie", "m1", "v1"),
            )
            val viewModel = createViewModel()
            advanceUntilIdle()
            val ctrl = fakeFactory.createdControllers.first()
            ctrl.snapshotFlow.value = PlayerSnapshot(isPlaying = true)
            advanceUntilIdle()

            // Cycle 1
            viewModel.onEvent(PlayerEvent.HoldSpeed(true))
            viewModel.onEvent(PlayerEvent.HoldSpeed(false))
            // Cycle 2
            viewModel.onEvent(PlayerEvent.HoldSpeed(true))
            viewModel.onEvent(PlayerEvent.HoldSpeed(false))
            advanceUntilIdle()

            assertEquals(listOf(2.0f, 1.0f, 2.0f, 1.0f), ctrl.rateCalls)
            assertFalse(viewModel.uiState.value.holdSpeed)
        }

    @Test
    fun PL37_seekStepMs_followsRepository() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()
            assertEquals(10_000L, viewModel.uiState.value.seekStepMs)

            fakePrefs.seekStepSecondsFlow.value = 15
            advanceUntilIdle()
            assertEquals(15_000L, viewModel.uiState.value.seekStepMs)
        }

    @Test
    fun PL38_uiStateToString_containsNoSensitiveData() =
        runTest(testDispatcher) {
            val secretUrl = "https://token.stream.example.org/path/video.mp4"
            val secretHeaderVal = "Bearer SecretTokenXYZ"
            val secretHash = "4a5b6c7d8e9f00112233445566778899aabbccdd"
            holder.start(
                PlaybackSelection(
                    title = "Secret Movie",
                    source = PlaybackSource.DirectWithHeaders(secretUrl, mapOf("Authorization" to secretHeaderVal)),
                    type = "movie",
                    metaId = "m1",
                    videoId = secretHash,
                ),
            )
            val viewModel = createViewModel()
            advanceUntilIdle()
            val str = viewModel.uiState.value.toString()
            assertFalse(str.contains(secretUrl))
            assertFalse(str.contains(secretHeaderVal))
            assertFalse(str.contains(secretHash))
        }

    @Test
    fun PL40_select_audio_valid_forwards_to_controller() =
        runTest(testDispatcher) {
            val url = "https://example.com/video.mp4"
            holder.start(PlaybackSelection("Movie", PlaybackSource.Direct(url), "movie", "m1", "v1"))
            val vm = createViewModel()
            advanceUntilIdle()

            val ctrl = fakeFactory.createdControllers.single()
            ctrl.tracksFlow.value =
                PlayerTracks(
                    audio =
                        listOf(
                            PlayerTrack("1", "English"),
                            PlayerTrack("2", "French"),
                        ),
                )
            advanceUntilIdle()

            vm.onEvent(PlayerEvent.SelectAudio("2"))
            advanceUntilIdle()

            assertEquals(listOf("2"), ctrl.selectedAudioTracks)

            vm.onEvent(PlayerEvent.SelectAudio("99"))
            advanceUntilIdle()
            assertEquals(listOf("2"), ctrl.selectedAudioTracks)
        }

    @Test
    fun PL41_select_subtitle_off_calls_null() =
        runTest(testDispatcher) {
            val url = "https://example.com/video.mp4"
            holder.start(PlaybackSelection("Movie", PlaybackSource.Direct(url), "movie", "m1", "v1"))
            val vm = createViewModel()
            advanceUntilIdle()

            val ctrl = fakeFactory.createdControllers.single()
            ctrl.tracksFlow.value =
                PlayerTracks(
                    subtitles =
                        listOf(
                            PlayerTrack("1", "English"),
                            PlayerTrack("3", "Spanish"),
                        ),
                )
            advanceUntilIdle()

            vm.onEvent(PlayerEvent.SelectSubtitle(null))
            advanceUntilIdle()
            assertEquals(listOf<String?>(null), ctrl.selectedSubtitleTracks)

            vm.onEvent(PlayerEvent.SelectSubtitle("3"))
            advanceUntilIdle()
            assertEquals(listOf<String?>(null, "3"), ctrl.selectedSubtitleTracks)

            vm.onEvent(PlayerEvent.SelectSubtitle("99"))
            advanceUntilIdle()
            assertEquals(listOf<String?>(null, "3"), ctrl.selectedSubtitleTracks)
        }

    @Test
    fun PL42_set_speed_valid_updates_controller_and_state() =
        runTest(testDispatcher) {
            val url = "https://example.com/video.mp4"
            holder.start(PlaybackSelection("Movie", PlaybackSource.Direct(url), "movie", "m1", "v1"))
            val vm = createViewModel()
            advanceUntilIdle()

            val ctrl = fakeFactory.createdControllers.single()
            vm.onEvent(PlayerEvent.SetSpeed(1.5f))
            advanceUntilIdle()

            assertEquals(listOf(1.5f), ctrl.rateCalls)
            assertEquals(1.5f, vm.uiState.value.rate)
        }

    @Test
    fun PL43_set_speed_invalid_ignored() =
        runTest(testDispatcher) {
            val url = "https://example.com/video.mp4"
            holder.start(PlaybackSelection("Movie", PlaybackSource.Direct(url), "movie", "m1", "v1"))
            val vm = createViewModel()
            advanceUntilIdle()

            val ctrl = fakeFactory.createdControllers.single()
            vm.onEvent(PlayerEvent.SetSpeed(3.0f))
            advanceUntilIdle()

            assertTrue(ctrl.rateCalls.isEmpty())
            assertEquals(1.0f, vm.uiState.value.rate)
        }

    @Test
    fun PL44_hold_speed_restores_to_base_rate() =
        runTest(testDispatcher) {
            val url = "https://example.com/video.mp4"
            holder.start(PlaybackSelection("Movie", PlaybackSource.Direct(url), "movie", "m1", "v1"))
            val vm = createViewModel()
            advanceUntilIdle()

            val ctrl = fakeFactory.createdControllers.single()
            ctrl.snapshotFlow.value = PlayerSnapshot(isPlaying = true)
            advanceUntilIdle()

            vm.onEvent(PlayerEvent.SetSpeed(1.25f))
            advanceUntilIdle()
            assertEquals(listOf(1.25f), ctrl.rateCalls)

            vm.onEvent(PlayerEvent.HoldSpeed(true))
            advanceUntilIdle()
            assertTrue(vm.uiState.value.holdSpeed)
            assertEquals(listOf(1.25f, 2.0f), ctrl.rateCalls)

            vm.onEvent(PlayerEvent.HoldSpeed(false))
            advanceUntilIdle()
            assertFalse(vm.uiState.value.holdSpeed)
            assertEquals(listOf(1.25f, 2.0f, 1.25f), ctrl.rateCalls)
        }

    @Test
    fun PL45_hold_speed_noop_if_base_rate_ge_2() =
        runTest(testDispatcher) {
            val url = "https://example.com/video.mp4"
            holder.start(PlaybackSelection("Movie", PlaybackSource.Direct(url), "movie", "m1", "v1"))
            val vm = createViewModel()
            advanceUntilIdle()

            val ctrl = fakeFactory.createdControllers.single()
            ctrl.snapshotFlow.value = PlayerSnapshot(isPlaying = true)
            advanceUntilIdle()

            vm.onEvent(PlayerEvent.SetSpeed(2.0f))
            advanceUntilIdle()
            assertEquals(listOf(2.0f), ctrl.rateCalls)

            vm.onEvent(PlayerEvent.HoldSpeed(true))
            advanceUntilIdle()
            assertFalse(vm.uiState.value.holdSpeed)
            assertEquals(listOf(2.0f), ctrl.rateCalls)
            assertEquals(2.0f, vm.uiState.value.rate)
        }

    @Test
    fun PL46_cycle_scale_cycles_and_persists() =
        runTest(testDispatcher) {
            val url = "https://example.com/video.mp4"
            holder.start(PlaybackSelection("Movie", PlaybackSource.Direct(url), "movie", "m1", "v1"))
            val vm = createViewModel()
            advanceUntilIdle()

            val ctrl = fakeFactory.createdControllers.single()
            assertEquals(VideoScale.FIT, vm.uiState.value.scale)

            val expectedSequence =
                listOf(
                    VideoScale.CROP,
                    VideoScale.STRETCH,
                    VideoScale.RATIO_16_9,
                    VideoScale.RATIO_4_3,
                    VideoScale.ORIGINAL,
                    VideoScale.FIT,
                )

            for (expected in expectedSequence) {
                vm.onEvent(PlayerEvent.CycleScale)
                runCurrent()
                assertEquals(expected, vm.uiState.value.scale)
                assertEquals(expected, fakePrefs.videoScaleFlow.value)
                assertTrue(vm.uiState.value.scaleChipLabel != null)
            }

            assertEquals(expectedSequence, ctrl.videoScaleCalls)

            advanceTimeBy(1300L)
            runCurrent()
            assertNull(vm.uiState.value.scaleChipLabel)
        }

    @Test
    fun PL47_open_external_emits_effect_and_pauses() =
        runTest(testDispatcher) {
            val url = "https://example.com/video.mp4"
            holder.start(PlaybackSelection("Movie", PlaybackSource.Direct(url), "movie", "m1", "v1"))
            val vm = createViewModel()
            advanceUntilIdle()

            val ctrl = fakeFactory.createdControllers.single()
            ctrl.snapshotFlow.value = PlayerSnapshot(isPlaying = true)
            advanceUntilIdle()

            val effects = mutableListOf<PlayerEffect>()
            val job = launch { vm.effects.collect { effects.add(it) } }

            vm.onEvent(PlayerEvent.OpenExternal)
            advanceUntilIdle()

            assertEquals(1, ctrl.pauseCallCount)
            assertEquals(1, effects.size)
            val effect = effects.single() as PlayerEffect.OpenExternal
            assertEquals(url, effect.url)
            assertEquals("Movie", effect.title)

            job.cancel()
        }

    @Test
    fun PL48_initial_video_scale_applied_on_start() =
        runTest(testDispatcher) {
            fakePrefs.videoScaleFlow.value = VideoScale.CROP
            val url = "https://example.com/video.mp4"
            holder.start(PlaybackSelection("Movie", PlaybackSource.Direct(url), "movie", "m1", "v1"))
            val vm = createViewModel()
            advanceUntilIdle()

            val ctrl = fakeFactory.createdControllers.single()
            assertTrue(ctrl.videoScaleCalls.isEmpty())

            ctrl.snapshotFlow.value = PlayerSnapshot(isPlaying = true)
            advanceUntilIdle()

            assertEquals(listOf(VideoScale.CROP), ctrl.videoScaleCalls)
            assertEquals(VideoScale.CROP, vm.uiState.value.scale)
        }

    @Test
    fun PL50_drawer_switching_closing_and_controls_visibility() =
        runTest(testDispatcher) {
            val url = "https://example.com/video.mp4"
            holder.start(PlaybackSelection("Movie", PlaybackSource.Direct(url), "movie", "m1", "v1"))
            val vm = createViewModel()
            advanceUntilIdle()

            val ctrl = fakeFactory.createdControllers.single()
            ctrl.snapshotFlow.value = PlayerSnapshot(isPlaying = true, isBuffering = false)
            runCurrent()
            assertTrue(vm.uiState.value.controlsVisible)

            // OpenDrawer(AUDIO) sets drawer
            vm.onEvent(PlayerEvent.OpenDrawer(PlayerDrawer.AUDIO))
            runCurrent()
            assertEquals(PlayerDrawer.AUDIO, vm.uiState.value.drawer)
            assertTrue(vm.uiState.value.controlsVisible)

            // Controls do not hide while drawer is open
            advanceTimeBy(3500L)
            runCurrent()
            assertTrue(vm.uiState.value.controlsVisible)

            // OpenDrawer(SUBTITLES) replaces it
            vm.onEvent(PlayerEvent.OpenDrawer(PlayerDrawer.SUBTITLES))
            runCurrent()
            assertEquals(PlayerDrawer.SUBTITLES, vm.uiState.value.drawer)

            // OpenDrawer(SUBTITLES) again closes it
            vm.onEvent(PlayerEvent.OpenDrawer(PlayerDrawer.SUBTITLES))
            runCurrent()
            assertNull(vm.uiState.value.drawer)
            assertTrue(vm.uiState.value.controlsVisible)

            // Timer restarts on close: controls hide after hide delay
            advanceTimeBy(3500L)
            runCurrent()
            assertFalse(vm.uiState.value.controlsVisible)

            // Re-open and test CloseDrawer
            vm.onEvent(PlayerEvent.OpenDrawer(PlayerDrawer.SPEED))
            runCurrent()
            assertEquals(PlayerDrawer.SPEED, vm.uiState.value.drawer)
            assertTrue(vm.uiState.value.controlsVisible)

            vm.onEvent(PlayerEvent.CloseDrawer)
            runCurrent()
            assertNull(vm.uiState.value.drawer)
            assertTrue(vm.uiState.value.controlsVisible)
            advanceTimeBy(3500L)
            runCurrent()
            assertFalse(vm.uiState.value.controlsVisible)
        }

    @Test
    fun PL51_audio_selection_valid_forwards_unknown_ignored() =
        runTest(testDispatcher) {
            val url = "https://example.com/video.mp4"
            holder.start(PlaybackSelection("Movie", PlaybackSource.Direct(url), "movie", "m1", "v1"))
            val vm = createViewModel()
            advanceUntilIdle()

            val ctrl = fakeFactory.createdControllers.single()
            ctrl.tracksFlow.value =
                PlayerTracks(
                    audio = listOf(PlayerTrack("1", "English"), PlayerTrack("2", "Spanish")),
                )
            runCurrent()

            vm.onEvent(PlayerEvent.OpenDrawer(PlayerDrawer.AUDIO))
            runCurrent()

            vm.onEvent(PlayerEvent.SelectAudio("2"))
            runCurrent()
            assertEquals(listOf("2"), ctrl.selectedAudioTracks)

            vm.onEvent(PlayerEvent.SelectAudio("999")) // unknown id
            runCurrent()
            assertEquals(listOf("2"), ctrl.selectedAudioTracks)
        }

    @Test
    fun PL52_speed_selection_listed_values_only() =
        runTest(testDispatcher) {
            val url = "https://example.com/video.mp4"
            holder.start(PlaybackSelection("Movie", PlaybackSource.Direct(url), "movie", "m1", "v1"))
            val vm = createViewModel()
            advanceUntilIdle()

            val ctrl = fakeFactory.createdControllers.single()
            vm.onEvent(PlayerEvent.OpenDrawer(PlayerDrawer.SPEED))
            runCurrent()

            vm.onEvent(PlayerEvent.SetSpeed(1.25f))
            runCurrent()
            assertEquals(1.25f, vm.uiState.value.rate)
            assertEquals(listOf(1.25f), ctrl.rateCalls)

            // Invalid speed not in SPEED_OPTIONS
            vm.onEvent(PlayerEvent.SetSpeed(3.0f))
            runCurrent()
            assertEquals(1.25f, vm.uiState.value.rate)
            assertEquals(listOf(1.25f), ctrl.rateCalls)
        }

    @Test
    fun PL53_subtitles_drawer_loads_once_on_first_open_refresh_reloads() =
        runTest(testDispatcher) {
            val url = "https://example.com/video.mp4"
            holder.start(PlaybackSelection("Movie", PlaybackSource.Direct(url), "movie", "m1", "v1"))

            coEvery { loadOnlineSubtitlesUseCase(any(), any(), any()) } returns
                listOf(
                    com.nexus.stream.domain.model.OnlineSubtitle(
                        Subtitle("s1", "https://sub.org/en.srt", "en", "English"),
                        "OpenSubtitles",
                    ),
                )

            val vm = createViewModel()
            advanceUntilIdle()

            assertEquals(OnlineSubtitlesState.Idle, vm.uiState.value.onlineSubtitles)

            // Open subtitles drawer first time -> loads
            vm.onEvent(PlayerEvent.OpenDrawer(PlayerDrawer.SUBTITLES))
            runCurrent()
            assertTrue(vm.uiState.value.onlineSubtitles is OnlineSubtitlesState.Loaded)
            coVerify(exactly = 1) { loadOnlineSubtitlesUseCase("movie", "v1", null) }

            // Close and reopen -> does NOT reload
            vm.onEvent(PlayerEvent.CloseDrawer)
            runCurrent()
            vm.onEvent(PlayerEvent.OpenDrawer(PlayerDrawer.SUBTITLES))
            runCurrent()
            coVerify(exactly = 1) { loadOnlineSubtitlesUseCase("movie", "v1", null) }

            // Refresh -> reloads
            vm.onEvent(PlayerEvent.RefreshOnlineSubtitles)
            runCurrent()
            coVerify(exactly = 2) { loadOnlineSubtitlesUseCase("movie", "v1", null) }
        }

    @Test
    fun PL54_select_online_subtitle_calls_add_and_clears_on_growth() =
        runTest(testDispatcher) {
            val url = "https://example.com/video.mp4"
            holder.start(PlaybackSelection("Movie", PlaybackSource.Direct(url), "movie", "m1", "v1"))

            coEvery { loadOnlineSubtitlesUseCase(any(), any(), any()) } returns
                listOf(
                    com.nexus.stream.domain.model.OnlineSubtitle(
                        Subtitle("s1", "https://sub.org/test.srt", "en", "English"),
                        "AddonX",
                    ),
                )

            val vm = createViewModel()
            advanceUntilIdle()

            val ctrl = fakeFactory.createdControllers.single()
            ctrl.tracksFlow.value = PlayerTracks(subtitles = emptyList())

            vm.onEvent(PlayerEvent.OpenDrawer(PlayerDrawer.SUBTITLES))
            runCurrent()

            val loadedState = vm.uiState.value.onlineSubtitles as OnlineSubtitlesState.Loaded
            val itemKey =
                loadedState.groups
                    .single()
                    .items
                    .single()
                    .key

            vm.onEvent(PlayerEvent.SelectOnlineSubtitle(itemKey))
            runCurrent()

            assertEquals(listOf("https://sub.org/test.srt"), ctrl.addedSubtitles)
            assertEquals(itemKey, vm.uiState.value.applyingSubtitleKey)

            // Simulate controller's subtitle tracks growing by one
            ctrl.tracksFlow.value = PlayerTracks(subtitles = listOf(PlayerTrack("100", "test.srt")))
            runCurrent()

            assertNull(vm.uiState.value.applyingSubtitleKey)
            assertNull(vm.uiState.value.subtitleError)
        }

    @Test
    fun PL55_select_online_subtitle_timeout_after_6s_sets_error() =
        runTest(testDispatcher) {
            val url = "https://example.com/video.mp4"
            holder.start(PlaybackSelection("Movie", PlaybackSource.Direct(url), "movie", "m1", "v1"))

            coEvery { loadOnlineSubtitlesUseCase(any(), any(), any()) } returns
                listOf(
                    com.nexus.stream.domain.model.OnlineSubtitle(
                        Subtitle("s1", "https://sub.org/test.srt", "en", "English"),
                        "AddonX",
                    ),
                )

            val vm = createViewModel()
            advanceUntilIdle()

            val ctrl = fakeFactory.createdControllers.single()
            ctrl.tracksFlow.value = PlayerTracks(subtitles = emptyList())

            vm.onEvent(PlayerEvent.OpenDrawer(PlayerDrawer.SUBTITLES))
            runCurrent()

            val loadedState = vm.uiState.value.onlineSubtitles as OnlineSubtitlesState.Loaded
            val itemKey =
                loadedState.groups
                    .single()
                    .items
                    .single()
                    .key

            vm.onEvent(PlayerEvent.SelectOnlineSubtitle(itemKey))
            runCurrent()

            assertEquals(itemKey, vm.uiState.value.applyingSubtitleKey)
            assertNull(vm.uiState.value.subtitleError)

            // Advance 6000ms without track growth
            advanceTimeBy(6001L)
            runCurrent()

            assertNull(vm.uiState.value.applyingSubtitleKey)
            assertEquals(SubtitleError.SUBTITLE_LOAD_FAILED, vm.uiState.value.subtitleError)
        }

    @Test
    fun PL56_subtitle_file_picked_imported_adds_slave_errors_set_state() =
        runTest(testDispatcher) {
            val url = "https://example.com/video.mp4"
            holder.start(PlaybackSelection("Movie", PlaybackSource.Direct(url), "movie", "m1", "v1"))
            val vm = createViewModel()
            advanceUntilIdle()

            val ctrl = fakeFactory.createdControllers.single()

            // 1. Success
            fakeSubtitleImporter.importResult = ImportResult.Imported("/cache/subtitles/123.srt")
            vm.onEvent(PlayerEvent.SubtitleFilePicked("content://sub.srt"))
            runCurrent()

            assertEquals(listOf("file:///cache/subtitles/123.srt"), ctrl.addedSubtitles)
            assertNull(vm.uiState.value.subtitleError)

            // 2. BadType
            fakeSubtitleImporter.importResult = ImportResult.BadType
            vm.onEvent(PlayerEvent.SubtitleFilePicked("content://bad.txt"))
            runCurrent()
            assertEquals(1, ctrl.addedSubtitles.size)
            assertEquals(SubtitleError.BAD_TYPE, vm.uiState.value.subtitleError)

            // 3. TooLarge
            fakeSubtitleImporter.importResult = ImportResult.TooLarge
            vm.onEvent(PlayerEvent.SubtitleFilePicked("content://huge.srt"))
            runCurrent()
            assertEquals(1, ctrl.addedSubtitles.size)
            assertEquals(SubtitleError.TOO_LARGE, vm.uiState.value.subtitleError)

            // 4. Failed
            fakeSubtitleImporter.importResult = ImportResult.Failed
            vm.onEvent(PlayerEvent.SubtitleFilePicked("content://error.srt"))
            runCurrent()
            assertEquals(1, ctrl.addedSubtitles.size)
            assertEquals(SubtitleError.FAILED, vm.uiState.value.subtitleError)
        }

    @Test
    fun PL57_subtitle_delay_clamped_reset_and_reapplied_on_add() =
        runTest(testDispatcher) {
            val url = "https://example.com/video.mp4"
            holder.start(PlaybackSelection("Movie", PlaybackSource.Direct(url), "movie", "m1", "v1"))
            val vm = createViewModel()
            advanceUntilIdle()

            val ctrl = fakeFactory.createdControllers.single()
            assertEquals(0L, vm.uiState.value.subtitleDelayMs)

            // +100 ms
            vm.onEvent(PlayerEvent.SubtitleDelay(100L))
            runCurrent()
            assertEquals(100L, vm.uiState.value.subtitleDelayMs)
            assertEquals(listOf(100L), ctrl.subtitleDelayCalls)

            // Clamp check: jump by +40_000 -> clamps at 30_000
            vm.onEvent(PlayerEvent.SubtitleDelay(40_000L))
            runCurrent()
            assertEquals(30_000L, vm.uiState.value.subtitleDelayMs)
            assertEquals(30_000L, ctrl.subtitleDelayCalls.last())

            // -40_000 ms -> clamped to -10_000
            vm.onEvent(PlayerEvent.SubtitleDelay(-40_000L))
            runCurrent()
            assertEquals(-10_000L, vm.uiState.value.subtitleDelayMs)
            assertEquals(-10_000L, ctrl.subtitleDelayCalls.last())

            // Reset
            vm.onEvent(PlayerEvent.ResetSubtitleDelay)
            runCurrent()
            assertEquals(0L, vm.uiState.value.subtitleDelayMs)
            assertEquals(0L, ctrl.subtitleDelayCalls.last())

            // Set delay to 2500ms
            vm.onEvent(PlayerEvent.SubtitleDelay(2500L))
            runCurrent()
            assertEquals(2500L, vm.uiState.value.subtitleDelayMs)

            // Newly added subtitle re-applies current delay (2500L)
            fakeSubtitleImporter.importResult = ImportResult.Imported("/cache/subtitles/sub.srt")
            vm.onEvent(PlayerEvent.SubtitleFilePicked("content://sub.srt"))
            runCurrent()
            assertEquals(listOf("file:///cache/subtitles/sub.srt"), ctrl.addedSubtitles)
            assertEquals(2500L, ctrl.subtitleDelayCalls.last())
        }

    @Test
    fun PL58_uiState_toString_contains_no_urls_hashes_or_headers() =
        runTest(testDispatcher) {
            val url = "https://sensitive.org/stream?token=secret123"
            val hints = SubtitleHints("movie.mkv", "secretHash999", 12345L)
            holder.start(
                PlaybackSelection(
                    title = "Movie",
                    source = PlaybackSource.Direct(url),
                    type = "movie",
                    metaId = "m1",
                    videoId = "v1",
                    subtitleHints = hints,
                ),
            )

            coEvery { loadOnlineSubtitlesUseCase(any(), any(), any()) } returns
                listOf(
                    com.nexus.stream.domain.model.OnlineSubtitle(
                        Subtitle("s1", "https://private-sub.org/secret.srt?auth=abc", "en", "English"),
                        "SecretAddon",
                    ),
                )

            val vm = createViewModel()
            advanceUntilIdle()

            vm.onEvent(PlayerEvent.OpenDrawer(PlayerDrawer.SUBTITLES))
            runCurrent()

            val stateString = vm.uiState.value.toString()
            assertFalse(stateString.contains("sensitive.org"))
            assertFalse(stateString.contains("secret123"))
            assertFalse(stateString.contains("secretHash999"))
            assertFalse(stateString.contains("private-sub.org"))
            assertFalse(stateString.contains("auth=abc"))
        }

    @Test
    fun PL59_onCleared_calls_importer_cleanup() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            advanceUntilIdle()

            assertEquals(0, fakeSubtitleImporter.cleanupCallCount)
            vm.onCleared()
            assertEquals(1, fakeSubtitleImporter.cleanupCallCount)
        }

    @Test
    fun PL60_failing_loader_leaves_error_refresh_can_succeed() =
        runTest(testDispatcher) {
            val url = "https://example.com/video.mp4"
            holder.start(PlaybackSelection("Movie", PlaybackSource.Direct(url), "movie", "m1", "v1"))

            coEvery { loadOnlineSubtitlesUseCase(any(), any(), any()) } throws RuntimeException("Network error")

            val vm = createViewModel()
            advanceUntilIdle()

            vm.onEvent(PlayerEvent.OpenDrawer(PlayerDrawer.SUBTITLES))
            runCurrent()

            assertEquals(OnlineSubtitlesState.Error, vm.uiState.value.onlineSubtitles)

            // Now configure loader to succeed
            coEvery { loadOnlineSubtitlesUseCase(any(), any(), any()) } returns
                listOf(
                    com.nexus.stream.domain.model.OnlineSubtitle(
                        Subtitle("s1", "https://sub.org/en.srt", "en", "English"),
                        "Addon",
                    ),
                )

            vm.onEvent(PlayerEvent.RefreshOnlineSubtitles)
            runCurrent()

            assertTrue(vm.uiState.value.onlineSubtitles is OnlineSubtitlesState.Loaded)
        }

    @Test
    fun PL61_factoryReceivesRenderStyleResolvedFromStoredStyle() =
        runTest(testDispatcher) {
            val customStyle =
                SubtitleStyle(
                    size = SubtitleSize.BIG,
                    colorId = "yellow",
                    background = SubtitleBackground.DIM,
                    edge = SubtitleEdge.SHADOW,
                    bold = true,
                )
            fakePrefs.subtitleStyleFlow.value = customStyle

            holder.start(
                PlaybackSelection("Movie", PlaybackSource.Direct("https://example.com/v.mp4"), "movie", "m1", "v1"),
            )
            createViewModel()
            advanceUntilIdle()

            val expected = customStyle.toRenderStyle(SubtitlePalette.rgbFor("yellow"))
            assertEquals(expected, fakeFactory.lastRenderStyle)
            assertEquals(13, fakeFactory.lastRenderStyle?.relFontSize)
            assertEquals(0xFFFF00, fakeFactory.lastRenderStyle?.rgb)
            assertEquals(true, fakeFactory.lastRenderStyle?.bold)
            assertEquals(128, fakeFactory.lastRenderStyle?.backgroundOpacity)
            assertEquals(0, fakeFactory.lastRenderStyle?.outlineOpacity)
            assertEquals(128, fakeFactory.lastRenderStyle?.shadowOpacity)
        }

    @Test
    fun PL62_preferredAudioMatchesTrack_selectsOnce_andAlreadySelectedNoCall() =
        runTest(testDispatcher) {
            fakePrefs.preferredAudioLanguageFlow.value = "es"

            holder.start(
                PlaybackSelection("Movie", PlaybackSource.Direct("https://example.com/v.mp4"), "movie", "m1", "v1"),
            )
            createViewModel()
            advanceUntilIdle()
            val ctrl = fakeFactory.createdControllers.single()

            // 1. Snapshot playing arrived
            ctrl.snapshotFlow.value = PlayerSnapshot(isPlaying = true)
            runCurrent()

            // 2. Tracks arrive with English selected
            ctrl.tracksFlow.value =
                PlayerTracks(
                    audio = listOf(PlayerTrack("1", "Track 1 - [English]"), PlayerTrack("2", "Track 2 - [Spanish]")),
                    selectedAudioId = "1",
                )
            runCurrent()

            assertEquals(listOf("2"), ctrl.selectedAudioTracks)

            // 3. Subsequent emission does not select again
            ctrl.tracksFlow.value =
                PlayerTracks(
                    audio = listOf(PlayerTrack("1", "Track 1 - [English]"), PlayerTrack("2", "Track 2 - [Spanish]")),
                    selectedAudioId = "2",
                )
            runCurrent()
            assertEquals(listOf("2"), ctrl.selectedAudioTracks)

            // 4. Test already selected: fresh start where Spanish is already selected
            fakePrefs.preferredAudioLanguageFlow.value = "es"
            fakeFactory.createdControllers.clear()
            createViewModel()
            advanceUntilIdle()
            val ctrl2 = fakeFactory.createdControllers.single()
            ctrl2.snapshotFlow.value = PlayerSnapshot(isPlaying = true)
            runCurrent()
            ctrl2.tracksFlow.value =
                PlayerTracks(
                    audio = listOf(PlayerTrack("1", "Track 1 - [English]"), PlayerTrack("2", "Track 2 - [Spanish]")),
                    selectedAudioId = "2",
                )
            runCurrent()
            assertTrue(ctrl2.selectedAudioTracks.isEmpty())
        }

    @Test
    fun PL63_noPreferredAudio_noAutomaticCall() =
        runTest(testDispatcher) {
            fakePrefs.preferredAudioLanguageFlow.value = null

            holder.start(
                PlaybackSelection("Movie", PlaybackSource.Direct("https://example.com/v.mp4"), "movie", "m1", "v1"),
            )
            createViewModel()
            advanceUntilIdle()
            val ctrl = fakeFactory.createdControllers.single()

            ctrl.snapshotFlow.value = PlayerSnapshot(isPlaying = true)
            runCurrent()

            ctrl.tracksFlow.value =
                PlayerTracks(
                    audio = listOf(PlayerTrack("1", "Track 1 - [English]"), PlayerTrack("2", "Track 2 - [Spanish]")),
                    selectedAudioId = "1",
                )
            runCurrent()

            assertTrue(ctrl.selectedAudioTracks.isEmpty())
        }

    @Test
    fun PL64_subtitlePreferenceOffOrCode_selectsExpectedTrack() =
        runTest(testDispatcher) {
            // Case A: preference "off" with a selected subtitle -> selects null
            fakePrefs.preferredSubtitleLanguageFlow.value = SUBTITLES_OFF
            holder.start(
                PlaybackSelection("Movie", PlaybackSource.Direct("https://example.com/v.mp4"), "movie", "m1", "v1"),
            )
            createViewModel()
            advanceUntilIdle()
            val ctrl1 = fakeFactory.createdControllers.single()
            ctrl1.snapshotFlow.value = PlayerSnapshot(isPlaying = true)
            runCurrent()
            ctrl1.tracksFlow.value =
                PlayerTracks(
                    subtitles = listOf(PlayerTrack("s1", "English")),
                    selectedSubtitleId = "s1",
                )
            runCurrent()
            assertEquals(listOf<String?>(null), ctrl1.selectedSubtitleTracks)

            // Case B: "fr" with a French track -> selected
            fakePrefs.preferredSubtitleLanguageFlow.value = "fr"
            fakeFactory.createdControllers.clear()
            createViewModel()
            advanceUntilIdle()
            val ctrl2 = fakeFactory.createdControllers.single()
            ctrl2.snapshotFlow.value = PlayerSnapshot(isPlaying = true)
            runCurrent()
            ctrl2.tracksFlow.value =
                PlayerTracks(
                    subtitles = listOf(PlayerTrack("s1", "English"), PlayerTrack("s2", "French")),
                    selectedSubtitleId = "s1",
                )
            runCurrent()
            assertEquals(listOf("s2"), ctrl2.selectedSubtitleTracks)

            // Case C: "fr" without French track -> no call
            fakePrefs.preferredSubtitleLanguageFlow.value = "fr"
            fakeFactory.createdControllers.clear()
            createViewModel()
            advanceUntilIdle()
            val ctrl3 = fakeFactory.createdControllers.single()
            ctrl3.snapshotFlow.value = PlayerSnapshot(isPlaying = true)
            runCurrent()
            ctrl3.tracksFlow.value =
                PlayerTracks(
                    subtitles = listOf(PlayerTrack("s1", "English"), PlayerTrack("s2", "German")),
                    selectedSubtitleId = "s1",
                )
            runCurrent()
            assertTrue(ctrl3.selectedSubtitleTracks.isEmpty())
        }

    @Test
    fun PL65_userSelectsAudioFirst_orAfterAuto_noFurtherSelection() =
        runTest(testDispatcher) {
            // User selects audio first
            fakePrefs.preferredAudioLanguageFlow.value = "es"
            holder.start(
                PlaybackSelection("Movie", PlaybackSource.Direct("https://example.com/v.mp4"), "movie", "m1", "v1"),
            )
            val vm = createViewModel()
            advanceUntilIdle()
            val ctrl = fakeFactory.createdControllers.single()

            ctrl.tracksFlow.value =
                PlayerTracks(
                    audio = listOf(PlayerTrack("1", "Track 1 - [English]"), PlayerTrack("2", "Track 2 - [Spanish]")),
                    selectedAudioId = "1",
                )
            runCurrent()

            // User manually chooses track 1
            vm.onEvent(PlayerEvent.SelectAudio("1"))
            runCurrent()
            assertEquals(listOf("1"), ctrl.selectedAudioTracks)

            // First playing snapshot arrives
            ctrl.snapshotFlow.value = PlayerSnapshot(isPlaying = true)
            runCurrent()

            // Another track emission arrives -> no automatic selection override
            ctrl.tracksFlow.value =
                PlayerTracks(
                    audio = listOf(PlayerTrack("1", "Track 1 - [English]"), PlayerTrack("2", "Track 2 - [Spanish]")),
                    selectedAudioId = "1",
                )
            runCurrent()
            assertEquals(listOf("1"), ctrl.selectedAudioTracks)
        }

    @Test
    fun PL66_subtitleTracksArrivalTiming_matchesAt10s_notAt16s() =
        runTest(testDispatcher) {
            fakePrefs.preferredSubtitleLanguageFlow.value = "fr"

            // Subtitle tracks arriving at 10s: matched
            holder.start(
                PlaybackSelection("Movie", PlaybackSource.Direct("https://example.com/v.mp4"), "movie", "m1", "v1"),
            )
            createViewModel()
            advanceUntilIdle()
            val ctrl1 = fakeFactory.createdControllers.single()
            ctrl1.snapshotFlow.value = PlayerSnapshot(isPlaying = true)
            runCurrent()

            advanceTimeBy(10_000L)
            ctrl1.tracksFlow.value =
                PlayerTracks(
                    subtitles = listOf(PlayerTrack("s1", "English"), PlayerTrack("s2", "French")),
                    selectedSubtitleId = "s1",
                )
            runCurrent()
            assertEquals(listOf("s2"), ctrl1.selectedSubtitleTracks)

            // Subtitle tracks arriving at 16s: NOT matched
            fakeFactory.createdControllers.clear()
            createViewModel()
            advanceUntilIdle()
            val ctrl2 = fakeFactory.createdControllers.single()
            ctrl2.snapshotFlow.value = PlayerSnapshot(isPlaying = true)
            runCurrent()

            advanceTimeBy(16_000L)
            runCurrent()
            ctrl2.tracksFlow.value =
                PlayerTracks(
                    subtitles = listOf(PlayerTrack("s1", "English"), PlayerTrack("s2", "French")),
                    selectedSubtitleId = "s1",
                )
            runCurrent()
            assertTrue(ctrl2.selectedSubtitleTracks.isEmpty())
        }

    @Test
    fun PL67_onlineSubtitlesGroupedWithPreferredLanguageFirst() =
        runTest(testDispatcher) {
            fakePrefs.preferredSubtitleLanguageFlow.value = "es"
            holder.start(
                PlaybackSelection("Movie", PlaybackSource.Direct("https://example.com/v.mp4"), "movie", "m1", "v1"),
            )

            coEvery { loadOnlineSubtitlesUseCase(any(), any(), any()) } returns
                listOf(
                    com.nexus.stream.domain.model.OnlineSubtitle(
                        Subtitle("s1", "https://sub.org/en.srt", "en", "English"),
                        "Addon",
                    ),
                    com.nexus.stream.domain.model.OnlineSubtitle(
                        Subtitle("s2", "https://sub.org/es.srt", "es", "Spanish"),
                        "Addon",
                    ),
                    com.nexus.stream.domain.model.OnlineSubtitle(
                        Subtitle("s3", "https://sub.org/fr.srt", "fr", "French"),
                        "Addon",
                    ),
                )

            val vm = createViewModel()
            advanceUntilIdle()

            vm.onEvent(PlayerEvent.OpenDrawer(PlayerDrawer.SUBTITLES))
            advanceUntilIdle()

            val state = vm.uiState.value.onlineSubtitles
            assertTrue(state is OnlineSubtitlesState.Loaded)
            val groups = (state as OnlineSubtitlesState.Loaded).groups
            assertEquals(3, groups.size)
            // Preferred language "es" (Spanish) is sorted first
            assertEquals("Spanish", groups.first().language)
        }

    @Test
    fun PL68_uiStateToString_containsNoUrlHashOrHeader() =
        runTest(testDispatcher) {
            val secretUrl = "https://private.stream.com/secret/path/video.m3u8?token=12345"
            val secretHeaderVal = "Bearer super_secret_jwt_token_value"
            val secretHash = "40charinfohash0123456789abcdef012345678"

            holder.start(
                PlaybackSelection(
                    title = "Secret Movie",
                    source = PlaybackSource.DirectWithHeaders(secretUrl, mapOf("Authorization" to secretHeaderVal)),
                    type = "movie",
                    metaId = "tt123",
                    videoId = "tt123",
                ),
            )
            fakePrefs.preferredAudioLanguageFlow.value = "en"
            fakePrefs.preferredSubtitleLanguageFlow.value = "es"

            val vm = createViewModel()
            advanceUntilIdle()

            val str = vm.uiState.value.toString()
            assertFalse(str.contains(secretUrl))
            assertFalse(str.contains(secretHeaderVal))
            assertFalse(str.contains(secretHash))
            assertFalse(str.contains("secret"))
        }

    @Test
    fun PL69_selectionWithMetaIdAndVideoId_playingSnapshots_saveProgressIsCalled() =
        runTest(testDispatcher) {
            holder.start(
                PlaybackSelection("Movie", PlaybackSource.Direct("https://example.com/v.mp4"), "movie", "m1", "v1"),
            )
            val vm = createViewModel()
            advanceUntilIdle()

            val ctrl = fakeFactory.createdControllers.single()
            ctrl.snapshotFlow.value =
                PlayerSnapshot(isPlaying = true, isSeekable = true, durationMs = 100_000L, positionMs = 10_000L)
            advanceUntilIdle()
            ctrl.snapshotFlow.value =
                PlayerSnapshot(isPlaying = false, isSeekable = true, durationMs = 100_000L, positionMs = 10_000L)
            advanceUntilIdle()

            coVerify(atLeast = 1) {
                fakeSaveProgress.invoke(match { it.metaId == "m1" && it.videoId == "m1" }, any(), any(), any())
            }
            vm.onCleared()
        }

    @Test
    fun PL70_selectionWithoutMetaIdOrVideoId_noProgressRead_noWrite() =
        runTest(testDispatcher) {
            holder.start(
                PlaybackSelection("Movie", PlaybackSource.Direct("https://example.com/v.mp4"), "movie", "", ""),
            )
            createViewModel()
            advanceUntilIdle()

            val ctrl = fakeFactory.createdControllers.single()
            ctrl.snapshotFlow.value =
                PlayerSnapshot(isPlaying = true, isSeekable = true, durationMs = 100_000L, positionMs = 10_000L)
            advanceTimeBy(15_000L)
            advanceUntilIdle()

            coVerify(exactly = 0) { fakeProgressRepository.get(any()) }
            coVerify(exactly = 0) { fakeSaveProgress.invoke(any(), any(), any(), any()) }
        }

    @Test
    fun PL71_savedRowUnwatched_firstSnapshotPlayingSeekable_controllerSeekToPositionOnce() =
        runTest(testDispatcher) {
            val savedRow =
                WatchProgress(
                    key = "m1|m1",
                    metaId = "m1",
                    videoId = "m1",
                    type = "movie",
                    season = null,
                    episode = null,
                    positionMs = 720_000L,
                    durationMs = 7_200_000L,
                    watched = false,
                    updatedAt = 1_000L,
                )
            coEvery { fakeProgressRepository.get("m1|m1") } returns savedRow

            holder.start(
                PlaybackSelection("Movie", PlaybackSource.Direct("https://example.com/v.mp4"), "movie", "m1", "v1"),
            )
            createViewModel()
            advanceUntilIdle()

            val ctrl = fakeFactory.createdControllers.single()
            ctrl.snapshotFlow.value =
                PlayerSnapshot(isPlaying = true, isSeekable = true, durationMs = 7_200_000L, positionMs = 1_000L)
            advanceUntilIdle()

            assertEquals(listOf(720_000L), ctrl.seekPositions)
        }

    @Test
    fun PL72_noSavedRow_noSeek() =
        runTest(testDispatcher) {
            coEvery { fakeProgressRepository.get(any()) } returns null

            holder.start(
                PlaybackSelection("Movie", PlaybackSource.Direct("https://example.com/v.mp4"), "movie", "m1", "v1"),
            )
            createViewModel()
            advanceUntilIdle()

            val ctrl = fakeFactory.createdControllers.single()
            ctrl.snapshotFlow.value =
                PlayerSnapshot(isPlaying = true, isSeekable = true, durationMs = 7_200_000L, positionMs = 1_000L)
            advanceUntilIdle()

            assertTrue(ctrl.seekPositions.isEmpty())
        }

    @Test
    fun PL73_watchedRow_noSeek() =
        runTest(testDispatcher) {
            val watchedRow =
                WatchProgress(
                    key = "m1|m1",
                    metaId = "m1",
                    videoId = "m1",
                    type = "movie",
                    season = null,
                    episode = null,
                    positionMs = 720_000L,
                    durationMs = 7_200_000L,
                    watched = true,
                    updatedAt = 1_000L,
                )
            coEvery { fakeProgressRepository.get("m1|m1") } returns watchedRow

            holder.start(
                PlaybackSelection("Movie", PlaybackSource.Direct("https://example.com/v.mp4"), "movie", "m1", "v1"),
            )
            createViewModel()
            advanceUntilIdle()

            val ctrl = fakeFactory.createdControllers.single()
            ctrl.snapshotFlow.value =
                PlayerSnapshot(isPlaying = true, isSeekable = true, durationMs = 7_200_000L, positionMs = 1_000L)
            advanceUntilIdle()

            assertTrue(ctrl.seekPositions.isEmpty())
        }

    @Test
    fun PL74_savedPosition5s_noSeek() =
        runTest(testDispatcher) {
            val row5s =
                WatchProgress(
                    key = "m1|m1",
                    metaId = "m1",
                    videoId = "m1",
                    type = "movie",
                    season = null,
                    episode = null,
                    positionMs = 5_000L,
                    durationMs = 7_200_000L,
                    watched = false,
                    updatedAt = 1_000L,
                )
            coEvery { fakeProgressRepository.get("m1|m1") } returns row5s

            holder.start(
                PlaybackSelection("Movie", PlaybackSource.Direct("https://example.com/v.mp4"), "movie", "m1", "v1"),
            )
            createViewModel()
            advanceUntilIdle()

            val ctrl = fakeFactory.createdControllers.single()
            ctrl.snapshotFlow.value =
                PlayerSnapshot(isPlaying = true, isSeekable = true, durationMs = 7_200_000L, positionMs = 1_000L)
            advanceUntilIdle()

            assertTrue(ctrl.seekPositions.isEmpty())
        }

    @Test
    fun PL75_savedPositionWithin10sOfDuration_noSeek() =
        runTest(testDispatcher) {
            val rowNearEnd =
                WatchProgress(
                    key = "m1|m1",
                    metaId = "m1",
                    videoId = "m1",
                    type = "movie",
                    season = null,
                    episode = null,
                    positionMs = 7_195_000L,
                    durationMs = 7_200_000L,
                    watched = false,
                    updatedAt = 1_000L,
                )
            coEvery { fakeProgressRepository.get("m1|m1") } returns rowNearEnd

            holder.start(
                PlaybackSelection("Movie", PlaybackSource.Direct("https://example.com/v.mp4"), "movie", "m1", "v1"),
            )
            createViewModel()
            advanceUntilIdle()

            val ctrl = fakeFactory.createdControllers.single()
            ctrl.snapshotFlow.value =
                PlayerSnapshot(isPlaying = true, isSeekable = true, durationMs = 7_200_000L, positionMs = 1_000L)
            advanceUntilIdle()

            assertTrue(ctrl.seekPositions.isEmpty())
        }

    @Test
    fun PL76_snapshotsBeforeSeekable_thenSeekable_thenMore_exactlyOneSeek() =
        runTest(testDispatcher) {
            val savedRow =
                WatchProgress(
                    key = "m1|m1",
                    metaId = "m1",
                    videoId = "m1",
                    type = "movie",
                    season = null,
                    episode = null,
                    positionMs = 720_000L,
                    durationMs = 7_200_000L,
                    watched = false,
                    updatedAt = 1_000L,
                )
            coEvery { fakeProgressRepository.get("m1|m1") } returns savedRow

            holder.start(
                PlaybackSelection("Movie", PlaybackSource.Direct("https://example.com/v.mp4"), "movie", "m1", "v1"),
            )
            createViewModel()
            advanceUntilIdle()

            val ctrl = fakeFactory.createdControllers.single()
            ctrl.snapshotFlow.value = PlayerSnapshot(isPlaying = true, isSeekable = false, durationMs = 7_200_000L)
            advanceUntilIdle()
            assertTrue(ctrl.seekPositions.isEmpty())

            ctrl.snapshotFlow.value = PlayerSnapshot(isPlaying = true, isSeekable = true, durationMs = 7_200_000L)
            advanceUntilIdle()
            assertEquals(listOf(720_000L), ctrl.seekPositions)

            ctrl.snapshotFlow.value =
                PlayerSnapshot(isPlaying = true, isSeekable = true, durationMs = 7_200_000L, positionMs = 725_000L)
            advanceUntilIdle()
            assertEquals(listOf(720_000L), ctrl.seekPositions)
        }

    @Test
    fun PL77_controllerFailsBeforePlaying_reopenResumesOnce_afterResumeReopenDoesNotResumeAgain() =
        runTest(testDispatcher) {
            val savedRow =
                WatchProgress(
                    key = "m1|m1",
                    metaId = "m1",
                    videoId = "m1",
                    type = "movie",
                    season = null,
                    episode = null,
                    positionMs = 720_000L,
                    durationMs = 7_200_000L,
                    watched = false,
                    updatedAt = 1_000L,
                )
            coEvery { fakeProgressRepository.get("m1|m1") } returns savedRow

            holder.start(
                PlaybackSelection("Movie", PlaybackSource.Direct("https://example.com/v.mp4"), "movie", "m1", "v1"),
            )
            val vm = createViewModel()
            advanceUntilIdle()

            val ctrl = fakeFactory.createdControllers.single()
            ctrl.snapshotFlow.value = PlayerSnapshot(failed = true)
            advanceUntilIdle()
            assertEquals(0, ctrl.seekPositions.size)

            vm.onEvent(PlayerEvent.Retry)
            advanceUntilIdle()
            ctrl.snapshotFlow.value = PlayerSnapshot(isPlaying = true, isSeekable = true, durationMs = 7_200_000L)
            advanceUntilIdle()
            assertEquals(listOf(720_000L), ctrl.seekPositions)

            ctrl.snapshotFlow.value = PlayerSnapshot(failed = true)
            advanceUntilIdle()
            vm.onEvent(PlayerEvent.Retry)
            advanceUntilIdle()
            ctrl.snapshotFlow.value = PlayerSnapshot(isPlaying = true, isSeekable = true, durationMs = 7_200_000L)
            advanceUntilIdle()
            assertEquals(listOf(720_000L), ctrl.seekPositions)
        }

    @Test
    fun PL78_onCleared_flushCalledBeforeControllerReleased() =
        runTest(testDispatcher) {
            val mockTracker = mockk<PlaybackProgressTracker>(relaxed = true)
            val mockController = mockk<PlayerController>(relaxed = true)
            coEvery { mockController.snapshot } returns MutableStateFlow(PlayerSnapshot())
            coEvery { mockController.tracks } returns MutableStateFlow(PlayerTracks())
            fakeFactory.nextController = mockController

            holder.start(
                PlaybackSelection("Movie", PlaybackSource.Direct("https://example.com/v.mp4"), "movie", "m1", "v1"),
            )
            val vm = createViewModel()
            advanceUntilIdle()

            vm.progressTracker = mockTracker

            vm.onCleared()
            verifyOrder {
                mockTracker.flush()
                mockController.release()
            }
        }

    @Test
    fun PL79_savedRowReadThrows_noSeek_noErrorState_playbackContinues() =
        runTest(testDispatcher) {
            coEvery { fakeProgressRepository.get(any()) } throws RuntimeException("Storage failure")

            holder.start(
                PlaybackSelection("Movie", PlaybackSource.Direct("https://example.com/v.mp4"), "movie", "m1", "v1"),
            )
            val vm = createViewModel()
            advanceUntilIdle()

            val ctrl = fakeFactory.createdControllers.single()
            ctrl.snapshotFlow.value = PlayerSnapshot(isPlaying = true, isSeekable = true, durationMs = 7_200_000L)
            advanceUntilIdle()

            assertTrue(ctrl.seekPositions.isEmpty())
            assertEquals(PlayerPhase.Playing, vm.uiState.value.phase)
        }

    @Test
    fun PL80_snapshotToTrackerInput_playingPausedEndedFailed() {
        val snapPlaying =
            PlayerSnapshot(positionMs = 10, durationMs = 100, isPlaying = true, ended = false, failed = false)
        assertEquals(
            PlaybackProgressTracker.Input(10, 100, playing = true, ended = false, failed = false),
            snapPlaying.toTrackerInput(),
        )

        val snapPaused =
            PlayerSnapshot(positionMs = 10, durationMs = 100, isPlaying = false, ended = false, failed = false)
        assertEquals(
            PlaybackProgressTracker.Input(10, 100, playing = false, ended = false, failed = false),
            snapPaused.toTrackerInput(),
        )

        val snapEnded =
            PlayerSnapshot(positionMs = 100, durationMs = 100, isPlaying = false, ended = true, failed = false)
        assertEquals(
            PlaybackProgressTracker.Input(100, 100, playing = false, ended = true, failed = false),
            snapEnded.toTrackerInput(),
        )

        val snapFailed =
            PlayerSnapshot(positionMs = 10, durationMs = 100, isPlaying = false, ended = false, failed = true)
        assertEquals(
            PlaybackProgressTracker.Input(10, 100, playing = false, ended = false, failed = true),
            snapFailed.toTrackerInput(),
        )
    }

    @Test
    fun PL81_toProgressTarget_movieEpisodeMissingIds() {
        val movieSel =
            PlaybackSelection(
                title = "Movie",
                source = PlaybackSource.Direct(""),
                type = "movie",
                metaId = "m1",
                videoId = "v1",
                logoUrl = "logo",
                backgroundUrl = "bg",
            )
        val movieTarget = movieSel.toProgressTarget()
        assertTrue(
            movieTarget != null && movieTarget.isMovie && movieTarget.season == null && movieTarget.episode == null,
        )
        assertEquals("m1", movieTarget?.metaId)
        assertEquals("m1", movieTarget?.videoId)

        val epSel =
            PlaybackSelection(
                title = "Series",
                source = PlaybackSource.Direct(""),
                type = "series",
                metaId = "s1",
                videoId = "e1",
                season = 1,
                episode = 2,
            )
        val epTarget = epSel.toProgressTarget()
        assertTrue(epTarget != null && !epTarget.isMovie && epTarget.season == 1 && epTarget.episode == 2)

        val missingMeta = PlaybackSelection("Ep", PlaybackSource.Direct(""), "movie", "", "v1")
        assertNull(missingMeta.toProgressTarget())

        val missingVideo = PlaybackSelection("Ep", PlaybackSource.Direct(""), "movie", "m1", "")
        assertNull(missingVideo.toProgressTarget())

        val withMetaAndPoster =
            PlaybackSelection(
                title = "Breaking Bad - S01E01 Pilot",
                source = PlaybackSource.Direct(""),
                type = "series",
                metaId = "bb",
                videoId = "bb:1:1",
                posterUrl = "https://poster.jpg",
                logoUrl = "https://logo.png",
                metaName = "Breaking Bad",
            )
        val targetWithMeta = withMetaAndPoster.toProgressTarget()
        assertEquals("Breaking Bad", targetWithMeta?.name)
        assertEquals("https://poster.jpg", targetWithMeta?.poster)
    }

    private class FakePlayerController : PlayerController {
        val snapshotFlow = MutableStateFlow(PlayerSnapshot())
        override val snapshot: StateFlow<PlayerSnapshot> = snapshotFlow.asStateFlow()

        val tracksFlow = MutableStateFlow(PlayerTracks())
        override val tracks: StateFlow<PlayerTracks> = tracksFlow.asStateFlow()

        val playedUrls = mutableListOf<String>()
        var pauseCallCount = 0
        var resumeCallCount = 0
        val seekPositions = mutableListOf<Long>()
        val rateCalls = mutableListOf<Float>()
        val selectedAudioTracks = mutableListOf<String>()
        val selectedSubtitleTracks = mutableListOf<String?>()
        val addedSubtitles = mutableListOf<String>()
        val subtitleDelayCalls = mutableListOf<Long>()
        val videoScaleCalls = mutableListOf<VideoScale>()
        var releaseCallCount = 0

        override fun createSurface(context: Context): View = View(context)

        override fun detachSurface() {
            // No-op for tests
        }

        override fun play(url: String) {
            playedUrls.add(url)
        }

        override fun pause() {
            pauseCallCount++
        }

        override fun resume() {
            resumeCallCount++
        }

        override fun seekTo(positionMs: Long) {
            seekPositions.add(positionMs)
        }

        override fun setRate(rate: Float) {
            rateCalls.add(rate)
        }

        override fun selectAudioTrack(id: String) {
            selectedAudioTracks.add(id)
        }

        override fun selectSubtitleTrack(id: String?) {
            selectedSubtitleTracks.add(id)
        }

        override fun addSubtitle(uri: String) {
            addedSubtitles.add(uri)
        }

        override fun setSubtitleDelay(ms: Long) {
            subtitleDelayCalls.add(ms)
        }

        override fun setVideoScale(scale: VideoScale) {
            videoScaleCalls.add(scale)
        }

        override fun release() {
            releaseCallCount++
        }
    }

    private class FakeSubtitleFileImporter : SubtitleFileImporter {
        var importResult: ImportResult = ImportResult.Failed
        val importedUris = mutableListOf<String>()
        var cleanupCallCount = 0

        override suspend fun import(uri: String): ImportResult {
            importedUris.add(uri)
            return importResult
        }

        override fun cleanup() {
            cleanupCallCount++
        }
    }

    private class FakePlayerControllerFactory : PlayerControllerFactory {
        val createdControllers = mutableListOf<FakePlayerController>()
        var nextController: PlayerController? = null
        var lastRenderStyle: SubtitleRenderStyle? = null

        override fun create(style: SubtitleRenderStyle): PlayerController {
            lastRenderStyle = style
            val custom = nextController
            if (custom != null) {
                nextController = null
                return custom
            }
            val ctrl = FakePlayerController()
            createdControllers.add(ctrl)
            return ctrl
        }
    }

    private class FakePlayerPrefsRepository : PlayerPrefsRepository {
        val seekStepSecondsFlow = MutableStateFlow(10)
        override val seekStepSeconds: Flow<Int> = seekStepSecondsFlow
        override val quickPlay: Flow<Boolean> = MutableStateFlow(false)
        override val sort: Flow<StreamSort> = MutableStateFlow(StreamSort.BEST)
        override val preferredLanguage: Flow<String?> = MutableStateFlow(null)
        override val preferredQuality: Flow<Int?> = MutableStateFlow(null)
        override val hideCam: Flow<Boolean> = MutableStateFlow(false)
        val videoScaleFlow = MutableStateFlow(VideoScale.FIT)
        override val videoScale: Flow<VideoScale> = videoScaleFlow
        val preferredAudioLanguageFlow = MutableStateFlow<String?>(null)
        override val preferredAudioLanguage: Flow<String?> = preferredAudioLanguageFlow
        val preferredSubtitleLanguageFlow = MutableStateFlow<String?>(null)
        override val preferredSubtitleLanguage: Flow<String?> = preferredSubtitleLanguageFlow
        val subtitleStyleFlow =
            MutableStateFlow(
                com.nexus.stream.domain.model
                    .SubtitleStyle(),
            )
        override val subtitleStyle: Flow<com.nexus.stream.domain.model.SubtitleStyle> = subtitleStyleFlow
        val autoPlayNextFlow = MutableStateFlow(true)
        override val autoPlayNext: Flow<Boolean> = autoPlayNextFlow

        override suspend fun setAutoPlayNext(enabled: Boolean) {
            autoPlayNextFlow.value = enabled
        }

        override suspend fun setQuickPlay(enabled: Boolean) {
            // No-op for tests
        }

        override suspend fun setSort(sort: StreamSort) {
            // No-op for tests
        }

        override suspend fun setPreferredLanguage(code: String?) {
            // No-op for tests
        }

        override suspend fun setPreferredQuality(quality: Int?) {
            // No-op for tests
        }

        override suspend fun setHideCam(hideCam: Boolean) {
            // No-op for tests
        }

        override suspend fun setSeekStepSeconds(seconds: Int) {
            seekStepSecondsFlow.value = seconds
        }

        override suspend fun setVideoScale(scale: VideoScale) {
            videoScaleFlow.value = scale
        }

        override suspend fun setPreferredAudioLanguage(code: String?) {
            preferredAudioLanguageFlow.value = code
        }

        override suspend fun setPreferredSubtitleLanguage(code: String?) {
            preferredSubtitleLanguageFlow.value = code
        }

        override suspend fun setSubtitleSize(size: com.nexus.stream.domain.model.SubtitleSize) {
            subtitleStyleFlow.value = subtitleStyleFlow.value.copy(size = size)
        }

        override suspend fun setSubtitleColor(id: String) {
            subtitleStyleFlow.value = subtitleStyleFlow.value.copy(colorId = id)
        }

        override suspend fun setSubtitleBackground(background: com.nexus.stream.domain.model.SubtitleBackground) {
            subtitleStyleFlow.value = subtitleStyleFlow.value.copy(background = background)
        }

        override suspend fun setSubtitleEdge(edge: com.nexus.stream.domain.model.SubtitleEdge) {
            subtitleStyleFlow.value = subtitleStyleFlow.value.copy(edge = edge)
        }

        override suspend fun setSubtitleBold(bold: Boolean) {
            subtitleStyleFlow.value = subtitleStyleFlow.value.copy(bold = bold)
        }
    }

    private class FakeStreamProxy : StreamProxy {
        val openCalls = mutableListOf<Pair<String, Map<String, String>>>()
        var closedSessionsCount = 0
        var nextSessionUrl = "http://127.0.0.1:45678/p/token/stream.mp4"
        var nextDiagnostics: StateFlow<ProxyDiagnostics> = MutableStateFlow(ProxyDiagnostics())
        var throwOnOpen: Exception? = null

        override fun open(
            url: String,
            headers: Map<String, String>,
        ): ProxySession {
            val err = throwOnOpen
            if (err != null) throw err
            openCalls.add(url to headers)
            return ProxySession(nextSessionUrl, nextDiagnostics) {
                closedSessionsCount++
            }
        }
    }

    private class FakeTorrentEngine : TorrentEngine {
        val prepareCalls = mutableListOf<TorrentRequest>()
        val prepareFlow = MutableSharedFlow<TorrentState>()
        val selectFileCalls = mutableListOf<Pair<String, Int>>()
        var isCancelled = false

        override fun prepare(request: TorrentRequest): Flow<TorrentState> {
            prepareCalls.add(request)
            return flow {
                try {
                    prepareFlow.collect { emit(it) }
                } finally {
                    isCancelled = true
                }
            }
        }

        override suspend fun selectFile(
            infoHash: String,
            fileIdx: Int,
        ) {
            selectFileCalls.add(infoHash to fileIdx)
        }

        override suspend fun stop(infoHash: String) {
            // No-op for tests
        }
    }

    private class FakeAudioFocusController : AudioFocusController {
        val eventsFlow = MutableSharedFlow<AudioFocusEvent>(extraBufferCapacity = 8)
        override val events: SharedFlow<AudioFocusEvent> = eventsFlow
        var requestResult: Boolean = true
        var requestCount: Int = 0
        var abandonCount: Int = 0

        override fun request(): Boolean {
            requestCount++
            return requestResult
        }

        override fun abandon() {
            abandonCount++
        }
    }

    @Test
    fun PL82_toProgressTarget_movieUsesMetaId_seriesKeepsVideoId() {
        val mockSource = mockk<PlaybackSource>(relaxed = true)

        val movieDifferentId =
            PlaybackSelection(
                title = "Movie Title",
                source = mockSource,
                type = "movie",
                metaId = "tt1234567",
                videoId = "differentVideoId123",
            )
        val movieDiffTarget = movieDifferentId.toProgressTarget()
        assertNotNull(movieDiffTarget)
        assertEquals("tt1234567", movieDiffTarget?.metaId)
        assertEquals("tt1234567", movieDiffTarget?.videoId)
        assertTrue(movieDiffTarget!!.isMovie)

        val movieSameId =
            PlaybackSelection(
                title = "Movie Title",
                source = mockSource,
                type = "movie",
                metaId = "tt1234567",
                videoId = "tt1234567",
            )
        val movieSameTarget = movieSameId.toProgressTarget()
        assertNotNull(movieSameTarget)
        assertEquals("tt1234567", movieSameTarget?.metaId)
        assertEquals("tt1234567", movieSameTarget?.videoId)
        assertTrue(movieSameTarget!!.isMovie)

        val seriesEp =
            PlaybackSelection(
                title = "Series Title",
                source = mockSource,
                type = "series",
                metaId = "tt9999999",
                videoId = "tt9999999:1:4",
                season = 1,
                episode = 4,
            )
        val seriesTarget = seriesEp.toProgressTarget()
        assertNotNull(seriesTarget)
        assertEquals("tt9999999", seriesTarget?.metaId)
        assertEquals("tt9999999:1:4", seriesTarget?.videoId)
        assertFalse(seriesTarget!!.isMovie)
    }

    @Test
    fun PL83_toProgressTarget_carriesTheSourceFingerprint() {
        val mockSource = mockk<PlaybackSource>(relaxed = true)
        val fp =
            com.nexus.stream.domain.model.SourceFingerprint(
                addonKeyHashes = listOf("hash1"),
                kind = "Torrent",
            )
        val withFp =
            PlaybackSelection(
                title = "Movie",
                source = mockSource,
                type = "movie",
                metaId = "tt1",
                videoId = "tt1",
                sourceFingerprint = fp,
            )
        assertEquals(fp, withFp.toProgressTarget()?.source)

        val withoutFp =
            PlaybackSelection(
                title = "Movie",
                source = mockSource,
                type = "movie",
                metaId = "tt1",
                videoId = "tt1",
            )
        assertNull(withoutFp.toProgressTarget()?.source)
    }

    @Test
    fun PL84_torrentRequest_carriesTheProgressKey() =
        runTest {
            val torrentReq = TorrentRequest(infoHash = "0123456789abcdef0123456789abcdef01234567")
            val torrentSource = PlaybackSource.Torrent(request = torrentReq)

            // Case 1: Series episode
            val seriesHolder = PlaybackSessionHolder()
            seriesHolder.start(
                PlaybackSelection(
                    title = "Series Ep",
                    source = torrentSource,
                    type = "series",
                    metaId = "tt123",
                    videoId = "tt123:1:2",
                ),
            )
            createViewModel(seriesHolder)
            runCurrent()
            assertEquals("tt123|tt123:1:2", fakeEngine.prepareCalls.last().cacheKey)

            // Case 2: Movie with defaultVideoId
            val movieHolder = PlaybackSessionHolder()
            movieHolder.start(
                PlaybackSelection(
                    title = "Movie",
                    source = torrentSource,
                    type = "movie",
                    metaId = "tt456",
                    videoId = "tt456",
                ),
            )
            createViewModel(movieHolder)
            runCurrent()
            assertEquals("tt456|tt456", fakeEngine.prepareCalls.last().cacheKey)

            // Case 3: Selection without ids
            val noIdsHolder = PlaybackSessionHolder()
            noIdsHolder.start(
                PlaybackSelection(
                    title = "No Ids",
                    source = torrentSource,
                    type = "movie",
                    metaId = "",
                    videoId = "",
                ),
            )
            createViewModel(noIdsHolder)
            runCurrent()
            assertNull(fakeEngine.prepareCalls.last().cacheKey)
        }

    @Test
    fun AU1_completionKey_equalsTheLedgerKey() {
        val seriesSelection =
            PlaybackSelection(
                title = "Series",
                source = PlaybackSource.Torrent(TorrentRequest(infoHash = "0123456789abcdef0123456789abcdef01234567")),
                type = "series",
                metaId = "tt123",
                videoId = "tt123:1:2",
            )
        val seriesTarget = seriesSelection.toProgressTarget()!!
        val seriesKey1 = ProgressRules.progressKey(seriesTarget.metaId, seriesTarget.videoId)
        val seriesKey2 = progressKeyFor(seriesSelection.type, seriesSelection.metaId, seriesSelection.videoId)
        assertEquals("tt123|tt123:1:2", seriesKey1)
        assertEquals(seriesKey1, seriesKey2)

        val movieDiffSelection =
            PlaybackSelection(
                title = "Movie Diff",
                source = PlaybackSource.Torrent(TorrentRequest(infoHash = "0123456789abcdef0123456789abcdef01234567")),
                type = "movie",
                metaId = "kitsu:1",
                videoId = "kitsu:1:1",
            )
        val movieDiffTarget = movieDiffSelection.toProgressTarget()!!
        val movieDiffKey1 = ProgressRules.progressKey(movieDiffTarget.metaId, movieDiffTarget.videoId)
        val movieDiffKey2 =
            progressKeyFor(
                movieDiffSelection.type,
                movieDiffSelection.metaId,
                movieDiffSelection.videoId,
            )
        assertEquals("kitsu:1|kitsu:1", movieDiffKey1)
        assertEquals(movieDiffKey1, movieDiffKey2)

        val movieSameSelection =
            PlaybackSelection(
                title = "Movie Same",
                source = PlaybackSource.Torrent(TorrentRequest(infoHash = "0123456789abcdef0123456789abcdef01234567")),
                type = "movie",
                metaId = "tt456",
                videoId = "tt456",
            )
        val movieSameTarget = movieSameSelection.toProgressTarget()!!
        val movieSameKey1 = ProgressRules.progressKey(movieSameTarget.metaId, movieSameTarget.videoId)
        val movieSameKey2 =
            progressKeyFor(
                movieSameSelection.type,
                movieSameSelection.metaId,
                movieSameSelection.videoId,
            )
        assertEquals("tt456|tt456", movieSameKey1)
        assertEquals(movieSameKey1, movieSameKey2)
    }

    @Test
    fun PB1_playerViewModel_forwardsSnapshotsAndFirstPlaying_toTheController() =
        runTest(testDispatcher) {
            val selection =
                PlaybackSelection(
                    title = "Test",
                    source = PlaybackSource.Direct("https://example.com/stream.mp4"),
                    type = "series",
                    metaId = "tt1",
                    videoId = "tt1:1:1",
                )
            holder.start(selection)

            val fakeNextCtrl = mockk<NextEpisodeController>(relaxed = true)
            every { fakeNextCtrl.state } returns MutableStateFlow(NextEpisodeState())
            every { fakeNextCtrl.effects } returns emptyFlow<NextEpisodeEffect>()

            val vm = createViewModel(nextEpisodeController = fakeNextCtrl)
            advanceUntilIdle()
            val ctrl = fakeFactory.createdControllers.first()

            ctrl.snapshotFlow.value = PlayerSnapshot(isPlaying = true, durationMs = 600_000L, positionMs = 10_000L)
            runCurrent()

            coVerify(exactly = 1) { fakeNextCtrl.onPlaybackStarted(selection) }
            coVerify(atLeast = 1) { fakeNextCtrl.onSnapshot(600_000L, 10_000L, false) }
        }

    @Test
    fun PB2_playerViewModel_effectsReachTheScreen() =
        runTest(testDispatcher) {
            val fakeNextCtrl = mockk<NextEpisodeController>(relaxed = true)
            val effectsFlow = MutableSharedFlow<NextEpisodeEffect>()
            every { fakeNextCtrl.state } returns MutableStateFlow(NextEpisodeState())
            every { fakeNextCtrl.effects } returns effectsFlow

            val vm = createViewModel(nextEpisodeController = fakeNextCtrl)
            advanceUntilIdle()
            val collectedEffects = mutableListOf<PlayerEffect>()
            val job = launch { vm.effects.collect { collectedEffects.add(it) } }
            runCurrent()

            effectsFlow.emit(NextEpisodeEffect.SwitchToNext)
            runCurrent()

            val req = PlaybackRequest(type = "series", metaId = "s1", videoId = "s1:1:2", title = "Ep 2")
            effectsFlow.emit(NextEpisodeEffect.ChooseNextSource(req))
            runCurrent()

            assertEquals(2, collectedEffects.size)
            assertEquals(PlayerEffect.SwitchToNext, collectedEffects[0])
            assertEquals(PlayerEffect.ChooseNextSource(req), collectedEffects[1])
            job.cancel()
        }

    @Test
    fun PB3_playerViewModel_clearedCancelsTheController() =
        runTest(testDispatcher) {
            val fakeNextCtrl = mockk<NextEpisodeController>(relaxed = true)
            every { fakeNextCtrl.state } returns MutableStateFlow(NextEpisodeState())
            every { fakeNextCtrl.effects } returns emptyFlow<NextEpisodeEffect>()

            val vm = createViewModel(nextEpisodeController = fakeNextCtrl)
            val mockTracker = mockk<PlaybackProgressTracker>(relaxed = true)
            vm.progressTracker = mockTracker

            vm.onCleared()

            verifyOrder {
                mockTracker.flush()
                fakeNextCtrl.clear()
            }
        }
}
