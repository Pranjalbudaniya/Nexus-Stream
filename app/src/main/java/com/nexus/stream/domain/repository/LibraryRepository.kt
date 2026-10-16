package com.nexus.stream.domain.repository

import com.nexus.stream.domain.model.MetaPreview
import kotlinx.coroutines.flow.Flow

interface LibraryRepository {
    fun observeWatchlistIds(): Flow<Set<String>>

    suspend fun toggleWatchlist(item: MetaPreview)
}
