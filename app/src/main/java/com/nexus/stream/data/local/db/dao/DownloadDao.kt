package com.nexus.stream.data.local.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.nexus.stream.data.local.db.entity.DownloadEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE metaId = :metaId ORDER BY createdAt DESC")
    fun observeForMeta(metaId: String): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun get(id: String): DownloadEntity?

    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    suspend fun getAll(): List<DownloadEntity>

    @Upsert
    suspend fun upsert(entity: DownloadEntity)

    @Query(
        "UPDATE downloads SET downloadedBytes = :downloadedBytes, totalBytes = :totalBytes, " +
            "state = :state, updatedAt = :updatedAt WHERE id = :id",
    )
    suspend fun updateProgress(
        id: String,
        downloadedBytes: Long,
        totalBytes: Long,
        state: String,
        updatedAt: Long,
    )

    @Query(
        "UPDATE downloads SET state = :state, updatedAt = :updatedAt, " +
            "pauseReason = :pauseReason, failure = :failure WHERE id = :id",
    )
    suspend fun updateState(
        id: String,
        state: String,
        updatedAt: Long,
        pauseReason: String?,
        failure: String?,
    )

    @Query(
        "UPDATE downloads SET filePath = :filePath, totalBytes = :totalBytes, " +
            "completedAt = :completedAt, updatedAt = :updatedAt, state = 'COMPLETED' WHERE id = :id",
    )
    suspend fun updateCompleted(
        id: String,
        filePath: String,
        totalBytes: Long,
        completedAt: Long,
        updatedAt: Long,
    )

    @Query("UPDATE downloads SET allowMetered = :allowMetered, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setAllowMetered(
        id: String,
        allowMetered: Boolean,
        updatedAt: Long,
    )

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun delete(id: String)
}
