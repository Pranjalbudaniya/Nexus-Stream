package com.nexus.stream.presentation.feature.streams

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexus.stream.R
import com.nexus.stream.di.DefaultDispatcher
import com.nexus.stream.domain.model.Addon
import com.nexus.stream.domain.model.ParsedStream
import com.nexus.stream.domain.model.PlaybackCapabilities
import com.nexus.stream.domain.model.SourceFingerprint
import com.nexus.stream.domain.model.StreamFilters
import com.nexus.stream.domain.model.StreamKind
import com.nexus.stream.domain.model.StreamSort
import com.nexus.stream.domain.model.StreamSource
import com.nexus.stream.domain.model.TypeFilter
import com.nexus.stream.domain.model.hashAddonKey
import com.nexus.stream.domain.model.supports
import com.nexus.stream.domain.repository.AddonFailure
import com.nexus.stream.domain.repository.AddonRepository
import com.nexus.stream.domain.repository.PlayerPrefsRepository
import com.nexus.stream.domain.repository.SourceMemoryRepository
import com.nexus.stream.domain.repository.StreamRepository
import com.nexus.stream.domain.repository.StreamsState
import com.nexus.stream.domain.usecase.MagnetLink
import com.nexus.stream.domain.usecase.ResumeStep
import com.nexus.stream.domain.usecase.StartPlayback
import com.nexus.stream.domain.usecase.StartResult
import com.nexus.stream.domain.usecase.StreamMatching
import com.nexus.stream.domain.usecase.StreamOrdering
import com.nexus.stream.domain.usecase.decideResume
import com.nexus.stream.domain.usecase.lastUsedKey
import com.nexus.stream.domain.usecase.progressKeyFor
import com.nexus.stream.presentation.common.PlaybackRequest
import com.nexus.stream.presentation.feature.player.PlaybackSessionHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

private const val RESUME_WAIT_MS = 12_000L

data class AddonFilterOption(
    val transportUrl: String,
    val displayName: String,
    val count: Int,
    val failed: Boolean = false,
)

enum class ResumeUi {
    None,
    Looking,
    NotFound,
}

