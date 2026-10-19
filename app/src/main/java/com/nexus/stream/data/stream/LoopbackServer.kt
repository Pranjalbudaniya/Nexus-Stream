package com.nexus.stream.data.stream

import com.nexus.stream.di.IoDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

const val DEFAULT_MAX_CONNECTIONS: Int = 8
const val DEFAULT_HEAD_TIMEOUT_MS: Int = 10_000

private const val SERVER_BACKLOG = 16
private const val STATUS_BAD_REQUEST = 400
private const val STATUS_NOT_FOUND = 404
private const val STATUS_INTERNAL_ERROR = 500
private const val STATUS_SERVICE_UNAVAILABLE = 503

interface HttpResponseWriter {
    val headWritten: Boolean

    suspend fun head(
        status: Int,
        headers: List<Pair<String, String>>,
    )

    suspend fun body(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    )
}

interface HttpRoute {
    suspend fun handle(
        request: HttpRequest,
        response: HttpResponseWriter,
    )
}

class ClientGoneException : Exception()

data class ServerConfig(
    val maxConnections: Int = DEFAULT_MAX_CONNECTIONS,
    val headTimeoutMs: Int = DEFAULT_HEAD_TIMEOUT_MS,
)

private sealed interface HeadReadResult {
    data class Success(
        val bytes: ByteArray,
    ) : HeadReadResult

    data object Oversize : HeadReadResult

    data object Failed : HeadReadResult
}

@Singleton
class LoopbackServer internal constructor(
    private val dispatcher: CoroutineDispatcher,
    private val config: ServerConfig = ServerConfig(),
) {
    @Inject
    constructor(
        @IoDispatcher dispatcher: CoroutineDispatcher,
    ) : this(dispatcher, ServerConfig())

    private val lock = Any()
    private var serverSocket: ServerSocket? = null
    private var scope: CoroutineScope? = null
    private var boundPort: Int = 0

    private val activeSockets = ConcurrentHashMap.newKeySet<Socket>()
    private val routes = ConcurrentHashMap<String, HttpRoute>()

    fun start(): Int =
        synchronized(lock) {
            serverSocket?.let { existing ->
                if (!existing.isClosed) return boundPort
            }

            val ss = ServerSocket(0, SERVER_BACKLOG, InetAddress.getByName("127.0.0.1"))
            serverSocket = ss
            boundPort = ss.localPort

            val newScope = CoroutineScope(SupervisorJob() + dispatcher)
            scope = newScope
            newScope.launch {
                acceptLoop(ss)
            }

            boundPort
        }

    fun stop() {
        synchronized(lock) {
            try {
                serverSocket?.close()
            } catch (_: IOException) {
            }
            serverSocket = null
            boundPort = 0

            scope?.cancel()
            scope = null

            val sockets = activeSockets.toList()
            activeSockets.clear()
            for (s in sockets) {
                try {
                    s.close()
                } catch (_: IOException) {
                }
            }
        }
    }

    fun mount(
        prefix: String,
        route: HttpRoute,
    ) {
        require(prefix.startsWith('/') && prefix.endsWith('/')) {
            "Prefix must start and end with /"
        }
        routes[prefix] = route
    }

    fun unmount(prefix: String) {
        routes.remove(prefix)
    }

    private suspend fun acceptLoop(ss: ServerSocket) {
        while (currentCoroutineContext().isActive && !ss.isClosed) {
            val socket =
                try {
                    withContext(dispatcher) {
                        ss.accept()
                    }
                } catch (_: IOException) {
                    break
                }

            if (activeSockets.size >= config.maxConnections) {
                scope?.launch(dispatcher) {
                    rejectOverloaded(socket)
                }
            } else {
                activeSockets.add(socket)
                scope?.launch(dispatcher) {
                    try {
                        handleConnection(socket)
                    } finally {
                        activeSockets.remove(socket)
                        try {
                            socket.close()
                        } catch (_: IOException) {
                        }
                    }
                }
            }
        }
    }

    private fun rejectOverloaded(socket: Socket) {
        try {
            val response =
                formatResponseHead(
                    STATUS_SERVICE_UNAVAILABLE,
                    listOf(
                        "Retry-After" to "1",
                        "Content-Length" to "0",
                        "Connection" to "close",
                    ),
                )
            socket.getOutputStream().write(response.toByteArray(Charsets.US_ASCII))
            socket.getOutputStream().flush()
        } catch (_: IOException) {
        } finally {
            try {
                socket.close()
            } catch (_: IOException) {
            }
        }
    }

    private suspend fun handleConnection(socket: Socket) {
        socket.soTimeout = config.headTimeoutMs

        val headBytes =
            when (val res = readHeadBytes(socket)) {
                is HeadReadResult.Success -> {
                    res.bytes
                }

                is HeadReadResult.Oversize -> {
                    sendErrorResponse(socket, STATUS_BAD_REQUEST)
                    return
                }

                is HeadReadResult.Failed -> {
                    return
                }
            }

        val headString = String(headBytes, Charsets.US_ASCII)
        val request = parseRequestHead(headString)
        val route = request?.let { findRoute(it.path) }

        when {
            request == null -> sendErrorResponse(socket, STATUS_BAD_REQUEST)
            route == null -> sendErrorResponse(socket, STATUS_NOT_FOUND)
            else -> executeRoute(route, request, socket)
        }
    }

    private suspend fun executeRoute(
        route: HttpRoute,
        request: HttpRequest,
        socket: Socket,
    ) {
        val writer = SocketResponseWriter(socket.getOutputStream())
        try {
            route.handle(request, writer)
        } catch (e: CancellationException) {
            throw e
        } catch (_: ClientGoneException) {
        } catch (_: Exception) {
            if (!writer.headWritten) {
                sendErrorResponse(socket, STATUS_INTERNAL_ERROR)
            }
        }
    }

    private fun findRoute(path: String): HttpRoute? =
        routes.entries
            .filter { (prefix, _) -> path.startsWith(prefix) }
            .maxByOrNull { it.key.length }
            ?.value
}

