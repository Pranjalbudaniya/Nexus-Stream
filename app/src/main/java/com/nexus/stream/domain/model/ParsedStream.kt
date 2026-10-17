package com.nexus.stream.domain.model

enum class HdrKind {
    HDR,
    HDR10,
    HDR10_PLUS,
    DOLBY_VISION,
}

enum class Codec {
    HEVC,
    AVC,
    AV1,
}

sealed interface StreamKind {
    data object Direct : StreamKind

    data object Torrent : StreamKind

    data object ExternalLink : StreamKind
}

data class ParsedStream(
    val key: String,
    val stream: Stream,
    val addonNames: List<String>,
    val addonKeys: List<String> = emptyList(),
    val resolution: Int?,
    val hdr: Set<HdrKind>,
    val codec: Codec?,
    val sizeBytes: Long?,
    val seeders: Int?,
    val kind: StreamKind,
    val needsHeaders: Boolean,
    val displayName: String,
)
