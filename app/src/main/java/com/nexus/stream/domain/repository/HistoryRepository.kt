package com.nexus.stream.domain.repository

import com.nexus.stream.domain.model.HistoryEntry
import kotlinx.coroutines.flow.Flow

interface HistoryRepository {
    fun observeHistory(limit: Int): Flow<List<HistoryEntry>>

    suspend fun remove(metaId: String)

    suspend fun clear()
}
