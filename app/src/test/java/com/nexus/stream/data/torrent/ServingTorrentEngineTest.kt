package com.nexus.stream.data.torrent

import com.nexus.stream.data.stream.LoopbackServer
import com.nexus.stream.domain.model.AppError
import com.nexus.stream.domain.model.TorrentFailReason
import com.nexus.stream.domain.model.TorrentFile
import com.nexus.stream.domain.model.TorrentRequest
import com.nexus.stream.domain.model.TorrentState
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.Socket
import java.net.URL

private const val TEST_HASH = "0123456789abcdef0123456789abcdef01234567"

private class ServingFakeLocator(
    private val fileMap: Map<String, File> = emptyMap(),
) : TorrentFileLocator {
    override fun fileOf(
        infoHash: String,
        relativePath: String,
    ): File = fileMap[relativePath] ?: File("/tmp/mock/$relativePath")
}

private class ServingFakeBackend : TorrentBackend {
    var metadataResult: TorrentMetadata? = null
    var awaitMetadataException: Exception? = null
    var snapshotResult =
        TorrentSnapshot(piecesHave = 100, downloadSpeedBps = 0L, peers = 1)
    var lastPrioritizePieces: List<Int>? = null

    override suspend fun add(
        infoHash: String,
        magnetUri: String,
    ) {
        // No-op for test fake
    }

    override suspend fun awaitMetadata(infoHash: String): TorrentMetadata {
        awaitMetadataException?.let { throw it }
        return metadataResult ?: TorrentMetadata(
            files =
                listOf(
                    BackendFile(
                        index = 1,
                        path = "a.mkv",
                        sizeBytes = 1000L,
                        offsetBytes = 0L,
                    ),
                ),
            pieceLength = 100L,
        )
    }

    override suspend fun prioritize(
        infoHash: String,
        fileIndex: Int,
        pieces: List<Int>,
    ) {
        lastPrioritizePieces = pieces
    }

    override suspend fun snapshot(
        infoHash: String,
        pieces: List<Int>,
    ): TorrentSnapshot = snapshotResult

    override suspend fun remove(infoHash: String) {
        // No-op for test fake
    }
}

private class ServingFakeDefaultEngine(
    backend: TorrentBackend,
    var flowToEmit: Flow<TorrentState>? = null,
) : DefaultTorrentEngine(backend) {
    var selectedHash: String? = null
    var selectedIdx: Int? = null
    var stopHash: String? = null
    val stoppedDeferred = CompletableDeferred<Unit>()

    override fun prepare(request: TorrentRequest): Flow<TorrentState> = flowToEmit ?: super.prepare(request)

    override suspend fun selectFile(
        infoHash: String,
        fileIdx: Int,
    ) {
        selectedHash = infoHash
        selectedIdx = fileIdx
        super.selectFile(infoHash, fileIdx)
    }

    override suspend fun stop(infoHash: String) {
        stopHash = infoHash
        stoppedDeferred.complete(Unit)
        super.stop(infoHash)
    }
}

private fun createEngine(
    delegate: DefaultTorrentEngine,
    backend: TorrentBackend,
    locator: TorrentFileLocator,
    server: LoopbackServer,
    token: String = "0123456789abcdef0123456789abcdef",
): ServingTorrentEngine =
    ServingTorrentEngine(
        delegate = delegate,
        backend = backend,
        locator = locator,
        server = server,
        tokenGenerator = { token },
    )

private fun createSe2Backend(totalSize: Long) =
    ServingFakeBackend().apply {
        metadataResult =
            TorrentMetadata(
                files =
                    listOf(
                        BackendFile(
                            index = 1,
                            path = "video.mkv",
                            sizeBytes = totalSize,
                            offsetBytes = 0L,
                        ),
                    ),
                pieceLength = 100L,
            )
        snapshotResult =
            TorrentSnapshot(piecesHave = 100, downloadSpeedBps = 1000L, peers = 5)
    }

