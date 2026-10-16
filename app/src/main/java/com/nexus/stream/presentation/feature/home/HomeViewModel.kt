package com.nexus.stream.presentation.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexus.stream.data.remote.addon.HomeRowLoader
import com.nexus.stream.di.DefaultDispatcher
import com.nexus.stream.domain.model.AppError
import com.nexus.stream.domain.model.ContinueWatchingItem
import com.nexus.stream.domain.model.HomeRowSpec
import com.nexus.stream.domain.model.MetaPreview
import com.nexus.stream.domain.model.Outcome
import com.nexus.stream.domain.model.RowState
import com.nexus.stream.domain.repository.AddonRepository
import com.nexus.stream.domain.repository.LibraryRepository
import com.nexus.stream.domain.repository.ProgressRepository
import com.nexus.stream.domain.usecase.BuildHero
import com.nexus.stream.domain.usecase.BuildHomeRows
import com.nexus.stream.domain.usecase.EnsureDefaultAddons
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
            Loading -> RowState.Loading
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

    data class RemoveContinue(
        val metaId: String,
    ) : HomeEvent
}

sealed interface HomeEffect

@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        private val addonRepository: AddonRepository,
        private val ensureDefaultAddons: EnsureDefaultAddons,
        private val homeRowLoader: HomeRowLoader,
        private val libraryRepository: LibraryRepository,
        private val progressRepository: ProgressRepository,
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

        init {
            runSeed()
            observeAddons()
            observeWatchlist()
            observeContinueWatching()
        }

        fun onEvent(event: HomeEvent) {
            when (event) {
                is HomeEvent.Retry -> onRetryRow(event.key)
                is HomeEvent.Refresh -> onRefresh()
                is HomeEvent.RetrySeed -> runSeed()
                is HomeEvent.ToggleWatchlist -> onToggleWatchlist(event.item)
                is HomeEvent.RemoveContinue -> onRemoveContinue(event.metaId)
            }
        }

        private fun onToggleWatchlist(item: MetaPreview) {
            viewModelScope.launch {
                libraryRepository.toggleWatchlist(item)
            }
        }

        private fun onRemoveContinue(metaId: String) {
            viewModelScope.launch {
                progressRepository.removeFromContinueWatching(metaId)
            }
        }

        private fun observeWatchlist() {
            viewModelScope.launch {
                libraryRepository.observeWatchlistIds().collectLatest { ids ->
                    _uiState.update { it.copy(watchlistIds = ids) }
                }
            }
        }

        private fun observeContinueWatching() {
            viewModelScope.launch {
                progressRepository.observeContinueWatching().collectLatest { items ->
                    _uiState.update { it.copy(continueWatching = items) }
                }
            }
        }

        private fun observeAddons() {
            viewModelScope.launch {
                combine(
                    addonRepository.observeAll(),
                    addonRepository.observeEnabled(),
                ) { all, enabled ->
                    Pair(all, enabled)
                }.collectLatest { (all, enabled) ->
                    hasAddons = all.isNotEmpty()
                    val specs = withContext(dispatcher) { buildHomeRows(enabled) }
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
            val networkFailures =
                orderedRows.count {
                    it.status is RowStatus.Failed && it.status.error is AppError.Network
                }
            val anyLoading = orderedRows.any { it.status is RowStatus.Loading }
            val offline = networkFailures >= 2 && !anyLoading
            val hero = buildHero(orderedRows.map { it.status.toRowState() })
            val isFirstRowLoading = orderedRows.isNotEmpty() && orderedRows.first().status is RowStatus.Loading

            _uiState.update {
                it.copy(
                    rows = orderedRows,
                    hero = hero,
                    isFirstRowLoading = isFirstRowLoading,
                    hasAddons = hasAddons,
                    hasCatalogRows = currentSpecs.isNotEmpty(),
                    seedFailed = seedFailed,
                    offline = offline,
                    isRefreshing = isRefreshing,
                )
            }
        }
    }
