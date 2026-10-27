package com.nexus.stream.presentation.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexus.stream.di.IoDispatcher
import com.nexus.stream.domain.model.AppError
import com.nexus.stream.domain.model.EpisodeLabel
import com.nexus.stream.domain.model.HISTORY_ROW_LIMIT
import com.nexus.stream.domain.model.HistoryBucket
import com.nexus.stream.domain.model.HistoryEntry
import com.nexus.stream.domain.model.ListStatus
import com.nexus.stream.domain.model.PosterMark
import com.nexus.stream.domain.model.WatchlistEntry
import com.nexus.stream.domain.model.WatchlistSort
import com.nexus.stream.domain.repository.ContentPrefsRepository
import com.nexus.stream.domain.repository.HistoryRepository
import com.nexus.stream.domain.repository.ProgressRepository
import com.nexus.stream.domain.repository.WatchlistRepository
import com.nexus.stream.domain.usecase.availableTypes
import com.nexus.stream.domain.usecase.buildPosterMarks
import com.nexus.stream.domain.usecase.episodeLabelOf
import com.nexus.stream.domain.usecase.facetCounts
import com.nexus.stream.domain.usecase.filterFamily
import com.nexus.stream.domain.usecase.filterHistoryByType
import com.nexus.stream.domain.usecase.filterHistoryFamily
import com.nexus.stream.domain.usecase.filterWatchlist
import com.nexus.stream.domain.usecase.groupHistory
import com.nexus.stream.domain.usecase.resolveTypeFilter
import com.nexus.stream.domain.usecase.sortWatchlist
import com.nexus.stream.presentation.feature.streams.DropdownOption
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject

enum class LibraryTab {
    WATCHLIST,
    HISTORY,
}

sealed interface LibraryPhase {
    data object Loading : LibraryPhase

    data object Empty : LibraryPhase

    data object FilteredEmpty : LibraryPhase

    data object Content : LibraryPhase

    data class Error(
        val error: AppError,
    ) : LibraryPhase
}

data class WatchlistCardUi(
    val metaId: String,
    val type: String,
    val name: String,
    val poster: String?,
    val status: ListStatus,
    val mark: PosterMark? = null,
    val showStatusLabel: Boolean = false,
    val isUnknownTitle: Boolean = false,
)

data class WatchlistUi(
    val phase: LibraryPhase = LibraryPhase.Loading,
    val cards: List<WatchlistCardUi> = emptyList(),
    val status: ListStatus? = null,
    val type: String? = null,
    val sort: WatchlistSort = WatchlistSort.RECENTLY_ADDED,
    val statusOptions: List<DropdownOption<ListStatus?>> = emptyList(),
    val typeOptions: List<DropdownOption<String?>> = emptyList(),
    val posterMarks: Map<String, PosterMark> = emptyMap(),
)

data class HistoryCardUi(
    val entry: HistoryEntry,
    val episodeLabel: EpisodeLabel? = null,
    val isUnknownTitle: Boolean = false,
) {
    val metaId: String get() = entry.metaId
    val type: String get() = entry.type
    val name: String? get() = entry.name
    val poster: String? get() = entry.poster
    val progress: Float? get() = entry.progress
    val watched: Boolean get() = entry.watched
    val season: Int? get() = entry.season
    val episode: Int? get() = entry.episode
    val videoId: String get() = entry.videoId
    val updatedAt: Long get() = entry.updatedAt
}

data class HistorySectionUi(
    val bucket: HistoryBucket,
    val items: List<HistoryCardUi>,
)

data class HistoryUi(
    val phase: LibraryPhase = LibraryPhase.Loading,
    val sections: List<HistorySectionUi> = emptyList(),
    val type: String? = null,
    val typeOptions: List<DropdownOption<String?>> = emptyList(),
    val truncated: Boolean = false,
)

data class LibraryUiState(
    val tab: LibraryTab = LibraryTab.WATCHLIST,
    val watchlist: WatchlistUi = WatchlistUi(),
    val history: HistoryUi = HistoryUi(),
    val pendingRemove: HistoryEntry? = null,
    val pendingClear: Boolean = false,
)

sealed interface LibraryEvent {
    data class SelectTab(
        val tab: LibraryTab,
    ) : LibraryEvent

    data class SelectStatus(
        val status: ListStatus?,
    ) : LibraryEvent

    data class SelectWatchlistType(
        val type: String?,
    ) : LibraryEvent

    data class SelectSort(
        val sort: WatchlistSort,
    ) : LibraryEvent

