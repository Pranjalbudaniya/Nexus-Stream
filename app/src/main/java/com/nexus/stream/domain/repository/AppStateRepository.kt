package com.nexus.stream.domain.repository

import kotlinx.coroutines.flow.Flow

interface AppStateRepository {
    val defaultAddonsSeeded: Flow<Boolean>

    suspend fun markDefaultAddonsSeeded()
}
