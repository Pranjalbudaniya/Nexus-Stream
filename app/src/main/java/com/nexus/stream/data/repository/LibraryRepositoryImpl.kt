package com.nexus.stream.data.repository

import com.nexus.stream.data.local.db.TransactionRunner
import com.nexus.stream.data.local.db.dao.LibraryDao
import com.nexus.stream.data.local.db.entity.LibraryItemEntity
import com.nexus.stream.di.IoDispatcher
import com.nexus.stream.domain.model.ListStatus
import com.nexus.stream.domain.model.MetaPreview
import com.nexus.stream.domain.repository.LibraryRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import javax.inject.Inject

class LibraryRepositoryImpl(
    private val libraryDao: LibraryDao,
    private val transactionRunner: TransactionRunner,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
    scope: CoroutineScope = CoroutineScope(dispatcher + SupervisorJob()),
    private val clock: () -> Long = { System.currentTimeMillis() },
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
        scope = CoroutineScope(dispatcher + SupervisorJob()),
        clock = { System.currentTimeMillis() },
    )

    private val sharedStatuses: StateFlow<Map<String, ListStatus>> =
        libraryDao
            .observeListStatuses()
            .map { list ->
                list.associate { tuple ->
                    val status =
                        runCatching {
                            ListStatus.valueOf(tuple.listStatus)
                        }.getOrDefault(ListStatus.PLAN_TO_WATCH)
                    tuple.metaId to status
                }
            }.distinctUntilChanged()
            .flowOn(dispatcher)
            .stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = emptyMap(),
            )

    override fun observeListStatuses(): Flow<Map<String, ListStatus>> = sharedStatuses

    override suspend fun setStatus(
        item: MetaPreview,
        status: ListStatus?,
    ) {
        withContext(dispatcher) {
            transactionRunner.run {
                val existing = libraryDao.get(item.id)
                if (status == null) {
                    if (existing != null && existing.inWatchlist) {
                        libraryDao.upsert(existing.copy(inWatchlist = false))
                    }
                } else {
                    val entity =
                        if (existing != null) {
                            existing.copy(
                                type = item.type,
                                name = item.name,
                                poster = item.poster,
                                background = item.background,
                                inWatchlist = true,
                                listStatus = status.name,
                            )
                        } else {
                            LibraryItemEntity(
                                metaId = item.id,
                                type = item.type,
                                name = item.name,
                                poster = item.poster,
                                background = item.background,
                                inWatchlist = true,
                                addedAt = clock(),
                                listStatus = status.name,
                            )
                        }
                    libraryDao.upsert(entity)
                }
            }
        }
    }
}
