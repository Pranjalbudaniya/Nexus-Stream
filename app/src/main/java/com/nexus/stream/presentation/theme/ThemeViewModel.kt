package com.nexus.stream.presentation.theme

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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ThemeUiState {
    data object Loading : ThemeUiState

    data class Ready(
        val settings: ThemeSettings,
        val previewAccent: Long?,
    ) : ThemeUiState
}

sealed interface ThemeEvent {
    data class SetMode(
        val mode: ThemeMode,
    ) : ThemeEvent

    data class SetDynamicColor(
        val enabled: Boolean,
    ) : ThemeEvent

    data class SetAmoled(
        val enabled: Boolean,
    ) : ThemeEvent

    data class SetAccent(
        val argb: Long?,
        val remember: Boolean,
    ) : ThemeEvent

    data class PreviewAccent(
        val argb: Long?,
    ) : ThemeEvent

    data class SetFont(
        val font: AppFont,
    ) : ThemeEvent

    data class SetUiSize(
        val uiSize: UiSize,
    ) : ThemeEvent

    data class SetTextSize(
        val textSize: TextSize,
    ) : ThemeEvent

    data class SetRoundness(
        val roundness: Roundness,
    ) : ThemeEvent

    data class SetCardStyle(
        val cardStyle: CardStyle,
    ) : ThemeEvent

    data class SetContrast(
        val contrast: ContrastLevel,
    ) : ThemeEvent

    data object ResetAppearance : ThemeEvent
}

@HiltViewModel
class ThemeViewModel
    @Inject
    constructor(
        private val repo: SettingsRepository,
    ) : ViewModel() {
        private val previewAccent = MutableStateFlow<Long?>(null)

        val uiState: StateFlow<ThemeUiState> =
            combine(
                repo.themeSettings,
                previewAccent,
            ) { settings, preview ->
                ThemeUiState.Ready(settings = settings, previewAccent = preview)
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = ThemeUiState.Loading,
            )

        val effects: Flow<Nothing> = emptyFlow()

        fun onEvent(event: ThemeEvent) {
            when (event) {
                is ThemeEvent.SetMode -> {
                    viewModelScope.launch {
                        repo.setThemeMode(event.mode)
                    }
                }

                is ThemeEvent.SetDynamicColor -> {
                    viewModelScope.launch {
                        repo.setDynamicColor(event.enabled)
                    }
                }

                is ThemeEvent.SetAmoled -> {
                    viewModelScope.launch {
                        repo.setAmoled(event.enabled)
                    }
                }

                is ThemeEvent.SetAccent -> {
                    viewModelScope.launch {
                        previewAccent.value = null
                        repo.setAccentColor(event.argb, remember = event.remember)
                    }
                }

                is ThemeEvent.PreviewAccent -> {
                    previewAccent.value = event.argb
                }

                is ThemeEvent.SetFont -> {
                    viewModelScope.launch {
                        repo.update { it.copy(font = event.font) }
                    }
                }

                is ThemeEvent.SetUiSize -> {
                    viewModelScope.launch {
                        repo.update { it.copy(uiSize = event.uiSize) }
                    }
                }

                is ThemeEvent.SetTextSize -> {
                    viewModelScope.launch {
                        repo.update { it.copy(textSize = event.textSize) }
                    }
                }

                is ThemeEvent.SetRoundness -> {
                    viewModelScope.launch {
                        repo.update { it.copy(roundness = event.roundness) }
                    }
                }

                is ThemeEvent.SetCardStyle -> {
                    viewModelScope.launch {
                        repo.update { it.copy(cardStyle = event.cardStyle) }
                    }
                }

                is ThemeEvent.SetContrast -> {
                    viewModelScope.launch {
                        repo.update { it.copy(contrast = event.contrast) }
                    }
                }

                is ThemeEvent.ResetAppearance -> {
                    viewModelScope.launch {
                        previewAccent.value = null
                        repo.resetTheme()
                    }
                }
            }
        }
    }
