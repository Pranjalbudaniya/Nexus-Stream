package com.nexus.stream.domain.repository

import kotlinx.coroutines.flow.Flow

interface ContentPrefsRepository {
    val familyMode: Flow<Boolean>

    suspend fun setFamilyMode(enabled: Boolean)
}
