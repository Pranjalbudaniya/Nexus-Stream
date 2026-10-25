package com.nexus.stream.presentation.feature.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.nexus.stream.domain.model.AppError
import com.nexus.stream.domain.model.EpisodeProgress
import com.nexus.stream.domain.model.EpisodeRef
import com.nexus.stream.domain.model.ListStatus
import com.nexus.stream.domain.model.MetaDetail
import com.nexus.stream.domain.model.Outcome
import com.nexus.stream.domain.model.PlayIntent
import com.nexus.stream.domain.model.SeasonGroup
import com.nexus.stream.domain.model.Video
import com.nexus.stream.domain.model.toPreview
import com.nexus.stream.domain.repository.LibraryRepository
import com.nexus.stream.domain.repository.MetaRepository
import com.nexus.stream.domain.repository.MetaResult
import com.nexus.stream.domain.repository.MetaSource
import com.nexus.stream.domain.repository.ProgressRepository
import com.nexus.stream.domain.usecase.WatchAction
import com.nexus.stream.domain.usecase.defaultSeason
import com.nexus.stream.domain.usecase.groupEpisodes
import com.nexus.stream.domain.usecase.isReleased
import com.nexus.stream.domain.usecase.resolvePlayIntent
import com.nexus.stream.domain.usecase.usesMovieRule
import com.nexus.stream.domain.usecase.watchActions
import com.nexus.stream.presentation.navigation.Detail
import com.nexus.stream.util.youtubeWatchUrlOrNull
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AllItemsKind {
    GENRES,
    CAST,
    DIRECTORS,
}

sealed interface PrimaryPlayState {
    data object NotReady : PrimaryPlayState

    data class Ready(
        val intent: PlayIntent,
        val targetVideoId: String?,
        val isEnabled: Boolean = true,
    ) : PrimaryPlayState
}

sealed interface DetailUiState {
    data object Loading : DetailUiState

    data class Content(
        val meta: MetaDetail,
        val source: MetaSource,
        val inWatchlist: Boolean = false,
        val listStatus: ListStatus? = null,
        val isSynopsisExpanded: Boolean = false,
        val seasons: List<SeasonGroup> = emptyList(),
        val selectedSeason: Int? = null,
        val episodeProgress: Map<String, EpisodeProgress> = emptyMap(),
        val trailerUrl: String? = null,
        val primaryPlay: PrimaryPlayState = PrimaryPlayState.NotReady,
        val isRefreshing: Boolean = false,
        val showStreamsSheet: Boolean = false,
        val streamsSheetVideoId: String? = null,
        val streamsSheetTitle: String? = null,
        val streamsSheetAutoResume: Boolean = false,
        val showWatchOptionsSheet: Boolean = false,
        val watchActions: List<WatchAction> = emptyList(),
        val pendingConfirmationAction: WatchAction? = null,
        val allSheetKind: AllItemsKind? = null,
    ) : DetailUiState {
        val activeVideoId: String?
            get() = (primaryPlay as? PrimaryPlayState.Ready)?.targetVideoId

        val isPlayEnabled: Boolean
            get() = (primaryPlay as? PrimaryPlayState.Ready)?.isEnabled == true
    }

    data class Error(
        val error: AppError,
    ) : DetailUiState
}

sealed interface DetailEvent {
    data object Retry : DetailEvent

    data object Refresh : DetailEvent

    data object ToggleWatchlist : DetailEvent

    data class SetListStatus(
        val status: ListStatus?,
    ) : DetailEvent

    data object PlayMain : DetailEvent

    data object ChooseSource : DetailEvent

    data class PlayEpisode(
        val videoId: String,
    ) : DetailEvent

    data object OpenTrailer : DetailEvent

    data object ToggleSynopsis : DetailEvent

    data class SelectSeason(
        val season: Int?,
    ) : DetailEvent

    data class ToggleWatched(
        val videoId: String,
    ) : DetailEvent

    data object DismissStreamsSheet : DetailEvent

    data object OpenWatchOptions : DetailEvent

    data object DismissWatchOptions : DetailEvent

    data class ApplyWatchAction(
        val action: WatchAction,
    ) : DetailEvent

