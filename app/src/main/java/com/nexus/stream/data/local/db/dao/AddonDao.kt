package com.nexus.stream.data.local.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.nexus.stream.data.local.db.entity.AddonEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AddonDao {
    @Query("SELECT * FROM addons ORDER BY position ASC")
    fun observeAll(): Flow<List<AddonEntity>>

    @Query("SELECT * FROM addons ORDER BY position ASC")
    suspend fun getAll(): List<AddonEntity>

    @Query("SELECT * FROM addons WHERE transportUrl = :transportUrl")
    suspend fun get(transportUrl: String): AddonEntity?

    @Upsert
    suspend fun upsert(entity: AddonEntity)

    @Update
    suspend fun updateAll(entities: List<AddonEntity>)

    @Query("DELETE FROM addons WHERE transportUrl = :transportUrl")
    suspend fun delete(transportUrl: String)

    @Query("UPDATE addons SET enabled = :enabled WHERE transportUrl = :transportUrl")
    suspend fun setEnabled(
        transportUrl: String,
        enabled: Boolean,
    )
}
