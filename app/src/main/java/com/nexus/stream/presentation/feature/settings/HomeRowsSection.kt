package com.nexus.stream.presentation.feature.settings

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.nexus.stream.R
import com.nexus.stream.presentation.common.NexusButton
import com.nexus.stream.presentation.common.NexusIconButton
import com.nexus.stream.presentation.common.NexusOutlinedButton
import com.nexus.stream.presentation.common.NexusTextButton
import com.nexus.stream.presentation.theme.NexusTheme

@Composable
fun HomeRowsSection(
    state: HomeRowsUiState,
    onEvent: (HomeRowsEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = NexusTheme.spacing

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Text(
            text = stringResource(R.string.settings_section_home_rows_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (state.rows.isEmpty()) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = spacing.md),
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                Text(
                    text = stringResource(R.string.home_rows_empty_message),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.home_rows_empty_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                state.rows.forEach { row ->
                    ManagedRowItem(
                        row = row,
                        onEvent = onEvent,
                    )
                }
            }

            NexusOutlinedButton(
                onClick = { onEvent(HomeRowsEvent.RequestReset) },
                enabled = !state.isDefault,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.home_rows_btn_reset))
            }
        }
    }

    if (state.showResetConfirm) {
        AlertDialog(
            onDismissRequest = { onEvent(HomeRowsEvent.DismissReset) },
            title = { Text(stringResource(R.string.home_rows_reset_dialog_title)) },
            text = { Text(stringResource(R.string.home_rows_reset_dialog_body)) },
            confirmButton = {
                NexusButton(onClick = { onEvent(HomeRowsEvent.ConfirmReset) }) {
                    Text(stringResource(R.string.btn_reset))
                }
            },
            dismissButton = {
                NexusTextButton(onClick = { onEvent(HomeRowsEvent.DismissReset) }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            },
        )
    }
}

@Composable
private fun ManagedRowItem(
    row: ManagedRowUi,
    onEvent: (HomeRowsEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = NexusTheme.spacing

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = row.title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier
                    .weight(1f)
                    .padding(end = spacing.sm),
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            NexusIconButton(
                onClick = { onEvent(HomeRowsEvent.MoveUp(row.key)) },
                enabled = row.canMoveUp,
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = stringResource(R.string.home_rows_action_move_up),
                )
            }

            NexusIconButton(
                onClick = { onEvent(HomeRowsEvent.MoveDown(row.key)) },
                enabled = row.canMoveDown,
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.home_rows_action_move_down),
                )
            }

            val toggleDescription = stringResource(R.string.home_rows_action_toggle)
            Switch(
                checked = !row.hidden,
                onCheckedChange = { checked -> onEvent(HomeRowsEvent.SetHidden(row.key, !checked)) },
                modifier =
                    Modifier.semantics {
                        contentDescription = toggleDescription
                    },
            )
        }
    }
}

@Preview(name = "HomeRowsSection - 4 Rows Light")
@Composable
internal fun HomeRowsSection4RowsLightPreview() {
    NexusTheme {
        HomeRowsSection(
            state =
                HomeRowsUiState(
                    rows =
                        listOf(
                            ManagedRowUi("1", "Popular Movies", hidden = false, canMoveUp = false, canMoveDown = true),
                            ManagedRowUi("2", "Top Rated Series", hidden = false, canMoveUp = true, canMoveDown = true),
                            ManagedRowUi("3", "Anime", hidden = true, canMoveUp = true, canMoveDown = true),
                            ManagedRowUi("4", "Documentaries", hidden = false, canMoveUp = true, canMoveDown = false),
                        ),
                    isDefault = false,
                    totalCount = 4,
                    hiddenCount = 1,
                ),
            onEvent = {},
        )
    }
}

@Preview(name = "HomeRowsSection - 4 Rows Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun HomeRowsSection4RowsDarkPreview() {
    NexusTheme {
        HomeRowsSection(
            state =
                HomeRowsUiState(
                    rows =
                        listOf(
                            ManagedRowUi("1", "Popular Movies", hidden = false, canMoveUp = false, canMoveDown = true),
                            ManagedRowUi("2", "Top Rated Series", hidden = false, canMoveUp = true, canMoveDown = true),
                            ManagedRowUi("3", "Anime", hidden = true, canMoveUp = true, canMoveDown = true),
                            ManagedRowUi("4", "Documentaries", hidden = false, canMoveUp = true, canMoveDown = false),
                        ),
                    isDefault = false,
                    totalCount = 4,
                    hiddenCount = 1,
                ),
            onEvent = {},
        )
    }
}

@Preview(name = "HomeRowsSection - Empty Light")
@Composable
internal fun HomeRowsSectionEmptyLightPreview() {
    NexusTheme {
        HomeRowsSection(
            state = HomeRowsUiState(rows = emptyList(), isDefault = true),
            onEvent = {},
        )
    }
}

@Preview(name = "HomeRowsSection - Empty Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun HomeRowsSectionEmptyDarkPreview() {
    NexusTheme {
        HomeRowsSection(
            state = HomeRowsUiState(rows = emptyList(), isDefault = true),
            onEvent = {},
        )
    }
}

@Preview(name = "HomeRowsSection - Reset Dialog Light")
@Composable
internal fun HomeRowsSectionResetDialogLightPreview() {
    NexusTheme {
        HomeRowsSection(
            state = HomeRowsUiState(showResetConfirm = true),
            onEvent = {},
        )
    }
}

@Preview(name = "HomeRowsSection - Reset Dialog Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun HomeRowsSectionResetDialogDarkPreview() {
    NexusTheme {
        HomeRowsSection(
            state = HomeRowsUiState(showResetConfirm = true),
            onEvent = {},
        )
    }
}
