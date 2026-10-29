package com.nexus.stream.data.repository

import com.nexus.stream.data.local.db.dao.SearchHistoryDao
import com.nexus.stream.data.local.db.entity.SearchHistoryEntity
import com.nexus.stream.di.IoDispatcher
import com.nexus.stream.domain.repository.SearchHistoryRepository
import com.nexus.stream.domain.usecase.HISTORY_LIMIT
import com.nexus.stream.domain.usecase.isSearchable
import com.nexus.stream.domain.usecase.normalizeSearchQuery
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

class SearchHistoryRepositoryImpl(
    private val dao: SearchHistoryDao,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : SearchHistoryRepository {
    @Inject
    constructor(
        dao: SearchHistoryDao,
        @IoDispatcher dispatcher: CoroutineDispatcher,
    ) : this(
        dao = dao,
        dispatcher = dispatcher,
        clock = { System.currentTimeMillis() },
    )

    override fun observeHistory(): Flow<List<String>> =
        dao
            .observeAll()
            .map { list -> list.map { it.query } }
            .flowOn(dispatcher)

    override suspend fun save(query: String): Unit =
        withContext(dispatcher) {
            val normalized = normalizeSearchQuery(query)
            if (!isSearchable(normalized)) return@withContext

            val all = dao.getAll()
            val existing = all.filter { it.query.equals(normalized, ignoreCase = true) }
            for (entry in existing) {
                if (entry.query != normalized) {
                    dao.delete(entry.query)
                }
            }

            val now = clock()
            dao.upsert(SearchHistoryEntity(query = normalized, lastUsedAt = now))

            val updated = dao.getAll()
            if (updated.size > HISTORY_LIMIT) {
                val toRemove = updated.drop(HISTORY_LIMIT)
                for (entry in toRemove) {
                    dao.delete(entry.query)
                }
            }
        }

    override suspend fun remove(query: String): Unit =
        withContext(dispatcher) {
            val trimmed = query.trim()
            val all = dao.getAll()
            val matching = all.filter { it.query.equals(trimmed, ignoreCase = true) }
            for (entry in matching) {
                dao.delete(entry.query)
            }
        }

    override suspend fun clear(): Unit =
        withContext(dispatcher) {
            dao.deleteAll()
        }
}
