package com.nexus.stream.domain.usecase

import com.nexus.stream.data.torrent.NetworkStatus
import com.nexus.stream.domain.model.DownloadFailure
import com.nexus.stream.domain.model.DownloadItem
import com.nexus.stream.domain.model.DownloadSource
import com.nexus.stream.domain.model.DownloadState
import com.nexus.stream.domain.model.NetworkGate
import java.util.Locale

const val STORAGE_RESERVE_BYTES = 500L * 1024L * 1024L
const val STORAGE_UNKNOWN_MIN_BYTES = 1024L * 1024L * 1024L
val RETRY_WAIT_MS = listOf(2_000L, 5_000L, 10_000L)
const val MAX_RETRIES = 3

private const val ONE_MIB = 1024L * 1024L
private const val HTTP_OK = 200
private const val HTTP_PARTIAL_CONTENT = 206
private const val HTTP_RANGE_NOT_SATISFIABLE = 416
private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_FORBIDDEN = 403
private const val HTTP_NOT_FOUND = 404
private const val HTTP_GONE = 410
private const val HTTP_CLIENT_TIMEOUT = 408
private const val HTTP_TOO_MANY_REQUESTS = 429
private const val HTTP_SERVER_ERROR_MIN = 500
private const val HTTP_SERVER_ERROR_MAX = 599
private const val HTTP_SUCCESS_MIN = 200
private const val HTTP_SUCCESS_MAX = 299
private const val SPEED_METER_DEFAULT_ALPHA = 0.3

private val EXTENSION_REGEX = Regex("^[a-z0-9]{1,5}$")

enum class ResumeDecision {
    Append,
    Restart,
    Fresh,
    AlreadyComplete,
}

enum class StatusClass {
    Ok,
    Retryable,
    LinkExpired,
    ServerError,
}

fun downloadFileExtension(
    hint: String? = null,
    urlPath: String? = null,
    contentType: String? = null,
): String {
    val fromHint =
        hint
            ?.let { h ->
                val clean = h.substringBefore('?').substringBefore('#').substringAfterLast('/')
                clean.takeIf { it.contains('.') }?.substringAfterLast('.')?.lowercase(Locale.ROOT)
            }?.takeIf { EXTENSION_REGEX.matches(it) }

    val fromPath =
        urlPath
            ?.let { p ->
                val clean = p.substringBefore('?').substringBefore('#').substringAfterLast('/')
                clean.takeIf { it.contains('.') }?.substringAfterLast('.')?.lowercase(Locale.ROOT)
            }?.takeIf { EXTENSION_REGEX.matches(it) }

    val fromType =
        contentType
            ?.let { ct ->
                val clean = ct.substringBefore(';').trim().lowercase(Locale.ROOT)
                when (clean) {
                    "video/mp4" -> "mp4"
                    "video/x-matroska" -> "mkv"
                    "video/webm" -> "webm"
                    "video/quicktime" -> "mov"
                    "video/x-msvideo" -> "avi"
                    else -> null
                }
            }?.takeIf { EXTENSION_REGEX.matches(it) }

    return fromHint ?: fromPath ?: fromType ?: "mp4"
}

fun rangeHeader(offset: Long): String? = if (offset > 0L) "bytes=$offset-" else null

fun decideResume(
    status: Int,
    requestedOffset: Long,
    contentRangeStart: Long?,
    total: Long?,
): ResumeDecision =
    when (status) {
        HTTP_PARTIAL_CONTENT -> {
            if (contentRangeStart == requestedOffset) ResumeDecision.Append else ResumeDecision.Restart
        }

        HTTP_OK -> {
            if (requestedOffset > 0L) ResumeDecision.Restart else ResumeDecision.Fresh
        }

        HTTP_RANGE_NOT_SATISFIABLE -> {
            if (total != null && total > 0L && requestedOffset == total) {
                ResumeDecision.AlreadyComplete
            } else {
                ResumeDecision.Restart
            }
        }

        else -> {
            ResumeDecision.Restart
        }
    }

