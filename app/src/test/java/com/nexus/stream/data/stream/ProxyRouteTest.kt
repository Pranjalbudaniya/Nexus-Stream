package com.nexus.stream.data.stream

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.InputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@Suppress("LargeClass")
class ProxyRouteTest {
    private val proxyServer = LoopbackServer(Dispatchers.IO, ServerConfig(maxConnections = 50))
    private val upstreamServer = LoopbackServer(Dispatchers.IO, ServerConfig(maxConnections = 50))
    private var secondUpstreamServer: LoopbackServer? = null
    private val testClient =
        OkHttpClient
            .Builder()
            .followRedirects(false)
            .callTimeout(5, TimeUnit.SECONDS)
            .build()

    private var proxyPort = 0
    private var upstreamPort = 0

    @Before
    fun setUp() {
        proxyPort = proxyServer.start()
        upstreamPort = upstreamServer.start()
    }

    @After
    fun tearDown() {
        proxyServer.stop()
        upstreamServer.stop()
        secondUpstreamServer?.stop()
    }

    private fun createProxyClient(
        readTimeoutMs: Long = 10_000,
        jar: SessionCookieJar = SessionCookieJar(),
    ): OkHttpClient =
        OkHttpClient
            .Builder()
            .apply {
                interceptors().clear()
                networkInterceptors().clear()
                cache(null)
                cookieJar(jar)
                callTimeout(0, TimeUnit.MILLISECONDS)
                connectTimeout(5_000, TimeUnit.MILLISECONDS)
                readTimeout(readTimeoutMs, TimeUnit.MILLISECONDS)
                followRedirects(true)
                followSslRedirects(true)
                addNetworkInterceptor(createLoopbackGuardInterceptor(proxyPort))
            }.build()

    @Test
    fun PT1_getDeterministic1MbBody_sameBytesStatus200HeadersForwarded() {
        val deterministicBody = ByteArray(1024 * 1024) { (it % 251).toByte() }
        val upstreamReceivedHeaders = ConcurrentHashMap<String, String>()

        upstreamServer.mount(
            "/video/",
            object : HttpRoute {
                override suspend fun handle(
                    request: HttpRequest,
                    response: HttpResponseWriter,
                ) {
                    upstreamReceivedHeaders.putAll(request.headers)
                    response.head(
                        200,
                        listOf(
                            "Content-Type" to "video/mp4",
                            "Content-Length" to deterministicBody.size.toString(),
                            "Connection" to "close",
                        ),
                    )
                    response.body(deterministicBody, 0, deterministicBody.size)
                }
            },
        )

        val streamHeaders = mapOf("X-Test" to "test-val", "Referer" to "https://ref.com")
        val route =
            ProxyRoute(
                upstream = "http://127.0.0.1:$upstreamPort/video/movie.mp4".toHttpUrl(),
                streamHeaders = streamHeaders,
                client = createProxyClient(),
                expectedPath = "/p/token1/movie.mp4",
                defaultUserAgent = "DefaultUA",
                io = Dispatchers.IO,
            )
        proxyServer.mount("/p/token1/", route)

        val request = Request.Builder().url("http://127.0.0.1:$proxyPort/p/token1/movie.mp4").build()
        val response = testClient.newCall(request).execute()

        assertEquals(200, response.code)
        assertEquals("video/mp4", response.header("Content-Type"))
        assertEquals(deterministicBody.size.toString(), response.header("Content-Length"))
        val bodyBytes = response.body.bytes()
        assertArrayEquals(deterministicBody, bodyBytes)

        assertEquals("test-val", upstreamReceivedHeaders["x-test"])
        assertEquals("https://ref.com", upstreamReceivedHeaders["referer"])
        assertEquals("identity", upstreamReceivedHeaders["accept-encoding"])
    }

