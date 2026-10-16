package com.nexus.stream.domain.repository

import com.nexus.stream.domain.model.ContinueWatchingItem
import com.nexus.stream.domain.model.EpisodeProgress
import com.nexus.stream.domain.model.EpisodeRef
import com.nexus.stream.domain.model.MetaPreview
import kotlinx.coroutines.flow.Flow

interface ProgressRepository {
    fun observeContinueWatching(): Flow<List<ContinueWatchingItem>>

    suspend fun removeFromContinueWatching(metaId: String)

    fun observeEpisodeProgress(metaId: String): Flow<Map<String, EpisodeProgress>>

    suspend fun setWatched(
        meta: MetaPreview,
        episode: EpisodeRef,
        watched: Boolean,
    )

    suspend fun setWatchedMany(
        meta: MetaPreview,
        episodes: List<EpisodeRef>,
        watched: Boolean,
    )

    suspend fun clearInProgress(metaId: String)

    fun observeWatchedMovieIds(): Flow<Set<String>>
}
