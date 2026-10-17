package com.nexus.stream.data.repository

import com.nexus.stream.data.remote.addon.AddonRemoteDataSource
import com.nexus.stream.data.remote.addon.StoreMappers
import com.nexus.stream.di.DefaultDispatcher
import com.nexus.stream.domain.model.AddonManifest
import com.nexus.stream.domain.model.AppError
import com.nexus.stream.domain.model.CatalogSpec
import com.nexus.stream.domain.model.Outcome
import com.nexus.stream.domain.model.StoreAddon
import com.nexus.stream.domain.repository.CommunityAddonsRepository
import com.nexus.stream.domain.repository.CommunityAddonsRepository.Companion.COMMUNITY_ADDONS_MANIFEST_URL
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CommunityAddonsRepositoryImpl(
    private val dataSource: AddonRemoteDataSource,
    private val json: Json,
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher,
    private val clock: () -> Long,
) : CommunityAddonsRepository {
    @Inject
    constructor(
        dataSource: AddonRemoteDataSource,
        json: Json,
        @DefaultDispatcher dispatcher: CoroutineDispatcher,
    ) : this(
        dataSource = dataSource,
        json = json,
        dispatcher = dispatcher,
        clock = { System.currentTimeMillis() },
    )

    private var cachedList: List<StoreAddon>? = null
    private var cacheTimestamp: Long = 0L

    override suspend fun fetch(forceRefresh: Boolean): Outcome<List<StoreAddon>> {
        val cached = getValidCache(forceRefresh)
        if (cached != null) {
            return Outcome.Success(cached)
        }
        return fetchRemote()
    }

    private fun getValidCache(forceRefresh: Boolean): List<StoreAddon>? {
        if (forceRefresh) return null
        return synchronized(this) {
            val cached = cachedList
            if (cached != null && (clock() - cacheTimestamp < CACHE_DURATION_MS)) {
                cached
            } else {
                null
            }
        }
    }

    private suspend fun fetchRemote(): Outcome<List<StoreAddon>> {
        val manifestOutcome = dataSource.manifest(COMMUNITY_ADDONS_MANIFEST_URL)
        return when (manifestOutcome) {
            is Outcome.Success -> loadCatalogForManifest(manifestOutcome.value)
            is Outcome.Failure -> manifestOutcome
        }
    }

    private suspend fun loadCatalogForManifest(manifest: AddonManifest): Outcome<List<StoreAddon>> {
        val spec =
            manifest.addonCatalogs.firstOrNull()
                ?: return Outcome.Failure(AppError.Parse)
        return loadCatalogAndParse(spec)
    }

    private suspend fun loadCatalogAndParse(spec: CatalogSpec): Outcome<List<StoreAddon>> {
        val jsonElement =
            when (val res = dataSource.addonCatalog(COMMUNITY_ADDONS_MANIFEST_URL, spec.type, spec.id)) {
                is Outcome.Success -> res.value
                is Outcome.Failure -> return res
            }

        return withContext(dispatcher) {
            parseAndCache(jsonElement)
        }
    }

    private fun parseAndCache(jsonElement: JsonElement): Outcome<List<StoreAddon>> {
        val result =
            StoreMappers.mapStoreAddons(jsonElement, json)
                ?: return Outcome.Failure(AppError.Parse)

        synchronized(this) {
            cachedList = result
            cacheTimestamp = clock()
        }
        return Outcome.Success(result)
    }

    companion object {
        const val CACHE_DURATION_MS = 10 * 60 * 1000L // 10 minutes
    }
}