    @Test
    fun PT2_playerRange_upstreamGotIt_206AndContentRangePassThrough() {
        val tenBytes = byteArrayOf(10, 11, 12, 13, 14, 15, 16, 17, 18, 19)
        val receivedRange = CompletableDeferred<String?>()

        upstreamServer.mount(
            "/stream/",
            object : HttpRoute {
                override suspend fun handle(
                    request: HttpRequest,
                    response: HttpResponseWriter,
                ) {
                    receivedRange.complete(request.headers["range"])
                    response.head(
                        206,
                        listOf(
                            "Content-Type" to "video/mp4",
                            "Content-Range" to "bytes 10-19/1000",
                            "Content-Length" to "10",
                            "Connection" to "close",
                        ),
                    )
                    response.body(tenBytes, 0, tenBytes.size)
                }
            },
        )

        val route =
            ProxyRoute(
                upstream = "http://127.0.0.1:$upstreamPort/stream/video.mp4".toHttpUrl(),
                streamHeaders = emptyMap(),
                client = createProxyClient(),
                expectedPath = "/p/tok/video.mp4",
                defaultUserAgent = "DefaultUA",
                io = Dispatchers.IO,
            )
        proxyServer.mount("/p/tok/", route)

        val request =
            Request
                .Builder()
                .url("http://127.0.0.1:$proxyPort/p/tok/video.mp4")
                .header("Range", "bytes=10-19")
                .build()
        val response = testClient.newCall(request).execute()

        assertEquals(206, response.code)
        assertEquals("bytes 10-19/1000", response.header("Content-Range"))
        assertEquals("10", response.header("Content-Length"))
        assertArrayEquals(tenBytes, response.body.bytes())
        assertEquals("bytes=10-19", runBlocking { receivedRange.await() })
    }

    @Test
    fun PT3_upstreamIgnoresRangeAndAnswers200_proxyAnswers200() {
        val hundredBytes = ByteArray(100) { it.toByte() }
        upstreamServer.mount(
            "/full/",
            object : HttpRoute {
                override suspend fun handle(
                    request: HttpRequest,
                    response: HttpResponseWriter,
                ) {
                    response.head(
                        200,
                        listOf(
                            "Content-Type" to "video/mp4",
                            "Content-Length" to "100",
                            "Connection" to "close",
                        ),
                    )
                    response.body(hundredBytes, 0, hundredBytes.size)
                }
            },
        )

        val route =
            ProxyRoute(
                upstream = "http://127.0.0.1:$upstreamPort/full/video.mp4".toHttpUrl(),
                streamHeaders = emptyMap(),
                client = createProxyClient(),
                expectedPath = "/p/t/video.mp4",
                defaultUserAgent = "DefaultUA",
                io = Dispatchers.IO,
            )
        proxyServer.mount("/p/t/", route)

        val request =
            Request
                .Builder()
                .url("http://127.0.0.1:$proxyPort/p/t/video.mp4")
                .header("Range", "bytes=10-19")
                .build()
        val response = testClient.newCall(request).execute()

        assertEquals(200, response.code)
        assertNull(response.header("Content-Range"))
        assertArrayEquals(hundredBytes, response.body.bytes())
    }

