package com.nexus.stream.data.local.db.dao

import com.nexus.stream.data.local.db.entity.WatchProgressEntity
import com.nexus.stream.domain.model.ContinueRow
import com.nexus.stream.domain.model.ListStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class FakeProgressDao(
    private val libraryDao: FakeLibraryDao? = null,
) : ProgressDao {
    val progressEntities = MutableStateFlow<Map<String, WatchProgressEntity>>(emptyMap())
    val continueRows = MutableStateFlow<List<ContinueRow>>(emptyList())
    val deletedMetaIds = mutableListOf<String>()
    val deletedKeys = mutableListOf<String>()

    override fun observeContinueRows(): Flow<List<ContinueRow>> {
        val libDao = libraryDao
        if (libDao != null) {
            return combine(progressEntities, libDao.items) { progressMap, libraryMap ->
                val byMeta = progressMap.values.groupBy { it.metaId }
                byMeta.values
                    .mapNotNull { rows ->
                        val newest = rows.maxByOrNull { it.updatedAt } ?: return@mapNotNull null
                        val libraryItem = libraryMap[newest.metaId]
                        ContinueRow(
                            metaId = newest.metaId,
                            type = newest.type,
                            name = libraryItem?.name.orEmpty(),
                            poster = libraryItem?.poster,
                            videoId = newest.videoId,
                            season = newest.season,
                            episode = newest.episode,
                            positionMs = newest.positionMs,
                            durationMs = newest.durationMs,
                            watched = newest.watched,
                            updatedAt = newest.updatedAt,
                            inWatchlist = libraryItem?.inWatchlist ?: false,
                            listStatus =
                                libraryItem?.listStatus?.let {
                                    runCatching { ListStatus.valueOf(it) }.getOrNull()
                                },
                        )
                    }.sortedByDescending { it.updatedAt }
                    .take(40)
            }
        }
        return continueRows
    }

    override suspend fun get(key: String): WatchProgressEntity? = progressEntities.value[key]

    override suspend fun upsert(entity: WatchProgressEntity) {
        val current = progressEntities.value.toMutableMap()
        current[entity.key] = entity
        progressEntities.value = current
    }

    override suspend fun upsertAll(entities: List<WatchProgressEntity>) {
        val current = progressEntities.value.toMutableMap()
        for (entity in entities) {
            current[entity.key] = entity
        }
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

    override suspend fun clearWatched(
        metaId: String,
        videoIds: List<String>,
    ) {
        deletedKeys.addAll(videoIds.map { "$metaId|$it" })
        val current = progressEntities.value.toMutableMap()
        current.entries.removeIf { it.value.metaId == metaId && it.value.videoId in videoIds }
        progressEntities.value = current
    }

    override suspend fun clearInProgress(metaId: String) {
        val current = progressEntities.value.toMutableMap()
        current.entries.removeIf { it.value.metaId == metaId && !it.value.watched }
        progressEntities.value = current

        val remainingForMeta = current.values.filter { it.metaId == metaId }
        val newest = remainingForMeta.maxByOrNull { it.updatedAt }
        val existingRow = continueRows.value.firstOrNull { it.metaId == metaId }
        val updatedList = continueRows.value.filter { it.metaId != metaId }.toMutableList()
        if (newest != null && existingRow != null) {
            updatedList.add(
                existingRow.copy(
                    videoId = newest.videoId,
                    season = newest.season,
                    episode = newest.episode,
                    positionMs = newest.positionMs,
                    durationMs = newest.durationMs,
                    watched = newest.watched,
                    updatedAt = newest.updatedAt,
                ),
            )
        }
        continueRows.value = updatedList
    }

    override fun observeWatchedMovieIds(): Flow<List<String>> =
        progressEntities.map { map ->
            map.values
                .filter { it.watched && it.type.equals("movie", ignoreCase = true) }
                .map { it.metaId }
        }

    override fun observeHistoryRows(limit: Int): Flow<List<ContinueRow>> {
        val libDao = libraryDao
        if (libDao != null) {
            return combine(progressEntities, libDao.items) { progressMap, libraryMap ->
                val byMeta = progressMap.values.groupBy { it.metaId }
                byMeta.values
                    .mapNotNull { rows ->
                        val newest = rows.maxByOrNull { it.updatedAt } ?: return@mapNotNull null
                        val libraryItem = libraryMap[newest.metaId]
                        ContinueRow(
                            metaId = newest.metaId,
                            type = newest.type,
                            name = libraryItem?.name,
                            poster = libraryItem?.poster,
                            videoId = newest.videoId,
                            season = newest.season,
                            episode = newest.episode,
                            positionMs = newest.positionMs,
                            durationMs = newest.durationMs,
                            watched = newest.watched,
                            updatedAt = newest.updatedAt,
                            inWatchlist = libraryItem?.inWatchlist ?: false,
                            listStatus =
                                libraryItem?.listStatus?.let {
                                    runCatching { ListStatus.valueOf(it) }.getOrNull()
                                },
                        )
                    }.sortedByDescending { it.updatedAt }
                    .take(limit)
            }
        }
        return continueRows.map { it.take(limit) }
    }

    override suspend fun deleteAll() {
        progressEntities.value = emptyMap()
        continueRows.value = emptyList()
    }
}
