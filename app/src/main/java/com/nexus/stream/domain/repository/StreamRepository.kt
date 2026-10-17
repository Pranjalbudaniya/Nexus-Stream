package com.nexus.stream.domain.repository

import com.nexus.stream.domain.model.AppError
import com.nexus.stream.domain.model.ParsedStream
import kotlinx.coroutines.flow.Flow

data class AddonFailure(
    val addonName: String,
    val error: AppError,
    val transportUrl: String = "",
)

data class StreamsState(
    val items: List<ParsedStream>,
    val answered: Int,
    val total: Int,
    val failures: List<AddonFailure>,
    val done: Boolean,
)

interface StreamRepository {
    fun streams(
        type: String,
        videoId: String,
        forceRefresh: Boolean = false,
        onlyAddons: Set<String>? = null,
    ): Flow<StreamsState>
}
