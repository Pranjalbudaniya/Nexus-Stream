package com.nexus.stream.domain.repository

import com.nexus.stream.domain.model.HomeRowConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

interface HomeRowConfigRepository {
    val config: Flow<HomeRowConfig>

    suspend fun update(transform: (HomeRowConfig) -> HomeRowConfig)

    suspend fun reset()
}

object EmptyHomeRowConfigRepository : HomeRowConfigRepository {
    override val config: Flow<HomeRowConfig> = flowOf(HomeRowConfig())

    override suspend fun update(transform: (HomeRowConfig) -> HomeRowConfig) = Unit

    override suspend fun reset() = Unit
}
