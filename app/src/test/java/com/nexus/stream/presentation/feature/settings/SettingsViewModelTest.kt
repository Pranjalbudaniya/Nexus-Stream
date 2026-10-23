package com.nexus.stream.presentation.feature.settings

import com.nexus.stream.domain.model.Addon
import com.nexus.stream.domain.model.AddonManifest
import com.nexus.stream.domain.model.AddonSource
import com.nexus.stream.domain.model.ManifestHints
import com.nexus.stream.domain.model.SubtitleBackground
import com.nexus.stream.domain.model.SubtitleEdge
import com.nexus.stream.domain.model.SubtitleSize
import com.nexus.stream.domain.model.SubtitleStyle
import com.nexus.stream.domain.repository.AddonRepository
import com.nexus.stream.domain.repository.ContentPrefsRepository
import com.nexus.stream.domain.repository.PlayerPrefsRepository
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
    private val playerPrefsRepository = mockk<PlayerPrefsRepository>(relaxed = true)
    private val addonRepository = mockk<AddonRepository>()

    private val familyModeFlow = MutableStateFlow(true)
    private val quickPlayFlow = MutableStateFlow(false)
    private val preferredLanguageFlow = MutableStateFlow<String?>(null)
    private val preferredQualityFlow = MutableStateFlow<Int?>(null)
    private val hideCamFlow = MutableStateFlow(false)
    private val seekStepSecondsFlow = MutableStateFlow(10)
    private val preferredAudioLanguageFlow = MutableStateFlow<String?>(null)
    private val preferredSubtitleLanguageFlow = MutableStateFlow<String?>(null)
    private val subtitleStyleFlow =
        MutableStateFlow(
            com.nexus.stream.domain.model
                .SubtitleStyle(),
        )
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
        every { playerPrefsRepository.quickPlay } returns quickPlayFlow
        every { playerPrefsRepository.preferredLanguage } returns preferredLanguageFlow
        every { playerPrefsRepository.preferredQuality } returns preferredQualityFlow
        every { playerPrefsRepository.hideCam } returns hideCamFlow
        every { playerPrefsRepository.seekStepSeconds } returns seekStepSecondsFlow
        every { playerPrefsRepository.preferredAudioLanguage } returns preferredAudioLanguageFlow
        every { playerPrefsRepository.preferredSubtitleLanguage } returns preferredSubtitleLanguageFlow
        every { playerPrefsRepository.subtitleStyle } returns subtitleStyleFlow
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
            val viewModel = SettingsViewModel(contentPrefsRepository, playerPrefsRepository, addonRepository)
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.familyMode)
            assertEquals(1, viewModel.uiState.value.hiddenAdultAddonsCount)
            assertFalse(viewModel.uiState.value.quickPlay)
        }

    @Test
    fun `hidden adult count is 0 when family mode is off`() =
        runTest {
            familyModeFlow.value = false
            addonsFlow.value = listOf(adultAddon, standardAddon)
            val viewModel = SettingsViewModel(contentPrefsRepository, playerPrefsRepository, addonRepository)
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.familyMode)
            assertEquals(0, viewModel.uiState.value.hiddenAdultAddonsCount)
        }

    @Test
    fun `SetFamilyMode event updates repository`() =
        runTest {
            val viewModel = SettingsViewModel(contentPrefsRepository, playerPrefsRepository, addonRepository)
            advanceUntilIdle()

            viewModel.onEvent(SettingsEvent.SetFamilyMode(false))
            advanceUntilIdle()

            coVerify { contentPrefsRepository.setFamilyMode(false) }
        }

    @Test
    fun `quickPlay reflects repository value`() =
        runTest {
            quickPlayFlow.value = true
            val viewModel = SettingsViewModel(contentPrefsRepository, playerPrefsRepository, addonRepository)
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.quickPlay)
        }

    @Test
    fun `SetQuickPlay event updates repository`() =
        runTest {
            val viewModel = SettingsViewModel(contentPrefsRepository, playerPrefsRepository, addonRepository)
            advanceUntilIdle()

            viewModel.onEvent(SettingsEvent.SetQuickPlay(true))
            advanceUntilIdle()

            coVerify { playerPrefsRepository.setQuickPlay(true) }
        }

    @Test
    fun `preferredLanguage reflects repository and updates on SetPreferredLanguage`() =
        runTest {
            preferredLanguageFlow.value = "fr"
            val viewModel = SettingsViewModel(contentPrefsRepository, playerPrefsRepository, addonRepository)
            advanceUntilIdle()

            assertEquals("fr", viewModel.uiState.value.preferredLanguage)

            viewModel.onEvent(SettingsEvent.SetPreferredLanguage("en"))
            advanceUntilIdle()

            coVerify { playerPrefsRepository.setPreferredLanguage("en") }
        }

    @Test
    fun `preferredQuality reflects repository and updates on SetPreferredQuality`() =
        runTest {
            preferredQualityFlow.value = 1080
            val viewModel = SettingsViewModel(contentPrefsRepository, playerPrefsRepository, addonRepository)
            advanceUntilIdle()

            assertEquals(1080, viewModel.uiState.value.preferredQuality)

            viewModel.onEvent(SettingsEvent.SetPreferredQuality(2160))
            advanceUntilIdle()

            coVerify { playerPrefsRepository.setPreferredQuality(2160) }
        }

    @Test
    fun `hideCam reflects repository and updates on SetHideCam`() =
        runTest {
            hideCamFlow.value = true
            val viewModel = SettingsViewModel(contentPrefsRepository, playerPrefsRepository, addonRepository)
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.hideCam)

            viewModel.onEvent(SettingsEvent.SetHideCam(false))
            advanceUntilIdle()

            coVerify { playerPrefsRepository.setHideCam(false) }
        }

    @Test
    fun `SS1 seekStepSeconds reflects repository value`() =
        runTest {
            seekStepSecondsFlow.value = 15
            val viewModel = SettingsViewModel(contentPrefsRepository, playerPrefsRepository, addonRepository)
            advanceUntilIdle()

            assertEquals(15, viewModel.uiState.value.seekStepSeconds)
        }

    @Test
    fun `SS2 SetSeekStepSeconds event updates repository`() =
        runTest {
            val viewModel = SettingsViewModel(contentPrefsRepository, playerPrefsRepository, addonRepository)
            advanceUntilIdle()

            viewModel.onEvent(SettingsEvent.SetSeekStepSeconds(20))
            advanceUntilIdle()

            coVerify { playerPrefsRepository.setSeekStepSeconds(20) }
        }

    @Test
    fun `SS3 state mirrors stored languages and style`() =
        runTest {
            preferredAudioLanguageFlow.value = "es"
            preferredSubtitleLanguageFlow.value = "fr"
            val customStyle =
                SubtitleStyle(
                    size = SubtitleSize.HUGE,
                    colorId = "yellow",
                    background = SubtitleBackground.SOLID,
                    edge = SubtitleEdge.SHADOW,
                    bold = true,
                )
            subtitleStyleFlow.value = customStyle

            val viewModel = SettingsViewModel(contentPrefsRepository, playerPrefsRepository, addonRepository)
            advanceUntilIdle()

            assertEquals("es", viewModel.uiState.value.preferredAudioLanguage)
            assertEquals("fr", viewModel.uiState.value.preferredSubtitleLanguage)
            assertEquals(customStyle, viewModel.uiState.value.subtitleStyle)
        }

    @Test
    fun `SS4 each new event writes through to repository`() =
        runTest {
            val viewModel = SettingsViewModel(contentPrefsRepository, playerPrefsRepository, addonRepository)
            advanceUntilIdle()

            viewModel.onEvent(SettingsEvent.SetPreferredSubtitleLanguage("off"))
            advanceUntilIdle()
            coVerify { playerPrefsRepository.setPreferredSubtitleLanguage("off") }

            viewModel.onEvent(SettingsEvent.SetPreferredAudioLanguage(null))
            advanceUntilIdle()
            coVerify { playerPrefsRepository.setPreferredAudioLanguage(null) }

            viewModel.onEvent(SettingsEvent.SetSubtitleSize(SubtitleSize.BIG))
            advanceUntilIdle()
            coVerify { playerPrefsRepository.setSubtitleSize(SubtitleSize.BIG) }

            viewModel.onEvent(SettingsEvent.SetSubtitleColor("cyan"))
            advanceUntilIdle()
            coVerify { playerPrefsRepository.setSubtitleColor("cyan") }

            viewModel.onEvent(SettingsEvent.SetSubtitleBackground(SubtitleBackground.DIM))
            advanceUntilIdle()
            coVerify { playerPrefsRepository.setSubtitleBackground(SubtitleBackground.DIM) }

            viewModel.onEvent(SettingsEvent.SetSubtitleEdge(SubtitleEdge.NONE))
            advanceUntilIdle()
            coVerify { playerPrefsRepository.setSubtitleEdge(SubtitleEdge.NONE) }

            viewModel.onEvent(SettingsEvent.SetSubtitleBold(true))
            advanceUntilIdle()
            coVerify { playerPrefsRepository.setSubtitleBold(true) }
        }

    @Test
    fun `SS5 defaults when nothing is stored`() =
        runTest {
            val viewModel = SettingsViewModel(contentPrefsRepository, playerPrefsRepository, addonRepository)
            advanceUntilIdle()

            assertEquals(null, viewModel.uiState.value.preferredAudioLanguage)
            assertEquals(null, viewModel.uiState.value.preferredSubtitleLanguage)
            assertEquals(SubtitleStyle(), viewModel.uiState.value.subtitleStyle)
            assertEquals(SubtitleSize.NORMAL, viewModel.uiState.value.subtitleStyle.size)
            assertEquals("white", viewModel.uiState.value.subtitleStyle.colorId)
            assertEquals(SubtitleBackground.NONE, viewModel.uiState.value.subtitleStyle.background)
            assertEquals(SubtitleEdge.OUTLINE, viewModel.uiState.value.subtitleStyle.edge)
            assertEquals(false, viewModel.uiState.value.subtitleStyle.bold)
        }
}
