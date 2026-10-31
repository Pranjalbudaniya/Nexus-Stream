package com.nexus.stream.presentation.feature.downloads

import android.content.res.Configuration
import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.nexus.stream.R
import com.nexus.stream.domain.repository.TorrentFileChoice
import com.nexus.stream.presentation.common.NexusButton
import com.nexus.stream.presentation.common.NexusTextButton
import com.nexus.stream.presentation.theme.NexusTheme
import java.io.File

@Composable
fun TorrentFilePickDialog(
    candidates: List<TorrentFileChoice>,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val spacing = NexusTheme.spacing
    val sorted = remember(candidates) { candidates.sortedByDescending { it.sizeBytes } }
    var selectedIndex by remember(sorted) {
        mutableIntStateOf(sorted.firstOrNull()?.index ?: 0)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.downloads_pick_title)) },
        text = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                sorted.forEach { choice ->
                    val selected = choice.index == selectedIndex
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = selected,
                                    onClick = { selectedIndex = choice.index },
                                    role = Role.RadioButton,
                                ).padding(vertical = spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selected,
                            onClick = null,
                        )
                        Spacer(modifier = Modifier.width(spacing.sm))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = File(choice.name).name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = Formatter.formatShortFileSize(context, choice.sizeBytes),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            NexusButton(onClick = { onSelect(selectedIndex) }) {
                Text(stringResource(R.string.downloads_pick_confirm))
            }
        },
        dismissButton = {
            NexusTextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel))
            }
        },
        modifier = modifier,
    )
}

@Preview(name = "Light")
@Composable
internal fun TorrentFilePickDialogPreviewLight() {
    NexusTheme {
        TorrentFilePickDialog(
            candidates =
                listOf(
                    TorrentFileChoice(index = 0, name = "Movie.2024.1080p.mkv", sizeBytes = 2_500_000_000L),
                    TorrentFileChoice(index = 1, name = "Sample.mkv", sizeBytes = 25_000_000L),
                ),
            onSelect = {},
            onDismiss = {},
        )
    }
}

@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun TorrentFilePickDialogPreviewDark() {
    NexusTheme {
        TorrentFilePickDialog(
            candidates =
                listOf(
                    TorrentFileChoice(index = 0, name = "Movie.2024.1080p.mkv", sizeBytes = 2_500_000_000L),
                    TorrentFileChoice(index = 1, name = "Sample.mkv", sizeBytes = 25_000_000L),
                ),
            onSelect = {},
            onDismiss = {},
        )
    }
}
