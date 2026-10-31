package com.nexus.stream.domain.usecase

import com.nexus.stream.R
import com.nexus.stream.domain.model.DownloadFailure
import com.nexus.stream.domain.model.DownloadItem
import com.nexus.stream.domain.model.DownloadLive
import com.nexus.stream.domain.model.DownloadState
import java.util.Locale

private const val PERCENT_MAX = 100
private const val KB_BYTES = 1024.0
private const val MB_BYTES = 1024.0 * 1024.0
private const val GB_BYTES = 1024.0 * 1024.0 * 1024.0
private const val ROUND_FACTOR = 10.0

data class AggregateNotificationModel(
    val titleRes: Int,
    val titleArgs: List<Any> = emptyList(),
    val textRes: Int? = null,
    val textArgs: List<Any> = emptyList(),
    val percent: Int? = null,
    val speedBps: Long = 0L,
    val speedText: String? = null,
    val indeterminate: Boolean = false,
    val pauseAllAction: Boolean = false,
    val resumeAllAction: Boolean = false,
    val publicTitleRes: Int = R.string.download_notif_public,
    val publicText: String? = null,
)

sealed interface DownloadEventNotification {
    val publicTitleRes: Int
    val publicText: String?

    data class Completed(
        val downloadId: String,
        val titleRes: Int = R.string.download_event_done_title,
        val privateText: String,
        override val publicTitleRes: Int = R.string.download_event_public_done,
        override val publicText: String? = null,
    ) : DownloadEventNotification

    data class Failed(
        val downloadId: String,
        val titleRes: Int = R.string.download_event_failed_title,
        val reasonRes: Int,
        val privateText: String,
        override val publicTitleRes: Int = R.string.download_event_public_failed,
        override val publicText: String? = null,
    ) : DownloadEventNotification

    data class PausedBySystem(
        val titleRes: Int = R.string.download_event_paused_title,
        val textRes: Int = R.string.download_event_paused_text,
        override val publicTitleRes: Int = R.string.download_notif_public,
        override val publicText: String? = null,
        val resumeActionAvailable: Boolean = true,
    ) : DownloadEventNotification
}

fun formatDownloadSpeed(speedBps: Long): String {
    if (speedBps <= 0L) return "0 B/s"

    return when {
        speedBps >= GB_BYTES -> formatRate(speedBps / GB_BYTES, "GB/s")
        speedBps >= MB_BYTES -> formatRate(speedBps / MB_BYTES, "MB/s")
        speedBps >= KB_BYTES -> formatRate(speedBps / KB_BYTES, "KB/s")
        else -> "$speedBps B/s"
    }
}

private fun formatRate(
    value: Double,
    unit: String,
): String {
    val rounded10 = Math.round(value * ROUND_FACTOR) / ROUND_FACTOR
    return if (rounded10 % 1.0 == 0.0) {
        String.format(Locale.US, "%.0f %s", rounded10, unit)
    } else {
        String.format(Locale.US, "%.1f %s", rounded10, unit)
    }
}

fun failureReasonRes(failure: DownloadFailure): Int =
    when (failure) {
        DownloadFailure.LINK_EXPIRED -> R.string.download_failure_link_expired

        DownloadFailure.NOT_A_VIDEO,
        DownloadFailure.TORRENT_NO_VIDEO_FILE,
        -> R.string.download_failure_not_video

        DownloadFailure.SERVER_ERROR -> R.string.download_failure_server

        DownloadFailure.NETWORK -> R.string.download_failure_network

        DownloadFailure.INSUFFICIENT_STORAGE,
        DownloadFailure.STORAGE_UNAVAILABLE,
        -> R.string.download_failure_storage

        DownloadFailure.INCOMPLETE -> R.string.download_failure_incomplete

        DownloadFailure.FILE_MISSING -> R.string.download_failure_missing

        DownloadFailure.TORRENT_NO_PEERS -> R.string.download_failure_no_peers

        DownloadFailure.TORRENT_IN_USE -> R.string.download_failure_torrent_in_use

        DownloadFailure.UNSUPPORTED_SOURCE,
        DownloadFailure.TORRENT_ENGINE,
        DownloadFailure.UNKNOWN,
        -> R.string.download_failure_unknown
    }