    data class SelectHistoryType(
        val type: String?,
    ) : LibraryEvent

    data object ClearWatchlistFilters : LibraryEvent

    data object ClearHistoryFilters : LibraryEvent

    data class RequestRemoveFromHistory(
        val metaId: String,
    ) : LibraryEvent

    data object ConfirmRemoveFromHistory : LibraryEvent

    data object DismissRemove : LibraryEvent

    data object RequestClear : LibraryEvent

    data object ConfirmClear : LibraryEvent

    data object DismissClear : LibraryEvent

    data object Retry : LibraryEvent
}

sealed interface LibraryEffect {
    data object HistoryRemoved : LibraryEffect

    data object HistoryCleared : LibraryEffect
}

private sealed interface ResultOutcome<out T> {
    data class Success<T>(
        val data: T,
    ) : ResultOutcome<T>

    data class Error(
        val error: AppError,
    ) : ResultOutcome<Nothing>
}

private data class WatchlistFilters(
    val status: ListStatus?,
    val type: String?,
    val sort: WatchlistSort,
)

private const val SUBSCRIPTION_TIMEOUT_MS = 5_000L

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LibraryViewModel
    @Inject
    constructor(
        watchlistRepository: WatchlistRepository,
        private val historyRepository: HistoryRepository,
        contentPrefsRepository: ContentPrefsRepository,
        progressRepository: ProgressRepository,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
        private val clock: Clock,
    ) : ViewModel() {
        private val selectedTab = MutableStateFlow(LibraryTab.WATCHLIST)
        private val watchlistStatus = MutableStateFlow<ListStatus?>(null)
        private val watchlistType = MutableStateFlow<String?>(null)
        private val watchlistSort = MutableStateFlow(WatchlistSort.RECENTLY_ADDED)
        private val historyType = MutableStateFlow<String?>(null)
        private val pendingRemove = MutableStateFlow<HistoryEntry?>(null)
        private val pendingClear = MutableStateFlow(false)
        private val watchlistRetryTrigger = MutableStateFlow(0)
        private val historyRetryTrigger = MutableStateFlow(0)

        private val _effects = MutableSharedFlow<LibraryEffect>(extraBufferCapacity = 64)
        val effects: Flow<LibraryEffect> = _effects.asSharedFlow()

        private var currentHistoryEntries: List<HistoryEntry> = emptyList()

        private val watchlistFiltersState: Flow<WatchlistFilters> =
            combine(watchlistStatus, watchlistType, watchlistSort) { status, type, sort ->
                WatchlistFilters(status, type, sort)
            }

        private val watchlistUiState: Flow<WatchlistUi> =
            watchlistRetryTrigger
                .flatMapLatest { _ ->
                    combine(
                        watchlistRepository
                            .observeWatchlist()
                            .map<List<WatchlistEntry>, ResultOutcome<List<WatchlistEntry>>> { entries ->
                                ResultOutcome.Success(entries)
                            }.catch { emit(ResultOutcome.Error(AppError.Unknown())) },
                        contentPrefsRepository.familyMode,
                        progressRepository.observeWatchedMovieIds(),
                        watchlistFiltersState,
                    ) { result, familyMode, watchedMovieIds, filters ->
                        buildWatchlistUi(
                            result = result,
                            familyMode = familyMode,
                            watchedMovieIds = watchedMovieIds,
                            filters = filters,
                        )
                    }
                }.onStart { emit(WatchlistUi(phase = LibraryPhase.Loading)) }

        private val historyUiState: Flow<HistoryUi> =
            historyRetryTrigger
                .flatMapLatest { _ ->
                    combine(
                        historyRepository
                            .observeHistory(HISTORY_ROW_LIMIT)
                            .map<List<HistoryEntry>, ResultOutcome<List<HistoryEntry>>> { entries ->
                                ResultOutcome.Success(entries)
                            }.catch { emit(ResultOutcome.Error(AppError.Unknown())) },
                        contentPrefsRepository.familyMode,
                        historyType,
                    ) { result, familyMode, typeFilter ->
                        buildHistoryUi(result, familyMode, typeFilter)
                    }
                }.onStart { emit(HistoryUi(phase = LibraryPhase.Loading)) }

        val uiState: StateFlow<LibraryUiState> =
            combine(
                selectedTab,
                watchlistUiState,
                historyUiState,
                pendingRemove,
                pendingClear,
            ) { tab, watchlist, history, remove, clear ->
                LibraryUiState(
                    tab = tab,
                    watchlist = watchlist,
                    history = history,
                    pendingRemove = remove,
                    pendingClear = clear,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
                initialValue = LibraryUiState(),
            )

        fun onEvent(event: LibraryEvent) {
            when (event) {
                is LibraryEvent.SelectTab -> {
                    selectedTab.value = event.tab
                }

                is LibraryEvent.SelectStatus,
                is LibraryEvent.SelectWatchlistType,
                is LibraryEvent.SelectSort,
                is LibraryEvent.SelectHistoryType,
                LibraryEvent.ClearWatchlistFilters,
                LibraryEvent.ClearHistoryFilters,
                -> {
                    handleFilterEvent(event)
                }

                is LibraryEvent.RequestRemoveFromHistory -> {
                    handleRequestRemove(event.metaId)
                }

                LibraryEvent.ConfirmRemoveFromHistory -> {
                    handleConfirmRemove()
                }

                LibraryEvent.DismissRemove -> {
                    pendingRemove.value = null
                }

                LibraryEvent.RequestClear -> {
                    pendingClear.value = true
                }

                LibraryEvent.ConfirmClear -> {
                    handleConfirmClear()
                }

                LibraryEvent.DismissClear -> {
                    pendingClear.value = false
                }

                LibraryEvent.Retry -> {
                    watchlistRetryTrigger.update { it + 1 }
                    historyRetryTrigger.update { it + 1 }
                }
            }
        }

        private fun handleFilterEvent(event: LibraryEvent) {
            when (event) {
                is LibraryEvent.SelectStatus -> {
                    watchlistStatus.value = event.status
                }

                is LibraryEvent.SelectWatchlistType -> {
                    watchlistType.value = event.type
                }

                is LibraryEvent.SelectSort -> {
                    watchlistSort.value = event.sort
                }

                is LibraryEvent.SelectHistoryType -> {
                    historyType.value = event.type
                }

                LibraryEvent.ClearWatchlistFilters -> {
                    watchlistStatus.value = null
                    watchlistType.value = null
                }

                LibraryEvent.ClearHistoryFilters -> {
                    historyType.value = null
                }

                else -> {}
            }
        }

        private fun handleRequestRemove(metaId: String) {
            val entry =
                currentHistoryEntries.find { it.metaId == metaId }
                    ?: HistoryEntry(
                        metaId = metaId,
                        type = "movie",
                        name = null,
                        poster = null,
                        season = null,
                        episode = null,
                        videoId = metaId,
                        progress = null,
                        watched = false,
                        updatedAt = 0L,
                    )
            pendingRemove.value = entry
        }

        private fun handleConfirmRemove() {
            val entry = pendingRemove.value ?: return
            pendingRemove.value = null
            viewModelScope.launch(ioDispatcher) {
                historyRepository.remove(entry.metaId)
                _effects.emit(LibraryEffect.HistoryRemoved)
            }
        }

        private fun handleConfirmClear() {
            pendingClear.value = false
            viewModelScope.launch(ioDispatcher) {
                historyRepository.clear()
                _effects.emit(LibraryEffect.HistoryCleared)
            }
        }

        private fun buildStatusOptions(
            totalCount: Int,
            selectedStatus: ListStatus?,
            counts: com.nexus.stream.domain.model.FacetCounts,
        ): List<DropdownOption<ListStatus?>> =
            listOf(
                DropdownOption<ListStatus?>(
                    value = null,
                    label = "All statuses",
                    count = totalCount,
                    selected = (selectedStatus == null),
                ),
            ) +
                ListStatus.entries.map { status ->
                    DropdownOption<ListStatus?>(
                        value = status,
                        label = status.name,
                        count = counts.byStatus[status] ?: 0,
                        selected = (selectedStatus == status),
                    )
                }

        private fun buildTypeOptions(
            totalCount: Int,
            resolvedType: String?,
            available: List<String>,
            counts: com.nexus.stream.domain.model.FacetCounts,
        ): List<DropdownOption<String?>> =
            listOf(
                DropdownOption<String?>(
                    value = null,
                    label = "All types",
                    count = totalCount,
                    selected = (resolvedType == null),
                ),
            ) +
                available.map { t ->
                    DropdownOption<String?>(
                        value = t,
                        label = t,
                        count = counts.byType[t] ?: 0,
                        selected = (resolvedType == t),
                    )
                }

        private fun buildWatchlistUi(
            result: ResultOutcome<List<WatchlistEntry>>,
            familyMode: Boolean,
            watchedMovieIds: Set<String>,
            filters: WatchlistFilters,
        ): WatchlistUi =
            when (result) {
                is ResultOutcome.Error -> {
                    WatchlistUi(phase = LibraryPhase.Error(result.error))
                }

                is ResultOutcome.Success -> {
                    val familyFiltered = filterFamily(result.data, familyMode)
                    if (familyFiltered.isEmpty()) {
                        WatchlistUi(phase = LibraryPhase.Empty)
                    } else {
                        val available = availableTypes(familyFiltered.map { it.type })
                        val resolvedType = resolveTypeFilter(filters.type, available)
                        if (filters.type != resolvedType) {
                            watchlistType.value = resolvedType
                        }
                        val counts =
                            facetCounts(
                                entries = familyFiltered,
                                statusFilter = filters.status,
                                typeFilter = resolvedType,
                            )
                        val filtered = filterWatchlist(familyFiltered, status = filters.status, type = resolvedType)
                        val sorted = sortWatchlist(filtered, filters.sort)
                        val phase = if (sorted.isEmpty()) LibraryPhase.FilteredEmpty else LibraryPhase.Content

                        val statusMap = familyFiltered.associate { it.metaId to it.status }
                        val marks = buildPosterMarks(statusMap, watchedMovieIds)
                        val cards =
                            sorted.map { entry ->
                                WatchlistCardUi(
                                    metaId = entry.metaId,
                                    type = entry.type,
                                    name = entry.name,
                                    poster = entry.poster,
                                    status = entry.status,
                                    mark = marks[entry.metaId],
                                    showStatusLabel = (filters.status == null),
                                    isUnknownTitle = entry.name.isBlank(),
                                )
                            }

                        val totalStatusCount =
                            familyFiltered.count {
                                resolvedType == null || it.type.equals(resolvedType, ignoreCase = true)
                            }
                        val totalTypeCount =
                            familyFiltered.count { filters.status == null || it.status == filters.status }

                        WatchlistUi(
                            phase = phase,
                            cards = cards,
                            status = filters.status,
                            type = resolvedType,
                            sort = filters.sort,
                            statusOptions = buildStatusOptions(totalStatusCount, filters.status, counts),
                            typeOptions = buildTypeOptions(totalTypeCount, resolvedType, available, counts),
                            posterMarks = marks,
                        )
                    }
                }
            }

        private fun buildHistoryUi(
            result: ResultOutcome<List<HistoryEntry>>,
            familyMode: Boolean,
            typeFilter: String?,
        ): HistoryUi =
            when (result) {
                is ResultOutcome.Error -> {
                    HistoryUi(phase = LibraryPhase.Error(result.error))
                }

                is ResultOutcome.Success -> {
                    currentHistoryEntries = result.data
                    val truncated = result.data.size >= HISTORY_ROW_LIMIT
                    val familyFiltered = filterHistoryFamily(result.data, familyMode)
                    if (familyFiltered.isEmpty()) {
                        HistoryUi(phase = LibraryPhase.Empty, truncated = truncated)
                    } else {
                        val available = availableTypes(familyFiltered.map { it.type })
                        val resolvedType = resolveTypeFilter(typeFilter, available)
                        if (typeFilter != resolvedType) {
                            historyType.value = resolvedType
                        }
                        val filtered = filterHistoryByType(familyFiltered, resolvedType)
                        val phase = if (filtered.isEmpty()) LibraryPhase.FilteredEmpty else LibraryPhase.Content

                        val nowMs = clock.millis()
                        val grouped = groupHistory(filtered, nowMs, clock.zone)
                        val sections =
                            grouped.map { (bucket, entries) ->
                                val items =
                                    entries.map { entry ->
                                        HistoryCardUi(
                                            entry = entry,
                                            episodeLabel = episodeLabelOf(entry.season, entry.episode),
                                            isUnknownTitle = entry.name.isNullOrBlank(),
                                        )
                                    }
                                HistorySectionUi(bucket = bucket, items = items)
                            }

                        val typeOptions =
                            listOf(
                                DropdownOption<String?>(
                                    value = null,
                                    label = "All types",
                                    count = familyFiltered.size,
                                    selected = (resolvedType == null),
                                ),
                            ) +
                                available.map { t ->
                                    DropdownOption<String?>(
                                        value = t,
                                        label = t,
                                        count = familyFiltered.count { it.type.equals(t, ignoreCase = true) },
                                        selected = (resolvedType == t),
                                    )
                                }

                        HistoryUi(
                            phase = phase,
                            sections = sections,
                            type = resolvedType,
                            typeOptions = typeOptions,
                            truncated = truncated,
                        )
                    }
                }
            }
    }
