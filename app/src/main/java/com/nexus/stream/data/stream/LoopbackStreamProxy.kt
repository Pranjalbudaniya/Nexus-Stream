package com.nexus.stream.data.stream

import android.os.Build
import com.nexus.stream.di.IoDispatcher
import com.nexus.stream.domain.repository.ProxySession
import com.nexus.stream.domain.repository.StreamProxy
import com.nexus.stream.domain.usecase.parseCookieHeader
import com.nexus.stream.domain.usecase.proxyFileName
import com.nexus.stream.domain.usecase.sanitizeRequestHeaders
import kotlinx.coroutines.CoroutineDispatcher
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.io.IOException
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val TOKEN_BYTES_COUNT = 16
private const val MAX_COOKIES = 50

data class ProxyLimits(
    val maxSessions: Int = 8,
    val connectTimeoutMs: Long = 10_000,
    val readTimeoutMs: Long = 30_000,
)

internal data class CookieKey(
    val name: String,
    val domain: String,
    val path: String,
)

internal class SessionCookieJar(
    private val clock: () -> Long = System::currentTimeMillis,
) : CookieJar {
    private val lock = Any()
    private val cookies = LinkedHashMap<CookieKey, Cookie>()

    fun seed(
        host: String,
        cookieHeader: String,
    ) {
        val pairs = parseCookieHeader(cookieHeader)
        synchronized(lock) {
            for ((name, value) in pairs) {
                val cookie =
                    runCatching {
                        Cookie
                            .Builder()
                            .name(name)
                            .value(value)
                            .hostOnlyDomain(host)
                            .path("/")
                            .build()
                    }.getOrNull()
                if (cookie != null) {
                    addOrReplaceLocked(cookie)
                }
            }
        }
    }

    override fun saveFromResponse(
        url: HttpUrl,
        cookies: List<Cookie>,
    ) {
        synchronized(lock) {
            for (cookie in cookies) {
                addOrReplaceLocked(cookie)
            }
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = clock()
        synchronized(lock) {
            return cookies.values.filter { cookie ->
                cookie.matches(url) && cookie.expiresAt > now
            }
        }
    }

    internal fun addCookieForTesting(cookie: Cookie) {
        synchronized(lock) {
            addOrReplaceLocked(cookie)
        }
    }

    private fun addOrReplaceLocked(cookie: Cookie) {
        val key = CookieKey(cookie.name, cookie.domain, cookie.path)
        cookies.remove(key)
        cookies[key] = cookie
        while (cookies.size > MAX_COOKIES) {
            val oldestKey = cookies.keys.firstOrNull() ?: break
            cookies.remove(oldestKey)
        }
    }
}

internal fun createLoopbackGuardInterceptor(serverPort: Int): Interceptor =
    Interceptor { chain ->
        val url = chain.request().url
        val host = url.host
        val port = url.port
        val isLoopbackHost =
            host.equals("127.0.0.1", ignoreCase = true) ||
                host.equals("localhost", ignoreCase = true) ||
                host.equals("::1", ignoreCase = true) ||
                host.equals("[::1]", ignoreCase = true)

        if (isLoopbackHost && port == serverPort) {
            throw IOException("Self-referencing loopback hop forbidden")
        }
        chain.proceed(chain.request())
    }

private fun generateSecureRandomToken(): String {
    val random = SecureRandom()
    val bytes = ByteArray(TOKEN_BYTES_COUNT)
    random.nextBytes(bytes)
    val sb = StringBuilder(TOKEN_BYTES_COUNT * 2)
    for (b in bytes) {
        sb.append(String.format(java.util.Locale.US, "%02x", b))
    }
    return sb.toString()
}

private fun validateProxyUrl(
    url: String,
    loopbackPort: Int,
): HttpUrl {
    val httpUrl = url.toHttpUrlOrNull() ?: throw IllegalArgumentException("Invalid URL: cannot parse HTTP URL")
    val scheme = httpUrl.scheme
    require(scheme == "http" || scheme == "https") {
        "Invalid scheme: must be http or https"
    }
    require(httpUrl.host.isNotEmpty()) {
        "Host missing"
    }
    val host = httpUrl.host
    val isLoopback =
        host.equals("127.0.0.1", ignoreCase = true) ||
            host.equals("localhost", ignoreCase = true) ||
            host.equals("::1", ignoreCase = true) ||
            host.equals("[::1]", ignoreCase = true)

    require(!isLoopback || httpUrl.port != loopbackPort) {
        "URL points to server's own loopback port"
    }
    return httpUrl
}

@Singleton
class LoopbackStreamProxy internal constructor(
    private val server: LoopbackServer,
    private val baseClient: OkHttpClient,
    private val io: CoroutineDispatcher,
    private val newToken: () -> String,
    private val defaultUserAgent: String,
    private val limits: ProxyLimits,
) : StreamProxy {
    @Inject
    constructor(
        server: LoopbackServer,
        baseClient: OkHttpClient,
        @IoDispatcher io: CoroutineDispatcher,
    ) : this(
        server = server,
        baseClient = baseClient,
        io = io,
        newToken = ::generateSecureRandomToken,
        defaultUserAgent = "NexusStream/Android ${Build.VERSION.RELEASE}",
        limits = ProxyLimits(),
    )

    private val lock = Any()
    private val sessions = LinkedHashMap<String, ProxySession>()

    override fun open(
        url: String,
        headers: Map<String, String>,
    ): ProxySession {
        val port = server.start()
        val httpUrl = validateProxyUrl(url, port)

        val sanitizedHeaders = sanitizeRequestHeaders(headers)
        val jar = SessionCookieJar()
        val cookieHeader =
            sanitizedHeaders.entries
                .find { it.key.equals("cookie", ignoreCase = true) }
                ?.value
        if (cookieHeader != null) {
            jar.seed(httpUrl.host, cookieHeader)
        }

        val perSessionClient = createPerSessionClient(jar, port)

        val token = newToken()
        val prefix = "/p/$token/"
        val fileName = proxyFileName(url)
        val expectedPath = "$prefix$fileName"

        val route =
            ProxyRoute(
                upstream = httpUrl,
                streamHeaders = sanitizedHeaders,
                client = perSessionClient,
                expectedPath = expectedPath,
                defaultUserAgent = defaultUserAgent,
                io = io,
            )

        val sessionUrl = "http://127.0.0.1:$port$expectedPath"

        synchronized(lock) {
            while (sessions.size >= limits.maxSessions) {
                val oldestEntry = sessions.entries.firstOrNull() ?: break
                oldestEntry.value.close()
            }

            server.mount(prefix, route)

            lateinit var session: ProxySession
            session =
                ProxySession(sessionUrl) {
                    synchronized(lock) {
                        sessions.remove(token)
                        server.unmount(prefix)
                    }
                }
            sessions[token] = session
            return session
        }
    }

    internal fun createPerSessionClient(
        jar: SessionCookieJar,
        port: Int,
    ): OkHttpClient =
        baseClient
            .newBuilder()
            .apply {
                interceptors().clear()
                networkInterceptors().clear()
                cache(null)
                cookieJar(jar)
                callTimeout(0, TimeUnit.MILLISECONDS)
                connectTimeout(limits.connectTimeoutMs, TimeUnit.MILLISECONDS)
                readTimeout(limits.readTimeoutMs, TimeUnit.MILLISECONDS)
                followRedirects(true)
                followSslRedirects(true)
                addNetworkInterceptor(createLoopbackGuardInterceptor(port))
            }.build()
}
