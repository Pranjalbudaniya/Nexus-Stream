package com.nexus.stream.dev

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexus.stream.domain.model.TorrentRequest
import com.nexus.stream.domain.model.TorrentState
import com.nexus.stream.domain.repository.TorrentEngine
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
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(TorrentTesterUiState())
        val uiState: StateFlow<TorrentTesterUiState> = _uiState.asStateFlow()

        private val _effects = MutableSharedFlow<TorrentTesterEffect>()
        val effects: SharedFlow<TorrentTesterEffect> = _effects.asSharedFlow()

        private var activeHash: String? = null
        private var prepareJob: Job? = null
        private var localUrl: String? = null

        fun onEvent(event: TorrentTesterEvent) {
            when (event) {
                is TorrentTesterEvent.InputChanged -> onInputChanged(event.text)
                TorrentTesterEvent.Start -> onStart()
                TorrentTesterEvent.Stop -> onStop()
                is TorrentTesterEvent.SelectFile -> onSelectFile(event.index)
                TorrentTesterEvent.PlayInOtherApp -> onPlayInOtherApp()
            }
        }

        private fun onInputChanged(text: String) {
            _uiState.update { it.copy(input = text, inputInvalid = false) }
        }

        private fun onStart() {
            if (_uiState.value.running) return
            val parsed = parseMagnetLink(_uiState.value.input)
            if (parsed == null) {
                _uiState.update { it.copy(inputInvalid = true) }
                return
            }

            val hash = parsed.infoHash
            activeHash = hash
            localUrl = null
            _uiState.update {
                it.copy(
                    input = "",
                    inputInvalid = false,
                    running = true,
                    state = null,
                )
            }

            prepareJob?.cancel()
            prepareJob =
                viewModelScope.launch {
                    engine
                        .prepare(TorrentRequest(infoHash = hash, trackers = parsed.trackers))
                        .onCompletion { _ ->
                            _uiState.update { currentState -> currentState.copy(running = false) }
                        }.collect { newState ->
                            if (newState is TorrentState.Ready) {
                                localUrl = newState.localUrl
                                _uiState.update { currentState ->
                                    currentState.copy(state = newState.copy(localUrl = null))
                                }
                            } else {
                                localUrl = null
                                _uiState.update { currentState -> currentState.copy(state = newState) }
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
            _uiState.update { it.copy(running = false, state = null) }
            if (hash != null) {
                viewModelScope.launch {
                    engine.stop(hash)
                }
            }
        }

        private fun onSelectFile(index: Int) {
            val hash = activeHash ?: return
            viewModelScope.launch {
                engine.selectFile(hash, index)
            }
        }

        private fun onPlayInOtherApp() {
            if (_uiState.value.state is TorrentState.Ready) {
                val url = localUrl ?: return
                viewModelScope.launch {
                    _effects.emit(TorrentTesterEffect.OpenPlayer(url))
                }
            }
        }

        override fun onCleared() {
            super.onCleared()
            prepareJob?.cancel()
            prepareJob = null
        }
    }
