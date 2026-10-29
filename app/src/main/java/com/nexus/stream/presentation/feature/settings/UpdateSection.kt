package com.nexus.stream.presentation.feature.settings

import android.content.Context
import android.content.res.Configuration
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.nexus.stream.R
import com.nexus.stream.presentation.common.NexusButton
import com.nexus.stream.presentation.common.NexusOutlinedButton
import com.nexus.stream.presentation.common.NexusTextButton
import com.nexus.stream.presentation.theme.NexusTheme

private const val NOTES_COLLAPSED_LINES = 6
private const val PREVIEW_SIZE_BYTES = 47_185_920L
private const val PREVIEW_PERCENT = 42
private const val PREVIEW_FRACTION = 0.42f

@Composable
fun UpdateSection(
    state: UpdateUi,
    onEvent: (UpdateEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = NexusTheme.spacing
    val context = LocalContext.current
    val statusText = formatStatusText(state.status, context)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        SettingsValueRow(
            title = stringResource(R.string.update_title),
            value = statusText,
        )

        UpdateProgressBar(progress = state.progress)

        UpdateButtons(buttons = state.bigButtons, onEvent = onEvent)

        if (!state.notes.isNullOrBlank()) {
            WhatsNewBlock(notes = state.notes)
        }

        SettingsSwitchRow(
            title = stringResource(R.string.update_auto_title),
            subtitle = stringResource(R.string.update_auto_caption),
            checked = state.autoCheck,
            onCheckedChange = { onEvent(UpdateEvent.SetAutoCheck(it)) },
        )
    }
}

@Composable
private fun formatStatusText(
    status: StatusText,
    context: Context,
): String =
    when {
        status.resId == R.string.update_up_to_date && status.formatArgs.isNotEmpty() -> {
            val checkedAt = status.formatArgs[0] as? Long ?: 0L
            val relative = DateUtils.getRelativeTimeSpanString(checkedAt).toString()
            stringResource(R.string.update_up_to_date, relative)
        }

        status.resId == R.string.update_failure_insufficient_storage &&
            status.formatArgs.isNotEmpty() -> {
            val needed = status.formatArgs[0] as? Long ?: 0L
            val formattedNeeded = Formatter.formatShortFileSize(context, needed)
            stringResource(R.string.update_failure_insufficient_storage, formattedNeeded)
        }

        status.resId == R.string.update_available && status.sizeBytes != null -> {
            val tag = status.formatArgs.firstOrNull() ?: ""
            val base = stringResource(status.resId, tag)
            val formattedSize = Formatter.formatShortFileSize(context, status.sizeBytes)
            val size = stringResource(R.string.update_size, formattedSize)
            "$base · $size"
        }

        status.formatArgs.size == 1 -> {
            stringResource(status.resId, status.formatArgs[0])
        }

        status.formatArgs.size > 1 -> {
            stringResource(status.resId, status.formatArgs[0], status.formatArgs[1])
        }

        else -> {
            stringResource(status.resId)
        }
    }

@Composable
private fun UpdateProgressBar(progress: Progress?) {
    when (progress) {
        is Progress.Determinate -> {
            LinearProgressIndicator(
                progress = { progress.fraction },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        is Progress.Indeterminate -> {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
            )
        }

        null -> {}
    }
}

@Composable
private fun UpdateButtons(
    buttons: List<UiButton>,
    onEvent: (UpdateEvent) -> Unit,
) {
    val spacing = NexusTheme.spacing
    if (buttons.size == 1) {
        val btn = buttons.first()
        if (btn.isPrimary) {
            NexusButton(onClick = { onEvent(btn.event) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(btn.textResId))
            }
        } else {
            NexusOutlinedButton(onClick = { onEvent(btn.event) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(btn.textResId))
            }
        }
    } else if (buttons.size > 1) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            buttons.forEach { btn ->
                if (btn.isPrimary) {
                    NexusButton(onClick = { onEvent(btn.event) }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(btn.textResId))
                    }
                } else {
                    NexusOutlinedButton(onClick = { onEvent(btn.event) }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(btn.textResId))
                    }
                }
            }
        }
    }
}

