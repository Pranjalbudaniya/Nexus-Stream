package com.nexus.stream.domain.repository

import kotlinx.coroutines.flow.Flow

interface UpdatePrefsRepository {
    val autoCheck: Flow<Boolean>
    val lastCheckedAt: Flow<Long?>
    val pendingInstallCode: Flow<Long?>

    suspend fun setAutoCheck(enabled: Boolean)

    suspend fun setLastCheckedAt(timestamp: Long)

    suspend fun setPendingInstallCode(versionCode: Long)

    suspend fun clearPendingInstall()
}
