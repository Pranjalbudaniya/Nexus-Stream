package com.nexus.stream.data.download

import com.nexus.stream.data.torrent.TorrentBackendException
import com.nexus.stream.data.torrent.TorrentDownloadBackend
import com.nexus.stream.data.torrent.TorrentMetadata
import com.nexus.stream.di.IoDispatcher
import com.nexus.stream.domain.model.DownloadFailure
import com.nexus.stream.domain.model.DownloadPhase
import com.nexus.stream.domain.model.TorrentFile
import com.nexus.stream.domain.repository.DownloadFileStore
import com.nexus.stream.domain.repository.StorageInfo
import com.nexus.stream.domain.repository.TorrentDownloader
import com.nexus.stream.domain.repository.TorrentFileChoice
import com.nexus.stream.domain.repository.TorrentResult
import com.nexus.stream.domain.repository.TorrentResume
import com.nexus.stream.domain.repository.TorrentTask
import com.nexus.stream.domain.repository.TorrentUpdate
import com.nexus.stream.domain.usecase.FileChoice
import com.nexus.stream.domain.usecase.POLL_INTERVAL_MS
import com.nexus.stream.domain.usecase.chooseTorrentFile
import com.nexus.stream.domain.usecase.completedTorrentPath
import com.nexus.stream.domain.usecase.hasEnoughStorage
import com.nexus.stream.domain.usecase.metadataTimedOut
import com.nexus.stream.domain.usecase.phaseOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

