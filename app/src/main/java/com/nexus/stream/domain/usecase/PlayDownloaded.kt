package com.nexus.stream.domain.usecase

import com.nexus.stream.domain.model.DownloadItem
import com.nexus.stream.domain.model.DownloadState
import com.nexus.stream.domain.model.PlaybackSource
import com.nexus.stream.domain.repository.DownloadManager
import com.nexus.stream.presentation.feature.player.PlaybackSelection
import java.io.File
import javax.inject.Inject

sealed interface PlayResult {
    data class Ready(
        val selection: PlaybackSelection,
    ) : PlayResult

    data object FileMissing : PlayResult

    data object NotReady : PlayResult
}

class PlayDownloaded(
    private val fileProvider: (String) -> File? = { null },
) {
    @Inject
    constructor(downloadManager: DownloadManager) : this({ id -> downloadManager.fileOf(id) })

    operator fun invoke(
        item: DownloadItem,
        file: File? = null,
    ): PlayResult {
        val resolvedFile = file ?: fileProvider(item.id) ?: item.filePath?.let { File(it) }
        return when {
            item.state != DownloadState.COMPLETED -> {
                PlayResult.NotReady
            }

            resolvedFile == null || !resolvedFile.exists() -> {
                PlayResult.FileMissing
            }

            else -> {
                PlayResult.Ready(
                    PlaybackSelection(
                        title = item.title,
                        source = PlaybackSource.Local(resolvedFile.absolutePath),
                        type = item.type,
                        metaId = item.metaId,
                        videoId = item.videoId,
                        season = item.season,
                        episode = item.episode,
                        posterUrl = item.posterUrl,
                        metaName = item.metaName,
                        sourceFingerprint = null,
                        request = null,
                        subtitleHints = null,
                    ),
                )
            }
        }
    }
}
