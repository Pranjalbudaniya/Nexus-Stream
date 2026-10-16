package com.nexus.stream.presentation.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexus.stream.domain.repository.AddonRepository
import com.nexus.stream.domain.repository.ContentPrefsRepository
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
)

sealed interface SettingsEvent {
    data class SetFamilyMode(
        val enabled: Boolean,
    ) : SettingsEvent
}

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val contentPrefsRepository: ContentPrefsRepository,
        addonRepository: AddonRepository,
    ) : ViewModel() {
        val uiState: StateFlow<SettingsUiState> =
            combine(
                contentPrefsRepository.familyMode,
                addonRepository.observeAll(),
            ) { familyMode, allAddons ->
                val hiddenCount = if (familyMode) allAddons.count { it.isAdult } else 0
                SettingsUiState(
                    familyMode = familyMode,
                    hiddenAdultAddonsCount = hiddenCount,
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
            }
        }
    }
