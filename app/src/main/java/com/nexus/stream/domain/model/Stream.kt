package com.nexus.stream.domain.model

sealed interface StreamSource {
    data class Url(
        val url: String,
        val requestHeaders: Map<String, String>,
    ) : StreamSource

    data class Torrent(
        val infoHash: String,
        val fileIdx: Int?,
        val trackers: List<String>,
    ) : StreamSource

    data class External(
        val url: String,
    ) : StreamSource

    data class YouTube(
        val ytId: String,
    ) : StreamSource

    data class Unsupported(
        val kind: String,
    ) : StreamSource
}

data class Stream(
    val source: StreamSource,
    val name: String?,
    val description: String?,
    val subtitles: List<Subtitle>,
    val bingeGroup: String?,
    val notWebReady: Boolean,
    val videoSize: Long?,
    val filename: String?,
    val videoHash: String?,
)

data class Subtitle(
    val id: String,
    val url: String,
    val lang: String,
    val label: String?,
)
