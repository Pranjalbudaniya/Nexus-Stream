package com.nexus.stream.domain.repository

import kotlinx.coroutines.flow.Flow

interface TorrentPrefsRepository {
    val wifiOnly: Flow<Boolean>

    suspend fun setWifiOnly(value: Boolean)
}
