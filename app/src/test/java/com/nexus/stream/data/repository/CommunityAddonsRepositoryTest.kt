package com.nexus.stream.data.repository

import com.nexus.stream.data.remote.addon.AddonRemoteDataSource
import com.nexus.stream.domain.model.AddonCategory
import com.nexus.stream.domain.model.AddonManifest
import com.nexus.stream.domain.model.AppError
import com.nexus.stream.domain.model.CatalogSpec
import com.nexus.stream.domain.model.ManifestHints
import com.nexus.stream.domain.model.Outcome
import com.nexus.stream.domain.repository.CommunityAddonsRepository.Companion.COMMUNITY_ADDONS_MANIFEST_URL
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CommunityAddonsRepositoryTest {
    private val remoteDataSource: AddonRemoteDataSource = mockk()
    private val testDispatcher = UnconfinedTestDispatcher()
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
            explicitNulls = false
        }

    private var currentTime = 1000000L

    private val repository =
        CommunityAddonsRepositoryImpl(
            dataSource = remoteDataSource,
            json = json,
            dispatcher = testDispatcher,
            clock = { currentTime },
        )

    private fun readFixture(name: String): String =
        javaClass.classLoader!!
            .getResourceAsStream("fixtures/$name")!!
            .bufferedReader()
            .readText()

    private fun fakeManifest(
        addonCatalogs: List<CatalogSpec> =
            listOf(
                CatalogSpec(type = "all", id = "stremio-addons.net", name = "All", extras = emptyList()),
            ),
    ) = AddonManifest(
        id = "net.stremioaddons",
        version = "1.0.0",
        name = "Community Addons",
        description = "Store catalog",
        logo = null,
        background = null,
        types = listOf("all"),
        idPrefixes = null,
        resources = emptyList(),
        catalogs = emptyList(),
        addonCatalogs = addonCatalogs,
        behaviorHints = ManifestHints(adult = false, p2p = false, configurable = false, configurationRequired = false),
    )

    @Test
    fun `fetches manifest then catalog with right url`() =
        runTest(testDispatcher) {
            val manifest = fakeManifest()
            val rawCatalog = readFixture("store_addon_catalog.json")
            val catalogJson = json.parseToJsonElement(rawCatalog)

            coEvery { remoteDataSource.manifest(COMMUNITY_ADDONS_MANIFEST_URL) } returns
                Outcome.Success(manifest)
            coEvery {
                remoteDataSource.addonCatalog(
                    COMMUNITY_ADDONS_MANIFEST_URL,
                    "all",
                    "stremio-addons.net",
                )
            } returns Outcome.Success(catalogJson)

            val outcome = repository.fetch(forceRefresh = false)
            assertTrue(outcome is Outcome.Success)
            val list = (outcome as Outcome.Success).value
            assertEquals(30, list.size)

            coVerify(exactly = 1) { remoteDataSource.manifest(COMMUNITY_ADDONS_MANIFEST_URL) }
            coVerify(exactly = 1) {
                remoteDataSource.addonCatalog(
                    COMMUNITY_ADDONS_MANIFEST_URL,
                    "all",
                    "stremio-addons.net",
                )
            }
        }

    @Test
    fun `cache hit within 10 minutes returns cached without remote call`() =
        runTest(testDispatcher) {
            val manifest = fakeManifest()
            val rawCatalog = readFixture("store_addon_catalog.json")
            val catalogJson = json.parseToJsonElement(rawCatalog)

            coEvery { remoteDataSource.manifest(COMMUNITY_ADDONS_MANIFEST_URL) } returns
                Outcome.Success(manifest)
            coEvery {
                remoteDataSource.addonCatalog(
                    COMMUNITY_ADDONS_MANIFEST_URL,
                    "all",
                    "stremio-addons.net",
                )
            } returns Outcome.Success(catalogJson)

            // First call fetches
            val first = repository.fetch(forceRefresh = false)
            assertTrue(first is Outcome.Success)

            // Advance time by 5 minutes (less than 10 minutes)
            currentTime += 5 * 60 * 1000L

            val second = repository.fetch(forceRefresh = false)
            assertTrue(second is Outcome.Success)
            assertEquals((first as Outcome.Success).value, (second as Outcome.Success).value)

            // Still only 1 remote call each
            coVerify(exactly = 1) { remoteDataSource.manifest(any()) }
            coVerify(exactly = 1) { remoteDataSource.addonCatalog(any(), any(), any()) }
        }

    @Test
    fun `forceRefresh bypasses cache`() =
        runTest(testDispatcher) {
            val manifest = fakeManifest()
            val rawCatalog = readFixture("store_addon_catalog.json")
            val catalogJson = json.parseToJsonElement(rawCatalog)

            coEvery { remoteDataSource.manifest(COMMUNITY_ADDONS_MANIFEST_URL) } returns
                Outcome.Success(manifest)
            coEvery {
                remoteDataSource.addonCatalog(
                    COMMUNITY_ADDONS_MANIFEST_URL,
                    "all",
                    "stremio-addons.net",
                )
            } returns Outcome.Success(catalogJson)

            repository.fetch(forceRefresh = false)
            currentTime += 1 * 60 * 1000L
            val forced = repository.fetch(forceRefresh = true)
            assertTrue(forced is Outcome.Success)

            coVerify(exactly = 2) { remoteDataSource.manifest(COMMUNITY_ADDONS_MANIFEST_URL) }
            coVerify(exactly = 2) {
                remoteDataSource.addonCatalog(
                    COMMUNITY_ADDONS_MANIFEST_URL,
                    "all",
                    "stremio-addons.net",
                )
            }
        }

    @Test
    fun `cache expires after 10 minutes`() =
        runTest(testDispatcher) {
            val manifest = fakeManifest()
            val rawCatalog = readFixture("store_addon_catalog.json")
            val catalogJson = json.parseToJsonElement(rawCatalog)

            coEvery { remoteDataSource.manifest(COMMUNITY_ADDONS_MANIFEST_URL) } returns
                Outcome.Success(manifest)
            coEvery {
                remoteDataSource.addonCatalog(
                    COMMUNITY_ADDONS_MANIFEST_URL,
                    "all",
                    "stremio-addons.net",
                )
            } returns Outcome.Success(catalogJson)

            repository.fetch(forceRefresh = false)
            currentTime += 11 * 60 * 1000L // 11 minutes
            repository.fetch(forceRefresh = false)

            coVerify(exactly = 2) { remoteDataSource.manifest(COMMUNITY_ADDONS_MANIFEST_URL) }
            coVerify(exactly = 2) {
                remoteDataSource.addonCatalog(
                    COMMUNITY_ADDONS_MANIFEST_URL,
                    "all",
                    "stremio-addons.net",
                )
            }
        }

    @Test
    fun `failure is not cached`() =
        runTest(testDispatcher) {
            coEvery { remoteDataSource.manifest(COMMUNITY_ADDONS_MANIFEST_URL) } returns
                Outcome.Failure(AppError.Network)

            val first = repository.fetch(forceRefresh = false)
            assertTrue(first is Outcome.Failure)

            // Second call should retry remote
            repository.fetch(forceRefresh = false)
            coVerify(exactly = 2) { remoteDataSource.manifest(COMMUNITY_ADDONS_MANIFEST_URL) }
        }

    @Test
    fun `missing addonCatalogs yields Parse error`() =
        runTest(testDispatcher) {
            val manifestWithoutAddonCatalogs = fakeManifest(addonCatalogs = emptyList())
            coEvery { remoteDataSource.manifest(COMMUNITY_ADDONS_MANIFEST_URL) } returns
                Outcome.Success(manifestWithoutAddonCatalogs)

            val outcome = repository.fetch(forceRefresh = false)
            assertTrue(outcome is Outcome.Failure)
            assertEquals(AppError.Parse, (outcome as Outcome.Failure).error)
        }

    @Test
    fun `unexpected catalog shape yields Parse error`() =
        runTest(testDispatcher) {
            val manifest = fakeManifest()
            coEvery { remoteDataSource.manifest(COMMUNITY_ADDONS_MANIFEST_URL) } returns
                Outcome.Success(manifest)
            coEvery {
                remoteDataSource.addonCatalog(
                    COMMUNITY_ADDONS_MANIFEST_URL,
                    "all",
                    "stremio-addons.net",
                )
            } returns Outcome.Success(JsonPrimitive("invalid json"))

            val outcome = repository.fetch(forceRefresh = false)
            assertTrue(outcome is Outcome.Failure)
            assertEquals(AppError.Parse, (outcome as Outcome.Failure).error)
        }

    @Test(expected = CancellationException::class)
    fun `cancellation is rethrown`() =
        runTest(testDispatcher) {
            coEvery { remoteDataSource.manifest(COMMUNITY_ADDONS_MANIFEST_URL) } throws
                CancellationException("cancelled")
            repository.fetch(forceRefresh = false)
        }
}
