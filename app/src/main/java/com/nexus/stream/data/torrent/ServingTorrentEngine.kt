package com.nexus.stream.data.torrent

import com.nexus.stream.data.stream.LoopbackServer
import com.nexus.stream.data.stream.RangeRoute
import com.nexus.stream.domain.model.AppError
import com.nexus.stream.domain.model.StreamStats
import com.nexus.stream.domain.model.TorrentConfig
import com.nexus.stream.domain.model.TorrentFailReason
import com.nexus.stream.domain.model.TorrentRequest
import com.nexus.stream.domain.model.TorrentState
import com.nexus.stream.domain.repository.TorrentEngine
import com.nexus.stream.domain.usecase.piecesForRange
import com.nexus.stream.domain.usecase.videoMimeType
import com.nexus.stream.domain.usecase.windowPieces
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

private const val TOKEN_BYTES = 16
private const val HEX_RADIX = 16
private val EXTENSION_REGEX = Regex("^[a-z0-9]{1,5}$")
private val secureRandom = SecureRandom()
private const val STATS_POLL_INTERVAL_MS = 1000L

private class ServingStartException : Exception()

private class AbortCollectionException : CancellationException("Serving aborted")

private class MountedStream(
    val token: String,
    val source: TorrentByteSource,
    val url: String,
    val pollerJob: Job,
)

