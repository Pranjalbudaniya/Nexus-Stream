package com.nexus.stream.presentation.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexus.stream.domain.repository.AddonRepository
import com.nexus.stream.domain.repository.ContentPrefsRepository
import com.nexus.stream.domain.repository.PlayerPrefsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val familyMode: Boolean = true,
    val hiddenAdultAddonsCount: Int = 0,
    val quickPlay: Boolean = false,
    val preferredLanguage: String? = null,
    val preferredQuality: Int? = null,
    val hideCam: Boolean = false,
    val seekStepSeconds: Int = 10,
)

sealed interface SettingsEvent {
    data class SetFamilyMode(
        val enabled: Boolean,
    ) : SettingsEvent

    data class SetQuickPlay(
        val enabled: Boolean,
    ) : SettingsEvent

    data class SetPreferredLanguage(
        val code: String?,
    ) : SettingsEvent

    data class SetPreferredQuality(
        val quality: Int?,
    ) : SettingsEvent

    data class SetHideCam(
        val enabled: Boolean,
    ) : SettingsEvent

    data class SetSeekStepSeconds(
        val seconds: Int,
    ) : SettingsEvent
}

private data class PlayerSettings(
    val quickPlay: Boolean,
    val preferredLanguage: String?,
    val preferredQuality: Int?,
    val hideCam: Boolean,
    val seekStepSeconds: Int,
)

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val contentPrefsRepository: ContentPrefsRepository,
        private val playerPrefsRepository: PlayerPrefsRepository,
        addonRepository: AddonRepository,
    ) : ViewModel() {
        private val playerSettingsFlow =
            combine(
                playerPrefsRepository.quickPlay,
                playerPrefsRepository.preferredLanguage,
                playerPrefsRepository.preferredQuality,
                playerPrefsRepository.hideCam,
                playerPrefsRepository.seekStepSeconds,
            ) { quickPlay, lang, quality, hideCam, seekStepSeconds ->
                PlayerSettings(quickPlay, lang, quality, hideCam, seekStepSeconds)
            }

        val uiState: StateFlow<SettingsUiState> =
            combine(
                contentPrefsRepository.familyMode,
                addonRepository.observeAll(),
                playerSettingsFlow,
            ) { familyMode, allAddons, player ->
                val hiddenCount = if (familyMode) allAddons.count { it.isAdult } else 0
                SettingsUiState(
                    familyMode = familyMode,
                    hiddenAdultAddonsCount = hiddenCount,
                    quickPlay = player.quickPlay,
                    preferredLanguage = player.preferredLanguage,
                    preferredQuality = player.preferredQuality,
                    hideCam = player.hideCam,
                    seekStepSeconds = player.seekStepSeconds,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = SettingsUiState(),
            )

        fun onEvent(event: SettingsEvent) {
            when (event) {
                is SettingsEvent.SetFamilyMode -> {
                    viewModelScope.launch {
                        contentPrefsRepository.setFamilyMode(event.enabled)
                    }
                }

                is SettingsEvent.SetQuickPlay -> {
                    viewModelScope.launch {
                        playerPrefsRepository.setQuickPlay(event.enabled)
                    }
                }

                is SettingsEvent.SetPreferredLanguage -> {
                    viewModelScope.launch {
                        playerPrefsRepository.setPreferredLanguage(event.code)
                    }
                }

                is SettingsEvent.SetPreferredQuality -> {
                    viewModelScope.launch {
                        playerPrefsRepository.setPreferredQuality(event.quality)
                    }
                }

                is SettingsEvent.SetHideCam -> {
                    viewModelScope.launch {
                        playerPrefsRepository.setHideCam(event.enabled)
                    }
                }

                is SettingsEvent.SetSeekStepSeconds -> {
                    viewModelScope.launch {
                        playerPrefsRepository.setSeekStepSeconds(event.seconds)
                    }
                }
            }
        }
    }
