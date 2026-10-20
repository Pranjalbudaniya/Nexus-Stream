package com.nexus.stream.data.torrent

import com.nexus.stream.domain.model.AppError
import com.nexus.stream.domain.model.TorrentFailReason
import com.nexus.stream.domain.model.TorrentRequest
import com.nexus.stream.domain.model.TorrentState
import com.nexus.stream.domain.repository.TorrentEngine
import com.nexus.stream.domain.repository.TorrentPrefsRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class GatedTorrentEngineTest {
    private class FakeTorrentEngine : TorrentEngine {
        val prepareCalls = mutableListOf<TorrentRequest>()
        val selectFileCalls = mutableListOf<Pair<String, Int>>()
        val stopCalls = mutableListOf<String>()
        var flowFactory: (TorrentRequest) -> Flow<TorrentState> = { flowOf(TorrentState.FetchingMetadata) }

        override fun prepare(request: TorrentRequest): Flow<TorrentState> {
            prepareCalls.add(request)
            return flowFactory(request)
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

    private class FakeNetworkStatusProvider(
        var status: NetworkStatus = NetworkStatus.UNMETERED,
    ) : NetworkStatusProvider {
        override fun current(): NetworkStatus = status
    }

    private class FakeTorrentPrefsRepository(
        initialWifiOnly: Boolean = true,
    ) : TorrentPrefsRepository {
        private val _wifiOnly = MutableStateFlow(initialWifiOnly)
        override val wifiOnly: Flow<Boolean> = _wifiOnly

        override suspend fun setWifiOnly(value: Boolean) {
            _wifiOnly.value = value
        }
    }

    private class FakeTorrentKeepAlive : TorrentKeepAlive {
        var acquireCount = 0
        var releaseCount = 0

        override fun acquire() {
            acquireCount++
        }

        override fun release() {
            releaseCount++
        }
    }

    @Test
    fun GE1_wifiOnlyMeteredAllowMeteredFalseFailsMeteredBlockedNoDelegateNoAcquire() =
        runTest {
            val delegate = FakeTorrentEngine()
            val network = FakeNetworkStatusProvider(NetworkStatus.METERED)
            val prefs = FakeTorrentPrefsRepository(initialWifiOnly = true)
            val keepAlive = FakeTorrentKeepAlive()
            val engine = GatedTorrentEngine(delegate, network, prefs, keepAlive)

            val states = engine.prepare(TorrentRequest("abc", allowMetered = false)).toList()

            assertEquals(listOf(TorrentState.Failed(AppError.MeteredBlocked)), states)
            assertEquals(0, delegate.prepareCalls.size)
            assertEquals(0, keepAlive.acquireCount)
        }

    @Test
    fun GE2_wifiOnlyMeteredAllowMeteredTrueDelegatedAcquiresOnce() =
        runTest {
            val delegate = FakeTorrentEngine()
            val network = FakeNetworkStatusProvider(NetworkStatus.METERED)
            val prefs = FakeTorrentPrefsRepository(initialWifiOnly = true)
            val keepAlive = FakeTorrentKeepAlive()
            val engine = GatedTorrentEngine(delegate, network, prefs, keepAlive)

            val states = engine.prepare(TorrentRequest("abc", allowMetered = true)).toList()

            assertEquals(1, delegate.prepareCalls.size)
            assertEquals(listOf(TorrentState.FetchingMetadata), states)
            assertEquals(1, keepAlive.acquireCount)
        }

    @Test
    fun GE3_wifiOnlyUnmeteredDelegated() =
        runTest {
            val delegate = FakeTorrentEngine()
            val network = FakeNetworkStatusProvider(NetworkStatus.UNMETERED)
            val prefs = FakeTorrentPrefsRepository(initialWifiOnly = true)
            val keepAlive = FakeTorrentKeepAlive()
            val engine = GatedTorrentEngine(delegate, network, prefs, keepAlive)

            val states = engine.prepare(TorrentRequest("abc")).toList()

            assertEquals(1, delegate.prepareCalls.size)
            assertEquals(listOf(TorrentState.FetchingMetadata), states)
        }

    @Test
    fun GE4_wifiOnlyFalseMeteredDelegated() =
        runTest {
            val delegate = FakeTorrentEngine()
            val network = FakeNetworkStatusProvider(NetworkStatus.METERED)
            val prefs = FakeTorrentPrefsRepository(initialWifiOnly = false)
            val keepAlive = FakeTorrentKeepAlive()
            val engine = GatedTorrentEngine(delegate, network, prefs, keepAlive)

            val states = engine.prepare(TorrentRequest("abc")).toList()

            assertEquals(1, delegate.prepareCalls.size)
            assertEquals(listOf(TorrentState.FetchingMetadata), states)
        }

    @Test
    fun GE5_wifiOnlyNoneDelegatedNotBlocked() =
        runTest {
            val delegate = FakeTorrentEngine()
            val network = FakeNetworkStatusProvider(NetworkStatus.NONE)
            val prefs = FakeTorrentPrefsRepository(initialWifiOnly = true)
            val keepAlive = FakeTorrentKeepAlive()
            val engine = GatedTorrentEngine(delegate, network, prefs, keepAlive)

            val states = engine.prepare(TorrentRequest("abc")).toList()

            assertEquals(1, delegate.prepareCalls.size)
            assertEquals(listOf(TorrentState.FetchingMetadata), states)
        }

    @Test
    fun GE6_delegateEmitsFailedFirstThenEndsPassedThroughNoAcquire() =
        runTest {
            val delegate = FakeTorrentEngine()
            delegate.flowFactory = {
                flowOf(TorrentState.Failed(AppError.Torrent(TorrentFailReason.INVALID_HASH)))
            }
            val network = FakeNetworkStatusProvider(NetworkStatus.UNMETERED)
            val prefs = FakeTorrentPrefsRepository(initialWifiOnly = true)
            val keepAlive = FakeTorrentKeepAlive()
            val engine = GatedTorrentEngine(delegate, network, prefs, keepAlive)

            val states = engine.prepare(TorrentRequest("abc")).toList()

            assertEquals(listOf(TorrentState.Failed(AppError.Torrent(TorrentFailReason.INVALID_HASH))), states)
            assertEquals(0, keepAlive.acquireCount)
            assertEquals(0, keepAlive.releaseCount)
        }

    @Test
    fun GE7_collectorCancelsAcquiresOnceOnFirstStateReleasesOnce() =
        runTest {
            val delegate = FakeTorrentEngine()
            val gate = CompletableDeferred<Unit>()
            delegate.flowFactory = {
                flow {
                    emit(TorrentState.FetchingMetadata)
                    emit(TorrentState.Buffering(10, 100L, 2))
                    emit(TorrentState.Ready(0, "video.mp4", 1000L))
                    gate.await()
                }
            }
            val network = FakeNetworkStatusProvider(NetworkStatus.UNMETERED)
            val prefs = FakeTorrentPrefsRepository(initialWifiOnly = true)
            val keepAlive = FakeTorrentKeepAlive()
            val engine = GatedTorrentEngine(delegate, network, prefs, keepAlive)

            val collected = mutableListOf<TorrentState>()
            val job =
                launch {
                    engine.prepare(TorrentRequest("abc")).collect {
                        collected.add(it)
                    }
                }
            testScheduler.advanceUntilIdle()

            assertEquals(3, collected.size)
            assertEquals(1, keepAlive.acquireCount)
            assertEquals(0, keepAlive.releaseCount)

            job.cancel()
            testScheduler.advanceUntilIdle()

            assertEquals(1, keepAlive.acquireCount)
            assertEquals(1, keepAlive.releaseCount)
        }

    @Test
    fun GE8_delegateFlowCompletesReleasesOnce() =
        runTest {
            val delegate = FakeTorrentEngine()
            delegate.flowFactory = {
                flowOf(TorrentState.FetchingMetadata, TorrentState.Ready(0, "a.mp4", 100L))
            }
            val network = FakeNetworkStatusProvider(NetworkStatus.UNMETERED)
            val prefs = FakeTorrentPrefsRepository(initialWifiOnly = true)
            val keepAlive = FakeTorrentKeepAlive()
            val engine = GatedTorrentEngine(delegate, network, prefs, keepAlive)

            val states = engine.prepare(TorrentRequest("abc")).toList()

            assertEquals(2, states.size)
            assertEquals(1, keepAlive.acquireCount)
            assertEquals(1, keepAlive.releaseCount)
        }

    @Test
    fun GE9_delegateThrowsAfterFetchingMetadataPropagatesAndReleasesOnce() =
        runTest {
            val delegate = FakeTorrentEngine()
            delegate.flowFactory = {
                flow {
                    emit(TorrentState.FetchingMetadata)
                    error("Delegate network crash")
                }
            }
            val network = FakeNetworkStatusProvider(NetworkStatus.UNMETERED)
            val prefs = FakeTorrentPrefsRepository(initialWifiOnly = true)
            val keepAlive = FakeTorrentKeepAlive()
            val engine = GatedTorrentEngine(delegate, network, prefs, keepAlive)

            try {
                engine.prepare(TorrentRequest("abc")).toList()
                fail("Expected exception")
            } catch (e: IllegalStateException) {
                assertEquals("Delegate network crash", e.message)
            }

            assertEquals(1, keepAlive.acquireCount)
            assertEquals(1, keepAlive.releaseCount)
        }

    @Test
    fun GE10_twoActiveHashesStopAllStopsBothNoneActiveNoCalls() =
        runTest {
            val delegate = FakeTorrentEngine()
            val gate1 = CompletableDeferred<Unit>()
            val gate2 = CompletableDeferred<Unit>()
            delegate.flowFactory = { req ->
                flow {
                    emit(TorrentState.FetchingMetadata)
                    if (req.infoHash == "hash1") gate1.await() else gate2.await()
                }
            }
            val network = FakeNetworkStatusProvider(NetworkStatus.UNMETERED)
            val prefs = FakeTorrentPrefsRepository(initialWifiOnly = true)
            val keepAlive = FakeTorrentKeepAlive()
            val engine = GatedTorrentEngine(delegate, network, prefs, keepAlive)

            val job1 = launch { engine.prepare(TorrentRequest("hash1")).toList() }
            val job2 = launch { engine.prepare(TorrentRequest("hash2")).toList() }
            testScheduler.advanceUntilIdle()

            engine.stopAll()
            assertTrue(delegate.stopCalls.contains("hash1"))
            assertTrue(delegate.stopCalls.contains("hash2"))
            assertEquals(2, delegate.stopCalls.size)

            // Cancel both sessions
            job1.cancel()
            job2.cancel()
            testScheduler.advanceUntilIdle()

            delegate.stopCalls.clear()
            engine.stopAll()
            assertEquals(0, delegate.stopCalls.size)
        }

    @Test
    fun GE11_sameHashTwiceSecondStartsBeforeFirstEndsStopAllStopsUntilBothEnd() =
        runTest {
            val delegate = FakeTorrentEngine()
            val gate1 = CompletableDeferred<Unit>()
            val gate2 = CompletableDeferred<Unit>()
            var callCount = 0
            delegate.flowFactory = {
                flow {
                    emit(TorrentState.FetchingMetadata)
                    callCount++
                    if (callCount == 1) gate1.await() else gate2.await()
                }
            }
            val network = FakeNetworkStatusProvider(NetworkStatus.UNMETERED)
            val prefs = FakeTorrentPrefsRepository(initialWifiOnly = true)
            val keepAlive = FakeTorrentKeepAlive()
            val engine = GatedTorrentEngine(delegate, network, prefs, keepAlive)

            val job1 = launch { engine.prepare(TorrentRequest("same_hash")).toList() }
            testScheduler.advanceUntilIdle()

            val job2 = launch { engine.prepare(TorrentRequest("same_hash")).toList() }
            testScheduler.advanceUntilIdle()

            // First session completes
            job1.cancel()
            testScheduler.advanceUntilIdle()

            // stopAll should still stop the hash because session 2 is active
            engine.stopAll()
            assertEquals(listOf("same_hash"), delegate.stopCalls)

            // Second session completes
            job2.cancel()
            testScheduler.advanceUntilIdle()

            delegate.stopCalls.clear()
            engine.stopAll()
            assertEquals(0, delegate.stopCalls.size)
        }

    @Test
    fun GE12_untrimmedUppercaseRequestTrackedAndStoppedAsTrimmedLowercase() =
        runTest {
            val delegate = FakeTorrentEngine()
            val gate = CompletableDeferred<Unit>()
            delegate.flowFactory = {
                flow {
                    emit(TorrentState.FetchingMetadata)
                    gate.await()
                }
            }
            val network = FakeNetworkStatusProvider(NetworkStatus.UNMETERED)
            val prefs = FakeTorrentPrefsRepository(initialWifiOnly = true)
            val keepAlive = FakeTorrentKeepAlive()
            val engine = GatedTorrentEngine(delegate, network, prefs, keepAlive)

            val job =
                launch {
                    engine.prepare(TorrentRequest("  ABCDEF1234567890ABCDEF1234567890ABCDEF12  ")).toList()
                }
            testScheduler.advanceUntilIdle()

            engine.stopAll()
            assertEquals(listOf("abcdef1234567890abcdef1234567890abcdef12"), delegate.stopCalls)

            job.cancel()
        }

    @Test
    fun GE13_selectFileAndStopReachDelegateUnchanged() =
        runTest {
            val delegate = FakeTorrentEngine()
            val network = FakeNetworkStatusProvider(NetworkStatus.UNMETERED)
            val prefs = FakeTorrentPrefsRepository(initialWifiOnly = true)
            val keepAlive = FakeTorrentKeepAlive()
            val engine = GatedTorrentEngine(delegate, network, prefs, keepAlive)

            engine.selectFile("hash1", 4)
            assertEquals(listOf("hash1" to 4), delegate.selectFileCalls)

            engine.stop("hash1")
            assertEquals(listOf("hash1"), delegate.stopCalls)
        }

    @Test
    fun GE14_prefsChangeBetweenTwoPrepareCallsUsesNewValue() =
        runTest {
            val delegate = FakeTorrentEngine()
            val network = FakeNetworkStatusProvider(NetworkStatus.METERED)
            val prefs = FakeTorrentPrefsRepository(initialWifiOnly = true)
            val keepAlive = FakeTorrentKeepAlive()
            val engine = GatedTorrentEngine(delegate, network, prefs, keepAlive)

            val states1 = engine.prepare(TorrentRequest("abc")).toList()
            assertEquals(listOf(TorrentState.Failed(AppError.MeteredBlocked)), states1)

            prefs.setWifiOnly(false)
            val states2 = engine.prepare(TorrentRequest("abc")).toList()
            assertEquals(listOf(TorrentState.FetchingMetadata), states2)
        }

    @Test
    fun GE15_statesReachCollectorUnmodifiedAndInOrder() =
        runTest {
            val ready = TorrentState.Ready(1, "movie.mkv", 5000L, localUrl = "http://127.0.0.1:8080/stream")
            val expectedStates =
                listOf(
                    TorrentState.FetchingMetadata,
                    TorrentState.Buffering(50, 1000L, 10),
                    ready,
                )
            val delegate = FakeTorrentEngine()
            delegate.flowFactory = { flowOf(*expectedStates.toTypedArray()) }
            val network = FakeNetworkStatusProvider(NetworkStatus.UNMETERED)
            val prefs = FakeTorrentPrefsRepository(initialWifiOnly = true)
            val keepAlive = FakeTorrentKeepAlive()
            val engine = GatedTorrentEngine(delegate, network, prefs, keepAlive)

            val states = engine.prepare(TorrentRequest("abc")).toList()

            assertEquals(expectedStates, states)
            assertEquals("http://127.0.0.1:8080/stream", (states[2] as TorrentState.Ready).localUrl)
        }
}