    @Test
    fun PT4_redirectWithPortChange_authorizationDropped_xTestKept() {
        val secondServer = LoopbackServer(Dispatchers.IO)
        secondUpstreamServer = secondServer
        val secondPort = secondServer.start()

        val hop1Headers = ConcurrentHashMap<String, String>()
        val hop2Headers = ConcurrentHashMap<String, String>()

        upstreamServer.mount(
            "/hop1/",
            object : HttpRoute {
                override suspend fun handle(
                    request: HttpRequest,
                    response: HttpResponseWriter,
                ) {
                    hop1Headers.putAll(request.headers)
                    response.head(
                        302,
                        listOf(
                            "Location" to "http://127.0.0.1:$secondPort/hop2/final.mp4",
                            "Connection" to "close",
                        ),
                    )
                }
            },
        )

        secondServer.mount(
            "/hop2/",
            object : HttpRoute {
                override suspend fun handle(
                    request: HttpRequest,
                    response: HttpResponseWriter,
                ) {
                    hop2Headers.putAll(request.headers)
                    response.head(
                        200,
                        listOf(
                            "Content-Type" to "video/mp4",
                            "Content-Length" to "4",
                            "Connection" to "close",
                        ),
                    )
                    response.body("done".toByteArray(), 0, 4)
                }
            },
        )

        val streamHeaders =
            mapOf(
                "Authorization" to "Bearer secret",
                "X-Test" to "kept-val",
            )

        val route =
            ProxyRoute(
                upstream = "http://127.0.0.1:$upstreamPort/hop1/start.mp4".toHttpUrl(),
                streamHeaders = streamHeaders,
                client = createProxyClient(),
                expectedPath = "/p/t4/start.mp4",
                defaultUserAgent = "DefaultUA",
                io = Dispatchers.IO,
            )
        proxyServer.mount("/p/t4/", route)

        val request = Request.Builder().url("http://127.0.0.1:$proxyPort/p/t4/start.mp4").build()
        val response = testClient.newCall(request).execute()

        assertEquals(200, response.code)
        assertEquals("done", response.body.string())

        assertEquals("Bearer secret", hop1Headers["authorization"])
        assertEquals("kept-val", hop1Headers["x-test"])

        assertNull(hop2Headers["authorization"])
        assertEquals("kept-val", hop2Headers["x-test"])
    }

    @Test
    fun PT5_cookieSeedingAndRedirectSetCookie() {
        val hop1Cookies = CompletableDeferred<String?>()
        val hop2Cookies = CompletableDeferred<String?>()

        upstreamServer.mount(
            "/step1/",
            object : HttpRoute {
                override suspend fun handle(
                    request: HttpRequest,
                    response: HttpResponseWriter,
                ) {
                    hop1Cookies.complete(request.headers["cookie"])
                    response.head(
                        302,
                        listOf(
                            "Set-Cookie" to "s=1; Path=/",
                            "Location" to "/step2/target.mp4",
                            "Connection" to "close",
                        ),
                    )
                }
            },
        )

        upstreamServer.mount(
            "/step2/",
            object : HttpRoute {
                override suspend fun handle(
                    request: HttpRequest,
                    response: HttpResponseWriter,
                ) {
                    hop2Cookies.complete(request.headers["cookie"])
                    response.head(
                        200,
                        listOf(
                            "Content-Type" to "video/mp4",
                            "Content-Length" to "2",
                            "Connection" to "close",
                        ),
                    )
                    response.body("ok".toByteArray(), 0, 2)
                }
            },
        )

        val jar = SessionCookieJar()
        jar.seed("127.0.0.1", "a=b")

        val route =
            ProxyRoute(
                upstream = "http://127.0.0.1:$upstreamPort/step1/first.mp4".toHttpUrl(),
                streamHeaders = mapOf("Cookie" to "a=b"),
                client = createProxyClient(jar = jar),
                expectedPath = "/p/t5/first.mp4",
                defaultUserAgent = "DefaultUA",
                io = Dispatchers.IO,
            )
        proxyServer.mount("/p/t5/", route)

        val request = Request.Builder().url("http://127.0.0.1:$proxyPort/p/t5/first.mp4").build()
        val response = testClient.newCall(request).execute()

        assertEquals(200, response.code)
        assertNull(response.header("Set-Cookie"))

        val firstHopCookie = runBlocking { hop1Cookies.await() }
        assertEquals("a=b", firstHopCookie)

        val secondHopCookie = runBlocking { hop2Cookies.await() }
        assertTrue(secondHopCookie != null && secondHopCookie.contains("s=1"))
        assertTrue(secondHopCookie != null && secondHopCookie.contains("a=b"))
    }