    data object ConfirmPending : DetailEvent

    data object CancelPending : DetailEvent

    data class OpenAll(
        val kind: AllItemsKind,
    ) : DetailEvent

    data object DismissAllSheet : DetailEvent

    data class Download(
        val videoId: String? = null,
    ) : DetailEvent
}

sealed interface DetailEffect {
    data class OpenUrl(
        val url: String,
    ) : DetailEffect

    data class ShowMessage(
        val message: String,
    ) : DetailEffect
}

@HiltViewModel
class DetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val metaRepository: MetaRepository,
    private val libraryRepository: LibraryRepository,
    private val progressRepository: ProgressRepository,
    private val clock: () -> Long,
) : ViewModel() {
    val type: String =
        savedStateHandle.get<String>("type")
            ?: runCatching { savedStateHandle.toRoute<Detail>().type }.getOrDefault("")
    val id: String =
        savedStateHandle.get<String>("id")
            ?: runCatching { savedStateHandle.toRoute<Detail>().id }.getOrDefault("")

    private val _uiState = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<DetailEffect>()
    val effects: SharedFlow<DetailEffect> = _effects.asSharedFlow()

    private var userSelectedSeason: Int? = null
    private var currentListStatus: ListStatus? = null
    private var progressEmitted: Boolean = false
    private var latestProgress: Map<String, EpisodeProgress>? = null

    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        metaRepository: MetaRepository,
        libraryRepository: LibraryRepository,
        progressRepository: ProgressRepository,
    ) : this(
        savedStateHandle = savedStateHandle,
        metaRepository = metaRepository,
        libraryRepository = libraryRepository,
        progressRepository = progressRepository,
        clock = { System.currentTimeMillis() },
    )

    init {
        loadMeta(forceRefresh = false)
        observeUserData()
    }

    fun onEvent(event: DetailEvent) {
        if (event is DetailEvent.Retry) {
            loadMeta(forceRefresh = false)
            return
        }
        val current = _uiState.value as? DetailUiState.Content ?: return
        handleContentEvent(event, current)
    }

    private fun handlePlaybackEvent(
        event: DetailEvent,
        content: DetailUiState.Content,
    ): Boolean =
        when (event) {
            DetailEvent.PlayMain -> {
                content.activeVideoId?.let { targetId ->
                    openStreamsSheet(content, targetId, content.meta.name, isResumable(content))
                }
                true
            }

            DetailEvent.ChooseSource -> {
                if (isResumable(content)) {
                    content.activeVideoId?.let { targetId ->
                        openStreamsSheet(content, targetId, content.meta.name, autoResume = false)
                    }
                }
                true
            }

            is DetailEvent.PlayEpisode -> {
                val video = content.meta.videos.find { it.id == event.videoId }
                if (video != null && !video.isReleased(clock())) {
                    viewModelScope.launch { _effects.emit(DetailEffect.ShowMessage("Not released yet")) }
                } else if (video != null) {
                    openStreamsSheet(
                        content,
                        event.videoId,
                        "${content.meta.name} - ${video.title}",
                        autoResume = false,
                    )
                }
                true
            }

            DetailEvent.OpenTrailer -> {
                content.trailerUrl?.let { url ->
                    viewModelScope.launch { _effects.emit(DetailEffect.OpenUrl(url)) }
                }
                true
            }

            is DetailEvent.Download -> {
                viewModelScope.launch { _effects.emit(DetailEffect.ShowMessage("Downloads aren't available yet.")) }
                true
            }

            else -> {
                false
            }
        }

    private fun openStreamsSheet(
        content: DetailUiState.Content,
        targetId: String,
        title: String,
        autoResume: Boolean,
    ) {
        _uiState.update {
            content.copy(
                showStreamsSheet = true,
                streamsSheetVideoId = targetId,
                streamsSheetTitle = title,
                streamsSheetAutoResume = autoResume,
            )
        }
    }

    private fun handleSheetEvent(event: DetailEvent): Boolean =
        when (event) {
            DetailEvent.DismissStreamsSheet -> {
                _uiState.update {
                    if (it is DetailUiState.Content) {
                        it.copy(
                            showStreamsSheet = false,
                            streamsSheetVideoId = null,
                            streamsSheetTitle = null,
                            streamsSheetAutoResume = false,
                        )
                    } else {
                        it
                    }
                }
                true
            }

            DetailEvent.DismissWatchOptions -> {
                _uiState.update { if (it is DetailUiState.Content) it.copy(showWatchOptionsSheet = false) else it }
                true
            }

            is DetailEvent.OpenAll -> {
                _uiState.update { if (it is DetailUiState.Content) it.copy(allSheetKind = event.kind) else it }
                true
            }

            DetailEvent.DismissAllSheet -> {
                _uiState.update { if (it is DetailUiState.Content) it.copy(allSheetKind = null) else it }
                true
            }

            else -> {
                false
            }
        }

    private fun handleContentEvent(
        event: DetailEvent,
        content: DetailUiState.Content,
    ) {
        if (handlePlaybackEvent(event, content) || handleSheetEvent(event)) {
            return
        }
        when (event) {
            DetailEvent.Refresh -> {
                _uiState.value = content.copy(isRefreshing = true)
                loadMeta(forceRefresh = true)
            }

            DetailEvent.ToggleWatchlist -> {
                val newStatus = if (content.inWatchlist) null else ListStatus.PLAN_TO_WATCH
                viewModelScope.launch { libraryRepository.setStatus(content.meta.toPreview(), newStatus) }
            }

            is DetailEvent.SetListStatus -> {
                viewModelScope.launch { libraryRepository.setStatus(content.meta.toPreview(), event.status) }
            }

            DetailEvent.ToggleSynopsis -> {
                _uiState.update {
                    if (it is DetailUiState.Content) {
                        it.copy(isSynopsisExpanded = !it.isSynopsisExpanded)
                    } else {
                        it
                    }
                }
            }

            is DetailEvent.SelectSeason -> {
                userSelectedSeason = event.season
                _uiState.update { if (it is DetailUiState.Content) it.copy(selectedSeason = event.season) else it }
            }

            is DetailEvent.ToggleWatched -> {
                val video = content.meta.videos.find { it.id == event.videoId }
                val isWatched = content.episodeProgress[event.videoId]?.watched == true
                viewModelScope.launch {
                    progressRepository.setWatched(
                        meta = content.meta.toPreview(),
                        episode =
                            EpisodeRef(
                                videoId = event.videoId,
                                type = content.meta.type,
                                season = video?.season,
                                episode = video?.episode,
                            ),
                        watched = !isWatched,
                    )
                }
            }

            else -> {
                handleWatchEvent(event, content)
            }
        }
    }

    private fun handleWatchEvent(
        event: DetailEvent,
        content: DetailUiState.Content,
    ) {
        when (event) {
            DetailEvent.OpenWatchOptions -> {
                val actions =
                    watchActions(
                        type = content.meta.type,
                        videos = content.meta.videos,
                        progress = content.episodeProgress,
                        metaId = content.meta.id,
                        inWatchlist = content.inWatchlist,
                        selectedSeason = content.selectedSeason,
                        now = clock(),
                    )
                _uiState.update {
                    if (it is DetailUiState.Content) {
                        it.copy(showWatchOptionsSheet = true, watchActions = actions)
                    } else {
                        it
                    }
                }
            }

            is DetailEvent.ApplyWatchAction -> {
                handleApplyWatchAction(content, event.action)
            }

            DetailEvent.ConfirmPending -> {
                content.pendingConfirmationAction?.let { action ->
                    _uiState.update {
                        if (it is DetailUiState.Content) {
                            it.copy(pendingConfirmationAction = null)
                        } else {
                            it
                        }
                    }
                    executeAction(content, action)
                }
            }

            DetailEvent.CancelPending -> {
                _uiState.update { if (it is DetailUiState.Content) it.copy(pendingConfirmationAction = null) else it }
            }

            else -> {}
        }
    }

    private fun handleApplyWatchAction(
        content: DetailUiState.Content,
        action: WatchAction,
    ) {
        _uiState.update { if (it is DetailUiState.Content) it.copy(showWatchOptionsSheet = false) else it }
        val needsConfirmation =
            when (action) {
                is WatchAction.SetSeasonWatched -> !action.watched && action.count > 1
                is WatchAction.SetAllWatched -> !action.watched && action.count > 1
                else -> false
            }
        if (needsConfirmation) {
            _uiState.update { if (it is DetailUiState.Content) it.copy(pendingConfirmationAction = action) else it }
        } else {
            executeAction(content, action)
        }
    }

    private fun executeAction(
        content: DetailUiState.Content,
        action: WatchAction,
    ) {
        viewModelScope.launch {
            val msg =
                when (action) {
                    is WatchAction.ToggleWatchlist -> {
                        val status = if (action.add) ListStatus.PLAN_TO_WATCH else null
                        libraryRepository.setStatus(content.meta.toPreview(), status)
                        if (action.add) "Added to watchlist" else "Removed from watchlist"
                    }

                    is WatchAction.SetMovieWatched -> {
                        progressRepository.setWatched(
                            meta = content.meta.toPreview(),
                            episode = EpisodeRef(videoId = content.meta.id, type = "movie"),
                            watched = action.watched,
                        )
                        if (action.watched) "Marked as watched" else "Marked as unwatched"
                    }

                    is WatchAction.SetSeasonWatched -> {
                        val targets =
                            content.meta.videos
                                .filter { it.season == action.season && it.isReleased(clock()) }
                                .map {
                                    EpisodeRef(
                                        videoId = it.id,
                                        type = content.meta.type,
                                        season = it.season,
                                        episode = it.episode,
                                    )
                                }
                        progressRepository.bulkMark(content, targets, action.watched, action.count)
                    }

                    is WatchAction.SetAllWatched -> {
                        val targets =
                            content.meta.videos
                                .filter { it.isReleased(clock()) }
                                .map {
                                    EpisodeRef(
                                        videoId = it.id,
                                        type = content.meta.type,
                                        season = it.season,
                                        episode = it.episode,
                                    )
                                }
                        progressRepository.bulkMark(content, targets, action.watched, action.count)
                    }

                    is WatchAction.RemoveFromContinue -> {
                        progressRepository.clearInProgress(content.meta.id)
                        "Removed from Continue watching"
                    }
                }
            _effects.emit(DetailEffect.ShowMessage(msg))
        }
    }

    private fun loadMeta(forceRefresh: Boolean) {
        viewModelScope.launch {
            if (!forceRefresh) {
                _uiState.value = DetailUiState.Loading
            }
            when (val outcome = metaRepository.getMeta(type, id, forceRefresh)) {
                is Outcome.Success -> {
                    updateWithResult(outcome.value)
                }

                is Outcome.Failure -> {
                    val current = _uiState.value
                    if (current is DetailUiState.Content) {
                        _uiState.value = current.copy(isRefreshing = false)
                        _effects.emit(DetailEffect.ShowMessage("Couldn't refresh."))
                    } else {
                        _uiState.value = DetailUiState.Error(outcome.error)
                    }
                }
            }
        }
    }

    private fun updateWithResult(result: MetaResult) {
        val meta = result.meta
        val seasons = if (meta.videos.isNotEmpty()) groupEpisodes(meta.videos) else emptyList()
        val trailerUrl = meta.trailers.firstNotNullOfOrNull { youtubeWatchUrlOrNull(it) }

        val currentContent = _uiState.value as? DetailUiState.Content
        val status = currentListStatus ?: currentContent?.listStatus
        val inWatchlist = status != null
        val progress = latestProgress ?: currentContent?.episodeProgress.orEmpty()
        val synopsisExpanded = currentContent?.isSynopsisExpanded ?: false

        val selectedSeason = userSelectedSeason ?: calculateDefaultSeason(seasons, progress)
        val primaryPlay =
            if (progressEmitted) {
                buildPrimaryPlayState(meta, progress, clock())
            } else {
                PrimaryPlayState.NotReady
            }

        _uiState.value =
            DetailUiState.Content(
                meta = meta,
                source = result.source,
                inWatchlist = inWatchlist,
                listStatus = status,
                isSynopsisExpanded = synopsisExpanded,
                seasons = seasons,
                selectedSeason = selectedSeason,
                episodeProgress = progress,
                trailerUrl = trailerUrl,
                primaryPlay = primaryPlay,
                isRefreshing = false,
            )
    }

    private fun observeUserData() {
        viewModelScope.launch {
            combine(
                libraryRepository.observeListStatuses(),
                progressRepository.observeEpisodeProgress(id),
            ) { statuses, progressMap ->
                val listStatus = statuses[id]
                val inWatchlist = listStatus != null
                Triple(inWatchlist, listStatus, progressMap)
            }.collect { (inWatchlist, listStatus, progressMap) ->
                progressEmitted = true
                latestProgress = progressMap
                currentListStatus = listStatus
                _uiState.update { current ->
                    if (current !is DetailUiState.Content) return@update current
                    val selectedSeason =
                        userSelectedSeason
                            ?: calculateDefaultSeason(current.seasons, progressMap)
                    val primaryPlay = buildPrimaryPlayState(current.meta, progressMap, clock())
                    val actions =
                        if (current.showWatchOptionsSheet) {
                            watchActions(
                                type = current.meta.type,
                                videos = current.meta.videos,
                                progress = progressMap,
                                metaId = current.meta.id,
                                inWatchlist = inWatchlist,
                                selectedSeason = selectedSeason,
                                now = clock(),
                            )
                        } else {
                            current.watchActions
                        }

                    current.copy(
                        inWatchlist = inWatchlist,
                        listStatus = listStatus,
                        episodeProgress = progressMap,
                        selectedSeason = selectedSeason,
                        primaryPlay = primaryPlay,
                        watchActions = actions,
                    )
                }
            }
        }
    }
}

