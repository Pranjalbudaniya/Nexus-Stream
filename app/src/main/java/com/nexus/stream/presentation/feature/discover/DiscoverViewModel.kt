package com.nexus.stream.presentation.feature.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexus.stream.data.remote.addon.DiscoverPageLoader
import com.nexus.stream.di.DefaultDispatcher
import com.nexus.stream.domain.model.Addon
import com.nexus.stream.domain.model.AppError
import com.nexus.stream.domain.model.DiscoverCatalog
import com.nexus.stream.domain.model.DiscoverSelection
import com.nexus.stream.domain.model.GenreKind
import com.nexus.stream.domain.model.ListStatus
import com.nexus.stream.domain.model.MetaPreview
import com.nexus.stream.domain.model.Outcome
import com.nexus.stream.domain.model.PosterMark
import com.nexus.stream.domain.model.PosterShape
import com.nexus.stream.domain.repository.AddonRepository
import com.nexus.stream.domain.repository.ContentPrefsRepository
import com.nexus.stream.domain.repository.LibraryRepository
import com.nexus.stream.domain.repository.ProgressRepository
import com.nexus.stream.domain.usecase.PagingState
import com.nexus.stream.domain.usecase.applyPage
import com.nexus.stream.domain.usecase.buildDiscoverCatalogs
import com.nexus.stream.domain.usecase.buildPosterMarks
import com.nexus.stream.domain.usecase.discoverExtra
import com.nexus.stream.domain.usecase.discoverTypes
import com.nexus.stream.domain.usecase.genreKind
import com.nexus.stream.domain.usecase.gridShapeOf
import com.nexus.stream.domain.usecase.isAdultContent
import com.nexus.stream.domain.usecase.nextSkip
import com.nexus.stream.domain.usecase.resolveDiscoverSelection
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed interface DiscoverPhase {
    data object NoAddons : DiscoverPhase

    data object NoCatalogs : DiscoverPhase

    data object Loading : DiscoverPhase

    data object Content : DiscoverPhase

    data object Empty : DiscoverPhase

    data class Error(
        val error: AppError,
    ) : DiscoverPhase
}

sealed interface DiscoverFooter {
    data object None : DiscoverFooter

    data object Loading : DiscoverFooter

    data class Error(
        val error: AppError,
    ) : DiscoverFooter
}

data class DiscoverGenreUi(
    val options: List<String>,
    val selectedGenre: String?,
    val isRequired: Boolean,
    val kind: GenreKind,
)

data class DiscoverUiState(
    val phase: DiscoverPhase = DiscoverPhase.Loading,
    val types: List<String> = emptyList(),
    val selectedType: String? = null,
    val catalogs: List<DiscoverCatalog> = emptyList(),
    val selectedCatalogKey: String? = null,
    val genreUi: DiscoverGenreUi? = null,
    val items: List<MetaPreview> = emptyList(),
    val gridShape: PosterShape = PosterShape.POSTER,
    val footer: DiscoverFooter = DiscoverFooter.None,
    val isRefreshing: Boolean = false,
    val posterMarks: Map<String, PosterMark> = emptyMap(),
)

sealed interface DiscoverEvent {
    data class SelectType(
        val type: String,
    ) : DiscoverEvent

    data class SelectCatalog(
        val catalogKey: String,
    ) : DiscoverEvent

    data class SelectGenre(
        val genre: String?,
    ) : DiscoverEvent

    data object LoadMore : DiscoverEvent

    data object Retry : DiscoverEvent

    data object Refresh : DiscoverEvent
}

sealed interface DiscoverEffect {
    data object RefreshFailed : DiscoverEffect
}