    @Test
    fun PT6_upstreamErrorsRelayedWithoutBody() {
        upstreamServer.mount(
            "/error403/",
            object : HttpRoute {
                override suspend fun handle(
                    request: HttpRequest,
                    response: HttpResponseWriter,
                ) {
                    response.head(
                        403,
                        listOf(
                            "Content-Type" to "text/plain",
                            "Content-Length" to "9",
                            "Connection" to "close",
                        ),
                    )
                    response.body("Forbidden".toByteArray(), 0, 9)
                }
            },
        )

        upstreamServer.mount(
            "/error416/",
            object : HttpRoute {
                override suspend fun handle(
                    request: HttpRequest,
                    response: HttpResponseWriter,
                ) {
                    response.head(
                        416,
                        listOf(
                            "Content-Range" to "bytes */500",
                            "Content-Length" to "12",
                            "Connection" to "close",
                        ),
                    )
                    response.body("Range Error".toByteArray(), 0, 12)
                }
            },
        )

        val route403 =
            ProxyRoute(
                upstream = "http://127.0.0.1:$upstreamPort/error403/video.mp4".toHttpUrl(),
                streamHeaders = emptyMap(),
                client = createProxyClient(),
                expectedPath = "/p/t6/video.mp4",
                defaultUserAgent = "DefaultUA",
                io = Dispatchers.IO,
            )
        proxyServer.mount("/p/t6/", route403)

        val response403 =
            testClient.newCall(Request.Builder().url("http://127.0.0.1:$proxyPort/p/t6/video.mp4").build()).execute()
        assertEquals(403, response403.code)
        assertEquals("0", response403.header("Content-Length"))
        assertEquals("", response403.body.string())

        val route416 =
            ProxyRoute(
                upstream = "http://127.0.0.1:$upstreamPort/error416/video.mp4".toHttpUrl(),
                streamHeaders = emptyMap(),
                client = createProxyClient(),
                expectedPath = "/p/t6b/video.mp4",
                defaultUserAgent = "DefaultUA",
                io = Dispatchers.IO,
            )
        proxyServer.mount("/p/t6b/", route416)

        val response416 =
            testClient.newCall(Request.Builder().url("http://127.0.0.1:$proxyPort/p/t6b/video.mp4").build()).execute()
        assertEquals(416, response416.code)
        assertEquals("bytes */500", response416.header("Content-Range"))
        assertEquals("0", response416.header("Content-Length"))
        assertEquals("", response416.body.string())
    }

    @Test
    fun PT7_connectionRefusedGives502_timeoutGives504() {
        val unusedSocket = ServerSocket(0)
        val deadPort = unusedSocket.localPort
        unusedSocket.close()

        val routeRefused =
            ProxyRoute(
                upstream = "http://127.0.0.1:$deadPort/nowhere".toHttpUrl(),
                streamHeaders = emptyMap(),
                client = createProxyClient(),
                expectedPath = "/p/refused/video.mp4",
                defaultUserAgent = "DefaultUA",
                io = Dispatchers.IO,
            )
        proxyServer.mount("/p/refused/", routeRefused)

        val response502 =
            testClient
                .newCall(
                    Request.Builder().url("http://127.0.0.1:$proxyPort/p/refused/video.mp4").build(),
                ).execute()
        assertEquals(502, response502.code)
        assertEquals("0", response502.header("Content-Length"))

        upstreamServer.mount(
            "/sleep/",
            object : HttpRoute {
                override suspend fun handle(
                    request: HttpRequest,
                    response: HttpResponseWriter,
                ) {
                    delay(2_000)
                }
            },
        )

        val routeTimeout =
            ProxyRoute(
                upstream = "http://127.0.0.1:$upstreamPort/sleep/video.mp4".toHttpUrl(),
                streamHeaders = emptyMap(),
                client = createProxyClient(readTimeoutMs = 300),
                expectedPath = "/p/timeout/video.mp4",
                defaultUserAgent = "DefaultUA",
                io = Dispatchers.IO,
            )
        proxyServer.mount("/p/timeout/", routeTimeout)

        val response504 =
            testClient
                .newCall(
                    Request.Builder().url("http://127.0.0.1:$proxyPort/p/timeout/video.mp4").build(),
                ).execute()
        assertEquals(504, response504.code)
        assertEquals("0", response504.header("Content-Length"))
    }

