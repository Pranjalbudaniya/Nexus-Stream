package com.nexus.stream.presentation.feature.library

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexus.stream.R
import com.nexus.stream.domain.model.AppError
import com.nexus.stream.presentation.common.toUserMessage
import com.nexus.stream.presentation.navigation.TopLevelDestination
import com.nexus.stream.presentation.theme.NexusTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

@Composable
fun LibraryScreen(
    onItemClick: (type: String, id: String) -> Unit = { _, _ -> },
    onNavigateToDiscover: () -> Unit = {},
    reselectEvents: Flow<TopLevelDestination> = emptyFlow(),
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val watchlistGridState = rememberLazyGridState()
    val historyGridState = rememberLazyGridState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(viewModel.effects) {
        viewModel.effects.collect { effect ->
            when (effect) {
                LibraryEffect.HistoryRemoved -> {
                    snackbarHostState.showSnackbar(
                        message = context.getString(R.string.library_history_removed_snackbar),
                    )
                }

                LibraryEffect.HistoryCleared -> {
                    snackbarHostState.showSnackbar(
                        message = context.getString(R.string.library_history_cleared_snackbar),
                    )
                }
            }
        }
    }

    LaunchedEffect(reselectEvents) {
        reselectEvents.collect {
            when (uiState.tab) {
                LibraryTab.WATCHLIST -> watchlistGridState.animateScrollToItem(0)
                LibraryTab.HISTORY -> historyGridState.animateScrollToItem(0)
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        Column(
            modifier =
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(top = statusBarTop),
        ) {
            LibraryTabRow(
                selectedTab = uiState.tab,
                onSelectTab = { viewModel.onEvent(LibraryEvent.SelectTab(it)) },
            )
            when (uiState.tab) {
                LibraryTab.WATCHLIST -> {
                    WatchlistContent(
                        ui = uiState.watchlist,
                        gridState = watchlistGridState,
                        onItemClick = onItemClick,
                        onEvent = viewModel::onEvent,
                        onNavigateToDiscover = onNavigateToDiscover,
                        modifier = Modifier.weight(1f),
                    )
                }

                LibraryTab.HISTORY -> {
                    HistoryContent(
                        ui = uiState.history,
                        gridState = historyGridState,
                        onItemClick = onItemClick,
                        onEvent = viewModel::onEvent,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }

    uiState.pendingRemove?.let { entry ->
        RemoveFromHistoryDialog(
            entry = entry,
            onConfirm = { viewModel.onEvent(LibraryEvent.ConfirmRemoveFromHistory) },
            onDismiss = { viewModel.onEvent(LibraryEvent.DismissRemove) },
        )
    }

    if (uiState.pendingClear) {
        ClearHistoryDialog(
            onConfirm = { viewModel.onEvent(LibraryEvent.ConfirmClear) },
            onDismiss = { viewModel.onEvent(LibraryEvent.DismissClear) },
        )
    }
}

@Composable
internal fun WatchlistContent(
    ui: WatchlistUi,
    gridState: LazyGridState,
    onItemClick: (type: String, id: String) -> Unit,
    onEvent: (LibraryEvent) -> Unit,
    onNavigateToDiscover: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        if (ui.statusOptions.isNotEmpty() || ui.typeOptions.isNotEmpty()) {
            WatchlistControlsRow(
                ui = ui,
                onEvent = onEvent,
            )
        }
        when (val phase = ui.phase) {
            is LibraryPhase.Loading -> {
                LibrarySkeletonGrid()
            }

            is LibraryPhase.Empty -> {
                LibraryEmptyState(
                    title = stringResource(R.string.library_watchlist_empty_title),
                    body = stringResource(R.string.library_watchlist_empty_body),
                    actionText = stringResource(R.string.library_watchlist_empty_action),
                    onAction = onNavigateToDiscover,
                    isOutlinedAction = false,
                )
            }

            is LibraryPhase.FilteredEmpty -> {
                LibraryEmptyState(
                    title = stringResource(R.string.library_watchlist_filtered_empty_title),
                    body = stringResource(R.string.library_watchlist_filtered_empty_body),
                    actionText = stringResource(R.string.library_watchlist_filtered_empty_action),
                    onAction = { onEvent(LibraryEvent.ClearWatchlistFilters) },
                    isOutlinedAction = true,
                )
            }

            is LibraryPhase.Error -> {
                val errorText = errorMessageText(phase.error)
                LibraryEmptyState(
                    title = stringResource(R.string.home_offline_banner),
                    body = errorText,
                    actionText = stringResource(R.string.retry),
                    onAction = { onEvent(LibraryEvent.Retry) },
                    isOutlinedAction = false,
                )
            }

            is LibraryPhase.Content -> {
                WatchlistGrid(
                    cards = ui.cards,
                    gridState = gridState,
                    onItemClick = onItemClick,
                )
            }
        }
    }
}

@Composable
internal fun HistoryContent(
    ui: HistoryUi,
    gridState: LazyGridState,
    onItemClick: (type: String, id: String) -> Unit,
    onEvent: (LibraryEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasEntries = ui.phase is LibraryPhase.Content || ui.phase is LibraryPhase.FilteredEmpty
    Column(modifier = modifier.fillMaxSize()) {
        if (ui.typeOptions.isNotEmpty() || hasEntries) {
            HistoryControlsRow(
                ui = ui,
                hasEntries = hasEntries,
                onEvent = onEvent,
            )
        }
        when (val phase = ui.phase) {
            is LibraryPhase.Loading -> {
                LibrarySkeletonGrid()
            }

            is LibraryPhase.Empty -> {
                LibraryEmptyState(
                    title = stringResource(R.string.library_history_empty_title),
                    body = stringResource(R.string.library_history_empty_body),
                )
            }

            is LibraryPhase.FilteredEmpty -> {
                LibraryEmptyState(
                    title = stringResource(R.string.library_history_filtered_empty_title),
                    body = stringResource(R.string.library_history_filtered_empty_body),
                    actionText = stringResource(R.string.library_history_filtered_empty_action),
                    onAction = { onEvent(LibraryEvent.ClearHistoryFilters) },
                    isOutlinedAction = true,
                )
            }

            is LibraryPhase.Error -> {
                val errorText = errorMessageText(phase.error)
                LibraryEmptyState(
                    title = stringResource(R.string.home_offline_banner),
                    body = errorText,
                    actionText = stringResource(R.string.retry),
                    onAction = { onEvent(LibraryEvent.Retry) },
                    isOutlinedAction = false,
                )
            }

            is LibraryPhase.Content -> {
                HistoryGrid(
                    sections = ui.sections,
                    truncated = ui.truncated,
                    gridState = gridState,
                    onItemClick = onItemClick,
                    onEvent = onEvent,
                )
            }
        }
    }
}

@Composable
private fun errorMessageText(error: AppError): String {
    val userMsg = error.toUserMessage()
    return if (userMsg.formatArgs.isEmpty()) {
        stringResource(userMsg.resId)
    } else {
        stringResource(userMsg.resId, userMsg.formatArgs[0])
    }
}

@Preview(name = "LibraryScreen - Light")
@Preview(name = "LibraryScreen - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun LibraryScreenPreview() {
    NexusTheme {
        LibraryScreen()
    }
}