@OptIn(ExperimentalCoroutinesApi::class)
private fun TestScope.launchCollectReady(
    engine: ServingTorrentEngine,
    delegate: ServingFakeDefaultEngine,
    hang: CompletableDeferred<Unit>,
): Pair<Job, () -> String?> {
    var readyUrl: String? = null
    delegate.flowToEmit =
        flow {
            emit(TorrentState.Ready(1, "video.mkv", 200L, null))
            hang.await()
        }
    val job =
        launch {
            engine.prepare(TorrentRequest(TEST_HASH)).collect {
                if (it is TorrentState.Ready) readyUrl = it.localUrl
            }
        }
    return job to { readyUrl }
}

@OptIn(ExperimentalCoroutinesApi::class)
private suspend fun TestScope.testFileExtensionAndMime(
    fileName: String,
    expectedExt: String,
    expectedMime: String,
) {
    val server = LoopbackServer(Dispatchers.IO)
    val tempFile = File.createTempFile("se8_test", ".tmp").apply { writeBytes(ByteArray(100)) }
    val backend =
        ServingFakeBackend().apply {
            metadataResult =
                TorrentMetadata(
                    files =
                        listOf(
                            BackendFile(
                                index = 1,
                                path = fileName,
                                sizeBytes = 100L,
                                offsetBytes = 0L,
                            ),
                        ),
                    pieceLength = 50L,
                )
        }
    val locator = ServingFakeLocator(mapOf(fileName to tempFile))
    val hang = CompletableDeferred<Unit>()
    val delegateFlow =
        flow {
            emit(TorrentState.Ready(1, fileName, 100L, null))
            hang.await()
        }
    val delegate = ServingFakeDefaultEngine(backend, delegateFlow)
    val engine = createEngine(delegate, backend, locator, server, "88888888888888888888888888888888")

    var readyUrl: String? = null
    val job =
        launch {
            engine.prepare(TorrentRequest(TEST_HASH)).collect {
                if (it is TorrentState.Ready) readyUrl = it.localUrl
            }
        }
    runCurrent()

    assertNotNull(readyUrl)
    assertTrue(
        "Expected URL to end with stream.$expectedExt but was $readyUrl",
        readyUrl!!.endsWith("/stream.$expectedExt"),
    )

    val resp = withContext(Dispatchers.IO) { httpGet(readyUrl!!) }
    assertEquals(expectedMime, resp.headers["content-type"])

    job.cancel()
    server.stop()
    tempFile.delete()
}

private fun httpGet(
    urlStr: String,
    rangeHeader: String? = null,
): HttpResponseData {
    val url = URL(urlStr)
    val socket = Socket(url.host, url.port)
    socket.soTimeout = 3000
    val writer = socket.getOutputStream().bufferedWriter(Charsets.US_ASCII)
    val path = if (url.query.isNullOrEmpty()) url.path else "${url.path}?${url.query}"
    writer.write("GET $path HTTP/1.1\r\n")
    writer.write("Host: ${url.host}:${url.port}\r\n")
    if (rangeHeader != null) {
        writer.write("Range: $rangeHeader\r\n")
    }
    writer.write("Connection: close\r\n\r\n")
    writer.flush()

    val stream = socket.getInputStream().buffered()
    val headerBytes = ByteArrayOutputStream()
    var prev = -1
    var count = 0
    var reading = true
    while (reading) {
        val b = stream.read()
        if (b == -1) {
            reading = false
        } else {
            headerBytes.write(b)
            if (prev == '\r'.code && b == '\n'.code) {
                count++
                if (count == 2) {
                    reading = false
                }
            } else if (b != '\r'.code) {
                count = 0
            }
            prev = b
        }
    }

    val headerText = headerBytes.toString(Charsets.US_ASCII.name())
    val lines = headerText.split("\r\n")
    val statusLine = lines[0]
    val statusCode = statusLine.split(" ")[1].toInt()
    val headers = mutableMapOf<String, String>()
    for (i in 1 until lines.size) {
        val line = lines[i]
        val colon = line.indexOf(':')
        if (colon > 0) {
            val headerName = line.substring(0, colon).trim().lowercase()
            val headerValue = line.substring(colon + 1).trim()
            headers[headerName] = headerValue
        }
    }
    val body = stream.readBytes()
    socket.close()
    return HttpResponseData(statusCode, headers, body)
}

