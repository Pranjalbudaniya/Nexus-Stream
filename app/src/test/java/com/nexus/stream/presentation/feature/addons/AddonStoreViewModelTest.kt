package com.nexus.stream.presentation.feature.addons

import com.nexus.stream.R
import com.nexus.stream.data.remote.addon.AddonUrlBuilder
import com.nexus.stream.domain.model.Addon
import com.nexus.stream.domain.model.AddonCategory
import com.nexus.stream.domain.model.AddonManifest
import com.nexus.stream.domain.model.AddonSource
import com.nexus.stream.domain.model.AppError
import com.nexus.stream.domain.model.ManifestHints
import com.nexus.stream.domain.model.Outcome
import com.nexus.stream.domain.model.ResourceSpec
import com.nexus.stream.domain.model.StoreAddon
import com.nexus.stream.domain.repository.AddonRepository
import com.nexus.stream.domain.repository.CommunityAddonsRepository
import com.nexus.stream.domain.repository.ContentPrefsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AddonStoreViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val communityAddonsRepository = mockk<CommunityAddonsRepository>()
    private val addonRepository = mockk<AddonRepository>()
    private val contentPrefsRepository = mockk<ContentPrefsRepository>()

    private val installedFlow = MutableStateFlow<List<Addon>>(emptyList())
    private val familyModeFlow = MutableStateFlow(true)

    private val storeAddon1 =
        StoreAddon(
            transportUrl = "https://torrent.stream/",
            name = "Torrent Stream",
            description = "High speed streams",
            version = "1.0.0",
            logo = null,
            types = listOf("movie"),
            resources = setOf("stream"),
            categories = setOf(AddonCategory.STREAMS),
            primary = AddonCategory.STREAMS,
            isAdult = false,
            p2p = true,
            configurable = false,
            usable = true,
            hasDetails = true,
            isInstalled = false,
        )

    private val storeAddon2 =
        StoreAddon(
            transportUrl = "https://v3-cinemeta.strem.io/",
            name = "Cinemeta Catalog",
            description = "Official catalog",
            version = "3.0.0",
            logo = null,
            types = listOf("movie"),
            resources = setOf("catalog"),
            categories = setOf(AddonCategory.CATALOGS),
            primary = AddonCategory.CATALOGS,
            isAdult = false,
            p2p = false,
            configurable = false,
            usable = true,
            hasDetails = true,
            isInstalled = false,
        )

    private val httpAddon =
        StoreAddon(
            transportUrl = "http://insecure.addon/",
            name = "Insecure Stream",
            description = "Unencrypted",
            version = "1.0.0",
            logo = null,
            types = listOf("movie"),
            resources = setOf("stream"),
            categories = setOf(AddonCategory.STREAMS),
            primary = AddonCategory.STREAMS,
            isAdult = false,
            p2p = false,
            configurable = false,
            usable = true,
            hasDetails = true,
            isInstalled = false,
        )

    private val adultAddon =
        StoreAddon(
            transportUrl = "https://adult.addon/",
            name = "Adult Videos",
            description = "Adult 18+ content",
            version = "1.0.0",
            logo = null,
            types = listOf("movie"),
            resources = setOf("stream"),
            categories = setOf(AddonCategory.STREAMS),
            primary = AddonCategory.STREAMS,
            isAdult = true,
            p2p = false,
            configurable = false,
            usable = true,
            hasDetails = true,
            isInstalled = false,
        )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { addonRepository.observeAll() } returns installedFlow
        every { contentPrefsRepository.familyMode } returns familyModeFlow
        coEvery { communityAddonsRepository.fetch(any()) } returns
            Outcome.Success(listOf(storeAddon1, storeAddon2, httpAddon, adultAddon))
        coEvery { addonRepository.install(any()) } returns Outcome.Success(mockk(relaxed = true))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() =
        AddonStoreViewModel(
            communityAddonsRepository = communityAddonsRepository,
            addonRepository = addonRepository,
            contentPrefsRepository = contentPrefsRepository,
            defaultDispatcher = testDispatcher,
        )

    @Test
    fun initialLoad_success_populatesFilteredAddons() =
        runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.isLoading)
            assertNull(viewModel.uiState.value.error)
            // Under familyMode=true, adultAddon is omitted -> 3 addons
            assertEquals(3, viewModel.uiState.value.filteredAddons.size)
            assertEquals(3, viewModel.uiState.value.totalCount)
            assertEquals(1, viewModel.uiState.value.categoryCounts[AddonCategory.CATALOGS])
            assertEquals(2, viewModel.uiState.value.categoryCounts[AddonCategory.STREAMS])
        }

    @Test
    fun initialLoad_failure_setsError_andRetryForcesRefresh() =
        runTest {
            coEvery { communityAddonsRepository.fetch(false) } returns Outcome.Failure(AppError.Network)
            val viewModel = createViewModel()
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.isLoading)
            assertEquals(AppError.Network, viewModel.uiState.value.error)
            assertTrue(
                viewModel.uiState.value.filteredAddons
                    .isEmpty(),
            )

            coEvery { communityAddonsRepository.fetch(true) } returns Outcome.Success(listOf(storeAddon1))
            viewModel.onEvent(AddonStoreEvent.Refresh)
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.error)
            assertEquals(1, viewModel.uiState.value.filteredAddons.size)
            coVerify(exactly = 1) { communityAddonsRepository.fetch(true) }
        }

    @Test
    fun searchAndCategory_filterStoreAddons() =
        runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            // Filter by search
            viewModel.onEvent(AddonStoreEvent.SearchQueryChanged("Torrent"))
            advanceUntilIdle()

            assertEquals(1, viewModel.uiState.value.filteredAddons.size)
            assertEquals(
                "Torrent Stream",
                viewModel.uiState.value.filteredAddons
                    .first()
                    .name,
            )

            viewModel.onEvent(AddonStoreEvent.ClearSearch)
            advanceUntilIdle()
            assertEquals(3, viewModel.uiState.value.filteredAddons.size)

            // Filter by category
            viewModel.onEvent(AddonStoreEvent.SelectCategory(AddonCategory.CATALOGS))
            advanceUntilIdle()
            assertEquals(1, viewModel.uiState.value.filteredAddons.size)
            assertEquals(
                "Cinemeta Catalog",
                viewModel.uiState.value.filteredAddons
                    .first()
                    .name,
            )
        }

    @Test
    fun install_successAndFailure() =
        runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onEvent(AddonStoreEvent.Install(storeAddon1))
            advanceUntilIdle()

            coVerify(exactly = 1) { addonRepository.install(storeAddon1.transportUrl) }

            // Failure case
            coEvery { addonRepository.install(storeAddon2.transportUrl) } returns Outcome.Failure(AppError.Timeout)
            viewModel.onEvent(AddonStoreEvent.Install(storeAddon2))
            advanceUntilIdle()

            coVerify(exactly = 1) { addonRepository.install(storeAddon2.transportUrl) }
            assertNull(viewModel.uiState.value.installingUrl)
        }

    @Test
    fun httpEntry_requiresConfirmationBeforeInstall() =
        runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onEvent(AddonStoreEvent.Install(httpAddon))
            assertNotNull(viewModel.uiState.value.pendingHttpAddon)
            coVerify(exactly = 0) { addonRepository.install(httpAddon.transportUrl) }

            viewModel.onEvent(AddonStoreEvent.ConfirmHttpInstall)
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.pendingHttpAddon)
            coVerify(exactly = 1) { addonRepository.install(httpAddon.transportUrl) }
        }

    @Test
    fun adultEntries_hiddenUnderFamilyMode_visibleWhenOff() =
        runTest {
            familyModeFlow.value = true
            val viewModel = createViewModel()
            advanceUntilIdle()

            assertFalse(
                viewModel.uiState.value.filteredAddons
                    .any { it.isAdult },
            )

            familyModeFlow.value = false
            advanceUntilIdle()

            assertTrue(
                viewModel.uiState.value.filteredAddons
                    .any { it.isAdult },
            )
        }

    @Test
    fun installed_updatesLiveFromRepository() =
        runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            assertFalse(
                viewModel.uiState.value.filteredAddons
                    .first { it.transportUrl == storeAddon1.transportUrl }
                    .isInstalled,
            )

            val normalizedUrl = AddonUrlBuilder.normalize(storeAddon1.transportUrl)!!.toString()
            val installedAddon =
                Addon(
                    transportUrl = normalizedUrl,
                    manifest =
                        AddonManifest(
                            id = "torrent.stream",
                            version = "1.0.0",
                            name = "Torrent Stream",
                            description = "High speed streams",
                            logo = null,
                            background = null,
                            types = listOf("movie"),
                            idPrefixes = null,
                            resources = listOf(ResourceSpec("stream", null, null)),
                            catalogs = emptyList(),
                            behaviorHints = ManifestHints(false, true, false, false),
                        ),
                    enabled = true,
                    position = 0,
                    source = AddonSource.LOCAL,
                    installedAt = 1000L,
                )

            installedFlow.value = listOf(installedAddon)
            advanceUntilIdle()

            assertTrue(
                viewModel.uiState.value.filteredAddons
                    .first { it.transportUrl == storeAddon1.transportUrl }
                    .isInstalled,
            )
        }
}
