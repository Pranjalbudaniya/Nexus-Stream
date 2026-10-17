package com.nexus.stream.domain.repository

import com.nexus.stream.domain.model.Outcome
import com.nexus.stream.domain.model.StoreAddon

interface CommunityAddonsRepository {
    suspend fun fetch(forceRefresh: Boolean = false): Outcome<List<StoreAddon>>

    companion object {
        const val COMMUNITY_ADDONS_MANIFEST_URL = "https://stremio-addons.net/api/manifest.json"
    }
}
