package com.nexus.stream.domain.model

data class EpisodeHint(
    val season: Int,
    val episode: Int,
)

data class TorrentRequest(
    val infoHash: String,
    val trackers: List<String> = emptyList(),
    val fileIdx: Int? = null,
    val episodeHint: EpisodeHint? = null,
    val allowMetered: Boolean = false,
)

data class TorrentFile(
    val index: Int,
    val path: String,
    val sizeBytes: Long,
)

enum class TorrentFailReason {
    INVALID_HASH,
    METADATA_TIMEOUT,
    NO_VIDEO_FILE,
    STORAGE,
    ENGINE_UNAVAILABLE,
}

sealed interface TorrentState {
    data object FetchingMetadata : TorrentState

    data class NeedsFilePick(
        val files: List<TorrentFile>,
    ) : TorrentState

    data class Buffering(
        val percent: Int,
        val speedBps: Long,
        val peers: Int,
    ) : TorrentState

    data class Ready(
        val fileIndex: Int,
        val fileName: String,
        val fileSize: Long,
        val localUrl: String? = null,
    ) : TorrentState

    data class Failed(
        val error: AppError,
    ) : TorrentState
}

object TorrentConfig {
    const val HEAD_BYTES = 8L * 1024 * 1024
    const val TAIL_BYTES = 2L * 1024 * 1024
    const val METADATA_TIMEOUT_MS = 60_000L
    const val POLL_INTERVAL_MS = 500L
    const val PIECE_DEADLINE_STEP_MS = 100L
    const val STREAM_WINDOW_BYTES = 16L * 1024 * 1024
    const val STREAM_POLL_MS = 100L
    const val STREAM_STALL_TIMEOUT_MS = 30_000L
    const val STATS_AHEAD_BYTES = 64L * 1024 * 1024
}
