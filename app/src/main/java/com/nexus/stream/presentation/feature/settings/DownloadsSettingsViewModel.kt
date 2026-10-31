package com.nexus.stream.presentation.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexus.stream.domain.model.DownloadState
import com.nexus.stream.domain.model.GalleryState
import com.nexus.stream.domain.repository.DownloadManager
import com.nexus.stream.domain.repository.DownloadPrefsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DownloadsSettingsUiState(
    val wifiOnly: Boolean = true,
    val maxConcurrent: Int = 2,
    val summary: String = "",
    val saveToGallery: Boolean = false,
    val unsavedCount: Int = 0,
    val showSaveExistingOffer: Boolean = false,
)

sealed interface DownloadsSettingsEvent {
    data class SetWifiOnly(
        val wifiOnly: Boolean,
    ) : DownloadsSettingsEvent

    data class SetMaxConcurrent(
        val maxConcurrent: Int,
    ) : DownloadsSettingsEvent

    data class SetSaveToGallery(
        val enabled: Boolean,
    ) : DownloadsSettingsEvent

    data object SaveExisting : DownloadsSettingsEvent

    data object DeclineOffer : DownloadsSettingsEvent
}

@HiltViewModel
class DownloadsSettingsViewModel
    @Inject
    constructor(
        private val downloadPrefsRepository: DownloadPrefsRepository,
        private val downloadManager: DownloadManager,
    ) : ViewModel() {
        private val showOfferFlow = MutableStateFlow(false)

        val uiState: StateFlow<DownloadsSettingsUiState> =
            combine(
                downloadPrefsRepository.wifiOnly,
                downloadPrefsRepository.maxConcurrent,
                downloadPrefsRepository.saveToGallery,
                downloadManager.items,
                showOfferFlow,
            ) { wifiOnly, maxConcurrent, saveToGallery, items, showOffer ->
                val unsavedCount =
                    items
                        .filter { it.state == DownloadState.COMPLETED }
                        .count { it.galleryState != GalleryState.SAVED }
                DownloadsSettingsUiState(
                    wifiOnly = wifiOnly,
                    maxConcurrent = maxConcurrent,
                    summary = downloadsSummary(wifiOnly, maxConcurrent),
                    saveToGallery = saveToGallery,
                    unsavedCount = unsavedCount,
                    showSaveExistingOffer = showOffer,
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

                    is DownloadsSettingsEvent.SetSaveToGallery -> {
                        downloadPrefsRepository.setSaveToGallery(event.enabled)
                        if (event.enabled) {
                            val unsaved =
                                downloadManager.items
                                    .first()
                                    .filter { it.state == DownloadState.COMPLETED }
                                    .count { it.galleryState != GalleryState.SAVED }
                            if (unsaved > 0) {
                                showOfferFlow.value = true
                            }
                        } else {
                            showOfferFlow.value = false
                        }
                    }

                    DownloadsSettingsEvent.SaveExisting -> {
                        showOfferFlow.value = false
                        downloadManager.exportAllToGallery()
                    }

                    DownloadsSettingsEvent.DeclineOffer -> {
                        showOfferFlow.value = false
                    }
                }
            }
        }
    }