data class StreamsUiState(
    val request: PlaybackRequest? = null,
    val isLoading: Boolean = false,
    val answered: Int = 0,
    val total: Int = 0,
    val allStreams: List<ParsedStream> = emptyList(),
    val filteredStreams: List<ParsedStream> = emptyList(),
    val availableResolutions: List<Int> = emptyList(),
    val resolutionCounts: Map<Int, Int> = emptyMap(),
    val allResolutionsCount: Int = 0,
    val selectedResolution: Int? = null,
    val availableAddons: List<AddonFilterOption> = emptyList(),
    val allAddonsCount: Int = 0,
    val selectedAddon: String? = null,
    val showAddonFilter: Boolean = false,
    val availableLanguages: List<String> = emptyList(),
    val languageCounts: Map<String, Int> = emptyMap(),
    val anyLanguageCount: Int = 0,
    val selectedLanguage: String? = null,
    val typeDirectCount: Int = 0,
    val typeTorrentCount: Int = 0,
    val allTypesCount: Int = 0,
    val selectedType: TypeFilter = TypeFilter.ALL,
    val cachedCount: Int = 0,
    val cachedOnly: Boolean = false,
    val sort: StreamSort = StreamSort.BEST,
    val preferredLanguage: String? = null,
    val preferredQuality: Int? = null,
    val hideCam: Boolean = false,
    val showResolutionFilter: Boolean = false,
    val showLanguageFilter: Boolean = false,
    val showTypeFilter: Boolean = false,
    val showCachedFilter: Boolean = false,
    val detailsStream: ParsedStream? = null,
    val failures: List<AddonFailure> = emptyList(),
    val isDone: Boolean = false,
    val quickPlayHandled: Boolean = false,
    val resume: ResumeUi = ResumeUi.None,
    val lastUsedKey: String? = null,
) {
    val filters: StreamFilters
        get() =
            StreamFilters(
                resolution = selectedResolution,
                addonKey = selectedAddon,
                language = selectedLanguage,
                type = selectedType,
                cachedOnly = cachedOnly,
            )
}

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

    data class SelectLanguage(
        val language: String?,
    ) : StreamsEvent

    data class SelectType(
        val type: TypeFilter,
    ) : StreamsEvent

    data object ToggleCached : StreamsEvent

    data class SelectSort(
        val sort: StreamSort,
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
@Suppress("TooManyFunctions", "LongParameterList")
class StreamsViewModel
    @Inject
    constructor(
        private val streamRepository: StreamRepository,
        private val addonRepository: AddonRepository,
        private val playerPrefsRepository: PlayerPrefsRepository,
        private val playbackCapabilities: PlaybackCapabilities,
        private val playbackSessionHolder: PlaybackSessionHolder,
        private val sourceMemoryRepository: SourceMemoryRepository,
        private val startPlayback: StartPlayback = StartPlayback(playbackCapabilities, playbackSessionHolder),
        @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(StreamsUiState())
        val uiState: StateFlow<StreamsUiState> = _uiState.asStateFlow()

        private val _effects = MutableSharedFlow<StreamsEffect>()
        val effects: SharedFlow<StreamsEffect> = _effects.asSharedFlow()

        private var loadingJob: Job? = null
        private var addonJob: Job? = null
        private var waitJob: Job? = null
        private var fingerprintDeferred: Deferred<SourceFingerprint?>? = null
        private var cachedActiveAddons: List<Addon> = emptyList()
        private var addonsLoaded = false
        private var userPicked = false
        private var handled = false
        private var waitExpired = false

        init {
            viewModelScope.launch {
                combine(
                    playerPrefsRepository.sort,
                    playerPrefsRepository.preferredLanguage,
                    playerPrefsRepository.preferredQuality,
                    playerPrefsRepository.hideCam,
                ) { sort, lang, quality, hideCam ->
                    PrefSettings(sort, lang, quality, hideCam)
                }.collect { prefs ->
                    _uiState.update { state ->
                        val sorted = StreamsFilterReducer.updateSort(state, cachedActiveAddons, prefs.sort)
                        StreamsFilterReducer.updatePreferences(
                            sorted,
                            cachedActiveAddons,
                            prefs.preferredLanguage,
                            prefs.preferredQuality,
                            prefs.hideCam,
                        )
                    }
                }
            }
        }

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

        private fun handleFilterEvent(event: StreamsEvent): Boolean {
            val filters = _uiState.value.filters
            val updated =
                when (event) {
                    is StreamsEvent.SelectResolution -> {
                        StreamFilterMemory.resolution = event.resolution
                        filters.copy(resolution = event.resolution)
                    }

                    is StreamsEvent.SelectAddon -> {
                        filters.copy(addonKey = event.addonKey)
                    }

                    is StreamsEvent.SelectLanguage -> {
                        StreamFilterMemory.language = event.language
                        filters.copy(language = event.language)
                    }

                    is StreamsEvent.SelectType -> {
                        StreamFilterMemory.type = event.type
                        filters.copy(type = event.type)
                    }

                    StreamsEvent.ToggleCached -> {
                        val toggled = !_uiState.value.cachedOnly
                        StreamFilterMemory.cachedOnly = toggled
                        filters.copy(cachedOnly = toggled)
                    }

                    else -> {
                        null
                    }
                }
            return when {
                updated != null -> {
                    _uiState.update { StreamsFilterReducer.updateFilters(it, cachedActiveAddons, updated) }
                    true
                }

                event is StreamsEvent.SelectSort -> {
                    viewModelScope.launch { playerPrefsRepository.setSort(event.sort) }
                    _uiState.update { StreamsFilterReducer.updateSort(it, cachedActiveAddons, event.sort) }
                    true
                }

                event is StreamsEvent.ClearFilters -> {
                    StreamFilterMemory.reset()
                    _uiState.update { StreamsFilterReducer.clearFilters(it, cachedActiveAddons) }
                    true
                }

                else -> {
                    false
                }
            }
        }

        private fun handleStreamActionEvent(event: StreamsEvent): Boolean =
            when (event) {
                is StreamsEvent.PlayStream -> {
                    userPicked = true
                    _uiState.update { it.copy(resume = ResumeUi.None) }
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
            waitJob?.cancel()
            fingerprintDeferred?.cancel()
            cachedActiveAddons = emptyList()
            addonsLoaded = false
            userPicked = false
            handled = false
            waitExpired = false
            _uiState.update {
                StreamsUiState(
                    request = request,
                    isLoading = true,
                    selectedResolution = StreamFilterMemory.resolution,
                    selectedLanguage = StreamFilterMemory.language,
                    selectedType = StreamFilterMemory.type,
                    cachedOnly = StreamFilterMemory.cachedOnly,
                    selectedAddon = null,
                    sort = it.sort,
                    preferredLanguage = it.preferredLanguage,
                    preferredQuality = it.preferredQuality,
                    hideCam = it.hideCam,
                    quickPlayHandled = false,
                    resume = if (request.autoResume) ResumeUi.Looking else ResumeUi.None,
                    lastUsedKey = null,
                )
            }
            val progressKey =
                request.sourceHintKey ?: progressKeyFor(request.type, request.metaId, request.videoId)
            fingerprintDeferred =
                viewModelScope.async {
                    try {
                        sourceMemoryRepository.get(progressKey)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        null
                    }
                }
            if (request.autoResume) {
                viewModelScope.launch {
                    fingerprintDeferred?.await()
                    evaluateResumeDecision()
                }
                waitJob =
                    viewModelScope.launch {
                        delay(RESUME_WAIT_MS)
                        waitExpired = true
                        evaluateResumeDecision()
                    }
            }
            observeActiveAddons(request)
            startLoading(request = request, forceRefresh = false, onlyAddons = null, mergeExisting = false)
        }

        private fun handleClearRequest() {
            loadingJob?.cancel()
            loadingJob = null
            addonJob?.cancel()
            addonJob = null
            waitJob?.cancel()
            fingerprintDeferred?.cancel()
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
                        addonsLoaded = true
                        _uiState.update { StreamsFilterReducer.updateActiveAddons(it, matching) }
                        if (request.autoResume) {
                            evaluateResumeDecision()
                        }
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

        private data class PrefSettings(
            val sort: StreamSort,
            val preferredLanguage: String?,
            val preferredQuality: Int?,
            val hideCam: Boolean,
        )

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
                                if (it.fileName.isNullOrBlank()) {
                                    it.match
                                } else {
                                    StreamMatching.matchOf(
                                        it.fileName,
                                        request.season,
                                        request.episode,
                                        request.year,
                                    )
                                }
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

            evaluateResumeDecision()
            val suppressQuickPlay = request.autoResume && awaitFingerprint() != null

            if (emission.done && !_uiState.value.quickPlayHandled) {
                _uiState.update { it.copy(quickPlayHandled = true) }
                if (!suppressQuickPlay && playerPrefsRepository.quickPlay.first()) {
                    val candidate =
                        StreamOrdering.bestPlayable(
                            list = combined,
                            capabilities = playbackCapabilities,
                            preferredLanguage = _uiState.value.preferredLanguage,
                            preferredQuality = _uiState.value.preferredQuality,
                        )
                    if (candidate != null) {
                        playStream(candidate)
                    }
                }
            }
        }

        private suspend fun awaitFingerprint(): SourceFingerprint? =
            try {
                fingerprintDeferred?.await()
            } catch (_: Exception) {
                null
            }

        private suspend fun evaluateResumeDecision() {
            val req = _uiState.value.request
            if (req == null || fingerprintDeferred == null) return
            val fp = awaitFingerprint()
            val allStreams = _uiState.value.allStreams
            val key = lastUsedKey(fp, allStreams)
            _uiState.update { it.copy(lastUsedKey = key) }

            if (!req.autoResume) return

            val isAddonDisabled =
                if (addonsLoaded && fp != null && fp.addonKeyHashes.isNotEmpty()) {
                    fp.addonKeyHashes.none { hash -> cachedActiveAddons.any { hashAddonKey(it.transportUrl) == hash } }
                } else {
                    false
                }
            val allDone = _uiState.value.isDone
            val addonAnswered = isAddonDisabled || allDone

            val step =
                decideResume(
                    autoResume = req.autoResume,
                    fingerprint = fp,
                    streams = allStreams,
                    addonAnswered = addonAnswered,
                    allDone = allDone,
                    waitExpired = waitExpired,
                    userPicked = userPicked,
                    handled = handled,
                )

            if (step !is ResumeStep.Waiting) waitJob?.cancel()
            when (step) {
                ResumeStep.Waiting -> {
                    _uiState.update { it.copy(resume = ResumeUi.Looking) }
                }

                is ResumeStep.Start -> {
                    handled = true
                    _uiState.update { it.copy(resume = ResumeUi.None) }
                    playStream(step.stream)
                }

                ResumeStep.NotFound -> {
                    handled = true
                    _uiState.update { it.copy(resume = ResumeUi.NotFound) }
                }

                ResumeStep.NotApplicable -> {
                    _uiState.update { it.copy(resume = ResumeUi.None) }
                }
            }
        }

        private suspend fun playStream(stream: ParsedStream) {
            val req = _uiState.value.request
            when (val result = startPlayback(req, stream)) {
                is StartResult.Started -> {
                    _effects.emit(StreamsEffect.DismissSheet)
                }

                is StartResult.External -> {
                    _effects.emit(StreamsEffect.OpenExternalUrl(result.url))
                }

                is StartResult.Unsupported -> {
                    _effects.emit(StreamsEffect.ShowMessage(result.messageRes))
                }

                is StartResult.NeedsGate -> {
                    // Not triggered today
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
