package com.nexus.stream.presentation.feature.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.nexus.stream.domain.model.AppError
import com.nexus.stream.domain.model.EpisodeProgress
import com.nexus.stream.domain.model.EpisodeRef
import com.nexus.stream.domain.model.MetaDetail
import com.nexus.stream.domain.model.Outcome
import com.nexus.stream.domain.model.SeasonGroup
import com.nexus.stream.domain.model.toPreview
import com.nexus.stream.domain.repository.LibraryRepository
import com.nexus.stream.domain.repository.MetaRepository
import com.nexus.stream.domain.repository.MetaResult
import com.nexus.stream.domain.repository.MetaSource
import com.nexus.stream.domain.repository.ProgressRepository
import com.nexus.stream.domain.usecase.defaultSeason
import com.nexus.stream.domain.usecase.groupEpisodes
import com.nexus.stream.domain.usecase.isReleased
import com.nexus.stream.domain.usecase.resumeVideoId
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

sealed interface DetailUiState {
    data object Loading : DetailUiState

    data class Content(
        val meta: MetaDetail,
        val source: MetaSource,
        val inWatchlist: Boolean = false,
        val isSynopsisExpanded: Boolean = false,
        val seasons: List<SeasonGroup> = emptyList(),
        val selectedSeason: Int? = null,
        val episodeProgress: Map<String, EpisodeProgress> = emptyMap(),
        val trailerUrl: String? = null,
        val playActionTitle: String = "Play",
        val isPlayEnabled: Boolean = true,
        val activeVideoId: String? = null,
        val isRefreshing: Boolean = false,
        val showStreamsSheet: Boolean = false,
        val streamsSheetVideoId: String? = null,
        val streamsSheetTitle: String? = null,
    ) : DetailUiState

    data class Error(
        val error: AppError,
    ) : DetailUiState
}

sealed interface DetailEvent {
    data object Retry : DetailEvent

    data object Refresh : DetailEvent

    data object ToggleWatchlist : DetailEvent

    data object PlayMain : DetailEvent

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

    private val routeArgs: Detail? = runCatching { savedStateHandle.toRoute<Detail>() }.getOrNull()
    val type: String = routeArgs?.type ?: checkNotNull(savedStateHandle["type"])
    val id: String = routeArgs?.id ?: checkNotNull(savedStateHandle["id"])

    private val _uiState = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<DetailEffect>()
    val effects: SharedFlow<DetailEffect> = _effects.asSharedFlow()

    private var userSelectedSeason: Int? = null

    init {
        loadMeta(forceRefresh = false)
        observeUserData()
    }