fun classifyStatus(code: Int): StatusClass =
    when {
        code in listOf(HTTP_UNAUTHORIZED, HTTP_FORBIDDEN, HTTP_NOT_FOUND, HTTP_GONE) -> {
            StatusClass.LinkExpired
        }

        code == HTTP_CLIENT_TIMEOUT || code == HTTP_TOO_MANY_REQUESTS ||
            code in HTTP_SERVER_ERROR_MIN..HTTP_SERVER_ERROR_MAX -> {
            StatusClass.Retryable
        }

        code in HTTP_SUCCESS_MIN..HTTP_SUCCESS_MAX -> {
            StatusClass.Ok
        }

        else -> {
            StatusClass.ServerError
        }
    }

fun looksLikeNotAVideo(
    contentType: String?,
    totalBytes: Long,
): Boolean {
    val cleanType = contentType?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)
    return when {
        cleanType == "application/vnd.apple.mpegurl" -> true
        cleanType != null && cleanType.startsWith("text/") -> true
        cleanType in listOf("application/json", "application/xml", "application/xhtml+xml") -> true
        cleanType != null && (cleanType.startsWith("video/") || cleanType.startsWith("audio/")) -> false
        cleanType in listOf("application/octet-stream", "video/x-matroska", "binary/octet-stream") -> false
        cleanType.isNullOrBlank() && totalBytes in 1 until ONE_MIB -> true
        else -> false
    }
}

fun hasEnoughStorage(
    availableBytes: Long,
    remainingBytes: Long,
    reserveBytes: Long = STORAGE_RESERVE_BYTES,
): Boolean =
    if (remainingBytes <= 0L) {
        availableBytes >= STORAGE_UNKNOWN_MIN_BYTES
    } else {
        availableBytes >= remainingBytes + reserveBytes
    }

fun networkGate(
    status: NetworkStatus,
    wifiOnly: Boolean,
    allowMetered: Boolean,
): NetworkGate =
    when (status) {
        NetworkStatus.NONE -> NetworkGate.WaitForNetwork
        NetworkStatus.METERED -> if (wifiOnly && !allowMetered) NetworkGate.WaitForWifi else NetworkGate.Proceed
        NetworkStatus.UNMETERED -> NetworkGate.Proceed
    }

fun pickToStart(
    items: List<DownloadItem>,
    maxConcurrent: Int,
    gate: (DownloadItem) -> NetworkGate,
): List<String> {
    val downloadingCount = items.count { it.state == DownloadState.DOWNLOADING }
    val availableSlots = (maxConcurrent - downloadingCount).coerceAtLeast(0)
    if (availableSlots == 0) return emptyList()

    val eligibleStates =
        setOf(
            DownloadState.QUEUED,
            DownloadState.WAITING_FOR_WIFI,
            DownloadState.WAITING_FOR_NETWORK,
        )

    val runningHashes =
        items
            .filter { it.state == DownloadState.DOWNLOADING }
            .mapNotNull { (it.source as? DownloadSource.Torrent)?.infoHash?.lowercase() }
            .toSet()

    val eligible =
        items
            .filter { it.state in eligibleStates && gate(it) == NetworkGate.Proceed }
            .sortedBy { it.createdAt }
    val runnableIds = runnableTorrentIds(eligible, runningHashes).toSet()

    return eligible
        .filter { it.id in runnableIds }
        .take(availableSlots)
        .map { it.id }
}

fun findDuplicate(
    items: List<DownloadItem>,
    metaId: String,
    videoId: String,
): DownloadItem? =
    items.firstOrNull {
        it.metaId == metaId && it.videoId == videoId && it.state != DownloadState.FAILED
    }

fun progressFraction(
    downloaded: Long,
    total: Long,
): Float? =
    if (total <= 0L) {
        null
    } else {
        (downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    }

class SpeedMeter(
    private val alpha: Double = SPEED_METER_DEFAULT_ALPHA,
) {
    private var currentSpeed: Double = 0.0
    private var initialized: Boolean = false

    val speed: Long
        get() = currentSpeed.toLong()

    fun addSample(bytesPerSecond: Long): Long {
        currentSpeed =
            if (!initialized) {
                initialized = true
                bytesPerSecond.toDouble()
            } else {
                alpha * bytesPerSecond + (1.0 - alpha) * currentSpeed
            }
        return currentSpeed.toLong()
    }

    fun reset() {
        currentSpeed = 0.0
        initialized = false
    }
}

fun etaSeconds(
    remainingBytes: Long,
    speedBps: Long,
): Long? =
    if (speedBps <= 0L || remainingBytes <= 0L) {
        null
    } else {
        remainingBytes / speedBps
    }
