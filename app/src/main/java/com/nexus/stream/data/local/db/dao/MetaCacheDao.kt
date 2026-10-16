package com.nexus.stream.data.local.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.nexus.stream.data.local.db.entity.MetaCacheEntity

@Dao
interface MetaCacheDao {
    @Query("SELECT * FROM meta_cache WHERE metaId = :metaId LIMIT 1")
    suspend fun get(metaId: String): MetaCacheEntity?

    @Upsert
    suspend fun upsert(entity: MetaCacheEntity)

    @Query(
        """
        DELETE FROM meta_cache 
        WHERE metaId NOT IN (
            SELECT metaId FROM meta_cache 
            ORDER BY fetchedAt DESC 
            LIMIT :max
        )
        """,
    )
    suspend fun trimTo(max: Int)
}
