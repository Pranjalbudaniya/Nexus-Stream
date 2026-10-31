package com.nexus.stream.domain.repository

import kotlinx.coroutines.flow.Flow

const val MAX_CONCURRENT_DEFAULT = 2
const val MAX_CONCURRENT_MIN = 1
const val MAX_CONCURRENT_MAX = 3

interface DownloadPrefsRepository {
    val wifiOnly: Flow<Boolean>
    val maxConcurrent: Flow<Int>
    val introSeen: Flow<Boolean>
    val saveToGallery: Flow<Boolean>

    suspend fun setWifiOnly(enabled: Boolean)

    suspend fun setMaxConcurrent(count: Int)

    suspend fun markIntroSeen()

    suspend fun setSaveToGallery(enabled: Boolean)
}
