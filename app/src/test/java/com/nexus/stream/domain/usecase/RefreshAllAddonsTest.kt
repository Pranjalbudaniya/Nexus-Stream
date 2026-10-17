package com.nexus.stream.domain.usecase

import com.nexus.stream.domain.model.Addon
import com.nexus.stream.domain.model.AddonManifest
import com.nexus.stream.domain.model.AddonSource
import com.nexus.stream.domain.model.AppError
import com.nexus.stream.domain.model.ManifestHints
import com.nexus.stream.domain.model.Outcome
import com.nexus.stream.domain.repository.AddonRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class RefreshAllAddonsTest {
    private val addonRepository: AddonRepository = mockk()
    private val refreshAllAddons = RefreshAllAddons(addonRepository)

    private fun fakeInstalled(
        id: String,
        name: String,
        url: String,
        isEnabled: Boolean = true,
    ) = Addon(
        transportUrl = url,
        manifest =
            AddonManifest(
                id = id,
                version = "1.0.0",
                name = name,
                description = "desc",
                logo = null,
                background = null,
                types = listOf("movie"),
                idPrefixes = null,
                resources = emptyList(),
                catalogs = emptyList(),
                behaviorHints =
                    ManifestHints(
                        adult = false,
                        p2p = false,
                        configurable = false,
                        configurationRequired = false,
                    ),
            ),
        enabled = isEnabled,
        position = 0,
        source = AddonSource.LOCAL,
        installedAt = 0L,
    )

    private val dummyAddon = fakeInstalled("dummy", "Dummy", "https://dummy.com/manifest.json")

    @Test
    fun `all succeed returns total updated and empty failed list`() =
        runTest {
            val addons =
                listOf(
                    fakeInstalled("a", "Addon A", "https://a.com/manifest.json"),
                    fakeInstalled("b", "Addon B", "https://b.com/manifest.json"),
                )
            coEvery { addonRepository.observeAll() } returns MutableStateFlow(addons).asStateFlow()
            coEvery { addonRepository.refresh("https://a.com/manifest.json") } returns Outcome.Success(dummyAddon)
            coEvery { addonRepository.refresh("https://b.com/manifest.json") } returns Outcome.Success(dummyAddon)

            val summary = refreshAllAddons()
            assertEquals(2, summary.updated)
            assertTrue(summary.failed.isEmpty())
        }

    @Test
    fun `some fail lists failed names and updates others`() =
        runTest {
            val addons =
                listOf(
                    fakeInstalled("a", "Addon A", "https://a.com/manifest.json"),
                    fakeInstalled("b", "Addon B", "https://b.com/manifest.json"),
                    fakeInstalled("c", "Addon C", "https://c.com/manifest.json"),
                )
            coEvery { addonRepository.observeAll() } returns MutableStateFlow(addons).asStateFlow()
            coEvery { addonRepository.refresh("https://a.com/manifest.json") } returns Outcome.Success(dummyAddon)
            coEvery { addonRepository.refresh("https://b.com/manifest.json") } returns Outcome.Failure(AppError.Network)
            coEvery { addonRepository.refresh("https://c.com/manifest.json") } returns Outcome.Success(dummyAddon)

            val summary = refreshAllAddons()
            assertEquals(2, summary.updated)
            assertEquals(listOf("Addon B"), summary.failed)
        }

    @Test
    fun `disabled addons are included in refresh`() =
        runTest {
            val addons =
                listOf(
                    fakeInstalled("a", "Disabled Addon", "https://a.com/manifest.json", isEnabled = false),
                )
            coEvery { addonRepository.observeAll() } returns MutableStateFlow(addons).asStateFlow()
            coEvery { addonRepository.refresh("https://a.com/manifest.json") } returns Outcome.Success(dummyAddon)

            val summary = refreshAllAddons()
            assertEquals(1, summary.updated)
            assertTrue(summary.failed.isEmpty())
        }

    @Test
    fun `progress counts emit monotonically`() =
        runTest {
            val addons =
                listOf(
                    fakeInstalled("a", "Addon A", "https://a.com/manifest.json"),
                    fakeInstalled("b", "Addon B", "https://b.com/manifest.json"),
                )
            coEvery { addonRepository.observeAll() } returns MutableStateFlow(addons).asStateFlow()
            coEvery { addonRepository.refresh(any()) } returns Outcome.Success(dummyAddon)

            val progressCalls = mutableListOf<Pair<Int, Int>>()
            refreshAllAddons { done, total ->
                progressCalls.add(done to total)
            }

            assertEquals(2, progressCalls.size)
            assertEquals(1 to 2, progressCalls[0])
            assertEquals(2 to 2, progressCalls[1])
        }

    @Test
    fun `at most 3 run at once`() =
        runTest {
            val addons = (1..6).map { fakeInstalled("id$it", "Addon $it", "https://$it.com/manifest.json") }
            coEvery { addonRepository.observeAll() } returns MutableStateFlow(addons).asStateFlow()

            val activeCount = AtomicInteger(0)
            var maxConcurrent = 0
            val lock = Any()

            coEvery { addonRepository.refresh(any()) } coAnswers {
                val current = activeCount.incrementAndGet()
                synchronized(lock) {
                    if (current > maxConcurrent) maxConcurrent = current
                }
                delay(50)
                activeCount.decrementAndGet()
                Outcome.Success(dummyAddon)
            }

            refreshAllAddons()
            assertTrue("Expected maxConcurrent <= 3 but was $maxConcurrent", maxConcurrent <= 3)
            assertEquals(3, maxConcurrent)
        }

    @Test
    fun `second call while running returns at once`() =
        runTest {
            val testDispatcher = StandardTestDispatcher(testScheduler)
            val addons = listOf(fakeInstalled("a", "Addon A", "https://a.com/manifest.json"))
            coEvery { addonRepository.observeAll() } returns MutableStateFlow(addons).asStateFlow()

            val barrier = CompletableDeferred<Unit>()
            coEvery { addonRepository.refresh(any()) } coAnswers {
                barrier.await()
                Outcome.Success(dummyAddon)
            }

            val job1 =
                async(testDispatcher) {
                    refreshAllAddons()
                }

            // Step scheduler so job1 acquires the lock and suspends on barrier
            testScheduler.advanceTimeBy(10)

            val job2 =
                async(testDispatcher) {
                    refreshAllAddons()
                }

            testScheduler.advanceTimeBy(10)
            assertTrue(job2.isCompleted)
            val summary2 = job2.await()
            assertEquals(0, summary2.updated)
            assertTrue(summary2.failed.isEmpty())

            // Now release barrier for job1
            barrier.complete(Unit)
            testScheduler.advanceUntilIdle()
            val summary1 = job1.await()
            assertEquals(1, summary1.updated)
        }

    @Test
    fun `cancellation leaves it ready for a new run`() =
        runTest {
            val testDispatcher = StandardTestDispatcher(testScheduler)
            val addons = listOf(fakeInstalled("a", "Addon A", "https://a.com/manifest.json"))
            coEvery { addonRepository.observeAll() } returns MutableStateFlow(addons).asStateFlow()

            val barrier = CompletableDeferred<Unit>()
            coEvery { addonRepository.refresh(any()) } coAnswers {
                barrier.await()
                Outcome.Success(dummyAddon)
            }

            val job1 =
                launch(testDispatcher) {
                    refreshAllAddons()
                }

            testScheduler.advanceTimeBy(10)
            job1.cancel()
            testScheduler.advanceUntilIdle()

            // New run should succeed immediately
            coEvery { addonRepository.refresh(any()) } returns Outcome.Success(dummyAddon)
            val job2 =
                async(testDispatcher) {
                    refreshAllAddons()
                }
            testScheduler.advanceUntilIdle()
            val summary2 = job2.await()
            assertEquals(1, summary2.updated)
        }
}
