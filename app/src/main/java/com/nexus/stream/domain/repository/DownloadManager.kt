package com.nexus.stream.domain.repository

import com.nexus.stream.domain.model.DownloadEffect
import com.nexus.stream.domain.model.DownloadItem
import com.nexus.stream.domain.model.DownloadLive
import com.nexus.stream.domain.model.DownloadRequest
import com.nexus.stream.domain.model.DownloadSource
import com.nexus.stream.domain.model.EnqueueResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

@Suppress("TooManyFunctions")
interface DownloadManager {
    val items: Flow<List<DownloadItem>>
    val live: StateFlow<Map<String, DownloadLive>>
    val effects: Flow<DownloadEffect>

    suspend fun enqueue(request: DownloadRequest): EnqueueResult

    fun pause(id: String)

    fun resume(id: String)

    fun pauseAll()

    fun resumeAll()

    fun allowMobileData(id: String?)

    suspend fun cancel(id: String)

    suspend fun delete(id: String)

    suspend fun deleteCompleted()

    suspend fun replaceSource(
        id: String,
        source: DownloadSource,
    )

    suspend fun onAppStart()

    suspend fun onServiceTimeout()

    suspend fun verifyCompleted(id: String): Boolean

    fun fileOf(id: String): File?
}
