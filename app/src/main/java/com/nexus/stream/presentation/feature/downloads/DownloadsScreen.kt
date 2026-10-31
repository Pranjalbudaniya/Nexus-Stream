package com.nexus.stream.presentation.feature.downloads

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexus.stream.R
import com.nexus.stream.domain.model.DownloadFailure
import com.nexus.stream.domain.model.DownloadItem
import com.nexus.stream.domain.model.DownloadKind
import com.nexus.stream.domain.model.DownloadLive
import com.nexus.stream.domain.model.DownloadPhase
import com.nexus.stream.domain.model.DownloadSource
import com.nexus.stream.domain.model.DownloadState
import com.nexus.stream.domain.usecase.DownloadSection
import com.nexus.stream.domain.usecase.DownloadSectionKind
import com.nexus.stream.domain.usecase.PlayDownloaded
import com.nexus.stream.domain.usecase.PlayResult
import com.nexus.stream.domain.usecase.StorageSummary
import com.nexus.stream.presentation.common.LocalPlayback
import com.nexus.stream.presentation.common.rememberNotificationPermissionGate
import com.nexus.stream.presentation.feature.player.PlaybackSessionHolder
import com.nexus.stream.presentation.navigation.TopLevelDestination
import com.nexus.stream.presentation.theme.NexusTheme
import dagger.hilt.EntryPoint
import dagger.hilt.EntryPoints
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.time.Instant

@Composable
fun DownloadsScreen(
    onItemClick: (type: String, id: String) -> Unit = { _, _ -> },
    onNavigateToDiscover: () -> Unit = {},
    onPlay: (DownloadItem) -> Unit = {},
    onOpenSettings: () -> Unit = {},
    reselectEvents: Flow<TopLevelDestination>? = null,
    playbackSessionHolder: PlaybackSessionHolder? = null,
    modifier: Modifier = Modifier,
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val localPlayback = LocalPlayback.current
    val listState = rememberLazyListState()
    val permissionGate = rememberNotificationPermissionGate()

    val entryPoint =
        remember(context) {
            runCatching {
                EntryPoints.get(context.applicationContext, DownloadsEntryPoint::class.java)
            }.getOrNull()
        }

    LaunchedEffect(reselectEvents) {
        reselectEvents?.collect {
            listState.animateScrollToItem(0)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is DownloadsEffect.ShowMessage -> {
                    snackbarHostState.showSnackbar(context.getString(effect.messageRes))
                }

                is DownloadsEffect.Play -> {
                    onPlay(effect.item)
                    val playResult = entryPoint?.playDownloaded()?.invoke(effect.item)
                    if (playResult is PlayResult.Ready) {
                        playbackSessionHolder?.start(playResult.selection)
                    }
                }

                is DownloadsEffect.OpenDetail -> {
                    onItemClick(effect.type, effect.metaId)
                }

                is DownloadsEffect.OpenDownloadSheet -> {
                    localPlayback.open(effect.request.copy(replaceDownloadId = effect.replaceDownloadId))
                }

                DownloadsEffect.OpenSettings -> {
                    onOpenSettings()
                }

                DownloadsEffect.RequestNotificationPermission -> {
                    permissionGate {}
                }
            }
        }
    }

    DownloadsScreenContent(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        onNavigateToDiscover = onNavigateToDiscover,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
        listState = listState,
    )
}

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface DownloadsEntryPoint {
    fun playDownloaded(): PlayDownloaded
}

