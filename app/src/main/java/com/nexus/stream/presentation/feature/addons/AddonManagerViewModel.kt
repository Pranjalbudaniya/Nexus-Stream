package com.nexus.stream.presentation.feature.addons

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexus.stream.R
import com.nexus.stream.data.remote.addon.AddonUrlBuilder
import com.nexus.stream.domain.model.Addon
import com.nexus.stream.domain.model.AddonCapabilities
import com.nexus.stream.domain.model.AddonManifest
import com.nexus.stream.domain.model.CINEMETA_URL
import com.nexus.stream.domain.model.Outcome
import com.nexus.stream.domain.model.capabilities
import com.nexus.stream.domain.model.isUsable
import com.nexus.stream.domain.repository.AddonRepository
import com.nexus.stream.domain.repository.ContentPrefsRepository
import com.nexus.stream.domain.usecase.EnsureDefaultAddons
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
    val capabilities: AddonCapabilities = AddonCapabilities(hasCatalogs = false, hasStreams = false),
    val seedLoading: Boolean = false,
    val seedFailed: Boolean = false,
    val addSheetState: AddSheetState = AddSheetState.Closed,
    val removeTarget: Addon? = null,
    val isFamilyMode: Boolean = true,
)

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
                -> handleAddSheetEvent(event)

                is AddonManagerEvent.SetEnabled,
                is AddonManagerEvent.MoveUp,
                is AddonManagerEvent.MoveDown,
                is AddonManagerEvent.Refresh,
                is AddonManagerEvent.CopyUrl,
                is AddonManagerEvent.OpenConfigure,
                AddonManagerEvent.AddCinemeta,
                AddonManagerEvent.RetrySeed,
                -> handleListEvent(event)

                is AddonManagerEvent.RequestRemove,
                AddonManagerEvent.ConfirmRemove,
                AddonManagerEvent.CancelRemove,
                -> handleDialogEvent(event)
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

        private fun handleListEvent(event: AddonManagerEvent) {
            when (event) {
                is AddonManagerEvent.SetEnabled -> {
                    if (_uiState.value.isFamilyMode && event.addon.isAdult) return
                    viewModelScope.launch { addonRepository.setEnabled(event.addon.transportUrl, event.enabled) }
                }

                is AddonManagerEvent.MoveUp -> {
                    viewModelScope.launch { addonRepository.move(event.addon.transportUrl, event.addon.position - 1) }
                }

                is AddonManagerEvent.MoveDown -> {
                    viewModelScope.launch { addonRepository.move(event.addon.transportUrl, event.addon.position + 1) }
                }

                is AddonManagerEvent.Refresh -> {
                    handleRefresh(event.addon)
                }

                is AddonManagerEvent.CopyUrl -> {
                    emitEffect(AddonManagerEffect.CopyToClipboard(event.addon.transportUrl))
                    emitEffect(AddonManagerEffect.ShowMessage(R.string.addon_copied_clipboard))
                }

                is AddonManagerEvent.OpenConfigure -> {
                    val configUrl = AddonUrlBuilder.configureUrl(event.addon.transportUrl)
                    if (configUrl != null) {
                        emitEffect(AddonManagerEffect.OpenUrl(configUrl))
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

        private fun handleRefresh(addon: Addon) {
            viewModelScope.launch {
                when (val outcome = addonRepository.refresh(addon.transportUrl)) {
                    is Outcome.Success -> {
                        emitEffect(
                            AddonManagerEffect.ShowMessage(R.string.addon_updated_success),
                        )
                    }

                    is Outcome.Failure -> {
                        val message = outcome.error.toUserMessage()
                        emitEffect(AddonManagerEffect.ShowMessage(message.resId, message.formatArgs))
                    }
                }
            }
        }

        private fun handleDialogEvent(event: AddonManagerEvent) {
            when (event) {
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
                        emitEffect(
                            AddonManagerEffect.ShowMessage(
                                R.string.addon_removed_success,
                                listOf(target.transportUrl.toDisplayHost()),
                            ),
                        )
                    }
                }

                else -> {}
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
                            emitEffect(AddonManagerEffect.ShowMessage(messageRes, listOf(preview.displayHost)))
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

        private fun emitEffect(effect: AddonManagerEffect) {
            viewModelScope.launch { _effects.emit(effect) }
        }
    }

fun String.toDisplayHost(): String {
    val httpUrl = AddonUrlBuilder.normalize(this)
    return httpUrl?.host ?: this
}