@Singleton
class TorrentDownloaderImpl(
    private val backend: TorrentDownloadBackend,
    private val storageInfo: StorageInfo,
    private val fileStore: DownloadFileStore,
    private val ioDispatcher: CoroutineDispatcher,
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val delayFn: suspend (Long) -> Unit = { delay(it) },
) : TorrentDownloader {
    @Inject
    constructor(
        backend: TorrentDownloadBackend,
        storageInfo: StorageInfo,
        fileStore: DownloadFileStore,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ) : this(
        backend = backend,
        storageInfo = storageInfo,
        fileStore = fileStore,
        ioDispatcher = ioDispatcher,
        nowMs = System::currentTimeMillis,
        delayFn = { delay(it) },
    )

    override suspend fun download(
        task: TorrentTask,
        onUpdate: (TorrentUpdate) -> Unit,
    ): TorrentResult =
        withContext(ioDispatcher) {
            if (backend.isStreamingActive(task.infoHash)) {
                return@withContext TorrentResult.Failed(DownloadFailure.TORRENT_IN_USE)
            }

            val magnetUri = buildMagnetUri(task.infoHash, task.trackers)
            try {
                backend.addForDownload(task.infoHash, magnetUri, task.saveDir)
            } catch (_: TorrentBackendException) {
                backend.removeKeepingFiles(task.infoHash)
                return@withContext TorrentResult.Failed(DownloadFailure.TORRENT_ENGINE)
            } catch (e: CancellationException) {
                backend.removeKeepingFiles(task.infoHash)
                throw e
            }

            try {
                executeDownload(task, onUpdate)
            } catch (_: TorrentBackendException) {
                backend.removeKeepingFiles(task.infoHash)
                TorrentResult.Failed(DownloadFailure.TORRENT_ENGINE)
            } catch (e: CancellationException) {
                backend.removeKeepingFiles(task.infoHash)
                throw e
            }
        }

    private suspend fun CoroutineScope.executeDownload(
        task: TorrentTask,
        onUpdate: (TorrentUpdate) -> Unit,
    ): TorrentResult =
        when (val setup = setupTransfer(task)) {
            is TransferSetup.Failure -> {
                setup.result
            }

            is TransferSetup.Ready -> {
                backend.selectFile(task.infoHash, setup.fileIndex)
                pollProgress(task, setup.fileIndex, setup.fileName, setup.path, onUpdate)
            }
        }

    private suspend fun CoroutineScope.setupTransfer(task: TorrentTask): TransferSetup {
        val meta =
            fetchMetadata(task)
                ?: return TransferSetup.Failure(TorrentResult.Failed(DownloadFailure.TORRENT_NO_PEERS))

        return resolveSetup(task, meta)
    }

    private suspend fun resolveSetup(
        task: TorrentTask,
        meta: TorrentMetadata,
    ): TransferSetup {
        val domainFiles = meta.files.map { TorrentFile(it.index, it.path, it.sizeBytes) }
        val choice =
            chooseTorrentFile(
                files = domainFiles,
                requestedIndex = task.knownFileIdx ?: task.requestedFileIdx,
                season = task.season,
                episode = task.episode,
            )

        val outcome = evaluateFileChoice(task.infoHash, choice)
        return when (outcome) {
            is ChoiceOutcome.Failure -> {
                outcome.failure
            }

            is ChoiceOutcome.Success -> {
                val setup = verifyStorageAndPrepare(task, meta, outcome.chosenIndex)
                if (setup is TransferSetup.Failure) {
                    backend.removeKeepingFiles(task.infoHash)
                }
                setup
            }
        }
    }

    private suspend fun evaluateFileChoice(
        infoHash: String,
        choice: FileChoice,
    ): ChoiceOutcome =
        when (choice) {
            is FileChoice.NoVideo -> {
                backend.removeKeepingFiles(infoHash)
                ChoiceOutcome.Failure(
                    TransferSetup.Failure(TorrentResult.Failed(DownloadFailure.TORRENT_NO_VIDEO_FILE)),
                )
            }

            is FileChoice.NeedsUser -> {
                backend.removeKeepingFiles(infoHash)
                val candidateChoices =
                    choice.candidates.map {
                        TorrentFileChoice(
                            index = it.index,
                            name = it.path.substringAfterLast('/').substringAfterLast('\\'),
                            sizeBytes = it.sizeBytes,
                        )
                    }
                ChoiceOutcome.Failure(TransferSetup.Failure(TorrentResult.NeedsUserPick(candidateChoices)))
            }

            is FileChoice.Chosen -> {
                ChoiceOutcome.Success(choice.index)
            }
        }

    private fun verifyStorageAndPrepare(
        task: TorrentTask,
        meta: TorrentMetadata,
        chosenFileIndex: Int,
    ): TransferSetup {
        val chosenFile = meta.files.firstOrNull { it.index == chosenFileIndex }
        val chosenFileName = chosenFile?.path?.substringAfterLast('/')?.substringAfterLast('\\') ?: "video.mkv"
        val totalBytes = chosenFile?.sizeBytes ?: 0L

        val availableBytes = storageInfo.availableBytes(task.saveDir)
        return if (!hasEnoughStorage(availableBytes, totalBytes)) {
            TransferSetup.Failure(TorrentResult.Failed(DownloadFailure.INSUFFICIENT_STORAGE))
        } else {
            TransferSetup.Ready(
                fileIndex = chosenFileIndex,
                fileName = chosenFileName,
                path = chosenFile?.path,
            )
        }
    }

    private suspend fun CoroutineScope.fetchMetadata(task: TorrentTask): TorrentMetadata? {
        val metaDeferred = async(ioDispatcher) { backend.awaitMetadata(task.infoHash) }
        val startedAt = nowMs()
        while (!metaDeferred.isCompleted) {
            coroutineContext.ensureActive()
            if (metadataTimedOut(startedAt, nowMs(), false)) {
                metaDeferred.cancel()
                backend.removeKeepingFiles(task.infoHash)
                return null
            }
            delayFn(POLL_INTERVAL_MS)
        }
        return metaDeferred.await()
    }

    private suspend fun pollProgress(
        task: TorrentTask,
        chosenFileIndex: Int,
        chosenFileName: String,
        backendPath: String?,
        onUpdate: (TorrentUpdate) -> Unit,
    ): TorrentResult {
        var lastProgressAt = nowMs()
        var lastBytesDone = 0L

        while (true) {
            coroutineContext.ensureActive()

            val snapshot = backend.downloadSnapshot(task.infoHash, chosenFileIndex)
            val now = nowMs()

            if (snapshot.fileBytesDone > lastBytesDone) {
                lastBytesDone = snapshot.fileBytesDone
                lastProgressAt = now
            }

            val phase =
                phaseOf(
                    state = snapshot.state,
                    hasMetadata = true,
                    peers = snapshot.peers,
                    bytesDone = snapshot.fileBytesDone,
                    lastProgressAtMs = lastProgressAt,
                    nowMs = now,
                )

            onUpdate(
                TorrentUpdate(
                    phase = phase,
                    fileBytesDone = snapshot.fileBytesDone,
                    fileBytesTotal = snapshot.fileBytesTotal,
                    speedBps = snapshot.speedBps,
                    peers = snapshot.peers,
                    chosenFileIdx = chosenFileIndex,
                    fileName = chosenFileName,
                ),
            )

            if (snapshot.fileBytesDone >= snapshot.fileBytesTotal && snapshot.fileBytesTotal > 0L) {
                backend.removeKeepingFiles(task.infoHash)
                val completedFile =
                    completedTorrentPath(task.saveDir, backendPath.orEmpty())
                        ?: File(task.saveDir, chosenFileName)
                return TorrentResult.Completed(file = completedFile, totalBytes = snapshot.fileBytesTotal)
            }

            if (backend.isPaused(task.infoHash)) {
                backend.removeKeepingFiles(task.infoHash)
                return TorrentResult.Paused(
                    TorrentResume(
                        fileIndex = chosenFileIndex,
                        bytesDone = snapshot.fileBytesDone,
                    ),
                )
            }

            delayFn(POLL_INTERVAL_MS)
        }
    }

    private fun buildMagnetUri(
        hash: String,
        trackers: List<String>,
    ): String {
        val params =
            trackers
                .map { it.trim() }
                .filter { tracker ->
                    tracker.startsWith("http://", ignoreCase = true) ||
                        tracker.startsWith("https://", ignoreCase = true) ||
                        tracker.startsWith("udp://", ignoreCase = true)
                }.distinct()
                .joinToString("") { "&tr=" + URLEncoder.encode(it, "UTF-8") }
        return "magnet:?xt=urn:btih:$hash$params"
    }
}

private sealed interface TransferSetup {
    data class Failure(
        val result: TorrentResult,
    ) : TransferSetup

    data class Ready(
        val fileIndex: Int,
        val fileName: String,
        val path: String?,
    ) : TransferSetup
}

private sealed interface ChoiceOutcome {
    data class Failure(
        val failure: TransferSetup.Failure,
    ) : ChoiceOutcome

    data class Success(
        val chosenIndex: Int,
    ) : ChoiceOutcome
}
