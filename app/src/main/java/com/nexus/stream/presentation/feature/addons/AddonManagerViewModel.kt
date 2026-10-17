package com.nexus.stream.presentation.feature.addons

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexus.stream.R
import com.nexus.stream.data.remote.addon.AddonUrlBuilder
import com.nexus.stream.domain.model.Addon
import com.nexus.stream.domain.model.AddonCapabilities
import com.nexus.stream.domain.model.AddonCategory
import com.nexus.stream.domain.model.AddonManifest
import com.nexus.stream.domain.model.CINEMETA_URL
import com.nexus.stream.domain.model.Outcome
import com.nexus.stream.domain.model.capabilities
import com.nexus.stream.domain.model.isUsable
import com.nexus.stream.domain.repository.AddonRepository
import com.nexus.stream.domain.repository.ContentPrefsRepository
import com.nexus.stream.domain.usecase.AddonFilters
import com.nexus.stream.domain.usecase.EnsureDefaultAddons
import com.nexus.stream.domain.usecase.RefreshAllAddons
import com.nexus.stream.domain.usecase.toDisplayHost
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
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

sealed interface AddSheetState {
    data object Closed : AddSheetState

    data class Input(
        val url: String = "",
        val error: AddonErrorText? = null,
    ) : AddSheetState

    data class Checking(
        val url: String,
    ) : AddSheetState

    data class Preview(
        val manifest: AddonManifest,
        val transportUrl: String,
        val displayHost: String,
        val isHttp: Boolean,
        val alreadyInstalled: Boolean,
        val usable: Boolean,
        val isAdultBlocked: Boolean = false,
    ) : AddSheetState

    data class Installing(
        val transportUrl: String,
    ) : AddSheetState
}

data class AddonManagerUiState(
    val addons: List<Addon> = emptyList(),
    val searchQuery: String = "",
    val selectedCategory: AddonCategory? = null,
    val capabilities: AddonCapabilities = AddonCapabilities(hasCatalogs = false, hasStreams = false),
    val seedLoading: Boolean = false,
    val seedFailed: Boolean = false,
    val addSheetState: AddSheetState = AddSheetState.Closed,
    val removeTarget: Addon? = null,
    val isFamilyMode: Boolean = true,
    val isRefreshingAll: Boolean = false,
    val refreshProgress: Pair<Int, Int>? = null,
    val failedRefreshAddons: Set<String> = emptySet(),
    val isStoreOpen: Boolean = false,
    val storeInitialCategory: AddonCategory? = null,
) {
    val visibleAddons: List<Addon>
        get() = AddonFilters.filterInstalled(addons, searchQuery, selectedCategory)

    val sections: List<Pair<AddonCategory, List<Addon>>>
        get() = AddonFilters.groupBySection(visibleAddons)

    val categoryCounts: Map<AddonCategory, Int>
        get() = AddonFilters.categoryCounts(addons)

    val flattenedVisibleAddons: List<Addon>
        get() =
            if (selectedCategory == null) {
                sections.flatMap { it.second }
            } else {
                visibleAddons
            }
}

sealed interface AddonManagerEvent {
    data object RetrySeed : AddonManagerEvent

    data object AddCinemeta : AddonManagerEvent

    data class OpenAdd(
        val prefill: String? = null,
        val autoCheck: Boolean = false,
    ) : AddonManagerEvent

    data class UrlChanged(
        val url: String,
    ) : AddonManagerEvent

    data object Check : AddonManagerEvent

    data object Install : AddonManagerEvent

    data object DismissAddSheet : AddonManagerEvent

    data class SearchQueryChanged(
        val query: String,
    ) : AddonManagerEvent

    data object ClearSearch : AddonManagerEvent

    data class SelectCategory(
        val category: AddonCategory?,
    ) : AddonManagerEvent

    data object RefreshAll : AddonManagerEvent

    data class OpenStore(
        val initialCategory: AddonCategory? = null,
    ) : AddonManagerEvent

    data object DismissStore : AddonManagerEvent

    data class SetEnabled(
        val addon: Addon,
        val enabled: Boolean,
    ) : AddonManagerEvent

    data class MoveUp(
        val addon: Addon,
    ) : AddonManagerEvent

    data class MoveDown(
        val addon: Addon,
    ) : AddonManagerEvent

    data class Refresh(
        val addon: Addon,
    ) : AddonManagerEvent

