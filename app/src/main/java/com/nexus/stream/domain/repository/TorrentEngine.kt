package com.nexus.stream.domain.repository

import com.nexus.stream.domain.model.TorrentRequest
import com.nexus.stream.domain.model.TorrentState
import kotlinx.coroutines.flow.Flow

interface TorrentEngine {
    fun prepare(request: TorrentRequest): Flow<TorrentState>

    suspend fun selectFile(
        infoHash: String,
        fileIdx: Int,
    )

    suspend fun stop(infoHash: String)
}
