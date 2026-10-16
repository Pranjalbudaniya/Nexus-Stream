package com.nexus.stream.data.repository

import com.nexus.stream.data.local.db.TransactionRunner
import com.nexus.stream.data.local.db.dao.LibraryDao
import com.nexus.stream.data.local.db.entity.LibraryItemEntity
import com.nexus.stream.di.IoDispatcher
import com.nexus.stream.domain.model.MetaPreview
import com.nexus.stream.domain.repository.LibraryRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

class LibraryRepositoryImpl(
    private val libraryDao: LibraryDao,
    private val transactionRunner: TransactionRunner,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
    private val clock: () -> Long,
) : LibraryRepository {
    @Inject
    constructor(
        libraryDao: LibraryDao,
        transactionRunner: TransactionRunner,
        @IoDispatcher dispatcher: CoroutineDispatcher,
    ) : this(
        libraryDao = libraryDao,
        transactionRunner = transactionRunner,
        dispatcher = dispatcher,
        clock = { System.currentTimeMillis() },
    )

    override fun observeWatchlistIds(): Flow<Set<String>> =
        libraryDao
            .observeWatchlistIds()
            .map { it.toSet() }
            .distinctUntilChanged()
            .flowOn(dispatcher)

    override suspend fun toggleWatchlist(item: MetaPreview) {
        withContext(dispatcher) {
            transactionRunner.run {
                val existing = libraryDao.get(item.id)
                if (existing == null) {
                    libraryDao.upsert(
                        LibraryItemEntity(
                            metaId = item.id,
                            type = item.type,
                            name = item.name,
                            poster = item.poster,
                            background = item.background,
                            inWatchlist = true,
                            addedAt = clock(),
                        ),
                    )
                } else {
                    val newInWatchlist = !existing.inWatchlist
                    val updated =
                        if (newInWatchlist) {
                            existing.copy(
                                type = item.type,
                                name = item.name,
                                poster = item.poster,
                                background = item.background,
                                inWatchlist = true,
                            )
                        } else {
                            existing.copy(
                                inWatchlist = false,
                            )
                        }
                    libraryDao.upsert(updated)
                }
            }
        }
    }
}
