package com.nexus.stream.data.download

import com.nexus.stream.domain.model.DownloadFailure
import com.nexus.stream.domain.repository.HttpResult
import com.nexus.stream.domain.repository.HttpTask
import com.nexus.stream.domain.repository.ProxyDiagnostics
import com.nexus.stream.domain.repository.ProxySession
import com.nexus.stream.domain.repository.StreamProxy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class HttpDownloaderTest {
    @get:Rule
    val tmpFolder = TemporaryFolder()

    private class FakeStreamProxy : StreamProxy {
        val openedUrls = mutableListOf<String>()
        var sessionClosed = false

        override fun open(
            url: String,
            headers: Map<String, String>,
        ): ProxySession {
            openedUrls.add(url)
            return ProxySession(
                url = "http://127.0.0.1:8080/proxied",
                diagnostics = MutableStateFlow(ProxyDiagnostics()),
            ) {
                sessionClosed = true
            }
        }
    }

    private fun createClient(interceptor: Interceptor): OkHttpClient =
        OkHttpClient
            .Builder()
            .addInterceptor(interceptor)
            .build()

    private fun createDownloader(
        client: OkHttpClient,
        proxy: StreamProxy = FakeStreamProxy(),
        dispatcher: CoroutineDispatcher = kotlinx.coroutines.Dispatchers.Unconfined,
    ): HttpDownloaderImpl =
        HttpDownloaderImpl(
            client = client,
            streamProxy = proxy,
            ioDispatcher = dispatcher,
            delayFn = {},
        )

    @Test
    fun HD1_freshDownload_writesTheFile_andReportsProgress() =
        runTest {
            val totalSize = 1024 * 1024
            val data = ByteArray(totalSize) { 0x42.toByte() }

            val client =
                createClient { chain ->
                    Response
                        .Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .header("Content-Type", "video/mp4")
                        .header("Content-Length", totalSize.toString())
                        .body(data.toResponseBody("video/mp4".toMediaType()))
                        .build()
                }

            val partFile = File(tmpFolder.root, "test.part")
            val downloader = createDownloader(client)

            val progressList = mutableListOf<Pair<Long, Long>>()
            val result =
                downloader.download(HttpTask(url = "https://example.com/video.mp4", partFile = partFile)) { b, t ->
                    progressList.add(b to t)
                }

            assertTrue(result is HttpResult.Completed)
            val completed = result as HttpResult.Completed
            assertEquals(totalSize.toLong(), completed.total)
            assertEquals(totalSize.toLong(), partFile.length())
            assertEquals(totalSize.toLong(), progressList.last().first)
            assertEquals(totalSize.toLong(), progressList.last().second)
        }

    @Test
    fun HD2_resume_sendsRangeAndIfRange_andAppends() =
        runTest {
            val partFile = File(tmpFolder.root, "test.part")
            partFile.writeBytes(ByteArray(400) { 0x01.toByte() })

            var recordedRange: String? = null
            var recordedIfRange: String? = null

            val remainingData = ByteArray(600) { 0x02.toByte() }

            val client =
                createClient { chain ->
                    recordedRange = chain.request().header("Range")
                    recordedIfRange = chain.request().header("If-Range")

                    Response
                        .Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(206)
                        .message("Partial Content")
                        .header("Content-Type", "video/mp4")
                        .header("Content-Range", "bytes 400-999/1000")
                        .header("Content-Length", "600")
                        .body(remainingData.toResponseBody("video/mp4".toMediaType()))
                        .build()
                }

            val downloader = createDownloader(client)
            val result =
                downloader.download(
                    HttpTask(
                        url = "https://example.com/video.mp4",
                        partFile = partFile,
                        offset = 400L,
                        etag = "e1",
                    ),
                ) { _, _ -> }

            assertEquals("bytes=400-", recordedRange)
            assertEquals("e1", recordedIfRange)
            assertTrue(result is HttpResult.Completed)
            assertEquals(1000L, partFile.length())
        }

    @Test
    fun HD3_serverIgnoresRange_restartsFromZero() =
        runTest {
            val partFile = File(tmpFolder.root, "test.part")
            partFile.writeBytes(ByteArray(400) { 0x01.toByte() })

            val fullData = ByteArray(1000) { 0x03.toByte() }

            val client =
                createClient { chain ->
                    Response
                        .Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .header("Content-Type", "video/mp4")
                        .header("Content-Length", "1000")
                        .body(fullData.toResponseBody("video/mp4".toMediaType()))
                        .build()
                }

            val downloader = createDownloader(client)
            val result =
                downloader.download(
                    HttpTask(
                        url = "https://example.com/video.mp4",
                        partFile = partFile,
                        offset = 400L,
                    ),
                ) { _, _ -> }

            assertTrue(result is HttpResult.Completed)
            val completed = result as HttpResult.Completed
            assertFalse(completed.supportsResume)
            assertEquals(1000L, partFile.length())
        }

    @Test
    fun HD4_416_whenAlreadyComplete_isCompleted() =
        runTest {
            val partFile = File(tmpFolder.root, "test.part")
            partFile.writeBytes(ByteArray(1000) { 0x01.toByte() })

            val client =
                createClient { chain ->
                    Response
                        .Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(416)
                        .message("Range Not Satisfiable")
                        .body("".toResponseBody("text/plain".toMediaType()))
                        .build()
                }

            val downloader = createDownloader(client)
            val result =
                downloader.download(
                    HttpTask(
                        url = "https://example.com/video.mp4",
                        partFile = partFile,
                        offset = 1000L,
                        etag = "e1",
                    ),
                ) { _, _ -> }

            assertTrue(result is HttpResult.Completed)
            val completed = result as HttpResult.Completed
            assertEquals(1000L, completed.total)
        }

    @Test
    fun HD5_expiredLink_403_failsLinkExpired() =
        runTest {
            val partFile = File(tmpFolder.root, "test.part")
            partFile.writeBytes(ByteArray(200) { 0x01.toByte() })

            val client =
                createClient { chain ->
                    Response
                        .Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(403)
                        .message("Forbidden")
                        .body("Forbidden".toResponseBody("text/plain".toMediaType()))
                        .build()
                }

            val downloader = createDownloader(client)
            val result =
                downloader.download(
                    HttpTask(
                        url = "https://example.com/video.mp4",
                        partFile = partFile,
                        offset = 200L,
                    ),
                ) { _, _ -> }

            assertTrue(result is HttpResult.Failed)
            val failed = result as HttpResult.Failed
            assertEquals(DownloadFailure.LINK_EXPIRED, failed.failure)
            assertEquals(200L, partFile.length())
        }

    @Test
    fun HD6_htmlAnswer_failsNotAVideo_beforeWriting() =
        runTest {
            val partFile = File(tmpFolder.root, "test.part")

            val client =
                createClient { chain ->
                    Response
                        .Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .header("Content-Type", "text/html; charset=utf-8")
                        .header("Content-Length", "2048")
                        .body("<html>Login</html>".toResponseBody("text/html".toMediaType()))
                        .build()
                }

            val downloader = createDownloader(client)
            val result =
                downloader.download(
                    HttpTask(
                        url = "https://example.com/video.mp4",
                        partFile = partFile,
                    ),
                ) { _, _ -> }

            assertTrue(result is HttpResult.Failed)
            val failed = result as HttpResult.Failed
            assertEquals(DownloadFailure.NOT_A_VIDEO, failed.failure)
            assertFalse(partFile.exists())
        }

    @Test
    fun HD7_serverError_retriesThenFails() =
        runTest {
            val attempts = AtomicInteger(0)
            val client =
                createClient { chain ->
                    attempts.incrementAndGet()
                    Response
                        .Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(503)
                        .message("Unavailable")
                        .body("Service Unavailable".toResponseBody("text/plain".toMediaType()))
                        .build()
                }

            val partFile = File(tmpFolder.root, "test.part")
            val downloader = createDownloader(client)

            val result =
                downloader.download(
                    HttpTask(url = "https://example.com/video.mp4", partFile = partFile),
                ) { _, _ -> }

            assertTrue(result is HttpResult.Failed)
            val failed = result as HttpResult.Failed
            assertEquals(DownloadFailure.SERVER_ERROR, failed.failure)
            assertEquals(4, attempts.get()) // 1 initial + 3 retries
        }

    @Test
    fun HD8_connectionDropsMidBody_keepsThePartFile_andReportsNetwork() =
        runTest {
            val partFile = File(tmpFolder.root, "test.part")

            val client =
                createClient { chain ->
                    val source =
                        object : ForwardingSource(Buffer().write(ByteArray(300) { 0x05 })) {
                            var readCount = 0L

                            override fun read(
                                sink: Buffer,
                                byteCount: Long,
                            ): Long {
                                if (readCount >= 300) {
                                    throw IOException("Connection dropped")
                                }
                                val bytesRead = super.read(sink, byteCount)
                                if (bytesRead > 0) {
                                    readCount += bytesRead
                                }
                                return bytesRead
                            }
                        }

                    Response
                        .Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .header("Content-Type", "video/mp4")
                        .header("Content-Length", "1000")
                        .body(
                            object : ResponseBody() {
                                override fun contentType() = "video/mp4".toMediaType()

                                override fun contentLength() = 1000L

                                override fun source(): BufferedSource = source.buffer()
                            },
                        ).build()
                }

            val downloader = createDownloader(client)

            val result =
                downloader.download(
                    HttpTask(url = "https://example.com/video.mp4", partFile = partFile),
                ) { _, _ -> }

            assertTrue(result is HttpResult.Failed)
            val failed = result as HttpResult.Failed
            assertEquals(DownloadFailure.NETWORK, failed.failure)
            assertEquals(300L, partFile.length())
            assertEquals(300L, failed.resumeInfo?.bytes)
        }

    @Test
    fun HD9_cancel_keepsThePartFile_andRethrows() =
        runTest {
            val partFile = File(tmpFolder.root, "test.part")

            val client =
                createClient { chain ->
                    Response
                        .Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .header("Content-Type", "video/mp4")
                        .header("Content-Length", "1000000")
                        .body(ByteArray(1000000) { 0x01 }.toResponseBody("video/mp4".toMediaType()))
                        .build()
                }

            val downloader =
                HttpDownloaderImpl(
                    client = client,
                    streamProxy = FakeStreamProxy(),
                    ioDispatcher = StandardTestDispatcher(testScheduler),
                    delayFn = {},
                )

            val job =
                launch {
                    downloader.download(
                        HttpTask(url = "https://example.com/video.mp4", partFile = partFile),
                    ) { _, _ -> }
                }

            testScheduler.advanceTimeBy(10)
            job.cancelAndJoin()
            assertTrue(partFile.exists())
        }

    @Test
    fun HD10_unknownTotal_streamsToTheEnd_withoutResume() =
        runTest {
            val totalBytes = 500
            val data = ByteArray(totalBytes) { 0x09 }
            val partFile = File(tmpFolder.root, "test.part")

            val client =
                createClient { chain ->
                    Response
                        .Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .header("Content-Type", "video/mp4")
                        .body(data.toResponseBody("video/mp4".toMediaType()))
                        .build()
                }

            val downloader = createDownloader(client)
            val result =
                downloader.download(
                    HttpTask(url = "https://example.com/video.mp4", partFile = partFile),
                ) { _, _ -> }

            assertTrue(result is HttpResult.Completed)
            val completed = result as HttpResult.Completed
            assertFalse(completed.supportsResume)
            assertEquals(totalBytes.toLong(), completed.total)
            assertEquals(totalBytes.toLong(), partFile.length())
        }

    @Test
    fun HD11_shortBody_afterCleanClose_isIncomplete() =
        runTest {
            val partFile = File(tmpFolder.root, "test.part")
            val data = ByteArray(700) { 0x07 }

            val client =
                createClient { chain ->
                    Response
                        .Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .header("Content-Type", "video/mp4")
                        .header("Content-Length", "1000")
                        .body(data.toResponseBody("video/mp4".toMediaType()))
                        .build()
                }

            val downloader = createDownloader(client)

            val result =
                downloader.download(
                    HttpTask(url = "https://example.com/video.mp4", partFile = partFile),
                ) { _, _ -> }

            assertTrue(result is HttpResult.Failed)
            val failed = result as HttpResult.Failed
            assertEquals(DownloadFailure.INCOMPLETE, failed.failure)
        }

    @Test
    fun HD12_progress_isThrottled() =
        runTest {
            val totalSize = 10 * 1024 * 1024
            val data = ByteArray(totalSize) { 0x01 }
            val partFile = File(tmpFolder.root, "test.part")

            val client =
                createClient { chain ->
                    Response
                        .Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .header("Content-Type", "video/mp4")
                        .header("Content-Length", totalSize.toString())
                        .body(data.toResponseBody("video/mp4".toMediaType()))
                        .build()
                }

            var callbackCount = 0
            var lastBytes = 0L

            val downloader = createDownloader(client)
            val result =
                downloader.download(
                    HttpTask(url = "https://example.com/video.mp4", partFile = partFile),
                ) { bytes, _ ->
                    callbackCount++
                    lastBytes = bytes
                }

            assertTrue(result is HttpResult.Completed)
            val chunkCount = totalSize / DOWNLOAD_CHUNK_BYTES
            assertTrue(callbackCount < chunkCount / 2)
            assertEquals(totalSize.toLong(), lastBytes)
        }

    @Test
    fun HD13_requestAsksForIdentityEncoding() =
        runTest {
            var recordedEncoding: String? = null
            val client =
                createClient { chain ->
                    recordedEncoding = chain.request().header("Accept-Encoding")
                    Response
                        .Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .header("Content-Type", "video/mp4")
                        .header("Content-Length", "100")
                        .body(ByteArray(100).toResponseBody("video/mp4".toMediaType()))
                        .build()
                }

            val partFile = File(tmpFolder.root, "test.part")
            val downloader = createDownloader(client)
            downloader.download(HttpTask(url = "https://example.com/video.mp4", partFile = partFile)) { _, _ -> }

            assertEquals("identity", recordedEncoding)
        }

    @Test
    fun HD14_diskFull_failsInsufficientStorage() =
        runTest {
            val partFile = File(tmpFolder.root, "test.part")

            val client =
                createClient { chain ->
                    val source =
                        object : ForwardingSource(Buffer().write(ByteArray(500))) {
                            override fun read(
                                sink: Buffer,
                                byteCount: Long,
                            ): Long = throw IOException("No space left on device")
                        }

                    Response
                        .Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .header("Content-Type", "video/mp4")
                        .header("Content-Length", "1000")
                        .body(
                            object : ResponseBody() {
                                override fun contentType() = "video/mp4".toMediaType()

                                override fun contentLength() = 1000L

                                override fun source(): BufferedSource = source.buffer()
                            },
                        ).build()
                }

            val downloader = createDownloader(client)

            val result =
                downloader.download(
                    HttpTask(url = "https://example.com/video.mp4", partFile = partFile),
                ) { _, _ -> }

            assertTrue(result is HttpResult.Failed)
            val failed = result as HttpResult.Failed
            assertEquals(DownloadFailure.INSUFFICIENT_STORAGE, failed.failure)
        }
}
