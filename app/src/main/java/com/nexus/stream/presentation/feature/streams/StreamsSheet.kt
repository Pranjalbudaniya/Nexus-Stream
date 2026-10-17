package com.nexus.stream.presentation.feature.streams

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexus.stream.R
import com.nexus.stream.domain.model.TypeFilter
import com.nexus.stream.presentation.common.NexusButton
import com.nexus.stream.presentation.common.NexusCard
import com.nexus.stream.presentation.common.NexusIconButton
import com.nexus.stream.presentation.common.NexusTextButton
import com.nexus.stream.presentation.common.PlaybackRequest
import com.nexus.stream.presentation.theme.NexusTheme
import kotlinx.coroutines.launch

const val STREAMS_SHEET_GESTURES_ENABLED = true
private const val SKELETON_COUNT = 4
private const val SHEET_MAX_HEIGHT_FRACTION = 0.9f
private const val SERIES_EPISODE_PARTS_MIN = 3

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreamsSheet(
    request: PlaybackRequest,
    onDismiss: () -> Unit,
    onNavigateToStore: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StreamsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(request) {
        viewModel.onEvent(StreamsEvent.SetRequest(request))
    }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is StreamsEffect.OpenVideo -> {
                    ExternalActions.openVideo(
                        context = context,
                        url = effect.url,
                        title = effect.title,
                        headers = effect.headers,
                        onError = { resId ->
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(context.getString(resId))
                            }
                        },
                    )
                }

                is StreamsEffect.OpenMagnet -> {
                    ExternalActions.openMagnet(
                        context = context,
                        magnet = effect.magnet,
                        onError = { resId ->
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(context.getString(resId))
                            }
                        },
                    )
                }

                is StreamsEffect.OpenExternalUrl -> {
                    ExternalActions.openLink(
                        context = context,
                        url = effect.url,
                        onError = { resId ->
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(context.getString(resId))
                            }
                        },
                    )
                }

                is StreamsEffect.CopyText -> {
                    ExternalActions.copy(
                        context = context,
                        text = effect.text,
                        onSuccess = { resId ->
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(context.getString(resId))
                            }
                        },
                    )
                }

                is StreamsEffect.ShowMessage -> {
                    val msg = formatStreamsMessage(context, effect.messageRes, effect.formatArgs)
                    snackbarHostState.showSnackbar(msg)
                }

                is StreamsEffect.NavigateToStore -> {
                    onNavigateToStore(effect.category)
                }

                StreamsEffect.DismissSheet -> {
                    onDismiss()
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            viewModel.onEvent(StreamsEvent.Dismiss)
            onDismiss()
        },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        sheetGesturesEnabled = STREAMS_SHEET_GESTURES_ENABLED,
        modifier = modifier,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(SHEET_MAX_HEIGHT_FRACTION),
        ) {
            StreamsSheetContent(
                request = request,
                uiState = uiState,
                onEvent = viewModel::onEvent,
                onDismiss = {
                    viewModel.onEvent(StreamsEvent.Dismiss)
                    onDismiss()
                },
            )

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter),
            )

            uiState.detailsStream?.let { stream ->
                StreamDetailsDialog(
                    stream = stream,
                    onDismiss = { viewModel.onEvent(StreamsEvent.DismissDetails) },
                )
            }
        }
    }
}

@Composable
private fun StreamsSheetContent(
    request: PlaybackRequest,
    uiState: StreamsUiState,
    onEvent: (StreamsEvent) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = NexusTheme.spacing

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        StreamsSheetHeader(
            request = request,
            onRefresh = { onEvent(StreamsEvent.Refresh) },
            onDismiss = onDismiss,
        )

        StreamsStatusRow(
            uiState = uiState,
            onRetryFailed = { onEvent(StreamsEvent.RetryFailed) },
            onClearFilters = { onEvent(StreamsEvent.ClearFilters) },
        )

        FilterBar(
            uiState = uiState,
            onEvent = onEvent,
        )

        StreamsListContent(uiState = uiState, onEvent = onEvent)
    }
}

@Composable
private fun StreamsSheetHeader(
    request: PlaybackRequest,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val seriesEpisodeLabel = parseSeriesEpisode(request.videoId)

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.streams_title, request.title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (seriesEpisodeLabel != null) {
                Text(
                    text = seriesEpisodeLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            NexusIconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.addon_refresh),
                )
            }
            if (!STREAMS_SHEET_GESTURES_ENABLED) {
                NexusTextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.btn_close))
                }
            }
        }
    }
}

