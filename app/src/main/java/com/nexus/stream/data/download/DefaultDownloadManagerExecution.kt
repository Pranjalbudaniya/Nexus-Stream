package com.nexus.stream.data.download

import com.nexus.stream.data.torrent.NetworkStatus
import com.nexus.stream.domain.model.DownloadEffect
import com.nexus.stream.domain.model.DownloadFailure
import com.nexus.stream.domain.model.DownloadItem
import com.nexus.stream.domain.model.DownloadLive
import com.nexus.stream.domain.model.DownloadSource
import com.nexus.stream.domain.model.DownloadState
import com.nexus.stream.domain.model.PauseReason
import com.nexus.stream.domain.repository.HttpResult
import com.nexus.stream.domain.repository.HttpTask
import com.nexus.stream.domain.repository.TorrentResult
import com.nexus.stream.domain.repository.TorrentTask
import com.nexus.stream.domain.repository.TorrentUpdate
import com.nexus.stream.domain.usecase.SpeedMeter
import com.nexus.stream.domain.usecase.downloadFileExtension
import com.nexus.stream.domain.usecase.etaSeconds
import com.nexus.stream.domain.usecase.hasEnoughStorage
import com.nexus.stream.domain.usecase.torrentSaveDir
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

internal suspend fun DefaultDownloadManager.executeDownload(item: DownloadItem) {
    when (val source = item.source) {
        is DownloadSource.Http -> executeHttpDownload(item, source)
        is DownloadSource.Torrent -> executeTorrentDownload(item, source)
    }
}

private suspend fun DefaultDownloadManager.executeHttpDownload(
    item: DownloadItem,
    httpSource: DownloadSource.Http,
) {
    val available = platform.storageInfo.availableBytes(platform.fileStore.root)
    val remaining = (item.totalBytes - item.downloadedBytes).coerceAtLeast(0L)
    if (!hasEnoughStorage(available, remaining)) {
        repository.updateState(
            item.id,
            DownloadState.FAILED,
            clock.now(),
            failure = DownloadFailure.INSUFFICIENT_STORAGE,
        )
        emitEffect(DownloadEffect.Failed(item.id, DownloadFailure.INSUFFICIENT_STORAGE))
        return
    }

    repository.updateState(item.id, DownloadState.DOWNLOADING, clock.now())
    val partFile = platform.fileStore.partFile(item.id)
    val offset = if (partFile.exists()) partFile.length() else 0L

    val session =
        if (httpSource.headers.isNotEmpty()) {
            streamProxy.open(
                httpSource.url,
                httpSource.headers,
            )
        } else {
            null
        }
    val targetUrl = session?.url ?: httpSource.url

    try {
        val task =
            HttpTask(
                url = targetUrl,
                headers = httpSource.headers,
                partFile = partFile,
                offset = offset,
                etag = item.etag,
                lastModified = item.lastModified,
                supportsResumeKnown = item.supportsResume,
            )
        val result =
            platform.downloader.download(task) { bytes, total ->
                onProgressUpdate(item.id, bytes, total)
            }
        handleHttpResult(item, result, httpSource.url)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        repository.updateState(item.id, DownloadState.FAILED, clock.now(), failure = DownloadFailure.UNKNOWN)
    } finally {
        session?.close()
    }
}

private suspend fun DefaultDownloadManager.executeTorrentDownload(
    item: DownloadItem,
    source: DownloadSource.Torrent,
) {
    val saveDir = torrentSaveDir(platform.fileStore.root, item.id)
    val task =
        TorrentTask(
            infoHash = source.infoHash,
            trackers = source.trackers,
            requestedFileIdx = source.fileIdx,
            season = item.season,
            episode = item.episode,
            saveDir = saveDir,
            knownFileIdx = source.fileIdx,
            expectedBytes = if (item.totalBytes > 0L) item.totalBytes else null,
        )

    repository.updateState(item.id, DownloadState.DOWNLOADING, clock.now())

    try {
        val result =
            torrentDownloader.download(task) { update ->
                onTorrentProgressUpdate(item.id, update)
            }
        handleTorrentResult(item, result, saveDir)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        repository.updateState(item.id, DownloadState.FAILED, clock.now(), failure = DownloadFailure.UNKNOWN)
        emitEffect(DownloadEffect.Failed(item.id, DownloadFailure.UNKNOWN))
    }
}

private fun DefaultDownloadManager.onTorrentProgressUpdate(
    id: String,
    update: TorrentUpdate,
) {
    val now = clock.now()
    val eta = etaSeconds((update.fileBytesTotal - update.fileBytesDone).coerceAtLeast(0L), update.speedBps)
    updateLive { current ->
        current + (
            id to
                DownloadLive(
                    bytes = update.fileBytesDone,
                    speedBps = update.speedBps,
                    etaSeconds = eta,
                    peers = update.peers,
                    phase = update.phase,
                )
        )
    }

    val lastTime = lastPersistTimes[id] ?: 0L
    val lastBytes = lastPersistBytes[id] ?: 0L
    val timeDue = now - lastTime >= PROGRESS_PERSIST_INTERVAL_MS
    val total = update.fileBytesTotal
    val pctDue =
        total <= 0L || ((update.fileBytesDone - lastBytes) * PERCENT_MULTIPLIER / total) >= PROGRESS_PERSIST_PERCENT

    if (timeDue || pctDue) {
        lastPersistTimes[id] = now
        lastPersistBytes[id] = update.fileBytesDone
        scope.launch(ioDispatcher) {
            repository.updateProgress(id, update.fileBytesDone, total, DownloadState.DOWNLOADING, now)
        }
    }
}

