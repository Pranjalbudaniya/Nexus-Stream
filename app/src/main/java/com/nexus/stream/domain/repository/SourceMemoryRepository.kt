package com.nexus.stream.domain.repository

import com.nexus.stream.domain.model.SourceFingerprint

interface SourceMemoryRepository {
    suspend fun get(key: String): SourceFingerprint?

    suspend fun remember(
        key: String,
        fingerprint: SourceFingerprint,
    )

    suspend fun forget(key: String)
}
