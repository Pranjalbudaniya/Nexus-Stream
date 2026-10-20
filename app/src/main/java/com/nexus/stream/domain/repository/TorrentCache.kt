package com.nexus.stream.domain.repository

interface TorrentCache {
    suspend fun cacheSizeBytes(): Long

    suspend fun clearCache()
}
