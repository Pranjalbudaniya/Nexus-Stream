package com.nexus.stream.presentation.feature.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexus.stream.R
import com.nexus.stream.di.IoDispatcher
import com.nexus.stream.domain.model.UpdateCandidate
import com.nexus.stream.domain.model.UpdateEffect
import com.nexus.stream.domain.model.UpdateFailure
import com.nexus.stream.domain.model.UpdateState
import com.nexus.stream.domain.repository.UpdateManager
import com.nexus.stream.domain.repository.UpdatePrefsRepository
import com.nexus.stream.domain.usecase.INSTALL_STORAGE_MARGIN_BYTES
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StatusText(
    val resId: Int,
    val formatArgs: List<Any> = emptyList(),
    val sizeBytes: Long? = null,
)

sealed interface Progress {
    data object Indeterminate : Progress

    data class Determinate(
        val fraction: Float,
    ) : Progress
}

data class UiButton(
    val textResId: Int,
    val event: UpdateEvent,
    val isPrimary: Boolean = true,
)

sealed interface UpdateEvent {
    data object CheckNow : UpdateEvent

    data object Download : UpdateEvent

    data object ConfirmMetered : UpdateEvent

    data object DismissMetered : UpdateEvent

    data object Cancel : UpdateEvent

    data object Install : UpdateEvent

    data object Continue : UpdateEvent

    data object OpenInstallSettings : UpdateEvent

    data object DismissPermissionDialog : UpdateEvent

    data object DismissFailure : UpdateEvent

    data object OpenRelease : UpdateEvent

    data class SetAutoCheck(
        val enabled: Boolean,
    ) : UpdateEvent

    data object Resumed : UpdateEvent
}

sealed interface UpdateUiEffect {
    data class OpenInstallPermissionSettings(
        val packageName: String,
    ) : UpdateUiEffect

    data class OpenUrl(
        val url: String,
    ) : UpdateUiEffect

    data class ShowUpdated(
        val versionName: String,
    ) : UpdateUiEffect
}

data class UpdateUi(
    val status: StatusText,
    val bigButtons: List<UiButton> = emptyList(),
    val notes: String? = null,
    val progress: Progress? = null,
    val showMeteredDialog: Boolean = false,
    val showPermissionDialog: Boolean = false,
    val autoCheck: Boolean = true,
    val summaryFlag: Boolean = false,
    val meteredSizeBytes: Long? = null,
)

private const val PERCENT_MULTIPLIER = 100
private const val STORAGE_MULTIPLIER = 2

private val STATIC_FAILURES =
    mapOf(
        UpdateFailure.Network to R.string.update_failure_network,
        UpdateFailure.Timeout to R.string.update_failure_timeout,
        UpdateFailure.NoRelease to R.string.update_failure_no_release,
        UpdateFailure.NoApk to R.string.update_failure_no_apk,
        UpdateFailure.BadResponse to R.string.update_failure_bad_response,
        UpdateFailure.DownloadFailed to R.string.update_failure_download_failed,
        UpdateFailure.SizeMismatch to R.string.update_failure_corrupt,
        UpdateFailure.ChecksumMismatch to R.string.update_failure_corrupt,
        UpdateFailure.InvalidApk to R.string.update_failure_invalid_apk,
        UpdateFailure.PackageMismatch to R.string.update_failure_package_mismatch,
        UpdateFailure.NotNewer to R.string.update_failure_not_newer,
        UpdateFailure.SignatureMismatch to R.string.update_failure_signature_mismatch,
        UpdateFailure.InstallBlocked to R.string.update_failure_install_blocked,
        UpdateFailure.InstallIncompatible to R.string.update_failure_install_incompatible,
        UpdateFailure.InstallConflict to R.string.update_failure_install_conflict,
        UpdateFailure.InstallStorage to R.string.update_failure_install_storage,
        UpdateFailure.InstallInvalid to R.string.update_failure_install_failed,
        UpdateFailure.InstallFailed to R.string.update_failure_install_failed,
        UpdateFailure.Unsupported to R.string.update_unsupported,
    )

