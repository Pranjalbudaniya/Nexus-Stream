package com.nexus.stream.data.remote.addon

import com.nexus.stream.domain.model.AppError
import com.nexus.stream.domain.model.HomeRowSpec
import com.nexus.stream.domain.model.MetaPreview
import com.nexus.stream.domain.model.Outcome
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HomeRowLoader(
    private val remote: AddonRemoteDataSource,
    private val clock: () -> Long,
    private val semaphore: Semaphore,
) {
    @Inject
    constructor(remote: AddonRemoteDataSource) : this(
        remote = remote,
        clock = { System.currentTimeMillis() },
        semaphore = Semaphore(CONCURRENCY_LIMIT),
    )

    private val cache = ConcurrentHashMap<String, CacheEntry>()

    suspend fun load(
        spec: HomeRowSpec,
        force: Boolean = false,
    ): Outcome<List<MetaPreview>> {
        if (!force) {
            val cached = cache[spec.key]
            if (cached != null && (clock() - cached.timestampMs) < CACHE_TTL_MS) {
                return Outcome.Success(cached.items)
            }
        }

        return semaphore.withPermit {
            if (!force) {
                val cached = cache[spec.key]
                if (cached != null && (clock() - cached.timestampMs) < CACHE_TTL_MS) {
                    return@withPermit Outcome.Success(cached.items)
                }
            }

            val result =
                withTimeoutOrNull(TIMEOUT_MS) {
                    remote.catalog(
                        transportUrl = spec.transportUrl,
                        type = spec.type,
                        catalogId = spec.catalogId,
                    )
                } ?: return@withPermit Outcome.Failure(AppError.Timeout)

            when (result) {
                is Outcome.Success -> {
                    val items = result.value.items.take(MAX_ITEMS_PER_ROW)
                    cache[spec.key] = CacheEntry(items = items, timestampMs = clock())
                    Outcome.Success(items)
                }

                is Outcome.Failure -> {
                    result
                }
            }
        }
    }

    fun clear(key: String) {
        cache.remove(key)
    }

    fun clearAll() {
        cache.clear()
    }

    private data class CacheEntry(
        val items: List<MetaPreview>,
        val timestampMs: Long,
    )

    companion object {
        const val CACHE_TTL_MS = 5 * 60 * 1000L
        const val TIMEOUT_MS = 8_000L
        const val CONCURRENCY_LIMIT = 4
        const val MAX_ITEMS_PER_ROW = 50
    }
}