private suspend fun DefaultDownloadManager.handleTorrentResult(
    item: DownloadItem,
    result: TorrentResult,
    saveDir: File,
) {
    when (result) {
        is TorrentResult.Completed -> {
            val now = clock.now()
            repository.updateCompleted(
                id = item.id,
                filePath = result.file.absolutePath,
                totalBytes = result.totalBytes,
                completedAt = now,
                updatedAt = now,
            )
            emitEffect(DownloadEffect.Completed(item.id))
            if (prefsRepository.saveToGallery.first()) {
                scope.launch(ioDispatcher) {
                    exportToGallery(item.id)
                }
            }
        }

        is TorrentResult.NeedsUserPick -> {
            fileChoices[item.id] = result.files
            repository.updateState(item.id, DownloadState.NEEDS_FILE_PICK, clock.now())
        }

        is TorrentResult.Paused -> {
            repository.updateState(
                id = item.id,
                state = DownloadState.PAUSED,
                updatedAt = clock.now(),
                pauseReason = PauseReason.USER,
            )
        }

        is TorrentResult.Failed -> {
            if (result.failure == DownloadFailure.TORRENT_NO_VIDEO_FILE) {
                saveDir.deleteRecursively()
            }
            repository.updateState(item.id, DownloadState.FAILED, clock.now(), failure = result.failure)
            emitEffect(DownloadEffect.Failed(item.id, result.failure))
        }
    }
}

private suspend fun DefaultDownloadManager.handleHttpResult(
    item: DownloadItem,
    result: HttpResult,
    url: String,
) {
    when (result) {
        is HttpResult.Completed -> {
            handleDownloadCompleted(item, result, url)
        }

        is HttpResult.Failed -> {
            handleDownloadFailed(item, result)
        }

        is HttpResult.Cancelled -> {}
    }
}

private suspend fun DefaultDownloadManager.handleDownloadCompleted(
    item: DownloadItem,
    result: HttpResult.Completed,
    url: String,
) {
    val ext = downloadFileExtension(item.fileNameHint, url)
    val finished = platform.fileStore.complete(item.id, ext)
    val now = clock.now()
    repository.updateCompleted(item.id, finished.absolutePath, result.total, now, now)
    emitEffect(DownloadEffect.Completed(item.id))
    if (prefsRepository.saveToGallery.first()) {
        scope.launch(ioDispatcher) {
            exportToGallery(item.id)
        }
    }
}

private suspend fun DefaultDownloadManager.handleDownloadFailed(
    item: DownloadItem,
    result: HttpResult.Failed,
) {
    val net = platform.network.status.value
    if (net == NetworkStatus.NONE && result.failure == DownloadFailure.NETWORK) {
        repository.updateState(item.id, DownloadState.WAITING_FOR_NETWORK, clock.now())
    } else {
        if (result.failure == DownloadFailure.NOT_A_VIDEO || result.failure == DownloadFailure.UNSUPPORTED_SOURCE) {
            platform.fileStore.deletePart(item.id)
        }
        repository.updateState(item.id, DownloadState.FAILED, clock.now(), failure = result.failure)
        emitEffect(DownloadEffect.Failed(item.id, result.failure))
    }
}

private fun DefaultDownloadManager.onProgressUpdate(
    id: String,
    bytes: Long,
    total: Long,
) {
    val now = clock.now()
    val meter = speedMeters.getOrPut(id) { SpeedMeter() }
    val prevTime = lastProgressTimes[id] ?: now
    val prevBytes = lastProgressBytes[id] ?: bytes
    val dt = (now - prevTime).coerceAtLeast(1L)
    val sampleBps = ((bytes - prevBytes) * 1000L / dt).coerceAtLeast(0L)
    val speed = meter.addSample(sampleBps)
    lastProgressTimes[id] = now
    lastProgressBytes[id] = bytes

    val eta = etaSeconds((total - bytes).coerceAtLeast(0L), speed)
    updateLive { current -> current + (id to DownloadLive(bytes, speed, eta)) }

    val lastTime = lastPersistTimes[id] ?: 0L
    val lastBytes = lastPersistBytes[id] ?: 0L
    val timeDue = now - lastTime >= PROGRESS_PERSIST_INTERVAL_MS
    val pctDue = total <= 0L || ((bytes - lastBytes) * PERCENT_MULTIPLIER / total) >= PROGRESS_PERSIST_PERCENT

    if (timeDue || pctDue) {
        lastPersistTimes[id] = now
        lastPersistBytes[id] = bytes
        scope.launch(ioDispatcher) {
            repository.updateProgress(id, bytes, total, DownloadState.DOWNLOADING, now)
        }
    }
}
