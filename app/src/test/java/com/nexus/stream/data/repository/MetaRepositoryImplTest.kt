package com.nexus.stream.data.repository

import com.nexus.stream.data.local.db.entity.MetaCacheEntity
import com.nexus.stream.data.remote.addon.AddonRemoteDataSource
import com.nexus.stream.data.remote.addon.FetchedMeta
import com.nexus.stream.domain.model.Addon
import com.nexus.stream.domain.model.AddonManifest
import com.nexus.stream.domain.model.AddonSource
import com.nexus.stream.domain.model.AppError
import com.nexus.stream.domain.model.ManifestHints
import com.nexus.stream.domain.model.MetaDetail
import com.nexus.stream.domain.model.Outcome
import com.nexus.stream.domain.model.PosterShape
import com.nexus.stream.domain.model.ResourceSpec
import com.nexus.stream.domain.repository.AddonRepository
import com.nexus.stream.domain.repository.MetaSource
import com.nexus.stream.testutil.FakeMetaCacheDao
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MetaRepositoryImplTest {
    private val remoteDataSource: AddonRemoteDataSource = mockk()
    private val addonRepository: AddonRepository = mockk()
    private val fakeDao = FakeMetaCacheDao()
    private val testDispatcher = UnconfinedTestDispatcher()
    private val currentTime = 1_000_000L

    private val repository =
        MetaRepositoryImpl(
            remoteDataSource = remoteDataSource,
            addonRepository = addonRepository,
            metaCacheDao = fakeDao,
            dispatcher = testDispatcher,
            clock = { currentTime },
        )

    private val rawJson =
        """{"meta":{"id":"tt0111161","type":"movie","name":"The Shawshank Redemption"}}"""

    private val testDetail =
        MetaDetail(
            id = "tt0111161",
            type = "movie",
            name = "The Shawshank Redemption",
            poster = null,
            posterShape = PosterShape.POSTER,
            background = null,
            logo = null,
            description = null,
            releaseInfo = null,
            released = null,
            runtime = null,
            imdbRating = null,
            genres = emptyList(),
            cast = emptyList(),
            directors = emptyList(),
            country = null,
            language = null,
            website = null,
            links = emptyList(),
            trailers = emptyList(),
            videos = emptyList(),
            defaultVideoId = null,
        )

    private fun testAddon(
        url: String = "https://addon.com/manifest.json",
        position: Int = 0,
    ) = Addon(
        transportUrl = url,
        manifest =
            AddonManifest(
                id = "addon_$position",
                version = "1.0.0",
                name = "Addon $position",
                description = "Desc",
                logo = null,
                background = null,
                types = listOf("movie", "series"),
                idPrefixes = listOf("tt"),
                resources =
                    listOf(
                        ResourceSpec(
                            name = "meta",
                            types = listOf("movie", "series"),
                            idPrefixes = listOf("tt"),
                        ),
                    ),
                catalogs = emptyList(),
                behaviorHints =
                    ManifestHints(
                        adult = false,
                        p2p = false,
                        configurable = false,
                        configurationRequired = false,
                    ),
            ),
        enabled = true,
        position = position,
        source = AddonSource.LOCAL,
        installedAt = 0L,
    )

    @Test
    fun `fresh cache returns CACHE with no remote call`() =
        runTest(testDispatcher) {
            fakeDao.upsert(
                MetaCacheEntity(
                    metaId = "movie:tt0111161",
                    json = rawJson,
                    fetchedAt = currentTime - 3600_000L, // 1 hour ago (< 24h)
                ),
            )

            val outcome = repository.getMeta(type = "movie", id = "tt0111161")
            assertTrue(outcome is Outcome.Success)
            val result = (outcome as Outcome.Success).value
            assertEquals(MetaSource.CACHE, result.source)
            assertEquals("tt0111161", result.meta.id)
            assertEquals("The Shawshank Redemption", result.meta.name)

            coVerify(exactly = 0) { remoteDataSource.metaWithRaw(any(), any(), any()) }
        }

    @Test
    fun `forceRefresh bypasses fresh cache`() =
        runTest(testDispatcher) {
            fakeDao.upsert(
                MetaCacheEntity(
                    metaId = "movie:tt0111161",
                    json = rawJson,
                    fetchedAt = currentTime - 3600_000L,
                ),
            )
            coEvery { addonRepository.observeActive() } returns flowOf(listOf(testAddon()))
            coEvery { remoteDataSource.metaWithRaw("https://addon.com/manifest.json", "movie", "tt0111161") } returns
                Outcome.Success(FetchedMeta(meta = testDetail.copy(name = "Updated"), rawJson = rawJson))

            val outcome = repository.getMeta(type = "movie", id = "tt0111161", forceRefresh = true)
            assertTrue(outcome is Outcome.Success)
            val result = (outcome as Outcome.Success).value
            assertEquals(MetaSource.FRESH, result.source)
            assertEquals("Updated", result.meta.name)

            coVerify(exactly = 1) {
                remoteDataSource.metaWithRaw("https://addon.com/manifest.json", "movie", "tt0111161")
            }
        }

    @Test
    fun `expired cache fetches and returns FRESH and rewrites row and trims`() =
        runTest(testDispatcher) {
            fakeDao.upsert(
                MetaCacheEntity(
                    metaId = "movie:tt0111161",
                    json = rawJson,
                    fetchedAt = currentTime - 90_000_000L, // > 24 hours ago
                ),
            )
            coEvery { addonRepository.observeActive() } returns flowOf(listOf(testAddon()))
            coEvery { remoteDataSource.metaWithRaw("https://addon.com/manifest.json", "movie", "tt0111161") } returns
                Outcome.Success(FetchedMeta(meta = testDetail, rawJson = rawJson))

            val outcome = repository.getMeta(type = "movie", id = "tt0111161")
            assertTrue(outcome is Outcome.Success)
            val result = (outcome as Outcome.Success).value
            assertEquals(MetaSource.FRESH, result.source)

            val cachedRow = fakeDao.get("movie:tt0111161")
            assertEquals(currentTime, cachedRow?.fetchedAt)
            assertEquals(1, fakeDao.trimToCallCount)
            assertEquals(200, fakeDao.lastTrimMax)
        }

    @Test
    fun `first addon failing falls through to second addon`() =
        runTest(testDispatcher) {
            val addon1 = testAddon("https://addon1.com/manifest.json", position = 0)
            val addon2 = testAddon("https://addon2.com/manifest.json", position = 1)

            coEvery { addonRepository.observeActive() } returns flowOf(listOf(addon2, addon1)) // unsorted in list
            coEvery { remoteDataSource.metaWithRaw(addon1.transportUrl, "movie", "tt0111161") } returns
                Outcome.Failure(AppError.Network)
            coEvery { remoteDataSource.metaWithRaw(addon2.transportUrl, "movie", "tt0111161") } returns
                Outcome.Success(FetchedMeta(meta = testDetail, rawJson = rawJson))

            val outcome = repository.getMeta(type = "movie", id = "tt0111161")
            assertTrue(outcome is Outcome.Success)
            assertEquals(MetaSource.FRESH, (outcome as Outcome.Success).value.source)

            coVerify(ordering = io.mockk.Ordering.ORDERED) {
                remoteDataSource.metaWithRaw(addon1.transportUrl, "movie", "tt0111161")
                remoteDataSource.metaWithRaw(addon2.transportUrl, "movie", "tt0111161")
            }
        }

    @Test
    fun `all addons failing with a stale cache returns STALE`() =
        runTest(testDispatcher) {
            fakeDao.upsert(
                MetaCacheEntity(
                    metaId = "movie:tt0111161",
                    json = rawJson,
                    fetchedAt = currentTime - 100_000_000L,
                ),
            )
            val addon = testAddon()
            coEvery { addonRepository.observeActive() } returns flowOf(listOf(addon))
            coEvery { remoteDataSource.metaWithRaw(addon.transportUrl, "movie", "tt0111161") } returns
                Outcome.Failure(AppError.Network)

            val outcome = repository.getMeta(type = "movie", id = "tt0111161")
            assertTrue(outcome is Outcome.Success)
            assertEquals(MetaSource.STALE, (outcome as Outcome.Success).value.source)
        }

    @Test
    fun `all addons failing without cache returns first non-NotFound error`() =
        runTest(testDispatcher) {
            val addon1 = testAddon("https://addon1.com/manifest.json", position = 0)
            val addon2 = testAddon("https://addon2.com/manifest.json", position = 1)

            coEvery { addonRepository.observeActive() } returns flowOf(listOf(addon1, addon2))
            coEvery { remoteDataSource.metaWithRaw(addon1.transportUrl, "movie", "tt0111161") } returns
                Outcome.Failure(AppError.NotFound)
            coEvery { remoteDataSource.metaWithRaw(addon2.transportUrl, "movie", "tt0111161") } returns
                Outcome.Failure(AppError.Network)

            val outcome = repository.getMeta(type = "movie", id = "tt0111161")
            assertTrue(outcome is Outcome.Failure)
            assertEquals(AppError.Network, (outcome as Outcome.Failure).error)
        }

    @Test
    fun `no supporting addon gives NotFound`() =
        runTest(testDispatcher) {
            coEvery { addonRepository.observeActive() } returns flowOf(emptyList())

            val outcome = repository.getMeta(type = "movie", id = "tt0111161")
            assertTrue(outcome is Outcome.Failure)
            assertEquals(AppError.NotFound, (outcome as Outcome.Failure).error)
        }

    @Test
    fun `corrupt cache row is ignored`() =
        runTest(testDispatcher) {
            fakeDao.upsert(
                MetaCacheEntity(
                    metaId = "movie:tt0111161",
                    json = "not valid json {{{",
                    fetchedAt = currentTime - 1000L,
                ),
            )
            coEvery { addonRepository.observeActive() } returns flowOf(listOf(testAddon()))
            coEvery { remoteDataSource.metaWithRaw("https://addon.com/manifest.json", "movie", "tt0111161") } returns
                Outcome.Success(FetchedMeta(meta = testDetail, rawJson = rawJson))

            val outcome = repository.getMeta(type = "movie", id = "tt0111161")
            assertTrue(outcome is Outcome.Success)
            assertEquals(MetaSource.FRESH, (outcome as Outcome.Success).value.source)
        }

    @Test(expected = CancellationException::class)
    fun `cancellation is always rethrown`() =
        runTest(testDispatcher) {
            val addon = testAddon()
            coEvery { addonRepository.observeActive() } returns flowOf(listOf(addon))
            coEvery { remoteDataSource.metaWithRaw(addon.transportUrl, "movie", "tt0111161") } throws
                CancellationException("Cancelled")

            repository.getMeta(type = "movie", id = "tt0111161")
        }
}
