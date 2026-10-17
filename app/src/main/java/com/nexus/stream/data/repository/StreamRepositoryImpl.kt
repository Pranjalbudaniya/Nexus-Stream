package com.nexus.stream.data.repository

import com.nexus.stream.data.remote.addon.AddonRemoteDataSource
import com.nexus.stream.di.DefaultDispatcher
import com.nexus.stream.di.IoDispatcher
import com.nexus.stream.domain.model.Addon
import com.nexus.stream.domain.model.AppError
import com.nexus.stream.domain.model.Outcome
import com.nexus.stream.domain.model.ParsedStream
import com.nexus.stream.domain.model.supports
import com.nexus.stream.domain.repository.AddonFailure
import com.nexus.stream.domain.repository.AddonRepository
import com.nexus.stream.domain.repository.ContentPrefsRepository
import com.nexus.stream.domain.repository.StreamRepository
import com.nexus.stream.domain.repository.StreamsState
import com.nexus.stream.domain.usecase.StreamOrdering
import com.nexus.stream.domain.usecase.StreamParsing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StreamRepositoryImpl(
    private val addonRepository: AddonRepository,
    private val remoteDataSource: AddonRemoteDataSource,
    private val contentPrefsRepository: ContentPrefsRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    private val clock: () -> Long = System::currentTimeMillis,
) : StreamRepository {
    @Inject
    constructor(
        addonRepository: AddonRepository,
        remoteDataSource: AddonRemoteDataSource,
        contentPrefsRepository: ContentPrefsRepository,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
        @DefaultDispatcher defaultDispatcher: CoroutineDispatcher,
    ) : this(
        addonRepository = addonRepository,
        remoteDataSource = remoteDataSource,
        contentPrefsRepository = contentPrefsRepository,
        ioDispatcher = ioDispatcher,
        defaultDispatcher = defaultDispatcher,
        clock = System::currentTimeMillis,
    )

    private val cache = ConcurrentHashMap<String, CacheEntry>()

    override fun streams(
        type: String,
        videoId: String,
        forceRefresh: Boolean,
        onlyAddons: Set<String>?,
    ): Flow<StreamsState> =
        channelFlow {
            val cacheKey = "$type:$videoId"
            if (!forceRefresh && onlyAddons == null) {
                val cached = cache[cacheKey]
                if (cached != null && (clock() - cached.timestamp < CACHE_DURATION_MS)) {
                    send(cached.state)
                    return@channelFlow
                }
            }

            val matching = resolveMatchingAddons(type, videoId, onlyAddons)
            if (matching.isEmpty()) {
                send(StreamsState(emptyList(), answered = 0, total = 0, failures = emptyList(), done = true))
                return@channelFlow
            }

            executeStreamRequests(
                matching = matching,
                type = type,
                videoId = videoId,
                cacheKey = cacheKey,
            )
        }

    private suspend fun resolveMatchingAddons(
        type: String,
        videoId: String,
        onlyAddons: Set<String>?,
    ): List<Addon> {
        val activeAddons = withContext(ioDispatcher) { addonRepository.observeActive().first() }
        return activeAddons.filter { addon ->
            addon.manifest.supports("stream", type, videoId) &&
                (onlyAddons == null || addon.transportUrl in onlyAddons)
        }
    }

    private suspend fun ProducerScope<StreamsState>.executeStreamRequests(
        matching: List<Addon>,
        type: String,
        videoId: String,
        cacheKey: String,
    ) {
        val familyMode = withContext(ioDispatcher) { contentPrefsRepository.familyMode.first() }
        val total = matching.size
        val semaphore = Semaphore(MAX_PARALLEL_REQUESTS)
        val mutex = Mutex()
        val accumulator = StreamAccumulator(total = total, familyMode = familyMode)

        send(StreamsState(emptyList(), answered = 0, total = total, failures = emptyList(), done = false))

        for (addon in matching) {
            launch(ioDispatcher) {
                val result = queryAddonWithTimeout(addon.transportUrl, type, videoId, semaphore)
                val updatedState =
                    mutex.withLock {
                        withContext(defaultDispatcher) {
                            accumulator.recordResult(addon.manifest.name, addon.transportUrl, result)
                            accumulator.buildState()
                        }
                    }

                send(updatedState)
                if (updatedState.done && updatedState.items.isNotEmpty()) {
                    cache[cacheKey] = CacheEntry(updatedState, clock())
                }
            }
        }
    }

    private suspend fun queryAddonWithTimeout(
        transportUrl: String,
        type: String,
        videoId: String,
        semaphore: Semaphore,
    ): Outcome<List<com.nexus.stream.domain.model.Stream>> =
        semaphore.withPermit {
            withTimeoutOrNull(ADDON_TIMEOUT_MS) {
                remoteDataSource.streams(transportUrl, type, videoId)
            } ?: Outcome.Failure(AppError.Timeout)
        }

    private class StreamAccumulator(
        private val total: Int,
        private val familyMode: Boolean,
    ) {
        private var answeredCount = 0
        private val allItems = mutableListOf<ParsedStream>()
        private val allFailures = mutableListOf<AddonFailure>()

        fun recordResult(
            addonName: String,
            transportUrl: String,
            result: Outcome<List<com.nexus.stream.domain.model.Stream>>,
        ) {
            answeredCount++
            when (result) {
                is Outcome.Success -> {
                    val parsed =
                        result.value
                            .filter { if (familyMode) StreamOrdering.isFamilySafe(it) else true }
                            .mapNotNull { StreamParsing.parse(it, addonName) }
                    allItems.addAll(parsed)
                }

                is Outcome.Failure -> {
                    allFailures.add(AddonFailure(addonName, result.error, transportUrl))
                }
            }
        }

        fun buildState(): StreamsState {
            val dedupedSorted = StreamOrdering.sort(StreamOrdering.dedupe(allItems))
            return StreamsState(
                items = dedupedSorted,
                answered = answeredCount,
                total = total,
                failures = allFailures.toList(),
                done = answeredCount == total,
            )
        }
    }

    private data class CacheEntry(
        val state: StreamsState,
        val timestamp: Long,
    )

    companion object {
        private const val ADDON_TIMEOUT_MS = 15_000L
        private const val MAX_PARALLEL_REQUESTS = 4
        private const val CACHE_DURATION_MS = 2 * 60 * 1000L
    }
}
