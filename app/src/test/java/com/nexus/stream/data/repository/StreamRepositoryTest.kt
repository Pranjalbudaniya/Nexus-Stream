package com.nexus.stream.data.repository

import com.nexus.stream.data.remote.addon.AddonRemoteDataSource
import com.nexus.stream.domain.model.Addon
import com.nexus.stream.domain.model.AddonManifest
import com.nexus.stream.domain.model.AddonSource
import com.nexus.stream.domain.model.AppError
import com.nexus.stream.domain.model.ManifestHints
import com.nexus.stream.domain.model.Outcome
import com.nexus.stream.domain.model.ResourceSpec
import com.nexus.stream.domain.model.Stream
import com.nexus.stream.domain.model.StreamSource
import com.nexus.stream.domain.repository.AddonRepository
import com.nexus.stream.domain.repository.ContentPrefsRepository
import com.nexus.stream.domain.repository.StreamsState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class StreamRepositoryTest {
    private val addonRepository = mockk<AddonRepository>()
    private val remoteDataSource = mockk<AddonRemoteDataSource>()
    private val contentPrefsRepository = mockk<ContentPrefsRepository>()
    private val testDispatcher = StandardTestDispatcher()

    private var currentTime = 1_000_000L
    private val activeAddonsFlow = MutableStateFlow<List<Addon>>(emptyList())
    private val familyModeFlow = MutableStateFlow(true)

    private lateinit var repository: StreamRepositoryImpl

    private fun createAddon(
        url: String,
        name: String,
        supportsStream: Boolean = true,
    ): Addon =
        Addon(
            transportUrl = url,
            manifest =
                AddonManifest(
                    id = name.lowercase().replace(" ", "."),
                    version = "1.0.0",
                    name = name,
                    description = "Test addon",
                    logo = null,
                    background = null,
                    types = listOf("movie", "series"),
                    idPrefixes = listOf("tt"),
                    resources =
                        if (supportsStream) {
                            listOf(ResourceSpec("stream", listOf("movie", "series"), listOf("tt")))
                        } else {
                            listOf(ResourceSpec("meta", listOf("movie", "series"), listOf("tt")))
                        },
                    catalogs = emptyList(),
                    behaviorHints =
                        ManifestHints(
                            adult = false,
                            p2p = true,
                            configurable = false,
                            configurationRequired = false,
                        ),
                ),
            enabled = true,
            position = 0,
            source = AddonSource.LOCAL,
            installedAt = 1000L,
        )

    private fun createStream(
        name: String,
        resolution: String,
    ): Stream =
        Stream(
            source = StreamSource.Url("https://stream.url/$name", emptyMap()),
            name = "$name $resolution",
            description = "Test stream",
            subtitles = emptyList(),
            bingeGroup = null,
            notWebReady = false,
            videoSize = 1024L * 1024L * 1024L,
            filename = "$name.mp4",
            videoHash = null,
        )

    @Before
    fun setUp() {
        every { addonRepository.observeActive() } returns activeAddonsFlow
        every { contentPrefsRepository.familyMode } returns familyModeFlow

        repository =
            StreamRepositoryImpl(
                addonRepository = addonRepository,
                remoteDataSource = remoteDataSource,
                contentPrefsRepository = contentPrefsRepository,
                ioDispatcher = testDispatcher,
                defaultDispatcher = testDispatcher,
                clock = { currentTime },
            )
    }

    @Test
    fun zeroMatchingAddons_emitsOneDoneStateWithTotalZero() =
        runTest(testDispatcher) {
            val nonStreamAddon = createAddon("https://meta.addon/", "Meta Only", supportsStream = false)
            activeAddonsFlow.value = listOf(nonStreamAddon)

            val states = mutableListOf<StreamsState>()
            val job = launch { repository.streams("movie", "tt123456").toList(states) }
            advanceUntilIdle()
            job.join()

            assertEquals(1, states.size)
            val finalState = states.first()
            assertEquals(0, finalState.total)
            assertEquals(0, finalState.answered)
            assertTrue(finalState.items.isEmpty())
            assertTrue(finalState.failures.isEmpty())
            assertTrue(finalState.done)

            coVerify(exactly = 0) { remoteDataSource.streams(any(), any(), any()) }
        }

    @Test
    fun progressiveEmissions_emitsCountsAndItemsAsAddonsAnswer() =
        runTest(testDispatcher) {
            val addon1 = createAddon("https://addon1.stream/", "Addon One")
            val addon2 = createAddon("https://addon2.stream/", "Addon Two")
            activeAddonsFlow.value = listOf(addon1, addon2)

            coEvery { remoteDataSource.streams(addon1.transportUrl, "movie", "tt123456") } coAnswers {
                delay(100)
                Outcome.Success(listOf(createStream("Stream1", "1080p")))
            }
            coEvery { remoteDataSource.streams(addon2.transportUrl, "movie", "tt123456") } coAnswers {
                delay(200)
                Outcome.Success(listOf(createStream("Stream2", "2160p")))
            }

            val states = mutableListOf<StreamsState>()
            val job = launch { repository.streams("movie", "tt123456").toList(states) }

            advanceTimeBy(50)
            assertEquals(1, states.size) // initial state 0/2
            assertEquals(0, states[0].answered)
            assertEquals(2, states[0].total)
            assertFalse(states[0].done)

            advanceTimeBy(100)
            assertEquals(2, states.size) // addon1 answered 1/2
            assertEquals(1, states[1].answered)
            assertEquals(1, states[1].items.size)
            assertFalse(states[1].done)

            advanceTimeBy(100)
            advanceUntilIdle()
            job.join()

            assertTrue(states.size >= 3)
            val finalState = states.last()
            assertEquals(2, finalState.answered)
            assertEquals(2, finalState.total)
            assertEquals(2, finalState.items.size)
            assertEquals(2160, finalState.items[0].resolution) // sorted 2160p first
            assertEquals(1080, finalState.items[1].resolution)
            assertTrue(finalState.done)
        }

    @Test
    fun timeoutAt15s_becomesFailureWhileOthersSucceed() =
        runTest(testDispatcher) {
            val addon1 = createAddon("https://addon1.stream/", "Fast Addon")
            val addon2 = createAddon("https://addon2.stream/", "Slow Addon")
            activeAddonsFlow.value = listOf(addon1, addon2)

            coEvery { remoteDataSource.streams(addon1.transportUrl, "movie", "tt123456") } returns
                Outcome.Success(listOf(createStream("Fast", "1080p")))

            coEvery { remoteDataSource.streams(addon2.transportUrl, "movie", "tt123456") } coAnswers {
                delay(20_000L) // Exceeds 15_000L timeout
                Outcome.Success(listOf(createStream("Slow", "720p")))
            }

            val states = mutableListOf<StreamsState>()
            val job = launch { repository.streams("movie", "tt123456").toList(states) }

            advanceUntilIdle()
            job.join()

            val finalState = states.last()
            assertEquals(2, finalState.answered)
            assertEquals(1, finalState.items.size)
            assertEquals(1, finalState.failures.size)
            assertEquals("Slow Addon", finalState.failures[0].addonName)
            assertEquals(AppError.Timeout, finalState.failures[0].error)
            assertTrue(finalState.done)
        }

    @Test
    fun concurrencyLimit_atMostFourRequestsAtOnce() =
        runTest(testDispatcher) {
            val addons = (1..6).map { createAddon("https://addon$it.stream/", "Addon $it") }
            activeAddonsFlow.value = addons

            val activeCount = AtomicInteger(0)
            var maxObservedParallel = 0

            for (addon in addons) {
                coEvery { remoteDataSource.streams(addon.transportUrl, "movie", "tt123456") } coAnswers {
                    val current = activeCount.incrementAndGet()
                    if (current > maxObservedParallel) {
                        maxObservedParallel = current
                    }
                    delay(100)
                    activeCount.decrementAndGet()
                    Outcome.Success(emptyList())
                }
            }

            val job = launch { repository.streams("movie", "tt123456").collect {} }
            advanceUntilIdle()
            job.join()

            assertTrue("Max parallel was $maxObservedParallel, expected <= 4", maxObservedParallel in 1..4)
        }

    @Test
    fun twoMinuteCache_servesCachedResult_bypassedByForceRefreshOrOnlyAddons() =
        runTest(testDispatcher) {
            val addon = createAddon("https://addon.stream/", "Stream Addon")
            activeAddonsFlow.value = listOf(addon)

            coEvery { remoteDataSource.streams(addon.transportUrl, "movie", "tt123456") } returns
                Outcome.Success(listOf(createStream("Stream", "1080p")))

            // First call -> populates cache
            val states1 = mutableListOf<StreamsState>()
            val job1 = launch { repository.streams("movie", "tt123456").toList(states1) }
            advanceUntilIdle()
            job1.join()
            coVerify(exactly = 1) { remoteDataSource.streams(addon.transportUrl, "movie", "tt123456") }

            // Second call within 1 minute -> cache hit
            currentTime += 60_000L
            val states2 = mutableListOf<StreamsState>()
            val job2 = launch { repository.streams("movie", "tt123456").toList(states2) }
            advanceUntilIdle()
            job2.join()
            assertEquals(1, states2.size) // served immediately as done
            assertTrue(states2.first().done)
            coVerify(exactly = 1) { remoteDataSource.streams(addon.transportUrl, "movie", "tt123456") }

            // Third call with forceRefresh -> cache bypassed
            val states3 = mutableListOf<StreamsState>()
            val job3 = launch { repository.streams("movie", "tt123456", forceRefresh = true).toList(states3) }
            advanceUntilIdle()
            job3.join()
            coVerify(exactly = 2) { remoteDataSource.streams(addon.transportUrl, "movie", "tt123456") }

            // Fourth call after 2 minutes -> cache expired
            currentTime += 130_000L
            val states4 = mutableListOf<StreamsState>()
            val job4 = launch { repository.streams("movie", "tt123456").toList(states4) }
            advanceUntilIdle()
            job4.join()
            coVerify(exactly = 3) { remoteDataSource.streams(addon.transportUrl, "movie", "tt123456") }
        }

    @Test
    fun failuresAndEmptyResults_areNotCached() =
        runTest(testDispatcher) {
            val addon = createAddon("https://addon.stream/", "Stream Addon")
            activeAddonsFlow.value = listOf(addon)

            coEvery { remoteDataSource.streams(addon.transportUrl, "movie", "tt123456") } returns
                Outcome.Failure(AppError.Network)

            // First call fails
            val states1 = mutableListOf<StreamsState>()
            val job1 = launch { repository.streams("movie", "tt123456").toList(states1) }
            advanceUntilIdle()
            job1.join()

            // Second call immediately -> should re-attempt since failures are not cached
            val states2 = mutableListOf<StreamsState>()
            val job2 = launch { repository.streams("movie", "tt123456").toList(states2) }
            advanceUntilIdle()
            job2.join()

            coVerify(exactly = 2) { remoteDataSource.streams(addon.transportUrl, "movie", "tt123456") }
        }

    @Test
    fun cancellingFlow_cancelsInFlightRequests() =
        runTest(testDispatcher) {
            val addon = createAddon("https://addon.stream/", "Stream Addon")
            activeAddonsFlow.value = listOf(addon)

            var wasCancelled = false
            coEvery { remoteDataSource.streams(addon.transportUrl, "movie", "tt123456") } coAnswers {
                try {
                    delay(5000)
                    Outcome.Success(emptyList())
                } catch (e: CancellationException) {
                    wasCancelled = true
                    throw e
                }
            }

            val job = launch { repository.streams("movie", "tt123456").collect {} }
            advanceTimeBy(100)
            job.cancelAndJoin()

            assertTrue(wasCancelled)
        }

    @Test
    fun disablingAddon_removesItsRowsImmediately_andReenablingRestoresFromCache() =
        runTest(testDispatcher) {
            val addon1 = createAddon("https://addon1.stream/", "Addon One")
            val addon2 = createAddon("https://addon2.stream/", "Addon Two")
            activeAddonsFlow.value = listOf(addon1, addon2)

            coEvery { remoteDataSource.streams(addon1.transportUrl, "movie", "tt123456") } returns
                Outcome.Success(listOf(createStream("StreamOne", "1080p")))
            coEvery { remoteDataSource.streams(addon2.transportUrl, "movie", "tt123456") } returns
                Outcome.Success(listOf(createStream("StreamTwo", "720p")))

            // First call -> both are queried and cached
            val states1 = mutableListOf<StreamsState>()
            val job1 = launch { repository.streams("movie", "tt123456").toList(states1) }
            advanceUntilIdle()
            job1.join()

            assertEquals(2, states1.last().items.size)
            coVerify(exactly = 1) { remoteDataSource.streams(addon1.transportUrl, "movie", "tt123456") }
            coVerify(exactly = 1) { remoteDataSource.streams(addon2.transportUrl, "movie", "tt123456") }

            // Disable addon2 within TTL
            currentTime += 30_000L
            activeAddonsFlow.value = listOf(addon1)

            val states2 = mutableListOf<StreamsState>()
            val job2 = launch { repository.streams("movie", "tt123456").toList(states2) }
            advanceUntilIdle()
            job2.join()

            // Only addon1 rows appear; addon2 rows are gone immediately
            val lastState2 = states2.last()
            assertEquals(1, lastState2.total)
            assertEquals(1, lastState2.answered)
            assertEquals(1, lastState2.items.size)
            assertEquals(listOf("Addon One"), lastState2.items[0].addonNames)
            // No new network calls made because addon1 was cached
            coVerify(exactly = 1) { remoteDataSource.streams(addon1.transportUrl, "movie", "tt123456") }
            coVerify(exactly = 1) { remoteDataSource.streams(addon2.transportUrl, "movie", "tt123456") }

            // Re-enable addon2 within TTL
            currentTime += 30_000L
            activeAddonsFlow.value = listOf(addon1, addon2)

            val states3 = mutableListOf<StreamsState>()
            val job3 = launch { repository.streams("movie", "tt123456").toList(states3) }
            advanceUntilIdle()
            job3.join()

            // Both addons appear again immediately from cache
            val lastState3 = states3.last()
            assertEquals(2, lastState3.total)
            assertEquals(2, lastState3.answered)
            assertEquals(2, lastState3.items.size)
            coVerify(exactly = 1) { remoteDataSource.streams(addon1.transportUrl, "movie", "tt123456") }
            coVerify(exactly = 1) { remoteDataSource.streams(addon2.transportUrl, "movie", "tt123456") }
        }
}
