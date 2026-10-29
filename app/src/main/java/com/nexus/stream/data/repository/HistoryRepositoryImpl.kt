package com.nexus.stream.data.repository

import com.nexus.stream.data.local.db.dao.ProgressDao
import com.nexus.stream.domain.model.ContinueRow
import com.nexus.stream.domain.model.HistoryEntry
import com.nexus.stream.domain.repository.HistoryRepository
import com.nexus.stream.domain.usecase.latestPerTitle
import com.nexus.stream.domain.usecase.toHistoryEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class HistoryRepositoryImpl
    @Inject
    constructor(
        private val progressDao: ProgressDao,
    ) : HistoryRepository {
        override fun observeHistory(limit: Int): Flow<List<HistoryEntry>> =
            progressDao.observeHistoryRows(limit).map { rows ->
                val uniqueRows = latestPerTitle(rows)
                uniqueRows
                    .sortedWith(
                        compareByDescending<ContinueRow> { it.updatedAt }
                            .thenBy { it.metaId },
                    ).map { toHistoryEntry(it) }
            }

        override suspend fun remove(metaId: String) {
            progressDao.deleteByMeta(metaId)
        }

        override suspend fun clear() {
            progressDao.deleteAll()
        }
    }
