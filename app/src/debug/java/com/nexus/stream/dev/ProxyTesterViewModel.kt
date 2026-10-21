package com.nexus.stream.dev

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexus.stream.domain.model.PlaybackSource
import com.nexus.stream.domain.repository.ProxySession
import com.nexus.stream.domain.repository.StreamProxy
import com.nexus.stream.presentation.feature.player.PlaybackSelection
import com.nexus.stream.presentation.feature.player.PlaybackSessionHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProxyTesterUiState(
    val url: String = "",
    val headersText: String = "",
    val parsedHeaderCount: Int = 0,
    val ignoredLineCount: Int = 0,
    val isSessionActive: Boolean = false,
    val statusText: String? = null,
) {
    override fun toString(): String =
        "ProxyTesterUiState(parsedHeaderCount=$parsedHeaderCount, ignoredLineCount=$ignoredLineCount, isSessionActive=$isSessionActive, statusText=$statusText)"
}

sealed interface ProxyTesterEvent {
    data class UrlChanged(
        val url: String,
    ) : ProxyTesterEvent

    data class HeadersChanged(
        val text: String,
    ) : ProxyTesterEvent

    data object OpenInBrowser : ProxyTesterEvent

    data object PlayInOtherApp : ProxyTesterEvent

    data object PlayInApp : ProxyTesterEvent

    data object Stop : ProxyTesterEvent
}

sealed interface ProxyTesterEffect {
    data class OpenUrl(
        val url: String,
        val asVideo: Boolean,
    ) : ProxyTesterEffect
}

internal fun parseHeadersText(text: String): Pair<Map<String, String>, Int> {
    val headers = mutableMapOf<String, String>()
    var ignored = 0
    val lines = text.lines()
    for (rawLine in lines) {
        val line = rawLine.trim()
        if (line.isEmpty()) continue
        val colon = line.indexOf(':')
        if (colon > 0) {
            val name = line.substring(0, colon).trim()
            val value = line.substring(colon + 1).trim()
            if (name.isNotEmpty()) {
                headers[name] = value
            } else {
                ignored++
            }
        } else {
            ignored++
        }
    }
    return headers to ignored
}

@HiltViewModel
class ProxyTesterViewModel
    @Inject
    constructor(
        private val proxy: StreamProxy,
        private val holder: PlaybackSessionHolder,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(ProxyTesterUiState())
        val uiState: StateFlow<ProxyTesterUiState> = _uiState.asStateFlow()

        private val _effects = MutableSharedFlow<ProxyTesterEffect>()
        val effects: SharedFlow<ProxyTesterEffect> = _effects.asSharedFlow()

        private var activeSession: ProxySession? = null

        fun onEvent(event: ProxyTesterEvent) {
            when (event) {
                is ProxyTesterEvent.UrlChanged -> {
                    _uiState.update { it.copy(url = event.url) }
                }

                is ProxyTesterEvent.HeadersChanged -> {
                    val (headers, ignored) = parseHeadersText(event.text)
                    _uiState.update {
                        it.copy(
                            headersText = event.text,
                            parsedHeaderCount = headers.size,
                            ignoredLineCount = ignored,
                        )
                    }
                }

                ProxyTesterEvent.OpenInBrowser -> {
                    openSession(asVideo = false)
                }

                ProxyTesterEvent.PlayInOtherApp -> {
                    openSession(asVideo = true)
                }

                ProxyTesterEvent.PlayInApp -> {
                    playInApp()
                }

                ProxyTesterEvent.Stop -> {
                    stopSession()
                }
            }
        }

        private fun playInApp() {
            val currentUrl = _uiState.value.url.trim()
            if (currentUrl.isEmpty()) {
                _uiState.update { it.copy(isSessionActive = false, statusText = "Error: invalid URL") }
                return
            }
            val (headers, _) = parseHeadersText(_uiState.value.headersText)
            val source =
                if (headers.isEmpty()) {
                    PlaybackSource.Direct(currentUrl)
                } else {
                    PlaybackSource.DirectWithHeaders(currentUrl, headers)
                }
            holder.start(
                PlaybackSelection(
                    title = "Proxy test",
                    source = source,
                    type = "movie",
                    metaId = "debug",
                    videoId = "debug",
                ),
            )
        }

        private fun openSession(asVideo: Boolean) {
            activeSession?.close()
            activeSession = null

            val currentUrl = _uiState.value.url.trim()
            if (currentUrl.isEmpty()) {
                _uiState.update { it.copy(isSessionActive = false, statusText = "Error: invalid URL") }
                return
            }

            val (headers, _) = parseHeadersText(_uiState.value.headersText)
            try {
                val session = proxy.open(currentUrl, headers)
                activeSession = session
                _uiState.update { it.copy(isSessionActive = true, statusText = "Active") }
                viewModelScope.launch {
                    _effects.emit(ProxyTesterEffect.OpenUrl(session.url, asVideo))
                }
            } catch (_: Exception) {
                activeSession = null
                _uiState.update { it.copy(isSessionActive = false, statusText = "Error: invalid URL") }
            }
        }

        private fun stopSession() {
            activeSession?.close()
            activeSession = null
            _uiState.update { it.copy(isSessionActive = false, statusText = "Stopped") }
        }

        override fun onCleared() {
            super.onCleared()
            activeSession?.close()
            activeSession = null
        }
    }
