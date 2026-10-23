package com.nexus.stream.data.local.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.nexus.stream.data.local.db.entity.WatchProgressEntity
import com.nexus.stream.domain.model.ContinueRow
import kotlinx.coroutines.flow.Flow

@Dao
interface ProgressDao {
    @Query(
        """
        SELECT 
            p.metaId AS metaId,
            p.type AS type,
            l.name AS name,
            l.poster AS poster,
            p.videoId AS videoId,
            p.season AS season,
            p.episode AS episode,
            p.positionMs AS positionMs,
            p.durationMs AS durationMs,
            p.watched AS watched,
            p.updatedAt AS updatedAt
        FROM watch_progress p
        LEFT JOIN library_items l ON p.metaId = l.metaId
        WHERE p.key = (
            SELECT p2.key 
            FROM watch_progress p2 
            WHERE p2.metaId = p.metaId 
            ORDER BY p2.updatedAt DESC 
            LIMIT 1
        )
        ORDER BY p.updatedAt DESC
        LIMIT 40
        """,
    )
    fun observeContinueRows(): Flow<List<ContinueRow>>

    @Query("SELECT * FROM watch_progress WHERE `key` = :key LIMIT 1")
    suspend fun get(key: String): WatchProgressEntity?

    @Upsert
    suspend fun upsert(entity: WatchProgressEntity)

    @Upsert
    suspend fun upsertAll(entities: List<WatchProgressEntity>)

    @Query("DELETE FROM watch_progress WHERE metaId = :metaId")
    suspend fun deleteByMeta(metaId: String)

    @Query("SELECT * FROM watch_progress WHERE metaId = :metaId")
    fun observeByMeta(metaId: String): Flow<List<WatchProgressEntity>>

    @Query("DELETE FROM watch_progress WHERE `key` = :key")
    suspend fun deleteByKey(key: String)

    @Query("DELETE FROM watch_progress WHERE metaId = :metaId AND videoId IN (:videoIds)")
    suspend fun clearWatched(
        metaId: String,
        videoIds: List<String>,
    )

    @Query("DELETE FROM watch_progress WHERE metaId = :metaId AND watched = 0")
    suspend fun clearInProgress(metaId: String)

    @Query("SELECT metaId FROM watch_progress WHERE watched = 1 AND type = 'movie'")
    fun observeWatchedMovieIds(): Flow<List<String>>
}
