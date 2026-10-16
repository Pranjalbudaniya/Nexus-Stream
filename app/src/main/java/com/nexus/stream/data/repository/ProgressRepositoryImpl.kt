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
import com.nexus.stream.domain.repository.ProgressRepository
import com.nexus.stream.domain.usecase.ContinueWatchingRules
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
        withContext(dispatcher) {
            progressDao.deleteByMeta(metaId)
        }
    }

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
        withContext(dispatcher) {
            transactionRunner.run {
                val key = "${meta.id}|${episode.videoId}"
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
                    progressDao.upsert(
                        WatchProgressEntity(
                            key = key,
                            metaId = meta.id,
                            videoId = episode.videoId,
                            type = episode.type,
                            season = episode.season,
                            episode = episode.episode,
                            positionMs = 0L,
                            durationMs = 0L,
                            watched = true,
                            updatedAt = clock(),
                        ),
                    )
                } else {
                    progressDao.deleteByKey(key)
                }
            }
        }
    }
}
