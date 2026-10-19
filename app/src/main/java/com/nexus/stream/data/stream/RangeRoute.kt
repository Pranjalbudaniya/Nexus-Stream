package com.nexus.stream.data.stream

import com.nexus.stream.domain.usecase.RangeSpec
import com.nexus.stream.domain.usecase.parseRange
import kotlinx.coroutines.CancellationException

const val STREAM_CHUNK_BYTES: Int = 128 * 1024

private const val STATUS_OK = 200
private const val STATUS_PARTIAL_CONTENT = 206
private const val STATUS_METHOD_NOT_ALLOWED = 405
private const val STATUS_RANGE_NOT_SATISFIABLE = 416
private const val STATUS_BAD_GATEWAY = 502
private const val STATUS_GATEWAY_TIMEOUT = 504

interface RangeByteSource {
    val sizeBytes: Long
    val contentType: String

    suspend fun read(
        position: Long,
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int
}

class SourceStallException : Exception()

class SourceIoException : Exception()

class RangeRoute(
    private val source: RangeByteSource,
    private val chunkBytes: Int = STREAM_CHUNK_BYTES,
) : HttpRoute {
    override suspend fun handle(
        request: HttpRequest,
        response: HttpResponseWriter,
    ) {
        if (request.method != "GET" && request.method != "HEAD") {
            response.head(
                STATUS_METHOD_NOT_ALLOWED,
                listOf(
                    "Allow" to "GET, HEAD",
                    "Content-Length" to "0",
                ),
            )
            return
        }

        when (val range = parseRange(request.headers["range"], source.sizeBytes)) {
            is RangeSpec.Unsatisfiable -> handleUnsatisfiable(response)
            is RangeSpec.Full -> handleFull(request.method, response)
            is RangeSpec.Partial -> handlePartial(request.method, range.start, range.endInclusive, response)
        }
    }

    private suspend fun handleUnsatisfiable(response: HttpResponseWriter) {
        response.head(
            STATUS_RANGE_NOT_SATISFIABLE,
            listOf(
                "Content-Range" to "bytes */${source.sizeBytes}",
                "Content-Length" to "0",
                "Accept-Ranges" to "bytes",
                "Content-Type" to source.contentType,
                "Cache-Control" to "no-store",
                "Connection" to "close",
            ),
        )
    }

    private suspend fun handleFull(
        method: String,
        response: HttpResponseWriter,
    ) {
        val headers =
            listOf(
                "Content-Length" to "${source.sizeBytes}",
                "Accept-Ranges" to "bytes",
                "Content-Type" to source.contentType,
                "Cache-Control" to "no-store",
                "Connection" to "close",
            )
        if (method == "HEAD" || source.sizeBytes == 0L) {
            response.head(STATUS_OK, headers)
            return
        }
        streamBody(STATUS_OK, headers, 0L, source.sizeBytes - 1, response)
    }

    private suspend fun handlePartial(
        method: String,
        start: Long,
        end: Long,
        response: HttpResponseWriter,
    ) {
        val contentLength = end - start + 1
        val headers =
            listOf(
                "Content-Range" to "bytes $start-$end/${source.sizeBytes}",
                "Content-Length" to "$contentLength",
                "Accept-Ranges" to "bytes",
                "Content-Type" to source.contentType,
                "Cache-Control" to "no-store",
                "Connection" to "close",
            )
        if (method == "HEAD") {
            response.head(STATUS_PARTIAL_CONTENT, headers)
            return
        }
        streamBody(STATUS_PARTIAL_CONTENT, headers, start, end, response)
    }

    private suspend fun streamBody(
        status: Int,
        headers: List<Pair<String, String>>,
        start: Long,
        end: Long,
        response: HttpResponseWriter,
    ) {
        val buffer = ByteArray(chunkBytes)
        var currentPos = start
        var remaining = end - start + 1
        var isFirstChunk = true
        var canContinue = true

        while (remaining > 0 && canContinue) {
            val toRead = minOf(chunkBytes.toLong(), remaining).toInt()
            val (bytesRead, error) = readFromSource(currentPos, buffer, toRead)
            if (error != null) {
                handleReadFailure(error, isFirstChunk, response)
                canContinue = false
            } else if (bytesRead <= 0) {
                canContinue = false
            } else {
                if (isFirstChunk) {
                    response.head(status, headers)
                    isFirstChunk = false
                }
                val writeOk = writeToResponse(response, buffer, bytesRead)
                if (writeOk) {
                    currentPos += bytesRead
                    remaining -= bytesRead
                } else {
                    canContinue = false
                }
            }
        }
    }

    private suspend fun readFromSource(
        position: Long,
        buffer: ByteArray,
        length: Int,
    ): Pair<Int, Throwable?> {
        val result = runCatching { source.read(position, buffer, 0, length) }
        val exception = result.exceptionOrNull()
        if (exception is CancellationException) throw exception
        val count = result.getOrDefault(-1)
        return count to exception
    }

    private suspend fun writeToResponse(
        response: HttpResponseWriter,
        buffer: ByteArray,
        length: Int,
    ): Boolean {
        val result = runCatching { response.body(buffer, 0, length) }
        val exception = result.exceptionOrNull()
        if (exception is CancellationException) throw exception
        return result.isSuccess
    }

    private suspend fun handleReadFailure(
        e: Throwable,
        isFirstChunk: Boolean,
        response: HttpResponseWriter,
    ) {
        if (!isFirstChunk) return
        val status =
            when (e) {
                is SourceStallException -> STATUS_GATEWAY_TIMEOUT
                is SourceIoException -> STATUS_BAD_GATEWAY
                else -> null
            }
        if (status != null) {
            response.head(status, listOf("Content-Length" to "0", "Connection" to "close"))
        }
    }
}
