package com.nexus.stream.data.remote.addon

import com.nexus.stream.domain.model.AppError
import com.nexus.stream.domain.model.DiscoverCatalog
import com.nexus.stream.domain.model.MetaPreview
import com.nexus.stream.domain.model.Outcome
import com.nexus.stream.domain.model.PosterShape
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DiscoverPageLoaderTest {
    private val remote: AddonRemoteDataSource = mockk()
    private lateinit var loader: DiscoverPageLoader

    private val testCatalog =
        DiscoverCatalog(
            key = "https://example.com/manifest.json|movie|top",
            type = "movie",
            catalogId = "top",
            transportUrl = "https://example.com/manifest.json",
            title = "Top",
            genre = null,
            supportsSkip = true,
        )

    @Before
    fun setUp() {
        loader = DiscoverPageLoader(remote = remote)
    }

    @Test
    fun DL1_success_returnsItems_andForwardsTheExtra() =
        runTest {
            val items = (1..3).map { createMeta("id_$it") }
            val extra = mapOf("genre" to "A", "skip" to "100")
            coEvery {
                remote.catalog(
                    transportUrl = testCatalog.transportUrl,
                    type = testCatalog.type,
                    catalogId = testCatalog.catalogId,
                    extra = extra,
                )
            } returns Outcome.Success(CatalogPage(items = items, skipped = 0))

            val result = loader.load(testCatalog, extra)

            assertTrue(result is Outcome.Success)
            assertEquals(items, (result as Outcome.Success).value)
            coVerify(exactly = 1) {
                remote.catalog(
                    transportUrl = testCatalog.transportUrl,
                    type = testCatalog.type,
                    catalogId = testCatalog.catalogId,
                    extra = extra,
                )
            }
        }

    @Test
    fun DL2_slowRemote_timesOutAfterEightSeconds() =
        runTest {
            coEvery {
                remote.catalog(
                    transportUrl = any(),
                    type = any(),
                    catalogId = any(),
                    extra = any(),
                )
            } coAnswers {
                delay(10_000)
                Outcome.Success(CatalogPage(emptyList(), 0))
            }

            val deferred = async { loader.load(testCatalog, emptyMap()) }
            advanceTimeBy(8_000)
            val result = deferred.await()

            assertTrue(result is Outcome.Failure)
            assertEquals(AppError.Timeout, (result as Outcome.Failure).error)
        }

    @Test
    fun DL3_remoteFailure_isPassedThrough() =
        runTest {
            coEvery {
                remote.catalog(
                    transportUrl = any(),
                    type = any(),
                    catalogId = any(),
                    extra = any(),
                )
            } returns Outcome.Failure(AppError.Network)

            val result = loader.load(testCatalog, emptyMap())

            assertTrue(result is Outcome.Failure)
            assertEquals(AppError.Network, (result as Outcome.Failure).error)
        }

    @Test
    fun DL4_cancellation_isNotSwallowed() =
        runTest {
            coEvery {
                remote.catalog(
                    transportUrl = any(),
                    type = any(),
                    catalogId = any(),
                    extra = any(),
                )
            } coAnswers {
                delay(2_000)
                Outcome.Success(CatalogPage(emptyList(), 0))
            }

            val job = async { loader.load(testCatalog, emptyMap()) }
            advanceTimeBy(500)
            job.cancel(CancellationException("Cancelled"))

            try {
                job.await()
                fail("Expected CancellationException")
            } catch (_: CancellationException) {
                // Success
            }
        }

    @Test
    fun DL5_pageIsNotTruncated() =
        runTest {
            val items = (1..120).map { createMeta("id_$it") }
            coEvery {
                remote.catalog(
                    transportUrl = any(),
                    type = any(),
                    catalogId = any(),
                    extra = any(),
                )
            } returns Outcome.Success(CatalogPage(items = items, skipped = 0))

            val result = loader.load(testCatalog, emptyMap())

            assertTrue(result is Outcome.Success)
            assertEquals(120, (result as Outcome.Success).value.size)
        }

    private fun createMeta(id: String): MetaPreview =
        MetaPreview(
            id = id,
            type = "movie",
            name = "Title $id",
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
