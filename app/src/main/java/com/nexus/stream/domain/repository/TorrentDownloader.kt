package com.nexus.stream.domain.repository

import com.nexus.stream.domain.model.DownloadFailure
import com.nexus.stream.domain.model.DownloadPhase
import java.io.File

interface TorrentDownloader {
    suspend fun download(
        task: TorrentTask,
        onUpdate: (TorrentUpdate) -> Unit,
    ): TorrentResult
}

data class TorrentTask(
    val infoHash: String,
    val trackers: List<String> = emptyList(),
    val requestedFileIdx: Int? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val saveDir: File,
    val knownFileIdx: Int? = null,
    val expectedBytes: Long? = null,
)

data class TorrentUpdate(
    val phase: DownloadPhase,
    val fileBytesDone: Long,
    val fileBytesTotal: Long,
    val speedBps: Long,
    val peers: Int,
    val chosenFileIdx: Int? = null,
    val fileName: String? = null,
)

sealed interface TorrentResult {
    data class Completed(
        val file: File,
        val totalBytes: Long,
    ) : TorrentResult

    data class NeedsUserPick(
        val files: List<TorrentFileChoice>,
    ) : TorrentResult

    data class Failed(
        val failure: DownloadFailure,
    ) : TorrentResult

    data class Paused(
        val resume: TorrentResume,
    ) : TorrentResult
}

data class TorrentResume(
    val fileIndex: Int,
    val bytesDone: Long,
)

data class TorrentFileChoice(
    val index: Int,
    val name: String,
    val sizeBytes: Long,
)