    @Test
    fun PT8_wrongSubpathGives404_postGives405() {
        val route =
            ProxyRoute(
                upstream = "http://127.0.0.1:$upstreamPort/video.mp4".toHttpUrl(),
                streamHeaders = emptyMap(),
                client = createProxyClient(),
                expectedPath = "/p/tok8/video.mp4",
                defaultUserAgent = "DefaultUA",
                io = Dispatchers.IO,
            )
        proxyServer.mount("/p/tok8/", route)

        val response404 =
            testClient.newCall(Request.Builder().url("http://127.0.0.1:$proxyPort/p/tok8/other.mp4").build()).execute()
        assertEquals(404, response404.code)
        assertEquals("0", response404.header("Content-Length"))

        val postRequest =
            Request
                .Builder()
                .url("http://127.0.0.1:$proxyPort/p/tok8/video.mp4")
                .post("payload".toRequestBody("text/plain".toMediaType()))
                .build()
        val response405 = testClient.newCall(postRequest).execute()
        assertEquals(405, response405.code)
        assertEquals("GET, HEAD", response405.header("Allow"))
        assertEquals("0", response405.header("Content-Length"))
    }

    @Test
    fun PT9_headRequest_forwardsHead_noBody() {
        val upstreamReceivedMethod = CompletableDeferred<String>()
        upstreamServer.mount(
            "/headtest/",
            object : HttpRoute {
                override suspend fun handle(
                    request: HttpRequest,
                    response: HttpResponseWriter,
                ) {
                    upstreamReceivedMethod.complete(request.method)
                    response.head(
                        200,
                        listOf(
                            "Content-Type" to "video/mp4",
                            "Content-Length" to "12345",
                            "Connection" to "close",
                        ),
                    )
                }
            },
        )

        val route =
            ProxyRoute(
                upstream = "http://127.0.0.1:$upstreamPort/headtest/movie.mp4".toHttpUrl(),
                streamHeaders = emptyMap(),
                client = createProxyClient(),
                expectedPath = "/p/t9/movie.mp4",
                defaultUserAgent = "DefaultUA",
                io = Dispatchers.IO,
            )
        proxyServer.mount("/p/t9/", route)

        val request =
            Request
                .Builder()
                .url("http://127.0.0.1:$proxyPort/p/t9/movie.mp4")
                .head()
                .build()
        val response = testClient.newCall(request).execute()

        assertEquals(200, response.code)
        assertEquals("12345", response.header("Content-Length"))
        assertEquals("HEAD", runBlocking { upstreamReceivedMethod.await() })
        assertEquals(0, response.body.bytes().size)
    }

    @Test
    fun PT10_playerClosesSocketEarly_upstreamCancelsWithin2s() {
        val upstreamEnded = CompletableDeferred<Unit>()
        val largeBlock = ByteArray(64 * 1024) { 1 }

        upstreamServer.mount(
            "/large/",
            object : HttpRoute {
                override suspend fun handle(
                    request: HttpRequest,
                    response: HttpResponseWriter,
                ) {
                    try {
                        response.head(
                            200,
                            listOf(
                                "Content-Type" to "application/octet-stream",
                                "Content-Length" to (64 * 1024 * 100).toString(),
                                "Connection" to "close",
                            ),
                        )
                        while (true) {
                            response.body(largeBlock, 0, largeBlock.size)
                            delay(50)
                        }
                    } catch (_: Exception) {
                        upstreamEnded.complete(Unit)
                    } finally {
                        upstreamEnded.complete(Unit)
                    }
                }
            },
        )

        val route =
            ProxyRoute(
                upstream = "http://127.0.0.1:$upstreamPort/large/stream.bin".toHttpUrl(),
                streamHeaders = emptyMap(),
                client = createProxyClient(),
                expectedPath = "/p/t10/stream.bin",
                defaultUserAgent = "DefaultUA",
                io = Dispatchers.IO,
            )
        proxyServer.mount("/p/t10/", route)

        val socket = Socket("127.0.0.1", proxyPort)
        socket.soTimeout = 3_000
        val out = socket.getOutputStream()
        val inStream = socket.getInputStream()
        out.write("GET /p/t10/stream.bin HTTP/1.1\r\nHost: 127.0.0.1\r\nConnection: close\r\n\r\n".toByteArray())
        out.flush()

        val buf = ByteArray(1024)
        var totalRead = 0
        while (totalRead < 1024) {
            val r = inStream.read(buf, 0, 1024 - totalRead)
            if (r == -1) break
            totalRead += r
        }
        socket.close()

        runBlocking {
            withTimeout(2_000) {
                upstreamEnded.await()
            }
        }
    }

