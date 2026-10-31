package com.nexus.stream.domain.repository

import com.nexus.stream.domain.model.DownloadFailure
import com.nexus.stream.domain.model.DownloadItem
import com.nexus.stream.domain.model.DownloadSource
import com.nexus.stream.domain.model.DownloadState
import com.nexus.stream.domain.model.GalleryFailure
import com.nexus.stream.domain.model.GalleryState
import com.nexus.stream.domain.model.PauseReason
import kotlinx.coroutines.flow.Flow

interface DownloadRepository {
    fun observeAll(): Flow<List<DownloadItem>>

    fun observeForMeta(metaId: String): Flow<List<DownloadItem>>

    suspend fun get(id: String): DownloadItem?

    suspend fun getAll(): List<DownloadItem>

    suspend fun insert(item: DownloadItem)

    suspend fun updateProgress(
        id: String,
        downloadedBytes: Long,
        totalBytes: Long,
        state: DownloadState,
        updatedAt: Long,
    )

    suspend fun updateState(
        id: String,
        state: DownloadState,
        updatedAt: Long,
        pauseReason: PauseReason? = null,
        failure: DownloadFailure? = null,
    )

    suspend fun updateCompleted(
        id: String,
        filePath: String,
        totalBytes: Long,
        completedAt: Long,
        updatedAt: Long,
    )

    suspend fun updateSource(
        id: String,
        source: DownloadSource,
        state: DownloadState,
        updatedAt: Long,
    )

    suspend fun setAllowMetered(
        id: String,
        allowMetered: Boolean,
        updatedAt: Long,
    )

    suspend fun delete(id: String)
}