@Singleton
@Suppress("LongParameterList")
class ServingTorrentEngine internal constructor(
    private val delegate: DefaultTorrentEngine,
    private val backend: TorrentBackend,
    private val locator: TorrentFileLocator,
    private val server: LoopbackServer,
    private val hub: StreamStatsHub,
    private val tokenGenerator: () -> String,
) : TorrentEngine {
    @Inject
    constructor(
        delegate: DefaultTorrentEngine,
        backend: TorrentBackend,
        locator: TorrentFileLocator,
        server: LoopbackServer,
        hub: StreamStatsHub,
    ) : this(
        delegate = delegate,
        backend = backend,
        locator = locator,
        server = server,
        hub = hub,
        tokenGenerator = ::generateSecureToken,
    )

    override fun prepare(request: TorrentRequest): Flow<TorrentState> =
        channelFlow {
            val hash = request.infoHash.trim().lowercase()
            var currentToken: String? = null
            var currentSource: TorrentByteSource? = null
            var currentPollerJob: Job? = null

            try {
                try {
                    delegate.prepare(request).collect { state ->
                        if (state is TorrentState.Ready && state.localUrl == null) {
                            try {
                                val mounted = mountStream(this@channelFlow, hash, state.fileIndex)
                                currentToken = mounted.token
                                currentSource = mounted.source
                                currentPollerJob = mounted.pollerJob
                                send(state.copy(localUrl = mounted.url))
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: ServingStartException) {
                                send(TorrentState.Failed(AppError.Torrent(TorrentFailReason.ENGINE_UNAVAILABLE)))
                                throw AbortCollectionException()
                            } catch (e: TorrentBackendException) {
                                send(TorrentState.Failed(AppError.Torrent(e.reason)))
                                throw AbortCollectionException()
                            } catch (_: Exception) {
                                send(TorrentState.Failed(AppError.Unknown()))
                                throw AbortCollectionException()
                            }
                        } else {
                            send(state)
                        }
                    }
                } catch (_: AbortCollectionException) {
                    // Flow aborted after emitting Failed; complete cleanly
                }
            } finally {
                currentPollerJob?.cancel()
                hub.clear()
                currentToken?.let { token ->
                    server.unmount("/t/$token/")
                }
                currentSource?.close()
            }
        }

    private fun startServer(): Int =
        try {
            server.start()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            throw ServingStartException()
        }

    private suspend fun mountStream(
        scope: CoroutineScope,
        hash: String,
        fileIndex: Int,
    ): MountedStream {
        val port = startServer()

        val meta = backend.awaitMetadata(hash)
        val file =
            meta.files.firstOrNull { it.index == fileIndex }
                ?: throw TorrentBackendException(TorrentFailReason.STORAGE)
        val localFile = locator.fileOf(hash, file.path)
        val mime = videoMimeType(file.path)
        val source =
            TorrentByteSource(
                backend = backend,
                infoHash = hash,
                fileIndex = file.index,
                fileOffset = file.offsetBytes,
                sizeBytes = file.sizeBytes,
                pieceLength = meta.pieceLength,
                localFile = localFile,
                contentType = mime,
            )
        val token = tokenGenerator()
        server.mount("/t/$token/", RangeRoute(source))

        val pollerJob = startStatsPoller(scope, hash, file, meta.pieceLength, source)

        val ext = extractExtension(file.path)
        val url = "http://127.0.0.1:$port/t/$token/stream.$ext"
        return MountedStream(token, source, url, pollerJob)
    }

    private fun startStatsPoller(
        scope: CoroutineScope,
        hash: String,
        file: BackendFile,
        pieceLength: Long,
        source: TorrentByteSource,
    ): Job {
        val filePieces = piecesForRange(file.offsetBytes, pieceLength, 0L, file.sizeBytes)
        return scope.launch {
            while (isActive) {
                delay(STATS_POLL_INTERVAL_MS)
                pollStatsTick(hash, file, pieceLength, source, filePieces)
            }
        }
    }

    private suspend fun pollStatsTick(
        hash: String,
        file: BackendFile,
        pieceLength: Long,
        source: TorrentByteSource,
        filePieces: List<Int>,
    ) {
        try {
            val aheadPieces =
                windowPieces(
                    fileOffset = file.offsetBytes,
                    fileSize = file.sizeBytes,
                    pieceLength = pieceLength,
                    position = source.lastReadPosition,
                    windowBytes = TorrentConfig.STATS_AHEAD_BYTES,
                )
            val snapAhead = backend.snapshot(hash, aheadPieces)
            val snapFile = backend.snapshot(hash, filePieces)
            val aheadBytes =
                minOf(
                    snapAhead.piecesHave.toLong() * pieceLength,
                    TorrentConfig.STATS_AHEAD_BYTES,
                )
            val downloadedBytes =
                minOf(
                    snapFile.piecesHave.toLong() * pieceLength,
                    file.sizeBytes,
                )
            val stats =
                StreamStats(
                    speedBps = snapAhead.downloadSpeedBps,
                    peers = snapAhead.peers,
                    aheadBytes = aheadBytes,
                    downloadedBytes = downloadedBytes,
                    fileSizeBytes = file.sizeBytes,
                    waitMs = source.currentWaitMs(),
                )
            hub.publish(stats)
        } catch (_: TorrentBackendException) {
            // Swallowed for that tick (do not log)
        }
    }

    override suspend fun selectFile(
        infoHash: String,
        fileIdx: Int,
    ) {
        delegate.selectFile(infoHash, fileIdx)
    }

    override suspend fun stop(infoHash: String) {
        delegate.stop(infoHash)
    }
}

private fun extractExtension(path: String): String {
    val name = path.substringAfterLast('/').substringAfterLast('\\')
    val dot = name.lastIndexOf('.')
    if (dot < 0 || dot == name.length - 1) return "bin"
    val candidate = name.substring(dot + 1).lowercase()
    return if (EXTENSION_REGEX.matches(candidate)) candidate else "bin"
}

private fun generateSecureToken(): String {
    val bytes = ByteArray(TOKEN_BYTES)
    secureRandom.nextBytes(bytes)
    val sb = StringBuilder(TOKEN_BYTES * 2)
    for (b in bytes) {
        val unsigned = b.toInt() and 0xFF
        if (unsigned < HEX_RADIX) {
            sb.append('0')
        }
        sb.append(unsigned.toString(HEX_RADIX))
    }
    return sb.toString()
}
