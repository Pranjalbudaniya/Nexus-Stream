package com.nexus.stream.presentation.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexus.stream.domain.repository.DownloadPrefsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

fun downloadsSummary(
    wifiOnly: Boolean,
    maxConcurrent: Int,
): String =
    if (wifiOnly) {
        "Wi-Fi only · $maxConcurrent at a time"
    } else {
        "Any network · $maxConcurrent at a time"
    }

data class DownloadsSettingsUiState(
    val wifiOnly: Boolean = true,
    val maxConcurrent: Int = 2,
    val summary: String = "",
)

sealed interface DownloadsSettingsEvent {
    data class SetWifiOnly(
        val wifiOnly: Boolean,
    ) : DownloadsSettingsEvent

    data class SetMaxConcurrent(
        val maxConcurrent: Int,
    ) : DownloadsSettingsEvent
}

@HiltViewModel
class DownloadsSettingsViewModel
    @Inject
    constructor(
        private val downloadPrefsRepository: DownloadPrefsRepository,
    ) : ViewModel() {
        val uiState: StateFlow<DownloadsSettingsUiState> =
            combine(
                downloadPrefsRepository.wifiOnly,
                downloadPrefsRepository.maxConcurrent,
            ) { wifiOnly, maxConcurrent ->
                DownloadsSettingsUiState(
                    wifiOnly = wifiOnly,
                    maxConcurrent = maxConcurrent,
                    summary = downloadsSummary(wifiOnly, maxConcurrent),
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = DownloadsSettingsUiState(),
            )

        fun onEvent(event: DownloadsSettingsEvent) {
            viewModelScope.launch {
                when (event) {
                    is DownloadsSettingsEvent.SetWifiOnly -> {
                        downloadPrefsRepository.setWifiOnly(event.wifiOnly)
                    }

                    is DownloadsSettingsEvent.SetMaxConcurrent -> {
                        downloadPrefsRepository.setMaxConcurrent(event.maxConcurrent)
                    }
                }
            }
        }
    }