private data class HttpResponseData(
    val statusCode: Int,
    val headers: Map<String, String>,
    val body: ByteArray,
)

@OptIn(ExperimentalCoroutinesApi::class)
class ServingTorrentEngineTest {
    @Test
    fun SE1_delegateEmitsStates_wrappedWithLocalUrl() =
        runTest {
            val server = LoopbackServer(Dispatchers.IO)
            val backend = ServingFakeBackend()
            val locator = ServingFakeLocator()
            val hangDeferred = CompletableDeferred<Unit>()

            val delegateFlow =
                flow {
                    emit(TorrentState.FetchingMetadata)
                    emit(TorrentState.Buffering(50, 1024L, 5))
                    emit(TorrentState.Ready(1, "a.mkv", 1000L, null))
                    hangDeferred.await()
                }
            val delegate = ServingFakeDefaultEngine(backend, delegateFlow)
            val engine = createEngine(delegate, backend, locator, server)

            val states = mutableListOf<TorrentState>()
            val job =
                launch {
                    engine.prepare(TorrentRequest(TEST_HASH)).collect { states.add(it) }
                }
            runCurrent()

            assertEquals(3, states.size)
            assertTrue(states[0] is TorrentState.FetchingMetadata)
            val buffering = states[1] as TorrentState.Buffering
            assertEquals(50, buffering.percent)
            assertEquals(1024L, buffering.speedBps)
            assertEquals(5, buffering.peers)

            val ready = states[2] as TorrentState.Ready
            assertEquals(1, ready.fileIndex)
            assertEquals("a.mkv", ready.fileName)
            assertEquals(1000L, ready.fileSize)
            assertNotNull(ready.localUrl)
            assertTrue(
                ready.localUrl!!.matches(
                    Regex("^http://127\\.0\\.0\\.1:\\d+/t/[0-9a-f]{32}/stream\\.mkv$"),
                ),
            )

            job.cancel()
            server.stop()
        }

    @Test
    fun SE2_realServerRangeGet_returnsPartialContent() =
        runTest {
            val totalSize = 500L
            val testBytes = ByteArray(totalSize.toInt()) { (it % 256).toByte() }
            val tempFile = File.createTempFile("se2_test", ".mkv").apply { writeBytes(testBytes) }
            val server = LoopbackServer(Dispatchers.IO)
            val backend = createSe2Backend(totalSize)
            val locator = ServingFakeLocator(mapOf("video.mkv" to tempFile))
            val hang = CompletableDeferred<Unit>()
            val delegateFlow =
                flow {
                    emit(TorrentState.Ready(1, "video.mkv", totalSize, null))
                    hang.await()
                }
            val delegate = ServingFakeDefaultEngine(backend, delegateFlow)
            val token = "00112233445566778899aabbccddeeff"
            val engine = createEngine(delegate, backend, locator, server, token)

            var readyUrl: String? = null
            val job =
                launch {
                    engine.prepare(TorrentRequest(TEST_HASH)).collect { state ->
                        if (state is TorrentState.Ready) {
                            readyUrl = state.localUrl
                        }
                    }
                }
            runCurrent()

            assertNotNull(readyUrl)
            val response =
                withContext(Dispatchers.IO) {
                    withTimeout(5_000) {
                        httpGet(readyUrl!!, rangeHeader = "bytes=100-199")
                    }
                }

            assertEquals(206, response.statusCode)
            assertEquals("bytes 100-199/$totalSize", response.headers["content-range"])
            assertEquals("video/x-matroska", response.headers["content-type"])
            val expectedBytes = testBytes.copyOfRange(100, 200)
            assertArrayEquals(expectedBytes, response.body)

            job.cancel()
            server.stop()
            tempFile.delete()
        }

