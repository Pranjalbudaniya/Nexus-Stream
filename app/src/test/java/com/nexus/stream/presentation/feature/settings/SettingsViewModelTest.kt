package com.nexus.stream.presentation.feature.settings

import com.nexus.stream.domain.model.Addon
import com.nexus.stream.domain.model.AddonManifest
import com.nexus.stream.domain.model.AddonSource
import com.nexus.stream.domain.model.ManifestHints
import com.nexus.stream.domain.repository.AddonRepository
import com.nexus.stream.domain.repository.ContentPrefsRepository
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val contentPrefsRepository = mockk<ContentPrefsRepository>(relaxed = true)
    private val addonRepository = mockk<AddonRepository>()

    private val familyModeFlow = MutableStateFlow(true)
    private val addonsFlow = MutableStateFlow<List<Addon>>(emptyList())

    private val adultAddon =
        Addon(
            transportUrl = "https://adult.addon.local/manifest.json",
            manifest =
                AddonManifest(
                    id = "org.adult.addon",
                    version = "1.0.0",
                    name = "Adult Addon",
                    description = "Adult videos",
                    logo = null,
                    background = null,
                    types = listOf("movie"),
                    idPrefixes = null,
                    resources = emptyList(),
                    catalogs = emptyList(),
                    behaviorHints =
                        ManifestHints(
                            adult = true,
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

    private val standardAddon =
        Addon(
            transportUrl = "https://standard.addon.local/manifest.json",
            manifest =
                AddonManifest(
                    id = "org.standard.addon",
                    version = "1.0.0",
                    name = "Standard Addon",
                    description = "Standard videos",
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
            position = 1,
            source = AddonSource.LOCAL,
            installedAt = 2000L,
        )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { contentPrefsRepository.familyMode } returns familyModeFlow
        every { addonRepository.observeAll() } returns addonsFlow
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state reflects family mode and hidden adult count`() =
        runTest {
            addonsFlow.value = listOf(adultAddon, standardAddon)
            val viewModel = SettingsViewModel(contentPrefsRepository, addonRepository)
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.familyMode)
            assertEquals(1, viewModel.uiState.value.hiddenAdultAddonsCount)
        }

    @Test
    fun `hidden adult count is 0 when family mode is off`() =
        runTest {
            familyModeFlow.value = false
            addonsFlow.value = listOf(adultAddon, standardAddon)
            val viewModel = SettingsViewModel(contentPrefsRepository, addonRepository)
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.familyMode)
            assertEquals(0, viewModel.uiState.value.hiddenAdultAddonsCount)
        }

    @Test
    fun `SetFamilyMode event updates repository`() =
        runTest {
            val viewModel = SettingsViewModel(contentPrefsRepository, addonRepository)
            advanceUntilIdle()

            viewModel.onEvent(SettingsEvent.SetFamilyMode(false))
            advanceUntilIdle()

            coVerify { contentPrefsRepository.setFamilyMode(false) }
        }
}
