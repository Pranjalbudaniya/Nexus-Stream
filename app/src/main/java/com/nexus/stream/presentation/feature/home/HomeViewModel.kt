package com.nexus.stream.presentation.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexus.stream.data.remote.addon.HomeRowLoader
import com.nexus.stream.di.DefaultDispatcher
import com.nexus.stream.domain.model.AppError
import com.nexus.stream.domain.model.ContinueWatchingItem
import com.nexus.stream.domain.model.HomeRowSpec
import com.nexus.stream.domain.model.ListStatus
import com.nexus.stream.domain.model.MetaPreview
import com.nexus.stream.domain.model.Outcome
import com.nexus.stream.domain.model.PosterMark
import com.nexus.stream.domain.model.RowState
import com.nexus.stream.domain.repository.AddonRepository
import com.nexus.stream.domain.repository.ContentPrefsRepository
import com.nexus.stream.domain.repository.LibraryRepository
import com.nexus.stream.domain.repository.ProgressRepository
import com.nexus.stream.domain.usecase.BuildHero
import com.nexus.stream.domain.usecase.BuildHomeRows
import com.nexus.stream.domain.usecase.EnsureDefaultAddons
import com.nexus.stream.domain.usecase.buildPosterMarks
import com.nexus.stream.domain.usecase.isAdultContent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed interface RowStatus {
    data object Loading : RowStatus

    data class Loaded(
        val items: List<MetaPreview>,
    ) : RowStatus

    data class Failed(
        val error: AppError,
    ) : RowStatus

    fun toRowState(): RowState =
        when (this) {
            is Loading -> RowState.Loading
            is Loaded -> RowState.Loaded(items)
            is Failed -> RowState.Failed(error)
        }
}

data class HomeRow(
    val spec: HomeRowSpec,
    val status: RowStatus,
)

data class HomeUiState(
    val rows: List<HomeRow> = emptyList(),
    val hero: List<MetaPreview> = emptyList(),
    val isFirstRowLoading: Boolean = false,
    val watchlistIds: Set<String> = emptySet(),
    val listStatuses: Map<String, ListStatus> = emptyMap(),
    val posterMarks: Map<String, PosterMark> = emptyMap(),
    val confirmRemoveItem: Pair<MetaPreview, ListStatus>? = null,
    val continueWatching: List<ContinueWatchingItem> = emptyList(),
    val hasAddons: Boolean = false,
    val hasCatalogRows: Boolean = false,
    val seedFailed: Boolean = false,
    val offline: Boolean = false,
    val isRefreshing: Boolean = false,
)

sealed interface HomeEvent {
    data class Retry(
        val key: String,
    ) : HomeEvent

    data object Refresh : HomeEvent

    data object RetrySeed : HomeEvent

    data class ToggleWatchlist(
        val item: MetaPreview,
    ) : HomeEvent

    data class ConfirmRemoveWatchlist(
        val item: MetaPreview,
    ) : HomeEvent

    data object DismissRemoveDialog : HomeEvent

    data class RemoveContinue(
        val metaId: String,
    ) : HomeEvent
}

sealed interface HomeEffect