@Composable
private fun WhatsNewBlock(
    notes: String,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var hasOverflow by remember { mutableStateOf(false) }
    val spacing = NexusTheme.spacing

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        Text(
            text = stringResource(R.string.update_whats_new),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = notes,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (expanded) Int.MAX_VALUE else NOTES_COLLAPSED_LINES,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { result ->
                if (!expanded && (result.hasVisualOverflow || result.lineCount > NOTES_COLLAPSED_LINES)) {
                    hasOverflow = true
                }
            },
        )
        if (hasOverflow || expanded) {
            NexusTextButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(
                    text =
                        stringResource(
                            if (expanded) R.string.update_show_less else R.string.update_show_more,
                        ),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Previews for all states in spec 4.1
// ---------------------------------------------------------------------------

@Preview(name = "Idle & UpToDate - Light")
@Preview(name = "Idle & UpToDate - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun UpdateSectionIdleAndUpToDatePreview() {
    val spacing = NexusTheme.spacing
    NexusTheme {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.lg)) {
            // Idle
            UpdateSection(
                state =
                    UpdateUi(
                        status = StatusText(R.string.update_not_checked),
                        bigButtons = listOf(UiButton(R.string.update_check, UpdateEvent.CheckNow, isPrimary = true)),
                    ),
                onEvent = {},
            )
            // Up to date
            UpdateSection(
                state =
                    UpdateUi(
                        status = StatusText(R.string.update_up_to_date, listOf(System.currentTimeMillis())),
                        bigButtons =
                            listOf(
                                UiButton(R.string.update_check_again, UpdateEvent.CheckNow, isPrimary = true),
                            ),
                    ),
                onEvent = {},
            )
        }
    }
}

@Preview(name = "Active States - Light")
@Preview(name = "Active States - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun UpdateSectionActiveStatesPreview() {
    val spacing = NexusTheme.spacing
    NexusTheme {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.lg)) {
            // Checking
            UpdateSection(
                state = UpdateUi(status = StatusText(R.string.update_checking), progress = Progress.Indeterminate),
                onEvent = {},
            )
            // Available with notes
            UpdateSection(
                state =
                    UpdateUi(
                        status =
                            StatusText(
                                R.string.update_available,
                                listOf("1.0.1"),
                                sizeBytes = PREVIEW_SIZE_BYTES,
                            ),
                        bigButtons =
                            listOf(
                                UiButton(R.string.update_download, UpdateEvent.Download, isPrimary = true),
                                UiButton(R.string.update_view_release, UpdateEvent.OpenRelease, isPrimary = false),
                            ),
                        notes =
                            "• Fixed video playback issue\n" +
                                "• Improved torrent streaming speed\n" +
                                "• New subtitle options\n" +
                                "• Better battery life",
                        summaryFlag = true,
                    ),
                onEvent = {},
            )
            // Downloading
            UpdateSection(
                state =
                    UpdateUi(
                        status = StatusText(R.string.update_downloading, listOf("1.0.1", PREVIEW_PERCENT)),
                        progress = Progress.Determinate(PREVIEW_FRACTION),
                        bigButtons = listOf(UiButton(R.string.update_cancel, UpdateEvent.Cancel, isPrimary = false)),
                        summaryFlag = true,
                    ),
                onEvent = {},
            )
            // Verifying
            UpdateSection(
                state = UpdateUi(status = StatusText(R.string.update_verifying), progress = Progress.Indeterminate),
                onEvent = {},
            )
            // Installing
            UpdateSection(
                state = UpdateUi(status = StatusText(R.string.update_installing), progress = Progress.Indeterminate),
                onEvent = {},
            )
            // Awaiting confirmation
            UpdateSection(
                state =
                    UpdateUi(
                        status = StatusText(R.string.update_awaiting),
                        bigButtons = listOf(UiButton(R.string.update_continue, UpdateEvent.Continue, isPrimary = true)),
                    ),
                onEvent = {},
            )
        }
    }
}

@Preview(name = "Terminal States - Light")
@Preview(name = "Terminal States - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun UpdateSectionTerminalStatesPreview() {
    val spacing = NexusTheme.spacing
    NexusTheme {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.lg)) {
            // Failed
            UpdateSection(
                state =
                    UpdateUi(
                        status = StatusText(R.string.update_failure_network),
                        bigButtons =
                            listOf(
                                UiButton(R.string.update_try_again, UpdateEvent.DismissFailure, isPrimary = true),
                            ),
                    ),
                onEvent = {},
            )
            // Unsupported
            UpdateSection(
                state = UpdateUi(status = StatusText(R.string.update_unsupported)),
                onEvent = {},
            )
        }
    }
}
