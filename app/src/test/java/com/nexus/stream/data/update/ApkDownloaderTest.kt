package com.nexus.stream.data.update

import com.nexus.stream.domain.model.ReleaseAsset
import com.nexus.stream.domain.model.UpdateFailure
import com.nexus.stream.domain.repository.ApkFileStore
import com.nexus.stream.domain.repository.DownloadResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
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
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

class ApkDownloaderTest {
    @get:Rule
    val tmpFolder = TemporaryFolder()

    private fun createStore(): ApkFileStore {
        val folder = tmpFolder.newFolder()
        return object : ApkFileStore {
            override fun dir(): File = folder

            override fun cleanup(keep: String?) {
                folder.listFiles()?.forEach { file ->
                    if (keep == null || file.name != keep) file.delete()
                }
            }
        }
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(bytes).joinToString("") { java.lang.String.format(java.util.Locale.US, "%02x", it) }
    }

    @Test
    fun DD1_download_writesTheFile_andReportsProgress() =
        runTest {
            val size = 1024 * 1024 // 1 MiB
            val data = ByteArray(size) { (it % 128).toByte() }
            val client =
                OkHttpClient
                    .Builder()
                    .addInterceptor { chain ->
                        Response
                            .Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body(data.toResponseBody("application/vnd.android.package-archive".toMediaType()))
                            .build()
                    }.build()

            val store = createStore()
            val downloader = ApkDownloaderImpl(client, store)
            val asset = ReleaseAsset("app.apk", size.toLong(), "https://github.com/a/b/app.apk")

            val progressList = mutableListOf<Pair<Long, Long>>()
            val result =
                downloader.download(asset) { down, total ->
                    progressList.add(down to total)
                }

            assertTrue(result is DownloadResult.Success)
            val file = (result as DownloadResult.Success).file
            assertEquals(size.toLong(), file.length())
            assertTrue(file.readBytes().contentEquals(data))
            assertTrue(progressList.isNotEmpty())
            val last = progressList.last()
            assertEquals(size.toLong(), last.first)
            assertEquals(size.toLong(), last.second)
        }

    @Test
    fun DD2_download_usesAPartFile_untilComplete() =
        runTest {
            val shortData = ByteArray(10) { 1 }
            val client =
                OkHttpClient
                    .Builder()
                    .addInterceptor { chain ->
                        Response
                            .Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body(shortData.toResponseBody("application/vnd.android.package-archive".toMediaType()))
                            .build()
                    }.build()

            val store = createStore()
            val downloader = ApkDownloaderImpl(client, store)
            val asset = ReleaseAsset("app.apk", 100L, "https://github.com/a/b/app.apk")

            val result = downloader.download(asset)
            assertTrue(result is DownloadResult.Failure)
            assertFalse(File(store.dir(), "app.apk").exists())
            assertFalse(File(store.dir(), "app.apk.part").exists())
        }

    @Test
    fun DD3_cancel_deletesThePartFile_andRethrows() =
        runTest {
            val client =
                OkHttpClient
                    .Builder()
                    .addInterceptor { chain ->
                        val fakeStream =
                            object : InputStream() {
                                var count = 0

                                override fun read(): Int {
                                    count++
                                    if (count > 5) {
                                        throw CancellationException("Download cancelled")
                                    }
                                    return 1
                                }
                            }
                        val body =
                            object : ResponseBody() {
                                override fun contentType() = "application/vnd.android.package-archive".toMediaType()

                                override fun contentLength() = 1000L

                                override fun source(): BufferedSource =
                                    object : ForwardingSource(Buffer().readFrom(fakeStream)) {}.buffer()
                            }
                        Response
                            .Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body(body)
                            .build()
                    }.build()

            val store = createStore()
            val downloader = ApkDownloaderImpl(client, store)
            val asset = ReleaseAsset("app.apk", 1000L, "https://github.com/a/b/app.apk")

            try {
                downloader.download(asset)
                fail("Expected CancellationException")
            } catch (_: CancellationException) {
                assertFalse(File(store.dir(), "app.apk.part").exists())
                assertFalse(File(store.dir(), "app.apk").exists())
            }
        }

    @Test
    fun DD4_sizeMismatch_deletesTheFile_andFails() =
        runTest {
            val body90 = ByteArray(90) { 1 }
            val client =
                OkHttpClient
                    .Builder()
                    .addInterceptor { chain ->
                        Response
                            .Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body(body90.toResponseBody("application/vnd.android.package-archive".toMediaType()))
                            .build()
                    }.build()

            val store = createStore()
            val downloader = ApkDownloaderImpl(client, store)
            val asset = ReleaseAsset("app.apk", 100L, "https://github.com/a/b/app.apk")

            val result = downloader.download(asset)
            assertEquals(DownloadResult.Failure(UpdateFailure.SizeMismatch), result)
            assertFalse(File(store.dir(), "app.apk").exists())
            assertFalse(File(store.dir(), "app.apk.part").exists())
        }

    @Test
    fun DD5_checksumMismatch_deletesTheFile_andFails() =
        runTest {
            val body = ByteArray(100) { 1 }
            val client =
                OkHttpClient
                    .Builder()
                    .addInterceptor { chain ->
                        Response
                            .Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body(body.toResponseBody("application/vnd.android.package-archive".toMediaType()))
                            .build()
                    }.build()

            val store = createStore()
            val downloader = ApkDownloaderImpl(client, store)
            val wrongSha = "0000000000000000000000000000000000000000000000000000000000000000"
            val asset = ReleaseAsset("app.apk", 100L, "https://github.com/a/b/app.apk", sha256 = wrongSha)

            val result = downloader.download(asset)
            assertEquals(DownloadResult.Failure(UpdateFailure.ChecksumMismatch), result)
            assertFalse(File(store.dir(), "app.apk").exists())
            assertFalse(File(store.dir(), "app.apk.part").exists())
        }

