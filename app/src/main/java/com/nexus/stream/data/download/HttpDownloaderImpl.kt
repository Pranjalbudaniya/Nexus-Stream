package com.nexus.stream.data.download

import com.nexus.stream.di.IoDispatcher
import com.nexus.stream.domain.model.DownloadFailure
import com.nexus.stream.domain.repository.HttpDownloader
import com.nexus.stream.domain.repository.HttpResult
import com.nexus.stream.domain.repository.HttpTask
import com.nexus.stream.domain.repository.ResumeInfo
import com.nexus.stream.domain.repository.StreamProxy
import com.nexus.stream.domain.usecase.MAX_RETRIES
import com.nexus.stream.domain.usecase.RETRY_WAIT_MS
import com.nexus.stream.domain.usecase.ResumeDecision
import com.nexus.stream.domain.usecase.StatusClass
import com.nexus.stream.domain.usecase.classifyStatus
import com.nexus.stream.domain.usecase.decideResume
import com.nexus.stream.domain.usecase.looksLikeNotAVideo
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

const val DOWNLOAD_CHUNK_BYTES = 64 * 1024
const val PROGRESS_INTERVAL_MS = 250L

private const val CONNECT_TIMEOUT_SECONDS = 15L
private const val READ_TIMEOUT_SECONDS = 30L
private const val HTTP_RANGE_NOT_SATISFIABLE = 416
private const val HTTP_PARTIAL_CONTENT = 206
private const val PERCENT_DIVISOR = 100L

private data class DownloadStateHolder(
    var offset: Long,
    var etag: String?,
    var lastModified: String?,
    var supportsResume: Boolean,
    var knownTotal: Long?,
    var crStart: Long? = null,
)

