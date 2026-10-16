package com.nexus.stream.data.repository

import app.cash.turbine.test
import com.nexus.stream.data.local.db.TransactionRunner
import com.nexus.stream.data.local.db.dao.FakeAddonDao
import com.nexus.stream.data.local.db.entity.AddonEntity
import com.nexus.stream.data.remote.addon.AddonRemoteDataSource
import com.nexus.stream.data.remote.addon.FetchedManifest
import com.nexus.stream.di.NetworkModule
import com.nexus.stream.domain.model.AddonManifest
import com.nexus.stream.domain.model.AddonSource
import com.nexus.stream.domain.model.AppError
import com.nexus.stream.domain.model.ManifestHints
import com.nexus.stream.domain.model.Outcome
import com.nexus.stream.domain.model.ResourceSpec
import com.nexus.stream.domain.repository.ContentPrefsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeContentPrefsRepository(
    initialFamilyMode: Boolean = true,
) : ContentPrefsRepository {
    val familyModeFlow = MutableStateFlow(initialFamilyMode)
    override val familyMode: Flow<Boolean> = familyModeFlow

    override suspend fun setFamilyMode(enabled: Boolean) {
        familyModeFlow.value = enabled
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AddonRepositoryImplTest {
    private val fakeDao = FakeAddonDao()
    private val remoteDataSource: AddonRemoteDataSource = mockk()
    private val passThroughRunner =
        object : TransactionRunner {
            override suspend fun <T> run(block: suspend () -> T): T = block()
        }
    private val json = NetworkModule.provideJson()
    private val testDispatcher = UnconfinedTestDispatcher()
    private val fakeContentPrefsRepository = FakeContentPrefsRepository()
    private var currentTime = 1_000L

    private val repository =
        AddonRepositoryImpl(
            addonDao = fakeDao,
            remoteDataSource = remoteDataSource,
            transactionRunner = passThroughRunner,
            json = json,
            contentPrefsRepository = fakeContentPrefsRepository,
            dispatcher = testDispatcher,
            clock = { currentTime },
        )

    private fun sampleManifest(
        id: String = "org.example.addon",
        name: String = "Example Addon",
        version: String = "1.0.0",
        hasResources: Boolean = true,
    ): AddonManifest =
        AddonManifest(
            id = id,
            version = version,
            name = name,
            description = "Test description",
            logo = null,
            background = null,
            types = listOf("movie"),
            idPrefixes = listOf("tt"),
            resources = if (hasResources) listOf(ResourceSpec("catalog", null, null)) else emptyList(),
            catalogs = emptyList(),
            behaviorHints =
                ManifestHints(
                    adult = false,
                    p2p = false,
                    configurable = false,
                    configurationRequired = false,
                ),
        )

    private fun sampleRawJson(
        id: String = "org.example.addon",
        name: String = "Example Addon",
        version: String = "1.0.0",
        hasResources: Boolean = true,
        adult: Boolean = false,
    ): String {
        val part = if (hasResources) """["catalog"]""" else "[]"
        return "{\"id\":\"$id\",\"name\":\"$name\",\"version\":\"$version\"," +
            "\"resources\":$part,\"types\":[\"movie\"],\"behaviorHints\":{\"adult\":$adult}}"
    }

    @Test
    fun `observeActive excludes adult addons only when Family mode is on and reacts live to the flag`() =
        runTest(testDispatcher) {
            val normalRaw = sampleRawJson(id = "normal", name = "Normal Addon", adult = false)
            val adultRaw = sampleRawJson(id = "adult", name = "Adult Addon", adult = true)

            fakeDao.upsert(
                AddonEntity(
                    transportUrl = "https://example.com/normal/manifest.json",
                    id = "normal",
                    name = "Normal Addon",
                    version = "1.0.0",
                    enabled = true,
                    position = 0,
                    source = AddonSource.LOCAL,
                    installedAt = 1000L,
                    manifestJson = normalRaw,
                ),
            )
            fakeDao.upsert(
                AddonEntity(
                    transportUrl = "https://example.com/adult/manifest.json",
                    id = "adult",
                    name = "Adult Addon",
                    version = "1.0.0",
                    enabled = true,
                    position = 1,
                    source = AddonSource.LOCAL,
                    installedAt = 1000L,
                    manifestJson = adultRaw,
                ),
            )

            // Family mode is ON (default true)
            fakeContentPrefsRepository.familyModeFlow.value = true
            val activeWhenOn = repository.observeActive().first()
            assertEquals(1, activeWhenOn.size)
            assertEquals("normal", activeWhenOn[0].manifest.id)

            // Family mode toggled to OFF live
            fakeContentPrefsRepository.familyModeFlow.value = false
            val activeWhenOff = repository.observeActive().first()
            assertEquals(2, activeWhenOff.size)

            // Family mode toggled back to ON
            fakeContentPrefsRepository.familyModeFlow.value = true
            val activeWhenOnAgain = repository.observeActive().first()
            assertEquals(1, activeWhenOnAgain.size)
        }

    @Test
    fun install_newAddonGetsPositionZeroAndClockTimestamp_secondGetsPositionOne() =
        runTest {
            val manifest1 = sampleManifest("org.first", "First")
            val raw1 = sampleRawJson("org.first", "First")
            coEvery { remoteDataSource.manifestWithRaw("https://first.com/") } returns
                Outcome.Success(FetchedManifest(manifest1, raw1))

            currentTime = 12345L
            val outcome1 = repository.install("https://first.com/manifest.json")
            assertTrue(outcome1 is Outcome.Success)
            val addon1 = (outcome1 as Outcome.Success).value
            assertEquals("https://first.com/", addon1.transportUrl)
            assertEquals(0, addon1.position)
            assertTrue(addon1.enabled)
            assertEquals(AddonSource.LOCAL, addon1.source)
            assertEquals(12345L, addon1.installedAt)

            val manifest2 = sampleManifest("org.second", "Second")
            val raw2 = sampleRawJson("org.second", "Second")
            coEvery { remoteDataSource.manifestWithRaw("https://second.com/") } returns
                Outcome.Success(FetchedManifest(manifest2, raw2))

            currentTime = 67890L
            val outcome2 = repository.install("https://second.com")
            assertTrue(outcome2 is Outcome.Success)
            val addon2 = (outcome2 as Outcome.Success).value
            assertEquals(1, addon2.position)
            assertEquals(67890L, addon2.installedAt)
        }

    @Test
    fun install_reinstallSameUrlKeepsEnabledPositionInstalledAtAndUpdatesManifest() =
        runTest {
            val initialManifest = sampleManifest("org.addon", "Initial Name", "1.0.0")
            val initialRaw = sampleRawJson("org.addon", "Initial Name", "1.0.0")
            coEvery { remoteDataSource.manifestWithRaw("https://addon.com/") } returns
                Outcome.Success(FetchedManifest(initialManifest, initialRaw))

            currentTime = 1_000L
            repository.install("https://addon.com/manifest.json")
            repository.setEnabled("https://addon.com/", false)

            val updatedManifest = sampleManifest("org.addon", "Updated Name", "2.0.0")
            val updatedRaw = sampleRawJson("org.addon", "Updated Name", "2.0.0")
            coEvery { remoteDataSource.manifestWithRaw("https://addon.com/") } returns
                Outcome.Success(FetchedManifest(updatedManifest, updatedRaw))

            currentTime = 9_999L
            // Re-install with stremio:// scheme
            val outcome = repository.install("stremio://addon.com/")
            assertTrue(outcome is Outcome.Success)
            val addon = (outcome as Outcome.Success).value
            assertEquals("https://addon.com/", addon.transportUrl)
            assertEquals(0, addon.position)
            assertFalse(addon.enabled) // kept false
            assertEquals(1_000L, addon.installedAt) // kept original timestamp
            assertEquals("Updated Name", addon.manifest.name)
            assertEquals("2.0.0", addon.manifest.version)

            assertEquals(1, fakeDao.getAll().size)
        }

    @Test
    fun install_invalidUrlReturnsInvalidUrlWithoutRemoteCall() =
        runTest {
            val outcome = repository.install("not a valid url")
            assertEquals(Outcome.Failure(AppError.InvalidUrl), outcome)
            coVerify(exactly = 0) { remoteDataSource.manifestWithRaw(any()) }
            assertEquals(0, fakeDao.getAll().size)
        }

    @Test
    fun install_remoteFailurePassesThroughAndStoresNothing() =
        runTest {
            coEvery { remoteDataSource.manifestWithRaw("https://addon.com/") } returns
                Outcome.Failure(AppError.Network)

            val outcome = repository.install("https://addon.com/manifest.json")
            assertEquals(Outcome.Failure(AppError.Network), outcome)
            assertEquals(0, fakeDao.getAll().size)
        }

    @Test
    fun install_manifestWithNoResourcesGivesParseAndStoresNothing() =
        runTest {
            val emptyResManifest = sampleManifest(hasResources = false)
            val emptyResRaw = sampleRawJson(hasResources = false)
            coEvery { remoteDataSource.manifestWithRaw("https://addon.com/") } returns
                Outcome.Success(FetchedManifest(emptyResManifest, emptyResRaw))

            val outcome = repository.install("https://addon.com")
            assertEquals(Outcome.Failure(AppError.Parse), outcome)
            assertEquals(0, fakeDao.getAll().size)
        }

    @Test
    fun install_twoUrlsWithSameManifestIdAreTwoRows() =
        runTest {
            val manifest = sampleManifest("org.shared.id")
            val raw = sampleRawJson("org.shared.id")
            coEvery { remoteDataSource.manifestWithRaw("https://config1.addon.com/") } returns
                Outcome.Success(FetchedManifest(manifest, raw))
            coEvery { remoteDataSource.manifestWithRaw("https://config2.addon.com/") } returns
                Outcome.Success(FetchedManifest(manifest, raw))

            repository.install("https://config1.addon.com")
            repository.install("https://config2.addon.com")

            val all = fakeDao.getAll()
            assertEquals(2, all.size)
            assertEquals(
                setOf("https://config1.addon.com/", "https://config2.addon.com/"),
                all.map { it.transportUrl }.toSet(),
            )
        }

    @Test
    fun remove_renumbersRemainingRowsDense() =
        runTest {
            val m = sampleManifest()
            val r = sampleRawJson()
            coEvery { remoteDataSource.manifestWithRaw(any()) } returns Outcome.Success(FetchedManifest(m, r))

            repository.install("https://addon1.com")
            repository.install("https://addon2.com")
            repository.install("https://addon3.com")

            assertEquals(listOf(0, 1, 2), fakeDao.getAll().map { it.position })

            repository.remove("https://addon2.com")

            val remaining = fakeDao.getAll()
            assertEquals(listOf("https://addon1.com/", "https://addon3.com/"), remaining.map { it.transportUrl })
            assertEquals(listOf(0, 1), remaining.map { it.position })
        }

    @Test
    fun setEnabled_leavesPositionsUntouched() =
        runTest {
            val m = sampleManifest()
            val r = sampleRawJson()
            coEvery { remoteDataSource.manifestWithRaw(any()) } returns Outcome.Success(FetchedManifest(m, r))

            repository.install("https://addon1.com")
            repository.install("https://addon2.com")

            repository.setEnabled("https://addon1.com", false)

            val all = fakeDao.getAll()
            assertFalse(all[0].enabled)
            assertEquals(0, all[0].position)
            assertTrue(all[1].enabled)
            assertEquals(1, all[1].position)
        }

    @Test
    fun move_changesOrderAndPositionsRemainDense() =
        runTest {
            val m = sampleManifest()
            val r = sampleRawJson()
            coEvery { remoteDataSource.manifestWithRaw(any()) } returns Outcome.Success(FetchedManifest(m, r))

            repository.install("https://addon1.com")
            repository.install("https://addon2.com")
            repository.install("https://addon3.com")

            repository.move("https://addon1.com", 2)

            val all = fakeDao.getAll()
            assertEquals(
                listOf("https://addon2.com/", "https://addon3.com/", "https://addon1.com/"),
                all.map { it.transportUrl },
            )
            assertEquals(listOf(0, 1, 2), all.map { it.position })
        }

    @Test
    fun observeEnabled_returnsOnlyEnabledAddonsInOrder() =
        runTest {
            val m = sampleManifest()
            val r = sampleRawJson()
            coEvery { remoteDataSource.manifestWithRaw(any()) } returns Outcome.Success(FetchedManifest(m, r))

            repository.install("https://addon1.com")
            repository.install("https://addon2.com")
            repository.setEnabled("https://addon1.com", false)

            repository.observeEnabled().test {
                val list = awaitItem()
                assertEquals(1, list.size)
                assertEquals("https://addon2.com/", list.first().transportUrl)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun observeAll_skipsCorruptRowWithoutThrowing() =
        runTest {
            val validM = sampleManifest("org.valid", "Valid")
            val validR = sampleRawJson("org.valid", "Valid")
            coEvery { remoteDataSource.manifestWithRaw(any()) } returns Outcome.Success(FetchedManifest(validM, validR))

            repository.install("https://valid.com")

            // Insert corrupt row directly into dao
            fakeDao.upsert(
                AddonEntity(
                    transportUrl = "https://corrupt.com/",
                    id = "corrupt",
                    name = "Corrupt",
                    version = "1.0",
                    manifestJson = "{ not valid json }}}",
                    enabled = true,
                    position = 1,
                    source = AddonSource.LOCAL,
                    installedAt = 2_000L,
                ),
            )

            repository.observeAll().test {
                val list = awaitItem()
                assertEquals(1, list.size)
                assertEquals("https://valid.com/", list.first().transportUrl)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun refresh_unknownUrlGivesNotFound() =
        runTest {
            val outcome = repository.refresh("https://unknown.com")
            assertEquals(Outcome.Failure(AppError.NotFound), outcome)
        }

    @Test
    fun refresh_existingUrlUpdatesManifest() =
        runTest {
            val initialM = sampleManifest("org.addon", "Initial", "1.0.0")
            val initialR = sampleRawJson("org.addon", "Initial", "1.0.0")
            coEvery { remoteDataSource.manifestWithRaw("https://addon.com/") } returns
                Outcome.Success(FetchedManifest(initialM, initialR))

            repository.install("https://addon.com")

            val updatedM = sampleManifest("org.addon", "Refreshed Name", "1.0.1")
            val updatedR = sampleRawJson("org.addon", "Refreshed Name", "1.0.1")
            coEvery { remoteDataSource.manifestWithRaw("https://addon.com/") } returns
                Outcome.Success(FetchedManifest(updatedM, updatedR))

            val refreshOutcome = repository.refresh("https://addon.com")
            assertTrue(refreshOutcome is Outcome.Success)
            val refreshed = (refreshOutcome as Outcome.Success).value
            assertEquals("Refreshed Name", refreshed.manifest.name)
            assertEquals("1.0.1", refreshed.manifest.version)
        }
}