@HiltViewModel
@Suppress("LongParameterList")
class HomeViewModel
    @Inject
    constructor(
        private val addonRepository: AddonRepository,
        private val ensureDefaultAddons: EnsureDefaultAddons,
        private val homeRowLoader: HomeRowLoader,
        private val libraryRepository: LibraryRepository,
        private val progressRepository: ProgressRepository,
        private val contentPrefsRepository: ContentPrefsRepository,
        @DefaultDispatcher private val dispatcher: CoroutineDispatcher,
    ) : ViewModel() {
        private val buildHomeRows = BuildHomeRows()
        private val buildHero = BuildHero()

        private val _uiState = MutableStateFlow(HomeUiState())
        val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

        val hero: List<MetaPreview>
            get() = _uiState.value.hero

        val watchlistIds: Set<String>
            get() = _uiState.value.watchlistIds

        val continueWatching: List<ContinueWatchingItem>
            get() = _uiState.value.continueWatching

        private val _effects = MutableSharedFlow<HomeEffect>()
        val effects: SharedFlow<HomeEffect> = _effects.asSharedFlow()

        private val rowStates = mutableMapOf<String, HomeRow>()
        private val rowJobs = mutableMapOf<String, Job>()
        private var currentSpecs: List<HomeRowSpec> = emptyList()
        private var hasAddons = false
        private var seedFailed = false
        private var isRefreshing = false

        private var isFamilyMode = true
        private var listStatusesMap = emptyMap<String, ListStatus>()
        private var watchedMovieIdsSet = emptySet<String>()

        init {
            runSeed()
            observeState()
        }

        fun onEvent(event: HomeEvent) {
            when (event) {
                is HomeEvent.Retry -> {
                    onRetryRow(event.key)
                }

                is HomeEvent.Refresh -> {
                    onRefresh()
                }

                is HomeEvent.RetrySeed -> {
                    runSeed()
                }

                is HomeEvent.ToggleWatchlist -> {
                    onToggleWatchlist(event.item)
                }

                is HomeEvent.ConfirmRemoveWatchlist -> {
                    _uiState.update { it.copy(confirmRemoveItem = null) }
                    viewModelScope.launch { libraryRepository.setStatus(event.item, null) }
                }

                is HomeEvent.DismissRemoveDialog -> {
                    _uiState.update { it.copy(confirmRemoveItem = null) }
                }

                is HomeEvent.RemoveContinue -> {
                    viewModelScope.launch { progressRepository.removeFromContinueWatching(event.metaId) }
                }
            }
        }

        private fun onToggleWatchlist(item: MetaPreview) {
            val currentStatus = listStatusesMap[item.id]
            if (currentStatus == null) {
                viewModelScope.launch { libraryRepository.setStatus(item, ListStatus.PLAN_TO_WATCH) }
            } else if (currentStatus == ListStatus.PLAN_TO_WATCH) {
                viewModelScope.launch { libraryRepository.setStatus(item, null) }
            } else {
                _uiState.update { it.copy(confirmRemoveItem = item to currentStatus) }
            }
        }

        private fun observeState() {
            viewModelScope.launch {
                contentPrefsRepository.familyMode.collectLatest { enabled ->
                    isFamilyMode = enabled
                    recomputeUiState()
                }
            }
            viewModelScope.launch {
                libraryRepository.observeListStatuses().collectLatest { statuses ->
                    listStatusesMap = statuses
                    recomputeUiState()
                }
            }
            viewModelScope.launch {
                progressRepository.observeWatchedMovieIds().collectLatest { ids ->
                    watchedMovieIdsSet = ids
                    recomputeUiState()
                }
            }
            viewModelScope.launch {
                progressRepository.observeContinueWatching().collectLatest { items ->
                    _uiState.update { it.copy(continueWatching = items) }
                }
            }
            viewModelScope.launch {
                combine(
                    addonRepository.observeAll(),
                    addonRepository.observeActive(),
                ) { all, active ->
                    Pair(all, active)
                }.collectLatest { (all, active) ->
                    hasAddons = all.isNotEmpty()
                    val specs = withContext(dispatcher) { buildHomeRows(active) }
                    currentSpecs = specs

                    val newKeySet = specs.map { it.key }.toSet()
                    val removedKeys = rowStates.keys - newKeySet
                    for (key in removedKeys) {
                        rowJobs.remove(key)?.cancel()
                        rowStates.remove(key)
                    }

                    for (spec in specs) {
                        if (spec.key !in rowStates) {
                            rowStates[spec.key] = HomeRow(spec = spec, status = RowStatus.Loading)
                            loadRow(spec, force = false)
                        }
                    }
                    recomputeUiState()
                }
            }
        }

        private fun loadRow(
            spec: HomeRowSpec,
            force: Boolean,
        ) {
            rowJobs[spec.key]?.cancel()
            rowStates[spec.key] = HomeRow(spec = spec, status = RowStatus.Loading)
            recomputeUiState()

            rowJobs[spec.key] =
                viewModelScope.launch {
                    val outcome = homeRowLoader.load(spec, force = force)
                    if (spec.key in rowStates) {
                        val status =
                            when (outcome) {
                                is Outcome.Success -> RowStatus.Loaded(outcome.value)
                                is Outcome.Failure -> RowStatus.Failed(outcome.error)
                            }
                        rowStates[spec.key] = HomeRow(spec = spec, status = status)
                        recomputeUiState()
                    }
                }
        }

        private fun onRetryRow(key: String) {
            val spec = rowStates[key]?.spec ?: currentSpecs.find { it.key == key } ?: return
            loadRow(spec, force = true)
        }

        private fun onRefresh() {
            if (seedFailed) {
                runSeed()
            }
            isRefreshing = true
            recomputeUiState()

            viewModelScope.launch {
                homeRowLoader.clearAll()
                val jobs =
                    currentSpecs.map { spec ->
                        rowJobs[spec.key]?.cancel()
                        rowStates[spec.key] = HomeRow(spec = spec, status = RowStatus.Loading)
                        launch {
                            val outcome = homeRowLoader.load(spec, force = true)
                            if (spec.key in rowStates) {
                                val status =
                                    when (outcome) {
                                        is Outcome.Success -> RowStatus.Loaded(outcome.value)
                                        is Outcome.Failure -> RowStatus.Failed(outcome.error)
                                    }
                                rowStates[spec.key] = HomeRow(spec = spec, status = status)
                                recomputeUiState()
                            }
                        }
                    }
                recomputeUiState()
                jobs.joinAll()
                isRefreshing = false
                recomputeUiState()
            }
        }

        private fun runSeed() {
            viewModelScope.launch {
                when (ensureDefaultAddons()) {
                    is Outcome.Success -> {
                        seedFailed = false
                        recomputeUiState()
                    }

                    is Outcome.Failure -> {
                        seedFailed = true
                        recomputeUiState()
                    }
                }
            }
        }

        private fun recomputeUiState() {
            val orderedRows = currentSpecs.mapNotNull { rowStates[it.key] }
            val filteredRows =
                orderedRows.mapNotNull { row ->
                    when (val status = row.status) {
                        is RowStatus.Loaded -> {
                            val items =
                                if (isFamilyMode) {
                                    status.items.filter { !it.isAdultContent() }
                                } else {
                                    status.items
                                }
                            if (isFamilyMode && status.items.isNotEmpty() && items.isEmpty()) {
                                null
                            } else {
                                row.copy(status = RowStatus.Loaded(items))
                            }
                        }

                        else -> {
                            row
                        }
                    }
                }

            val networkFailures =
                filteredRows.count {
                    it.status is RowStatus.Failed && it.status.error is AppError.Network
                }
            val anyLoading = filteredRows.any { it.status is RowStatus.Loading }
            val offline = networkFailures >= 2 && !anyLoading
            val hero = buildHero(filteredRows.map { it.status.toRowState() })
            val isFirstRowLoading = filteredRows.isNotEmpty() && filteredRows.first().status is RowStatus.Loading
            val posterMarks = buildPosterMarks(listStatusesMap, watchedMovieIdsSet)

            _uiState.update {
                it.copy(
                    rows = filteredRows,
                    hero = hero,
                    isFirstRowLoading = isFirstRowLoading,
                    watchlistIds = listStatusesMap.keys,
                    listStatuses = listStatusesMap,
                    posterMarks = posterMarks,
                    hasAddons = hasAddons,
                    hasCatalogRows = currentSpecs.isNotEmpty(),
                    seedFailed = seedFailed,
                    offline = offline,
                    isRefreshing = isRefreshing,
                )
            }
        }
    }