@Singleton
class HttpDownloaderImpl(
    private val client: OkHttpClient,
    private val streamProxy: StreamProxy,
    private val ioDispatcher: CoroutineDispatcher,
    private val delayFn: suspend (Long) -> Unit,
) : HttpDownloader {
    @Inject
    constructor(
        baseClient: OkHttpClient,
        streamProxy: StreamProxy,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ) : this(
        client =
            baseClient
                .newBuilder()
                .apply {
                    interceptors().clear()
                    networkInterceptors().clear()
                    cache(null)
                    callTimeout(0, TimeUnit.MILLISECONDS)
                    connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    followRedirects(true)
                    followSslRedirects(true)
                }.build(),
        streamProxy = streamProxy,
        ioDispatcher = ioDispatcher,
        delayFn = { delay(it) },
    )

    override suspend fun download(
        task: HttpTask,
        onProgress: (bytes: Long, total: Long) -> Unit,
    ): HttpResult =
        withContext(ioDispatcher) {
            val session = if (task.headers.isNotEmpty()) streamProxy.open(task.url, task.headers) else null
            val targetUrl = session?.url ?: task.url

            try {
                performDownloadLoop(targetUrl, task, onProgress)
            } finally {
                session?.close()
            }
        }

    private suspend fun performDownloadLoop(
        targetUrl: String,
        task: HttpTask,
        onProgress: (bytes: Long, total: Long) -> Unit,
    ): HttpResult {
        val state =
            DownloadStateHolder(
                offset = task.offset,
                etag = task.etag,
                lastModified = task.lastModified,
                supportsResume = task.supportsResumeKnown,
                knownTotal = null,
            )

        var finalResult: HttpResult? = null
        var attempt = 0
        while (attempt <= MAX_RETRIES && finalResult == null) {
            coroutineContext.ensureActive()
            val request = buildRequest(targetUrl, state)

            val response =
                try {
                    client.newCall(request).execute()
                } catch (_: IOException) {
                    if (attempt < MAX_RETRIES) {
                        delayFn(RETRY_WAIT_MS[attempt])
                    } else {
                        finalResult = HttpResult.Failed(DownloadFailure.NETWORK, makeResumeInfo(task.partFile, state))
                    }
                    null
                }

            if (response != null) {
                finalResult = processResponse(response, task, state, attempt, onProgress)
            }
            attempt++
        }

        return finalResult ?: HttpResult.Failed(DownloadFailure.SERVER_ERROR, makeResumeInfo(task.partFile, state))
    }

    private fun buildRequest(
        targetUrl: String,
        state: DownloadStateHolder,
    ): Request {
        val builder = Request.Builder().url(targetUrl).header("Accept-Encoding", "identity")
        if (state.offset > 0L) {
            builder.header("Range", "bytes=${state.offset}-")
            val ifRange = state.etag ?: state.lastModified
            if (!ifRange.isNullOrBlank()) {
                builder.header("If-Range", ifRange)
            }
        }
        return builder.build()
    }

    private suspend fun processResponse(
        response: Response,
        task: HttpTask,
        state: DownloadStateHolder,
        attempt: Int,
        onProgress: (bytes: Long, total: Long) -> Unit,
    ): HttpResult? {
        val status = response.code
        val statusClass = classifyStatus(status)

        return when {
            statusClass == StatusClass.LinkExpired -> {
                response.close()
                HttpResult.Failed(DownloadFailure.LINK_EXPIRED, makeResumeInfo(task.partFile, state))
            }

            status == HTTP_RANGE_NOT_SATISFIABLE -> {
                response.close()
                handleRangeNotSatisfiable(task, state)
            }

            statusClass == StatusClass.ServerError -> {
                response.close()
                HttpResult.Failed(DownloadFailure.SERVER_ERROR, makeResumeInfo(task.partFile, state))
            }

            statusClass == StatusClass.Retryable -> {
                handleRetryable(response, task, state, attempt)
            }

            else -> {
                handleSuccessResponse(response, task, state, attempt, onProgress)
            }
        }
    }

    private suspend fun handleRetryable(
        response: Response,
        task: HttpTask,
        state: DownloadStateHolder,
        attempt: Int,
    ): HttpResult? {
        response.close()
        return if (attempt < MAX_RETRIES) {
            delayFn(RETRY_WAIT_MS[attempt])
            null
        } else {
            HttpResult.Failed(DownloadFailure.SERVER_ERROR, makeResumeInfo(task.partFile, state))
        }
    }

    private fun handleRangeNotSatisfiable(
        task: HttpTask,
        state: DownloadStateHolder,
    ): HttpResult {
        val decision = decideResume(HTTP_RANGE_NOT_SATISFIABLE, state.offset, null, state.knownTotal ?: state.offset)
        return if (decision == ResumeDecision.AlreadyComplete) {
            HttpResult.Completed(
                total = state.offset,
                etag = state.etag,
                lastModified = state.lastModified,
                supportsResume = state.supportsResume,
            )
        } else {
            HttpResult.Failed(DownloadFailure.SERVER_ERROR, makeResumeInfo(task.partFile, state))
        }
    }

    private suspend fun handleSuccessResponse(
        response: Response,
        task: HttpTask,
        state: DownloadStateHolder,
        attempt: Int,
        onProgress: (bytes: Long, total: Long) -> Unit,
    ): HttpResult? {
        extractHeaders(response, state)
        val decision = decideResume(response.code, state.offset, state.crStart, state.knownTotal)

        return when (decision) {
            ResumeDecision.AlreadyComplete -> {
                response.close()
                HttpResult.Completed(state.offset, state.etag, state.lastModified, state.supportsResume)
            }

            ResumeDecision.Restart, ResumeDecision.Fresh -> {
                applyRestart(task, state, decision)
                proceedWithStream(response, task, state, attempt, onProgress)
            }

            ResumeDecision.Append -> {
                proceedWithStream(response, task, state, attempt, onProgress)
            }
        }
    }

    private suspend fun proceedWithStream(
        response: Response,
        task: HttpTask,
        state: DownloadStateHolder,
        attempt: Int,
        onProgress: (bytes: Long, total: Long) -> Unit,
    ): HttpResult? {
        val contentType = response.header("Content-Type")
        return if (looksLikeNotAVideo(contentType, state.knownTotal ?: 0L)) {
            response.close()
            HttpResult.Failed(DownloadFailure.NOT_A_VIDEO, makeResumeInfo(task.partFile, state))
        } else {
            streamResponseBody(response, task, state, attempt, onProgress)
        }
    }

    private suspend fun streamResponseBody(
        response: Response,
        task: HttpTask,
        state: DownloadStateHolder,
        attempt: Int,
        onProgress: (bytes: Long, total: Long) -> Unit,
    ): HttpResult? {
        val failure =
            try {
                response.body.use { body ->
                    writeResponseBody(body, task, state, onProgress)
                }
                null
            } catch (e: IOException) {
                handleStreamIoException(e, task, state)
            }

        return failure ?: checkCompletion(task, state, attempt, onProgress)
    }

    private suspend fun writeResponseBody(
        body: ResponseBody,
        task: HttpTask,
        state: DownloadStateHolder,
        onProgress: (bytes: Long, total: Long) -> Unit,
    ) {
        val source = body.source()
        FileOutputStream(task.partFile, state.offset > 0L).use { out ->
            val buffer = ByteArray(DOWNLOAD_CHUNK_BYTES)
            var lastTime = System.currentTimeMillis()
            var lastBytes = state.offset

            while (true) {
                coroutineContext.ensureActive()
                val read = source.read(buffer)
                if (read == -1) break
                out.write(buffer, 0, read)
                state.offset += read

                val now = System.currentTimeMillis()
                val total = state.knownTotal ?: 0L
                val timeDue = now - lastTime >= PROGRESS_INTERVAL_MS
                val pctDue = total <= 0L || ((state.offset - lastBytes) * PERCENT_DIVISOR / total) >= 1L
                if (timeDue && pctDue) {
                    onProgress(state.offset, total)
                    lastTime = now
                    lastBytes = state.offset
                }
            }
            out.flush()
        }
    }

    private suspend fun checkCompletion(
        task: HttpTask,
        state: DownloadStateHolder,
        attempt: Int,
        onProgress: (bytes: Long, total: Long) -> Unit,
    ): HttpResult? {
        val total = state.knownTotal
        if (total != null && total > 0L && task.partFile.length() < total) {
            return if (attempt < MAX_RETRIES) {
                state.offset = task.partFile.length()
                delayFn(RETRY_WAIT_MS[attempt])
                null
            } else {
                HttpResult.Failed(DownloadFailure.INCOMPLETE, makeResumeInfo(task.partFile, state))
            }
        }

        val finalBytes = task.partFile.length()
        val finalTotal = if (total != null && total > 0L) total else finalBytes
        onProgress(finalBytes, finalTotal)
        return HttpResult.Completed(finalTotal, state.etag, state.lastModified, state.supportsResume)
    }
}

