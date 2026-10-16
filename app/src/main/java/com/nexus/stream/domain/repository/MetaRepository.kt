package com.nexus.stream.domain.repository

import com.nexus.stream.domain.model.MetaDetail
import com.nexus.stream.domain.model.Outcome

enum class MetaSource {
    FRESH,
    CACHE,
    STALE,
}

data class MetaResult(
    val meta: MetaDetail,
    val source: MetaSource,
)

interface MetaRepository {
    suspend fun getMeta(
        type: String,
        id: String,
        forceRefresh: Boolean = false,
    ): Outcome<MetaResult>
}
