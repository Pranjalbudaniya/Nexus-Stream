package com.nexus.stream.presentation.feature.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexus.stream.domain.repository.DownloadManager
import com.nexus.stream.domain.usecase.activeCount
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class DownloadBadgeViewModel
    @Inject
    constructor(
        downloadManager: DownloadManager,
    ) : ViewModel() {
        val activeCount: StateFlow<Int> =
            downloadManager.items
                .map { activeCount(it) }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = 0,
                )
    }
