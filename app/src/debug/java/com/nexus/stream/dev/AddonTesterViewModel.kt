package com.nexus.stream.dev

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexus.stream.data.remote.addon.AddonRemoteDataSource
import com.nexus.stream.domain.model.Addon
import com.nexus.stream.domain.model.AddonManifest
import com.nexus.stream.domain.model.AppError
import com.nexus.stream.domain.model.CatalogSpec
import com.nexus.stream.domain.model.MetaDetail
import com.nexus.stream.domain.model.MetaPreview
import com.nexus.stream.domain.model.Outcome
import com.nexus.stream.domain.model.Stream
import com.nexus.stream.domain.repository.AddonRepository
import com.nexus.stream.domain.usecase.mergeCatalogPage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AddonTesterUiState(
    val transportUrl: String = "https://v3-cinemeta.strem.io/manifest.json",
    val manifestLoading: Boolean = false,
    val manifestStatus: String? = null,
    val manifest: AddonManifest? = null,
    val saveLoading: Boolean = false,
    val saveStatus: String? = null,
    val savedAddons: List<Addon> = emptyList(),
    val addonActionStatus: String? = null,
    val selectedCatalog: CatalogSpec? = null,
    val selectedGenre: String? = null,
    val searchQuery: String = "",
    val catalogLoading: Boolean = false,
    val catalogStatus: String? = null,
    val catalogItems: List<MetaPreview> = emptyList(),
    val catalogLoadedCount: Int = 0,
    val catalogSkippedCount: Int = 0,
    val catalogEndReached: Boolean = false,
    val metaType: String = "movie",
    val metaId: String = "tt0111161",
    val metaLoading: Boolean = false,
    val metaStatus: String? = null,
    val metaDetail: MetaDetail? = null,
    val streamType: String = "movie",
    val streamId: String = "tt0111161",
    val streamLoading: Boolean = false,
    val streamStatus: String? = null,
    val streams: List<Stream>? = null,
)

sealed interface AddonTesterEvent {
    data class UpdateTransportUrl(
        val url: String,
    ) : AddonTesterEvent

    data object FetchManifest : AddonTesterEvent

    data object SaveManifestToDatabase : AddonTesterEvent

    data class ToggleAddonEnabled(
        val url: String,
        val enabled: Boolean,
    ) : AddonTesterEvent

    data class MoveAddon(
        val url: String,
        val toPosition: Int,
    ) : AddonTesterEvent

    data class RefreshAddon(
        val url: String,
    ) : AddonTesterEvent

    data class DeleteAddon(
        val url: String,
    ) : AddonTesterEvent

    data class UseAddon(
        val url: String,
    ) : AddonTesterEvent

    data class SelectCatalog(
        val catalog: CatalogSpec,
    ) : AddonTesterEvent

    data class SelectGenre(
        val genre: String?,
    ) : AddonTesterEvent

    data class UpdateSearchQuery(
        val query: String,
    ) : AddonTesterEvent

    data object LoadCatalogFirstPage : AddonTesterEvent

    data object LoadCatalogMore : AddonTesterEvent

    data class UpdateMetaType(
        val type: String,
    ) : AddonTesterEvent

    data class UpdateMetaId(
        val id: String,
    ) : AddonTesterEvent

    data object FetchMeta : AddonTesterEvent

    data class UpdateStreamType(
        val type: String,
    ) : AddonTesterEvent

    data class UpdateStreamId(
        val id: String,
    ) : AddonTesterEvent

    data object FetchStreams : AddonTesterEvent
}

sealed interface AddonTesterEffect