    @Test
    fun SE3_sequentialPrepares_differentTokens_firstAnswers404() =
        runTest {
            val server = LoopbackServer(Dispatchers.IO)
            val tempFile = File.createTempFile("se3_test", ".mkv").apply { writeBytes(ByteArray(200)) }
            val backend =
                ServingFakeBackend().apply {
                    metadataResult =
                        TorrentMetadata(
                            files =
                                listOf(
                                    BackendFile(
                                        index = 1,
                                        path = "video.mkv",
                                        sizeBytes = 200L,
                                        offsetBytes = 0L,
                                    ),
                                ),
                            pieceLength = 50L,
                        )
                }
            val locator = ServingFakeLocator(mapOf("video.mkv" to tempFile))

            var tokenCounter = 1
            val delegate = ServingFakeDefaultEngine(backend)
            val engine =
                ServingTorrentEngine(
                    delegate = delegate,
                    backend = backend,
                    locator = locator,
                    server = server,
                    tokenGenerator = {
                        if (tokenCounter++ == 1) {
                            "11111111111111111111111111111111"
                        } else {
                            "22222222222222222222222222222222"
                        }
                    },
                )

            val hang1 = CompletableDeferred<Unit>()
            val (job1, getUrl1) = launchCollectReady(engine, delegate, hang1)
            runCurrent()
            val url1 = getUrl1()
            assertNotNull(url1)
            assertTrue(url1!!.contains("11111111111111111111111111111111"))

            job1.cancel()
            runCurrent()

            val resp1 = withContext(Dispatchers.IO) { httpGet(url1) }
            assertEquals(404, resp1.statusCode)

            val hang2 = CompletableDeferred<Unit>()
            val (job2, getUrl2) = launchCollectReady(engine, delegate, hang2)
            runCurrent()
            val url2 = getUrl2()
            assertNotNull(url2)
            assertTrue(url2!!.contains("22222222222222222222222222222222"))

            job2.cancel()
            server.stop()
            tempFile.delete()
        }

    @Test
    fun SE4_collectorCancelledAfterReady() =
        runTest {
            val server = LoopbackServer(Dispatchers.IO)
            val tempFile = File.createTempFile("se4_test", ".mkv").apply { writeBytes(ByteArray(100)) }
            val backend = ServingFakeBackend()
            val locator = ServingFakeLocator(mapOf("a.mkv" to tempFile))
            val hang = CompletableDeferred<Unit>()
            val delegateFlow =
                flow {
                    emit(TorrentState.Ready(1, "a.mkv", 100L, null))
                    hang.await()
                }
            val delegate = ServingFakeDefaultEngine(backend, delegateFlow)
            val engine = createEngine(delegate, backend, locator, server, "44444444444444444444444444444444")

            var readyUrl: String? = null
            val job =
                launch {
                    engine.prepare(TorrentRequest(TEST_HASH)).collect {
                        if (it is TorrentState.Ready) readyUrl = it.localUrl
                    }
                }
            runCurrent()
            assertNotNull(readyUrl)

            job.cancel()
            runCurrent()

            val resp = withContext(Dispatchers.IO) { httpGet(readyUrl!!) }
            assertEquals(404, resp.statusCode)

            server.stop()
            tempFile.delete()
        }

