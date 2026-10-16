package com.nexus.stream.presentation.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.nexus.stream.R
import com.nexus.stream.domain.model.ListStatus
import com.nexus.stream.presentation.theme.NexusTheme

fun ListStatus.labelResId(): Int =
    when (this) {
        ListStatus.PLAN_TO_WATCH -> R.string.list_status_plan_to_watch
        ListStatus.WATCHING -> R.string.list_status_watching
        ListStatus.COMPLETED -> R.string.list_status_completed
        ListStatus.ON_HOLD -> R.string.list_status_on_hold
        ListStatus.DROPPED -> R.string.list_status_dropped
    }

private val STATUS_ORDER =
    listOf(
        ListStatus.WATCHING,
        ListStatus.COMPLETED,
        ListStatus.ON_HOLD,
        ListStatus.DROPPED,
        ListStatus.PLAN_TO_WATCH,
    )

@Composable
fun ListStatusDialog(
    current: ListStatus?,
    onSelect: (ListStatus) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.list_status_dialog_title),
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .selectableGroup(),
            ) {
                STATUS_ORDER.forEach { status ->
                    val selected = status == current
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .selectable(
                                    selected = selected,
                                    onClick = { onSelect(status) },
                                    role = Role.RadioButton,
                                ).padding(vertical = NexusTheme.spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selected,
                            onClick = null,
                        )
                        Spacer(modifier = Modifier.width(NexusTheme.spacing.md))
                        Text(
                            text = stringResource(status.labelResId()),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }

                if (current != null) {
                    Spacer(modifier = Modifier.height(NexusTheme.spacing.xs))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(NexusTheme.spacing.xs))

                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clickable(
                                    onClick = onRemove,
                                    role = Role.Button,
                                ).padding(vertical = NexusTheme.spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(start = NexusTheme.spacing.xs),
                        )
                        Spacer(modifier = Modifier.width(NexusTheme.spacing.md))
                        Text(
                            text = stringResource(R.string.list_status_remove),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(android.R.string.cancel))
            }
        },
        modifier = modifier,
    )
}
