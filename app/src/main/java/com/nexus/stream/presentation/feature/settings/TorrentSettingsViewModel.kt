package com.nexus.stream.presentation.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexus.stream.di.IoDispatcher
import com.nexus.stream.domain.repository.TorrentCache
import com.nexus.stream.domain.repository.TorrentPrefsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface CacheSizeState {
    data object Idle : CacheSizeState

    data object Loading : CacheSizeState

    data class Ready(
        val bytes: Long,
    ) : CacheSizeState

    data object Unknown : CacheSizeState
}

data class TorrentSettingsUiState(
    val wifiOnly: Boolean = true,
    val cacheLimitGb: Int = 2,
    val clearCacheOnExit: Boolean = true,
    val cacheSize: CacheSizeState = CacheSizeState.Idle,
    val showLimitDialog: Boolean = false,
    val showClearConfirm: Boolean = false,
    val isClearing: Boolean = false,
) {
    val limitOptions: List<Int>
        get() {
            val defaults = listOf(1, 2, 5, 10, 20)
            return if (cacheLimitGb in defaults) {
                defaults
            } else {
                (defaults + cacheLimitGb).sorted()
            }
        }
}

sealed interface TorrentSettingsEvent {
    data class SetWifiOnly(
        val wifiOnly: Boolean,
    ) : TorrentSettingsEvent

    data class SetClearOnExit(
        val clearOnExit: Boolean,
    ) : TorrentSettingsEvent

    data class SetCacheLimit(
        val limitGb: Int,
    ) : TorrentSettingsEvent

    data object SectionOpened : TorrentSettingsEvent

    data object RequestClear : TorrentSettingsEvent

    data object ConfirmClear : TorrentSettingsEvent

    data object DismissClear : TorrentSettingsEvent

    data object OpenLimitDialog : TorrentSettingsEvent

    data object DismissLimitDialog : TorrentSettingsEvent
}

sealed interface TorrentSettingsEffect {
    data object CacheCleared : TorrentSettingsEffect

    data object ClearFailed : TorrentSettingsEffect
}

@HiltViewModel
class TorrentSettingsViewModel
    @Inject
    constructor(
        private val torrentPrefsRepository: TorrentPrefsRepository,
        private val torrentCache: TorrentCache,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(TorrentSettingsUiState())
        val uiState: StateFlow<TorrentSettingsUiState> = _uiState.asStateFlow()

        private val _effects = MutableSharedFlow<TorrentSettingsEffect>()
        val effects: Flow<TorrentSettingsEffect> = _effects.asSharedFlow()

        init {
            viewModelScope.launch {
                combine(
                    torrentPrefsRepository.wifiOnly,
                    torrentPrefsRepository.cacheLimitGb,
                    torrentPrefsRepository.clearCacheOnExit,
                ) { wifiOnly, cacheLimitGb, clearCacheOnExit ->
                    Triple(wifiOnly, cacheLimitGb, clearCacheOnExit)
                }.collect { (wifiOnly, cacheLimitGb, clearCacheOnExit) ->
                    _uiState.update {
                        it.copy(
                            wifiOnly = wifiOnly,
                            cacheLimitGb = cacheLimitGb,
                            clearCacheOnExit = clearCacheOnExit,
                        )
                    }
                }
            }
        }

        fun onEvent(event: TorrentSettingsEvent) {
            when (event) {
                is TorrentSettingsEvent.SetWifiOnly -> {
                    viewModelScope.launch { torrentPrefsRepository.setWifiOnly(event.wifiOnly) }
                }

                is TorrentSettingsEvent.SetClearOnExit -> {
                    viewModelScope.launch { torrentPrefsRepository.setClearCacheOnExit(event.clearOnExit) }
                }

                is TorrentSettingsEvent.SetCacheLimit -> {
                    viewModelScope.launch { torrentPrefsRepository.setCacheLimitGb(event.limitGb) }
                }

                TorrentSettingsEvent.SectionOpened -> {
                    loadCacheSize()
                }

                TorrentSettingsEvent.RequestClear -> {
                    _uiState.update { it.copy(showClearConfirm = true) }
                }

                TorrentSettingsEvent.ConfirmClear -> {
                    clearCache()
                }

                TorrentSettingsEvent.DismissClear -> {
                    _uiState.update { it.copy(showClearConfirm = false) }
                }

                TorrentSettingsEvent.OpenLimitDialog -> {
                    _uiState.update { it.copy(showLimitDialog = true) }
                }

                TorrentSettingsEvent.DismissLimitDialog -> {
                    _uiState.update { it.copy(showLimitDialog = false) }
                }
            }
        }

        private fun loadCacheSize() {
            _uiState.update { it.copy(cacheSize = CacheSizeState.Loading) }
            viewModelScope.launch(ioDispatcher) {
                val result = runCatching { torrentCache.cacheSizeBytes() }
                _uiState.update { state ->
                    state.copy(
                        cacheSize =
                            result.fold(
                                onSuccess = { CacheSizeState.Ready(it) },
                                onFailure = { CacheSizeState.Unknown },
                            ),
                    )
                }
            }
        }

        private fun clearCache() {
            if (_uiState.value.isClearing) return
            _uiState.update { it.copy(isClearing = true, showClearConfirm = false) }
            viewModelScope.launch(ioDispatcher) {
                val clearResult = runCatching { torrentCache.clearCache() }
                if (clearResult.isSuccess) {
                    val size = runCatching { torrentCache.cacheSizeBytes() }.getOrDefault(0L)
                    _uiState.update { it.copy(cacheSize = CacheSizeState.Ready(size), isClearing = false) }
                    _effects.emit(TorrentSettingsEffect.CacheCleared)
                } else {
                    _uiState.update { it.copy(isClearing = false) }
                    _effects.emit(TorrentSettingsEffect.ClearFailed)
                }
            }
        }
    }
