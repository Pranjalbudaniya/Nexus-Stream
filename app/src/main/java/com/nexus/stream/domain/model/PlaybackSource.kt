package com.nexus.stream.domain.model

sealed interface PlaybackSource {
    data class Direct(
        val url: String,
    ) : PlaybackSource

    data class DirectWithHeaders(
        val url: String,
        val headers: Map<String, String>,
    ) : PlaybackSource

    data class Torrent(
        val request: TorrentRequest,
    ) : PlaybackSource

    data class External(
        val url: String,
    ) : PlaybackSource

    data class Unsupported(
        val reason: UnsupportedReason,
    ) : PlaybackSource

    data class Local(
        val filePath: String,
    ) : PlaybackSource {
        override fun toString(): String = "PlaybackSource.Local"
    }
}

enum class UnsupportedReason {
    NO_SOURCE,
    BAD_URL,
    BAD_SCHEME,
    INVALID_HASH,
}
