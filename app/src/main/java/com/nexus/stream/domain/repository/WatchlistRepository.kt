package com.nexus.stream.domain.repository

import com.nexus.stream.domain.model.WatchlistEntry
import kotlinx.coroutines.flow.Flow

interface WatchlistRepository {
    fun observeWatchlist(): Flow<List<WatchlistEntry>>
}