@Composable
fun DownloadsScreenContent(
    uiState: DownloadsUiState,
    onEvent: (DownloadsEvent) -> Unit,
    onNavigateToDiscover: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    listState: androidx.compose.foundation.lazy.LazyListState = rememberLazyListState(),
) {
    val spacing = NexusTheme.spacing

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier.fillMaxSize(),
    ) { padding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .statusBarsPadding(),
        ) {
            when {
                uiState.isLoading -> {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(horizontal = spacing.lg, vertical = spacing.md),
                    ) {
                        DownloadsSkeleton()
                    }
                }

                uiState.isEmpty -> {
                    DownloadsEmptyState(
                        onNavigateToDiscover = onNavigateToDiscover,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding =
                            PaddingValues(
                                start = spacing.lg,
                                end = spacing.lg,
                                top = spacing.sm,
                                bottom = spacing.xl,
                            ),
                        verticalArrangement = Arrangement.spacedBy(spacing.sm),
                    ) {
                        // 1. Storage summary card
                        uiState.storageSummary?.let { summary ->
                            item(key = "storage_summary") {
                                StorageSummaryCard(
                                    summary = summary,
                                    canPauseAll = uiState.canPauseAll,
                                    canResumeAll = uiState.canResumeAll,
                                    canDeleteCompleted = uiState.canDeleteCompleted,
                                    onPauseAll = { onEvent(DownloadsEvent.PauseAll) },
                                    onResumeAll = { onEvent(DownloadsEvent.ResumeAll) },
                                    onDeleteAll = { onEvent(DownloadsEvent.RequestDeleteAll) },
                                )
                            }
                        }

                        // 2. Wi-Fi banner
                        if (uiState.showWifiBanner) {
                            item(key = "wifi_banner") {
                                WifiBanner(
                                    onUseMobileData = { onEvent(DownloadsEvent.UseMobileData) },
                                )
                            }
                        }

                        // 3. Notification banner
                        if (uiState.showNotificationsBanner) {
                            item(key = "notifications_banner") {
                                NotificationBanner(
                                    onTurnOn = { onEvent(DownloadsEvent.DismissNotificationsBanner) },
                                )
                            }
                        }

                        // 4. Download sections
                        uiState.sections.forEach { section ->
                            item(key = "section_header_${section.kind.name}") {
                                val headerRes =
                                    when (section.kind) {
                                        DownloadSectionKind.IN_PROGRESS -> R.string.downloads_section_in_progress
                                        DownloadSectionKind.PROBLEMS -> R.string.downloads_section_problems
                                        DownloadSectionKind.READY -> R.string.downloads_section_ready
                                    }
                                Text(
                                    text = stringResource(headerRes),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(top = spacing.sm, bottom = spacing.xs)
                                            .semantics { heading() },
                                )
                            }

                            items(section.items, key = { it.id }) { item ->
                                val live = uiState.live[item.id]
                                DownloadRow(
                                    item = item,
                                    live = live,
                                    onTapRow = { onEvent(DownloadsEvent.TapRow(it)) },
                                    onPause = { onEvent(DownloadsEvent.Pause(it)) },
                                    onResume = { onEvent(DownloadsEvent.Resume(it)) },
                                    onRetry = { onEvent(DownloadsEvent.Retry(it)) },
                                    onFindNewLink = { onEvent(DownloadsEvent.FindNewLink(it)) },
                                    onRequestCancel = { onEvent(DownloadsEvent.RequestCancel(it)) },
                                    onRequestDelete = { onEvent(DownloadsEvent.RequestDelete(it)) },
                                    onChooseFile = { onEvent(DownloadsEvent.ChooseFile(it)) },
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }
                    }
                }
            }

            DownloadsDialogs(
                confirmDialog = uiState.confirmDialog,
                filePickDialog = uiState.filePickDialog,
                onEvent = onEvent,
            )
        }
    }
}

