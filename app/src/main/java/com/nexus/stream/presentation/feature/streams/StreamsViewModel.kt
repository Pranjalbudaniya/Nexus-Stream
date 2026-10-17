package com.nexus.stream.presentation.feature.streams

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexus.stream.R
import com.nexus.stream.di.DefaultDispatcher
import com.nexus.stream.domain.model.Addon
import com.nexus.stream.domain.model.ParsedStream
import com.nexus.stream.domain.model.PlaybackCapabilities
import com.nexus.stream.domain.model.StreamKind
import com.nexus.stream.domain.model.StreamSource
import com.nexus.stream.domain.model.supports
import com.nexus.stream.domain.repository.AddonFailure
import com.nexus.stream.domain.repository.AddonRepository
import com.nexus.stream.domain.repository.PlayerPrefsRepository
import com.nexus.stream.domain.repository.StreamRepository
import com.nexus.stream.domain.repository.StreamsState
import com.nexus.stream.domain.usecase.MagnetLink
import com.nexus.stream.domain.usecase.StreamMatching
import com.nexus.stream.domain.usecase.StreamOrdering
import com.nexus.stream.presentation.common.PlaybackRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class AddonFilterOption(
    val transportUrl: String,
    val displayName: String,
    val count: Int,
    val failed: Boolean = false,
)

data class StreamsUiState(
    val request: PlaybackRequest? = null,
    val isLoading: Boolean = false,
    val answered: Int = 0,
    val total: Int = 0,
    val allStreams: List<ParsedStream> = emptyList(),
    val filteredStreams: List<ParsedStream> = emptyList(),
    val availableResolutions: List<Int> = emptyList(),
    val resolutionCounts: Map<Int, Int> = emptyMap(),
    val selectedResolution: Int? = null,
    val availableAddons: List<AddonFilterOption> = emptyList(),
    val selectedAddon: String? = null,
    val showAddonFilter: Boolean = false,
    val detailsStream: ParsedStream? = null,
    val failures: List<AddonFailure> = emptyList(),
    val isDone: Boolean = false,
    val quickPlayHandled: Boolean = false,
)

sealed interface StreamsEffect {
    data class OpenVideo(
        val url: String,
        val title: String,
        val headers: Map<String, String>,
    ) : StreamsEffect

    data class OpenMagnet(
        val magnet: String,
    ) : StreamsEffect

    data class OpenExternalUrl(
        val url: String,
    ) : StreamsEffect

    data class CopyText(
        val text: String,
    ) : StreamsEffect

    data class ShowMessage(
        val messageRes: Int,
        val formatArgs: List<Any> = emptyList(),
    ) : StreamsEffect

    data class NavigateToStore(
        val category: String,
    ) : StreamsEffect

    data object DismissSheet : StreamsEffect
}

sealed interface StreamsEvent {
    data class SetRequest(
        val request: PlaybackRequest,
    ) : StreamsEvent

    data object ClearRequest : StreamsEvent

    data class SelectResolution(
        val resolution: Int?,
    ) : StreamsEvent

    data class SelectAddon(
        val addonKey: String?,
    ) : StreamsEvent

    data object ClearFilters : StreamsEvent

    data class ShowDetails(
        val stream: ParsedStream,
    ) : StreamsEvent

    data object DismissDetails : StreamsEvent

    data class CopyFileName(
        val fileName: String,
    ) : StreamsEvent

    data object Refresh : StreamsEvent

    data object RetryFailed : StreamsEvent

    data class PlayStream(
        val stream: ParsedStream,
    ) : StreamsEvent

    data class CopyLink(
        val stream: ParsedStream,
    ) : StreamsEvent

    data class CopyMagnet(
        val stream: ParsedStream,
    ) : StreamsEvent

    data class OpenMagnetExternal(
        val stream: ParsedStream,
    ) : StreamsEvent

    data class OpenLinkExternal(
        val stream: ParsedStream,
    ) : StreamsEvent

    data object FindStreams : StreamsEvent

    data object Dismiss : StreamsEvent
}

