package com.nexus.stream.presentation.feature.addons

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexus.stream.R
import com.nexus.stream.data.remote.addon.AddonUrlBuilder
import com.nexus.stream.di.DefaultDispatcher
import com.nexus.stream.domain.model.AddonCategory
import com.nexus.stream.domain.model.AppError
import com.nexus.stream.domain.model.Outcome
import com.nexus.stream.domain.model.StoreAddon
import com.nexus.stream.domain.repository.AddonRepository
import com.nexus.stream.domain.repository.CommunityAddonsRepository
import com.nexus.stream.domain.repository.ContentPrefsRepository
import com.nexus.stream.domain.usecase.AddonFilters
import com.nexus.stream.presentation.common.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class AddonStoreUiState(
    val isLoading: Boolean = false,
    val error: AppError? = null,
    val rawAddons: List<StoreAddon> = emptyList(),
    val filteredAddons: List<StoreAddon> = emptyList(),
    val installedUrls: Set<String> = emptySet(),
    val searchQuery: String = "",
    val selectedCategory: AddonCategory? = null,
    val isFamilyMode: Boolean = true,
    val totalCount: Int = 0,
    val categoryCounts: Map<AddonCategory, Int> = emptyMap(),
    val installingUrl: String? = null,
    val pendingHttpAddon: StoreAddon? = null,
)

sealed interface AddonStoreEvent {
    data class SearchQueryChanged(
        val query: String,
    ) : AddonStoreEvent

    data object ClearSearch : AddonStoreEvent

    data class SelectCategory(
        val category: AddonCategory?,
    ) : AddonStoreEvent

    data object Refresh : AddonStoreEvent

    data class Install(
        val addon: StoreAddon,
    ) : AddonStoreEvent

    data object ConfirmHttpInstall : AddonStoreEvent

    data object DismissHttpDialog : AddonStoreEvent
}

sealed interface AddonStoreEffect {
    data class ShowMessage(
        @StringRes val messageRes: Int,
        val formatArgs: List<Any> = emptyList(),
    ) : AddonStoreEffect
}

@HiltViewModel
class AddonStoreViewModel
    @Inject
    constructor(
        private val communityAddonsRepository: CommunityAddonsRepository,
        private val addonRepository: AddonRepository,
        private val contentPrefsRepository: ContentPrefsRepository,
        @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(AddonStoreUiState())
        val uiState: StateFlow<AddonStoreUiState> = _uiState.asStateFlow()

        private val _effects = MutableSharedFlow<AddonStoreEffect>()
        val effects: SharedFlow<AddonStoreEffect> = _effects.asSharedFlow()

        init {
            viewModelScope.launch {
                combine(
                    addonRepository.observeAll(),
                    contentPrefsRepository.familyMode,
                ) { installedList, familyMode ->
                    val installed =
                        installedList
                            .mapNotNull {
                                AddonUrlBuilder.normalize(it.transportUrl)?.toString() ?: it.transportUrl
                            }.toSet()
                    installed to familyMode
                }.collect { (installed, familyMode) ->
                    _uiState.update { it.copy(installedUrls = installed, isFamilyMode = familyMode) }
                    applyFilter()
                }
            }
            fetchAddons(forceRefresh = false)
        }

        fun onEvent(event: AddonStoreEvent) {
            when (event) {
                is AddonStoreEvent.SearchQueryChanged -> {
                    _uiState.update { it.copy(searchQuery = event.query) }
                    applyFilter()
                }

                AddonStoreEvent.ClearSearch -> {
                    _uiState.update { it.copy(searchQuery = "") }
                    applyFilter()
                }

                is AddonStoreEvent.SelectCategory -> {
                    _uiState.update { it.copy(selectedCategory = event.category) }
                    applyFilter()
                }

                AddonStoreEvent.Refresh -> {
                    fetchAddons(forceRefresh = true)
                }

                is AddonStoreEvent.Install -> {
                    if (event.addon.transportUrl.startsWith("http://", ignoreCase = true)) {
                        _uiState.update { it.copy(pendingHttpAddon = event.addon) }
                    } else {
                        performInstall(event.addon)
                    }
                }

                AddonStoreEvent.ConfirmHttpInstall -> {
                    val target = _uiState.value.pendingHttpAddon ?: return
                    _uiState.update { it.copy(pendingHttpAddon = null) }
                    performInstall(target)
                }

                AddonStoreEvent.DismissHttpDialog -> {
                    _uiState.update { it.copy(pendingHttpAddon = null) }
                }
            }
        }

        fun setInitialCategory(category: AddonCategory?) {
            if (_uiState.value.selectedCategory != category) {
                _uiState.update { it.copy(selectedCategory = category) }
                applyFilter()
            }
        }

        private fun fetchAddons(forceRefresh: Boolean) {
            if (_uiState.value.isLoading) return
            _uiState.update { it.copy(isLoading = true, error = null) }
            viewModelScope.launch {
                when (val outcome = communityAddonsRepository.fetch(forceRefresh)) {
                    is Outcome.Success -> {
                        _uiState.update { it.copy(rawAddons = outcome.value, isLoading = false, error = null) }
                        applyFilter()
                    }

                    is Outcome.Failure -> {
                        _uiState.update { it.copy(isLoading = false, error = outcome.error) }
                    }
                }
            }
        }

        private fun applyFilter() {
            viewModelScope.launch {
                val current = _uiState.value
                val (filtered, totalCount, counts) =
                    withContext(defaultDispatcher) {
                        val queryFiltered =
                            AddonFilters.filterStore(
                                entries = current.rawAddons,
                                query = current.searchQuery,
                                category = null,
                                familyMode = current.isFamilyMode,
                                installedUrls = current.installedUrls,
                            )
                        val counts = AddonFilters.storeCategoryCounts(queryFiltered)
                        val filtered =
                            if (current.selectedCategory == null) {
                                queryFiltered
                            } else {
                                queryFiltered.filter { current.selectedCategory in it.categories }
                            }
                        Triple(filtered, queryFiltered.size, counts)
                    }
                _uiState.update {
                    it.copy(
                        filteredAddons = filtered,
                        totalCount = totalCount,
                        categoryCounts = counts,
                    )
                }
            }
        }

        private fun performInstall(addon: StoreAddon) {
            _uiState.update { it.copy(installingUrl = addon.transportUrl) }
            viewModelScope.launch {
                when (val result = addonRepository.install(addon.transportUrl)) {
                    is Outcome.Success -> {
                        _effects.emit(
                            AddonStoreEffect.ShowMessage(
                                R.string.addon_installed_success,
                                listOf(addon.name),
                            ),
                        )
                    }

                    is Outcome.Failure -> {
                        val userMsg = result.error.toUserMessage()
                        _effects.emit(AddonStoreEffect.ShowMessage(userMsg.resId, userMsg.formatArgs))
                    }
                }
                _uiState.update { it.copy(installingUrl = null) }
            }
        }
    }