@Composable
private fun StreamsStatusRow(
    uiState: StreamsUiState,
    onRetryFailed: () -> Unit,
    onClearFilters: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (uiState.total == 0) return

    val isFilterActive =
        uiState.allStreams.isNotEmpty() &&
            (
                uiState.selectedResolution != null ||
                    uiState.selectedAddon != null ||
                    uiState.selectedLanguage != null ||
                    uiState.selectedType != TypeFilter.ALL ||
                    uiState.cachedOnly
            )

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val statusText =
            if (isFilterActive) {
                stringResource(
                    R.string.streams_showing_count,
                    uiState.filteredStreams.size,
                    uiState.allStreams.size,
                )
            } else if (!uiState.isDone) {
                stringResource(R.string.streams_searching_progress, uiState.answered, uiState.total)
            } else if (uiState.failures.isNotEmpty()) {
                stringResource(R.string.streams_answered_with_failures, uiState.answered, uiState.failures.size)
            } else {
                stringResource(R.string.streams_answered_summary, uiState.answered)
            }

        Text(
            text = statusText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isFilterActive) {
                NexusTextButton(onClick = onClearFilters) {
                    Text(stringResource(R.string.streams_clear_filters))
                }
            }
            if (uiState.isDone && uiState.failures.isNotEmpty()) {
                NexusTextButton(onClick = onRetryFailed) {
                    Text(stringResource(R.string.streams_retry_failed))
                }
            }
        }
    }
}

@Composable
private fun StreamsListContent(
    uiState: StreamsUiState,
    onEvent: (StreamsEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = NexusTheme.spacing

    if (uiState.total == 0 && uiState.isDone) {
        StreamsEmptyNoAddons(onFindStreams = { onEvent(StreamsEvent.FindStreams) }, modifier = modifier)
    } else if (uiState.isLoading && uiState.allStreams.isEmpty()) {
        Column(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            repeat(SKELETON_COUNT) {
                StreamSkeletonRow()
            }
        }
    } else if (uiState.allStreams.isEmpty() && uiState.isDone) {
        StreamsEmptyNoStreams(onRetry = { onEvent(StreamsEvent.Refresh) }, modifier = modifier)
    } else if (uiState.filteredStreams.isEmpty() && uiState.allStreams.isNotEmpty()) {
        Column(
            modifier = modifier.fillMaxSize().padding(top = spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Text(
                text = stringResource(R.string.streams_empty_filtered),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            NexusButton(onClick = { onEvent(StreamsEvent.ClearFilters) }) {
                Text(stringResource(R.string.streams_clear_filters))
            }
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            items(uiState.filteredStreams, key = { it.key }) { stream ->
                StreamRow(
                    stream = stream,
                    onClick = { onEvent(StreamsEvent.PlayStream(stream)) },
                    onShowDetails = { onEvent(StreamsEvent.ShowDetails(stream)) },
                    onCopyFileName = { onEvent(StreamsEvent.CopyFileName(it)) },
                    onCopyLink = { onEvent(StreamsEvent.CopyLink(stream)) },
                    onCopyMagnet = { onEvent(StreamsEvent.CopyMagnet(stream)) },
                    onOpenMagnet = { onEvent(StreamsEvent.OpenMagnetExternal(stream)) },
                    onOpenLink = { onEvent(StreamsEvent.OpenLinkExternal(stream)) },
                )
            }
        }
    }
}

@Composable
private fun StreamsEmptyNoAddons(
    onFindStreams: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = NexusTheme.spacing
    NexusCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.streams_empty_no_addons),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            NexusButton(onClick = onFindStreams) {
                Text(stringResource(R.string.streams_empty_find_streams))
            }
        }
    }
}

@Composable
private fun StreamsEmptyNoStreams(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = NexusTheme.spacing
    NexusCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.streams_empty_no_streams),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            NexusButton(onClick = onRetry) {
                Text(stringResource(R.string.addon_manager_retry))
            }
        }
    }
}

private fun parseSeriesEpisode(videoId: String): String? {
    val parts = videoId.split(":")
    if (parts.size >= SERIES_EPISODE_PARTS_MIN) {
        val season = parts[1].toIntOrNull()
        val episode = parts[2].toIntOrNull()
        if (season != null && episode != null) {
            return "S$season E$episode"
        }
    }
    return null
}

private fun formatStreamsMessage(
    context: Context,
    resId: Int,
    args: List<Any>,
): String =
    if (args.isEmpty()) {
        context.getString(resId)
    } else if (args.size == 1) {
        context.getString(resId, args[0])
    } else {
        context.getString(resId, args[0], args[1])
    }
