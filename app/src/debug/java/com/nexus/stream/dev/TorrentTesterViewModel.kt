package com.nexus.stream.dev

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexus.stream.domain.model.AppError
import com.nexus.stream.domain.model.TorrentRequest
import com.nexus.stream.domain.model.TorrentState
import com.nexus.stream.domain.repository.TorrentCache
import com.nexus.stream.domain.repository.TorrentEngine
import com.nexus.stream.domain.repository.TorrentPrefsRepository
import com.nexus.stream.domain.usecase.parseMagnetLink
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TorrentTesterUiState(
    val input: String = "",
    val inputInvalid: Boolean = false,
    val running: Boolean = false,
    val state: TorrentState? = null,
    val isBlockedMetered: Boolean = false,
    val cacheSizeBytes: Long = 0L,
    val keepDataAfterStop: Boolean = false,
    val cacheLimitGb: Int = 2,
)

sealed interface TorrentTesterEvent {
    data class InputChanged(
        val text: String,
    ) : TorrentTesterEvent

    data object Start : TorrentTesterEvent

    data object Stop : TorrentTesterEvent

    data class SelectFile(
        val index: Int,
    ) : TorrentTesterEvent

    data object PlayInOtherApp : TorrentTesterEvent

    data object AllowMeteredOnce : TorrentTesterEvent

    data object ClearCache : TorrentTesterEvent

    data class SetKeepAfterStop(
        val keep: Boolean,
    ) : TorrentTesterEvent

    data class SetCacheLimit(
        val limitGb: Int,
    ) : TorrentTesterEvent
}

sealed interface TorrentTesterEffect {
    data class OpenPlayer(
        val url: String,
    ) : TorrentTesterEffect
}

@HiltViewModel
class TorrentTesterViewModel
    @Inject
    constructor(
        private val engine: TorrentEngine,
        private val cache: TorrentCache,
        private val prefs: TorrentPrefsRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(TorrentTesterUiState())
        val uiState: StateFlow<TorrentTesterUiState> = _uiState.asStateFlow()

        private val _effects = MutableSharedFlow<TorrentTesterEffect>()
        val effects: SharedFlow<TorrentTesterEffect> = _effects.asSharedFlow()

        private var activeHash: String? = null
        private var prepareJob: Job? = null
        private var localUrl: String? = null
        private var lastRequest: TorrentRequest? = null

        init {
            refreshCacheSize()
            viewModelScope.launch {
                prefs.cacheLimitGb.collect { limit ->
                    _uiState.update { it.copy(cacheLimitGb = limit) }
                }
            }
            viewModelScope.launch {
                prefs.clearCacheOnExit.collect { clearOnExit ->
                    _uiState.update { it.copy(keepDataAfterStop = !clearOnExit) }
                }
            }
        }

        fun onEvent(event: TorrentTesterEvent) {
            when (event) {
                is TorrentTesterEvent.InputChanged -> {
                    _uiState.update { it.copy(input = event.text, inputInvalid = false) }
                }

                TorrentTesterEvent.Start -> {
                    onStart()
                }

                TorrentTesterEvent.Stop -> {
                    onStop()
                }

                is TorrentTesterEvent.SelectFile -> {
                    activeHash?.let { hash ->
                        viewModelScope.launch {
                            engine.selectFile(hash, event.index)
                        }
                    }
                }

                TorrentTesterEvent.PlayInOtherApp -> {
                    if (_uiState.value.state is TorrentState.Ready) {
                        localUrl?.let { url ->
                            viewModelScope.launch {
                                _effects.emit(TorrentTesterEffect.OpenPlayer(url))
                            }
                        }
                    }
                }

                TorrentTesterEvent.AllowMeteredOnce -> {
                    onAllowMeteredOnce()
                }

                TorrentTesterEvent.ClearCache -> {
                    viewModelScope.launch {
                        cache.clearCache()
                        refreshCacheSize()
                    }
                }

                is TorrentTesterEvent.SetKeepAfterStop -> {
                    viewModelScope.launch {
                        prefs.setClearCacheOnExit(!event.keep)
                        refreshCacheSize()
                    }
                }

                is TorrentTesterEvent.SetCacheLimit -> {
                    viewModelScope.launch {
                        prefs.setCacheLimitGb(event.limitGb)
                        refreshCacheSize()
                    }
                }
            }
        }

        private fun onStart() {
            if (_uiState.value.running) return
            val parsed = parseMagnetLink(_uiState.value.input)
            if (parsed == null) {
                _uiState.update { it.copy(inputInvalid = true) }
                return
            }

            val request =
                TorrentRequest(
                    infoHash = parsed.infoHash,
                    trackers = parsed.trackers,
                    allowMetered = false,
                )
            lastRequest = request
            _uiState.update { it.copy(input = "", inputInvalid = false) }
            runPrepare(request)
        }

        private fun onAllowMeteredOnce() {
            val req = lastRequest ?: return
            lastRequest = null
            runPrepare(req.copy(allowMetered = true))
        }

        private fun runPrepare(request: TorrentRequest) {
            activeHash = request.infoHash
            localUrl = null
            _uiState.update {
                it.copy(
                    running = true,
                    state = null,
                    isBlockedMetered = false,
                )
            }

            prepareJob?.cancel()
            prepareJob =
                viewModelScope.launch {
                    engine
                        .prepare(request)
                        .onCompletion { _ ->
                            _uiState.update { currentState -> currentState.copy(running = false) }
                            refreshCacheSize()
                        }.collect { newState ->
                            if (newState is TorrentState.Ready) {
                                localUrl = newState.localUrl
                                _uiState.update { currentState ->
                                    currentState.copy(
                                        state = newState.copy(localUrl = null),
                                        isBlockedMetered = false,
                                    )
                                }
                            } else if (newState is TorrentState.Failed && newState.error is AppError.MeteredBlocked) {
                                localUrl = null
                                _uiState.update { currentState ->
                                    currentState.copy(
                                        state = newState,
                                        isBlockedMetered = true,
                                    )
                                }
                            } else {
                                localUrl = null
                                _uiState.update { currentState ->
                                    currentState.copy(
                                        state = newState,
                                        isBlockedMetered = false,
                                    )
                                }
                            }
                        }
                }
        }

        private fun onStop() {
            prepareJob?.cancel()
            prepareJob = null
            localUrl = null
            val hash = activeHash
            activeHash = null
            _uiState.update { it.copy(running = false, state = null, isBlockedMetered = false) }
            if (hash != null) {
                viewModelScope.launch {
                    engine.stop(hash)
                }
            }
        }

        private fun refreshCacheSize() {
            viewModelScope.launch {
                val size = cache.cacheSizeBytes()
                _uiState.update { it.copy(cacheSizeBytes = size) }
            }
        }

        override fun onCleared() {
            super.onCleared()
            prepareJob?.cancel()
            prepareJob = null
        }
    }