@HiltViewModel
class StreamsViewModel
    @Inject
    constructor(
        private val streamRepository: StreamRepository,
        private val addonRepository: AddonRepository,
        private val playerPrefsRepository: PlayerPrefsRepository,
        private val playbackCapabilities: PlaybackCapabilities,
        @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(StreamsUiState())
        val uiState: StateFlow<StreamsUiState> = _uiState.asStateFlow()

        private val _effects = MutableSharedFlow<StreamsEffect>()
        val effects: SharedFlow<StreamsEffect> = _effects.asSharedFlow()

        private var loadingJob: Job? = null
        private var addonJob: Job? = null
        private var cachedActiveAddons: List<Addon> = emptyList()

        fun onEvent(event: StreamsEvent) {
            if (handleStreamActionEvent(event)) return
            if (handleFilterEvent(event)) return
            when (event) {
                is StreamsEvent.SetRequest -> {
                    handleSetRequest(event.request)
                }

                StreamsEvent.ClearRequest -> {
                    handleClearRequest()
                }

                is StreamsEvent.ShowDetails -> {
                    _uiState.update { it.copy(detailsStream = event.stream) }
                }

                StreamsEvent.DismissDetails -> {
                    _uiState.update { it.copy(detailsStream = null) }
                }

                is StreamsEvent.CopyFileName -> {
                    viewModelScope.launch {
                        _effects.emit(StreamsEffect.CopyText(event.fileName))
                        _effects.emit(StreamsEffect.ShowMessage(R.string.streams_filename_copied))
                    }
                }

                StreamsEvent.Refresh -> {
                    reload(failedOnly = false)
                }

                StreamsEvent.RetryFailed -> {
                    reload(failedOnly = true)
                }

                StreamsEvent.FindStreams -> {
                    viewModelScope.launch {
                        _effects.emit(StreamsEffect.NavigateToStore("STREAMS"))
                        _effects.emit(StreamsEffect.DismissSheet)
                    }
                }

                StreamsEvent.Dismiss -> {
                    viewModelScope.launch {
                        handleClearRequest()
                        _effects.emit(StreamsEffect.DismissSheet)
                    }
                }

                else -> {}
            }
        }

        private fun handleFilterEvent(event: StreamsEvent): Boolean =
            when (event) {
                is StreamsEvent.SelectResolution -> {
                    _uiState.update { StreamsFilterReducer.selectResolution(it, event.resolution) }
                    true
                }

                is StreamsEvent.SelectAddon -> {
                    _uiState.update { StreamsFilterReducer.selectAddon(it, event.addonKey) }
                    true
                }

                StreamsEvent.ClearFilters -> {
                    _uiState.update { StreamsFilterReducer.clearFilters(it) }
                    true
                }

                else -> {
                    false
                }
            }

        private fun handleStreamActionEvent(event: StreamsEvent): Boolean =
            when (event) {
                is StreamsEvent.PlayStream -> {
                    viewModelScope.launch { playStream(event.stream) }
                    true
                }

                is StreamsEvent.CopyLink,
                is StreamsEvent.CopyMagnet,
                is StreamsEvent.OpenMagnetExternal,
                is StreamsEvent.OpenLinkExternal,
                -> {
                    handleStreamAction(event)
                    true
                }

                else -> {
                    false
                }
            }

        private fun handleSetRequest(request: PlaybackRequest) {
            loadingJob?.cancel()
            addonJob?.cancel()
            _uiState.update {
                StreamsUiState(
                    request = request,
                    isLoading = true,
                    selectedResolution = null,
                    selectedAddon = null,
                    quickPlayHandled = false,
                )
            }
            observeActiveAddons(request)
            startLoading(request = request, forceRefresh = false, onlyAddons = null, mergeExisting = false)
        }

        private fun handleClearRequest() {
            loadingJob?.cancel()
            loadingJob = null
            addonJob?.cancel()
            addonJob = null
            _uiState.update { StreamsUiState() }
        }

        private fun observeActiveAddons(request: PlaybackRequest) {
            addonJob =
                viewModelScope.launch {
                    addonRepository.observeActive().collect { activeList ->
                        val matching =
                            activeList.filter {
                                it.manifest.supports("stream", request.type, request.videoId)
                            }
                        cachedActiveAddons = matching
                        _uiState.update { StreamsFilterReducer.updateActiveAddons(it, matching) }
                    }
                }
        }

        private fun reload(failedOnly: Boolean) {
            val req = _uiState.value.request ?: return
            val failedUrls =
                if (failedOnly) {
                    _uiState.value.failures
                        .map { it.transportUrl }
                        .filter { it.isNotBlank() }
                        .toSet()
                } else {
                    null
                }
            if (!failedOnly || !failedUrls.isNullOrEmpty()) {
                loadingJob?.cancel()
                _uiState.update {
                    it.copy(
                        isLoading = true,
                        quickPlayHandled = if (!failedOnly) false else it.quickPlayHandled,
                    )
                }
                startLoading(
                    request = req,
                    forceRefresh = true,
                    onlyAddons = failedUrls,
                    mergeExisting = failedOnly,
                )
            }
        }

        private fun startLoading(
            request: PlaybackRequest,
            forceRefresh: Boolean,
            onlyAddons: Set<String>?,
            mergeExisting: Boolean,
        ) {
            loadingJob =
                viewModelScope.launch {
                    val initialItems = if (mergeExisting) _uiState.value.allStreams else emptyList()
                    streamRepository
                        .streams(
                            type = request.type,
                            videoId = request.videoId,
                            forceRefresh = forceRefresh,
                            onlyAddons = onlyAddons,
                        ).collect { emission ->
                            processEmission(request, emission, initialItems)
                        }
                }
        }

        private suspend fun processEmission(
            request: PlaybackRequest,
            emission: StreamsState,
            initialItems: List<ParsedStream>,
        ) {
            val combined =
                withContext(defaultDispatcher) {
                    val enriched =
                        emission.items.map {
                            val match =
                                StreamMatching.matchOf(
                                    it.fileName,
                                    request.season,
                                    request.episode,
                                    request.year,
                                )
                            it.copy(match = match)
                        }
                    if (initialItems.isEmpty()) {
                        StreamOrdering.sort(StreamOrdering.dedupe(enriched))
                    } else {
                        StreamOrdering.sort(StreamOrdering.dedupe(initialItems + enriched))
                    }
                }

            _uiState.update {
                StreamsFilterReducer.buildEmissionState(
                    state = it,
                    emission = emission,
                    combined = combined,
                    matching = cachedActiveAddons,
                )
            }

            if (emission.done && !_uiState.value.quickPlayHandled) {
                _uiState.update { it.copy(quickPlayHandled = true) }
                if (playerPrefsRepository.quickPlay.first()) {
                    val candidate = StreamOrdering.bestPlayable(combined, playbackCapabilities)
                    if (candidate != null) {
                        playStream(candidate)
                        _effects.emit(StreamsEffect.DismissSheet)
                    }
                }
            }
        }

        private suspend fun playStream(stream: ParsedStream) {
            when (stream.kind) {
                StreamKind.Direct -> {
                    val source = stream.stream.source as? StreamSource.Url
                    val url = source?.url.orEmpty()
                    val title = _uiState.value.request?.title ?: stream.displayName
                    val headers = source?.requestHeaders.orEmpty()
                    _effects.emit(StreamsEffect.OpenVideo(url, title, headers))
                }

                StreamKind.Torrent -> {
                    if (playbackCapabilities.supportsTorrent) {
                        // Handled in P08
                    } else {
                        _effects.emit(StreamsEffect.ShowMessage(R.string.streams_torrent_engine_notice))
                    }
                }

                StreamKind.ExternalLink -> {
                    val source = stream.stream.source as? StreamSource.External
                    val url = source?.url.orEmpty()
                    _effects.emit(StreamsEffect.OpenExternalUrl(url))
                }
            }
        }

        private fun handleStreamAction(event: StreamsEvent) {
            viewModelScope.launch {
                when (event) {
                    is StreamsEvent.CopyLink -> {
                        val url =
                            (event.stream.stream.source as? StreamSource.Url)?.url
                                ?: (event.stream.stream.source as? StreamSource.External)?.url
                        if (!url.isNullOrBlank()) {
                            _effects.emit(StreamsEffect.CopyText(url))
                        }
                    }

                    is StreamsEvent.CopyMagnet -> {
                        val torrent = event.stream.stream.source as? StreamSource.Torrent ?: return@launch
                        val magnet =
                            MagnetLink.buildMagnet(
                                torrent.infoHash,
                                event.stream.displayName,
                                torrent.trackers,
                            )
                        if (magnet != null) {
                            _effects.emit(StreamsEffect.CopyText(magnet))
                        }
                    }

                    is StreamsEvent.OpenMagnetExternal -> {
                        val torrent = event.stream.stream.source as? StreamSource.Torrent ?: return@launch
                        val magnet =
                            MagnetLink.buildMagnet(
                                torrent.infoHash,
                                event.stream.displayName,
                                torrent.trackers,
                            )
                        if (magnet != null) {
                            _effects.emit(StreamsEffect.OpenMagnet(magnet))
                        }
                    }

                    is StreamsEvent.OpenLinkExternal -> {
                        val url =
                            (event.stream.stream.source as? StreamSource.External)?.url
                                ?: (event.stream.stream.source as? StreamSource.Url)?.url
                        if (!url.isNullOrBlank()) {
                            _effects.emit(StreamsEffect.OpenExternalUrl(url))
                        }
                    }

                    else -> {
                    }
                }
            }
        }
    }
