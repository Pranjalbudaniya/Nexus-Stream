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

    private data class AddonCacheKey(
        val transportUrl: String,
        val type: String,
        val videoId: String,
    )

    private data class AddonCacheEntry(
        val items: List<ParsedStream>,
        val timestamp: Long,
    )

    private val addonCache = ConcurrentHashMap<AddonCacheKey, AddonCacheEntry>()

    override fun streams(
        type: String,
        videoId: String,
        forceRefresh: Boolean,
        onlyAddons: Set<String>?,
    ): Flow<StreamsState> =
        channelFlow {
            val matching = resolveMatchingAddons(type, videoId, onlyAddons)
            if (matching.isEmpty()) {
                send(StreamsState(emptyList(), answered = 0, total = 0, failures = emptyList(), done = true))
                return@channelFlow
            }

            executeStreamRequests(
                matching = matching,
                type = type,
                videoId = videoId,
                forceRefresh = forceRefresh,
                onlyAddons = onlyAddons,
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
        forceRefresh: Boolean,
        onlyAddons: Set<String>?,
    ) {
        val familyMode = withContext(ioDispatcher) { contentPrefsRepository.familyMode.first() }
        val total = matching.size
        val semaphore = Semaphore(MAX_PARALLEL_REQUESTS)
        val mutex = Mutex()
        val accumulator = StreamAccumulator(total = total, familyMode = familyMode)

        val cachedItems = mutableListOf<List<ParsedStream>>()
        val toQuery = mutableListOf<Addon>()

        val now = clock()
        for (addon in matching) {
            val shouldBypass = forceRefresh || (onlyAddons != null && addon.transportUrl in onlyAddons)
            val entry = if (!shouldBypass) addonCache[AddonCacheKey(addon.transportUrl, type, videoId)] else null
            if (entry != null && (now - entry.timestamp < CACHE_DURATION_MS)) {
                cachedItems.add(entry.items)
            } else {
                toQuery.add(addon)
            }
        }

        for (items in cachedItems) {
            accumulator.recordCached(items)
        }

        if (toQuery.isEmpty()) {
            send(accumulator.buildState())
            return
        }

        send(accumulator.buildState())

        for (addon in toQuery) {
            launch(ioDispatcher) {
                val result = queryAddonWithTimeout(addon.transportUrl, type, videoId, semaphore)
                val updatedState =
                    mutex.withLock {
                        withContext(defaultDispatcher) {
                            val parsed = accumulator.recordResult(addon.manifest.name, addon.transportUrl, result)
                            if (parsed.isNotEmpty()) {
                                addonCache[AddonCacheKey(addon.transportUrl, type, videoId)] =
                                    AddonCacheEntry(parsed, clock())
                            }
                            accumulator.buildState()
                        }
                    }

                send(updatedState)
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

        fun recordCached(items: List<ParsedStream>) {
            answeredCount++
            val filtered = if (familyMode) items.filter { StreamOrdering.isFamilySafe(it.stream) } else items
            allItems.addAll(filtered)
        }

        fun recordResult(
            addonName: String,
            transportUrl: String,
            result: Outcome<List<com.nexus.stream.domain.model.Stream>>,
        ): List<ParsedStream> {
            answeredCount++
            when (result) {
                is Outcome.Success -> {
                    val parsed =
                        result.value
                            .filter { if (familyMode) StreamOrdering.isFamilySafe(it) else true }
                            .mapNotNull { StreamParsing.parse(it, addonName, transportUrl) }
                    allItems.addAll(parsed)
                    return parsed
                }

                is Outcome.Failure -> {
                    allFailures.add(AddonFailure(addonName, result.error, transportUrl))
                    return emptyList()
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

    companion object {
        private const val ADDON_TIMEOUT_MS = 15_000L
        private const val MAX_PARALLEL_REQUESTS = 4
        private const val CACHE_DURATION_MS = 2 * 60 * 1000L
    }
}
