package com.nexus.stream.domain.repository

import com.nexus.stream.domain.model.ListStatus
import com.nexus.stream.domain.model.MetaPreview
import kotlinx.coroutines.flow.Flow

interface LibraryRepository {
    fun observeListStatuses(): Flow<Map<String, ListStatus>>

    suspend fun setStatus(
        item: MetaPreview,
        status: ListStatus?,
    )
}