fun failureMessage(
    failure: UpdateFailure,
    neededBytes: Long = 0L,
): StatusText =
    when (failure) {
        is UpdateFailure.InsufficientStorage -> {
            StatusText(
                resId = R.string.update_failure_insufficient_storage,
                formatArgs = if (neededBytes > 0L) listOf(neededBytes) else emptyList(),
            )
        }

        is UpdateFailure.RateLimited -> {
            StatusText(R.string.update_failure_rate_limited)
        }

        else -> {
            StatusText(STATIC_FAILURES[failure] ?: R.string.update_failure_bad_response)
        }
    }

private fun cleanTag(tag: String): String = tag.removePrefix("v").removePrefix("V")

private fun isViewReleaseOnly(failure: UpdateFailure): Boolean =
    failure is UpdateFailure.SignatureMismatch ||
        failure is UpdateFailure.InstallBlocked ||
        failure is UpdateFailure.InstallConflict ||
        failure is UpdateFailure.InstallIncompatible

private fun buttonsForFailed(
    failure: UpdateFailure,
    candidate: UpdateCandidate?,
): List<UiButton> =
    if (isViewReleaseOnly(failure)) {
        listOf(UiButton(R.string.update_view_release, UpdateEvent.OpenRelease, isPrimary = true))
    } else {
        val primary = UiButton(R.string.update_try_again, UpdateEvent.DismissFailure, isPrimary = true)
        if (candidate != null && candidate.releaseUrl.isNotBlank()) {
            listOf(primary, UiButton(R.string.update_view_release, UpdateEvent.OpenRelease, isPrimary = false))
        } else {
            listOf(primary)
        }
    }