@Composable
private fun DownloadsDialogs(
    confirmDialog: ConfirmDialog?,
    filePickDialog: TorrentFilePickState?,
    onEvent: (DownloadsEvent) -> Unit,
) {
    filePickDialog?.let { dialogState ->
        TorrentFilePickDialog(
            candidates = dialogState.candidates,
            onSelect = { index -> onEvent(DownloadsEvent.SelectFile(index, dialogState.downloadId)) },
            onDismiss = { onEvent(DownloadsEvent.DismissPick) },
        )
    }

    when (confirmDialog) {
        is ConfirmDialog.Cancel -> {
            ConfirmCancelDialog(
                onConfirm = { onEvent(DownloadsEvent.ConfirmCancel(confirmDialog.id)) },
                onDismiss = { onEvent(DownloadsEvent.DismissConfirm) },
            )
        }

        is ConfirmDialog.Delete -> {
            ConfirmDeleteDialog(
                onConfirm = { onEvent(DownloadsEvent.ConfirmDelete(confirmDialog.id)) },
                onDismiss = { onEvent(DownloadsEvent.DismissConfirm) },
            )
        }

        ConfirmDialog.DeleteAll -> {
            ConfirmDeleteAllDialog(
                onConfirm = { onEvent(DownloadsEvent.ConfirmDeleteAll) },
                onDismiss = { onEvent(DownloadsEvent.DismissConfirm) },
            )
        }

        null -> {}
    }
}

// Previews
private fun sampleItem(
    id: String,
    title: String,
    state: DownloadState,
    failure: DownloadFailure? = null,
    kind: DownloadKind = DownloadKind.HTTP,
): DownloadItem =
    DownloadItem(
        id = id,
        kind = kind,
        source =
            if (kind == DownloadKind.TORRENT) {
                DownloadSource.Torrent("0123456789abcdef0123456789abcdef01234567", emptyList())
            } else {
                DownloadSource.Http("https://example.com/video.mp4")
            },
        metaId = "m1",
        videoId = "v1",
        type = "movie",
        title = title,
        fileName = "$id.mp4",
        state = state,
        totalBytes = 1_000_000_000L,
        downloadedBytes = if (state == DownloadState.COMPLETED) 1_000_000_000L else 420_000_000L,
        failure = failure,
        createdAt = 100L,
        updatedAt = 100L,
        completedAt = if (state == DownloadState.COMPLETED) 100L else null,
    )

@Preview(name = "Downloads - Empty - Light")
@Preview(name = "Downloads - Empty - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun DownloadsScreenEmptyPreview() {
    NexusTheme {
        DownloadsScreenContent(
            uiState = DownloadsUiState(isEmpty = true, isLoading = false),
            onEvent = {},
            onNavigateToDiscover = {},
            snackbarHostState = remember { SnackbarHostState() },
        )
    }
}

@Preview(name = "Downloads - In Progress - Light")
@Composable
internal fun DownloadsScreenInProgressPreviewLight() {
    val items =
        listOf(
            sampleItem("1", "Dune: Part Two", DownloadState.DOWNLOADING),
            sampleItem("2", "Blade Runner 2049", DownloadState.QUEUED),
            sampleItem("3", "Arrival", DownloadState.WAITING_FOR_WIFI),
            sampleItem("4", "Interstellar", DownloadState.PAUSED),
            sampleItem("5", "Alien", DownloadState.WAITING_FOR_NETWORK),
        )
    val live = mapOf("1" to DownloadLive(bytes = 420_000_000L, speedBps = 5_200_000L, etaSeconds = 120L))
    NexusTheme {
        DownloadsScreenContent(
            uiState =
                DownloadsUiState(
                    sections = listOf(DownloadSection(DownloadSectionKind.IN_PROGRESS, items)),
                    live = live,
                    storageSummary =
                        StorageSummary(
                            usedBytes = 420_000_000L,
                            freeBytes = 44_000_000_000L,
                            low = false,
                        ),
                    canPauseAll = true,
                    isLoading = false,
                ),
            onEvent = {},
            onNavigateToDiscover = {},
            snackbarHostState = remember { SnackbarHostState() },
        )
    }
}

@Preview(name = "Downloads - Problems - Light")
@Composable
internal fun DownloadsScreenProblemsPreviewLight() {
    val items =
        listOf(
            sampleItem(
                id = "1",
                title = "Expired Movie",
                state = DownloadState.FAILED,
                failure = DownloadFailure.LINK_EXPIRED,
            ),
        )
    NexusTheme {
        DownloadsScreenContent(
            uiState =
                DownloadsUiState(
                    sections = listOf(DownloadSection(DownloadSectionKind.PROBLEMS, items)),
                    isLoading = false,
                ),
            onEvent = {},
            onNavigateToDiscover = {},
            snackbarHostState = remember { SnackbarHostState() },
        )
    }
}

