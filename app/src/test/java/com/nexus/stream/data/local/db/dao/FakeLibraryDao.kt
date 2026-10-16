package com.nexus.stream.data.local.db.dao

import com.nexus.stream.data.local.db.entity.LibraryItemEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeLibraryDao : LibraryDao {
    val items = MutableStateFlow<Map<String, LibraryItemEntity>>(emptyMap())

    override fun observeWatchlistIds(): Flow<List<String>> =
        items.map { map ->
            map.values.filter { it.inWatchlist }.map { it.metaId }
        }

    override suspend fun get(metaId: String): LibraryItemEntity? = items.value[metaId]

    override suspend fun upsert(entity: LibraryItemEntity) {
        val current = items.value.toMutableMap()
        current[entity.metaId] = entity
        items.value = current
    }
}
