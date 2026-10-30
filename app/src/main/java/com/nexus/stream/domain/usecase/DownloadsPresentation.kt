package com.nexus.stream.domain.usecase

import com.nexus.stream.R
import com.nexus.stream.domain.model.DownloadFailure
import com.nexus.stream.domain.model.DownloadItem
import com.nexus.stream.domain.model.DownloadLive
import com.nexus.stream.domain.model.DownloadState
import com.nexus.stream.domain.model.PauseReason

enum class DownloadSectionKind {
    IN_PROGRESS,
    PROBLEMS,
    READY,
}

data class DownloadSection(
    val kind: DownloadSectionKind,
    val items: List<DownloadItem>,
)

data class StatusText(
    val stringRes: Int,
    val args: List<Any> = emptyList(),
)

enum class RowAction {
    PAUSE,
    RESUME,
    RETRY,
    FIND_NEW_LINK,
    REMOVE,
    NONE,
}

sealed interface DetailDownloadState {
    data object None : DetailDownloadState

    data object Queued : DetailDownloadState

    data class Downloading(
        val percent: Int? = null,
    ) : DetailDownloadState

    data object Paused : DetailDownloadState

    data object Failed : DetailDownloadState

    data object Completed : DetailDownloadState
}

data class StorageSummary(
    val usedBytes: Long,
    val freeBytes: Long,
    val low: Boolean,
)

fun rowTitle(item: DownloadItem): String = item.metaName ?: item.title

private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 3600L
private const val PERCENT_MAX = 100

