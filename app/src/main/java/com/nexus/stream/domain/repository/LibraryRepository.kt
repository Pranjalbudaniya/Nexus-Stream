package com.nexus.stream.domain.repository

import com.nexus.stream.domain.model.ListStatus
import com.nexus.stream.domain.model.MetaPreview
import com.nexus.stream.domain.model.ProgressTarget
import kotlinx.coroutines.flow.Flow

data class LibraryItem(
    val metaId: String,
    val inWatchlist: Boolean,
    val status: ListStatus,
)

interface LibraryRepository {
    fun observeListStatuses(): Flow<Map<String, ListStatus>>

    suspend fun setStatus(
        item: MetaPreview,
        status: ListStatus?,
    )

    suspend fun ensureSnapshot(target: ProgressTarget)

    suspend fun getItem(metaId: String): LibraryItem?

    suspend fun setStatusById(
        metaId: String,
        status: ListStatus,
    )
}
