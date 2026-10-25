package com.nexus.stream.data.torrent

import com.nexus.stream.domain.model.TorrentFailReason

interface TorrentBackend {
    suspend fun add(
        infoHash: String,
        magnetUri: String,
    )

    suspend fun awaitMetadata(infoHash: String): TorrentMetadata

    /**
     * Sets exactly these deadlines and clears earlier ones.
     */
    suspend fun prioritize(
        infoHash: String,
        fileIndex: Int,
        pieces: List<Int>,
    )

    suspend fun snapshot(
        infoHash: String,
        pieces: List<Int>,
    ): TorrentSnapshot

    suspend fun remove(
        infoHash: String,
        keep: Boolean? = null,
    )
}

data class BackendFile(
    val index: Int,
    val path: String,
    val sizeBytes: Long,
    val offsetBytes: Long,
)

data class TorrentMetadata(
    val files: List<BackendFile>,
    val pieceLength: Long,
)

data class TorrentSnapshot(
    val piecesHave: Int,
    val downloadSpeedBps: Long,
    val peers: Int,
)

class TorrentBackendException(
    val reason: TorrentFailReason,
) : Exception()