private fun extractHeaders(
    response: Response,
    state: DownloadStateHolder,
) {
    response.header("ETag")?.let { state.etag = it }
    response.header("Last-Modified")?.let { state.lastModified = it }

    val (crStart, crTotal) = parseContentRange(response.header("Content-Range"))
    state.crStart = crStart
    val headerLength = response.header("Content-Length")?.toLongOrNull()?.takeIf { it >= 0L }
    val computedTotal =
        crTotal ?: if (response.code == HTTP_PARTIAL_CONTENT && crStart != null && headerLength != null) {
            crStart + headerLength
        } else {
            headerLength
        }
    if (computedTotal != null) state.knownTotal = computedTotal
    if (computedTotal == null) state.supportsResume = false
}

private fun applyRestart(
    task: HttpTask,
    state: DownloadStateHolder,
    decision: ResumeDecision,
) {
    state.offset = 0L
    if (decision == ResumeDecision.Restart) state.supportsResume = false
    task.partFile.delete()
}

private fun handleStreamIoException(
    e: IOException,
    task: HttpTask,
    state: DownloadStateHolder,
): HttpResult {
    val msg = e.message.orEmpty()
    return if (msg.contains("ENOSPC", ignoreCase = true) ||
        msg.contains("No space left on device", ignoreCase = true)
    ) {
        HttpResult.Failed(DownloadFailure.INSUFFICIENT_STORAGE, makeResumeInfo(task.partFile, state))
    } else {
        HttpResult.Failed(DownloadFailure.NETWORK, makeResumeInfo(task.partFile, state))
    }
}

private fun makeResumeInfo(
    file: File,
    state: DownloadStateHolder,
) = ResumeInfo(
    bytes = if (file.exists()) file.length() else 0L,
    etag = state.etag,
    lastModified = state.lastModified,
    supportsResume = state.supportsResume,
)

private fun parseContentRange(header: String?): Pair<Long?, Long?> {
    if (header.isNullOrBlank()) return Pair(null, null)
    val clean = header.substringAfter("bytes ").trim()
    val rangePart = clean.substringBefore('/')
    val totalPart = clean.substringAfter('/', "")

    val start = rangePart.substringBefore('-').toLongOrNull()
    val total = totalPart.toLongOrNull()
    return Pair(start, total)
}
