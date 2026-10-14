package com.nexus.stream.data.local.db.dao

import com.nexus.stream.data.local.db.entity.AddonEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeAddonDao : AddonDao {
    private val addons = MutableStateFlow<Map<String, AddonEntity>>(emptyMap())

    override fun observeAll(): Flow<List<AddonEntity>> = addons.map { map -> map.values.sortedBy { it.position } }

    override suspend fun getAll(): List<AddonEntity> = addons.value.values.sortedBy { it.position }

    override suspend fun get(transportUrl: String): AddonEntity? = addons.value[transportUrl]

    override suspend fun upsert(entity: AddonEntity) {
        val current = addons.value.toMutableMap()
        current[entity.transportUrl] = entity
        addons.value = current
    }

    override suspend fun updateAll(entities: List<AddonEntity>) {
        val current = addons.value.toMutableMap()
        for (entity in entities) {
            current[entity.transportUrl] = entity
        }
        addons.value = current
    }

    override suspend fun delete(transportUrl: String) {
        val current = addons.value.toMutableMap()
        current.remove(transportUrl)
        addons.value = current
    }

    override suspend fun setEnabled(
        transportUrl: String,
        enabled: Boolean,
    ) {
        val current = addons.value.toMutableMap()
        val existing = current[transportUrl] ?: return
        current[transportUrl] = existing.copy(enabled = enabled)
        addons.value = current
    }
}
