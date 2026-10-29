package com.nexus.stream.domain.repository

import kotlinx.coroutines.flow.Flow

interface SearchHistoryRepository {
    fun observeHistory(): Flow<List<String>>

    suspend fun save(query: String)

    suspend fun remove(query: String)

    suspend fun clear()
}
