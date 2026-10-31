package com.nexus.stream.domain.usecase

import com.nexus.stream.R
import com.nexus.stream.domain.model.DownloadFailure
import com.nexus.stream.domain.model.DownloadItem
import com.nexus.stream.domain.model.DownloadKind
import com.nexus.stream.domain.model.DownloadLive
import com.nexus.stream.domain.model.DownloadPhase
import com.nexus.stream.domain.model.DownloadState
import com.nexus.stream.domain.model.GalleryFailure
import com.nexus.stream.domain.model.GalleryState
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
    CHOOSE_FILE,
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

fun detailDownloadRes(state: DetailDownloadState): Int =
    when (state) {
        DetailDownloadState.None -> {
            R.string.detail_download
        }

        DetailDownloadState.Queued -> {
            R.string.detail_download_queued
        }

        is DetailDownloadState.Downloading -> {
            if (state.percent != null) R.string.detail_download_progress else R.string.detail_download_queued
        }

        DetailDownloadState.Paused -> {
            R.string.detail_download_paused
        }

        DetailDownloadState.Failed -> {
            R.string.detail_download_failed
        }

        DetailDownloadState.Completed -> {
            R.string.detail_download_done
        }
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

private object StatusResolvers {
    fun transferringStatus(
        item: DownloadItem,
        live: DownloadLive?,
        percent: Int,
    ): StatusText {
        val total = item.totalBytes
        val downloaded = item.downloadedBytes
        val speed = live?.speedBps ?: 0L
        val eta = live?.etaSeconds ?: etaSeconds(total - downloaded, speed)
        val speedStr = formatDownloadSpeed(speed)
        val etaStr = formatEta(eta)

        return if (item.kind == DownloadKind.TORRENT) {
            val peers = live?.peers ?: 0
            StatusText(R.string.downloads_status_torrent, listOf(percent, speedStr, etaStr, peers))
        } else if (total > 0L) {
            if (speed <= 0L) {
                StatusText(R.string.downloads_status_starting, listOf(percent))
            } else {
                StatusText(R.string.downloads_status_downloading, listOf(percent, speedStr, etaStr))
            }
        } else {
            StatusText(R.string.downloads_status_downloading_unknown, listOf(downloaded, speedStr))
        }
    }

    fun downloadingStatus(
        item: DownloadItem,
        live: DownloadLive?,
    ): StatusText {
        val phase = live?.phase ?: DownloadPhase.TRANSFERRING
        val total = item.totalBytes
        val downloaded = item.downloadedBytes
        val percent = if (total > 0L) ((downloaded * PERCENT_MAX) / total).toInt().coerceIn(0, PERCENT_MAX) else 0

        return when (phase) {
            DownloadPhase.CONNECTING -> {
                StatusText(R.string.downloads_status_peers_looking)
            }

            DownloadPhase.FETCHING_METADATA -> {
                StatusText(R.string.downloads_status_metadata)
            }

            DownloadPhase.CHECKING_FILES -> {
                if (percent > 0) {
                    StatusText(R.string.downloads_status_checking_percent, listOf(percent))
                } else {
                    StatusText(R.string.downloads_status_checking)
                }
            }

            DownloadPhase.STALLED -> {
                StatusText(R.string.downloads_status_stalled)
            }

            DownloadPhase.TRANSFERRING -> {
                transferringStatus(item, live, percent)
            }
        }
    }

    fun pausedStatus(item: DownloadItem): StatusText {
        val total = item.totalBytes
        val downloaded = item.downloadedBytes
        val percent = if (total > 0L) ((downloaded * PERCENT_MAX) / total).toInt().coerceIn(0, PERCENT_MAX) else 0
        val res =
            if (item.pauseReason == PauseReason.SYSTEM_TIME_LIMIT) {
                R.string.downloads_status_paused_system
            } else {
                R.string.downloads_status_paused
            }
        return StatusText(res, listOf(percent))
    }

    fun completedStatus(
        item: DownloadItem,
        live: DownloadLive?,
    ): StatusText {
        val doneStatus =
            StatusText(
                R.string.downloads_status_done,
                listOf(item.totalBytes, item.completedAt ?: item.updatedAt),
            )
        return when (item.galleryState) {
            GalleryState.SAVED -> {
                StatusText(R.string.downloads_status_in_gallery, listOf(doneStatus))
            }

            GalleryState.SAVING -> {
                val copied = live?.copiedBytes ?: 0L
                val percent =
                    if (item.totalBytes > 0L) {
                        ((copied * PERCENT_MAX) / item.totalBytes).toInt().coerceIn(0, PERCENT_MAX)
                    } else {
                        0
                    }
                StatusText(R.string.downloads_status_saving_gallery, listOf(percent))
            }

            GalleryState.FAILED -> {
                val reason = galleryFailureReasonRes(item.galleryFailure ?: GalleryFailure.UNAVAILABLE)
                StatusText(R.string.downloads_gallery_failed, listOf(reason))
            }

            GalleryState.NONE -> {
                doneStatus
            }
        }
    }

    fun galleryFailureReasonRes(failure: GalleryFailure): Int =
        when (failure) {
            GalleryFailure.NO_SPACE -> R.string.downloads_gallery_reason_space
            GalleryFailure.WRITE_FAILED -> R.string.downloads_gallery_reason_write
            GalleryFailure.SOURCE_MISSING -> R.string.downloads_gallery_reason_missing
            GalleryFailure.UNAVAILABLE -> R.string.downloads_gallery_reason_unavailable
        }
}

val galleryFailureReasonRes: (GalleryFailure) -> Int = { StatusResolvers.galleryFailureReasonRes(it) }

fun rowStatus(
    item: DownloadItem,
    live: DownloadLive? = null,
): StatusText =
    when (item.state) {
        DownloadState.DOWNLOADING -> StatusResolvers.downloadingStatus(item, live)
        DownloadState.QUEUED -> StatusText(R.string.downloads_status_queued)
        DownloadState.WAITING_FOR_WIFI -> StatusText(R.string.downloads_status_wifi)
        DownloadState.WAITING_FOR_NETWORK -> StatusText(R.string.downloads_status_network)
        DownloadState.NEEDS_FILE_PICK -> StatusText(R.string.downloads_action_choose_file)
        DownloadState.PAUSED -> StatusResolvers.pausedStatus(item)
        DownloadState.FAILED -> StatusText(failureReasonRes(item.failure ?: DownloadFailure.UNKNOWN))
        DownloadState.COMPLETED -> StatusResolvers.completedStatus(item, live)
    }

enum class ActionIcon {
    PAUSE,
    PLAY,
    REFRESH,
    SEARCH,
    DELETE,
    LIST,
}

val actionIcon: (RowAction) -> ActionIcon? = { action ->
    when (action) {
        RowAction.PAUSE -> ActionIcon.PAUSE
        RowAction.RESUME -> ActionIcon.PLAY
        RowAction.RETRY -> ActionIcon.REFRESH
        RowAction.FIND_NEW_LINK -> ActionIcon.SEARCH
        RowAction.REMOVE -> ActionIcon.DELETE
        RowAction.CHOOSE_FILE -> ActionIcon.LIST
        RowAction.NONE -> null
    }
}

val actionDescription: (RowAction) -> Int = { action ->
    when (action) {
        RowAction.PAUSE -> R.string.downloads_action_desc_pause
        RowAction.RESUME -> R.string.downloads_action_desc_resume
        RowAction.RETRY -> R.string.downloads_action_desc_retry
        RowAction.FIND_NEW_LINK -> R.string.downloads_action_desc_new_link
        RowAction.REMOVE -> R.string.downloads_action_desc_remove
        RowAction.CHOOSE_FILE -> R.string.downloads_action_desc_choose_file
        RowAction.NONE -> 0
    }
}

enum class StorageHeaderAction {
    PAUSE_ALL,
    RESUME_ALL,
    NONE,
}

data class StorageCardModel(
    val usedBytes: Long,
    val freeBytes: Long,
    val low: Boolean,
    val headerAction: StorageHeaderAction,
    val showDeleteAll: Boolean,
)

fun storageCardModel(
    items: List<DownloadItem>,
    freeBytes: Long,
): StorageCardModel {
    val used = items.filter { it.state == DownloadState.COMPLETED }.sumOf { it.totalBytes }
    val low = freeBytes < STORAGE_UNKNOWN_MIN_BYTES

    val activeStates =
        setOf(
            DownloadState.DOWNLOADING,
            DownloadState.QUEUED,
            DownloadState.WAITING_FOR_WIFI,
            DownloadState.WAITING_FOR_NETWORK,
            DownloadState.NEEDS_FILE_PICK,
        )
    val hasActive = items.any { it.state in activeStates }
    val hasPaused = items.any { it.state == DownloadState.PAUSED }

    val headerAction =
        when {
            hasActive -> StorageHeaderAction.PAUSE_ALL
            hasPaused -> StorageHeaderAction.RESUME_ALL
            else -> StorageHeaderAction.NONE
        }

    val showDeleteAll = items.any { it.state == DownloadState.COMPLETED }

    return StorageCardModel(
        usedBytes = used,
        freeBytes = freeBytes,
        low = low,
        headerAction = headerAction,
        showDeleteAll = showDeleteAll,
    )
}

enum class RowMenuItem {
    SAVE_TO_GALLERY,
    REMOVE_FROM_GALLERY,
    RETRY_GALLERY,
    CANCEL,
    DELETE,
    REMOVE,
}

fun rowMenu(item: DownloadItem): List<RowMenuItem> =
    when (item.state) {
        DownloadState.COMPLETED -> {
            when (item.galleryState) {
                GalleryState.NONE -> listOf(RowMenuItem.SAVE_TO_GALLERY, RowMenuItem.DELETE)
                GalleryState.SAVED -> listOf(RowMenuItem.REMOVE_FROM_GALLERY, RowMenuItem.DELETE)
                GalleryState.FAILED -> listOf(RowMenuItem.RETRY_GALLERY, RowMenuItem.DELETE)
                GalleryState.SAVING -> listOf(RowMenuItem.DELETE)
            }
        }

        DownloadState.DOWNLOADING -> {
            listOf(RowMenuItem.CANCEL)
        }

        DownloadState.FAILED -> {
            listOf(RowMenuItem.REMOVE)
        }

        else -> {
            listOf(RowMenuItem.CANCEL)
        }
    }

fun primaryAction(item: DownloadItem): RowAction =
    when (item.state) {
        DownloadState.DOWNLOADING,
        DownloadState.QUEUED,
        DownloadState.WAITING_FOR_WIFI,
        DownloadState.WAITING_FOR_NETWORK,
        -> {
            RowAction.PAUSE
        }

        DownloadState.NEEDS_FILE_PICK -> {
            RowAction.CHOOSE_FILE
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

private val IN_PROGRESS_RANKS =
    mapOf(
        DownloadState.DOWNLOADING to RANK_DOWNLOADING,
        DownloadState.QUEUED to RANK_QUEUED_OR_WAITING,
        DownloadState.WAITING_FOR_WIFI to RANK_QUEUED_OR_WAITING,
        DownloadState.WAITING_FOR_NETWORK to RANK_QUEUED_OR_WAITING,
        DownloadState.NEEDS_FILE_PICK to RANK_QUEUED_OR_WAITING,
        DownloadState.PAUSED to RANK_PAUSED,
    )

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
        inProgressItems.sortedWith(
            compareBy<DownloadItem> { IN_PROGRESS_RANKS[it.state] ?: RANK_OTHER }
                .thenBy { it.createdAt },
        )

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
