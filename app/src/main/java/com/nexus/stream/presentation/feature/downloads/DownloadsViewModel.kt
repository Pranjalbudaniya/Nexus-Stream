package com.nexus.stream.presentation.feature.downloads

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexus.stream.R
import com.nexus.stream.domain.model.DownloadEffect
import com.nexus.stream.domain.model.DownloadItem
import com.nexus.stream.domain.model.DownloadLive
import com.nexus.stream.domain.model.DownloadState
import com.nexus.stream.domain.repository.ContentPrefsRepository
import com.nexus.stream.domain.repository.DownloadFileStore
import com.nexus.stream.domain.repository.DownloadManager
import com.nexus.stream.domain.repository.StorageInfo
import com.nexus.stream.domain.repository.TorrentFileChoice
import com.nexus.stream.domain.usecase.DownloadSection
import com.nexus.stream.domain.usecase.StatusText
import com.nexus.stream.domain.usecase.StorageSummary
import com.nexus.stream.domain.usecase.activeCount
import com.nexus.stream.domain.usecase.failureReasonRes
import com.nexus.stream.domain.usecase.galleryFailureReasonRes
import com.nexus.stream.domain.usecase.groupDownloads
import com.nexus.stream.domain.usecase.rowStatus
import com.nexus.stream.domain.usecase.storageSummary
import com.nexus.stream.presentation.common.PlaybackRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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

sealed interface ConfirmDialog {
    data class Cancel(
        val id: String,
    ) : ConfirmDialog

    data class Delete(
        val id: String,
    ) : ConfirmDialog

    data object DeleteAll : ConfirmDialog
}

data class TorrentFilePickState(
    val downloadId: String,
    val candidates: List<TorrentFileChoice>,
    val selectedIndex: Int,
)

data class DownloadsUiState(
    val sections: List<DownloadSection> = emptyList(),
    val live: Map<String, DownloadLive> = emptyMap(),
    val storageSummary: StorageSummary? = null,
    val showWifiBanner: Boolean = false,
    val showNotificationsBanner: Boolean = false,
    val confirmDialog: ConfirmDialog? = null,
    val filePickDialog: TorrentFilePickState? = null,
    val canPauseAll: Boolean = false,
    val canResumeAll: Boolean = false,
    val canDeleteCompleted: Boolean = false,
    val isEmpty: Boolean = false,
    val isLoading: Boolean = false,
) {
    fun rowStatus(item: DownloadItem): StatusText =
        com.nexus.stream.domain.usecase
            .rowStatus(item, live[item.id])

    val rowStatuses: Map<String, StatusText>
        get() = sections.flatMap { it.items }.associate { it.id to rowStatus(it) }
}

sealed interface DownloadsEvent {
    data class Pause(
        val id: String,
    ) : DownloadsEvent

    data class Resume(
        val id: String,
    ) : DownloadsEvent

    data class Retry(
        val id: String,
    ) : DownloadsEvent

    data class RequestCancel(
        val id: String,
    ) : DownloadsEvent

    data class ConfirmCancel(
        val id: String,
    ) : DownloadsEvent

    data class RequestDelete(
        val id: String,
    ) : DownloadsEvent

    data class ConfirmDelete(
        val id: String,
    ) : DownloadsEvent

    data object RequestDeleteAll : DownloadsEvent

    data object ConfirmDeleteAll : DownloadsEvent

    data object DismissConfirm : DownloadsEvent

    data object UseMobileData : DownloadsEvent

    data class Play(
        val item: DownloadItem,
    ) : DownloadsEvent

    data class TapRow(
        val item: DownloadItem,
    ) : DownloadsEvent

    data class FindNewLink(
        val item: DownloadItem,
    ) : DownloadsEvent

    data object DismissNotificationsBanner : DownloadsEvent

    data object PauseAll : DownloadsEvent

    data object ResumeAll : DownloadsEvent

    data class ChooseFile(
        val id: String,
    ) : DownloadsEvent

    data class SelectFile(
        val fileIndex: Int,
        val id: String? = null,
    ) : DownloadsEvent

    data object DismissPick : DownloadsEvent
}

sealed interface DownloadsEffect {
    data class Play(
        val item: DownloadItem,
    ) : DownloadsEffect

