package com.nexus.stream.presentation.feature.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexus.stream.data.remote.addon.DiscoverPageLoader
import com.nexus.stream.di.IoDispatcher
import com.nexus.stream.domain.model.AppError
import com.nexus.stream.domain.model.DiscoverCatalog
import com.nexus.stream.domain.model.MetaPreview
import com.nexus.stream.domain.model.Outcome
import com.nexus.stream.domain.model.PosterMark
import com.nexus.stream.domain.repository.AddonRepository
import com.nexus.stream.domain.repository.ContentPrefsRepository
import com.nexus.stream.domain.repository.LibraryRepository
import com.nexus.stream.domain.repository.ProgressRepository
import com.nexus.stream.domain.repository.SearchHistoryRepository
import com.nexus.stream.domain.usecase.SEARCH_DEBOUNCE_MS
import com.nexus.stream.domain.usecase.SEARCH_MAX_PARALLEL
import com.nexus.stream.domain.usecase.SEARCH_ROW_LIMIT
import com.nexus.stream.domain.usecase.buildPosterMarks
import com.nexus.stream.domain.usecase.buildSearchCatalogs
import com.nexus.stream.domain.usecase.isAdultContent
import com.nexus.stream.domain.usecase.isSearchable
import com.nexus.stream.domain.usecase.normalizeSearchQuery
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed interface SearchPhase {
    data object Idle : SearchPhase

    data object TooShort : SearchPhase

    data object NoSearchAddons : SearchPhase

    data object Searching : SearchPhase

    data object Results : SearchPhase

    data object NoResults : SearchPhase

    data class Error(
        val error: AppError,
    ) : SearchPhase
}

data class SearchRow(
    val catalog: DiscoverCatalog,
    val items: List<MetaPreview>,
)

data class SearchUiState(
    val rawQuery: String = "",
    val normalizedQuery: String = "",
    val phase: SearchPhase = SearchPhase.Idle,
    val isSearching: Boolean = false,
    val failedCount: Int = 0,
    val rows: List<SearchRow> = emptyList(),
    val history: List<String> = emptyList(),
    val posterMarks: Map<String, PosterMark> = emptyMap(),
)

sealed interface SearchEvent {
    data class QueryChanged(
        val raw: String,
    ) : SearchEvent

    data object Submit : SearchEvent

    data object ClearQuery : SearchEvent

    data class UseRecent(
        val query: String,
    ) : SearchEvent

    data class RemoveRecent(
        val query: String,
    ) : SearchEvent

    data object ClearHistory : SearchEvent

    data object Retry : SearchEvent

    data class ResultOpened(
        val type: String = "",
        val id: String = "",
    ) : SearchEvent
}

