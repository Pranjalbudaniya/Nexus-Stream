package com.nexus.stream.data.repository

import com.nexus.stream.data.local.db.TransactionRunner
import com.nexus.stream.data.local.db.dao.LibraryDao
import com.nexus.stream.data.local.db.dao.ProgressDao
import com.nexus.stream.data.local.db.entity.LibraryItemEntity
import com.nexus.stream.data.local.db.entity.WatchProgressEntity
import com.nexus.stream.di.IoDispatcher
import com.nexus.stream.domain.model.ContinueWatchingItem
import com.nexus.stream.domain.model.EpisodeProgress
import com.nexus.stream.domain.model.EpisodeRef
import com.nexus.stream.domain.model.MetaPreview
import com.nexus.stream.domain.model.WatchProgress
import com.nexus.stream.domain.repository.ProgressRepository
import com.nexus.stream.domain.usecase.ContinueWatchingRules
import com.nexus.stream.domain.usecase.ProgressRules
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

class ProgressRepositoryImpl(
    private val progressDao: ProgressDao,
    private val libraryDao: LibraryDao,
    private val transactionRunner: TransactionRunner,
    private val continueWatchingRules: ContinueWatchingRules,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
    private val clock: () -> Long,
) : ProgressRepository {
    @Inject
    constructor(
        progressDao: ProgressDao,
        libraryDao: LibraryDao,
        transactionRunner: TransactionRunner,
        continueWatchingRules: ContinueWatchingRules,
        @IoDispatcher dispatcher: CoroutineDispatcher,
    ) : this(
        progressDao = progressDao,
        libraryDao = libraryDao,
        transactionRunner = transactionRunner,
        continueWatchingRules = continueWatchingRules,
        dispatcher = dispatcher,
        clock = { System.currentTimeMillis() },
    )

    override fun observeContinueWatching(): Flow<List<ContinueWatchingItem>> =
        progressDao
            .observeContinueRows()
            .map { continueWatchingRules(it) }
            .distinctUntilChanged()
            .flowOn(dispatcher)

    override suspend fun removeFromContinueWatching(metaId: String) {
        clearInProgress(metaId)
    }

    override suspend fun clearInProgress(metaId: String) {
        withContext(dispatcher) {
            progressDao.clearInProgress(metaId)
        }
    }

    override fun observeWatchedMovieIds(): Flow<Set<String>> =
        progressDao
            .observeWatchedMovieIds()
            .map { it.toSet() }
            .distinctUntilChanged()
            .flowOn(dispatcher)

    override fun observeEpisodeProgress(metaId: String): Flow<Map<String, EpisodeProgress>> =
        progressDao
            .observeByMeta(metaId)
            .map { list ->
                list.associate { entity ->
                    entity.videoId to
                        EpisodeProgress(
                            videoId = entity.videoId,
                            positionMs = entity.positionMs,
                            durationMs = entity.durationMs,
                            watched = entity.watched,
                            updatedAt = entity.updatedAt,
                        )
                }
            }.distinctUntilChanged()
            .flowOn(dispatcher)

    override suspend fun setWatched(
        meta: MetaPreview,
        episode: EpisodeRef,
        watched: Boolean,
    ) {
        setWatchedMany(meta, listOf(episode), watched)
    }

    override suspend fun setWatchedMany(
        meta: MetaPreview,
        episodes: List<EpisodeRef>,
        watched: Boolean,
    ) {
        if (episodes.isEmpty()) return
        withContext(dispatcher) {
            transactionRunner.run {
                if (watched) {
                    val existing = libraryDao.get(meta.id)
                    if (existing == null) {
                        libraryDao.upsert(
                            LibraryItemEntity(
                                metaId = meta.id,
                                type = meta.type,
                                name = meta.name,
                                poster = meta.poster,
                                background = meta.background,
                                inWatchlist = false,
                                addedAt = clock(),
                            ),
                        )
                    }
                    val now = clock()
                    val entities =
                        episodes.map { ep ->
                            WatchProgressEntity(
                                key = ProgressRules.progressKey(meta.id, ep.videoId),
                                metaId = meta.id,
                                videoId = ep.videoId,
                                type = ep.type,
                                season = ep.season,
                                episode = ep.episode,
                                positionMs = 0L,
                                durationMs = 0L,
                                watched = true,
                                updatedAt = now,
                            )
                        }
                    progressDao.upsertAll(entities)
                } else {
                    val videoIds = episodes.map { it.videoId }
                    progressDao.clearWatched(meta.id, videoIds)
                }
            }
        }
    }

    override suspend fun get(key: String): WatchProgress? =
        withContext(dispatcher) {
            progressDao.get(key)?.toDomain()
        }

    override suspend fun upsert(progress: WatchProgress) {
        withContext(dispatcher) {
            progressDao.upsert(progress.toEntity())
        }
    }
}

private fun WatchProgressEntity.toDomain(): WatchProgress =
    WatchProgress(
        key = key,
        metaId = metaId,
        videoId = videoId,
        type = type,
        season = season,
        episode = episode,
        positionMs = positionMs,
        durationMs = durationMs,
        watched = watched,
        updatedAt = updatedAt,
    )

private fun WatchProgress.toEntity(): WatchProgressEntity =
    WatchProgressEntity(
        key = key,
        metaId = metaId,
        videoId = videoId,
        type = type,
        season = season,
        episode = episode,
        positionMs = positionMs,
        durationMs = durationMs,
        watched = watched,
        updatedAt = updatedAt,
    )