@Suppress("LongMethod", "CyclomaticComplexMethod")
fun updateUiOf(
    state: UpdateState,
    @Suppress("UnusedParameter") now: Long = 0L,
    autoCheck: Boolean = true,
    showPermissionDialog: Boolean = state is UpdateState.NeedsInstallPermission,
): UpdateUi =
    when (state) {
        is UpdateState.Idle -> {
            if (state.lastCheckedAt == null) {
                UpdateUi(
                    status = StatusText(R.string.update_not_checked),
                    bigButtons = listOf(UiButton(R.string.update_check, UpdateEvent.CheckNow, isPrimary = true)),
                    autoCheck = autoCheck,
                    summaryFlag = false,
                )
            } else {
                UpdateUi(
                    status = StatusText(R.string.update_up_to_date, listOf(state.lastCheckedAt)),
                    bigButtons = listOf(UiButton(R.string.update_check_again, UpdateEvent.CheckNow, isPrimary = true)),
                    autoCheck = autoCheck,
                    summaryFlag = false,
                )
            }
        }

        is UpdateState.Checking -> {
            UpdateUi(
                status = StatusText(R.string.update_checking),
                progress = Progress.Indeterminate,
                autoCheck = autoCheck,
                summaryFlag = false,
            )
        }

        is UpdateState.UpToDate -> {
            UpdateUi(
                status = StatusText(R.string.update_up_to_date, listOf(state.checkedAt)),
                bigButtons = listOf(UiButton(R.string.update_check_again, UpdateEvent.CheckNow, isPrimary = true)),
                autoCheck = autoCheck,
                summaryFlag = false,
            )
        }

        is UpdateState.Available -> {
            val size = if (state.candidate.asset.sizeBytes > 0L) state.candidate.asset.sizeBytes else null
            UpdateUi(
                status = StatusText(R.string.update_available, listOf(cleanTag(state.candidate.tag)), sizeBytes = size),
                bigButtons =
                    listOf(
                        UiButton(R.string.update_download, UpdateEvent.Download, isPrimary = true),
                        UiButton(R.string.update_view_release, UpdateEvent.OpenRelease, isPrimary = false),
                    ),
                notes = state.candidate.notes,
                autoCheck = autoCheck,
                summaryFlag = true,
            )
        }

        is UpdateState.ConfirmMetered -> {
            UpdateUi(
                status = StatusText(R.string.update_available, listOf(cleanTag(state.candidate.tag))),
                bigButtons =
                    listOf(
                        UiButton(R.string.update_download, UpdateEvent.ConfirmMetered, isPrimary = true),
                        UiButton(R.string.update_cancel, UpdateEvent.DismissMetered, isPrimary = false),
                    ),
                showMeteredDialog = true,
                autoCheck = autoCheck,
                summaryFlag = true,
                meteredSizeBytes = state.candidate.asset.sizeBytes,
            )
        }

        is UpdateState.Downloading -> {
            val total = state.totalBytes
            val tag = cleanTag(state.candidate.tag)
            if (total > 0L) {
                val fraction = (state.downloadedBytes.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                val percent =
                    ((state.downloadedBytes * PERCENT_MULTIPLIER) / total).toInt().coerceIn(
                        0,
                        PERCENT_MULTIPLIER,
                    )
                UpdateUi(
                    status = StatusText(R.string.update_downloading, listOf(tag, percent)),
                    bigButtons = listOf(UiButton(R.string.update_cancel, UpdateEvent.Cancel, isPrimary = false)),
                    progress = Progress.Determinate(fraction),
                    autoCheck = autoCheck,
                    summaryFlag = true,
                )
            } else {
                UpdateUi(
                    status = StatusText(R.string.update_downloading_indeterminate, listOf(tag)),
                    bigButtons = listOf(UiButton(R.string.update_cancel, UpdateEvent.Cancel, isPrimary = false)),
                    progress = Progress.Indeterminate,
                    autoCheck = autoCheck,
                    summaryFlag = true,
                )
            }
        }

        is UpdateState.Verifying -> {
            UpdateUi(
                status = StatusText(R.string.update_verifying),
                progress = Progress.Indeterminate,
                autoCheck = autoCheck,
                summaryFlag = true,
            )
        }

        is UpdateState.NeedsInstallPermission -> {
            UpdateUi(
                status = StatusText(R.string.update_available, listOf(cleanTag(state.candidate.tag))),
                bigButtons =
                    listOf(
                        UiButton(R.string.update_allow_install, UpdateEvent.OpenInstallSettings, isPrimary = true),
                    ),
                showPermissionDialog = showPermissionDialog,
                autoCheck = autoCheck,
                summaryFlag = true,
            )
        }

        is UpdateState.Installing -> {
            UpdateUi(
                status = StatusText(R.string.update_installing),
                progress = Progress.Indeterminate,
                autoCheck = autoCheck,
                summaryFlag = true,
            )
        }

        is UpdateState.AwaitingConfirmation -> {
            UpdateUi(
                status = StatusText(R.string.update_awaiting),
                bigButtons =
                    listOf(
                        UiButton(R.string.update_continue, UpdateEvent.Continue, isPrimary = true),
                    ),
                autoCheck = autoCheck,
                summaryFlag = true,
            )
        }

        is UpdateState.Failed -> {
            val needed =
                if (state.failure is UpdateFailure.InsufficientStorage && state.candidate != null) {
                    STORAGE_MULTIPLIER * state.candidate.asset.sizeBytes + INSTALL_STORAGE_MARGIN_BYTES
                } else {
                    0L
                }
            UpdateUi(
                status = failureMessage(state.failure, needed),
                bigButtons = buttonsForFailed(state.failure, state.candidate),
                autoCheck = autoCheck,
                summaryFlag = false,
            )
        }

        is UpdateState.Unsupported -> {
            UpdateUi(
                status = StatusText(R.string.update_unsupported),
                autoCheck = autoCheck,
                summaryFlag = false,
            )
        }
    }

@HiltViewModel
class UpdateViewModel(
    private val updateManager: UpdateManager,
    private val updatePrefsRepository: UpdatePrefsRepository,
    private val packageName: String = "com.nexus.stream",
    private val clock: () -> Long = { System.currentTimeMillis() },
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.Main,
) : ViewModel() {
    @Inject
    constructor(
        updateManager: UpdateManager,
        updatePrefsRepository: UpdatePrefsRepository,
        @ApplicationContext context: Context,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ) : this(
        updateManager = updateManager,
        updatePrefsRepository = updatePrefsRepository,
        packageName = context.packageName,
        clock = { System.currentTimeMillis() },
        ioDispatcher = ioDispatcher,
    )

    private val _effects = MutableSharedFlow<UpdateUiEffect>(extraBufferCapacity = 16)
    val effects: Flow<UpdateUiEffect> = _effects.asSharedFlow()

    private val showPermissionDialogState = MutableStateFlow(false)
    private var lastState: UpdateState? = null

    private val _uiState = MutableStateFlow(updateUiOf(UpdateState.Idle(null)))
    val uiState: StateFlow<UpdateUi> = _uiState.asStateFlow()

    init {
        viewModelScope.launch(ioDispatcher) {
            updateManager.effects.collect { effect ->
                when (effect) {
                    is UpdateEffect.OpenUrl -> _effects.emit(UpdateUiEffect.OpenUrl(effect.url))
                    is UpdateEffect.Updated -> _effects.emit(UpdateUiEffect.ShowUpdated(effect.versionName))
                }
            }
        }

        viewModelScope.launch(ioDispatcher) {
            combine(
                updateManager.state,
                updatePrefsRepository.autoCheck,
                showPermissionDialogState,
            ) { state, autoCheck, showPermDialog ->
                handleStateTransition(state)
                val dialogToShow = showPermissionDialogState.value && state is UpdateState.NeedsInstallPermission
                updateUiOf(
                    state = state,
                    now = clock(),
                    autoCheck = autoCheck,
                    showPermissionDialog = dialogToShow,
                )
            }.collect { ui ->
                _uiState.value = ui
            }
        }
    }

    private fun handleStateTransition(newState: UpdateState) {
        if (newState is UpdateState.NeedsInstallPermission && lastState !is UpdateState.NeedsInstallPermission) {
            showPermissionDialogState.value = true
        } else if (newState !is UpdateState.NeedsInstallPermission) {
            showPermissionDialogState.value = false
        }
        lastState = newState
    }

    fun onEvent(event: UpdateEvent) {
        when (event) {
            is UpdateEvent.CheckNow -> handleCheckNow()
            is UpdateEvent.Download -> updateManager.confirmDownload(allowMetered = false)
            is UpdateEvent.ConfirmMetered -> updateManager.confirmDownload(allowMetered = true)
            is UpdateEvent.DismissMetered -> updateManager.cancelDownload()
            is UpdateEvent.Cancel -> updateManager.cancelDownload()
            is UpdateEvent.Install -> updateManager.install()
            is UpdateEvent.Continue -> updateManager.continueConfirmation()
            is UpdateEvent.OpenInstallSettings -> handleOpenInstallSettings()
            is UpdateEvent.DismissPermissionDialog -> showPermissionDialogState.value = false
            is UpdateEvent.DismissFailure -> updateManager.dismissFailure()
            is UpdateEvent.OpenRelease -> updateManager.openReleasePage()
            is UpdateEvent.SetAutoCheck -> handleSetAutoCheck(event.enabled)
            is UpdateEvent.Resumed -> handleResumed()
        }
    }

    private fun handleCheckNow() {
        val current = updateManager.state.value
        val isBusy =
            current is UpdateState.Checking ||
                current is UpdateState.Downloading ||
                current is UpdateState.Verifying ||
                current is UpdateState.Installing
        if (!isBusy) {
            updateManager.checkNow()
        }
    }

    private fun handleOpenInstallSettings() {
        viewModelScope.launch(ioDispatcher) {
            _effects.emit(UpdateUiEffect.OpenInstallPermissionSettings(packageName))
        }
    }

    private fun handleSetAutoCheck(enabled: Boolean) {
        viewModelScope.launch(ioDispatcher) {
            updatePrefsRepository.setAutoCheck(enabled)
        }
    }

    private fun handleResumed() {
        if (updateManager.state.value is UpdateState.NeedsInstallPermission) {
            updateManager.onInstallPermissionResult()
        }
    }
}
