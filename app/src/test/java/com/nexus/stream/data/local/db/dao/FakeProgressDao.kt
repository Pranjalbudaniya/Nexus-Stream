package com.nexus.stream.data.local.db.dao

import com.nexus.stream.data.local.db.entity.WatchProgressEntity
import com.nexus.stream.domain.model.ContinueRow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeProgressDao : ProgressDao {
    val progressEntities = MutableStateFlow<Map<String, WatchProgressEntity>>(emptyMap())
    val continueRows = MutableStateFlow<List<ContinueRow>>(emptyList())
    val deletedMetaIds = mutableListOf<String>()
    val deletedKeys = mutableListOf<String>()

    override fun observeContinueRows(): Flow<List<ContinueRow>> = continueRows

    override suspend fun upsert(entity: WatchProgressEntity) {
        val current = progressEntities.value.toMutableMap()
        current[entity.key] = entity
        progressEntities.value = current
    }

    override suspend fun deleteByMeta(metaId: String) {
        deletedMetaIds.add(metaId)
        val current = progressEntities.value.toMutableMap()
        current.entries.removeIf { it.value.metaId == metaId }
        progressEntities.value = current

        continueRows.value = continueRows.value.filter { it.metaId != metaId }
    }

    override fun observeByMeta(metaId: String): Flow<List<WatchProgressEntity>> =
        progressEntities.map { map ->
            map.values.filter { it.metaId == metaId }
        }

    override suspend fun deleteByKey(key: String) {
        deletedKeys.add(key)
        val current = progressEntities.value.toMutableMap()
        current.remove(key)
        progressEntities.value = current
    }
}
