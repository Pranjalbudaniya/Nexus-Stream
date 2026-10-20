package com.nexus.stream.domain.repository

import kotlinx.coroutines.flow.Flow

interface TorrentPrefsRepository {
    val wifiOnly: Flow<Boolean>
    val cacheLimitGb: Flow<Int>
    val clearCacheOnExit: Flow<Boolean>

    suspend fun setWifiOnly(value: Boolean)

    suspend fun setCacheLimitGb(value: Int)

    suspend fun setClearCacheOnExit(value: Boolean)
}
