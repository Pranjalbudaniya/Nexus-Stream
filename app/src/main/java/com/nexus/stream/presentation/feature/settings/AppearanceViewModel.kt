package com.nexus.stream.presentation.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexus.stream.domain.model.AppFont
import com.nexus.stream.domain.model.CardStyle
import com.nexus.stream.domain.model.ContrastLevel
import com.nexus.stream.domain.model.Roundness
import com.nexus.stream.domain.model.TextSize
import com.nexus.stream.domain.model.ThemeMode
import com.nexus.stream.domain.model.ThemeSettings
import com.nexus.stream.domain.model.UiSize
import com.nexus.stream.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppearanceUiState(
    val settings: ThemeSettings = ThemeSettings(),
    val showResetConfirm: Boolean = false,
    val showCustomColorSheet: Boolean = false,
    val showFontPickerSheet: Boolean = false,
) {
    val accentEnabled: Boolean
        get() = !settings.dynamicColor

    val contrastEnabled: Boolean
        get() = !settings.dynamicColor

    val amoledAppliesInDarkOnlyNote: Boolean
        get() = settings.mode == ThemeMode.LIGHT
}

sealed interface AppearanceEvent {
    data class SetThemeMode(
        val mode: ThemeMode,
    ) : AppearanceEvent

    data class SetDynamicColor(
        val enabled: Boolean,
    ) : AppearanceEvent

    data class SetAmoled(
        val enabled: Boolean,
    ) : AppearanceEvent

    data class PickAccent(
        val argb: Long,
    ) : AppearanceEvent

    data class ApplyCustomAccent(
        val argb: Long,
    ) : AppearanceEvent

    data object TurnOffDynamic : AppearanceEvent

    data class SetFont(
        val font: AppFont,
    ) : AppearanceEvent

    data class SetUiSize(
        val size: UiSize,
    ) : AppearanceEvent

    data class SetTextSize(
        val size: TextSize,
    ) : AppearanceEvent

    data class SetRoundness(
        val roundness: Roundness,
    ) : AppearanceEvent

    data class SetCardStyle(
        val style: CardStyle,
    ) : AppearanceEvent

    data class SetContrast(
        val contrast: ContrastLevel,
    ) : AppearanceEvent

    data object RequestReset : AppearanceEvent

    data object ConfirmReset : AppearanceEvent

    data object DismissReset : AppearanceEvent

    data object OpenCustomColor : AppearanceEvent

    data object DismissCustomColor : AppearanceEvent

    data object OpenFontPicker : AppearanceEvent

    data object DismissFontPicker : AppearanceEvent
}

@HiltViewModel
class AppearanceViewModel
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(AppearanceUiState())
        val uiState: StateFlow<AppearanceUiState> = _uiState.asStateFlow()

        init {
            viewModelScope.launch {
                settingsRepository.themeSettings.collect { settings ->
                    _uiState.update { it.copy(settings = settings) }
                }
            }
        }

        fun onEvent(event: AppearanceEvent) {
            when (event) {
                is AppearanceEvent.SetThemeMode,
                is AppearanceEvent.SetDynamicColor,
                is AppearanceEvent.SetAmoled,
                is AppearanceEvent.PickAccent,
                is AppearanceEvent.ApplyCustomAccent,
                AppearanceEvent.TurnOffDynamic,
                -> handleThemeEvent(event)

                is AppearanceEvent.SetFont,
                is AppearanceEvent.SetUiSize,
                is AppearanceEvent.SetTextSize,
                is AppearanceEvent.SetRoundness,
                is AppearanceEvent.SetCardStyle,
                is AppearanceEvent.SetContrast,
                -> handleStyleEvent(event)

                AppearanceEvent.RequestReset,
                AppearanceEvent.ConfirmReset,
                AppearanceEvent.DismissReset,
                AppearanceEvent.OpenCustomColor,
                AppearanceEvent.DismissCustomColor,
                AppearanceEvent.OpenFontPicker,
                AppearanceEvent.DismissFontPicker,
                -> handleDialogEvent(event)
            }
        }

        private fun handleThemeEvent(event: AppearanceEvent) {
            when (event) {
                is AppearanceEvent.SetThemeMode -> {
                    viewModelScope.launch { settingsRepository.setThemeMode(event.mode) }
                }

                is AppearanceEvent.SetDynamicColor -> {
                    viewModelScope.launch { settingsRepository.setDynamicColor(event.enabled) }
                }

                is AppearanceEvent.SetAmoled -> {
                    viewModelScope.launch { settingsRepository.setAmoled(event.enabled) }
                }

                is AppearanceEvent.PickAccent -> {
                    viewModelScope.launch { settingsRepository.setAccentColor(event.argb, remember = false) }
                }

                is AppearanceEvent.ApplyCustomAccent -> {
                    viewModelScope.launch { settingsRepository.setAccentColor(event.argb, remember = true) }
                }

                AppearanceEvent.TurnOffDynamic -> {
                    viewModelScope.launch { settingsRepository.setDynamicColor(false) }
                }

                else -> {}
            }
        }

        private fun handleStyleEvent(event: AppearanceEvent) {
            when (event) {
                is AppearanceEvent.SetFont -> {
                    viewModelScope.launch { settingsRepository.update { it.copy(font = event.font) } }
                }

                is AppearanceEvent.SetUiSize -> {
                    viewModelScope.launch { settingsRepository.update { it.copy(uiSize = event.size) } }
                }

                is AppearanceEvent.SetTextSize -> {
                    viewModelScope.launch { settingsRepository.update { it.copy(textSize = event.size) } }
                }

                is AppearanceEvent.SetRoundness -> {
                    viewModelScope.launch { settingsRepository.update { it.copy(roundness = event.roundness) } }
                }

                is AppearanceEvent.SetCardStyle -> {
                    viewModelScope.launch { settingsRepository.update { it.copy(cardStyle = event.style) } }
                }

                is AppearanceEvent.SetContrast -> {
                    viewModelScope.launch { settingsRepository.update { it.copy(contrast = event.contrast) } }
                }

                else -> {}
            }
        }

        private fun handleDialogEvent(event: AppearanceEvent) {
            when (event) {
                AppearanceEvent.RequestReset -> {
                    _uiState.update { it.copy(showResetConfirm = true) }
                }

                AppearanceEvent.ConfirmReset -> {
                    _uiState.update { it.copy(showResetConfirm = false) }
                    viewModelScope.launch { settingsRepository.resetTheme() }
                }

                AppearanceEvent.DismissReset -> {
                    _uiState.update { it.copy(showResetConfirm = false) }
                }

                AppearanceEvent.OpenCustomColor -> {
                    _uiState.update { it.copy(showCustomColorSheet = true) }
                }

                AppearanceEvent.DismissCustomColor -> {
                    _uiState.update { it.copy(showCustomColorSheet = false) }
                }

                AppearanceEvent.OpenFontPicker -> {
                    _uiState.update { it.copy(showFontPickerSheet = true) }
                }

                AppearanceEvent.DismissFontPicker -> {
                    _uiState.update { it.copy(showFontPickerSheet = false) }
                }

                else -> {}
            }
        }
    }