    data class RequestRemove(
        val addon: Addon,
    ) : AddonManagerEvent

    data object ConfirmRemove : AddonManagerEvent

    data object CancelRemove : AddonManagerEvent

    data class CopyUrl(
        val addon: Addon,
    ) : AddonManagerEvent

    data class OpenConfigure(
        val addon: Addon,
    ) : AddonManagerEvent
}

sealed interface AddonManagerEffect {
    data class ShowMessage(
        @StringRes val messageRes: Int,
        val formatArgs: List<Any> = emptyList(),
    ) : AddonManagerEffect

    data class CopyToClipboard(
        val text: String,
    ) : AddonManagerEffect

    data class OpenUrl(
        val url: String,
    ) : AddonManagerEffect
}

@HiltViewModel
class AddonManagerViewModel
    @Inject
    constructor(
        private val addonRepository: AddonRepository,
        private val ensureDefaultAddons: EnsureDefaultAddons,
        private val contentPrefsRepository: ContentPrefsRepository,
        private val refreshAllAddons: RefreshAllAddons,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(AddonManagerUiState())
        val uiState: StateFlow<AddonManagerUiState> = _uiState.asStateFlow()

        private val _effects = MutableSharedFlow<AddonManagerEffect>()
        val effects: SharedFlow<AddonManagerEffect> = _effects.asSharedFlow()

        private var checkJob: Job? = null
        private var installJob: Job? = null

        init {
            viewModelScope.launch {
                combine(
                    addonRepository.observeAll(),
                    contentPrefsRepository.familyMode,
                ) { list, familyMode ->
                    list to familyMode
                }.collect { (list, familyMode) ->
                    _uiState.update { current ->
                        current.copy(
                            addons = list,
                            capabilities = list.filter { it.enabled && (!familyMode || !it.isAdult) }.capabilities(),
                            isFamilyMode = familyMode,
                        )
                    }
                }
            }
            runSeed()
        }

        fun onEvent(event: AddonManagerEvent) {
            when (event) {
                is AddonManagerEvent.OpenAdd,
                is AddonManagerEvent.UrlChanged,
                AddonManagerEvent.Check,
                AddonManagerEvent.Install,
                AddonManagerEvent.DismissAddSheet,
                -> {
                    handleAddSheetEvent(event)
                }

                is AddonManagerEvent.SearchQueryChanged,
                AddonManagerEvent.ClearSearch,
                is AddonManagerEvent.SelectCategory,
                is AddonManagerEvent.OpenStore,
                AddonManagerEvent.DismissStore,
                -> {
                    handleFilterEvent(event)
                }

                is AddonManagerEvent.MoveUp,
                is AddonManagerEvent.MoveDown,
                -> {
                    handleMove(event)
                }

                is AddonManagerEvent.RequestRemove -> {
                    _uiState.update { it.copy(removeTarget = event.addon) }
                }

                AddonManagerEvent.CancelRemove -> {
                    _uiState.update { it.copy(removeTarget = null) }
                }

                AddonManagerEvent.ConfirmRemove -> {
                    val target = _uiState.value.removeTarget ?: return
                    _uiState.update { it.copy(removeTarget = null) }
                    viewModelScope.launch {
                        addonRepository.remove(target.transportUrl)
                        _effects.emit(
                            AddonManagerEffect.ShowMessage(
                                R.string.addon_removed_success,
                                listOf(target.transportUrl.toDisplayHost()),
                            ),
                        )
                    }
                }

                else -> {
                    handleListEvent(event)
                }
            }
        }

        private fun handleAddSheetEvent(event: AddonManagerEvent) {
            when (event) {
                is AddonManagerEvent.OpenAdd -> {
                    val url = event.prefill?.trim().orEmpty()
                    _uiState.update { it.copy(addSheetState = AddSheetState.Input(url = url)) }
                    if (event.autoCheck && url.isNotEmpty()) {
                        checkAddonUrl()
                    }
                }

                is AddonManagerEvent.UrlChanged -> {
                    _uiState.update { it.copy(addSheetState = AddSheetState.Input(url = event.url)) }
                }

                AddonManagerEvent.Check -> {
                    checkAddonUrl()
                }

                AddonManagerEvent.Install -> {
                    installAddon()
                }

                AddonManagerEvent.DismissAddSheet -> {
                    checkJob?.cancel()
                    checkJob = null
                    installJob?.cancel()
                    installJob = null
                    _uiState.update { it.copy(addSheetState = AddSheetState.Closed) }
                }

                else -> {}
            }
        }

        private fun handleFilterEvent(event: AddonManagerEvent) {
            when (event) {
                is AddonManagerEvent.SearchQueryChanged -> {
                    _uiState.update { it.copy(searchQuery = event.query) }
                }

                AddonManagerEvent.ClearSearch -> {
                    _uiState.update { it.copy(searchQuery = "") }
                }

                is AddonManagerEvent.SelectCategory -> {
                    _uiState.update { it.copy(selectedCategory = event.category) }
                }

                is AddonManagerEvent.OpenStore -> {
                    _uiState.update {
                        it.copy(
                            isStoreOpen = true,
                            storeInitialCategory = event.initialCategory,
                        )
                    }
                }

                AddonManagerEvent.DismissStore -> {
                    _uiState.update { it.copy(isStoreOpen = false, storeInitialCategory = null) }
                }

                else -> {}
            }
        }

        private fun handleMove(event: AddonManagerEvent) {
            val list =
                if (_uiState.value.selectedCategory == null) {
                    _uiState.value.flattenedVisibleAddons
                } else {
                    _uiState.value.visibleAddons
                }
            val addon =
                if (event is AddonManagerEvent.MoveUp) {
                    event.addon
                } else {
                    (event as AddonManagerEvent.MoveDown).addon
                }
            val index = list.indexOfFirst { it.transportUrl == addon.transportUrl }
            val neighbor =
                if (event is AddonManagerEvent.MoveUp) {
                    if (index > 0) list[index - 1] else null
                } else {
                    if (index in 0 until list.size - 1) list[index + 1] else null
                } ?: return

            viewModelScope.launch {
                addonRepository.move(addon.transportUrl, neighbor.position)
            }
        }

        private fun handleListEvent(event: AddonManagerEvent) {
            when (event) {
                AddonManagerEvent.RefreshAll -> {
                    handleRefreshAll()
                }

                is AddonManagerEvent.SetEnabled -> {
                    if (!_uiState.value.isFamilyMode || !event.addon.isAdult) {
                        viewModelScope.launch {
                            addonRepository.setEnabled(event.addon.transportUrl, event.enabled)
                        }
                    }
                }

                is AddonManagerEvent.Refresh -> {
                    handleRefresh(event.addon)
                }

                is AddonManagerEvent.CopyUrl -> {
                    viewModelScope.launch {
                        _effects.emit(AddonManagerEffect.CopyToClipboard(event.addon.transportUrl))
                        _effects.emit(AddonManagerEffect.ShowMessage(R.string.addon_copied_clipboard))
                    }
                }

                is AddonManagerEvent.OpenConfigure -> {
                    AddonUrlBuilder.configureUrl(event.addon.transportUrl)?.let { url ->
                        viewModelScope.launch { _effects.emit(AddonManagerEffect.OpenUrl(url)) }
                    }
                }

                AddonManagerEvent.AddCinemeta -> {
                    viewModelScope.launch { addonRepository.install(CINEMETA_URL) }
                }

                AddonManagerEvent.RetrySeed -> {
                    runSeed()
                }

                else -> {}
            }
        }

        private fun handleRefreshAll() {
            if (_uiState.value.isRefreshingAll) return
            _uiState.update { it.copy(isRefreshingAll = true, refreshProgress = null) }
            viewModelScope.launch {
                val summary =
                    refreshAllAddons { done, total ->
                        _uiState.update { it.copy(refreshProgress = done to total) }
                    }
                _uiState.update {
                    it.copy(
                        isRefreshingAll = false,
                        refreshProgress = null,
                        failedRefreshAddons = summary.failed.toSet(),
                    )
                }
                if (summary.failed.isEmpty()) {
                    _effects.emit(
                        AddonManagerEffect.ShowMessage(
                            R.string.addon_refresh_all_success,
                            listOf(summary.updated),
                        ),
                    )
                } else {
                    _effects.emit(
                        AddonManagerEffect.ShowMessage(
                            R.string.addon_refresh_all_partial,
                            listOf(summary.updated, summary.failed.size),
                        ),
                    )
                }
            }
        }

        private fun handleRefresh(addon: Addon) {
            viewModelScope.launch {
                when (val outcome = addonRepository.refresh(addon.transportUrl)) {
                    is Outcome.Success -> {
                        _uiState.update {
                            it.copy(failedRefreshAddons = it.failedRefreshAddons - addon.manifest.name)
                        }
                        _effects.emit(
                            AddonManagerEffect.ShowMessage(R.string.addon_updated_success),
                        )
                    }

                    is Outcome.Failure -> {
                        _uiState.update {
                            it.copy(failedRefreshAddons = it.failedRefreshAddons + addon.manifest.name)
                        }
                        val message = outcome.error.toUserMessage()
                        _effects.emit(AddonManagerEffect.ShowMessage(message.resId, message.formatArgs))
                    }
                }
            }
        }

        private fun runSeed() {
            _uiState.update { it.copy(seedLoading = true, seedFailed = false) }
            viewModelScope.launch {
                when (ensureDefaultAddons()) {
                    is Outcome.Success -> _uiState.update { it.copy(seedLoading = false, seedFailed = false) }
                    is Outcome.Failure -> _uiState.update { it.copy(seedLoading = false, seedFailed = true) }
                }
            }
        }

        private fun checkAddonUrl() {
            val currentInput = _uiState.value.addSheetState as? AddSheetState.Input ?: return
            val trimmed = currentInput.url.trim()
            val normalized = AddonUrlBuilder.normalize(trimmed)
            if (trimmed.isEmpty() || normalized == null) {
                _uiState.update {
                    it.copy(
                        addSheetState =
                            AddSheetState.Input(
                                url = trimmed,
                                error = AddonErrorText(R.string.error_addon_invalid_url),
                            ),
                    )
                }
                return
            }

            _uiState.update { it.copy(addSheetState = AddSheetState.Checking(url = trimmed)) }
            checkJob?.cancel()
            checkJob =
                viewModelScope.launch {
                    val normalizedUrl = normalized.toString()
                    when (val outcome = addonRepository.fetchManifest(normalizedUrl)) {
                        is Outcome.Success -> {
                            val manifest = outcome.value
                            val isAdultBlocked = _uiState.value.isFamilyMode && manifest.isAdult
                            _uiState.update { current ->
                                current.copy(
                                    addSheetState =
                                        AddSheetState.Preview(
                                            manifest = manifest,
                                            transportUrl = normalizedUrl,
                                            displayHost = normalized.host,
                                            isHttp = normalized.scheme == "http",
                                            alreadyInstalled = current.addons.any { it.transportUrl == normalizedUrl },
                                            usable = manifest.isUsable,
                                            isAdultBlocked = isAdultBlocked,
                                        ),
                                )
                            }
                        }

                        is Outcome.Failure -> {
                            val errorText = outcome.error.toUserMessage()
                            _uiState.update {
                                it.copy(
                                    addSheetState =
                                        AddSheetState.Input(
                                            url = trimmed,
                                            error = errorText,
                                        ),
                                )
                            }
                        }
                    }
                }
        }

        private fun installAddon() {
            val preview = _uiState.value.addSheetState as? AddSheetState.Preview ?: return
            if (!preview.usable || preview.isAdultBlocked) return

            _uiState.update { it.copy(addSheetState = AddSheetState.Installing(preview.transportUrl)) }
            installJob?.cancel()
            installJob =
                viewModelScope.launch {
                    when (addonRepository.install(preview.transportUrl)) {
                        is Outcome.Success -> {
                            _uiState.update { it.copy(addSheetState = AddSheetState.Closed) }
                            val messageRes =
                                if (preview.alreadyInstalled) {
                                    R.string.addon_updated_name_success
                                } else {
                                    R.string.addon_installed_success
                                }
                            _effects.emit(AddonManagerEffect.ShowMessage(messageRes, listOf(preview.displayHost)))
                        }

                        is Outcome.Failure -> {
                            _uiState.update {
                                it.copy(
                                    addSheetState =
                                        AddSheetState.Input(
                                            url = preview.transportUrl,
                                            error = AddonErrorText(R.string.error_addon_network),
                                        ),
                                )
                            }
                        }
                    }
                }
        }
    }

fun String.toDisplayHost(): String {
    val httpUrl = AddonUrlBuilder.normalize(this)
    return httpUrl?.host ?: this
}
