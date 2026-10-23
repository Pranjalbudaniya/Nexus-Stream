package com.nexus.stream.presentation.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexus.stream.domain.model.SubtitleBackground
import com.nexus.stream.domain.model.SubtitleEdge
import com.nexus.stream.domain.model.SubtitleSize
import com.nexus.stream.domain.model.SubtitleStyle
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
    val preferredAudioLanguage: String? = null,
    val preferredSubtitleLanguage: String? = null,
    val subtitleStyle: SubtitleStyle = SubtitleStyle(),
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

    data class SetPreferredAudioLanguage(
        val code: String?,
    ) : SettingsEvent

    data class SetPreferredSubtitleLanguage(
        val code: String?,
    ) : SettingsEvent

    data class SetSubtitleSize(
        val size: SubtitleSize,
    ) : SettingsEvent

    data class SetSubtitleColor(
        val colorId: String,
    ) : SettingsEvent

    data class SetSubtitleBackground(
        val background: SubtitleBackground,
    ) : SettingsEvent

    data class SetSubtitleEdge(
        val edge: SubtitleEdge,
    ) : SettingsEvent

    data class SetSubtitleBold(
        val bold: Boolean,
    ) : SettingsEvent
}

private data class PlayerSettings(
    val quickPlay: Boolean,
    val preferredLanguage: String?,
    val preferredQuality: Int?,
    val hideCam: Boolean,
    val seekStepSeconds: Int,
)

private data class SubtitlePrefs(
    val preferredAudioLanguage: String?,
    val preferredSubtitleLanguage: String?,
    val subtitleStyle: SubtitleStyle,
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

        private val subtitlePrefsFlow =
            combine(
                playerPrefsRepository.preferredAudioLanguage,
                playerPrefsRepository.preferredSubtitleLanguage,
                playerPrefsRepository.subtitleStyle,
            ) { audio, sub, style ->
                SubtitlePrefs(audio, sub, style)
            }

        val uiState: StateFlow<SettingsUiState> =
            combine(
                contentPrefsRepository.familyMode,
                addonRepository.observeAll(),
                playerSettingsFlow,
                subtitlePrefsFlow,
            ) { familyMode, allAddons, player, subPrefs ->
                val hiddenCount = if (familyMode) allAddons.count { it.isAdult } else 0
                SettingsUiState(
                    familyMode = familyMode,
                    hiddenAdultAddonsCount = hiddenCount,
                    quickPlay = player.quickPlay,
                    preferredLanguage = player.preferredLanguage,
                    preferredQuality = player.preferredQuality,
                    hideCam = player.hideCam,
                    seekStepSeconds = player.seekStepSeconds,
                    preferredAudioLanguage = subPrefs.preferredAudioLanguage,
                    preferredSubtitleLanguage = subPrefs.preferredSubtitleLanguage,
                    subtitleStyle = subPrefs.subtitleStyle,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = SettingsUiState(),
            )

        fun onEvent(event: SettingsEvent) {
            if (!handleGeneralSettingsEvent(event)) {
                handleSubtitleSettingsEvent(event)
            }
        }

        private fun handleGeneralSettingsEvent(event: SettingsEvent): Boolean =
            when (event) {
                is SettingsEvent.SetFamilyMode -> {
                    viewModelScope.launch { contentPrefsRepository.setFamilyMode(event.enabled) }
                    true
                }

                is SettingsEvent.SetQuickPlay -> {
                    viewModelScope.launch { playerPrefsRepository.setQuickPlay(event.enabled) }
                    true
                }

                is SettingsEvent.SetPreferredLanguage -> {
                    viewModelScope.launch { playerPrefsRepository.setPreferredLanguage(event.code) }
                    true
                }

                is SettingsEvent.SetPreferredQuality -> {
                    viewModelScope.launch { playerPrefsRepository.setPreferredQuality(event.quality) }
                    true
                }

                is SettingsEvent.SetHideCam -> {
                    viewModelScope.launch { playerPrefsRepository.setHideCam(event.enabled) }
                    true
                }

                is SettingsEvent.SetSeekStepSeconds -> {
                    viewModelScope.launch { playerPrefsRepository.setSeekStepSeconds(event.seconds) }
                    true
                }

                else -> {
                    false
                }
            }

        private fun handleSubtitleSettingsEvent(event: SettingsEvent) {
            when (event) {
                is SettingsEvent.SetPreferredAudioLanguage -> {
                    viewModelScope.launch { playerPrefsRepository.setPreferredAudioLanguage(event.code) }
                }

                is SettingsEvent.SetPreferredSubtitleLanguage -> {
                    viewModelScope.launch { playerPrefsRepository.setPreferredSubtitleLanguage(event.code) }
                }

                is SettingsEvent.SetSubtitleSize -> {
                    viewModelScope.launch { playerPrefsRepository.setSubtitleSize(event.size) }
                }

                is SettingsEvent.SetSubtitleColor -> {
                    viewModelScope.launch { playerPrefsRepository.setSubtitleColor(event.colorId) }
                }

                is SettingsEvent.SetSubtitleBackground -> {
                    viewModelScope.launch { playerPrefsRepository.setSubtitleBackground(event.background) }
                }

                is SettingsEvent.SetSubtitleEdge -> {
                    viewModelScope.launch { playerPrefsRepository.setSubtitleEdge(event.edge) }
                }

                is SettingsEvent.SetSubtitleBold -> {
                    viewModelScope.launch { playerPrefsRepository.setSubtitleBold(event.bold) }
                }

                else -> {}
            }
        }
    }
