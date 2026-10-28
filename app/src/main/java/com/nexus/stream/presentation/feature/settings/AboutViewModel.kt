package com.nexus.stream.presentation.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexus.stream.domain.model.GITHUB_ISSUES_URL
import com.nexus.stream.domain.model.GITHUB_URL
import com.nexus.stream.domain.model.formatAppVersion
import com.nexus.stream.domain.repository.AppInfoProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AboutUiState(
    val versionText: String = "",
    val showLicenses: Boolean = false,
    val showDisclaimer: Boolean = false,
    val versionSummary: String = "",
)

sealed interface AboutEvent {
    data object OpenGithub : AboutEvent

    data object OpenIssues : AboutEvent

    data object OpenLicenses : AboutEvent

    data object DismissLicenses : AboutEvent

    data object OpenDisclaimer : AboutEvent

    data object DismissDisclaimer : AboutEvent
}

sealed interface AboutEffect {
    data class OpenUrl(
        val url: String,
    ) : AboutEffect
}

@HiltViewModel
class AboutViewModel
    @Inject
    constructor(
        appInfoProvider: AppInfoProvider,
    ) : ViewModel() {
        private val _uiState =
            MutableStateFlow(
                AboutUiState(
                    versionText = formatAppVersion(appInfoProvider.versionName, appInfoProvider.versionCode),
                    versionSummary = aboutSummary(appInfoProvider.versionName, appInfoProvider.versionCode),
                ),
            )
        val uiState: StateFlow<AboutUiState> = _uiState.asStateFlow()

        private val _effects = MutableSharedFlow<AboutEffect>()
        val effects: Flow<AboutEffect> = _effects.asSharedFlow()

        fun onEvent(event: AboutEvent) {
            when (event) {
                AboutEvent.OpenGithub -> {
                    viewModelScope.launch { _effects.emit(AboutEffect.OpenUrl(GITHUB_URL)) }
                }

                AboutEvent.OpenIssues -> {
                    viewModelScope.launch { _effects.emit(AboutEffect.OpenUrl(GITHUB_ISSUES_URL)) }
                }

                AboutEvent.OpenLicenses -> {
                    _uiState.update { it.copy(showLicenses = true) }
                }

                AboutEvent.DismissLicenses -> {
                    _uiState.update { it.copy(showLicenses = false) }
                }

                AboutEvent.OpenDisclaimer -> {
                    _uiState.update { it.copy(showDisclaimer = true) }
                }

                AboutEvent.DismissDisclaimer -> {
                    _uiState.update { it.copy(showDisclaimer = false) }
                }
            }
        }
    }
