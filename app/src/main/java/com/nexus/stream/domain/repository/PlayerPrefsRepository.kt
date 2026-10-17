package com.nexus.stream.domain.repository

import kotlinx.coroutines.flow.Flow

interface PlayerPrefsRepository {
    val quickPlay: Flow<Boolean>

    suspend fun setQuickPlay(enabled: Boolean)
}
