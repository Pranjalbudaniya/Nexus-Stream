package com.nexus.stream.data.repository

import com.nexus.stream.data.local.db.dao.ProgressDao
import com.nexus.stream.di.IoDispatcher
import com.nexus.stream.domain.model.ContinueWatchingItem
import com.nexus.stream.domain.repository.ProgressRepository
import com.nexus.stream.domain.usecase.ContinueWatchingRules
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

class ProgressRepositoryImpl
    @Inject
    constructor(
        private val progressDao: ProgressDao,
        private val continueWatchingRules: ContinueWatchingRules,
        @IoDispatcher private val dispatcher: CoroutineDispatcher,
    ) : ProgressRepository {
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
    }
