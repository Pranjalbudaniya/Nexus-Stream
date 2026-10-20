package com.nexus.stream.data.torrent

import com.frostwire.jlibtorrent.AlertListener
import com.frostwire.jlibtorrent.Priority
import com.frostwire.jlibtorrent.SessionHandle
import com.frostwire.jlibtorrent.SessionManager
import com.frostwire.jlibtorrent.Sha1Hash
import com.frostwire.jlibtorrent.TorrentFlags
import com.frostwire.jlibtorrent.TorrentHandle
import com.frostwire.jlibtorrent.TorrentInfo
import com.frostwire.jlibtorrent.alerts.Alert
import com.frostwire.jlibtorrent.alerts.AlertType
import com.frostwire.jlibtorrent.alerts.FileErrorAlert
import com.frostwire.jlibtorrent.alerts.MetadataReceivedAlert
import com.frostwire.jlibtorrent.alerts.TorrentAlert
import com.frostwire.jlibtorrent.alerts.TorrentErrorAlert
import com.nexus.stream.di.IoDispatcher
import com.nexus.stream.domain.model.TorrentConfig
import com.nexus.stream.domain.model.TorrentFailReason
import com.nexus.stream.domain.usecase.deadlineMsFor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JLibTorrentBackend
    @Inject
    constructor(
        private val cache: TorrentCacheStore,
        @IoDispatcher private val io: CoroutineDispatcher,
    ) : TorrentBackend,
        TorrentFileLocator {
        private val sessionMutex = Mutex()
        private var sessionManager: SessionManager? = null

        private val deferredMap = ConcurrentHashMap<String, CompletableDeferred<TorrentMetadata>>()
        private val errorHashes = ConcurrentHashMap.newKeySet<String>()

        private val alertListener =
            object : AlertListener {
                override fun types(): IntArray =
                    intArrayOf(
                        AlertType.METADATA_RECEIVED.swig(),
                        AlertType.TORRENT_ERROR.swig(),
                        AlertType.FILE_ERROR.swig(),
                    )

                override fun alert(alert: Alert<*>) {
                    when (alert) {
                        is MetadataReceivedAlert -> onMetadataReceived(alert)
                        is TorrentErrorAlert, is FileErrorAlert -> onTorrentAlertError(alert)
                        else -> Unit
                    }
                }
            }

        private fun onMetadataReceived(alert: MetadataReceivedAlert) {
            val handle = alert.handle()?.takeIf { it.isValid }
            val ti = handle?.torrentFile()
            if (handle != null && ti != null) {
                val hash = handle.infoHash().toHex().lowercase()
                handleMetadata(handle, ti, hash)
            }
        }

        private fun onTorrentAlertError(alert: TorrentAlert<*>) {
            val handle = alert.handle() ?: return
            if (handle.isValid) {
                val hash = handle.infoHash().toHex().lowercase()
                errorHashes.add(hash)
                deferredMap[hash]?.completeExceptionally(TorrentBackendException(TorrentFailReason.STORAGE))
            }
        }

        private fun handleMetadata(
            handle: TorrentHandle,
            ti: TorrentInfo,
            hash: String,
        ) {
            val fs = ti.files()
            val numFiles = ti.numFiles()
            val filesList = ArrayList<BackendFile>(numFiles)
            for (i in 0 until numFiles) {
                filesList.add(
                    BackendFile(
                        index = i,
                        path = fs.filePath(i),
                        sizeBytes = fs.fileSize(i),
                        offsetBytes = fs.fileOffset(i),
                    ),
                )
            }
            handle.prioritizeFiles(Priority.array(Priority.IGNORE, numFiles))

            val metadata =
                TorrentMetadata(
                    files = filesList,
                    pieceLength = ti.pieceLength().toLong(),
                )
            deferredMap[hash]?.complete(metadata)
        }

        private suspend fun getOrCreateSession(): SessionManager =
            sessionMutex.withLock {
                sessionManager?.let { return it }
                try {
                    runCatchingLibraryCall {
                        cache.onSessionStart()
                        val sm = SessionManager()
                        sm.addListener(alertListener)
                        sm.start()
                        sessionManager = sm
                        sm
                    }
                } catch (e: TorrentBackendException) {
                    sessionManager = null
                    throw e
                }
            }

        override suspend fun add(
            infoHash: String,
            magnetUri: String,
        ) = withContext(io) {
            val hash = infoHash.lowercase()
            val deferred = CompletableDeferred<TorrentMetadata>()
            deferredMap[hash] = deferred
            errorHashes.remove(hash)

            runCatchingLibraryCall {
                val sm = getOrCreateSession()
                val torrentDir = File(cache.root, hash)
                if (!torrentDir.exists()) {
                    torrentDir.mkdirs()
                }
                cache.markActive(hash)
                cache.touch(hash)

                sm.download(magnetUri, torrentDir, TorrentFlags.SEQUENTIAL_DOWNLOAD)

                val handle = sm.find(Sha1Hash(hash))
                if (handle != null && handle.isValid && handle.status().hasMetadata()) {
                    val ti = handle.torrentFile()
                    if (ti != null) {
                        handleMetadata(handle, ti, hash)
                    }
                }
            }
        }

        override suspend fun awaitMetadata(infoHash: String): TorrentMetadata =
            withContext(io) {
                val hash = infoHash.lowercase()
                if (errorHashes.contains(hash)) {
                    throw TorrentBackendException(TorrentFailReason.STORAGE)
                }
                val deferred = deferredMap[hash] ?: throw TorrentBackendException(TorrentFailReason.STORAGE)
                runCatchingLibraryCall(TorrentFailReason.STORAGE) {
                    deferred.await()
                }
            }

        override suspend fun prioritize(
            infoHash: String,
            fileIndex: Int,
            pieces: List<Int>,
        ) = withContext(io) {
            runCatchingLibraryCall {
                val hash = infoHash.lowercase()
                val sm = sessionManager ?: throw TorrentBackendException(TorrentFailReason.STORAGE)
                val handle = sm.find(Sha1Hash(hash)) ?: throw TorrentBackendException(TorrentFailReason.STORAGE)
                if (!handle.isValid) throw TorrentBackendException(TorrentFailReason.STORAGE)

                handle.clearPieceDeadlines()

                val ti = handle.torrentFile()
                val numFiles = ti?.numFiles() ?: (fileIndex + 1)
                val priorities =
                    Array(numFiles) { idx ->
                        if (idx == fileIndex) Priority.SEVEN else Priority.IGNORE
                    }
                handle.prioritizeFiles(priorities)
                handle.setFlags(TorrentFlags.SEQUENTIAL_DOWNLOAD)

                pieces.forEachIndexed { position, pieceIndex ->
                    val deadlineMs = deadlineMsFor(position, TorrentConfig.PIECE_DEADLINE_STEP_MS)
                    handle.setPieceDeadline(pieceIndex, deadlineMs)
                }
            }
        }

        override suspend fun snapshot(
            infoHash: String,
            pieces: List<Int>,
        ): TorrentSnapshot =
            withContext(io) {
                val hash = infoHash.lowercase()
                if (errorHashes.contains(hash)) {
                    throw TorrentBackendException(TorrentFailReason.STORAGE)
                }
                runCatchingLibraryCall {
                    val sm = sessionManager ?: throw TorrentBackendException(TorrentFailReason.STORAGE)
                    val handle = sm.find(Sha1Hash(hash)) ?: throw TorrentBackendException(TorrentFailReason.STORAGE)
                    if (!handle.isValid) throw TorrentBackendException(TorrentFailReason.STORAGE)

                    val status = handle.status()
                    val haveCount = pieces.count { handle.havePiece(it) }
                    TorrentSnapshot(
                        piecesHave = haveCount,
                        downloadSpeedBps = status.downloadRate().toLong(),
                        peers = status.numPeers(),
                    )
                }
            }

        override suspend fun remove(infoHash: String) =
            withContext(io) {
                val hash = infoHash.lowercase()
                val keep = cache.keepAfterStop()
                deferredMap.remove(hash)?.cancel()
                errorHashes.remove(hash)

                runCatching {
                    val sm = sessionManager
                    if (sm != null) {
                        val handle = sm.find(Sha1Hash(hash))
                        if (handle != null && handle.isValid) {
                            if (keep) {
                                sm.remove(handle)
                            } else {
                                sm.remove(handle, SessionHandle.DELETE_FILES)
                            }
                        }
                    }
                }.onFailure { if (it is CancellationException) throw it }

                if (!keep) {
                    runCatching {
                        val torrentDir = File(cache.root, hash)
                        if (torrentDir.exists()) {
                            torrentDir.deleteRecursively()
                        }
                    }.onFailure { if (it is CancellationException) throw it }
                }

                cache.onRemoved(hash, kept = keep)
            }

        override fun fileOf(
            infoHash: String,
            relativePath: String,
        ): File =
            resolveInside(cache.root, infoHash, relativePath)
                ?: throw TorrentBackendException(TorrentFailReason.STORAGE)
    }

private inline fun <T> runCatchingLibraryCall(
    failReason: TorrentFailReason = TorrentFailReason.ENGINE_UNAVAILABLE,
    block: () -> T,
): T =
    runCatching(block).getOrElse { e ->
        if (e is CancellationException) throw e
        if (e is TorrentBackendException) throw e
        val ex = TorrentBackendException(failReason)
        ex.initCause(e)
        throw ex
    }