fun formatEta(seconds: Long?): String {
    if (seconds == null || seconds <= 0L) return "0s"
    val hours = seconds / SECONDS_PER_HOUR
    val minutes = (seconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
    val remSec = seconds % SECONDS_PER_MINUTE
    return when {
        hours > 0 -> if (minutes > 0) "$hours h $minutes min" else "$hours h"
        minutes > 0 -> "$minutes min"
        else -> "${remSec}s"
    }
}

private fun downloadingStatus(
    item: DownloadItem,
    live: DownloadLive?,
): StatusText {
    val speed = live?.speedBps ?: 0L
    val total = item.totalBytes
    val downloaded = item.downloadedBytes
    return if (total > 0L) {
        val percent = ((downloaded * PERCENT_MAX) / total).toInt().coerceIn(0, PERCENT_MAX)
        if (speed <= 0L) {
            StatusText(R.string.downloads_status_starting, listOf(percent))
        } else {
            val eta = live?.etaSeconds ?: etaSeconds(total - downloaded, speed)
            val speedStr = formatDownloadSpeed(speed)
            val etaStr = formatEta(eta)
            StatusText(R.string.downloads_status_downloading, listOf(percent, speedStr, etaStr))
        }
    } else {
        val speedStr = formatDownloadSpeed(speed)
        StatusText(R.string.downloads_status_downloading_unknown, listOf(downloaded, speedStr))
    }
}

private fun pausedStatus(item: DownloadItem): StatusText {
    val total = item.totalBytes
    val downloaded = item.downloadedBytes
    val percent = if (total > 0L) ((downloaded * PERCENT_MAX) / total).toInt().coerceIn(0, PERCENT_MAX) else 0
    return if (item.pauseReason == PauseReason.SYSTEM_TIME_LIMIT) {
        StatusText(R.string.downloads_status_paused_system, listOf(percent))
    } else {
        StatusText(R.string.downloads_status_paused, listOf(percent))
    }
}

fun rowStatus(
    item: DownloadItem,
    live: DownloadLive? = null,
): StatusText =
    when (item.state) {
        DownloadState.DOWNLOADING -> {
            downloadingStatus(item, live)
        }

        DownloadState.QUEUED -> {
            StatusText(R.string.downloads_status_queued)
        }

        DownloadState.WAITING_FOR_WIFI -> {
            StatusText(R.string.downloads_status_wifi)
        }

        DownloadState.WAITING_FOR_NETWORK -> {
            StatusText(R.string.downloads_status_network)
        }

        DownloadState.NEEDS_FILE_PICK -> {
            StatusText(R.string.downloads_status_pick_file)
        }

        DownloadState.PAUSED -> {
            pausedStatus(item)
        }

        DownloadState.FAILED -> {
            val failure = item.failure ?: DownloadFailure.UNKNOWN
            StatusText(failureReasonRes(failure))
        }

        DownloadState.COMPLETED -> {
            StatusText(
                R.string.downloads_status_done,
                listOf(item.totalBytes, item.completedAt ?: item.updatedAt),
            )
        }
    }

fun primaryAction(item: DownloadItem): RowAction =
    when (item.state) {
        DownloadState.DOWNLOADING,
        DownloadState.QUEUED,
        DownloadState.WAITING_FOR_WIFI,
        DownloadState.WAITING_FOR_NETWORK,
        DownloadState.NEEDS_FILE_PICK,
        -> {
            RowAction.PAUSE
        }

        DownloadState.PAUSED -> {
            RowAction.RESUME
        }

        DownloadState.FAILED -> {
            when (item.failure) {
                DownloadFailure.LINK_EXPIRED -> RowAction.FIND_NEW_LINK
                DownloadFailure.FILE_MISSING -> RowAction.REMOVE
                else -> RowAction.RETRY
            }
        }

        DownloadState.COMPLETED -> {
            RowAction.NONE
        }
    }

fun detailButtonState(
    items: List<DownloadItem>,
    metaId: String,
    videoId: String,
): DetailDownloadState {
    val matching = items.filter { it.metaId == metaId && it.videoId == videoId }
    val completed = matching.firstOrNull { it.state == DownloadState.COMPLETED }
    val downloading = matching.firstOrNull { it.state == DownloadState.DOWNLOADING }
    val queued =
        matching.firstOrNull {
            it.state == DownloadState.QUEUED ||
                it.state == DownloadState.WAITING_FOR_WIFI ||
                it.state == DownloadState.WAITING_FOR_NETWORK ||
                it.state == DownloadState.NEEDS_FILE_PICK
        }
    val paused = matching.firstOrNull { it.state == DownloadState.PAUSED }
    val failed = matching.firstOrNull { it.state == DownloadState.FAILED }

    return when {
        completed != null -> {
            DetailDownloadState.Completed
        }

        downloading != null -> {
            val total = downloading.totalBytes
            val percent =
                if (total > 0L) {
                    ((downloading.downloadedBytes * PERCENT_MAX) / total).toInt().coerceIn(0, PERCENT_MAX)
                } else {
                    null
                }
            DetailDownloadState.Downloading(percent)
        }

        queued != null -> {
            DetailDownloadState.Queued
        }

        paused != null -> {
            DetailDownloadState.Paused
        }

        failed != null -> {
            DetailDownloadState.Failed
        }

        else -> {
            DetailDownloadState.None
        }
    }
}

fun storageSummary(
    items: List<DownloadItem>,
    freeBytes: Long,
): StorageSummary {
    val used = items.sumOf { it.downloadedBytes }
    val low = freeBytes < STORAGE_UNKNOWN_MIN_BYTES
    return StorageSummary(usedBytes = used, freeBytes = freeBytes, low = low)
}

fun activeCount(items: List<DownloadItem>): Int =
    items.count {
        it.state == DownloadState.DOWNLOADING ||
            it.state == DownloadState.QUEUED ||
            it.state == DownloadState.WAITING_FOR_WIFI ||
            it.state == DownloadState.WAITING_FOR_NETWORK
    }

private const val RANK_DOWNLOADING = 1
private const val RANK_QUEUED_OR_WAITING = 2
private const val RANK_PAUSED = 3
private const val RANK_OTHER = 4

private fun inProgressRank(state: DownloadState): Int =
    when (state) {
        DownloadState.DOWNLOADING -> RANK_DOWNLOADING

        DownloadState.QUEUED,
        DownloadState.WAITING_FOR_WIFI,
        DownloadState.WAITING_FOR_NETWORK,
        DownloadState.NEEDS_FILE_PICK,
        -> RANK_QUEUED_OR_WAITING

        DownloadState.PAUSED -> RANK_PAUSED

        else -> RANK_OTHER
    }

fun groupDownloads(
    items: List<DownloadItem>,
    familyMode: Boolean,
): List<DownloadSection> {
    val filtered =
        if (familyMode) {
            items.filterNot { isAdultText(it.title) || (it.metaName != null && isAdultText(it.metaName)) }
        } else {
            items
        }

    val inProgressItems = mutableListOf<DownloadItem>()
    val problemItems = mutableListOf<DownloadItem>()
    val readyItems = mutableListOf<DownloadItem>()

    for (item in filtered) {
        when (item.state) {
            DownloadState.DOWNLOADING,
            DownloadState.QUEUED,
            DownloadState.WAITING_FOR_WIFI,
            DownloadState.WAITING_FOR_NETWORK,
            DownloadState.NEEDS_FILE_PICK,
            DownloadState.PAUSED,
            -> inProgressItems.add(item)

            DownloadState.FAILED -> problemItems.add(item)

            DownloadState.COMPLETED -> readyItems.add(item)
        }
    }

    val sortedInProgress =
        inProgressItems.sortedWith { a, b ->
            val rankA = inProgressRank(a.state)
            val rankB = inProgressRank(b.state)
            if (rankA != rankB) {
                rankA.compareTo(rankB)
            } else {
                a.createdAt.compareTo(b.createdAt)
            }
        }

    val sortedProblems = problemItems.sortedByDescending { it.updatedAt }
    val sortedReady = readyItems.sortedByDescending { it.completedAt ?: it.updatedAt }

    val sections = mutableListOf<DownloadSection>()
    if (sortedInProgress.isNotEmpty()) {
        sections.add(DownloadSection(DownloadSectionKind.IN_PROGRESS, sortedInProgress))
    }
    if (sortedProblems.isNotEmpty()) {
        sections.add(DownloadSection(DownloadSectionKind.PROBLEMS, sortedProblems))
    }
    if (sortedReady.isNotEmpty()) {
        sections.add(DownloadSection(DownloadSectionKind.READY, sortedReady))
    }
    return sections
}
