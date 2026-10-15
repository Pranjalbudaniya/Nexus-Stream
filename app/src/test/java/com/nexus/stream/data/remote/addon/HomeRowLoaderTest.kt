package com.nexus.stream.data.remote.addon

import com.nexus.stream.domain.model.AppError
import com.nexus.stream.domain.model.HomeRowSpec
import com.nexus.stream.domain.model.MetaPreview
import com.nexus.stream.domain.model.Outcome
import com.nexus.stream.domain.model.PosterShape
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class HomeRowLoaderTest {
    private val remote: AddonRemoteDataSource = mockk()
    private var currentTime = 1_000_000L
    private lateinit var loader: HomeRowLoader

    private val testSpec =
        HomeRowSpec(
            key = "https://example.com|movie|top",
            title = "Popular - Movies",
            transportUrl = "https://example.com",
            type = "movie",
            catalogId = "top",
        )

    @Before
    fun setUp() {
        currentTime = 1_000_000L
        loader =
            HomeRowLoader(
                remote = remote,
                clock = { currentTime },
                semaphore = Semaphore(4),
            )
    }

    @Test
    fun returnsAtMost50Items() =
        runTest {
            val items =
                (1..60).map { index ->
                    createMetaPreview(id = "item_$index", name = "Title $index")
                }
            coEvery {
                remote.catalog(
                    transportUrl = any(),
                    type = any(),
                    catalogId = any(),
                    extra = any(),
                )
            } returns Outcome.Success(CatalogPage(items = items, skipped = 0))

            val outcome = loader.load(testSpec)

            assertTrue(outcome is Outcome.Success)
            assertEquals(50, (outcome as Outcome.Success).value.size)
        }

    @Test
    fun remoteDelays9Seconds_givesTimeout() =
        runTest {
            coEvery {
                remote.catalog(
                    transportUrl = any(),
                    type = any(),
                    catalogId = any(),
                    extra = any(),
                )
            } coAnswers {
                delay(9_000)
                Outcome.Success(CatalogPage(items = emptyList(), skipped = 0))
            }

            val outcome = loader.load(testSpec)

            assertTrue(outcome is Outcome.Failure)
            assertEquals(AppError.Timeout, (outcome as Outcome.Failure).error)
        }

    @Test
    fun secondCallWithin5Minutes_makesNoRemoteCall() =
        runTest {
            val items = listOf(createMetaPreview("item_1", "Item 1"))
            coEvery {
                remote.catalog(
                    transportUrl = any(),
                    type = any(),
                    catalogId = any(),
                    extra = any(),
                )
            } returns Outcome.Success(CatalogPage(items = items, skipped = 0))

            val result1 = loader.load(testSpec)
            assertTrue(result1 is Outcome.Success)

            currentTime += 4 * 60 * 1000L // 4 minutes later

            val result2 = loader.load(testSpec)
            assertTrue(result2 is Outcome.Success)

            coVerify(exactly = 1) {
                remote.catalog(
                    transportUrl = any(),
                    type = any(),
                    catalogId = any(),
                    extra = any(),
                )
            }
        }

    @Test
    fun forceTrue_bypassesCacheAndCallsAgain() =
        runTest {
            val items = listOf(createMetaPreview("item_1", "Item 1"))
            coEvery {
                remote.catalog(
                    transportUrl = any(),
                    type = any(),
                    catalogId = any(),
                    extra = any(),
                )
            } returns Outcome.Success(CatalogPage(items = items, skipped = 0))

            loader.load(testSpec)
            loader.load(testSpec, force = true)

            coVerify(exactly = 2) {
                remote.catalog(
                    transportUrl = any(),
                    type = any(),
                    catalogId = any(),
                    extra = any(),
                )
            }
        }

    @Test
    fun failureIsNotCached() =
        runTest {
            coEvery {
                remote.catalog(
                    transportUrl = any(),
                    type = any(),
                    catalogId = any(),
                    extra = any(),
                )
            } returns Outcome.Failure(AppError.Network) andThen
                Outcome.Success(
                    CatalogPage(
                        items = listOf(createMetaPreview("id1", "Name")),
                        skipped = 0,
                    ),
                )

            val result1 = loader.load(testSpec)
            assertTrue(result1 is Outcome.Failure)

            val result2 = loader.load(testSpec)
            assertTrue(result2 is Outcome.Success)

            coVerify(exactly = 2) {
                remote.catalog(
                    transportUrl = any(),
                    type = any(),
                    catalogId = any(),
                    extra = any(),
                )
            }
        }

    @Test
    fun afterTtlExpired_callsRemoteAgain() =
        runTest {
            val items = listOf(createMetaPreview("item_1", "Item 1"))
            coEvery {
                remote.catalog(
                    transportUrl = any(),
                    type = any(),
                    catalogId = any(),
                    extra = any(),
                )
            } returns Outcome.Success(CatalogPage(items = items, skipped = 0))

            loader.load(testSpec)

            currentTime += 5 * 60 * 1000L + 1L // TTL expired

            loader.load(testSpec)

            coVerify(exactly = 2) {
                remote.catalog(
                    transportUrl = any(),
                    type = any(),
                    catalogId = any(),
                    extra = any(),
                )
            }
        }

    @Test
    fun cancellationIsRethrown() =
        runTest {
            coEvery {
                remote.catalog(
                    transportUrl = any(),
                    type = any(),
                    catalogId = any(),
                    extra = any(),
                )
            } throws CancellationException("Explicit cancel")

            try {
                loader.load(testSpec)
                fail("Expected CancellationException")
            } catch (e: CancellationException) {
                assertEquals("Explicit cancel", e.message)
            }
        }

    @Test
    fun concurrencyLimit_neverMoreThan4RunAtOnce() =
        runTest {
            val runningCount = AtomicInteger(0)
            val maxObservedRunning = AtomicInteger(0)

            coEvery {
                remote.catalog(
                    transportUrl = any(),
                    type = any(),
                    catalogId = any(),
                    extra = any(),
                )
            } coAnswers {
                val current = runningCount.incrementAndGet()
                maxObservedRunning.updateAndGet { prev -> maxOf(prev, current) }
                delay(1_000)
                runningCount.decrementAndGet()
                Outcome.Success(CatalogPage(items = emptyList(), skipped = 0))
            }

            val deferreds =
                (1..10).map { index ->
                    async {
                        val spec =
                            HomeRowSpec(
                                key = "key_$index",
                                title = "Title $index",
                                transportUrl = "url_$index",
                                type = "movie",
                                catalogId = "cat_$index",
                            )
                        loader.load(spec)
                    }
                }

            deferreds.awaitAll()

            assertTrue(
                "Expected max running <= 4 but observed ${maxObservedRunning.get()}",
                maxObservedRunning.get() <= 4,
            )
            assertTrue(
                "Expected at least concurrency > 1 but observed ${maxObservedRunning.get()}",
                maxObservedRunning.get() > 1,
            )
        }

    private fun createMetaPreview(
        id: String,
        name: String,
    ): MetaPreview =
        MetaPreview(
            id = id,
            type = "movie",
            name = name,
            poster = null,
            posterShape = PosterShape.POSTER,
            background = null,
            logo = null,
            description = null,
            releaseInfo = null,
            imdbRating = null,
            genres = emptyList(),
        )
}
