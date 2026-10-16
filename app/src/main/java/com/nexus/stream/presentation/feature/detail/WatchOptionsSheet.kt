package com.nexus.stream.presentation.feature.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.nexus.stream.R
import com.nexus.stream.domain.model.ListStatus
import com.nexus.stream.domain.usecase.WatchAction
import com.nexus.stream.presentation.common.labelResId
import com.nexus.stream.presentation.theme.NexusTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchOptionsSheet(
    title: String,
    actions: List<WatchAction>,
    onAction: (WatchAction) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    listStatus: ListStatus? = null,
    onOpenListStatusDialog: () -> Unit = {},
    sheetState: SheetState = rememberModalBottomSheetState(),
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = NexusTheme.shapes.sheet,
        modifier = modifier,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = NexusTheme.spacing.xl),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = NexusTheme.spacing.lg)
                        .padding(bottom = NexusTheme.spacing.md)
                        .semantics { heading() },
            )

            actions.forEach { action ->
                val (label, icon) = actionLabelAndIcon(action, listStatus)
                WatchOptionRow(
                    label = label,
                    icon = icon,
                    onClick = {
                        onDismissRequest()
                        if (action is WatchAction.ToggleWatchlist) {
                            onOpenListStatusDialog()
                        } else {
                            onAction(action)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun WatchOptionRow(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(onClick = onClick)
                .padding(horizontal = NexusTheme.spacing.lg, vertical = NexusTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(NexusTheme.spacing.md))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun actionLabelAndIcon(
    action: WatchAction,
    listStatus: ListStatus?,
): Pair<String, ImageVector> =
    when (action) {
        is WatchAction.ToggleWatchlist -> {
            if (listStatus == null) {
                stringResource(R.string.watch_options_add_watchlist) to Icons.Default.Add
            } else {
                stringResource(
                    R.string.watch_options_watchlist_status,
                    stringResource(listStatus.labelResId()),
                ) to Icons.Default.Check
            }
        }

        is WatchAction.SetMovieWatched -> {
            if (action.watched) {
                stringResource(R.string.watch_options_mark_watched) to Icons.Default.Check
            } else {
                stringResource(R.string.watch_options_mark_unwatched) to Icons.Default.Close
            }
        }

        is WatchAction.SetSeasonWatched -> {
            val seasonName =
                when (action.season) {
                    0 -> stringResource(R.string.detail_season_specials)
                    null -> stringResource(R.string.detail_season_episodes)
                    else -> stringResource(R.string.detail_season_label, action.season)
                }
            if (action.watched) {
                stringResource(R.string.watch_options_mark_season_watched, seasonName) to Icons.Default.Check
            } else {
                stringResource(R.string.watch_options_mark_season_unwatched, seasonName) to Icons.Default.Close
            }
        }

        is WatchAction.SetAllWatched -> {
            if (action.watched) {
                stringResource(R.string.watch_options_mark_all_watched) to Icons.Default.Check
            } else {
                stringResource(R.string.watch_options_mark_all_unwatched) to Icons.Default.Close
            }
        }

        is WatchAction.RemoveFromContinue -> {
            stringResource(R.string.watch_options_remove_continue) to Icons.Default.Delete
        }
    }
