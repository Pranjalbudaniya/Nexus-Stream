package com.nexus.stream.data.torrent

import com.nexus.stream.domain.model.AppError
import com.nexus.stream.domain.model.TorrentRequest
import com.nexus.stream.domain.model.TorrentState
import com.nexus.stream.domain.repository.TorrentEngine
import com.nexus.stream.domain.repository.TorrentPrefsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

interface TorrentSessionControl {
    suspend fun stopAll()
}

internal fun isMeteredBlocked(
    wifiOnly: Boolean,
    allowMetered: Boolean,
    status: NetworkStatus,
): Boolean = wifiOnly && !allowMetered && status == NetworkStatus.METERED

@Singleton
class GatedTorrentEngine internal constructor(
    private val delegate: TorrentEngine,
    private val network: NetworkStatusProvider,
    private val prefs: TorrentPrefsRepository,
    private val keepAlive: TorrentKeepAlive,
) : TorrentEngine,
    TorrentSessionControl {
    @Inject
    constructor(
        delegate: ServingTorrentEngine,
        network: NetworkStatusProvider,
        prefs: TorrentPrefsRepository,
        keepAlive: TorrentKeepAlive,
    ) : this(
        delegate = delegate as TorrentEngine,
        network = network,
        prefs = prefs,
        keepAlive = keepAlive,
    )

    private val activeHashesLock = Any()
    private val activeHashes = mutableMapOf<String, Int>()

    override fun prepare(request: TorrentRequest): Flow<TorrentState> =
        flow {
            val wifiOnly = prefs.wifiOnly.first()
            val networkStatus = network.current()

            if (isMeteredBlocked(wifiOnly = wifiOnly, allowMetered = request.allowMetered, status = networkStatus)) {
                emit(TorrentState.Failed(AppError.MeteredBlocked))
                return@flow
            }

            val normalizedHash = request.infoHash.trim().lowercase()
            var acquired = false

            try {
                delegate.prepare(request).collect { state ->
                    if (!acquired && state !is TorrentState.Failed) {
                        acquired = true
                        trackHash(normalizedHash)
                        keepAlive.acquire()
                    }
                    emit(state)
                }
            } finally {
                if (acquired) {
                    untrackHash(normalizedHash)
                    keepAlive.release()
                }
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

    override suspend fun stopAll() {
        val hashesToStop =
            synchronized(activeHashesLock) {
                activeHashes.keys.toList()
            }
        for (hash in hashesToStop) {
            delegate.stop(hash)
        }
    }

    private fun trackHash(hash: String) {
        synchronized(activeHashesLock) {
            activeHashes[hash] = (activeHashes[hash] ?: 0) + 1
        }
    }

    private fun untrackHash(hash: String) {
        synchronized(activeHashesLock) {
            val count = activeHashes[hash] ?: 0
            if (count <= 1) {
                activeHashes.remove(hash)
            } else {
                activeHashes[hash] = count - 1
            }
        }
    }
}