@HiltViewModel
class DiscoverViewModel
    @Inject
    constructor(
        private val addonRepository: AddonRepository,
        private val contentPrefsRepository: ContentPrefsRepository,
        private val libraryRepository: LibraryRepository,
        private val progressRepository: ProgressRepository,
        private val loader: DiscoverPageLoader,
        @DefaultDispatcher private val dispatcher: CoroutineDispatcher,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(DiscoverUiState())
        val uiState: StateFlow<DiscoverUiState> = _uiState.asStateFlow()

        private val _effects = MutableSharedFlow<DiscoverEffect>()
        val effects: SharedFlow<DiscoverEffect> = _effects.asSharedFlow()

        private var catalogs: List<DiscoverCatalog> = emptyList()
        private var selection: DiscoverSelection? = null
        private val lastCatalogByType = mutableMapOf<String, String>()
        private var rawPaging = PagingState(items = emptyList(), pagesLoaded = 0, hasMore = true)
        private var isFamilyMode = false
        private var listStatusesMap = emptyMap<String, ListStatus>()
        private var watchedMovieIdsSet = emptySet<String>()

        private var currentLoadJob: Job? = null
        private var currentRequestId = 0L
        private var isLoadingMore = false

        init {
            startObserving()
        }

        fun onEvent(event: DiscoverEvent) {
            when (event) {
                is DiscoverEvent.SelectType,
                is DiscoverEvent.SelectCatalog,
                is DiscoverEvent.SelectGenre,
                -> {
                    applySelection(handleUserSelection(selection, catalogs, lastCatalogByType, event))
                }

                is DiscoverEvent.Retry -> {
                    if (selection != null) {
                        if (_uiState.value.footer is DiscoverFooter.Error) {
                            handleLoadMore()
                        } else if (_uiState.value.phase is DiscoverPhase.Error) {
                            loadPage1()
                        }
                    }
                }

                is DiscoverEvent.LoadMore -> {
                    handleLoadMore()
                }

                is DiscoverEvent.Refresh -> {
                    handleRefresh()
                }
            }
        }

        private fun startObserving() {
            viewModelScope.launch {
                combine(
                    addonRepository.observeAll(),
                    addonRepository.observeActive(),
                ) { all, active -> all to active }
                    .collectLatest { (all, active) ->
                        processAddonsUpdate(all, active)
                    }
            }
            viewModelScope.launch {
                contentPrefsRepository.familyMode.collectLatest { enabled ->
                    isFamilyMode = enabled
                    recomputeVisibleItems()
                }
            }
            viewModelScope.launch {
                combine(
                    libraryRepository.observeListStatuses(),
                    progressRepository.observeWatchedMovieIds(),
                ) { statuses, watchedIds -> statuses to watchedIds }
                    .collectLatest { (statuses, watchedIds) ->
                        listStatusesMap = statuses
                        watchedMovieIdsSet = watchedIds
                        val marks = buildPosterMarks(statuses, watchedIds)
                        _uiState.update { it.copy(posterMarks = marks) }
                    }
            }
        }

        private suspend fun processAddonsUpdate(
            all: List<Addon>,
            active: List<Addon>,
        ) {
            if (all.isEmpty()) {
                currentLoadJob?.cancel()
                catalogs = emptyList()
                selection = null
                rawPaging = PagingState(emptyList(), 0, false)
                _uiState.update {
                    it.copy(
                        phase = DiscoverPhase.NoAddons,
                        types = emptyList(),
                        selectedType = null,
                        catalogs = emptyList(),
                        selectedCatalogKey = null,
                        genreUi = null,
                        items = emptyList(),
                    )
                }
                return
            }

            catalogs = withContext(dispatcher) { buildDiscoverCatalogs(active) }
            if (catalogs.isEmpty()) {
                currentLoadJob?.cancel()
                selection = null
                rawPaging = PagingState(emptyList(), 0, false)
                _uiState.update {
                    it.copy(
                        phase = DiscoverPhase.NoCatalogs,
                        types = emptyList(),
                        selectedType = null,
                        catalogs = emptyList(),
                        selectedCatalogKey = null,
                        genreUi = null,
                        items = emptyList(),
                    )
                }
                return
            }

            val newSelection = resolveDiscoverSelection(catalogs, selection, lastCatalogByType)
            if (newSelection != selection) {
                selection = newSelection
                newSelection?.let { lastCatalogByType[it.type] = it.catalogKey }
                loadPage1()
            } else {
                updateHeaderUi()
            }
        }

        private fun applySelection(newSelection: DiscoverSelection?) {
            if (newSelection != null) {
                selection = newSelection
                lastCatalogByType[newSelection.type] = newSelection.catalogKey
                loadPage1()
            }
        }

        private fun handleLoadMore() {
            val currentSel = selection
            val catalog = catalogs.find { it.key == currentSel?.catalogKey }
            val idle = !isLoadingMore && !_uiState.value.isRefreshing
            if (currentSel == null || catalog == null ||
                !isLoadMoreReady(_uiState.value.phase, rawPaging.hasMore, idle)
            ) {
                return
            }

            isLoadingMore = true
            _uiState.update { it.copy(footer = DiscoverFooter.Loading) }
            val reqId = currentRequestId

            viewModelScope.launch(dispatcher) {
                val skip = nextSkip(rawPaging)
                val outcome = loader.load(catalog, discoverExtra(catalog, currentSel.genre, skip))
                if (reqId != currentRequestId) return@launch
                isLoadingMore = false

                when (outcome) {
                    is Outcome.Failure -> {
                        _uiState.update { it.copy(footer = DiscoverFooter.Error(outcome.error)) }
                    }

                    is Outcome.Success -> {
                        rawPaging = applyPage(rawPaging, outcome.value, catalog)
                        val visible = rawPaging.items.filter { !isFamilyMode || !it.isAdultContent() }
                        _uiState.update {
                            it.copy(
                                items = visible,
                                footer = DiscoverFooter.None,
                            )
                        }
                    }
                }
            }
        }

        private fun handleRefresh() {
            val currentSel = selection
            val catalog = catalogs.find { it.key == currentSel?.catalogKey }
            if (currentSel == null || catalog == null || _uiState.value.isRefreshing) {
                return
            }

            _uiState.update { it.copy(isRefreshing = true) }
            val reqId = currentRequestId

            viewModelScope.launch(dispatcher) {
                val outcome = loader.load(catalog, discoverExtra(catalog, currentSel.genre, 0))
                if (reqId != currentRequestId) return@launch

                when (outcome) {
                    is Outcome.Failure -> {
                        _uiState.update { it.copy(isRefreshing = false) }
                        _effects.emit(DiscoverEffect.RefreshFailed)
                    }

                    is Outcome.Success -> {
                        val finalPaging = pageUntilVisible(catalog, currentSel.genre, outcome.value, reqId)
                        if (reqId != currentRequestId) return@launch
                        rawPaging = finalPaging
                        val visible = finalPaging.items.filter { !isFamilyMode || !it.isAdultContent() }
                        val phase = if (visible.isEmpty()) DiscoverPhase.Empty else DiscoverPhase.Content
                        _uiState.update {
                            it.copy(
                                phase = phase,
                                items = visible,
                                gridShape = gridShapeOf(finalPaging.items),
                                footer = DiscoverFooter.None,
                                isRefreshing = false,
                            )
                        }
                    }
                }
            }
        }

        private fun loadPage1() {
            val currentSel = selection ?: return
            val catalog = catalogs.find { it.key == currentSel.catalogKey } ?: return

            currentLoadJob?.cancel()
            rawPaging = PagingState(items = emptyList(), pagesLoaded = 0, hasMore = true)
            updateHeaderUi(phase = DiscoverPhase.Loading, items = emptyList())

            val reqId = ++currentRequestId
            currentLoadJob =
                viewModelScope.launch(dispatcher) {
                    val outcome = loader.load(catalog, discoverExtra(catalog, currentSel.genre, 0))
                    if (reqId != currentRequestId) return@launch

                    when (outcome) {
                        is Outcome.Failure -> {
                            _uiState.update { it.copy(phase = DiscoverPhase.Error(outcome.error)) }
                        }

                        is Outcome.Success -> {
                            val finalPaging = pageUntilVisible(catalog, currentSel.genre, outcome.value, reqId)
                            if (reqId != currentRequestId) return@launch
                            rawPaging = finalPaging
                            val visible = finalPaging.items.filter { !isFamilyMode || !it.isAdultContent() }
                            val phase = if (visible.isEmpty()) DiscoverPhase.Empty else DiscoverPhase.Content
                            _uiState.update {
                                it.copy(
                                    phase = phase,
                                    items = visible,
                                    gridShape = gridShapeOf(finalPaging.items),
                                    footer = DiscoverFooter.None,
                                )
                            }
                        }
                    }
                }
        }

        private suspend fun pageUntilVisible(
            catalog: DiscoverCatalog,
            genre: String?,
            firstPageItems: List<MetaPreview>,
            reqId: Long,
        ): PagingState {
            var state = applyPage(PagingState(emptyList(), 0, true), firstPageItems, catalog)
            var visible = state.items.filter { !isFamilyMode || !it.isAdultContent() }

            while (visible.isEmpty() && state.hasMore) {
                val skip = nextSkip(state)
                val outcome = loader.load(catalog, discoverExtra(catalog, genre, skip))
                if (reqId != currentRequestId || outcome !is Outcome.Success) break
                state = applyPage(state, outcome.value, catalog)
                visible = state.items.filter { !isFamilyMode || !it.isAdultContent() }
            }
            return state
        }

        private fun recomputeVisibleItems() {
            val visible = rawPaging.items.filter { !isFamilyMode || !it.isAdultContent() }
            _uiState.update { current ->
                val newPhase =
                    when (current.phase) {
                        is DiscoverPhase.Content, is DiscoverPhase.Empty -> {
                            if (visible.isEmpty()) DiscoverPhase.Empty else DiscoverPhase.Content
                        }

                        else -> {
                            current.phase
                        }
                    }
                current.copy(items = visible, phase = newPhase)
            }
        }

        private fun updateHeaderUi(
            phase: DiscoverPhase? = null,
            items: List<MetaPreview>? = null,
        ) {
            val currentSel = selection
            val types = discoverTypes(catalogs)
            val selectedType = currentSel?.type
            val catalogsForType = catalogs.filter { it.type == selectedType }
            val currentCatalog = catalogsForType.find { it.key == currentSel?.catalogKey }

            val genreUi =
                currentCatalog?.genre?.let { genreSpec ->
                    DiscoverGenreUi(
                        options = genreSpec.options,
                        selectedGenre = currentSel?.genre,
                        isRequired = genreSpec.required,
                        kind = genreKind(genreSpec.options),
                    )
                }

            _uiState.update { current ->
                current.copy(
                    phase = phase ?: current.phase,
                    types = types,
                    selectedType = selectedType,
                    catalogs = catalogsForType,
                    selectedCatalogKey = currentSel?.catalogKey,
                    genreUi = genreUi,
                    items = items ?: current.items,
                )
            }
        }
    }

