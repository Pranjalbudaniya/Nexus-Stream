package com.nexus.stream.data.torrent

import app.cash.turbine.test
import com.nexus.stream.domain.model.AppError
import com.nexus.stream.domain.model.EpisodeHint
import com.nexus.stream.domain.model.TorrentConfig
import com.nexus.stream.domain.model.TorrentFailReason
import com.nexus.stream.domain.model.TorrentFile
import com.nexus.stream.domain.model.TorrentRequest
import com.nexus.stream.domain.model.TorrentState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultTorrentEngineTest {
    private val validHash = "0123456789abcdef0123456789abcdef01234567"

    private class FakeTorrentBackend : TorrentBackend {
        val callLog: MutableList<String> = Collections.synchronizedList(mutableListOf<String>())
        val metadataDeferredMap = mutableMapOf<String, CompletableDeferred<TorrentMetadata>>()
        var metadataResult: TorrentMetadata? = null
        var awaitMetadataError: Exception? = null

        val snapshotQueue = mutableListOf<TorrentSnapshot>()
        var defaultSnapshot = TorrentSnapshot(piecesHave = 0, downloadSpeedBps = 0L, peers = 0)
        var snapshotError: Exception? = null

        var lastPrioritizeIndex: Int? = null
        var lastPrioritizePieces: List<Int>? = null

        override suspend fun add(
            infoHash: String,
            magnetUri: String,
        ) {
            callLog.add("add:$infoHash:$magnetUri")
        }

        override suspend fun awaitMetadata(infoHash: String): TorrentMetadata {
            callLog.add("awaitMetadata:$infoHash")
            awaitMetadataError?.let { throw it }
            val deferred = metadataDeferredMap.getOrPut(infoHash) { CompletableDeferred() }
            metadataResult?.let { deferred.complete(it) }
            return deferred.await()
        }

        override suspend fun prioritize(
            infoHash: String,
            fileIndex: Int,
            pieces: List<Int>,
        ) {
            callLog.add("prioritize:$infoHash:$fileIndex:$pieces")
            lastPrioritizeIndex = fileIndex
            lastPrioritizePieces = pieces
        }

        override suspend fun snapshot(
            infoHash: String,
            pieces: List<Int>,
        ): TorrentSnapshot {
            callLog.add("snapshot:$infoHash")
            snapshotError?.let { throw it }
            return if (snapshotQueue.isNotEmpty()) snapshotQueue.removeAt(0) else defaultSnapshot
        }

        val removedKept: MutableList<Pair<String, Boolean?>> =
            Collections.synchronizedList(mutableListOf())

        override suspend fun remove(
            infoHash: String,
            keep: Boolean?,
        ) {
            callLog.add("remove:$infoHash")
            removedKept.add(infoHash to keep)
        }
    }

    private fun sampleMetadata(
        files: List<BackendFile>,
        pieceLength: Long = 2L * 1024 * 1024,
    ): TorrentMetadata = TorrentMetadata(files = files, pieceLength = pieceLength)

    @Test
    fun T1_invalidHashLengthOrCharacters_failsWithoutCallingBackend() =
        runTest {
            val backend = FakeTorrentBackend()
            val engine = DefaultTorrentEngine(backend)

            // 39 chars
            val shortReq = TorrentRequest(infoHash = "0123456789abcdef0123456789abcdef0123456")
            engine.prepare(shortReq).test {
                val item = awaitItem()
                assertTrue(
                    item is TorrentState.Failed && item.error == AppError.Torrent(TorrentFailReason.INVALID_HASH),
                )
                awaitComplete()
            }

            // 40 chars with non-hex
            val nonHexReq = TorrentRequest(infoHash = "0123456789abcdef0123456789abcdef0123456g")
            engine.prepare(nonHexReq).test {
                val item = awaitItem()
                assertTrue(
                    item is TorrentState.Failed && item.error == AppError.Torrent(TorrentFailReason.INVALID_HASH),
                )
                awaitComplete()
            }

            assertTrue(backend.callLog.isEmpty())
        }

    @Test
    fun T2_happyPathBufferingToReady_staysOpenUntilCancelledAndRemovesFiles() =
        runTest {
            val backend = FakeTorrentBackend()
            val meta =
                sampleMetadata(
                    files =
                        listOf(
                            BackendFile(0, "a.mkv", 6L * 1024 * 1024, 0L),
                            BackendFile(1, "b.mkv", 6L * 1024 * 1024, 6L * 1024 * 1024),
                        ),
                    pieceLength = 2L * 1024 * 1024,
                )
            backend.metadataResult = meta
            backend.snapshotQueue.addAll(
                listOf(
                    TorrentSnapshot(piecesHave = 0, downloadSpeedBps = 1000L, peers = 5),
                    TorrentSnapshot(piecesHave = 1, downloadSpeedBps = 2000L, peers = 6),
                    TorrentSnapshot(piecesHave = 2, downloadSpeedBps = 3000L, peers = 7),
                    TorrentSnapshot(piecesHave = 3, downloadSpeedBps = 4000L, peers = 8),
                ),
            )

            val engine = DefaultTorrentEngine(backend)
            val req = TorrentRequest(infoHash = validHash, fileIdx = 1)

            engine.prepare(req).test {
                assertEquals(TorrentState.FetchingMetadata, awaitItem())
                assertEquals(TorrentState.Buffering(0, 1000L, 5), awaitItem())
                assertEquals(TorrentState.Buffering(33, 2000L, 6), awaitItem())
                assertEquals(TorrentState.Buffering(66, 3000L, 7), awaitItem())
                assertEquals(TorrentState.Ready(1, "b.mkv", 6L * 1024 * 1024), awaitItem())

                cancelAndIgnoreRemainingEvents()
            }

            runCurrent()
            assertEquals(1, backend.callLog.count { it.startsWith("remove:") })
        }

    @Test
    fun T3_uppercaseHashAndTrackerFormatting_normalizesAndEncodes() =
        runTest {
            val backend = FakeTorrentBackend()
            backend.metadataResult = sampleMetadata(listOf(BackendFile(0, "a.mkv", 1000L, 0L)))
            backend.defaultSnapshot = TorrentSnapshot(1, 0L, 0)
            val engine = DefaultTorrentEngine(backend)

            val upperHash = "0123456789ABCDEF0123456789ABCDEF01234567"
            val trackers =
                listOf(
                    "udp://t1.example:1337/announce",
                    "udp://t1.example:1337/announce",
                    "ftp://example.com/announce",
                    "   ",
                    "udp://t2.example:1337/announce",
                )

            engine.prepare(TorrentRequest(infoHash = upperHash, trackers = trackers)).test {
                cancelAndIgnoreRemainingEvents()
            }

            val addCall = backend.callLog.first { it.startsWith("add:") }
            assertTrue(addCall.startsWith("add:$validHash:"))
            assertTrue(addCall.contains("&tr=udp%3A%2F%2Ft1.example%3A1337%2Fannounce"))
            assertTrue(addCall.contains("&tr=udp%3A%2F%2Ft2.example%3A1337%2Fannounce"))
            assertFalse(addCall.contains("ftp://"))

            val singleMagnet = buildMagnet(validHash, listOf("udp://t.example:1337/announce"))
            assertEquals("magnet:?xt=urn:btih:$validHash&tr=udp%3A%2F%2Ft.example%3A1337%2Fannounce", singleMagnet)
        }

    @Test
    fun T4_metadataTimeout_emitsFailedAndRemoves() =
        runTest {
            val backend = FakeTorrentBackend()
            val engine = DefaultTorrentEngine(backend)

            engine.prepare(TorrentRequest(infoHash = validHash)).test {
                assertEquals(TorrentState.FetchingMetadata, awaitItem())
                advanceTimeBy(TorrentConfig.METADATA_TIMEOUT_MS)
                val failed = awaitItem()
                assertTrue(
                    failed is TorrentState.Failed &&
                        failed.error == AppError.Torrent(TorrentFailReason.METADATA_TIMEOUT),
                )
                awaitComplete()
            }

            assertTrue(backend.callLog.contains("remove:$validHash"))
        }

    @Test
    fun T5_hintMatchesPackFile_prioritizesMatchedFileWithoutNeedsFilePick() =
        runTest {
            val backend = FakeTorrentBackend()
            backend.metadataResult =
                sampleMetadata(
                    listOf(
                        BackendFile(0, "Show.S01E01.mkv", 1000L, 0L),
                        BackendFile(1, "Show.S01E02.mkv", 1000L, 1000L),
                        BackendFile(2, "Show.S01E03.mkv", 1000L, 2000L),
                    ),
                )
            backend.defaultSnapshot = TorrentSnapshot(100, 0L, 0)
            val engine = DefaultTorrentEngine(backend)

            val req = TorrentRequest(infoHash = validHash, episodeHint = EpisodeHint(1, 2))
            engine.prepare(req).test {
                assertEquals(TorrentState.FetchingMetadata, awaitItem())
                val ready = awaitItem()
                assertTrue(ready is TorrentState.Ready && ready.fileIndex == 1)
                cancelAndIgnoreRemainingEvents()
            }

            assertEquals(1, backend.lastPrioritizeIndex)
        }

    @Test
    fun T6_hintMatchesNothingInMultiVideo_emitsNeedsFilePickAndSelects() =
        runTest {
            val backend = FakeTorrentBackend()
            backend.metadataResult =
                sampleMetadata(
                    listOf(
                        BackendFile(0, "Show.S01E05.mkv", 100L, 0L),
                        BackendFile(1, "Show.S01E06.mkv", 300L, 100L),
                        BackendFile(2, "Show.S01E07.mkv", 200L, 400L),
                    ),
                )
            backend.defaultSnapshot = TorrentSnapshot(100, 0L, 0)
            val engine = DefaultTorrentEngine(backend)

            val req = TorrentRequest(infoHash = validHash, episodeHint = EpisodeHint(1, 2))
            engine.prepare(req).test {
                assertEquals(TorrentState.FetchingMetadata, awaitItem())
                val needsPick = awaitItem() as TorrentState.NeedsFilePick
                assertEquals(listOf(1, 2, 0), needsPick.files.map { it.index })

                engine.selectFile(validHash, 2)
                val ready = awaitItem() as TorrentState.Ready
                assertEquals(2, ready.fileIndex)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun T7_selectFileWithUnOfferedIndex_ignoredAndStaysWaiting() =
        runTest {
            val backend = FakeTorrentBackend()
            backend.metadataResult =
                sampleMetadata(
                    listOf(
                        BackendFile(0, "Show.S01E05.mkv", 100L, 0L),
                        BackendFile(1, "Show.S01E06.mkv", 200L, 100L),
                    ),
                )
            val engine = DefaultTorrentEngine(backend)

            val req = TorrentRequest(infoHash = validHash, episodeHint = EpisodeHint(1, 2))
            engine.prepare(req).test {
                assertEquals(TorrentState.FetchingMetadata, awaitItem())
                awaitItem() // NeedsFilePick

                engine.selectFile(validHash, 99)
                runCurrent()
                expectNoEvents()

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun T8_onlyNonVideoFiles_failsNoVideoFileAndRemoves() =
        runTest {
            val backend = FakeTorrentBackend()
            backend.metadataResult =
                sampleMetadata(
                    listOf(
                        BackendFile(0, "info.nfo", 100L, 0L),
                        BackendFile(1, "readme.txt", 100L, 100L),
                    ),
                )
            val engine = DefaultTorrentEngine(backend)

            engine.prepare(TorrentRequest(infoHash = validHash)).test {
                assertEquals(TorrentState.FetchingMetadata, awaitItem())
                val failed = awaitItem()
                assertTrue(
                    failed is TorrentState.Failed && failed.error == AppError.Torrent(TorrentFailReason.NO_VIDEO_FILE),
                )
                awaitComplete()
            }

            assertTrue(backend.callLog.contains("remove:$validHash"))
        }

    @Test
    fun T9_outOfRangeFileIdx_ignoredAndPicksByHintOrLargest() =
        runTest {
            val backend = FakeTorrentBackend()
            backend.metadataResult =
                sampleMetadata(
                    listOf(
                        BackendFile(0, "Show.S01E01.mkv", 100L, 0L),
                        BackendFile(1, "Show.S01E02.mkv", 200L, 100L),
                        BackendFile(2, "Show.S01E03.mkv", 150L, 300L),
                    ),
                )
            backend.defaultSnapshot = TorrentSnapshot(100, 0L, 0)
            val engine = DefaultTorrentEngine(backend)

            val req = TorrentRequest(infoHash = validHash, fileIdx = 99, episodeHint = EpisodeHint(1, 2))
            engine.prepare(req).test {
                assertEquals(TorrentState.FetchingMetadata, awaitItem())
                val ready = awaitItem() as TorrentState.Ready
                assertEquals(1, ready.fileIndex)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun T10_prioritizeArguments_passedInHeadThenTailOrder() =
        runTest {
            val backend = FakeTorrentBackend()
            backend.metadataResult =
                sampleMetadata(
                    files = listOf(BackendFile(0, "movie.mkv", 10L * 1024 * 1024, 0L)),
                    pieceLength = 2L * 1024 * 1024,
                )
            backend.defaultSnapshot = TorrentSnapshot(100, 0L, 0)
            val engine = DefaultTorrentEngine(backend)

            engine.prepare(TorrentRequest(infoHash = validHash, fileIdx = 0)).test {
                cancelAndIgnoreRemainingEvents()
            }

            assertEquals(0, backend.lastPrioritizeIndex)
            assertEquals(listOf(0, 1, 2, 3, 4), backend.lastPrioritizePieces)
            assertEquals(1, backend.callLog.count { it.startsWith("prioritize:") })
        }

    @Test
    fun T11_snapshotUnchanged_emitsOnlyOnce() =
        runTest {
            val backend = FakeTorrentBackend()
            backend.metadataResult = sampleMetadata(listOf(BackendFile(0, "movie.mkv", 10L * 1024 * 1024, 0L)))
            backend.snapshotQueue.addAll(
                listOf(
                    TorrentSnapshot(piecesHave = 1, downloadSpeedBps = 100L, peers = 2),
                    TorrentSnapshot(piecesHave = 1, downloadSpeedBps = 100L, peers = 2),
                    TorrentSnapshot(piecesHave = 1, downloadSpeedBps = 100L, peers = 2),
                    TorrentSnapshot(piecesHave = 5, downloadSpeedBps = 100L, peers = 2),
                ),
            )
            val engine = DefaultTorrentEngine(backend)

            engine.prepare(TorrentRequest(infoHash = validHash, fileIdx = 0)).test {
                assertEquals(TorrentState.FetchingMetadata, awaitItem())
                assertEquals(TorrentState.Buffering(20, 100L, 2), awaitItem())
                advanceTimeBy(TorrentConfig.POLL_INTERVAL_MS * 4)
                assertEquals(TorrentState.Ready(0, "movie.mkv", 10L * 1024 * 1024), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun T12_snapshotPartialProgress_bufferingCarriesSpeedAndPeers() =
        runTest {
            val backend = FakeTorrentBackend()
            backend.metadataResult =
                sampleMetadata(
                    files = listOf(BackendFile(0, "movie.mkv", 8L * 1024 * 1024, 0L)),
                    pieceLength = 2L * 1024 * 1024,
                )
            backend.snapshotQueue.add(TorrentSnapshot(piecesHave = 2, downloadSpeedBps = 54321L, peers = 12))
            backend.snapshotQueue.add(TorrentSnapshot(piecesHave = 4, downloadSpeedBps = 54321L, peers = 12))
            val engine = DefaultTorrentEngine(backend)

            engine.prepare(TorrentRequest(infoHash = validHash, fileIdx = 0)).test {
                assertEquals(TorrentState.FetchingMetadata, awaitItem())
                assertEquals(TorrentState.Buffering(50, 54321L, 12), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun T13_awaitMetadataThrowsBackendException_emitsFailedAndRemoves() =
        runTest {
            val backend = FakeTorrentBackend()
            backend.awaitMetadataError = TorrentBackendException(TorrentFailReason.ENGINE_UNAVAILABLE)
            val engine = DefaultTorrentEngine(backend)

            engine.prepare(TorrentRequest(infoHash = validHash)).test {
                assertEquals(TorrentState.FetchingMetadata, awaitItem())
                val failed = awaitItem()
                assertTrue(
                    failed is TorrentState.Failed &&
                        failed.error == AppError.Torrent(TorrentFailReason.ENGINE_UNAVAILABLE),
                )
                awaitComplete()
            }

            assertTrue(backend.callLog.contains("remove:$validHash"))
        }

    @Test
    fun T14_cancellationExceptionPropagatesWithoutFailedState() =
        runTest {
            val backend = FakeTorrentBackend()
            backend.awaitMetadataError = CancellationException("backend cancelled")
            val engine = DefaultTorrentEngine(backend)

            val emitted = mutableListOf<TorrentState>()
            var thrown: Throwable? = null
            try {
                engine.prepare(TorrentRequest(infoHash = validHash)).collect {
                    emitted.add(it)
                }
            } catch (e: Throwable) {
                thrown = e
            }

            assertTrue(thrown is CancellationException)
            assertEquals("backend cancelled", thrown?.message)
            assertEquals(listOf(TorrentState.FetchingMetadata), emitted)
            assertFalse(emitted.any { it is TorrentState.Failed })
        }

    @Test
    fun T15_stopSessionLifecycleAndDuplicateStop_flowCompletesWithoutFailed() =
        runTest {
            val backend = FakeTorrentBackend()
            val engine = DefaultTorrentEngine(backend)

            // 1. stop while FetchingMetadata
            val job1 =
                launch {
                    engine.prepare(TorrentRequest(infoHash = validHash)).test {
                        assertEquals(TorrentState.FetchingMetadata, awaitItem())
                        awaitComplete()
                    }
                }
            runCurrent()
            engine.stop(validHash)
            job1.join()
            assertEquals(1, backend.callLog.count { it == "remove:$validHash" })

            // 2. stop while Ready
            backend.metadataResult = sampleMetadata(listOf(BackendFile(0, "movie.mkv", 1000L, 0L)))
            backend.defaultSnapshot = TorrentSnapshot(100, 0L, 0)
            val job2 =
                launch {
                    engine.prepare(TorrentRequest(infoHash = validHash, fileIdx = 0)).test {
                        assertEquals(TorrentState.FetchingMetadata, awaitItem())
                        assertTrue(awaitItem() is TorrentState.Ready)
                        awaitComplete()
                    }
                }
            runCurrent()
            engine.stop(validHash)
            job2.join()
            assertEquals(2, backend.callLog.count { it == "remove:$validHash" })

            // 3. duplicate stop and unknown hash are no-ops
            engine.stop(validHash)
            engine.stop("0000000000000000000000000000000000000000")
            assertEquals(2, backend.callLog.count { it == "remove:$validHash" })
        }

    @Test
    fun T16_prepareSameHashConcurrently_stopsOldAndRemovesBeforeNewAdd() =
        runTest {
            val backend = FakeTorrentBackend()
            val engine = DefaultTorrentEngine(backend)

            var flow1Completed = false
            val job1 =
                launch {
                    engine.prepare(TorrentRequest(infoHash = validHash)).test {
                        assertEquals(TorrentState.FetchingMetadata, awaitItem())
                        awaitComplete()
                        flow1Completed = true
                    }
                }
            runCurrent()

            backend.metadataResult = sampleMetadata(listOf(BackendFile(0, "movie.mkv", 1000L, 0L)))
            backend.defaultSnapshot = TorrentSnapshot(100, 0L, 0)

            val job2 =
                launch {
                    engine.prepare(TorrentRequest(infoHash = validHash, fileIdx = 0)).test {
                        assertEquals(TorrentState.FetchingMetadata, awaitItem())
                        cancelAndIgnoreRemainingEvents()
                    }
                }
            runCurrent()
            job1.join()
            job2.join()

            assertTrue(flow1Completed)
            val removeIdx = backend.callLog.indexOfFirst { it.startsWith("remove:$validHash") }
            val secondAddIdx = backend.callLog.indexOfLast { it.startsWith("add:$validHash") }
            assertTrue(removeIdx in 0..<secondAddIdx)
        }

    @Test
    fun T17_snapshotThrowsRuntimeException_failsWithAppErrorUnknownAndRemoves() =
        runTest {
            val backend = FakeTorrentBackend()
            backend.metadataResult = sampleMetadata(listOf(BackendFile(0, "movie.mkv", 10L * 1024 * 1024, 0L)))
            backend.snapshotError = RuntimeException("io error")
            val engine = DefaultTorrentEngine(backend)

            engine.prepare(TorrentRequest(infoHash = validHash, fileIdx = 0)).test {
                assertEquals(TorrentState.FetchingMetadata, awaitItem())
                val failed = awaitItem()
                assertTrue(failed is TorrentState.Failed && failed.error is AppError.Unknown)
                awaitComplete()
            }

            assertTrue(backend.callLog.contains("remove:$validHash"))
        }

    @Test
    fun EN1_ready_callsOnReady_withTheRequestKey() =
        runTest {
            val backend = FakeTorrentBackend()
            val coordinator: CacheRetentionCoordinator = mockk(relaxed = true)
            val engine = DefaultTorrentEngine(backend, coordinator)
            backend.metadataResult = sampleMetadata(listOf(BackendFile(0, "movie.mkv", 1000L, 0L)))
            backend.defaultSnapshot = TorrentSnapshot(100, 0L, 0)

            val job =
                launch {
                    engine.prepare(TorrentRequest(infoHash = validHash, fileIdx = 0, cacheKey = "k")).test {
                        assertEquals(TorrentState.FetchingMetadata, awaitItem())
                        val ready = awaitItem()
                        assertTrue(ready is TorrentState.Ready)
                        cancelAndIgnoreRemainingEvents()
                    }
                }
            runCurrent()
            job.join()

            coVerify(exactly = 1) { coordinator.onReady("k", validHash, 0, 1000L) }
        }

    @Test
    fun EN2_end_callsOnStop_beforeTheBackendRemove() =
        runTest {
            val callOrder = Collections.synchronizedList(mutableListOf<String>())
            val backend = FakeTorrentBackend()
            val coordinator: CacheRetentionCoordinator = mockk(relaxed = true)
            coEvery { coordinator.onStop(any(), any(), any(), any(), any()) } coAnswers {
                callOrder.add("onStop")
                true
            }
            val engine = DefaultTorrentEngine(backend, coordinator)
            backend.metadataResult = sampleMetadata(listOf(BackendFile(0, "movie.mkv", 1000L, 0L)))
            backend.defaultSnapshot = TorrentSnapshot(100, 0L, 0)

            val job =
                launch {
                    engine.prepare(TorrentRequest(infoHash = validHash, fileIdx = 0, cacheKey = "k")).test {
                        assertEquals(TorrentState.FetchingMetadata, awaitItem())
                        val ready = awaitItem()
                        assertTrue(ready is TorrentState.Ready)
                        cancelAndIgnoreRemainingEvents()
                    }
                }
            runCurrent()
            job.join()

            assertEquals(listOf("onStop"), callOrder)
            assertEquals(listOf(validHash to true), backend.removedKept)
        }

    @Test
    fun EN3_noCacheKey_noCoordinatorCalls_oldBehaviour() =
        runTest {
            val backend = FakeTorrentBackend()
            val coordinator: CacheRetentionCoordinator = mockk(relaxed = true)
            val engine = DefaultTorrentEngine(backend, coordinator)
            backend.metadataResult = sampleMetadata(listOf(BackendFile(0, "movie.mkv", 1000L, 0L)))
            backend.defaultSnapshot = TorrentSnapshot(100, 0L, 0)

            val job =
                launch {
                    engine.prepare(TorrentRequest(infoHash = validHash, fileIdx = 0, cacheKey = null)).test {
                        assertEquals(TorrentState.FetchingMetadata, awaitItem())
                        val ready = awaitItem()
                        assertTrue(ready is TorrentState.Ready)
                        cancelAndIgnoreRemainingEvents()
                    }
                }
            runCurrent()
            job.join()

            coVerify(exactly = 0) { coordinator.onReady(any(), any(), any(), any()) }
            coVerify(exactly = 0) { coordinator.onStop(any(), any(), any(), any(), any()) }
            assertEquals(listOf(validHash to null), backend.removedKept)
        }

    @Test
    fun EN4_failureBeforeReady_callsNothing() =
        runTest {
            val backend = FakeTorrentBackend()
            backend.awaitMetadataError = TorrentBackendException(TorrentFailReason.ENGINE_UNAVAILABLE)
            val coordinator: CacheRetentionCoordinator = mockk(relaxed = true)
            val engine = DefaultTorrentEngine(backend, coordinator)

            engine.prepare(TorrentRequest(infoHash = validHash, fileIdx = 0, cacheKey = "k")).test {
                assertEquals(TorrentState.FetchingMetadata, awaitItem())
                val failed = awaitItem()
                assertTrue(failed is TorrentState.Failed)
                awaitComplete()
            }

            coVerify(exactly = 0) { coordinator.onReady(any(), any(), any(), any()) }
            coVerify(exactly = 0) { coordinator.onStop(any(), any(), any(), any(), any()) }
            assertEquals(listOf(validHash to null), backend.removedKept)
        }
}