@Preview(name = "Downloads - Ready - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun DownloadsScreenReadyPreviewDark() {
    val items =
        listOf(
            sampleItem(
                id = "1",
                title = "Dune (2021)",
                state = DownloadState.COMPLETED,
            ),
        )
    NexusTheme {
        DownloadsScreenContent(
            uiState =
                DownloadsUiState(
                    sections = listOf(DownloadSection(DownloadSectionKind.READY, items)),
                    storageSummary =
                        StorageSummary(
                            usedBytes = 1_400_000_000L,
                            freeBytes = 30_000_000_000L,
                            low = false,
                        ),
                    canDeleteCompleted = true,
                    isLoading = false,
                ),
            onEvent = {},
            onNavigateToDiscover = {},
            snackbarHostState = remember { SnackbarHostState() },
        )
    }
}

@Preview(name = "Downloads - Confirm Dialogs")
@Composable
internal fun DownloadsScreenDialogsPreview() {
    NexusTheme {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ConfirmCancelDialog(onConfirm = {}, onDismiss = {})
            ConfirmDeleteDialog(onConfirm = {}, onDismiss = {})
            ConfirmDeleteAllDialog(onConfirm = {}, onDismiss = {})
        }
    }
}

@Preview(name = "Downloads - Torrent Statuses - Light")
@Preview(name = "Downloads - Torrent Statuses - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun DownloadsScreenTorrentStatusesPreview() {
    val items =
        listOf(
            sampleItem("1", "Connecting Torrent", DownloadState.DOWNLOADING, kind = DownloadKind.TORRENT),
            sampleItem("2", "Metadata Torrent", DownloadState.DOWNLOADING, kind = DownloadKind.TORRENT),
            sampleItem("3", "Checking Torrent", DownloadState.DOWNLOADING, kind = DownloadKind.TORRENT),
            sampleItem("4", "Transferring Torrent", DownloadState.DOWNLOADING, kind = DownloadKind.TORRENT),
            sampleItem("5", "Stalled Torrent", DownloadState.DOWNLOADING, kind = DownloadKind.TORRENT),
            sampleItem("6", "Season Pack Torrent", DownloadState.NEEDS_FILE_PICK, kind = DownloadKind.TORRENT),
        )
    val problems =
        listOf(
            sampleItem(
                id = "7",
                title = "Dead Torrent",
                state = DownloadState.FAILED,
                failure = DownloadFailure.TORRENT_NO_PEERS,
                kind = DownloadKind.TORRENT,
            ),
        )
    val live =
        mapOf(
            "1" to DownloadLive(bytes = 0L, speedBps = 0L, peers = 0, phase = DownloadPhase.CONNECTING),
            "2" to DownloadLive(bytes = 0L, speedBps = 0L, peers = 0, phase = DownloadPhase.FETCHING_METADATA),
            "3" to DownloadLive(bytes = 400_000_000L, speedBps = 0L, peers = 0, phase = DownloadPhase.CHECKING_FILES),
            "4" to
                DownloadLive(
                    bytes = 420_000_000L,
                    speedBps = 3_500_000L,
                    etaSeconds = 240L,
                    peers = 12,
                    phase = DownloadPhase.TRANSFERRING,
                ),
            "5" to DownloadLive(bytes = 100_000_000L, speedBps = 0L, peers = 0, phase = DownloadPhase.STALLED),
        )
    NexusTheme {
        DownloadsScreenContent(
            uiState =
                DownloadsUiState(
                    sections =
                        listOf(
                            DownloadSection(DownloadSectionKind.IN_PROGRESS, items),
                            DownloadSection(DownloadSectionKind.PROBLEMS, problems),
                        ),
                    live = live,
                    canPauseAll = true,
                    isLoading = false,
                ),
            onEvent = {},
            onNavigateToDiscover = {},
            snackbarHostState = remember { SnackbarHostState() },
        )
    }
}
