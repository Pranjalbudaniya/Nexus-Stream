package com.nexus.stream.domain.repository

import com.nexus.stream.data.torrent.NetworkStatus
import com.nexus.stream.domain.model.DownloadFailure
import com.nexus.stream.domain.usecase.AggregateNotificationModel
import com.nexus.stream.domain.usecase.DownloadEventNotification
import kotlinx.coroutines.flow.StateFlow
import java.io.File

const val NOTIFICATION_CHANNEL_PROGRESS = "download_progress"
const val NOTIFICATION_CHANNEL_EVENTS = "download_events"
const val EXTRA_OPEN_DOWNLOADS = "open_downloads"

data class HttpTask(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val partFile: File,
    val offset: Long = 0L,
    val etag: String? = null,
    val lastModified: String? = null,
    val supportsResumeKnown: Boolean = true,
)

data class ResumeInfo(
    val bytes: Long,
    val etag: String? = null,
    val lastModified: String? = null,
    val supportsResume: Boolean = true,
)

sealed interface HttpResult {
    data class Completed(
        val total: Long,
        val etag: String? = null,
        val lastModified: String? = null,
        val supportsResume: Boolean = true,
    ) : HttpResult

    data class Failed(
        val failure: DownloadFailure,
        val resumeInfo: ResumeInfo? = null,
    ) : HttpResult

    data class Cancelled(
        val resumeInfo: ResumeInfo? = null,
    ) : HttpResult
}

interface HttpDownloader {
    suspend fun download(
        task: HttpTask,
        onProgress: (bytes: Long, total: Long) -> Unit,
    ): HttpResult
}

interface DownloadFileStore {
    val root: File

    fun partFile(id: String): File

    fun finishedFile(
        id: String,
        extension: String,
    ): File

    fun fileOf(id: String): File?

    fun complete(
        id: String,
        extension: String,
    ): File

    fun delete(id: String)

    fun deletePart(id: String)

    fun listAllFiles(): List<File>
}

interface DownloadNetwork {
    val status: StateFlow<NetworkStatus>
}

interface DownloadNotifier {
    fun showAggregate(model: AggregateNotificationModel)

    fun showEvent(notification: DownloadEventNotification)

    fun cancelAggregate()

    fun cancelEvent(downloadId: String)
}

interface DownloadServiceController {
    fun ensureRunning()

    fun stop()
}

fun interface DownloadClock {
    fun now(): Long
}
