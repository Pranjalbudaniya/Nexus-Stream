package com.nexus.stream.data.remote.addon

import kotlinx.serialization.json.JsonObject
import retrofit2.http.GET
import retrofit2.http.Url

interface AddonApi {
    @GET
    suspend fun get(
        @Url url: String,
    ): JsonObject
}
