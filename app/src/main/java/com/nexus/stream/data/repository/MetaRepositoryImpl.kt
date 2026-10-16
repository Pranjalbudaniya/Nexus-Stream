package com.nexus.stream.data.repository

import com.nexus.stream.data.local.db.dao.MetaCacheDao
import com.nexus.stream.data.local.db.entity.MetaCacheEntity
import com.nexus.stream.data.remote.addon.AddonMappers
import com.nexus.stream.data.remote.addon.AddonRemoteDataSource
import com.nexus.stream.di.IoDispatcher
import com.nexus.stream.domain.model.Addon
import com.nexus.stream.domain.model.AppError
import com.nexus.stream.domain.model.MetaDetail
import com.nexus.stream.domain.model.Outcome
import com.nexus.stream.domain.model.supports
import com.nexus.stream.domain.repository.AddonRepository
import com.nexus.stream.domain.repository.MetaRepository
import com.nexus.stream.domain.repository.MetaResult
import com.nexus.stream.domain.repository.MetaSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

private const val CACHE_TTL_MS: Long = 86_400_000L // 24 hours
private const val CANDIDATE_TIMEOUT_MS: Long = 10_000L // 10 seconds
private const val MAX_CACHE_ENTRIES: Int = 200

class MetaRepositoryImpl(
    private val remoteDataSource: AddonRemoteDataSource,
    private val addonRepository: AddonRepository,
    private val metaCacheDao: MetaCacheDao,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
    private val clock: () -> Long,
) : MetaRepository {
    @Inject
    constructor(
        remoteDataSource: AddonRemoteDataSource,
        addonRepository: AddonRepository,
        metaCacheDao: MetaCacheDao,
        @IoDispatcher dispatcher: CoroutineDispatcher,
    ) : this(
        remoteDataSource = remoteDataSource,
        addonRepository = addonRepository,
        metaCacheDao = metaCacheDao,
        dispatcher = dispatcher,
        clock = { System.currentTimeMillis() },
    )

    override suspend fun getMeta(
        type: String,
        id: String,
        forceRefresh: Boolean,
    ): Outcome<MetaResult> =
        withContext(dispatcher) {
            val cacheKey = "$type:$id"
            val cachedResult = getCached(cacheKey, forceRefresh)
            if (cachedResult?.source == MetaSource.CACHE) {
                return@withContext Outcome.Success(cachedResult)
            }

            val candidates =
                addonRepository
                    .observeEnabled()
                    .first()
                    .filter { it.manifest.supports("meta", type, id) }
                    .sortedBy { it.position }

            fetchFromAddons(
                candidates = candidates,
                type = type,
                id = id,
                cacheKey = cacheKey,
                staleMeta = cachedResult?.meta,
            )
        }

    private suspend fun getCached(
        cacheKey: String,
        forceRefresh: Boolean,
    ): MetaResult? {
        val cacheRow = metaCacheDao.get(cacheKey) ?: return null
        val cachedMeta = runCatching { AddonMappers.parseMeta(cacheRow.json) }.getOrNull()
        return cachedMeta?.let { meta ->
            val isFresh = (clock() - cacheRow.fetchedAt) < CACHE_TTL_MS
            val source = if (!forceRefresh && isFresh) MetaSource.CACHE else MetaSource.STALE
            MetaResult(meta = meta, source = source)
        }
    }

    private suspend fun fetchFromAddons(
        candidates: List<Addon>,
        type: String,
        id: String,
        cacheKey: String,
        staleMeta: MetaDetail?,
    ): Outcome<MetaResult> {
        var firstNonNotFoundError: AppError? = null
        for (candidate in candidates) {
            val outcome =
                withTimeoutOrNull(CANDIDATE_TIMEOUT_MS) {
                    remoteDataSource.metaWithRaw(candidate.transportUrl, type, id)
                } ?: Outcome.Failure(AppError.Timeout)

            when (outcome) {
                is Outcome.Success -> {
                    metaCacheDao.upsert(
                        MetaCacheEntity(
                            metaId = cacheKey,
                            json = outcome.value.rawJson,
                            fetchedAt = clock(),
                        ),
                    )
                    metaCacheDao.trimTo(MAX_CACHE_ENTRIES)
                    return Outcome.Success(MetaResult(outcome.value.meta, MetaSource.FRESH))
                }

                is Outcome.Failure -> {
                    if (firstNonNotFoundError == null && outcome.error !is AppError.NotFound) {
                        firstNonNotFoundError = outcome.error
                    }
                }
            }
        }

        return staleMeta?.let { Outcome.Success(MetaResult(it, MetaSource.STALE)) }
            ?: Outcome.Failure(firstNonNotFoundError ?: AppError.NotFound)
    }
}