@HiltViewModel
class AddonTesterViewModel
    @Inject
    constructor(
        private val remoteDataSource: AddonRemoteDataSource,
        private val addonRepository: AddonRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(AddonTesterUiState())
        val uiState: StateFlow<AddonTesterUiState> = _uiState.asStateFlow()

        private val _effects = MutableSharedFlow<AddonTesterEffect>()
        val effects: SharedFlow<AddonTesterEffect> = _effects.asSharedFlow()

        init {
            viewModelScope.launch {
                addonRepository.observeAll().collect { addons ->
                    _uiState.update { it.copy(savedAddons = addons) }
                }
            }
        }

        fun onEvent(event: AddonTesterEvent) {
            when (event) {
                is AddonTesterEvent.UpdateTransportUrl,
                AddonTesterEvent.FetchManifest,
                AddonTesterEvent.SaveManifestToDatabase,
                is AddonTesterEvent.ToggleAddonEnabled,
                is AddonTesterEvent.MoveAddon,
                is AddonTesterEvent.RefreshAddon,
                is AddonTesterEvent.DeleteAddon,
                is AddonTesterEvent.UseAddon,
                -> handleManifestAndDbEvent(event)

                is AddonTesterEvent.SelectCatalog,
                is AddonTesterEvent.SelectGenre,
                is AddonTesterEvent.UpdateSearchQuery,
                AddonTesterEvent.LoadCatalogFirstPage,
                AddonTesterEvent.LoadCatalogMore,
                -> handleCatalogEvent(event)

                is AddonTesterEvent.UpdateMetaType,
                is AddonTesterEvent.UpdateMetaId,
                AddonTesterEvent.FetchMeta,
                is AddonTesterEvent.UpdateStreamType,
                is AddonTesterEvent.UpdateStreamId,
                AddonTesterEvent.FetchStreams,
                -> handleMetaAndStreamEvent(event)
            }
        }

        private fun handleManifestAndDbEvent(event: AddonTesterEvent) {
            when (event) {
                is AddonTesterEvent.UpdateTransportUrl -> {
                    _uiState.update { it.copy(transportUrl = event.url) }
                }

                AddonTesterEvent.FetchManifest -> {
                    fetchManifest()
                }

                AddonTesterEvent.SaveManifestToDatabase -> {
                    saveManifestToDatabase()
                }

                is AddonTesterEvent.ToggleAddonEnabled -> {
                    viewModelScope.launch { addonRepository.setEnabled(event.url, event.enabled) }
                }

                is AddonTesterEvent.MoveAddon -> {
                    viewModelScope.launch { addonRepository.move(event.url, event.toPosition) }
                }

                is AddonTesterEvent.RefreshAddon -> {
                    viewModelScope.launch {
                        val outcome = addonRepository.refresh(event.url)
                        _uiState.update { current ->
                            val status =
                                when (outcome) {
                                    is Outcome.Success -> "Refreshed ${outcome.value.manifest.name}"
                                    is Outcome.Failure -> "Refresh failed: ${outcome.error.toDisplayName()}"
                                }
                            current.copy(addonActionStatus = status)
                        }
                    }
                }

                is AddonTesterEvent.DeleteAddon -> {
                    viewModelScope.launch {
                        addonRepository.remove(event.url)
                        _uiState.update { it.copy(addonActionStatus = "Deleted ${event.url}") }
                    }
                }

                is AddonTesterEvent.UseAddon -> {
                    _uiState.update { it.copy(transportUrl = event.url) }
                    fetchManifest()
                }

                else -> {}
            }
        }

        private fun handleCatalogEvent(event: AddonTesterEvent) {
            when (event) {
                is AddonTesterEvent.SelectCatalog -> {
                    _uiState.update {
                        it.copy(
                            selectedCatalog = event.catalog,
                            selectedGenre = null,
                            searchQuery = "",
                            catalogItems = emptyList(),
                            catalogLoadedCount = 0,
                            catalogSkippedCount = 0,
                            catalogEndReached = false,
                            catalogStatus = null,
                        )
                    }
                }

                is AddonTesterEvent.SelectGenre -> {
                    _uiState.update { it.copy(selectedGenre = event.genre) }
                }

                is AddonTesterEvent.UpdateSearchQuery -> {
                    _uiState.update { it.copy(searchQuery = event.query) }
                }

                AddonTesterEvent.LoadCatalogFirstPage -> {
                    loadCatalogFirstPage()
                }

                AddonTesterEvent.LoadCatalogMore -> {
                    loadCatalogMore()
                }

                else -> {}
            }
        }

        private fun handleMetaAndStreamEvent(event: AddonTesterEvent) {
            when (event) {
                is AddonTesterEvent.UpdateMetaType -> {
                    _uiState.update { it.copy(metaType = event.type) }
                }

                is AddonTesterEvent.UpdateMetaId -> {
                    _uiState.update { it.copy(metaId = event.id) }
                }

                AddonTesterEvent.FetchMeta -> {
                    fetchMeta()
                }

                is AddonTesterEvent.UpdateStreamType -> {
                    _uiState.update { it.copy(streamType = event.type) }
                }

                is AddonTesterEvent.UpdateStreamId -> {
                    _uiState.update { it.copy(streamId = event.id) }
                }

                AddonTesterEvent.FetchStreams -> {
                    fetchStreams()
                }

                else -> {}
            }
        }

        private fun fetchManifest() {
            val url = _uiState.value.transportUrl
            _uiState.update { it.copy(manifestLoading = true, manifestStatus = null) }
            viewModelScope.launch {
                val start = System.currentTimeMillis()
                val outcome = remoteDataSource.manifest(url)
                val elapsed = System.currentTimeMillis() - start
                _uiState.update { current ->
                    when (outcome) {
                        is Outcome.Success -> {
                            val manifest = outcome.value
                            current.copy(
                                manifestLoading = false,
                                manifestStatus = "Success ($elapsed ms)",
                                manifest = manifest,
                                selectedCatalog = manifest.catalogs.firstOrNull(),
                                selectedGenre = null,
                                searchQuery = "",
                                catalogItems = emptyList(),
                                catalogLoadedCount = 0,
                                catalogSkippedCount = 0,
                                catalogEndReached = false,
                                catalogStatus = null,
                            )
                        }

                        is Outcome.Failure -> {
                            current.copy(
                                manifestLoading = false,
                                manifestStatus = "Failure: ${outcome.error.toDisplayName()} ($elapsed ms)",
                            )
                        }
                    }
                }
            }
        }

        private fun loadCatalogFirstPage() {
            val state = _uiState.value
            val catalog = state.selectedCatalog ?: return
            val extras =
                buildMap {
                    if (!state.selectedGenre.isNullOrBlank()) {
                        put("genre", state.selectedGenre)
                    }
                    if (state.searchQuery.isNotBlank()) {
                        put("search", state.searchQuery)
                    }
                }
            _uiState.update { it.copy(catalogLoading = true, catalogStatus = null) }
            viewModelScope.launch {
                val start = System.currentTimeMillis()
                val outcome = remoteDataSource.catalog(state.transportUrl, catalog.type, catalog.id, extras)
                val elapsed = System.currentTimeMillis() - start
                _uiState.update { current ->
                    when (outcome) {
                        is Outcome.Success -> {
                            val result = outcome.value
                            current.copy(
                                catalogLoading = false,
                                catalogItems = result.items,
                                catalogLoadedCount = result.items.size,
                                catalogSkippedCount = result.skipped,
                                catalogEndReached = result.items.isEmpty(),
                                catalogStatus = "Success ($elapsed ms)",
                            )
                        }

                        is Outcome.Failure -> {
                            current.copy(
                                catalogLoading = false,
                                catalogStatus = "Failure: ${outcome.error.toDisplayName()} ($elapsed ms)",
                            )
                        }
                    }
                }
            }
        }

        private fun loadCatalogMore() {
            val state = _uiState.value
            val catalog = state.selectedCatalog ?: return
            val skip = state.catalogItems.size
            val extras =
                buildMap {
                    if (!state.selectedGenre.isNullOrBlank()) {
                        put("genre", state.selectedGenre)
                    }
                    if (state.searchQuery.isNotBlank()) {
                        put("search", state.searchQuery)
                    }
                    put("skip", skip.toString())
                }
            _uiState.update { it.copy(catalogLoading = true, catalogStatus = null) }
            viewModelScope.launch {
                val start = System.currentTimeMillis()
                val outcome = remoteDataSource.catalog(state.transportUrl, catalog.type, catalog.id, extras)
                val elapsed = System.currentTimeMillis() - start
                _uiState.update { current ->
                    when (outcome) {
                        is Outcome.Success -> {
                            val result = outcome.value
                            val merged = mergeCatalogPage(current.catalogItems, result.items)
                            current.copy(
                                catalogLoading = false,
                                catalogItems = merged.items,
                                catalogLoadedCount = merged.items.size,
                                catalogSkippedCount = current.catalogSkippedCount + result.skipped,
                                catalogEndReached = merged.addedCount == 0,
                                catalogStatus = "Success ($elapsed ms)",
                            )
                        }

                        is Outcome.Failure -> {
                            current.copy(
                                catalogLoading = false,
                                catalogStatus = "Failure: ${outcome.error.toDisplayName()} ($elapsed ms)",
                            )
                        }
                    }
                }
            }
        }

        private fun fetchMeta() {
            val state = _uiState.value
            _uiState.update { it.copy(metaLoading = true, metaStatus = null, metaDetail = null) }
            viewModelScope.launch {
                val start = System.currentTimeMillis()
                val outcome = remoteDataSource.meta(state.transportUrl, state.metaType, state.metaId)
                val elapsed = System.currentTimeMillis() - start
                _uiState.update { current ->
                    when (outcome) {
                        is Outcome.Success -> {
                            current.copy(
                                metaLoading = false,
                                metaDetail = outcome.value,
                                metaStatus = "Success ($elapsed ms)",
                            )
                        }

                        is Outcome.Failure -> {
                            current.copy(
                                metaLoading = false,
                                metaStatus = "Failure: ${outcome.error.toDisplayName()} ($elapsed ms)",
                            )
                        }
                    }
                }
            }
        }

        private fun fetchStreams() {
            val state = _uiState.value
            _uiState.update { it.copy(streamLoading = true, streamStatus = null, streams = null) }
            viewModelScope.launch {
                val start = System.currentTimeMillis()
                val outcome = remoteDataSource.streams(state.transportUrl, state.streamType, state.streamId)
                val elapsed = System.currentTimeMillis() - start
                _uiState.update { current ->
                    when (outcome) {
                        is Outcome.Success -> {
                            current.copy(
                                streamLoading = false,
                                streams = outcome.value,
                                streamStatus = "Success ($elapsed ms)",
                            )
                        }

                        is Outcome.Failure -> {
                            current.copy(
                                streamLoading = false,
                                streamStatus = "Failure: ${outcome.error.toDisplayName()} ($elapsed ms)",
                            )
                        }
                    }
                }
            }
        }

        private fun saveManifestToDatabase() {
            val url = _uiState.value.transportUrl
            _uiState.update { it.copy(saveLoading = true, saveStatus = null) }
            viewModelScope.launch {
                val outcome = addonRepository.install(url)
                _uiState.update { current ->
                    when (outcome) {
                        is Outcome.Success -> {
                            current.copy(
                                saveLoading = false,
                                saveStatus = "Saved: ${outcome.value.manifest.name}",
                            )
                        }

                        is Outcome.Failure -> {
                            current.copy(
                                saveLoading = false,
                                saveStatus = "Failure: ${outcome.error.toDisplayName()}",
                            )
                        }
                    }
                }
            }
        }
    }

private fun AppError.toDisplayName(): String =
    when (this) {
        AppError.InvalidUrl -> "InvalidUrl"
        AppError.Network -> "Network"
        AppError.NotFound -> "NotFound"
        AppError.Parse -> "Parse"
        AppError.Timeout -> "Timeout"
        is AppError.Http -> "Http($code)"
        is AppError.Unknown -> "Unknown(${message.orEmpty()})"
        is AppError.Torrent -> "Torrent(${reason.name})"
    }