    data class OpenDetail(
        val type: String,
        val metaId: String,
    ) : DownloadsEffect

    data class OpenDownloadSheet(
        val request: PlaybackRequest,
        val replaceDownloadId: String?,
    ) : DownloadsEffect

    data class ShowMessage(
        @StringRes val messageRes: Int,
        val formatArgs: List<Any> = emptyList(),
    ) : DownloadsEffect

    data object OpenSettings : DownloadsEffect

    data object RequestNotificationPermission : DownloadsEffect
}

@HiltViewModel
class DownloadsViewModel(
    private val downloadManager: DownloadManager,
    private val contentPrefsRepository: ContentPrefsRepository,
    private val freeSpaceProvider: () -> Long,
    private val notificationPermissionGranted: () -> Boolean,
) : ViewModel() {
    @Inject
    constructor(
        downloadManager: DownloadManager,
        contentPrefsRepository: ContentPrefsRepository,
        fileStore: DownloadFileStore,
        storageInfo: StorageInfo,
        @ApplicationContext context: Context,
    ) : this(
        downloadManager = downloadManager,
        contentPrefsRepository = contentPrefsRepository,
        freeSpaceProvider = { storageInfo.availableBytes(fileStore.root) },
        notificationPermissionGranted = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        },
    )

    private val _uiState = MutableStateFlow(DownloadsUiState(isLoading = true))
    val uiState: StateFlow<DownloadsUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<DownloadsEffect>()
    val effects: SharedFlow<DownloadsEffect> = _effects.asSharedFlow()

    private val confirmDialogFlow = MutableStateFlow<ConfirmDialog?>(null)
    private val filePickDialogFlow = MutableStateFlow<TorrentFilePickState?>(null)
    private var notificationsBannerDismissed = false

    init {
        observeDownloads()
        observeEffects()
    }

    private fun observeDownloads() {
        viewModelScope.launch {
            combine(
                downloadManager.items,
                downloadManager.live,
                contentPrefsRepository.familyMode,
                confirmDialogFlow,
                filePickDialogFlow,
            ) { items, live, familyMode, dialog, filePick ->
                val sections = groupDownloads(items, familyMode)
                val summary = if (items.isNotEmpty()) storageSummary(items, freeSpaceProvider()) else null
                val hasWifiWait = items.any { it.state == DownloadState.WAITING_FOR_WIFI }
                val active = activeCount(items) > 0
                val showNotif = active && !notificationsBannerDismissed && !notificationPermissionGranted()

                val hasActive =
                    items.any {
                        it.state == DownloadState.DOWNLOADING ||
                            it.state == DownloadState.QUEUED ||
                            it.state == DownloadState.WAITING_FOR_WIFI ||
                            it.state == DownloadState.WAITING_FOR_NETWORK
                    }
                val hasPaused = items.any { it.state == DownloadState.PAUSED }
                val hasCompleted = items.any { it.state == DownloadState.COMPLETED }

                DownloadsUiState(
                    sections = sections,
                    live = live,
                    storageSummary = summary,
                    showWifiBanner = hasWifiWait,
                    showNotificationsBanner = showNotif,
                    confirmDialog = dialog,
                    filePickDialog = filePick,
                    canPauseAll = hasActive,
                    canResumeAll = !hasActive && hasPaused,
                    canDeleteCompleted = hasCompleted,
                    isEmpty = items.isEmpty(),
                    isLoading = false,
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    private fun observeEffects() {
        viewModelScope.launch {
            downloadManager.effects.collect { effect ->
                when (effect) {
                    is DownloadEffect.Completed -> {
                        _effects.emit(DownloadsEffect.ShowMessage(R.string.download_event_done_title))
                    }

                    is DownloadEffect.Failed -> {
                        _effects.emit(DownloadsEffect.ShowMessage(failureReasonRes(effect.failure)))
                    }

                    is DownloadEffect.GalleryFailed -> {
                        _effects.emit(
                            DownloadsEffect.ShowMessage(
                                messageRes = R.string.downloads_gallery_failed,
                                formatArgs = listOf(galleryFailureReasonRes(effect.failure)),
                            ),
                        )
                    }

                    DownloadEffect.PausedBySystem -> {
                        _effects.emit(DownloadsEffect.ShowMessage(R.string.download_event_paused_title))
                    }
                }
            }
        }
    }

    fun onEvent(event: DownloadsEvent) {
        when (event) {
            is DownloadsEvent.Pause -> downloadManager.pause(event.id)
            is DownloadsEvent.Resume -> downloadManager.resume(event.id)
            is DownloadsEvent.Retry -> downloadManager.resume(event.id)
            DownloadsEvent.PauseAll -> downloadManager.pauseAll()
            DownloadsEvent.ResumeAll -> downloadManager.resumeAll()
            DownloadsEvent.UseMobileData -> downloadManager.allowMobileData(null)
            DownloadsEvent.DismissNotificationsBanner -> handleDismissNotifications()
            is DownloadsEvent.Play -> handlePlay(event.item)
            is DownloadsEvent.TapRow -> handleTapRow(event.item)
            is DownloadsEvent.FindNewLink -> handleFindNewLink(event.item)
            else -> handleDialogEvent(event)
        }
    }

    private fun handleChooseFile(id: String) {
        val choices = downloadManager.getTorrentFileChoices(id).sortedByDescending { it.sizeBytes }
        val initialSelection = choices.firstOrNull()?.index ?: 0
        filePickDialogFlow.value =
            TorrentFilePickState(
                downloadId = id,
                candidates = choices,
                selectedIndex = initialSelection,
            )
    }

    private fun handleSelectFile(event: DownloadsEvent.SelectFile) {
        val targetId = event.id ?: filePickDialogFlow.value?.downloadId
        filePickDialogFlow.value = null
        if (targetId != null) {
            viewModelScope.launch {
                downloadManager.selectTorrentFile(targetId, event.fileIndex)
            }
        }
    }

    private fun handleDialogEvent(event: DownloadsEvent) {
        when (event) {
            is DownloadsEvent.RequestCancel -> confirmDialogFlow.value = ConfirmDialog.Cancel(event.id)
            is DownloadsEvent.RequestDelete -> confirmDialogFlow.value = ConfirmDialog.Delete(event.id)
            DownloadsEvent.RequestDeleteAll -> confirmDialogFlow.value = ConfirmDialog.DeleteAll
            DownloadsEvent.DismissConfirm -> confirmDialogFlow.value = null
            is DownloadsEvent.ChooseFile -> handleChooseFile(event.id)
            is DownloadsEvent.SelectFile -> handleSelectFile(event)
            DownloadsEvent.DismissPick -> filePickDialogFlow.value = null
            else -> handleConfirmAction(event)
        }
    }

    private fun handleConfirmAction(event: DownloadsEvent) {
        confirmDialogFlow.value = null
        viewModelScope.launch {
            when (event) {
                is DownloadsEvent.ConfirmCancel -> downloadManager.cancel(event.id)
                is DownloadsEvent.ConfirmDelete -> downloadManager.delete(event.id)
                DownloadsEvent.ConfirmDeleteAll -> downloadManager.deleteCompleted()
                else -> Unit
            }
        }
    }

    private fun handlePlay(item: DownloadItem) {
        viewModelScope.launch {
            if (downloadManager.verifyCompleted(item.id)) {
                _effects.emit(DownloadsEffect.Play(item))
            } else {
                _effects.emit(DownloadsEffect.ShowMessage(R.string.download_file_missing))
            }
        }
    }

    private fun handleTapRow(item: DownloadItem) {
        if (item.state == DownloadState.COMPLETED) {
            handlePlay(item)
        } else {
            viewModelScope.launch {
                _effects.emit(DownloadsEffect.OpenDetail(item.type, item.metaId))
            }
        }
    }

    private fun handleFindNewLink(item: DownloadItem) {
        val req =
            PlaybackRequest(
                type = item.type,
                metaId = item.metaId,
                videoId = item.videoId,
                title = item.title,
                season = item.season,
                episode = item.episode,
                posterUrl = item.posterUrl,
                metaName = item.metaName,
                downloadMode = true,
                replaceDownloadId = item.id,
            )
        viewModelScope.launch {
            _effects.emit(DownloadsEffect.OpenDownloadSheet(req, replaceDownloadId = item.id))
        }
    }

    private fun handleDismissNotifications() {
        notificationsBannerDismissed = true
        _uiState.update { it.copy(showNotificationsBanner = false) }
    }
}