private suspend fun ProgressRepository.bulkMark(
    content: DetailUiState.Content,
    episodes: List<EpisodeRef>,
    watched: Boolean,
    count: Int,
): String {
    setWatchedMany(content.meta.toPreview(), episodes, watched)
    return if (watched) "Marked $count episodes as watched" else "Marked $count episodes as unwatched"
}

private fun calculateDefaultSeason(
    seasons: List<SeasonGroup>,
    progress: Map<String, EpisodeProgress>,
): Int? {
    val latestProgress =
        progress.values
            .sortedByDescending { it.updatedAt }
            .firstOrNull()
    return defaultSeason(seasons, latestProgress?.videoId)
}

private fun buildPrimaryPlayState(
    meta: MetaDetail,
    progress: Map<String, EpisodeProgress>,
    now: Long,
): PrimaryPlayState.Ready {
    val intent =
        resolvePlayIntent(
            type = meta.type,
            metaId = meta.id,
            videos = meta.videos,
            progress = progress,
            now = now,
        )
    val targetVideoId =
        when (intent) {
            is PlayIntent.Start -> {
                if (meta.type == "series") {
                    val firstReleased =
                        meta.videos.firstOrNull { it.isReleased(now) } ?: meta.videos.firstOrNull()
                    firstReleased?.id
                } else {
                    meta.defaultVideoId ?: meta.id
                }
            }

            is PlayIntent.Resume -> {
                if (usesMovieRule(meta.type, meta.videos)) {
                    meta.defaultVideoId ?: meta.id
                } else {
                    intent.videoId
                }
            }

            is PlayIntent.Next -> {
                intent.videoId
            }
        }
    val isEnabled =
        when (intent) {
            is PlayIntent.Start -> {
                if (meta.type == "series") {
                    val firstReleased =
                        meta.videos.firstOrNull { it.isReleased(now) } ?: meta.videos.firstOrNull()
                    firstReleased != null
                } else {
                    true
                }
            }

            is PlayIntent.Resume -> {
                true
            }

            is PlayIntent.Next -> {
                true
            }
        }
    return PrimaryPlayState.Ready(
        intent = intent,
        targetVideoId = targetVideoId,
        isEnabled = isEnabled,
    )
}

private fun isResumable(content: DetailUiState.Content): Boolean =
    content.primaryPlay is PrimaryPlayState.Ready && content.primaryPlay.intent is PlayIntent.Resume