    @Test
    fun DD6_redirectToDisallowedHost_isRejected() =
        runTest {
            var requestCount = 0
            val client =
                OkHttpClient
                    .Builder()
                    .addInterceptor { chain ->
                        requestCount++
                        Response
                            .Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(302)
                            .message("Found")
                            .header("Location", "https://evil.example/x.apk")
                            .body("".toResponseBody())
                            .build()
                    }.build()

            val store = createStore()
            val downloader = ApkDownloaderImpl(client, store)
            val asset = ReleaseAsset("x.apk", 100L, "https://github.com/a/b/x.apk")

            val result = downloader.download(asset)
            assertEquals(DownloadResult.Failure(UpdateFailure.DownloadFailed), result)
            assertEquals(1, requestCount)
        }

    @Test
    fun DD7_httpError_isDownloadFailed() =
        runTest {
            val client404 =
                OkHttpClient
                    .Builder()
                    .addInterceptor { chain ->
                        Response
                            .Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(404)
                            .message("Not Found")
                            .body("".toResponseBody())
                            .build()
                    }.build()
            val store = createStore()
            val asset = ReleaseAsset("x.apk", 100L, "https://github.com/a/b/x.apk")
            val dl404 = ApkDownloaderImpl(client404, store)
            assertEquals(DownloadResult.Failure(UpdateFailure.DownloadFailed), dl404.download(asset))

            val client500 =
                OkHttpClient
                    .Builder()
                    .addInterceptor { chain ->
                        Response
                            .Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(500)
                            .message("Server Error")
                            .body("".toResponseBody())
                            .build()
                    }.build()
            val dl500 = ApkDownloaderImpl(client500, store)
            assertEquals(DownloadResult.Failure(UpdateFailure.DownloadFailed), dl500.download(asset))
        }

    @Test
    fun DD8_existingVerifiedFile_isReused() =
        runTest {
            val data = ByteArray(50) { 7 }
            val sha = sha256Hex(data)
            val store = createStore()
            val existing = File(store.dir(), "reused.apk")
            existing.writeBytes(data)

            val clientNeverCalled =
                OkHttpClient
                    .Builder()
                    .addInterceptor { throw AssertionError("Network must not be called") }
                    .build()

            val downloader = ApkDownloaderImpl(clientNeverCalled, store)
            val asset = ReleaseAsset("reused.apk", 50L, "https://github.com/a/b/reused.apk", sha256 = sha)

            var reportedProgress = false
            val result = downloader.download(asset) { _, _ -> reportedProgress = true }
            assertTrue(result is DownloadResult.Success)
            assertEquals(existing, (result as DownloadResult.Success).file)
            assertTrue(reportedProgress)
        }

    @Test
    fun DD9_cleanup_removesEverythingExceptTheKeptFile() {
        val store = createStore()
        val fileA = File(store.dir(), "a.apk").apply { writeText("a") }
        val fileB = File(store.dir(), "b.apk").apply { writeText("b") }
        val fileC = File(store.dir(), "c.part").apply { writeText("c") }

        val downloader = ApkDownloaderImpl(OkHttpClient(), store)
        downloader.cleanup(keep = "a.apk")

        assertTrue(fileA.exists())
        assertFalse(fileB.exists())
        assertFalse(fileC.exists())
    }

    @Test
    fun DD10_progress_isThrottled() =
        runTest {
            val totalBytes = 10 * 1024 * 1024L // 10 MiB
            val chunkSize = 8192 // 8 KiB -> 1280 chunks
            var fakeTime = 1000L

            val client =
                OkHttpClient
                    .Builder()
                    .addInterceptor { chain ->
                        val fakeStream =
                            object : InputStream() {
                                var bytesRead = 0L

                                override fun read(
                                    b: ByteArray,
                                    off: Int,
                                    len: Int,
                                ): Int {
                                    if (bytesRead >= totalBytes) return -1
                                    val toRead = minOf(len.toLong(), chunkSize.toLong(), totalBytes - bytesRead).toInt()
                                    bytesRead += toRead
                                    fakeTime += 10L // advance by 10ms per chunk (250ms interval)
                                    return toRead
                                }

                                override fun read(): Int = if (bytesRead++ < totalBytes) 1 else -1
                            }
                        Response
                            .Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body(
                                object : ResponseBody() {
                                    override fun contentType() = "application/vnd.android.package-archive".toMediaType()

                                    override fun contentLength() = totalBytes

                                    override fun source(): BufferedSource =
                                        object : ForwardingSource(Buffer().readFrom(fakeStream)) {}.buffer()
                                },
                            ).build()
                    }.build()

            val store = createStore()
            val downloader = ApkDownloaderImpl(client, store, clock = { fakeTime })
            val asset = ReleaseAsset("large.apk", totalBytes, "https://github.com/a/b/large.apk")

            val progressCalls = mutableListOf<Long>()
            val result =
                downloader.download(asset) { down, _ ->
                    progressCalls.add(down)
                }

            assertTrue(result is DownloadResult.Success)
            // 1280 chunks total, throttled callbacks should be at most ~100
            assertTrue("Progress calls count ${progressCalls.size} should be < 200", progressCalls.size < 200)
            assertEquals(totalBytes, progressCalls.last())
        }
}
