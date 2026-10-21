package com.nexus.stream.presentation.feature.player

import android.content.Context
import android.view.View
import androidx.lifecycle.ViewModel
import com.nexus.stream.data.player.PlayerController
import com.nexus.stream.data.player.PlayerControllerFactory
import com.nexus.stream.data.player.PlayerSnapshot
import com.nexus.stream.domain.model.AppError
import com.nexus.stream.domain.model.PlaybackSource
import com.nexus.stream.domain.model.TorrentFailReason
import com.nexus.stream.domain.model.TorrentFile
import com.nexus.stream.domain.model.TorrentRequest
import com.nexus.stream.domain.model.TorrentState
import com.nexus.stream.domain.repository.ProxyDiagnostics
import com.nexus.stream.domain.repository.ProxySession
import com.nexus.stream.domain.repository.StreamProxy
import com.nexus.stream.domain.repository.TorrentEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val holder = PlaybackSessionHolder()
    private val fakeFactory = FakePlayerControllerFactory()
    private val fakeProxy = FakeStreamProxy()
    private val fakeEngine = FakeTorrentEngine()
    private val statsHub =
        com.nexus.stream.data.torrent
            .StreamStatsHub()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(h: PlaybackSessionHolder = holder): PlayerViewModel =
        PlayerViewModel(
            holder = h,
            controllerFactory = fakeFactory,
            streamProxy = fakeProxy,
            torrentEngine = fakeEngine,
            statsHub = statsHub,
            mainDispatcher = testDispatcher,
        )

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

    private class FakePlayerController : PlayerController {
        val snapshotFlow = MutableStateFlow(PlayerSnapshot())
        override val snapshot: StateFlow<PlayerSnapshot> = snapshotFlow.asStateFlow()

        val playedUrls = mutableListOf<String>()
        var pauseCallCount = 0
        var resumeCallCount = 0
        val seekPositions = mutableListOf<Long>()
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

        override fun release() {
            releaseCallCount++
        }
    }

    private class FakePlayerControllerFactory : PlayerControllerFactory {
        val createdControllers = mutableListOf<FakePlayerController>()

        override fun create(): PlayerController {
            val ctrl = FakePlayerController()
            createdControllers.add(ctrl)
            return ctrl
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
}
