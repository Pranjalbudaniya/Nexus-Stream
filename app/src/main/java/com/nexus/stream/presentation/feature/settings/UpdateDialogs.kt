package com.nexus.stream.presentation.feature.settings

import android.content.res.Configuration
import android.text.format.Formatter
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.nexus.stream.R
import com.nexus.stream.presentation.common.NexusButton
import com.nexus.stream.presentation.common.NexusTextButton
import com.nexus.stream.presentation.theme.NexusTheme

@Composable
fun ConfirmMeteredDialog(
    sizeBytes: Long?,
    onEvent: (UpdateEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val sizeText =
        if (sizeBytes != null && sizeBytes > 0L) {
            Formatter.formatShortFileSize(context, sizeBytes)
        } else {
            ""
        }

    AlertDialog(
        onDismissRequest = { onEvent(UpdateEvent.DismissMetered) },
        title = { Text(stringResource(R.string.update_metered_title)) },
        text = { Text(stringResource(R.string.update_metered_body, sizeText)) },
        confirmButton = {
            NexusButton(onClick = { onEvent(UpdateEvent.ConfirmMetered) }) {
                Text(stringResource(R.string.update_download))
            }
        },
        dismissButton = {
            NexusTextButton(onClick = { onEvent(UpdateEvent.DismissMetered) }) {
                Text(stringResource(R.string.update_cancel))
            }
        },
        modifier = modifier,
    )
}

@Composable
fun InstallPermissionDialog(
    onEvent: (UpdateEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = { onEvent(UpdateEvent.DismissPermissionDialog) },
        title = { Text(stringResource(R.string.update_permission_title)) },
        text = { Text(stringResource(R.string.update_permission_body)) },
        confirmButton = {
            NexusButton(onClick = { onEvent(UpdateEvent.OpenInstallSettings) }) {
                Text(stringResource(R.string.update_open_settings))
            }
        },
        dismissButton = {
            NexusTextButton(onClick = { onEvent(UpdateEvent.DismissPermissionDialog) }) {
                Text(stringResource(R.string.update_not_now))
            }
        },
        modifier = modifier,
    )
}

@Preview(name = "ConfirmMetered - Light")
@Preview(name = "ConfirmMetered - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun ConfirmMeteredDialogPreview() {
    NexusTheme {
        ConfirmMeteredDialog(
            sizeBytes = 45 * 1024 * 1024L,
            onEvent = {},
        )
    }
}

@Preview(name = "InstallPermission - Light")
@Preview(name = "InstallPermission - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun InstallPermissionDialogPreview() {
    NexusTheme {
        InstallPermissionDialog(
            onEvent = {},
        )
    }
}
