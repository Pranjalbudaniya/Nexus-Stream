package com.nexus.stream.data.local.db.dao

import com.nexus.stream.data.local.db.entity.WatchProgressEntity
import com.nexus.stream.domain.model.ContinueRow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeProgressDao : ProgressDao {
    val progressEntities = MutableStateFlow<Map<String, WatchProgressEntity>>(emptyMap())
    val continueRows = MutableStateFlow<List<ContinueRow>>(emptyList())
    val deletedMetaIds = mutableListOf<String>()

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
}
