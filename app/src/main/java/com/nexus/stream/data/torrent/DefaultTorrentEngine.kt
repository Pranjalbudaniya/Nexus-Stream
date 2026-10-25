package com.nexus.stream.data.torrent

import com.nexus.stream.domain.model.AppError
import com.nexus.stream.domain.model.TorrentConfig
import com.nexus.stream.domain.model.TorrentFailReason
import com.nexus.stream.domain.model.TorrentFile
import com.nexus.stream.domain.model.TorrentRequest
import com.nexus.stream.domain.model.TorrentState
import com.nexus.stream.domain.repository.TorrentEngine
import com.nexus.stream.domain.usecase.bufferingPercent
import com.nexus.stream.domain.usecase.isVideoFile
import com.nexus.stream.domain.usecase.pickFile
import com.nexus.stream.domain.usecase.targetPieces
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

private val HASH_REGEX = Regex("^[0-9a-f]{40}$")

internal fun buildMagnet(
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

@Singleton
open class DefaultTorrentEngine
    @Inject
    constructor(
        private val backend: TorrentBackend,
        private val coordinator: CacheRetentionCoordinator?,
    ) : TorrentEngine {
        constructor(backend: TorrentBackend) : this(backend, null)

        private val sessions = ConcurrentHashMap<String, SessionRecord>()

        private class SessionRecord(
            val stopped: CompletableDeferred<Unit> = CompletableDeferred(),
            val pick: CompletableDeferred<Int> = CompletableDeferred(),
            @Volatile var offered: Set<Int> = emptySet(),
            val finished: CompletableDeferred<Unit> = CompletableDeferred(),
        )

        private sealed interface MetaResult {
            data class Success(
                val metadata: TorrentMetadata,
            ) : MetaResult

            data object Timeout : MetaResult

            data object Stopped : MetaResult
        }

        open override fun prepare(request: TorrentRequest): Flow<TorrentState> =
            channelFlow {
                val hash = request.infoHash.trim().lowercase()
                if (!HASH_REGEX.matches(hash)) {
                    send(TorrentState.Failed(AppError.Torrent(TorrentFailReason.INVALID_HASH)))
                    return@channelFlow
                }

                stopInternal(hash)
                val session = SessionRecord()
                sessions[hash] = session

                var readyFileIndex: Int? = null
                var readyFileSize: Long? = null

                try {
                    send(TorrentState.FetchingMetadata)
                    backend.add(hash, buildMagnet(hash, request.trackers))

                    val meta = awaitMetadataOrNull(hash, session) ?: return@channelFlow
                    val idx = resolveFileIndex(meta.files, request, session) ?: return@channelFlow

                    val file = meta.files[idx]
                    coordinator?.recordReusedOnAdd(hash, idx)
                    val target =
                        targetPieces(
                            fileOffset = file.offsetBytes,
                            fileSize = file.sizeBytes,
                            pieceLength = meta.pieceLength,
                            headBytes = TorrentConfig.HEAD_BYTES,
                            tailBytes = TorrentConfig.TAIL_BYTES,
                        )
                    backend.prioritize(hash, idx, target.all)

                    pollBuffering(hash, target.all, session)
                    if (session.stopped.isCompleted) return@channelFlow

                    readyFileIndex = idx
                    readyFileSize = file.sizeBytes

                    if (request.cacheKey != null) {
                        coordinator?.onReady(request.cacheKey, hash, idx, file.sizeBytes)
                    }

                    val fileName = file.path.substringAfterLast('/').substringAfterLast('\\')
                    send(TorrentState.Ready(idx, fileName, file.sizeBytes))
                    session.stopped.await()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: TorrentBackendException) {
                    send(TorrentState.Failed(AppError.Torrent(e.reason)))
                } catch (_: Exception) {
                    send(TorrentState.Failed(AppError.Unknown()))
                } finally {
                    cleanupSession(request, hash, readyFileIndex, readyFileSize, session)
                }
            }

        private suspend fun cleanupSession(
            request: TorrentRequest,
            hash: String,
            readyFileIndex: Int?,
            readyFileSize: Long?,
            session: SessionRecord,
        ) {
            withContext(NonCancellable) {
                runCatching {
                    val keep =
                        if (request.cacheKey != null && readyFileIndex != null && readyFileSize != null) {
                            coordinator?.onStop(
                                key = request.cacheKey,
                                hash = hash,
                                fileIdx = readyFileIndex,
                                fileSize = readyFileSize,
                                completed = false,
                            )
                        } else {
                            null
                        }
                    backend.remove(hash, keep = keep)
                }
            }
            sessions.remove(hash, session)
            session.finished.complete(Unit)
        }

        private suspend fun ProducerScope<TorrentState>.awaitMetadataOrNull(
            hash: String,
            session: SessionRecord,
        ): TorrentMetadata? {
            val metaResult =
                coroutineScope {
                    val fetch =
                        async {
                            withTimeoutOrNull(TorrentConfig.METADATA_TIMEOUT_MS) {
                                backend.awaitMetadata(hash)
                            }
                        }
                    select<MetaResult> {
                        fetch.onAwait { meta ->
                            if (meta != null) MetaResult.Success(meta) else MetaResult.Timeout
                        }
                        session.stopped.onAwait {
                            fetch.cancel()
                            MetaResult.Stopped
                        }
                    }
                }
            return when (metaResult) {
                is MetaResult.Stopped -> {
                    null
                }

                is MetaResult.Timeout -> {
                    send(TorrentState.Failed(AppError.Torrent(TorrentFailReason.METADATA_TIMEOUT)))
                    null
                }

                is MetaResult.Success -> {
                    metaResult.metadata
                }
            }
        }

        private suspend fun ProducerScope<TorrentState>.resolveFileIndex(
            backendFiles: List<BackendFile>,
            request: TorrentRequest,
            session: SessionRecord,
        ): Int? {
            val files = backendFiles.map { TorrentFile(it.index, it.path, it.sizeBytes) }
            val validReqIdx = request.fileIdx?.takeIf { it in files.indices }
            val autoIdx = validReqIdx ?: pickFile(files, request.episodeHint)
            if (autoIdx != null) return autoIdx

            val videos = files.filter { isVideoFile(it.path) }
            return if (videos.isEmpty()) {
                send(TorrentState.Failed(AppError.Torrent(TorrentFailReason.NO_VIDEO_FILE)))
                null
            } else {
                val offered = videos.sortedByDescending { it.sizeBytes }
                session.offered = offered.map { it.index }.toSet()
                send(TorrentState.NeedsFilePick(offered))

                val pickedIdx =
                    select<Int?> {
                        session.pick.onAwait { it }
                        session.stopped.onAwait { null }
                    }
                if (session.stopped.isCompleted) null else pickedIdx
            }
        }

        private suspend fun ProducerScope<TorrentState>.pollBuffering(
            hash: String,
            targetPieces: List<Int>,
            session: SessionRecord,
        ) {
            var lastState: TorrentState.Buffering? = null
            while (true) {
                if (session.stopped.isCompleted) return
                val snap = backend.snapshot(hash, targetPieces)
                if (snap.piecesHave >= targetPieces.size) break

                val state =
                    TorrentState.Buffering(
                        percent = bufferingPercent(snap.piecesHave, targetPieces.size),
                        speedBps = snap.downloadSpeedBps,
                        peers = snap.peers,
                    )
                if (state != lastState) {
                    send(state)
                    lastState = state
                }
                delay(TorrentConfig.POLL_INTERVAL_MS)
            }
        }

        open override suspend fun selectFile(
            infoHash: String,
            fileIdx: Int,
        ) {
            val hash = infoHash.trim().lowercase()
            val session = sessions[hash] ?: return
            if (fileIdx in session.offered) {
                session.pick.complete(fileIdx)
            }
        }

        open override suspend fun stop(infoHash: String) {
            val hash = infoHash.trim().lowercase()
            sessions[hash]?.stopped?.complete(Unit)
        }

        private suspend fun stopInternal(hash: String) {
            val existing = sessions[hash] ?: return
            existing.stopped.complete(Unit)
            existing.finished.await()
        }
    }