private fun buildDownloadingNotification(
    downloading: List<DownloadItem>,
    totalActiveCount: Int,
    live: Map<String, DownloadLive>,
): AggregateNotificationModel {
    val totalSpeed = downloading.sumOf { live[it.id]?.speedBps ?: 0L }
    val anyTotalUnknown = downloading.any { it.totalBytes <= 0L }
    val indeterminate = anyTotalUnknown
    val percent =
        if (indeterminate) {
            null
        } else {
            val sumDownloaded = downloading.sumOf { live[it.id]?.bytes ?: it.downloadedBytes }
            val sumTotal = downloading.sumOf { it.totalBytes }
            if (sumTotal > 0L) {
                ((sumDownloaded * PERCENT_MAX) / sumTotal).toInt().coerceIn(0, PERCENT_MAX)
            } else {
                null
            }
        }

    val isSingle = downloading.size == 1 && totalActiveCount == 1
    val titleRes = if (isSingle) R.string.download_notif_title_one else R.string.download_notif_title_many
    val titleArgs = if (isSingle) emptyList() else listOf(totalActiveCount)
    val speedText = formatDownloadSpeed(totalSpeed)

    return AggregateNotificationModel(
        titleRes = titleRes,
        titleArgs = titleArgs,
        textRes = R.string.download_notif_text_progress,
        textArgs = listOf(percent ?: 0, speedText),
        percent = percent,
        speedBps = totalSpeed,
        speedText = speedText,
        indeterminate = indeterminate,
        pauseAllAction = true,
        resumeAllAction = false,
        publicTitleRes = R.string.download_notif_public,
        publicText = null,
    )
}

private fun buildWaitingNotification(
    activeItems: List<DownloadItem>,
    waitingWifi: List<DownloadItem>,
    waitingNetwork: List<DownloadItem>,
): AggregateNotificationModel {
    val titleRes =
        when {
            waitingWifi.isNotEmpty() && waitingNetwork.isEmpty() -> R.string.download_notif_waiting_wifi
            waitingNetwork.isNotEmpty() && waitingWifi.isEmpty() -> R.string.download_notif_waiting_network
            waitingWifi.isNotEmpty() && waitingNetwork.isNotEmpty() -> R.string.download_notif_waiting_both
            activeItems.size == 1 -> R.string.download_notif_title_one
            else -> R.string.download_notif_title_many
        }
    val titleArgs =
        if (titleRes == R.string.download_notif_title_many) listOf(activeItems.size) else emptyList()

    return AggregateNotificationModel(
        titleRes = titleRes,
        titleArgs = titleArgs,
        textRes = null,
        textArgs = emptyList(),
        percent = null,
        speedBps = 0L,
        speedText = null,
        indeterminate = false,
        pauseAllAction = true,
        resumeAllAction = false,
        publicTitleRes = R.string.download_notif_public,
        publicText = null,
    )
}

fun aggregateNotification(
    items: List<DownloadItem>,
    live: Map<String, DownloadLive> = emptyMap(),
): AggregateNotificationModel? {
    val activeStates =
        setOf(
            DownloadState.DOWNLOADING,
            DownloadState.QUEUED,
            DownloadState.WAITING_FOR_WIFI,
            DownloadState.WAITING_FOR_NETWORK,
        )
    val activeItems = items.filter { it.state in activeStates }
    if (activeItems.isEmpty()) return null

    val downloading = activeItems.filter { it.state == DownloadState.DOWNLOADING }
    val waitingWifi = activeItems.filter { it.state == DownloadState.WAITING_FOR_WIFI }
    val waitingNetwork = activeItems.filter { it.state == DownloadState.WAITING_FOR_NETWORK }

    return if (downloading.isNotEmpty()) {
        buildDownloadingNotification(downloading, activeItems.size, live)
    } else {
        buildWaitingNotification(activeItems, waitingWifi, waitingNetwork)
    }
}

fun eventNotification(
    item: DownloadItem,
    failure: DownloadFailure? = item.failure,
): DownloadEventNotification? =
    when (item.state) {
        DownloadState.COMPLETED -> {
            DownloadEventNotification.Completed(
                downloadId = item.id,
                privateText = item.title,
            )
        }

        DownloadState.FAILED -> {
            val f = failure ?: item.failure ?: DownloadFailure.UNKNOWN
            DownloadEventNotification.Failed(
                downloadId = item.id,
                reasonRes = failureReasonRes(f),
                privateText = item.title,
            )
        }

        else -> {
            null
        }
    }

fun pausedBySystemNotification(): DownloadEventNotification.PausedBySystem = DownloadEventNotification.PausedBySystem()