    @Test
    fun PT11_chunkedUpstream_relayedUntilClose() {
        val chunk1 = "Hello ".toByteArray()
        val chunk2 = "world!".toByteArray()

        upstreamServer.mount(
            "/chunked/",
            object : HttpRoute {
                override suspend fun handle(
                    request: HttpRequest,
                    response: HttpResponseWriter,
                ) {
                    response.head(
                        200,
                        listOf(
                            "Content-Type" to "text/plain",
                            "Connection" to "close",
                        ),
                    )
                    response.body(chunk1, 0, chunk1.size)
                    response.body(chunk2, 0, chunk2.size)
                }
            },
        )

        val route =
            ProxyRoute(
                upstream = "http://127.0.0.1:$upstreamPort/chunked/msg.txt".toHttpUrl(),
                streamHeaders = emptyMap(),
                client = createProxyClient(),
                expectedPath = "/p/t11/msg.txt",
                defaultUserAgent = "DefaultUA",
                io = Dispatchers.IO,
            )
        proxyServer.mount("/p/t11/", route)

        val request = Request.Builder().url("http://127.0.0.1:$proxyPort/p/t11/msg.txt").build()
        val response = testClient.newCall(request).execute()

        assertEquals(200, response.code)
        assertNull(response.header("Content-Length"))
        assertEquals("close", response.header("Connection"))
        assertEquals("Hello world!", response.body.string())
    }

    @Test
    fun PT12_redirectOntoOwnPort_gives502() {
        upstreamServer.mount(
            "/selfloop/",
            object : HttpRoute {
                override suspend fun handle(
                    request: HttpRequest,
                    response: HttpResponseWriter,
                ) {
                    response.head(
                        302,
                        listOf(
                            "Location" to "http://127.0.0.1:$proxyPort/p/t12/loop.mp4",
                            "Connection" to "close",
                        ),
                    )
                }
            },
        )

        val route =
            ProxyRoute(
                upstream = "http://127.0.0.1:$upstreamPort/selfloop/loop.mp4".toHttpUrl(),
                streamHeaders = emptyMap(),
                client = createProxyClient(),
                expectedPath = "/p/t12/loop.mp4",
                defaultUserAgent = "DefaultUA",
                io = Dispatchers.IO,
            )
        proxyServer.mount("/p/t12/", route)

        val request = Request.Builder().url("http://127.0.0.1:$proxyPort/p/t12/loop.mp4").build()
        val response = testClient.newCall(request).execute()

        assertEquals(502, response.code)
    }

    @Test
    fun PT13_playerUserAgentReachesUpstreamWhenStreamHeadersEmpty() {
        val receivedUa = CompletableDeferred<String?>()

        upstreamServer.mount(
            "/ua/",
            object : HttpRoute {
                override suspend fun handle(
                    request: HttpRequest,
                    response: HttpResponseWriter,
                ) {
                    receivedUa.complete(request.headers["user-agent"])
                    response.head(
                        200,
                        listOf("Content-Length" to "0", "Connection" to "close"),
                    )
                }
            },
        )

        val route =
            ProxyRoute(
                upstream = "http://127.0.0.1:$upstreamPort/ua/video.mp4".toHttpUrl(),
                streamHeaders = emptyMap(),
                client = createProxyClient(),
                expectedPath = "/p/t13/video.mp4",
                defaultUserAgent = "DefaultUA",
                io = Dispatchers.IO,
            )
        proxyServer.mount("/p/t13/", route)

        val request =
            Request
                .Builder()
                .url("http://127.0.0.1:$proxyPort/p/t13/video.mp4")
                .header("User-Agent", "CustomPlayer/2.5")
                .build()
        val response = testClient.newCall(request).execute()

        assertEquals(200, response.code)
        assertEquals("CustomPlayer/2.5", runBlocking { receivedUa.await() })
    }

