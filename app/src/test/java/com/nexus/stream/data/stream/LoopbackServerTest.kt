package com.nexus.stream.data.stream

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class LoopbackServerTest {
    private var server: LoopbackServer? = null

    @After
    fun tearDown() {
        server?.stop()
    }

    private data class RawResponse(
        val status: Int,
        val headers: Map<String, String>,
        val body: String,
        val bodyBytes: ByteArray = ByteArray(0),
    )

    private fun sendRaw(
        port: Int,
        request: String,
        timeoutMs: Int = 3000,
    ): RawResponse {
        Socket("127.0.0.1", port).use { socket ->
            socket.soTimeout = timeoutMs
            val out = socket.getOutputStream()
            out.write(request.toByteArray(Charsets.US_ASCII))
            out.flush()

            val bytes = socket.getInputStream().readBytes()
            val text = String(bytes, Charsets.US_ASCII)
            val headPart = text.substringBefore("\r\n\r\n")
            val bodyPart = if (text.contains("\r\n\r\n")) text.substringAfter("\r\n\r\n") else ""
            val headBytesLength = headPart.length + 4
            val bodyBytes =
                if (text.contains("\r\n\r\n") && bytes.size >= headBytesLength) {
                    bytes.copyOfRange(headBytesLength, bytes.size)
                } else {
                    ByteArray(0)
                }

            val lines = headPart.split("\r\n")
            val status =
                lines
                    .firstOrNull()
                    ?.split(" ")
                    ?.getOrNull(1)
                    ?.toIntOrNull() ?: 0
            val headers = mutableMapOf<String, String>()
            for (i in 1 until lines.size) {
                val line = lines[i]
                val colon = line.indexOf(':')
                if (colon > 0) {
                    headers[line.substring(0, colon).lowercase()] = line.substring(colon + 1).trim()
                }
            }
            return RawResponse(status, headers, bodyPart, bodyBytes)
        }
    }

    @Test
    fun LS1_startTwice_bindsLoopbackAddress() =
        runBlocking {
            withTimeout(5_000) {
                val s = LoopbackServer(Dispatchers.IO)
                server = s
                val port1 = s.start()
                val port2 = s.start()
                assertEquals(port1, port2)
                assertTrue(port1 > 0)

                Socket("127.0.0.1", port1).use { client ->
                    assertEquals("127.0.0.1", client.inetAddress.hostAddress)
                    assertTrue(client.inetAddress.isLoopbackAddress)
                }
            }
        }

    @Test
    fun LS2_getMountedPrefix_returns200AndBody() =
        runBlocking {
            withTimeout(5_000) {
                val s = LoopbackServer(Dispatchers.IO)
                server = s
                s.mount(
                    "/test/",
                    object : HttpRoute {
                        override suspend fun handle(
                            request: HttpRequest,
                            response: HttpResponseWriter,
                        ) {
                            response.head(
                                200,
                                listOf(
                                    "Content-Length" to "2",
                                    "Connection" to "close",
                                ),
                            )
                            val bytes = "hi".toByteArray(Charsets.US_ASCII)
                            response.body(bytes, 0, bytes.size)
                        }
                    },
                )
                val port = s.start()
                val res = sendRaw(port, "GET /test/hello HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n")
                assertEquals(200, res.status)
                assertEquals("hi", res.body)
            }
        }

    @Test
    fun LS3_noMount_returns404() =
        runBlocking {
            withTimeout(5_000) {
                val s = LoopbackServer(Dispatchers.IO)
                server = s
                val port = s.start()
                val res = sendRaw(port, "GET /unmounted/x HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n")
                assertEquals(404, res.status)
                assertEquals("0", res.headers["content-length"])
                assertEquals("close", res.headers["connection"])
            }
        }

    @Test
    fun LS4_malformedRequestLine_returns400() =
        runBlocking {
            withTimeout(5_000) {
                val s = LoopbackServer(Dispatchers.IO)
                server = s
                val port = s.start()
                val res = sendRaw(port, "HELLO\r\n\r\n")
                assertEquals(400, res.status)
            }
        }

    @Test
    fun LS5_oversizeHead_returns400AndServerAcceptsLaterRequest() =
        runBlocking {
            withTimeout(5_000) {
                val s = LoopbackServer(Dispatchers.IO)
                server = s
                s.mount(
                    "/ok/",
                    object : HttpRoute {
                        override suspend fun handle(
                            request: HttpRequest,
                            response: HttpResponseWriter,
                        ) {
                            response.head(200, listOf("Content-Length" to "0", "Connection" to "close"))
                        }
                    },
                )
                val port = s.start()

                val largeHead = "GET / HTTP/1.1\r\nX-Large: " + "a".repeat(MAX_HEAD_BYTES) + "\r\n\r\n"
                val resBad = sendRaw(port, largeHead)
                assertEquals(400, resBad.status)

                val resGood = sendRaw(port, "GET /ok/ HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n")
                assertEquals(200, resGood.status)
            }
        }

    @Test
    fun LS6_headTimeout_closesConnectionWithinOneSecond() =
        runBlocking {
            withTimeout(5_000) {
                val s = LoopbackServer(Dispatchers.IO, ServerConfig(headTimeoutMs = 300))
                server = s
                val port = s.start()

                val start = System.currentTimeMillis()
                Socket("127.0.0.1", port).use { socket ->
                    socket.soTimeout = 2000
                    socket.getOutputStream().write("GET /half-head".toByteArray(Charsets.US_ASCII))
                    socket.getOutputStream().flush()

                    try {
                        val b = socket.getInputStream().read()
                        assertEquals(-1, b)
                    } catch (_: SocketTimeoutException) {
                        fail("Should have closed connection, but timed out on client")
                    }
                }
                val elapsed = System.currentTimeMillis() - start
                assertTrue("Expected close within about 1s, took $elapsed ms", elapsed < 1500)
            }
        }

    @Test
    fun LS7_longestPrefixWins() =
        runBlocking {
            withTimeout(5_000) {
                val s = LoopbackServer(Dispatchers.IO)
                server = s
                s.mount(
                    "/t/",
                    object : HttpRoute {
                        override suspend fun handle(
                            request: HttpRequest,
                            response: HttpResponseWriter,
                        ) {
                            response.head(
                                200,
                                listOf("X-Route" to "short", "Content-Length" to "0", "Connection" to "close"),
                            )
                        }
                    },
                )
                s.mount(
                    "/t/abc/",
                    object : HttpRoute {
                        override suspend fun handle(
                            request: HttpRequest,
                            response: HttpResponseWriter,
                        ) {
                            response.head(
                                200,
                                listOf("X-Route" to "long", "Content-Length" to "0", "Connection" to "close"),
                            )
                        }
                    },
                )
                val port = s.start()

                val resLong = sendRaw(port, "GET /t/abc/x HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n")
                assertEquals("long", resLong.headers["x-route"])

                val resShort = sendRaw(port, "GET /t/zzz HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n")
                assertEquals("short", resShort.headers["x-route"])
            }
        }

    @Test
    fun LS8_unmount_returns404() =
        runBlocking {
            withTimeout(5_000) {
                val s = LoopbackServer(Dispatchers.IO)
                server = s
                s.mount(
                    "/test/",
                    object : HttpRoute {
                        override suspend fun handle(
                            request: HttpRequest,
                            response: HttpResponseWriter,
                        ) {
                            response.head(200, listOf("Content-Length" to "0", "Connection" to "close"))
                        }
                    },
                )
                val port = s.start()
                s.unmount("/test/")

                val res = sendRaw(port, "GET /test/x HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n")
                assertEquals(404, res.status)
            }
        }

    @Test
    fun LS9_parallelRequestsWaitOnLatch() =
        runBlocking {
            withTimeout(5_000) {
                val s = LoopbackServer(Dispatchers.IO)
                server = s
                val latch = CountDownLatch(1)
                s.mount(
                    "/parallel/",
                    object : HttpRoute {
                        override suspend fun handle(
                            request: HttpRequest,
                            response: HttpResponseWriter,
                        ) {
                            latch.await(3, TimeUnit.SECONDS)
                            response.head(200, listOf("Content-Length" to "0", "Connection" to "close"))
                        }
                    },
                )
                val port = s.start()

                val r1 = async(Dispatchers.IO) { sendRaw(port, "GET /parallel/1 HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n") }
                val r2 = async(Dispatchers.IO) { sendRaw(port, "GET /parallel/2 HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n") }
                val r3 = async(Dispatchers.IO) { sendRaw(port, "GET /parallel/3 HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n") }

                delay(100)
                latch.countDown()

                assertEquals(200, r1.await().status)
                assertEquals(200, r2.await().status)
                assertEquals(200, r3.await().status)
            }
        }

    @Test
    fun LS10_maxConnectionsExceeded_returns503AndRecovers() =
        runBlocking {
            withTimeout(5_000) {
                val s = LoopbackServer(Dispatchers.IO, ServerConfig(maxConnections = 2))
                server = s
                val releaseLatch = CountDownLatch(1)
                val startedLatch = CountDownLatch(2)

                s.mount(
                    "/hold/",
                    object : HttpRoute {
                        override suspend fun handle(
                            request: HttpRequest,
                            response: HttpResponseWriter,
                        ) {
                            startedLatch.countDown()
                            releaseLatch.await(3, TimeUnit.SECONDS)
                            response.head(200, listOf("Content-Length" to "0", "Connection" to "close"))
                        }
                    },
                )
                val port = s.start()

                val c1 = async(Dispatchers.IO) { sendRaw(port, "GET /hold/1 HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n") }
                val c2 = async(Dispatchers.IO) { sendRaw(port, "GET /hold/2 HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n") }

                assertTrue(startedLatch.await(2, TimeUnit.SECONDS))

                val c3 = sendRaw(port, "GET /hold/3 HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n")
                assertEquals(503, c3.status)
                assertEquals("1", c3.headers["retry-after"])

                releaseLatch.countDown()
                assertEquals(200, c1.await().status)
                assertEquals(200, c2.await().status)

                delay(50)
                val c4 = sendRaw(port, "GET /hold/4 HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n")
                assertEquals(200, c4.status)
            }
        }

    @Test
    fun LS11_clientDisconnectDuringLargeBody_serverRecovers() =
        runBlocking {
            withTimeout(5_000) {
                val s = LoopbackServer(Dispatchers.IO)
                server = s
                s.mount(
                    "/large/",
                    object : HttpRoute {
                        override suspend fun handle(
                            request: HttpRequest,
                            response: HttpResponseWriter,
                        ) {
                            response.head(200, listOf("Content-Length" to "10000000", "Connection" to "close"))
                            val chunk = ByteArray(65536)
                            while (true) {
                                response.body(chunk, 0, chunk.size)
                            }
                        }
                    },
                )
                s.mount(
                    "/ping/",
                    object : HttpRoute {
                        override suspend fun handle(
                            request: HttpRequest,
                            response: HttpResponseWriter,
                        ) {
                            response.head(200, listOf("Content-Length" to "0", "Connection" to "close"))
                        }
                    },
                )
                val port = s.start()

                Socket("127.0.0.1", port).use { socket ->
                    socket.getOutputStream().write(
                        "GET /large/ HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n".toByteArray(Charsets.US_ASCII),
                    )
                    socket.getOutputStream().flush()
                    val buf = ByteArray(1024)
                    socket.getInputStream().read(buf)
                    // Disconnect immediately
                }

                delay(100)
                val res = sendRaw(port, "GET /ping/ HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n")
                assertEquals(200, res.status)
            }
        }

    @Test
    fun LS12_stopDuringInFlightRequest_cancelsRouteAndRefusesConnectAndRestarts() =
        runBlocking {
            withTimeout(5_000) {
                val s = LoopbackServer(Dispatchers.IO)
                server = s
                val inFlightCancelled = CompletableDeferred<Boolean>()
                val connected = CompletableDeferred<Unit>()

                s.mount(
                    "/inflight/",
                    object : HttpRoute {
                        override suspend fun handle(
                            request: HttpRequest,
                            response: HttpResponseWriter,
                        ) {
                            connected.complete(Unit)
                            try {
                                delay(10_000)
                            } catch (e: CancellationException) {
                                inFlightCancelled.complete(true)
                                throw e
                            }
                        }
                    },
                )
                val port = s.start()

                val clientSocket = Socket("127.0.0.1", port)
                clientSocket.getOutputStream().write(
                    "GET /inflight/ HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n".toByteArray(Charsets.US_ASCII),
                )
                clientSocket.getOutputStream().flush()

                connected.await()
                s.stop()

                assertTrue(inFlightCancelled.await())
                try {
                    clientSocket.close()
                } catch (_: IOException) {
                }

                try {
                    Socket("127.0.0.1", port).close()
                    fail("Connection to stopped port should fail")
                } catch (_: ConnectException) {
                    // Expected
                }

                val newPort = s.start()
                assertTrue(newPort > 0)
            }
        }

    @Test
    fun LS13_routeThrowsBeforeOrAfterHead() =
        runBlocking {
            withTimeout(5_000) {
                val s = LoopbackServer(Dispatchers.IO)
                server = s
                s.mount(
                    "/throw-before/",
                    object : HttpRoute {
                        override suspend fun handle(
                            request: HttpRequest,
                            response: HttpResponseWriter,
                        ): Unit = error("Boom before head")
                    },
                )
                s.mount(
                    "/throw-after/",
                    object : HttpRoute {
                        override suspend fun handle(
                            request: HttpRequest,
                            response: HttpResponseWriter,
                        ) {
                            response.head(200, listOf("Content-Length" to "10", "Connection" to "close"))
                            error("Boom after head")
                        }
                    },
                )
                val port = s.start()

                val resBefore = sendRaw(port, "GET /throw-before/ HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n")
                assertEquals(500, resBefore.status)
                assertEquals("0", resBefore.headers["content-length"])

                val resAfter = sendRaw(port, "GET /throw-after/ HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n")
                assertEquals(200, resAfter.status)
            }
        }

    @Test
    fun LS14_http10Request() =
        runBlocking {
            withTimeout(5_000) {
                val s = LoopbackServer(Dispatchers.IO)
                server = s
                s.mount(
                    "/test/",
                    object : HttpRoute {
                        override suspend fun handle(
                            request: HttpRequest,
                            response: HttpResponseWriter,
                        ) {
                            response.head(200, listOf("Content-Length" to "0", "Connection" to "close"))
                        }
                    },
                )
                val port = s.start()
                val res = sendRaw(port, "GET /test/ HTTP/1.0\r\n\r\n")
                assertEquals(200, res.status)
            }
        }

    @Test
    fun LS15_targetWithQueryParams_matchesMount() =
        runBlocking {
            withTimeout(5_000) {
                val s = LoopbackServer(Dispatchers.IO)
                server = s
                s.mount(
                    "/t/abc/",
                    object : HttpRoute {
                        override suspend fun handle(
                            request: HttpRequest,
                            response: HttpResponseWriter,
                        ) {
                            response.head(
                                200,
                                listOf("X-Matched" to "yes", "Content-Length" to "0", "Connection" to "close"),
                            )
                        }
                    },
                )
                val port = s.start()
                val res = sendRaw(port, "GET /t/abc/x?y=1 HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n")
                assertEquals(200, res.status)
                assertEquals("yes", res.headers["x-matched"])
            }
        }

    @Test
    fun LS16_rangeRoute_partialContentOverSocket() =
        runBlocking {
            withTimeout(5_000) {
                val s = LoopbackServer(Dispatchers.IO)
                server = s
                val data = ByteArray(1000) { (it % 100).toByte() }
                val source = InMemorySource(data)
                val route = RangeRoute(source)
                s.mount("/stream/", route)
                val port = s.start()

                val res =
                    sendRaw(
                        port,
                        "GET /stream/file.mp4 HTTP/1.1\r\nHost: 127.0.0.1\r\nRange: bytes=10-19\r\n\r\n",
                    )
                assertEquals(206, res.status)
                assertEquals("bytes 10-19/1000", res.headers["content-range"])
                assertEquals("10", res.headers["content-length"])
                val expected = data.copyOfRange(10, 20)
                assertArrayEquals(expected, res.bodyBytes)
            }
        }

    @Test
    fun LS17_rangeRoute_headNoRangeOverSocket() =
        runBlocking {
            withTimeout(5_000) {
                val s = LoopbackServer(Dispatchers.IO)
                server = s
                val data = ByteArray(1000) { (it % 100).toByte() }
                val source = InMemorySource(data)
                val route = RangeRoute(source)
                s.mount("/stream/", route)
                val port = s.start()

                val res =
                    sendRaw(
                        port,
                        "HEAD /stream/file.mp4 HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n",
                    )
                assertEquals(200, res.status)
                assertEquals("1000", res.headers["content-length"])
                assertEquals("bytes", res.headers["accept-ranges"])
                assertEquals(0, res.bodyBytes.size)
            }
        }

    @Test
    fun LS18_rangeRoute_postMethodNotAllowedOverSocket() =
        runBlocking {
            withTimeout(5_000) {
                val s = LoopbackServer(Dispatchers.IO)
                server = s
                val data = ByteArray(1000) { (it % 100).toByte() }
                val source = InMemorySource(data)
                val route = RangeRoute(source)
                s.mount("/stream/", route)
                val port = s.start()

                val res =
                    sendRaw(
                        port,
                        "POST /stream/file.mp4 HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n",
                    )
                assertEquals(405, res.status)
                assertEquals("GET, HEAD", res.headers["allow"])
            }
        }

    private class InMemorySource(
        private val data: ByteArray,
        override val contentType: String = "video/mp4",
    ) : RangeByteSource {
        override val sizeBytes: Long = data.size.toLong()

        override suspend fun read(
            position: Long,
            buffer: ByteArray,
            offset: Int,
            length: Int,
        ): Int {
            val pos = position.toInt()
            return if (pos >= data.size) {
                -1
            } else {
                val toRead = minOf(length, data.size - pos)
                System.arraycopy(data, pos, buffer, offset, toRead)
                toRead
            }
        }
    }
}
