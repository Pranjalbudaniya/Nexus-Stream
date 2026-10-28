package com.nexus.stream.presentation.feature.settings

import com.nexus.stream.domain.model.AppFont
import com.nexus.stream.domain.model.CardStyle
import com.nexus.stream.domain.model.ContrastLevel
import com.nexus.stream.domain.model.Roundness
import com.nexus.stream.domain.model.TextSize
import com.nexus.stream.domain.model.ThemeMode
import com.nexus.stream.domain.model.ThemeSettings
import com.nexus.stream.domain.model.UiSize
import com.nexus.stream.domain.repository.SettingsRepository
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppearanceViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)
    private val themeSettingsFlow = MutableStateFlow(ThemeSettings())

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { settingsRepository.themeSettings } returns themeSettingsFlow
        coEvery { settingsRepository.setThemeMode(any()) } answers {
            val mode = firstArg<ThemeMode>()
            themeSettingsFlow.value = themeSettingsFlow.value.copy(mode = mode)
        }
        coEvery { settingsRepository.setDynamicColor(any()) } answers {
            val dynamic = firstArg<Boolean>()
            themeSettingsFlow.value = themeSettingsFlow.value.copy(dynamicColor = dynamic)
        }
        coEvery { settingsRepository.setAmoled(any()) } answers {
            val amoled = firstArg<Boolean>()
            themeSettingsFlow.value = themeSettingsFlow.value.copy(amoled = amoled)
        }
        coEvery { settingsRepository.setAccentColor(any(), any()) } answers {
            val argb = firstArg<Long?>()
            themeSettingsFlow.value = themeSettingsFlow.value.copy(accentColor = argb)
        }
        coEvery { settingsRepository.update(any()) } answers {
            val transform = firstArg<(ThemeSettings) -> ThemeSettings>()
            themeSettingsFlow.value = transform(themeSettingsFlow.value)
        }
        coEvery { settingsRepository.resetTheme() } answers {
            themeSettingsFlow.value = ThemeSettings()
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun AV1_state_mirrorsThemeSettings() =
        runTest {
            themeSettingsFlow.value =
                ThemeSettings(
                    mode = ThemeMode.DARK,
                    font = AppFont.OUTFIT,
                )
            val viewModel = AppearanceViewModel(settingsRepository)
            advanceUntilIdle()

            assertEquals(ThemeMode.DARK, viewModel.uiState.value.settings.mode)
            assertEquals(AppFont.OUTFIT, viewModel.uiState.value.settings.font)
        }

    @Test
    fun AV2_setThemeMode_callsRepository() =
        runTest {
            val viewModel = AppearanceViewModel(settingsRepository)
            advanceUntilIdle()

            viewModel.onEvent(AppearanceEvent.SetThemeMode(ThemeMode.LIGHT))
            advanceUntilIdle()

            coVerify(exactly = 1) { settingsRepository.setThemeMode(ThemeMode.LIGHT) }
            assertEquals(ThemeMode.LIGHT, viewModel.uiState.value.settings.mode)
        }

    @Test
    fun AV3_setDynamicColor_callsRepository() =
        runTest {
            val viewModel = AppearanceViewModel(settingsRepository)
            advanceUntilIdle()

            viewModel.onEvent(AppearanceEvent.SetDynamicColor(true))
            advanceUntilIdle()

            coVerify(exactly = 1) { settingsRepository.setDynamicColor(true) }
            assertTrue(viewModel.uiState.value.settings.dynamicColor)
        }

    @Test
    fun AV4_setAmoled_callsRepository() =
        runTest {
            val viewModel = AppearanceViewModel(settingsRepository)
            advanceUntilIdle()

            viewModel.onEvent(AppearanceEvent.SetAmoled(true))
            advanceUntilIdle()

            coVerify(exactly = 1) { settingsRepository.setAmoled(true) }
            assertTrue(viewModel.uiState.value.settings.amoled)
        }

    @Test
    fun AV5_pickAccent_callsSetAccentColor_withoutRemember() =
        runTest {
            val targetColor = 0xFF2979C9L
            val viewModel = AppearanceViewModel(settingsRepository)
            advanceUntilIdle()

            viewModel.onEvent(AppearanceEvent.PickAccent(targetColor))
            advanceUntilIdle()

            coVerify(exactly = 1) { settingsRepository.setAccentColor(targetColor, false) }
        }

    @Test
    fun AV6_applyCustomAccent_remembers() =
        runTest {
            val customColor = 0xFF123456L
            val viewModel = AppearanceViewModel(settingsRepository)
            advanceUntilIdle()

            viewModel.onEvent(AppearanceEvent.ApplyCustomAccent(customColor))
            advanceUntilIdle()

            coVerify(exactly = 1) { settingsRepository.setAccentColor(customColor, true) }
        }

    @Test
    fun AV7_turnOffDynamic_setsDynamicFalse_keepsAccent() =
        runTest {
            val accent = 0xFF6D5EF5L
            themeSettingsFlow.value =
                ThemeSettings(
                    dynamicColor = true,
                    accentColor = accent,
                )
            val viewModel = AppearanceViewModel(settingsRepository)
            advanceUntilIdle()

            viewModel.onEvent(AppearanceEvent.TurnOffDynamic)
            advanceUntilIdle()

            coVerify(exactly = 1) { settingsRepository.setDynamicColor(false) }
            assertFalse(viewModel.uiState.value.settings.dynamicColor)
            assertEquals(accent, viewModel.uiState.value.settings.accentColor)
        }

    @Test
    fun AV8_setFont_updatesOnlyFont() =
        runTest {
            val viewModel = AppearanceViewModel(settingsRepository)
            advanceUntilIdle()

            val before = viewModel.uiState.value.settings
            viewModel.onEvent(AppearanceEvent.SetFont(AppFont.POPPINS))
            advanceUntilIdle()

            val after = viewModel.uiState.value.settings
            assertEquals(AppFont.POPPINS, after.font)
            assertEquals(before.copy(font = AppFont.POPPINS), after)
        }

    @Test
    fun AV9_setUiSize_updatesOnlyUiSize() =
        runTest {
            val viewModel = AppearanceViewModel(settingsRepository)
            advanceUntilIdle()

            val before = viewModel.uiState.value.settings
            viewModel.onEvent(AppearanceEvent.SetUiSize(UiSize.LARGE))
            advanceUntilIdle()

            val after = viewModel.uiState.value.settings
            assertEquals(UiSize.LARGE, after.uiSize)
            assertEquals(before.copy(uiSize = UiSize.LARGE), after)
        }

    @Test
    fun AV10_setTextSize_updatesOnlyTextSize() =
        runTest {
            val viewModel = AppearanceViewModel(settingsRepository)
            advanceUntilIdle()

            val before = viewModel.uiState.value.settings
            viewModel.onEvent(AppearanceEvent.SetTextSize(TextSize.LARGER))
            advanceUntilIdle()

            val after = viewModel.uiState.value.settings
            assertEquals(TextSize.LARGER, after.textSize)
            assertEquals(before.copy(textSize = TextSize.LARGER), after)
        }

    @Test
    fun AV11_setRoundness_updatesOnlyRoundness() =
        runTest {
            val viewModel = AppearanceViewModel(settingsRepository)
            advanceUntilIdle()

            val before = viewModel.uiState.value.settings
            viewModel.onEvent(AppearanceEvent.SetRoundness(Roundness.SHARP))
            advanceUntilIdle()

            val after = viewModel.uiState.value.settings
            assertEquals(Roundness.SHARP, after.roundness)
            assertEquals(before.copy(roundness = Roundness.SHARP), after)
        }

    @Test
    fun AV12_setCardStyle_updatesOnlyCardStyle() =
        runTest {
            val viewModel = AppearanceViewModel(settingsRepository)
            advanceUntilIdle()

            val before = viewModel.uiState.value.settings
            viewModel.onEvent(AppearanceEvent.SetCardStyle(CardStyle.OUTLINED))
            advanceUntilIdle()

            val after = viewModel.uiState.value.settings
            assertEquals(CardStyle.OUTLINED, after.cardStyle)
            assertEquals(before.copy(cardStyle = CardStyle.OUTLINED), after)
        }

    @Test
    fun AV13_setContrast_updatesOnlyContrast() =
        runTest {
            val viewModel = AppearanceViewModel(settingsRepository)
            advanceUntilIdle()

            val before = viewModel.uiState.value.settings
            viewModel.onEvent(AppearanceEvent.SetContrast(ContrastLevel.HIGH))
            advanceUntilIdle()

            val after = viewModel.uiState.value.settings
            assertEquals(ContrastLevel.HIGH, after.contrast)
            assertEquals(before.copy(contrast = ContrastLevel.HIGH), after)
        }

    @Test
    fun AV14_requestReset_showsConfirm_thenResetsTheme() =
        runTest {
            val viewModel = AppearanceViewModel(settingsRepository)
            advanceUntilIdle()

            viewModel.onEvent(AppearanceEvent.RequestReset)
            assertTrue(viewModel.uiState.value.showResetConfirm)

            viewModel.onEvent(AppearanceEvent.ConfirmReset)
            advanceUntilIdle()

            coVerify(exactly = 1) { settingsRepository.resetTheme() }
            assertFalse(viewModel.uiState.value.showResetConfirm)
        }

    @Test
    fun AV15_dismissReset_doesNothing() =
        runTest {
            val viewModel = AppearanceViewModel(settingsRepository)
            advanceUntilIdle()

            viewModel.onEvent(AppearanceEvent.RequestReset)
            assertTrue(viewModel.uiState.value.showResetConfirm)

            viewModel.onEvent(AppearanceEvent.DismissReset)
            assertFalse(viewModel.uiState.value.showResetConfirm)
            coVerify(exactly = 0) { settingsRepository.resetTheme() }
        }

    @Test
    fun AV16_dynamicColorOn_flagsAccentAndContrastDisabled() =
        runTest {
            themeSettingsFlow.value = ThemeSettings(dynamicColor = true)
            val viewModel = AppearanceViewModel(settingsRepository)
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.accentEnabled)
            assertFalse(viewModel.uiState.value.contrastEnabled)

            themeSettingsFlow.value = ThemeSettings(dynamicColor = false)
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.accentEnabled)
            assertTrue(viewModel.uiState.value.contrastEnabled)
        }

    @Test
    fun AV17_amoledCaption_onlyWhenModeIsLight() =
        runTest {
            themeSettingsFlow.value = ThemeSettings(mode = ThemeMode.LIGHT)
            val viewModel = AppearanceViewModel(settingsRepository)
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.amoledAppliesInDarkOnlyNote)

            themeSettingsFlow.value = ThemeSettings(mode = ThemeMode.DARK)
            advanceUntilIdle()
            assertFalse(viewModel.uiState.value.amoledAppliesInDarkOnlyNote)

            themeSettingsFlow.value = ThemeSettings(mode = ThemeMode.SYSTEM)
            advanceUntilIdle()
            assertFalse(viewModel.uiState.value.amoledAppliesInDarkOnlyNote)
        }
}
