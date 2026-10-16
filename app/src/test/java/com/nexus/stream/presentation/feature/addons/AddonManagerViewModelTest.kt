package com.nexus.stream.presentation.feature.addons

import com.nexus.stream.R
import com.nexus.stream.data.remote.addon.AddonUrlBuilder
import com.nexus.stream.domain.model.Addon
import com.nexus.stream.domain.model.AddonManifest
import com.nexus.stream.domain.model.AddonSource
import com.nexus.stream.domain.model.AppError
import com.nexus.stream.domain.model.CINEMETA_URL
import com.nexus.stream.domain.model.ManifestHints
import com.nexus.stream.domain.model.Outcome
import com.nexus.stream.domain.model.ResourceSpec
import com.nexus.stream.domain.repository.AddonRepository
import com.nexus.stream.domain.repository.ContentPrefsRepository
import com.nexus.stream.domain.usecase.EnsureDefaultAddons
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AddonManagerViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val addonRepository = mockk<AddonRepository>()
    private val ensureDefaultAddons = mockk<EnsureDefaultAddons>()
    private val contentPrefsRepository = mockk<ContentPrefsRepository>()
    private val familyModeFlow = MutableStateFlow(true)

    private val sampleManifest =
        AddonManifest(
            id = "org.stremio.cinemeta",
            version = "3.0.14",
            name = "Cinemeta",
            description = "Official movie directory",
            logo = null,
            background = null,
            types = listOf("movie"),
            idPrefixes = null,
            resources = listOf(ResourceSpec("catalog", null, null)),
            catalogs = emptyList(),
            behaviorHints =
                ManifestHints(
                    adult = false,
                    p2p = false,
                    configurable = false,
                    configurationRequired = false,
                ),
        )

    private val adultManifest =
        AddonManifest(
            id = "org.adult.addon",
            version = "1.0.0",
            name = "Adult Addon",
            description = "Adult videos",
            logo = null,
            background = null,
            types = listOf("movie"),
            idPrefixes = null,
            resources = listOf(ResourceSpec("stream", null, null)),
            catalogs = emptyList(),
            behaviorHints =
                ManifestHints(
                    adult = true,
                    p2p = false,
                    configurable = false,
                    configurationRequired = false,
                ),
        )

    private val normalizedCinemeta = AddonUrlBuilder.normalize(CINEMETA_URL)!!.toString()

    private val sampleAddon =
        Addon(
            transportUrl = normalizedCinemeta,
            manifest = sampleManifest,
            enabled = true,
            position = 0,
            source = AddonSource.LOCAL,
            installedAt = 1000L,
        )

    private val adultAddon =
        Addon(
            transportUrl = "https://adult.addon.local/manifest.json",
            manifest = adultManifest,
            enabled = false,
            position = 1,
            source = AddonSource.LOCAL,
            installedAt = 2000L,
        )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { addonRepository.observeAll() } returns flowOf(emptyList())
        every { contentPrefsRepository.familyMode } returns familyModeFlow
        coEvery { ensureDefaultAddons() } returns Outcome.Success(Unit)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = AddonManagerViewModel(addonRepository, ensureDefaultAddons, contentPrefsRepository)

    @Test
    fun init_seedsSuccessfully_setsSeedFailedFalse() =
        runTest {
            coEvery { ensureDefaultAddons() } returns Outcome.Success(Unit)
            val viewModel = createViewModel()
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.seedFailed)
            assertFalse(viewModel.uiState.value.seedLoading)
        }

    @Test
    fun init_seedsFailure_setsSeedFailedTrue() =
        runTest {
            coEvery { ensureDefaultAddons() } returns Outcome.Failure(AppError.Network)
            val viewModel = createViewModel()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.seedFailed)
            assertFalse(viewModel.uiState.value.seedLoading)
        }

    @Test
    fun checkSuccess_givesPreviewWithAlreadyInstalledAndIsHttp() =
        runTest {
            val manifest = sampleManifest
            val httpUrl = "http://example.com/manifest.json"
            val normalizedHttp = AddonUrlBuilder.normalize(httpUrl)!!.toString()
            every { addonRepository.observeAll() } returns flowOf(listOf(sampleAddon))
            coEvery { addonRepository.fetchManifest(normalizedHttp) } returns Outcome.Success(manifest)

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onEvent(AddonManagerEvent.OpenAdd())
            viewModel.onEvent(AddonManagerEvent.UrlChanged(httpUrl))
            viewModel.onEvent(AddonManagerEvent.Check)
            advanceUntilIdle()

            val state = viewModel.uiState.value.addSheetState
            assertTrue(state is AddSheetState.Preview)
            val preview = state as AddSheetState.Preview
            assertTrue(preview.isHttp)
            assertFalse(preview.alreadyInstalled)
            assertTrue(preview.usable)
            assertFalse(preview.isAdultBlocked)
        }

    @Test
    fun checkSuccess_adultManifestWhenFamilyModeOn_setsIsAdultBlocked() =
        runTest {
            val adultUrl = "https://adult.addon.local/manifest.json"
            val normalizedAdult = AddonUrlBuilder.normalize(adultUrl)!!.toString()
            coEvery { addonRepository.fetchManifest(normalizedAdult) } returns Outcome.Success(adultManifest)

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onEvent(AddonManagerEvent.OpenAdd())
            viewModel.onEvent(AddonManagerEvent.UrlChanged(adultUrl))
            viewModel.onEvent(AddonManagerEvent.Check)
            advanceUntilIdle()

            val state = viewModel.uiState.value.addSheetState
            assertTrue(state is AddSheetState.Preview)
            val preview = state as AddSheetState.Preview
            assertTrue(preview.isAdultBlocked)

            // Attempting to install should be blocked
            viewModel.onEvent(AddonManagerEvent.Install)
            advanceUntilIdle()

            coVerify(exactly = 0) { addonRepository.install(any()) }
        }

    @Test
    fun setEnabled_adultAddonWhenFamilyModeOn_isIgnored() =
        runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onEvent(AddonManagerEvent.SetEnabled(adultAddon, true))
            advanceUntilIdle()

            coVerify(exactly = 0) { addonRepository.setEnabled(any(), any()) }
        }

    @Test
    fun checkFailure_mapsToErrorStringInInputState() =
        runTest {
            val validUrl = "https://example.com/manifest.json"
            val normalized = AddonUrlBuilder.normalize(validUrl)!!.toString()
            coEvery { addonRepository.fetchManifest(normalized) } returns Outcome.Failure(AppError.Network)

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onEvent(AddonManagerEvent.OpenAdd())
            viewModel.onEvent(AddonManagerEvent.UrlChanged(validUrl))
            viewModel.onEvent(AddonManagerEvent.Check)
            advanceUntilIdle()

            val state = viewModel.uiState.value.addSheetState
            assertTrue(state is AddSheetState.Input)
            val input = state as AddSheetState.Input
            assertEquals(R.string.error_addon_network, input.error?.resId)
        }

    @Test
    fun installSuccess_emitsMessageAndCloses() =
        runTest {
            val url = "https://example.com/manifest.json"
            val normalized = AddonUrlBuilder.normalize(url)!!.toString()
            coEvery { addonRepository.fetchManifest(normalized) } returns Outcome.Success(sampleManifest)
            coEvery { addonRepository.install(normalized) } returns Outcome.Success(sampleAddon)

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onEvent(AddonManagerEvent.OpenAdd())
            viewModel.onEvent(AddonManagerEvent.UrlChanged(url))
            viewModel.onEvent(AddonManagerEvent.Check)
            advanceUntilIdle()

            viewModel.onEvent(AddonManagerEvent.Install)
            advanceUntilIdle()

            assertEquals(AddSheetState.Closed, viewModel.uiState.value.addSheetState)
            coVerify(exactly = 1) { addonRepository.install(normalized) }
        }

    @Test
    fun toggleEnabled_callsRepository() =
        runTest {
            coEvery { addonRepository.setEnabled(sampleAddon.transportUrl, false) } returns Unit

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onEvent(AddonManagerEvent.SetEnabled(sampleAddon, false))
            advanceUntilIdle()

            coVerify(exactly = 1) { addonRepository.setEnabled(sampleAddon.transportUrl, false) }
        }

    @Test
    fun reorder_callsRepositoryWithCorrectPosition() =
        runTest {
            coEvery { addonRepository.move(sampleAddon.transportUrl, -1) } returns Unit
            coEvery { addonRepository.move(sampleAddon.transportUrl, 1) } returns Unit

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onEvent(AddonManagerEvent.MoveUp(sampleAddon))
            advanceUntilIdle()
            coVerify(exactly = 1) { addonRepository.move(sampleAddon.transportUrl, -1) }

            viewModel.onEvent(AddonManagerEvent.MoveDown(sampleAddon))
            advanceUntilIdle()
            coVerify(exactly = 1) { addonRepository.move(sampleAddon.transportUrl, 1) }
        }

    @Test
    fun removeFlow_requestCancelConfirm() =
        runTest {
            coEvery { addonRepository.remove(sampleAddon.transportUrl) } returns Unit

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onEvent(AddonManagerEvent.RequestRemove(sampleAddon))
            assertEquals(sampleAddon, viewModel.uiState.value.removeTarget)

            viewModel.onEvent(AddonManagerEvent.CancelRemove)
            assertNull(viewModel.uiState.value.removeTarget)

            viewModel.onEvent(AddonManagerEvent.RequestRemove(sampleAddon))
            viewModel.onEvent(AddonManagerEvent.ConfirmRemove)
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.removeTarget)
            coVerify(exactly = 1) { addonRepository.remove(sampleAddon.transportUrl) }
        }

    @Test
    fun noErrorMessageContainsUrl() {
        val errors =
            listOf(
                AppError.InvalidUrl,
                AppError.Network,
                AppError.NotFound,
                AppError.Parse,
                AppError.Timeout,
                AppError.Http(404),
                AppError.Http(500),
                AppError.Unknown("custom message"),
            )

        for (err in errors) {
            val userMsg = err.toUserMessage()
            for (arg in userMsg.formatArgs) {
                val str = arg.toString()
                val hasUrl = str.contains("http://") || str.contains("https://")
                assertFalse("Error argument should not contain http:// or https://: $str", hasUrl)
            }
        }
    }
}