private fun isLoadMoreReady(
    phase: DiscoverPhase,
    hasMore: Boolean,
    idle: Boolean,
): Boolean = phase is DiscoverPhase.Content && hasMore && idle

private fun handleUserSelection(
    current: DiscoverSelection?,
    catalogs: List<DiscoverCatalog>,
    lastCatalogByType: MutableMap<String, String>,
    event: DiscoverEvent,
): DiscoverSelection? =
    when (event) {
        is DiscoverEvent.SelectType -> {
            if (current != null && event.type != current.type) {
                lastCatalogByType[current.type] = current.catalogKey
                val wanted = DiscoverSelection(type = event.type, catalogKey = "", genre = null)
                resolveDiscoverSelection(catalogs, wanted, lastCatalogByType)
            } else {
                null
            }
        }

        is DiscoverEvent.SelectCatalog -> {
            if (current != null && event.catalogKey != current.catalogKey) {
                lastCatalogByType[current.type] = event.catalogKey
                val wanted = DiscoverSelection(type = current.type, catalogKey = event.catalogKey, genre = null)
                resolveDiscoverSelection(catalogs, wanted, lastCatalogByType)
            } else {
                null
            }
        }

        is DiscoverEvent.SelectGenre -> {
            if (current != null && event.genre != current.genre) {
                val wanted =
                    DiscoverSelection(
                        type = current.type,
                        catalogKey = current.catalogKey,
                        genre = event.genre,
                    )
                resolveDiscoverSelection(catalogs, wanted, lastCatalogByType)
            } else {
                null
            }
        }

        else -> {
            null
        }
    }
