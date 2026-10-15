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
import com.nexus.stream.domain.usecase.EnsureDefaultAddons
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
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

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { addonRepository.observeAll() } returns flowOf(emptyList())
        coEvery { ensureDefaultAddons() } returns Outcome.Success(Unit)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun init_seedsSuccessfully_setsSeedFailedFalse() =
        runTest {
            coEvery { ensureDefaultAddons() } returns Outcome.Success(Unit)
            val viewModel = AddonManagerViewModel(addonRepository, ensureDefaultAddons)
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.seedFailed)
            assertFalse(viewModel.uiState.value.seedLoading)
        }

    @Test
    fun init_seedsFailure_setsSeedFailedTrue() =
        runTest {
            coEvery { ensureDefaultAddons() } returns Outcome.Failure(AppError.Network)
            val viewModel = AddonManagerViewModel(addonRepository, ensureDefaultAddons)
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.seedFailed)
            assertFalse(viewModel.uiState.value.seedLoading)
        }

    @Test
    fun checkSuccess_givesPreviewWithAlreadyInstalledAndIsHttp() =
        runTest {
            every { addonRepository.observeAll() } returns flowOf(listOf(sampleAddon))
            coEvery { addonRepository.fetchManifest("http://my-lan-addon.local/") }
                .returns(Outcome.Success(sampleManifest))

            val viewModel = AddonManagerViewModel(addonRepository, ensureDefaultAddons)
            advanceUntilIdle()

            viewModel.onEvent(AddonManagerEvent.OpenAdd(prefill = "http://my-lan-addon.local/manifest.json"))
            viewModel.onEvent(AddonManagerEvent.Check)
            advanceUntilIdle()

            val state = viewModel.uiState.value.addSheetState
            assertTrue(state is AddSheetState.Preview)
            val preview = state as AddSheetState.Preview
            assertEquals("my-lan-addon.local", preview.displayHost)
            assertTrue(preview.isHttp)
            assertFalse(preview.alreadyInstalled)
            assertTrue(preview.usable)
        }

    @Test
    fun checkSuccess_alreadyInstalledIsDetected() =
        runTest {
            every { addonRepository.observeAll() } returns flowOf(listOf(sampleAddon))
            coEvery { addonRepository.fetchManifest(normalizedCinemeta) } returns Outcome.Success(sampleManifest)

            val viewModel = AddonManagerViewModel(addonRepository, ensureDefaultAddons)
            advanceUntilIdle()

            viewModel.onEvent(AddonManagerEvent.OpenAdd(prefill = "stremio://v3-cinemeta.strem.io/manifest.json"))
            viewModel.onEvent(AddonManagerEvent.Check)
            advanceUntilIdle()

            val state = viewModel.uiState.value.addSheetState
            assertTrue(state is AddSheetState.Preview)
            val preview = state as AddSheetState.Preview
            assertTrue(preview.alreadyInstalled)
            assertFalse(preview.isHttp)
        }

    @Test
    fun checkFailure_keepsInputWithFriendlyErrorText() =
        runTest {
            coEvery { addonRepository.fetchManifest("https://broken.com/") } returns Outcome.Failure(AppError.Timeout)

            val viewModel = AddonManagerViewModel(addonRepository, ensureDefaultAddons)
            advanceUntilIdle()

            viewModel.onEvent(AddonManagerEvent.OpenAdd(prefill = "https://broken.com/manifest.json"))
            viewModel.onEvent(AddonManagerEvent.Check)
            advanceUntilIdle()

            val state = viewModel.uiState.value.addSheetState
            assertTrue(state is AddSheetState.Input)
            val input = state as AddSheetState.Input
            assertEquals(R.string.error_addon_timeout, input.error?.resId)
        }

    @Test
    fun installSuccess_closesSheet() =
        runTest {
            coEvery { addonRepository.fetchManifest(normalizedCinemeta) } returns Outcome.Success(sampleManifest)
            coEvery { addonRepository.install(normalizedCinemeta) } returns Outcome.Success(sampleAddon)

            val viewModel = AddonManagerViewModel(addonRepository, ensureDefaultAddons)
            advanceUntilIdle()

            viewModel.onEvent(AddonManagerEvent.OpenAdd(prefill = CINEMETA_URL, autoCheck = true))
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.addSheetState is AddSheetState.Preview)
            viewModel.onEvent(AddonManagerEvent.Install)
            advanceUntilIdle()

            assertEquals(AddSheetState.Closed, viewModel.uiState.value.addSheetState)
            coVerify(exactly = 1) { addonRepository.install(normalizedCinemeta) }
        }

    @Test
    fun dismissingDuringChecking_cancelsJob() =
        runTest {
            coEvery { addonRepository.fetchManifest(any()) } coAnswers {
                delay(1000)
                Outcome.Success(sampleManifest)
            }

            val viewModel = AddonManagerViewModel(addonRepository, ensureDefaultAddons)
            advanceUntilIdle()

            viewModel.onEvent(AddonManagerEvent.OpenAdd(prefill = "https://example.com/manifest.json"))
            viewModel.onEvent(AddonManagerEvent.Check)

            assertTrue(viewModel.uiState.value.addSheetState is AddSheetState.Checking)
            viewModel.onEvent(AddonManagerEvent.DismissAddSheet)

            assertEquals(AddSheetState.Closed, viewModel.uiState.value.addSheetState)
        }

    @Test
    fun removeFlow_requestCancelConfirm() =
        runTest {
            coEvery { addonRepository.remove(sampleAddon.transportUrl) } returns Unit

            val viewModel = AddonManagerViewModel(addonRepository, ensureDefaultAddons)
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
