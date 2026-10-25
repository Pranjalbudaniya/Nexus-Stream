package com.nexus.stream.domain.repository

interface CacheCompletion {
    suspend fun onWatched(key: String)
}
