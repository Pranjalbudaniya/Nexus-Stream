package com.nexus.stream.data.torrent

import com.nexus.stream.domain.usecase.TorrentSessionState
import java.io.File

interface TorrentDownloadBackend {
    suspend fun addForDownload(
        infoHash: String,
        magnetUri: String,
        saveDir: File,
    )

    suspend fun awaitMetadata(infoHash: String): TorrentMetadata

    suspend fun selectFile(
        infoHash: String,
        fileIndex: Int,
    )

    suspend fun downloadSnapshot(
        infoHash: String,
        fileIndex: Int,
    ): DownloadSnapshot

    suspend fun removeKeepingFiles(infoHash: String)

    suspend fun removeAndDelete(infoHash: String)

    fun isStreamingActive(infoHash: String): Boolean = false

    fun isPaused(infoHash: String): Boolean = false
}

data class DownloadSnapshot(
    val fileBytesDone: Long,
    val fileBytesTotal: Long,
    val speedBps: Long,
    val peers: Int,
    val state: TorrentSessionState,
)