    fun onEvent(event: DetailEvent) {
        when (event) {
            DetailEvent.Retry -> {
                loadMeta(forceRefresh = false)
            }

            DetailEvent.Refresh -> {
                refresh()
            }

            DetailEvent.ToggleWatchlist -> {
                toggleWatchlist()
            }

            DetailEvent.PlayMain -> {
                playMain()
            }

            is DetailEvent.PlayEpisode -> {
                playEpisode(event.videoId)
            }

            DetailEvent.OpenTrailer -> {
                openTrailer()
            }

            DetailEvent.ToggleSynopsis -> {
                _uiState.update { cur ->
                    if (cur is DetailUiState.Content) cur.copy(isSynopsisExpanded = !cur.isSynopsisExpanded) else cur
                }
            }

            is DetailEvent.SelectSeason -> {
                userSelectedSeason = event.season
                _uiState.update { cur ->
                    if (cur is DetailUiState.Content) cur.copy(selectedSeason = event.season) else cur
                }
            }

            is DetailEvent.ToggleWatched -> {
                toggleWatched(event.videoId)
            }

            DetailEvent.DismissStreamsSheet -> {
                _uiState.update { cur ->
                    if (cur is DetailUiState.Content) cur.copy(showStreamsSheet = false) else cur
                }
            }
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

    private fun refresh() {
        val current = _uiState.value as? DetailUiState.Content ?: return
        _uiState.value = current.copy(isRefreshing = true)
        loadMeta(forceRefresh = true)
    }

    private fun updateWithResult(result: MetaResult) {
        val meta = result.meta
        val seasons = if (meta.videos.isNotEmpty()) groupEpisodes(meta.videos) else emptyList()
        val trailerUrl = meta.trailers.firstNotNullOfOrNull { youtubeWatchUrlOrNull(it) }

        val currentContent = _uiState.value as? DetailUiState.Content
        val inWatchlist = currentContent?.inWatchlist ?: false
        val progress = currentContent?.episodeProgress.orEmpty()
        val synopsisExpanded = currentContent?.isSynopsisExpanded ?: false

        val selectedSeason = userSelectedSeason ?: calculateDefaultSeason(seasons, progress)
        val playState = calculatePlayState(meta, progress, clock())

        _uiState.value =
            DetailUiState.Content(
                meta = meta,
                source = result.source,
                inWatchlist = inWatchlist,
                isSynopsisExpanded = synopsisExpanded,
                seasons = seasons,
                selectedSeason = selectedSeason,
                episodeProgress = progress,
                trailerUrl = trailerUrl,
                playActionTitle = playState.title,
                isPlayEnabled = playState.isEnabled,
                activeVideoId = playState.videoId,
                isRefreshing = false,
            )
    }

    private fun observeUserData() {
        viewModelScope.launch {
            combine(
                libraryRepository.observeWatchlistIds(),
                progressRepository.observeEpisodeProgress(id),
            ) { watchlistIds, progressMap ->
                val inWatchlist = watchlistIds.contains(id)
                Pair(inWatchlist, progressMap)
            }.collect { (inWatchlist, progressMap) ->
                _uiState.update { current ->
                    if (current !is DetailUiState.Content) return@update current
                    val selectedSeason =
                        userSelectedSeason
                            ?: calculateDefaultSeason(current.seasons, progressMap)
                    val playState = calculatePlayState(current.meta, progressMap, clock())

                    current.copy(
                        inWatchlist = inWatchlist,
                        episodeProgress = progressMap,
                        selectedSeason = selectedSeason,
                        playActionTitle = playState.title,
                        isPlayEnabled = playState.isEnabled,
                        activeVideoId = playState.videoId,
                    )
                }
            }
        }
    }

    private fun toggleWatchlist() {
        val current = _uiState.value as? DetailUiState.Content ?: return
        viewModelScope.launch {
            libraryRepository.toggleWatchlist(current.meta.toPreview())
        }
    }

    private fun playMain() {
        val current = _uiState.value as? DetailUiState.Content ?: return
        val targetId = current.activeVideoId ?: return
        _uiState.update {
            current.copy(
                showStreamsSheet = true,
                streamsSheetVideoId = targetId,
                streamsSheetTitle = current.meta.name,
            )
        }
    }

    private fun playEpisode(videoId: String) {
        val current = _uiState.value as? DetailUiState.Content ?: return
        val video = current.meta.videos.find { it.id == videoId } ?: return
        if (!video.isReleased(clock())) {
            viewModelScope.launch {
                _effects.emit(DetailEffect.ShowMessage("Not released yet"))
            }
        } else {
            val sheetTitle = "${current.meta.name} - ${video.title}"
            _uiState.update {
                current.copy(
                    showStreamsSheet = true,
                    streamsSheetVideoId = videoId,
                    streamsSheetTitle = sheetTitle,
                )
            }
        }
    }

    private fun openTrailer() {
        val current = _uiState.value as? DetailUiState.Content ?: return
        val url = current.trailerUrl ?: return
        viewModelScope.launch {
            _effects.emit(DetailEffect.OpenUrl(url))
        }
    }

    private fun toggleWatched(videoId: String) {
        val current = _uiState.value as? DetailUiState.Content ?: return
        val video = current.meta.videos.find { it.id == videoId }
        val isWatched = current.episodeProgress[videoId]?.watched == true
        viewModelScope.launch {
            progressRepository.setWatched(
                meta = current.meta.toPreview(),
                episode =
                    EpisodeRef(
                        videoId = videoId,
                        type = current.meta.type,
                        season = video?.season,
                        episode = video?.episode,
                    ),
                watched = !isWatched,
            )
        }
    }
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

private data class PlayState(
    val title: String,
    val isEnabled: Boolean,
    val videoId: String?,
)

private fun calculatePlayState(
    meta: MetaDetail,
    progress: Map<String, EpisodeProgress>,
    now: Long,
): PlayState {
    if (meta.type != "series") {
        return PlayState("Play", true, meta.defaultVideoId ?: meta.id)
    }

    val resumeId = resumeVideoId(meta.videos, progress, now)
    return when {
        meta.videos.isEmpty() -> {
            PlayState("Play", false, null)
        }

        resumeId != null -> {
            val video = meta.videos.find { it.id == resumeId }
            val s = video?.season ?: 1
            val e = video?.episode ?: 1
            val inProgress =
                (progress[resumeId]?.positionMs ?: 0L) > 0L &&
                    progress[resumeId]?.watched != true
            val title = if (inProgress) "Resume S$s E$e" else "Play S$s E$e"
            PlayState(title, true, resumeId)
        }

        else -> {
            val firstReleased =
                meta.videos.firstOrNull { it.isReleased(now) } ?: meta.videos.firstOrNull()
            PlayState("Play", firstReleased != null, firstReleased?.id)
        }
    }
}
