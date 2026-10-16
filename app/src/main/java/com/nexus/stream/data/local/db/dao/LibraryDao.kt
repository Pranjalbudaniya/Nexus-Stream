package com.nexus.stream.data.local.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.nexus.stream.data.local.db.entity.LibraryItemEntity
import kotlinx.coroutines.flow.Flow

data class LibraryStatusTuple(
    val metaId: String,
    val listStatus: String,
)

@Dao
interface LibraryDao {
    @Query("SELECT metaId, listStatus FROM library_items WHERE inWatchlist = 1")
    fun observeListStatuses(): Flow<List<LibraryStatusTuple>>

    @Query("SELECT * FROM library_items WHERE metaId = :metaId")
    suspend fun get(metaId: String): LibraryItemEntity?

    @Upsert
    suspend fun upsert(entity: LibraryItemEntity)
}
