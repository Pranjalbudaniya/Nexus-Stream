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
    val fileName: String? = null,
    val sourceSite: String? = null,
    val languages: StreamLanguages = StreamLanguages(),
    val audio: Set<AudioTag> = emptySet(),
    val channels: String? = null,
    val releaseType: ReleaseType? = null,
    val debrid: DebridMark? = null,
    val isPack: Boolean = false,
    val infoLines: List<String> = emptyList(),
    val match: StreamMatch = StreamMatch.OK,
)
