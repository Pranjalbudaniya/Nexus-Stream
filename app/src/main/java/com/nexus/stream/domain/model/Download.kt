package com.nexus.stream.domain.model

enum class GalleryState {
    NONE,
    SAVING,
    SAVED,
    FAILED,
}

enum class GalleryFailure {
    NO_SPACE,
    WRITE_FAILED,
    SOURCE_MISSING,
    UNAVAILABLE,
}

enum class DownloadKind {
    HTTP,
    TORRENT,
}

enum class DownloadState {
    QUEUED,
    DOWNLOADING,
    PAUSED,
    WAITING_FOR_WIFI,
    WAITING_FOR_NETWORK,
    NEEDS_FILE_PICK,
    COMPLETED,
    FAILED,
}

enum class PauseReason {
    USER,
    SYSTEM_TIME_LIMIT,
}

enum class DownloadFailure {
    LINK_EXPIRED,
    NOT_A_VIDEO,
    SERVER_ERROR,
    NETWORK,
    INSUFFICIENT_STORAGE,
    STORAGE_UNAVAILABLE,
    INCOMPLETE,
    FILE_MISSING,
    UNSUPPORTED_SOURCE,
    TORRENT_NO_VIDEO_FILE,
    TORRENT_ENGINE,
    TORRENT_NO_PEERS,
    TORRENT_IN_USE,
    UNKNOWN,
}

sealed interface DownloadSource {
    data class Http(
        val url: String,
        val headers: Map<String, String> = emptyMap(),
    ) : DownloadSource {
        override fun toString(): String = "DownloadSource.Http"
    }

    data class Torrent(
        val infoHash: String,
        val trackers: List<String> = emptyList(),
        val fileIdx: Int? = null,
    ) : DownloadSource {
        override fun toString(): String = "DownloadSource.Torrent"
    }
}

data class DownloadDisplay(
    val metaId: String,
    val type: String,
    val title: String,
    val metaName: String? = null,
    val posterUrl: String? = null,
    val videoId: String,
    val season: Int? = null,
    val episode: Int? = null,
    val fileNameHint: String? = null,
) {
    override fun toString(): String = "DownloadDisplay(metaId=$metaId, videoId=$videoId)"
}

data class DownloadRequest(
    val source: DownloadSource,
    val display: DownloadDisplay,
    val sizeHint: Long? = null,
) {
    override fun toString(): String = "DownloadRequest(source=$source, sizeHint=$sizeHint)"
}

data class DownloadItem(
    val id: String,
    val kind: DownloadKind,
    val source: DownloadSource,
    val metaId: String,
    val type: String,
    val title: String,
    val metaName: String? = null,
    val posterUrl: String? = null,
    val videoId: String,
    val season: Int? = null,
    val episode: Int? = null,
    val fileNameHint: String? = null,
    val fileName: String,
    val totalBytes: Long = 0L,
    val downloadedBytes: Long = 0L,
    val state: DownloadState,
    val pauseReason: PauseReason? = null,
    val failure: DownloadFailure? = null,
    val filePath: String? = null,
    val supportsResume: Boolean = true,
    val etag: String? = null,
    val lastModified: String? = null,
    val allowMetered: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val completedAt: Long? = null,
    val galleryState: GalleryState = GalleryState.NONE,
    val galleryUri: String? = null,
    val galleryFailure: GalleryFailure? = null,
) {
    override fun toString(): String =
        "DownloadItem(id=$id, kind=$kind, state=$state, downloadedBytes=$downloadedBytes, totalBytes=$totalBytes)"
}

enum class DownloadPhase {
    CONNECTING,
    FETCHING_METADATA,
    CHECKING_FILES,
    TRANSFERRING,
    STALLED,
}

data class DownloadLive(
    val bytes: Long,
    val speedBps: Long,
    val etaSeconds: Long? = null,
    val peers: Int = 0,
    val phase: DownloadPhase = DownloadPhase.TRANSFERRING,
    val copiedBytes: Long = 0L,
)

sealed interface EnqueueResult {
    data class Added(
        val id: String,
    ) : EnqueueResult

    data class Duplicate(
        val existingId: String,
        val existingState: DownloadState,
    ) : EnqueueResult

    data class Rejected(
        val reason: DownloadFailure,
    ) : EnqueueResult
}

sealed interface DownloadEffect {
    data class Completed(
        val id: String,
    ) : DownloadEffect

    data class Failed(
        val id: String,
        val failure: DownloadFailure,
    ) : DownloadEffect

    data class GalleryFailed(
        val id: String,
        val failure: GalleryFailure,
    ) : DownloadEffect

    data object PausedBySystem : DownloadEffect
}

enum class NetworkGate {
    Proceed,
    WaitForWifi,
    WaitForNetwork,
}
