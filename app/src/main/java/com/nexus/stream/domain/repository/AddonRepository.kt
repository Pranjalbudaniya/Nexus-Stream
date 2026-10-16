package com.nexus.stream.domain.repository

import com.nexus.stream.domain.model.Addon
import com.nexus.stream.domain.model.AddonManifest
import com.nexus.stream.domain.model.Outcome
import kotlinx.coroutines.flow.Flow

interface AddonRepository {
    fun observeAll(): Flow<List<Addon>>

    fun observeEnabled(): Flow<List<Addon>>

    fun observeActive(): Flow<List<Addon>>

    suspend fun install(transportUrl: String): Outcome<Addon>

    suspend fun refresh(transportUrl: String): Outcome<Addon>

    suspend fun fetchManifest(transportUrl: String): Outcome<AddonManifest>

    suspend fun remove(transportUrl: String)

    suspend fun setEnabled(
        transportUrl: String,
        enabled: Boolean,
    )

    suspend fun move(
        transportUrl: String,
        toPosition: Int,
    )
}
