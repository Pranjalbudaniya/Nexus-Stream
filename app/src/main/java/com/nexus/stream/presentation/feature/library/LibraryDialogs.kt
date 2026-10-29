package com.nexus.stream.presentation.feature.library

import android.content.res.Configuration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.nexus.stream.R
import com.nexus.stream.domain.model.HistoryEntry
import com.nexus.stream.presentation.common.NexusButton
import com.nexus.stream.presentation.common.NexusTextButton
import com.nexus.stream.presentation.theme.NexusTheme

@Composable
fun RemoveFromHistoryDialog(
    entry: HistoryEntry,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val message =
        if (entry.name.isNullOrBlank()) {
            stringResource(R.string.library_history_remove_dialog_message_unknown)
        } else {
            stringResource(R.string.library_history_remove_dialog_message, entry.name)
        }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.library_history_remove_dialog_title)) },
        text = { Text(message) },
        confirmButton = {
            NexusButton(
                onClick = onConfirm,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
            ) {
                Text(stringResource(R.string.library_history_remove_dialog_confirm))
            }
        },
        dismissButton = {
            NexusTextButton(onClick = onDismiss) {
                Text(stringResource(R.string.library_history_remove_dialog_dismiss))
            }
        },
    )
}

@Composable
fun ClearHistoryDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.library_history_clear_dialog_title)) },
        text = { Text(stringResource(R.string.library_history_clear_dialog_message)) },
        confirmButton = {
            NexusButton(
                onClick = onConfirm,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
            ) {
                Text(stringResource(R.string.library_history_clear_dialog_confirm))
            }
        },
        dismissButton = {
            NexusTextButton(onClick = onDismiss) {
                Text(stringResource(R.string.library_history_clear_dialog_dismiss))
            }
        },
    )
}

@Preview(name = "RemoveFromHistoryDialog - Light")
@Preview(name = "RemoveFromHistoryDialog - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun RemoveFromHistoryDialogPreview() {
    NexusTheme {
        RemoveFromHistoryDialog(
            entry =
                HistoryEntry(
                    metaId = "m1",
                    type = "movie",
                    name = "Inception",
                    poster = null,
                    season = null,
                    episode = null,
                    videoId = "v1",
                    progress = 0.5f,
                    watched = false,
                    updatedAt = 0L,
                ),
            onConfirm = {},
            onDismiss = {},
        )
    }
}

@Preview(name = "ClearHistoryDialog - Light")
@Preview(name = "ClearHistoryDialog - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun ClearHistoryDialogPreview() {
    NexusTheme {
        ClearHistoryDialog(
            onConfirm = {},
            onDismiss = {},
        )
    }
}
