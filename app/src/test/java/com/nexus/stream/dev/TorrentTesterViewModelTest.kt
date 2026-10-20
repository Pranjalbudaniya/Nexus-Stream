package com.nexus.stream.dev

import androidx.lifecycle.ViewModel
import com.nexus.stream.domain.model.AppError
import com.nexus.stream.domain.model.TorrentFailReason
import com.nexus.stream.domain.model.TorrentFile
import com.nexus.stream.domain.model.TorrentRequest
import com.nexus.stream.domain.model.TorrentState
import com.nexus.stream.domain.repository.TorrentCache
import com.nexus.stream.domain.repository.TorrentEngine
import com.nexus.stream.domain.repository.TorrentPrefsRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TorrentTesterViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val fakeEngine = FakeTorrentEngine()
    private val fakeCache = FakeTorrentCache()
    private val fakePrefs = FakeTorrentPrefsRepository()
    private lateinit var viewModel: TorrentTesterViewModel

    private val validHash = "0123456789abcdef0123456789abcdef01234567"
    private val validTracker = "udp://t.example:1337/announce"
    private val validMagnet = "magnet:?xt=urn:btih:${validHash.uppercase()}&tr=udp%3A%2F%2Ft.example%3A1337%2Fannounce"

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = TorrentTesterViewModel(fakeEngine, fakeCache, fakePrefs)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun V1_startWithGarbageText_inputInvalid() =
        runTest(testDispatcher) {
            viewModel.onEvent(TorrentTesterEvent.InputChanged("not-a-valid-magnet"))
            viewModel.onEvent(TorrentTesterEvent.Start)
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.inputInvalid)
            assertFalse(viewModel.uiState.value.running)
            assertTrue(fakeEngine.prepareCalls.isEmpty())
        }

    @Test
    fun V2_startWithValidMagnet_clearsInputAndPrepares() =
        runTest(testDispatcher) {
            viewModel.onEvent(TorrentTesterEvent.InputChanged(validMagnet))
            viewModel.onEvent(TorrentTesterEvent.Start)
            advanceUntilIdle()

            assertEquals("", viewModel.uiState.value.input)
            assertFalse(viewModel.uiState.value.inputInvalid)
            assertTrue(viewModel.uiState.value.running)
            assertEquals(1, fakeEngine.prepareCalls.size)
            assertEquals(validHash, fakeEngine.prepareCalls[0].infoHash)
            assertEquals(listOf(validTracker), fakeEngine.prepareCalls[0].trackers)

            fakeEngine.prepareFlow.emit(TorrentState.FetchingMetadata)
            advanceUntilIdle()
            assertEquals(TorrentState.FetchingMetadata, viewModel.uiState.value.state)

            val buffering = TorrentState.Buffering(percent = 42, speedBps = 1024L, peers = 10)
            fakeEngine.prepareFlow.emit(buffering)
            advanceUntilIdle()
            assertEquals(buffering, viewModel.uiState.value.state)
        }

    @Test
    fun V3_needsFilePick_selectFileCallsEngine() =
        runTest(testDispatcher) {
            viewModel.onEvent(TorrentTesterEvent.InputChanged(validMagnet))
            viewModel.onEvent(TorrentTesterEvent.Start)
            advanceUntilIdle()

            val pickState =
                TorrentState.NeedsFilePick(
                    files = listOf(TorrentFile(index = 2, path = "video.mkv", sizeBytes = 1024L)),
                )
            fakeEngine.prepareFlow.emit(pickState)
            advanceUntilIdle()

            viewModel.onEvent(TorrentTesterEvent.SelectFile(2))
            advanceUntilIdle()

            assertEquals(1, fakeEngine.selectFileCalls.size)
            assertEquals(validHash to 2, fakeEngine.selectFileCalls[0])
        }

    @Test
    fun V4_stopWhileRunning_cancelsAndStopsEngine() =
        runTest(testDispatcher) {
            viewModel.onEvent(TorrentTesterEvent.InputChanged(validMagnet))
            viewModel.onEvent(TorrentTesterEvent.Start)
            advanceUntilIdle()

            fakeEngine.prepareFlow.emit(TorrentState.FetchingMetadata)
            advanceUntilIdle()

            viewModel.onEvent(TorrentTesterEvent.Stop)
            advanceUntilIdle()

            assertTrue(fakeEngine.isCancelled)
            assertEquals(listOf(validHash), fakeEngine.stopCalls)
            assertNull(viewModel.uiState.value.state)
            assertFalse(viewModel.uiState.value.running)
        }

    @Test
    fun V5_failedEmitted_runningFalseAndShowsFailed() =
        runTest(testDispatcher) {
            val failedState = TorrentState.Failed(AppError.Torrent(TorrentFailReason.NO_VIDEO_FILE))
            fakeEngine.customFlow = flowOf(failedState)

            viewModel.onEvent(TorrentTesterEvent.InputChanged(validMagnet))
            viewModel.onEvent(TorrentTesterEvent.Start)
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.running)
            assertEquals(failedState, viewModel.uiState.value.state)

            fakeEngine.customFlow = null
            viewModel.onEvent(TorrentTesterEvent.InputChanged(validMagnet))
            viewModel.onEvent(TorrentTesterEvent.Start)
            advanceUntilIdle()
            assertEquals(2, fakeEngine.prepareCalls.size)
        }

    @Test
    fun V6_viewModelClearedWhileRunning_cancelsCollection() =
        runTest(testDispatcher) {
            viewModel.onEvent(TorrentTesterEvent.InputChanged(validMagnet))
            viewModel.onEvent(TorrentTesterEvent.Start)
            advanceUntilIdle()

            assertFalse(fakeEngine.isCancelled)
            val clearMethod = ViewModel::class.java.getDeclaredMethod("onCleared")
            clearMethod.isAccessible = true
            clearMethod.invoke(viewModel)
            advanceUntilIdle()

            assertTrue(fakeEngine.isCancelled)
        }

    @Test
    fun V7_afterV2_uiStateToString_noHashOrTracker() =
        runTest(testDispatcher) {
            viewModel.onEvent(TorrentTesterEvent.InputChanged(validMagnet))
            viewModel.onEvent(TorrentTesterEvent.Start)
            advanceUntilIdle()

            val stateString = viewModel.uiState.value.toString()
            assertFalse(stateString.contains(validHash, ignoreCase = true))
            assertFalse(stateString.contains(validTracker, ignoreCase = true))
            assertFalse(stateString.contains("example", ignoreCase = true))
        }

    @Test
    fun V8_startTwice_callsPrepareOnce() =
        runTest(testDispatcher) {
            viewModel.onEvent(TorrentTesterEvent.InputChanged(validMagnet))
            viewModel.onEvent(TorrentTesterEvent.Start)
            viewModel.onEvent(TorrentTesterEvent.Start)
            advanceUntilIdle()

            assertEquals(1, fakeEngine.prepareCalls.size)
        }

    @Test
    fun V9_readyWithLocalUrl_playInOtherAppEmitsOpenPlayerAndUrlNotInUiState() =
        runTest(testDispatcher) {
            val effects = mutableListOf<TorrentTesterEffect>()
            val effectsJob =
                launch {
                    viewModel.effects.collect { effects.add(it) }
                }

            viewModel.onEvent(TorrentTesterEvent.InputChanged(validMagnet))
            viewModel.onEvent(TorrentTesterEvent.Start)
            advanceUntilIdle()

            val streamUrl = "http://127.0.0.1:12345/t/0123456789abcdef0123456789abcdef/stream.mkv"
            val readyWithUrl =
                TorrentState.Ready(
                    fileIndex = 1,
                    fileName = "movie.mkv",
                    fileSize = 100_000_000L,
                    localUrl = streamUrl,
                )
            fakeEngine.prepareFlow.emit(readyWithUrl)
            advanceUntilIdle()

            val stateString = viewModel.uiState.value.toString()
            assertFalse(stateString.contains("http://", ignoreCase = true))
            assertFalse(stateString.contains("12345"))
            assertFalse(stateString.contains(streamUrl))

            viewModel.onEvent(TorrentTesterEvent.PlayInOtherApp)
            advanceUntilIdle()

            assertEquals(1, effects.size)
            assertEquals(TorrentTesterEffect.OpenPlayer(streamUrl), effects[0])

            effectsJob.cancel()
        }

    @Test
    fun V10_readyWithNullLocalUrl_playInOtherAppEmitsNoEffect() =
        runTest(testDispatcher) {
            val effects = mutableListOf<TorrentTesterEffect>()
            val effectsJob =
                launch {
                    viewModel.effects.collect { effects.add(it) }
                }

            viewModel.onEvent(TorrentTesterEvent.InputChanged(validMagnet))
            viewModel.onEvent(TorrentTesterEvent.Start)
            advanceUntilIdle()

            val readyNullUrl =
                TorrentState.Ready(
                    fileIndex = 1,
                    fileName = "movie.mkv",
                    fileSize = 100_000_000L,
                    localUrl = null,
                )
            fakeEngine.prepareFlow.emit(readyNullUrl)
            advanceUntilIdle()

            viewModel.onEvent(TorrentTesterEvent.PlayInOtherApp)
            advanceUntilIdle()

            assertTrue(effects.isEmpty())

            effectsJob.cancel()
        }

    @Test
    fun V12_failedMeteredBlocked_stateOffersAllowOnce_noHashInUiState() =
        runTest(testDispatcher) {
            fakeEngine.customFlow = flowOf(TorrentState.Failed(AppError.MeteredBlocked))

            viewModel.onEvent(TorrentTesterEvent.InputChanged(validMagnet))
            viewModel.onEvent(TorrentTesterEvent.Start)
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.isBlockedMetered)
            assertFalse(viewModel.uiState.value.running)
            assertEquals(TorrentState.Failed(AppError.MeteredBlocked), viewModel.uiState.value.state)

            val stateString = viewModel.uiState.value.toString()
            assertFalse(stateString.contains(validHash, ignoreCase = true))
        }

    @Test
    fun V13_allowMeteredOnce_callsEngineWithAllowMeteredTrueAndClearsBlockedFlag() =
        runTest(testDispatcher) {
            fakeEngine.customFlow = flowOf(TorrentState.Failed(AppError.MeteredBlocked))

            viewModel.onEvent(TorrentTesterEvent.InputChanged(validMagnet))
            viewModel.onEvent(TorrentTesterEvent.Start)
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.isBlockedMetered)
            assertEquals(1, fakeEngine.prepareCalls.size)
            assertFalse(fakeEngine.prepareCalls[0].allowMetered)

            fakeEngine.customFlow = null
            viewModel.onEvent(TorrentTesterEvent.AllowMeteredOnce)
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.isBlockedMetered)
            assertEquals(2, fakeEngine.prepareCalls.size)
            assertEquals(validHash, fakeEngine.prepareCalls[1].infoHash)
            assertTrue(fakeEngine.prepareCalls[1].allowMetered)
        }

    @Test
    fun V14_afterV13_newStartHasAllowMeteredFalse() =
        runTest(testDispatcher) {
            fakeEngine.customFlow = flowOf(TorrentState.Failed(AppError.MeteredBlocked))

            viewModel.onEvent(TorrentTesterEvent.InputChanged(validMagnet))
            viewModel.onEvent(TorrentTesterEvent.Start)
            advanceUntilIdle()

            fakeEngine.customFlow = null
            viewModel.onEvent(TorrentTesterEvent.AllowMeteredOnce)
            advanceUntilIdle()

            viewModel.onEvent(TorrentTesterEvent.Stop)
            advanceUntilIdle()

            viewModel.onEvent(TorrentTesterEvent.InputChanged(validMagnet))
            viewModel.onEvent(TorrentTesterEvent.Start)
            advanceUntilIdle()

            assertEquals(3, fakeEngine.prepareCalls.size)
            assertFalse(fakeEngine.prepareCalls[2].allowMetered)
        }

    @Test
    fun V15_allowMeteredOnceWithNoStoredRequest_noEngineCallNoCrash() =
        runTest(testDispatcher) {
            viewModel.onEvent(TorrentTesterEvent.AllowMeteredOnce)
            advanceUntilIdle()

            assertTrue(fakeEngine.prepareCalls.isEmpty())
            assertFalse(viewModel.uiState.value.isBlockedMetered)
        }

    @Test
    fun V16_initialState_showsSizeFromCacheAndPrefsValues() =
        runTest(testDispatcher) {
            advanceUntilIdle()
            assertEquals(1024L * 1024L, viewModel.uiState.value.cacheSizeBytes)
            assertFalse(viewModel.uiState.value.keepDataAfterStop)
            assertEquals(2, viewModel.uiState.value.cacheLimitGb)
            assertEquals(1, fakeCache.cacheSizeBytesCallCount)
        }

    @Test
    fun V17_clearCache_callsClearCacheOnceThenRefreshesSize() =
        runTest(testDispatcher) {
            advanceUntilIdle()
            assertEquals(1, fakeCache.cacheSizeBytesCallCount)

            viewModel.onEvent(TorrentTesterEvent.ClearCache)
            advanceUntilIdle()

            assertEquals(1, fakeCache.clearCacheCallCount)
            assertEquals(0L, viewModel.uiState.value.cacheSizeBytes)
            assertEquals(2, fakeCache.cacheSizeBytesCallCount)
        }

    @Test
    fun V18_setKeepAfterStopTrue_prefsClearCacheOnExitBecomesFalse() =
        runTest(testDispatcher) {
            advanceUntilIdle()
            assertFalse(viewModel.uiState.value.keepDataAfterStop)

            viewModel.onEvent(TorrentTesterEvent.SetKeepAfterStop(true))
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.keepDataAfterStop)
            assertFalse(fakePrefs.clearCacheOnExit.first())
        }

    @Test
    fun V19_setCacheLimit5_prefsCacheLimitGbBecomes5() =
        runTest(testDispatcher) {
            advanceUntilIdle()
            assertEquals(2, viewModel.uiState.value.cacheLimitGb)

            viewModel.onEvent(TorrentTesterEvent.SetCacheLimit(5))
            advanceUntilIdle()

            assertEquals(5, viewModel.uiState.value.cacheLimitGb)
            assertEquals(5, fakePrefs.cacheLimitGb.first())
        }

    @Test
    fun V20_sessionFlowEnding_exactlyOneExtraRefresh() =
        runTest(testDispatcher) {
            val vm = TorrentTesterViewModel(fakeEngine, fakeCache, fakePrefs, testDispatcher)
            runCurrent()
            val initialRefreshes = fakeCache.cacheSizeBytesCallCount

            vm.onEvent(TorrentTesterEvent.InputChanged(validMagnet))
            fakeEngine.customFlow = flowOf(TorrentState.Buffering(10, 1000L, 5))
            vm.onEvent(TorrentTesterEvent.Start)
            runCurrent()

            val afterEndRefreshes = fakeCache.cacheSizeBytesCallCount
            assertEquals(initialRefreshes + 1, afterEndRefreshes)

            advanceTimeBy(5_000L)
            runCurrent()
            assertEquals(afterEndRefreshes, fakeCache.cacheSizeBytesCallCount)
        }

    @Test
    fun V21_sessionRunning_liveSizeRefreshesEachSecond() =
        runTest(testDispatcher) {
            val vm = TorrentTesterViewModel(fakeEngine, fakeCache, fakePrefs, testDispatcher)
            runCurrent()
            vm.onEvent(TorrentTesterEvent.InputChanged(validMagnet))
            vm.onEvent(TorrentTesterEvent.Start)
            runCurrent()

            fakeCache.sizeBytes = 100L
            advanceTimeBy(1_000L)
            runCurrent()
            assertEquals(100L, vm.uiState.value.cacheSizeBytes)

            fakeCache.sizeBytes = 200L
            advanceTimeBy(1_000L)
            runCurrent()
            assertEquals(200L, vm.uiState.value.cacheSizeBytes)

            fakeCache.sizeBytes = 300L
            advanceTimeBy(1_000L)
            runCurrent()
            assertEquals(300L, vm.uiState.value.cacheSizeBytes)

            vm.onEvent(TorrentTesterEvent.Stop)
            runCurrent()
        }

    @Test
    fun V22_sessionEnds_exactlyOneRefreshAfterEndNoMoreAfterFiveSeconds() =
        runTest(testDispatcher) {
            val vm = TorrentTesterViewModel(fakeEngine, fakeCache, fakePrefs, testDispatcher)
            runCurrent()
            vm.onEvent(TorrentTesterEvent.InputChanged(validMagnet))
            fakeEngine.customFlow = flowOf(TorrentState.Buffering(10, 1000L, 5))
            vm.onEvent(TorrentTesterEvent.Start)
            runCurrent()

            val afterEndRefreshes = fakeCache.cacheSizeBytesCallCount
            advanceTimeBy(5_000L)
            runCurrent()
            assertEquals(afterEndRefreshes, fakeCache.cacheSizeBytesCallCount)
        }

    @Test
    fun V23_nothingRunning_noRefreshTicks() =
        runTest(testDispatcher) {
            val vm = TorrentTesterViewModel(fakeEngine, fakeCache, fakePrefs, testDispatcher)
            runCurrent()
            val initialRefreshes = fakeCache.cacheSizeBytesCallCount
            assertFalse(vm.uiState.value.running)

            advanceTimeBy(5_000L)
            runCurrent()
            assertEquals(initialRefreshes, fakeCache.cacheSizeBytesCallCount)
        }

    @Test
    fun V24_startStopStartQuickly_onlyOneRefreshJobAlive() =
        runTest(testDispatcher) {
            val vm = TorrentTesterViewModel(fakeEngine, fakeCache, fakePrefs, testDispatcher)
            runCurrent()

            vm.onEvent(TorrentTesterEvent.InputChanged(validMagnet))
            vm.onEvent(TorrentTesterEvent.Start)
            advanceTimeBy(200L)
            runCurrent()

            vm.onEvent(TorrentTesterEvent.Stop)
            advanceTimeBy(200L)
            runCurrent()

            vm.onEvent(TorrentTesterEvent.InputChanged(validMagnet))
            vm.onEvent(TorrentTesterEvent.Start)
            runCurrent()

            val baselineRefreshes = fakeCache.cacheSizeBytesCallCount
            advanceTimeBy(3_000L)
            runCurrent()
            assertEquals(baselineRefreshes + 3, fakeCache.cacheSizeBytesCallCount)

            vm.onEvent(TorrentTesterEvent.Stop)
            runCurrent()
        }

    @Test
    fun V25_setKeepAfterStopTrue_uiStateKeepAfterStopIsTrueBeforePrefsWriteCompletes() =
        runTest(testDispatcher) {
            advanceUntilIdle()
            assertFalse(viewModel.uiState.value.keepDataAfterStop)

            val writeLatch = CompletableDeferred<Unit>()
            fakePrefs.onSetClearCacheOnExit = {
                writeLatch.await()
            }

            viewModel.onEvent(TorrentTesterEvent.SetKeepAfterStop(true))

            assertTrue(viewModel.uiState.value.keepDataAfterStop)

            writeLatch.complete(Unit)
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.keepDataAfterStop)
            assertFalse(fakePrefs.clearCacheOnExit.first())
        }

    private class FakeTorrentCache : TorrentCache {
        var sizeBytes: Long = 1024L * 1024L
        var clearCacheCallCount = 0
        var cacheSizeBytesCallCount = 0

        override suspend fun cacheSizeBytes(): Long {
            cacheSizeBytesCallCount++
            return sizeBytes
        }

        override suspend fun clearCache() {
            clearCacheCallCount++
            sizeBytes = 0L
        }
    }

    private class FakeTorrentPrefsRepository(
        initialWifiOnly: Boolean = false,
        initialCacheLimitGb: Int = 2,
        initialClearCacheOnExit: Boolean = true,
    ) : TorrentPrefsRepository {
        private val _wifiOnly = MutableStateFlow(initialWifiOnly)
        override val wifiOnly: Flow<Boolean> = _wifiOnly

        private val _cacheLimitGb = MutableStateFlow(initialCacheLimitGb)
        override val cacheLimitGb: Flow<Int> = _cacheLimitGb

        private val _clearCacheOnExit = MutableStateFlow(initialClearCacheOnExit)
        override val clearCacheOnExit: Flow<Boolean> = _clearCacheOnExit

        var onSetClearCacheOnExit: (suspend (Boolean) -> Unit)? = null

        override suspend fun setWifiOnly(value: Boolean) {
            _wifiOnly.value = value
        }

        override suspend fun setCacheLimitGb(value: Int) {
            _cacheLimitGb.value = value.coerceAtLeast(1)
        }

        override suspend fun setClearCacheOnExit(value: Boolean) {
            onSetClearCacheOnExit?.invoke(value)
            _clearCacheOnExit.value = value
        }
    }

    private class FakeTorrentEngine : TorrentEngine {
        val prepareCalls = mutableListOf<TorrentRequest>()
        val selectFileCalls = mutableListOf<Pair<String, Int>>()
        val stopCalls = mutableListOf<String>()
        val prepareFlow = MutableSharedFlow<TorrentState>()
        var customFlow: Flow<TorrentState>? = null
        var isCancelled = false

        override fun prepare(request: TorrentRequest): Flow<TorrentState> {
            prepareCalls.add(request)
            val custom = customFlow
            if (custom != null) {
                return custom
            }
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
            stopCalls.add(infoHash)
        }
    }
}
