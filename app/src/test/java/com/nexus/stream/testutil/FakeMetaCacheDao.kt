package com.nexus.stream.testutil

import com.nexus.stream.data.local.db.dao.MetaCacheDao
import com.nexus.stream.data.local.db.entity.MetaCacheEntity

class FakeMetaCacheDao : MetaCacheDao {
    val items = mutableMapOf<String, MetaCacheEntity>()
    var trimToCallCount = 0
    var lastTrimMax = 0

    override suspend fun get(metaId: String): MetaCacheEntity? = items[metaId]

    override suspend fun upsert(entity: MetaCacheEntity) {
        items[entity.metaId] = entity
    }

    override suspend fun trimTo(max: Int) {
        trimToCallCount++
        lastTrimMax = max
        if (items.size > max) {
            val sorted = items.values.sortedByDescending { it.fetchedAt }
            val toKeep = sorted.take(max).map { it.metaId }.toSet()
            items.keys.retainAll(toKeep)
        }
    }
}