@HiltViewModel
@Suppress("LongParameterList")
class SearchViewModel
    @Inject
    constructor(
        private val addonRepository: AddonRepository,
        private val contentPrefsRepository: ContentPrefsRepository,
        private val libraryRepository: LibraryRepository,
        private val progressRepository: ProgressRepository,
        private val searchHistoryRepository: SearchHistoryRepository,
        private val loader: DiscoverPageLoader,
        @IoDispatcher private val dispatcher: CoroutineDispatcher,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(SearchUiState())
        val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

        private var activeSearchJob: Job? = null
        private var debounceJob: Job? = null
        private var currentNormalizedQuery: String = ""
        private var isFamilyMode: Boolean = false
        private var currentCatalogs: List<DiscoverCatalog> = emptyList()
        private var listStatusesMap: Map<String, com.nexus.stream.domain.model.ListStatus> = emptyMap()
        private var watchedMovieIdsSet: Set<String> = emptySet()

        private sealed interface CatalogResult {
            data object Loading : CatalogResult

            data class Success(
                val items: List<MetaPreview>,
            ) : CatalogResult

            data class Failure(
                val error: AppError,
            ) : CatalogResult
        }

        private val catalogResults = mutableMapOf<String, CatalogResult>()

        init {
            observeRepositories()
        }

        private fun observeRepositories() {
            viewModelScope.launch {
                contentPrefsRepository.familyMode.collectLatest { enabled ->
                    isFamilyMode = enabled
                    recomputeUiState()
                }
            }
            viewModelScope.launch {
                libraryRepository.observeListStatuses().collectLatest { statuses ->
                    listStatusesMap = statuses
                    _uiState.update { it.copy(posterMarks = buildPosterMarks(listStatusesMap, watchedMovieIdsSet)) }
                }
            }
            viewModelScope.launch {
                progressRepository.observeWatchedMovieIds().collectLatest { ids ->
                    watchedMovieIdsSet = ids
                    _uiState.update { it.copy(posterMarks = buildPosterMarks(listStatusesMap, watchedMovieIdsSet)) }
                }
            }
            viewModelScope.launch {
                searchHistoryRepository.observeHistory().collectLatest { history ->
                    _uiState.update { it.copy(history = history) }
                }
            }
            viewModelScope.launch {
                addonRepository.observeActive().collectLatest { addons ->
                    val newCatalogs = withContext(dispatcher) { buildSearchCatalogs(addons) }
                    if (newCatalogs != currentCatalogs) {
                        currentCatalogs = newCatalogs
                        if (isSearchable(currentNormalizedQuery)) {
                            executeSearch(currentNormalizedQuery, saveToHistory = false)
                        }
                    }
                }
            }
        }

        fun onEvent(event: SearchEvent) {
            when (event) {
                is SearchEvent.QueryChanged -> handleQueryChanged(event.raw)
                is SearchEvent.Submit -> handleSubmit()
                is SearchEvent.UseRecent -> handleUseRecent(event.query)
                is SearchEvent.ClearQuery -> handleClearQuery()
                is SearchEvent.RemoveRecent -> viewModelScope.launch { searchHistoryRepository.remove(event.query) }
                is SearchEvent.ClearHistory -> viewModelScope.launch { searchHistoryRepository.clear() }
                is SearchEvent.Retry -> handleRetry()
                is SearchEvent.ResultOpened -> handleResultOpened()
            }
        }

        private fun handleQueryChanged(raw: String) {
            val normalized = normalizeSearchQuery(raw)
            _uiState.update { it.copy(rawQuery = raw) }
            if (normalized == currentNormalizedQuery) return

            currentNormalizedQuery = normalized
            debounceJob?.cancel()
            activeSearchJob?.cancel()
            catalogResults.clear()

            when {
                normalized.isEmpty() -> {
                    _uiState.update {
                        it.copy(
                            normalizedQuery = "",
                            phase = SearchPhase.Idle,
                            isSearching = false,
                            failedCount = 0,
                            rows = emptyList(),
                        )
                    }
                }

                !isSearchable(normalized) -> {
                    _uiState.update {
                        it.copy(
                            normalizedQuery = normalized,
                            phase = SearchPhase.TooShort,
                            isSearching = false,
                            failedCount = 0,
                            rows = emptyList(),
                        )
                    }
                }

                else -> {
                    debounceJob =
                        viewModelScope.launch {
                            delay(SEARCH_DEBOUNCE_MS)
                            executeSearch(normalized, saveToHistory = false)
                        }
                }
            }
        }

        private fun handleSubmit() {
            val normalized = normalizeSearchQuery(_uiState.value.rawQuery)
            if (isSearchable(normalized)) {
                debounceJob?.cancel()
                currentNormalizedQuery = normalized
                executeSearch(normalized, saveToHistory = true)
            }
        }

        private fun handleUseRecent(query: String) {
            val normalized = normalizeSearchQuery(query)
            _uiState.update { it.copy(rawQuery = query) }
            if (isSearchable(normalized)) {
                debounceJob?.cancel()
                currentNormalizedQuery = normalized
                executeSearch(normalized, saveToHistory = true)
            }
        }

        private fun handleClearQuery() {
            debounceJob?.cancel()
            activeSearchJob?.cancel()
            currentNormalizedQuery = ""
            catalogResults.clear()
            _uiState.update {
                it.copy(
                    rawQuery = "",
                    normalizedQuery = "",
                    phase = SearchPhase.Idle,
                    isSearching = false,
                    failedCount = 0,
                    rows = emptyList(),
                )
            }
        }

        private fun handleRetry() {
            if (isSearchable(currentNormalizedQuery)) {
                executeSearch(currentNormalizedQuery, saveToHistory = false, onlyFailed = true)
            }
        }

        private fun handleResultOpened() {
            if (isSearchable(currentNormalizedQuery)) {
                viewModelScope.launch { searchHistoryRepository.save(currentNormalizedQuery) }
            }
        }

        private fun executeSearch(
            query: String,
            saveToHistory: Boolean,
            onlyFailed: Boolean = false,
        ) {
            debounceJob?.cancel()
            if (saveToHistory && isSearchable(query)) {
                viewModelScope.launch { searchHistoryRepository.save(query) }
            }

            if (currentCatalogs.isEmpty()) {
                _uiState.update {
                    it.copy(
                        normalizedQuery = query,
                        phase = SearchPhase.NoSearchAddons,
                        isSearching = false,
                        failedCount = 0,
                        rows = emptyList(),
                    )
                }
                return
            }

            val targets =
                if (onlyFailed) {
                    currentCatalogs.filter { catalogResults[it.key] is CatalogResult.Failure }
                } else {
                    activeSearchJob?.cancel()
                    catalogResults.clear()
                    currentCatalogs
                }

            for (catalog in targets) {
                catalogResults[catalog.key] = CatalogResult.Loading
            }
            recomputeUiState()

            val semaphore = Semaphore(SEARCH_MAX_PARALLEL)
            activeSearchJob =
                viewModelScope.launch {
                    for (catalog in targets) {
                        launch {
                            semaphore.withPermit {
                                val outcome =
                                    withContext(dispatcher) {
                                        loader.load(catalog, mapOf("search" to query))
                                    }
                                when (outcome) {
                                    is Outcome.Success -> {
                                        val capped = outcome.value.take(SEARCH_ROW_LIMIT)
                                        catalogResults[catalog.key] = CatalogResult.Success(capped)
                                    }

                                    is Outcome.Failure -> {
                                        catalogResults[catalog.key] = CatalogResult.Failure(outcome.error)
                                    }
                                }
                                recomputeUiState()
                            }
                        }
                    }
                }
        }

        private fun recomputeUiState() {
            if (!isSearchable(currentNormalizedQuery)) return

            if (currentCatalogs.isEmpty()) {
                _uiState.update {
                    it.copy(
                        normalizedQuery = currentNormalizedQuery,
                        phase = SearchPhase.NoSearchAddons,
                        isSearching = false,
                        failedCount = 0,
                        rows = emptyList(),
                    )
                }
                return
            }

            val visibleRows =
                currentCatalogs.mapNotNull { catalog ->
                    extractVisibleRow(catalog, catalogResults[catalog.key])
                }
            val anyLoading =
                currentCatalogs.any {
                    catalogResults[it.key] is CatalogResult.Loading || catalogResults[it.key] == null
                }
            val failures = currentCatalogs.mapNotNull { catalogResults[it.key] as? CatalogResult.Failure }
            val failedCount = failures.size
            val firstError = failures.firstOrNull()?.error

            val phase =
                when {
                    visibleRows.isNotEmpty() -> SearchPhase.Results
                    anyLoading -> SearchPhase.Searching
                    failedCount == currentCatalogs.size && firstError != null -> SearchPhase.Error(firstError)
                    else -> SearchPhase.NoResults
                }

            _uiState.update {
                it.copy(
                    normalizedQuery = currentNormalizedQuery,
                    phase = phase,
                    isSearching = anyLoading,
                    failedCount = failedCount,
                    rows = visibleRows,
                )
            }
        }

        private fun extractVisibleRow(
            catalog: DiscoverCatalog,
            res: CatalogResult?,
        ): SearchRow? {
            if (res !is CatalogResult.Success) return null
            val filtered = if (isFamilyMode) res.items.filter { !it.isAdultContent() } else res.items
            return if (filtered.isNotEmpty()) SearchRow(catalog = catalog, items = filtered) else null
        }
    }
