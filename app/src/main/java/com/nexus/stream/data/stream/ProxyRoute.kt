package com.nexus.stream.data.stream

import com.nexus.stream.domain.usecase.buildUpstreamHeaders
import com.nexus.stream.domain.usecase.filterResponseHeaders
import com.nexus.stream.domain.usecase.mapUpstreamStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException

private const val STATUS_METHOD_NOT_ALLOWED = 405
private const val STATUS_NOT_FOUND = 404
private const val STATUS_BAD_REQUEST = 400
private const val STATUS_BAD_GATEWAY = 502
private const val STATUS_GATEWAY_TIMEOUT = 504
private const val STATUS_NO_CONTENT = 204
private const val STATUS_NOT_MODIFIED = 304
private const val BUFFER_SIZE = 64 * 1024

class ProxyRoute internal constructor(
    private val upstream: HttpUrl,
    private val streamHeaders: Map<String, String>,
    private val client: OkHttpClient,
    private val expectedPath: String,
    private val defaultUserAgent: String,
    private val io: CoroutineDispatcher,
) : HttpRoute {
    override suspend fun handle(
        request: HttpRequest,
        response: HttpResponseWriter,
    ) {
        if (!validateRequest(request, response)) return

        val call = prepareCall(request)
        val upstreamResponse = executeCall(call, response) ?: return
        try {
            relayResponse(request, response, upstreamResponse, call)
        } finally {
            upstreamResponse.close()
        }
    }

    private suspend fun validateRequest(
        request: HttpRequest,
        response: HttpResponseWriter,
    ): Boolean {
        val errorStatus =
            when {
                request.method != "GET" && request.method != "HEAD" -> STATUS_METHOD_NOT_ALLOWED
                request.path != expectedPath -> STATUS_NOT_FOUND
                else -> null
            } ?: return true

        val headers =
            if (errorStatus == STATUS_METHOD_NOT_ALLOWED) {
                listOf(
                    "Allow" to "GET, HEAD",
                    "Content-Length" to "0",
                    "Connection" to "close",
                )
            } else {
                listOf(
                    "Content-Length" to "0",
                    "Connection" to "close",
                )
            }
        response.head(errorStatus, headers)
        return false
    }

    private suspend fun prepareCall(request: HttpRequest): okhttp3.Call {
        val upstreamHeaders =
            buildUpstreamHeaders(
                streamHeaders = streamHeaders,
                playerRange = request.headers["range"],
                playerUserAgent = request.headers["user-agent"],
                defaultUserAgent = defaultUserAgent,
            )

        val requestBuilder = Request.Builder().url(upstream)
        for ((name, value) in upstreamHeaders) {
            requestBuilder.addHeader(name, value)
        }
        if (request.method == "HEAD") {
            requestBuilder.head()
        } else {
            requestBuilder.get()
        }

        val call = client.newCall(requestBuilder.build())
        currentCoroutineContext().job.invokeOnCompletion {
            call.cancel()
        }
        return call
    }

    private suspend fun executeCall(
        call: okhttp3.Call,
        response: HttpResponseWriter,
    ): Response? =
        try {
            withContext(io) {
                call.execute()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: SocketTimeoutException) {
            sendErrorHead(response, STATUS_GATEWAY_TIMEOUT)
            null
        } catch (_: InterruptedIOException) {
            sendErrorHead(response, STATUS_GATEWAY_TIMEOUT)
            null
        } catch (_: IOException) {
            sendErrorHead(response, STATUS_BAD_GATEWAY)
            null
        }

    private suspend fun sendErrorHead(
        response: HttpResponseWriter,
        status: Int,
    ) {
        response.head(
            status,
            listOf(
                "Content-Length" to "0",
                "Connection" to "close",
            ),
        )
    }

    private suspend fun relayResponse(
        request: HttpRequest,
        response: HttpResponseWriter,
        upstreamResponse: Response,
        call: okhttp3.Call,
    ) {
        val mappedStatus = mapUpstreamStatus(upstreamResponse.code)
        val upstreamHeadersList =
            (0 until upstreamResponse.headers.size).map { i ->
                upstreamResponse.headers.name(i) to upstreamResponse.headers.value(i)
            }
        val filteredHeaders = filterResponseHeaders(mappedStatus, upstreamHeadersList)

        response.head(mappedStatus, filteredHeaders)

        val shouldSendBody =
            mappedStatus != STATUS_BAD_GATEWAY &&
                mappedStatus < STATUS_BAD_REQUEST &&
                request.method != "HEAD" &&
                mappedStatus != STATUS_NO_CONTENT &&
                mappedStatus != STATUS_NOT_MODIFIED

        if (shouldSendBody) {
            relayBody(upstreamResponse, response, call)
        }
    }

    private suspend fun relayBody(
        upstreamResponse: Response,
        response: HttpResponseWriter,
        call: okhttp3.Call,
    ) {
        val body = upstreamResponse.body
        val source = body.source()
        val buffer = ByteArray(BUFFER_SIZE)
        while (currentCoroutineContext().isActive) {
            val read =
                withContext(io) {
                    source.read(buffer)
                }
            if (read == -1) break
            try {
                response.body(buffer, 0, read)
            } catch (e: ClientGoneException) {
                call.cancel()
                throw e
            }
        }
    }
}
