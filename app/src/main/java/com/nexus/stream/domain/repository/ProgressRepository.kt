package com.nexus.stream.domain.repository

import com.nexus.stream.domain.model.ContinueWatchingItem
import kotlinx.coroutines.flow.Flow

interface ProgressRepository {
    fun observeContinueWatching(): Flow<List<ContinueWatchingItem>>

    suspend fun removeFromContinueWatching(metaId: String)
}