    @Test
    fun PT14_concurrent20Gets_allCompleteWithCorrectBytes() {
        runBlocking(Dispatchers.IO) {
            val payload = ByteArray(32 * 1024) { (it % 127).toByte() }

            upstreamServer.mount(
                "/concurrent/",
                object : HttpRoute {
                    override suspend fun handle(
                        request: HttpRequest,
                        response: HttpResponseWriter,
                    ) {
                        response.head(
                            200,
                            listOf(
                                "Content-Type" to "application/octet-stream",
                                "Content-Length" to payload.size.toString(),
                                "Connection" to "close",
                            ),
                        )
                        response.body(payload, 0, payload.size)
                    }
                },
            )

            val route =
                ProxyRoute(
                    upstream = "http://127.0.0.1:$upstreamPort/concurrent/test.bin".toHttpUrl(),
                    streamHeaders = emptyMap(),
                    client = createProxyClient(),
                    expectedPath = "/p/t14/test.bin",
                    defaultUserAgent = "DefaultUA",
                    io = Dispatchers.IO,
                )
            proxyServer.mount("/p/t14/", route)

            val deferreds =
                (1..20).map {
                    async {
                        val req = Request.Builder().url("http://127.0.0.1:$proxyPort/p/t14/test.bin").build()
                        val resp = testClient.newCall(req).execute()
                        assertEquals(200, resp.code)
                        val bytes = resp.body.bytes()
                        assertArrayEquals(payload, bytes)
                    }
                }
            deferreds.awaitAll()
        }
    }

    @Test
    fun CJ1_seededHostOnlyCookieMatchesOnlyThatHost() {
        val jar = SessionCookieJar()
        jar.seed("example.org", "auth=token123")

        val match = jar.loadForRequest("https://example.org/path".toHttpUrl())
        assertEquals(1, match.size)
        assertEquals("auth", match[0].name)
        assertEquals("token123", match[0].value)

        val subMatch = jar.loadForRequest("https://sub.example.org/path".toHttpUrl())
        assertTrue(subMatch.isEmpty())

        val otherMatch = jar.loadForRequest("https://other.org/path".toHttpUrl())
        assertTrue(otherMatch.isEmpty())
    }

    @Test
    fun CJ2_expiredCookieNotReturned() {
        var currentTime = 1_000L
        val jar = SessionCookieJar(clock = { currentTime })

        val cookie =
            Cookie
                .Builder()
                .name("sess")
                .value("val")
                .domain("example.org")
                .path("/")
                .expiresAt(5_000L)
                .build()
        jar.addCookieForTesting(cookie)

        val active = jar.loadForRequest("https://example.org/".toHttpUrl())
        assertEquals(1, active.size)

        currentTime = 6_000L
        val expired = jar.loadForRequest("https://example.org/".toHttpUrl())
        assertTrue(expired.isEmpty())
    }

    @Test
    fun CJ3_sameNameDomainPathReplaces() {
        val jar = SessionCookieJar()
        val c1 =
            Cookie
                .Builder()
                .name("session")
                .value("first")
                .domain("example.org")
                .path("/")
                .build()
        val c2 =
            Cookie
                .Builder()
                .name("session")
                .value("second")
                .domain("example.org")
                .path("/")
                .build()

        jar.saveFromResponse("https://example.org/".toHttpUrl(), listOf(c1))
        val list1 = jar.loadForRequest("https://example.org/".toHttpUrl())
        assertEquals("first", list1[0].value)

        jar.saveFromResponse("https://example.org/".toHttpUrl(), listOf(c2))
        val list2 = jar.loadForRequest("https://example.org/".toHttpUrl())
        assertEquals(1, list2.size)
        assertEquals("second", list2[0].value)
    }