private fun sendErrorResponse(
    socket: Socket,
    status: Int,
) {
    try {
        val head =
            formatResponseHead(
                status,
                listOf(
                    "Content-Length" to "0",
                    "Connection" to "close",
                ),
            )
        socket.getOutputStream().write(head.toByteArray(Charsets.US_ASCII))
        socket.getOutputStream().flush()
    } catch (_: IOException) {
    }
}

private fun readHeadBytes(socket: Socket): HeadReadResult {
    val inputStream = BufferedInputStream(socket.getInputStream())
    val buffer = ByteArrayOutputStream()
    var b0 = -1
    var b1 = -1
    var b2 = -1
    var found = false
    var failed = false

    while (buffer.size() < MAX_HEAD_BYTES && !found && !failed) {
        val byte = runCatching { inputStream.read() }.getOrDefault(-1)
        if (byte == -1) {
            failed = true
        } else {
            buffer.write(byte)
            if (isEndOfHead(byte, b0, b1, b2)) {
                found = true
            }
            b2 = b1
            b1 = b0
            b0 = byte
        }
    }

    return when {
        found -> HeadReadResult.Success(buffer.toByteArray())
        failed -> HeadReadResult.Failed
        else -> HeadReadResult.Oversize
    }
}

private fun isEndOfHead(
    current: Int,
    b0: Int,
    b1: Int,
    b2: Int,
): Boolean {
    val isCrLfCrLf = current == '\n'.code && b0 == '\r'.code && b1 == '\n'.code && b2 == '\r'.code
    val isLfLf = current == '\n'.code && b0 == '\n'.code
    return isCrLfCrLf || isLfLf
}

private class SocketResponseWriter(
    private val output: java.io.OutputStream,
) : HttpResponseWriter {
    @Volatile
    override var headWritten: Boolean = false
        private set

    override suspend fun head(
        status: Int,
        headers: List<Pair<String, String>>,
    ) {
        check(!headWritten) { "Head already written" }
        val headText = formatResponseHead(status, headers)
        try {
            output.write(headText.toByteArray(Charsets.US_ASCII))
            output.flush()
            headWritten = true
        } catch (_: IOException) {
            throw ClientGoneException()
        }
    }

    override suspend fun body(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ) {
        check(headWritten) { "Head not written yet" }
        try {
            output.write(buffer, offset, length)
            output.flush()
        } catch (_: IOException) {
            throw ClientGoneException()
        }
    }
}