    @Test
    fun SE5_stopWhileReady() =
        runTest {
            val server = LoopbackServer(Dispatchers.IO)
            val tempFile = File.createTempFile("se5_test", ".mkv").apply { writeBytes(ByteArray(100)) }
            val backend = ServingFakeBackend()
            val locator = ServingFakeLocator(mapOf("a.mkv" to tempFile))
            val stopDeferred = CompletableDeferred<Unit>()
            val delegateFlow =
                flow {
                    emit(TorrentState.Ready(1, "a.mkv", 100L, null))
                    stopDeferred.await()
                }
            val delegate = ServingFakeDefaultEngine(backend, delegateFlow)
            val engine = createEngine(delegate, backend, locator, server, "55555555555555555555555555555555")

            var readyUrl: String? = null
            var flowCompleted = false
            val job =
                launch {
                    engine.prepare(TorrentRequest(TEST_HASH)).collect {
                        if (it is TorrentState.Ready) readyUrl = it.localUrl
                    }
                    flowCompleted = true
                }
            runCurrent()
            assertNotNull(readyUrl)

            engine.stop(TEST_HASH)
            assertEquals(TEST_HASH, delegate.stopHash)

            stopDeferred.complete(Unit)
            runCurrent()

            assertTrue(flowCompleted)
            val resp = withContext(Dispatchers.IO) { httpGet(readyUrl!!) }
            assertEquals(404, resp.statusCode)

            job.cancel()
            server.stop()
            tempFile.delete()
        }

    @Test
    fun SE6_delegateEmitsNeedsFilePickAndFailed() =
        runTest {
            val server = LoopbackServer(Dispatchers.IO)
            val backend = ServingFakeBackend()
            val locator = ServingFakeLocator()
            val pickFiles =
                listOf(
                    TorrentFile(1, "video1.mkv", 1000L),
                    TorrentFile(2, "video2.mkv", 2000L),
                )
            val delegateFlow =
                flowOf(
                    TorrentState.NeedsFilePick(pickFiles),
                    TorrentState.Failed(AppError.Torrent(TorrentFailReason.STORAGE)),
                )
            val delegate = ServingFakeDefaultEngine(backend, delegateFlow)
            val engine = createEngine(delegate, backend, locator, server, "66666666666666666666666666666666")

            val states = mutableListOf<TorrentState>()
            engine.prepare(TorrentRequest(TEST_HASH)).collect { states.add(it) }

            assertEquals(2, states.size)
            assertTrue(states[0] is TorrentState.NeedsFilePick)
            assertTrue(states[1] is TorrentState.Failed)

            // Server route should NOT be mounted
            val port = server.start()
            val resp =
                withContext(Dispatchers.IO) {
                    httpGet("http://127.0.0.1:$port/t/66666666666666666666666666666666/stream.bin")
                }
            assertEquals(404, resp.statusCode)
            server.stop()
        }

    @Test
    fun SE7_serverStartThrows() =
        runTest {
            val failingServer = mockk<LoopbackServer>()
            every { failingServer.start() } throws IOException("Cannot bind port")
            every { failingServer.unmount(any()) } returns Unit

            val backend = ServingFakeBackend()
            val locator = ServingFakeLocator()
            var delegateCancelled = false
            val hang = CompletableDeferred<Unit>()
            val delegateFlow =
                flow {
                    try {
                        emit(TorrentState.Ready(1, "a.mkv", 1000L, null))
                        hang.await()
                    } finally {
                        delegateCancelled = true
                    }
                }
            val delegate = ServingFakeDefaultEngine(backend, delegateFlow)
            val engine = createEngine(delegate, backend, locator, failingServer, "77777777777777777777777777777777")

            val states = mutableListOf<TorrentState>()
            engine.prepare(TorrentRequest(TEST_HASH)).collect { states.add(it) }

            assertEquals(1, states.size)
            assertTrue(states[0] is TorrentState.Failed)
            val failed = states[0] as TorrentState.Failed
            assertEquals(AppError.Torrent(TorrentFailReason.ENGINE_UNAVAILABLE), failed.error)
            assertTrue(delegateCancelled)
        }

    @Test
    fun SE8_fileExtensionsAndContentTypes() =
        runTest {
            val testCases =
                listOf(
                    Triple("X.MP4", "mp4", "video/mp4"),
                    Triple("x.weirdext1", "bin", "application/octet-stream"),
                    Triple("noext", "bin", "application/octet-stream"),
                    Triple("a.b-c", "bin", "application/octet-stream"),
                )

            for ((fileName, expectedExt, expectedMime) in testCases) {
                testFileExtensionAndMime(fileName, expectedExt, expectedMime)
            }
        }

