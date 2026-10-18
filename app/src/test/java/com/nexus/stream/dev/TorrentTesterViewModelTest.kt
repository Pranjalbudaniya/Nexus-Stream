package com.nexus.stream.dev

import androidx.lifecycle.ViewModel
import com.nexus.stream.domain.model.AppError
import com.nexus.stream.domain.model.TorrentFailReason
import com.nexus.stream.domain.model.TorrentFile
import com.nexus.stream.domain.model.TorrentRequest
import com.nexus.stream.domain.model.TorrentState
import com.nexus.stream.domain.repository.TorrentEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
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
    private lateinit var viewModel: TorrentTesterViewModel

    private val validHash = "0123456789abcdef0123456789abcdef01234567"
    private val validTracker = "udp://t.example:1337/announce"
    private val validMagnet = "magnet:?xt=urn:btih:${validHash.uppercase()}&tr=udp%3A%2F%2Ft.example%3A1337%2Fannounce"

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = TorrentTesterViewModel(fakeEngine)
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
