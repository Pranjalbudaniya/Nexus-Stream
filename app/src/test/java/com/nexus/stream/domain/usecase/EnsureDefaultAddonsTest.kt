package com.nexus.stream.domain.usecase

import com.nexus.stream.domain.model.Addon
import com.nexus.stream.domain.model.AddonManifest
import com.nexus.stream.domain.model.AddonSource
import com.nexus.stream.domain.model.AppError
import com.nexus.stream.domain.model.CINEMETA_URL
import com.nexus.stream.domain.model.ManifestHints
import com.nexus.stream.domain.model.Outcome
import com.nexus.stream.domain.repository.AddonRepository
import com.nexus.stream.domain.repository.AppStateRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EnsureDefaultAddonsTest {
    private class FakeAppStateRepository(
        initialSeeded: Boolean = false,
    ) : AppStateRepository {
        private val seededState = MutableStateFlow(initialSeeded)
        override val defaultAddonsSeeded: Flow<Boolean> = seededState

        override suspend fun markDefaultAddonsSeeded() {
            seededState.value = true
        }
    }

    private val sampleAddon =
        Addon(
            transportUrl = CINEMETA_URL,
            manifest =
                AddonManifest(
                    id = "org.stremio.cinemeta",
                    version = "3.0.14",
                    name = "Cinemeta",
                    description = "Official",
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
            enabled = true,
            position = 0,
            source = AddonSource.LOCAL,
            installedAt = 1000L,
        )

    @Test
    fun seedsWhenEmptyAndNotFlagged_marksFlagAndReturnsSuccess() =
        runTest {
            val addonRepo = mockk<AddonRepository>()
            val appStateRepo = FakeAppStateRepository(initialSeeded = false)
            every { addonRepo.observeAll() } returns flowOf(emptyList())
            coEvery { addonRepo.install(CINEMETA_URL) } returns Outcome.Success(sampleAddon)

            val useCase = EnsureDefaultAddons(addonRepo, appStateRepo)
            val result = useCase()

            assertTrue(result is Outcome.Success)
            assertTrue(appStateRepo.defaultAddonsSeeded.first())
            coVerify(exactly = 1) { addonRepo.install(CINEMETA_URL) }
        }

    @Test
    fun whenAlreadyFlagged_noSeedAndNoInstall() =
        runTest {
            val addonRepo = mockk<AddonRepository>()
            val appStateRepo = FakeAppStateRepository(initialSeeded = true)
            every { addonRepo.observeAll() } returns flowOf(emptyList())

            val useCase = EnsureDefaultAddons(addonRepo, appStateRepo)
            val result = useCase()

            assertTrue(result is Outcome.Success)
            assertTrue(appStateRepo.defaultAddonsSeeded.first())
            coVerify(exactly = 0) { addonRepo.install(any()) }
        }

    @Test
    fun whenAddonsAlreadyExist_setsFlagAndDoesNotInstall() =
        runTest {
            val addonRepo = mockk<AddonRepository>()
            val appStateRepo = FakeAppStateRepository(initialSeeded = false)
            every { addonRepo.observeAll() } returns flowOf(listOf(sampleAddon))

            val useCase = EnsureDefaultAddons(addonRepo, appStateRepo)
            val result = useCase()

            assertTrue(result is Outcome.Success)
            assertTrue(appStateRepo.defaultAddonsSeeded.first())
            coVerify(exactly = 0) { addonRepo.install(any()) }
        }

    @Test
    fun whenInstallFails_leavesFlagFalseAndReturnsFailure() =
        runTest {
            val addonRepo = mockk<AddonRepository>()
            val appStateRepo = FakeAppStateRepository(initialSeeded = false)
            every { addonRepo.observeAll() } returns flowOf(emptyList())
            coEvery { addonRepo.install(CINEMETA_URL) } returns Outcome.Failure(AppError.Network)

            val useCase = EnsureDefaultAddons(addonRepo, appStateRepo)
            val result = useCase()

            assertEquals(Outcome.Failure(AppError.Network), result)
            assertFalse(appStateRepo.defaultAddonsSeeded.first())
        }

    @Test
    fun concurrentCalls_installOnlyOnce() =
        runTest {
            val addonRepo = mockk<AddonRepository>()
            val appStateRepo = FakeAppStateRepository(initialSeeded = false)
            every { addonRepo.observeAll() } returns flowOf(emptyList())
            coEvery { addonRepo.install(CINEMETA_URL) } coAnswers {
                delay(50)
                Outcome.Success(sampleAddon)
            }

            val useCase = EnsureDefaultAddons(addonRepo, appStateRepo)
            val call1 = async { useCase() }
            val call2 = async { useCase() }

            val res1 = call1.await()
            val res2 = call2.await()

            assertTrue(res1 is Outcome.Success)
            assertTrue(res2 is Outcome.Success)
            assertTrue(appStateRepo.defaultAddonsSeeded.first())
            coVerify(exactly = 1) { addonRepo.install(CINEMETA_URL) }
        }
}