    @Test
    fun CJ4_max50CookiesDropsOldest() {
        val jar = SessionCookieJar()
        val cookies =
            (1..55).map { i ->
                Cookie
                    .Builder()
                    .name("cookie_$i")
                    .value("v_$i")
                    .domain("example.org")
                    .path("/")
                    .build()
            }

        jar.saveFromResponse("https://example.org/".toHttpUrl(), cookies)
        val loaded = jar.loadForRequest("https://example.org/".toHttpUrl())

        assertEquals(50, loaded.size)
        // 1..5 should be dropped, 6..55 should remain
        assertNull(loaded.find { it.name == "cookie_1" })
        assertNull(loaded.find { it.name == "cookie_5" })
        assertEquals("v_6", loaded.find { it.name == "cookie_6" }?.value)
        assertEquals("v_55", loaded.find { it.name == "cookie_55" }?.value)
    }

    @Test
    fun CJ5_twoJarsAreIndependent() {
        val jar1 = SessionCookieJar()
        val jar2 = SessionCookieJar()

        jar1.seed("example.org", "c1=v1")
        jar2.seed("example.org", "c2=v2")

        val res1 = jar1.loadForRequest("https://example.org/".toHttpUrl())
        val res2 = jar2.loadForRequest("https://example.org/".toHttpUrl())

        assertEquals(listOf("c1"), res1.map { it.name })
        assertEquals(listOf("c2"), res2.map { it.name })
    }

    @Test
    fun PT15_proxyRoute_diagnostics_recordsStatusAndUnsupportedPath() {
        val diagnosticsFlow =
            kotlinx.coroutines.flow.MutableStateFlow(
                com.nexus.stream.domain.repository
                    .ProxyDiagnostics(),
            )
        val upstreamUrl = "http://127.0.0.1:$upstreamPort/video/video.mp4".toHttpUrl()
        val client = createProxyClient()
        val expectedPath = "/p/token/video.mp4"
        val route =
            ProxyRoute(
                upstream = upstreamUrl,
                streamHeaders = emptyMap(),
                client = client,
                expectedPath = expectedPath,
                defaultUserAgent = "TestUA",
                io = Dispatchers.IO,
                diagnostics = diagnosticsFlow,
            )
        proxyServer.mount("/p/token/", route)

        // 1. After a 403 upstream
        upstreamServer.mount(
            "/video/",
            object : HttpRoute {
                override suspend fun handle(
                    request: HttpRequest,
                    response: HttpResponseWriter,
                ) {
                    response.head(403, listOf("Content-Length" to "0", "Connection" to "close"))
                }
            },
        )
        val res403 =
            testClient
                .newCall(
                    Request.Builder().url("http://127.0.0.1:$proxyPort$expectedPath").build(),
                ).execute()
        res403.close()
        assertEquals(403, diagnosticsFlow.value.lastUpstreamStatus)

        // 2. After a wrong sub-path
        val resWrongPath =
            testClient
                .newCall(
                    Request.Builder().url("http://127.0.0.1:$proxyPort/p/token/segment0.ts").build(),
                ).execute()
        resWrongPath.close()
        assertEquals(1, diagnosticsFlow.value.unsupportedPathRequests)

        // 3. After a refused connection
        val deadPort = ServerSocket(0).use { it.localPort }
        val deadUrl = "http://127.0.0.1:$deadPort/video.mp4".toHttpUrl()
        val deadRoute =
            ProxyRoute(
                upstream = deadUrl,
                streamHeaders = emptyMap(),
                client = client,
                expectedPath = expectedPath,
                defaultUserAgent = "TestUA",
                io = Dispatchers.IO,
                diagnostics = diagnosticsFlow,
            )
        proxyServer.mount("/p/token/", deadRoute)
        val res502 =
            testClient
                .newCall(
                    Request.Builder().url("http://127.0.0.1:$proxyPort$expectedPath").build(),
                ).execute()
        res502.close()
        assertEquals(502, diagnosticsFlow.value.lastUpstreamStatus)
    }
}