    @Test
    fun SE9_awaitMetadataThrows() =
        runTest {
            // Case 1: TorrentBackendException(STORAGE)
            val server = LoopbackServer(Dispatchers.IO)
            val backend1 =
                ServingFakeBackend().apply {
                    awaitMetadataException = TorrentBackendException(TorrentFailReason.STORAGE)
                }
            val locator = ServingFakeLocator()
            val readyFlow1 = flowOf(TorrentState.Ready(1, "a.mkv", 1000L, null))
            val delegate1 = ServingFakeDefaultEngine(backend1, readyFlow1)
            val engine1 = createEngine(delegate1, backend1, locator, server, "99999999999999999999999999999999")

            val states1 = mutableListOf<TorrentState>()
            engine1.prepare(TorrentRequest(TEST_HASH)).collect { states1.add(it) }

            assertEquals(1, states1.size)
            assertTrue(states1[0] is TorrentState.Failed)
            val failed1 = states1[0] as TorrentState.Failed
            assertEquals(AppError.Torrent(TorrentFailReason.STORAGE), failed1.error)

            // Case 2: RuntimeException
            val backend2 =
                ServingFakeBackend().apply {
                    awaitMetadataException = RuntimeException("unexpected disk failure")
                }
            val readyFlow2 = flowOf(TorrentState.Ready(1, "a.mkv", 1000L, null))
            val delegate2 = ServingFakeDefaultEngine(backend2, readyFlow2)
            val engine2 = createEngine(delegate2, backend2, locator, server, "99999999999999999999999999999999")

            val states2 = mutableListOf<TorrentState>()
            engine2.prepare(TorrentRequest(TEST_HASH)).collect { states2.add(it) }

            assertEquals(1, states2.size)
            assertTrue(states2[0] is TorrentState.Failed)
            val failed2 = states2[0] as TorrentState.Failed
            assertEquals(AppError.Unknown(), failed2.error)

            server.stop()
        }

    @Test
    fun SE10_selectFileDelegates() =
        runTest {
            val server = LoopbackServer(Dispatchers.IO)
            val backend = ServingFakeBackend()
            val locator = ServingFakeLocator()
            val delegate = ServingFakeDefaultEngine(backend)
            val engine = createEngine(delegate, backend, locator, server, "00000000000000000000000000000000")

            engine.selectFile(TEST_HASH, 3)
            assertEquals(TEST_HASH, delegate.selectedHash)
            assertEquals(3, delegate.selectedIdx)
            server.stop()
        }

    @Test
    fun SE11_readyEmittedOnceAndFlowStaysOpenUntilCancelled() =
        runTest {
            val server = LoopbackServer(Dispatchers.IO)
            val tempFile = File.createTempFile("se11_test", ".mkv").apply { writeBytes(ByteArray(1000)) }
            val backend = ServingFakeBackend()
            val locator = ServingFakeLocator(mapOf("a.mkv" to tempFile))
            val hang = CompletableDeferred<Unit>()
            val delegateFlow =
                flow {
                    emit(TorrentState.Ready(1, "a.mkv", 1000L, null))
                    hang.await()
                }
            val delegate = ServingFakeDefaultEngine(backend, delegateFlow)
            val engine = createEngine(delegate, backend, locator, server, "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")

            val states = mutableListOf<TorrentState>()
            var flowFinished = false
            val job =
                launch {
                    try {
                        engine.prepare(TorrentRequest(TEST_HASH)).collect {
                            states.add(it)
                        }
                    } finally {
                        flowFinished = true
                    }
                }
            runCurrent()

            assertEquals(1, states.size)
            assertTrue(states[0] is TorrentState.Ready)
            assertTrue(!flowFinished)

            // Cancel job and verify it cleans up
            job.cancel()
            runCurrent()
            assertTrue(flowFinished)

            server.stop()
            tempFile.delete()
        }
}
