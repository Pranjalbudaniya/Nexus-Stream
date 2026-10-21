package com.nexus.stream.data.stream

import kotlinx.coroutines.Dispatchers
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class LoopbackStreamProxyTest {
    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val proxyServer = LoopbackServer(Dispatchers.IO)
    private val upstreamServer = LoopbackServer(Dispatchers.IO)
    private val testClient =
        OkHttpClient
            .Builder()
            .followRedirects(false)
            .callTimeout(5, TimeUnit.SECONDS)
            .build()

    private lateinit var baseClient: OkHttpClient
    private lateinit var proxy: LoopbackStreamProxy
    private var proxyPort = 0
    private var upstreamPort = 0

    @Before
    fun setUp() {
        proxyPort = proxyServer.start()
        upstreamPort = upstreamServer.start()

        val cacheDir = File(tmpFolder.root, "http_cache")
        cacheDir.mkdirs()

        baseClient =
            OkHttpClient
                .Builder()
                .addInterceptor { chain -> chain.proceed(chain.request()) }
                .cache(Cache(cacheDir, 10 * 1024 * 1024))
                .callTimeout(20, TimeUnit.SECONDS)
                .build()

        proxy =
            LoopbackStreamProxy(
                server = proxyServer,
                baseClient = baseClient,
                io = Dispatchers.IO,
                newToken = {
                    java.util.UUID
                        .randomUUID()
                        .toString()
                        .replace("-", "")
                },
                defaultUserAgent = "NexusStream/Android 14",
                limits = ProxyLimits(maxSessions = 8),
            )
    }

    @After
    fun tearDown() {
        proxyServer.stop()
        upstreamServer.stop()
    }

    @Test
    fun SP1_urlShape_differentTokens() {
        val s1 = proxy.open("http://127.0.0.1:$upstreamPort/path/movie.mp4", emptyMap())
        val s2 = proxy.open("http://127.0.0.1:$upstreamPort/path/video.mkv", emptyMap())

        val shapeRegex = Regex("^http://127\\.0\\.0\\.1:\\d+/p/[0-9a-f]{32}/[A-Za-z0-9._-]+$")
        assertTrue(shapeRegex.matches(s1.url))
        assertTrue(shapeRegex.matches(s2.url))
        assertTrue(s1.url.endsWith("/movie.mp4"))
        assertTrue(s2.url.endsWith("/video.mkv"))

        val token1 = s1.url.substringAfter("/p/").substringBefore("/")
        val token2 = s2.url.substringAfter("/p/").substringBefore("/")
        assertNotEquals(token1, token2)
    }

    @Test
    fun SP2_close_laterGetGives404_closingTwiceIsHarmless() {
        upstreamServer.mount(
            "/res/",
            object : HttpRoute {
                override suspend fun handle(
                    request: HttpRequest,
                    response: HttpResponseWriter,
                ) {
                    response.head(200, listOf("Content-Length" to "2", "Connection" to "close"))
                    response.body("ok".toByteArray(), 0, 2)
                }
            },
        )

        val session = proxy.open("http://127.0.0.1:$upstreamPort/res/item.mp4", emptyMap())
        val resp1 = testClient.newCall(Request.Builder().url(session.url).build()).execute()
        assertEquals(200, resp1.code)

        session.close()
        session.close() // Harmless

        val resp2 = testClient.newCall(Request.Builder().url(session.url).build()).execute()
        assertEquals(404, resp2.code)
    }

    @Test
    fun SP3_ninthOpenClosesOldest_newestWorks() {
        upstreamServer.mount(
            "/stream/",
            object : HttpRoute {
                override suspend fun handle(
                    request: HttpRequest,
                    response: HttpResponseWriter,
                ) {
                    response.head(200, listOf("Content-Length" to "4", "Connection" to "close"))
                    response.body("test".toByteArray(), 0, 4)
                }
            },
        )

        val sessions =
            (1..9).map { i ->
                proxy.open("http://127.0.0.1:$upstreamPort/stream/video$i.mp4", emptyMap())
            }

        // Oldest session (1) must be closed -> 404
        val resp1 = testClient.newCall(Request.Builder().url(sessions[0].url).build()).execute()
        assertEquals(404, resp1.code)

        // Newest session (9) must work -> 200
        val resp9 = testClient.newCall(Request.Builder().url(sessions[8].url).build()).execute()
        assertEquals(200, resp9.code)
    }

    @Test
    fun SP4_invalidUrlsThrowIllegalArgumentException() {
        val invalidUrls =
            listOf(
                "ftp://x/y",
                "not a url",
                "http://127.0.0.1:$proxyPort/p/loop",
                "http://localhost:$proxyPort/p/loop",
            )

        for (url in invalidUrls) {
            var caught = false
            try {
                proxy.open(url, emptyMap())
            } catch (_: IllegalArgumentException) {
                caught = true
            }
            assertTrue("Expected IllegalArgumentException for URL: $url", caught)
        }
    }

    @Test
    fun SP5_deniedHeadersNotForwarded() {
        val upstreamHeaders = ConcurrentHashMap<String, String>()
        upstreamServer.mount(
            "/denied/",
            object : HttpRoute {
                override suspend fun handle(
                    request: HttpRequest,
                    response: HttpResponseWriter,
                ) {
                    upstreamHeaders.putAll(request.headers)
                    response.head(200, listOf("Content-Length" to "0", "Connection" to "close"))
                }
            },
        )

        val deniedInput =
            mapOf(
                "Host" to "evil.com",
                "Range" to "bytes=0-10",
                "Accept-Encoding" to "gzip",
                "X-Custom" to "allowed-val",
            )

        val session = proxy.open("http://127.0.0.1:$upstreamPort/denied/file.mp4", deniedInput)
        val resp = testClient.newCall(Request.Builder().url(session.url).build()).execute()
        assertEquals(200, resp.code)

        assertEquals("allowed-val", upstreamHeaders["x-custom"])
        assertEquals("identity", upstreamHeaders["accept-encoding"])
        assertNull(upstreamHeaders["range"])
        assertEquals("127.0.0.1:$upstreamPort", upstreamHeaders["host"])
    }

    @Test
    fun SP6_cookiesSetInOneSessionAreNotSentInAnother() {
        val session2ReceivedCookies = ConcurrentHashMap<String, String>()

        upstreamServer.mount(
            "/set_cookie/",
            object : HttpRoute {
                override suspend fun handle(
                    request: HttpRequest,
                    response: HttpResponseWriter,
                ) {
                    response.head(
                        200,
                        listOf(
                            "Set-Cookie" to "session_id=123; Path=/",
                            "Content-Length" to "0",
                            "Connection" to "close",
                        ),
                    )
                }
            },
        )

        upstreamServer.mount(
            "/check_cookie/",
            object : HttpRoute {
                override suspend fun handle(
                    request: HttpRequest,
                    response: HttpResponseWriter,
                ) {
                    request.headers["cookie"]?.let { session2ReceivedCookies["cookie"] = it }
                    response.head(200, listOf("Content-Length" to "0", "Connection" to "close"))
                }
            },
        )

        val session1 = proxy.open("http://127.0.0.1:$upstreamPort/set_cookie/s1.mp4", emptyMap())
        val resp1 = testClient.newCall(Request.Builder().url(session1.url).build()).execute()
        assertEquals(200, resp1.code)

        val session2 = proxy.open("http://127.0.0.1:$upstreamPort/check_cookie/s2.mp4", emptyMap())
        val resp2 = testClient.newCall(Request.Builder().url(session2.url).build()).execute()
        assertEquals(200, resp2.code)

        assertNull(session2ReceivedCookies["cookie"])
    }

    @Test
    fun SP7_proxySessionToStringPrivacy() {
        val session = proxy.open("http://127.0.0.1:$upstreamPort/secret_movie.mp4", emptyMap())
        val str = session.toString()
        assertEquals("ProxySession", str)
        assertFalse(str.contains("secret_movie"))
        assertFalse(str.contains("127.0.0.1"))
        assertFalse(str.contains("/p/"))
    }

    @Test
    fun SP8_perSessionClientHasNoInterceptorsNoCacheAndZeroCallTimeout() {
        val client = proxy.createPerSessionClient(SessionCookieJar(), proxyPort)

        assertTrue(client.interceptors.isEmpty())
        assertNull(client.cache)
        assertEquals(0, client.callTimeoutMillis)
        assertEquals(1, client.networkInterceptors.size)
    }

    @Test
    fun SP9_diagnostics_startsAsDefaultsAndIsPerSession() {
        val session1 = proxy.open("http://127.0.0.1:$upstreamPort/s1.mp4", emptyMap())
        val session2 = proxy.open("http://127.0.0.1:$upstreamPort/s2.mp4", emptyMap())

        assertNull(session1.diagnostics.value.lastUpstreamStatus)
        assertEquals(0, session1.diagnostics.value.unsupportedPathRequests)

        assertNull(session2.diagnostics.value.lastUpstreamStatus)
        assertEquals(0, session2.diagnostics.value.unsupportedPathRequests)

        assertNotEquals(session1.diagnostics, session2.diagnostics)
    }
}
