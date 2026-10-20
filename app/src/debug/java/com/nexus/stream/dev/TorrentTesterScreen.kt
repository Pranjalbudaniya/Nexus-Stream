package com.nexus.stream.dev

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nexus.stream.R
import com.nexus.stream.domain.model.AppError
import com.nexus.stream.domain.model.TorrentFailReason
import com.nexus.stream.domain.model.TorrentFile
import com.nexus.stream.domain.model.TorrentState
import com.nexus.stream.presentation.common.NexusButton
import com.nexus.stream.presentation.common.NexusCard
import com.nexus.stream.presentation.common.NexusOutlinedButton
import com.nexus.stream.presentation.common.rememberNotificationPermissionGate
import com.nexus.stream.presentation.theme.NexusTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TorrentTesterScreen(
    state: TorrentTesterUiState,
    onEvent: (TorrentTesterEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    effects: Flow<TorrentTesterEffect> = emptyFlow(),
) {
    val spacing = NexusTheme.spacing
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var noPlayerFound by remember { mutableStateOf(false) }
    val noPlayerFoundMessage = stringResource(R.string.torrent_tester_no_player_found)
    val permissionGate = rememberNotificationPermissionGate()

    LaunchedEffect(effects) {
        effects.collect { effect ->
            when (effect) {
                is TorrentTesterEffect.OpenPlayer -> {
                    noPlayerFound = false
                    try {
                        val intent =
                            Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(Uri.parse(effect.url), "video/*")
                            }
                        val chooser = Intent.createChooser(intent, null)
                        context.startActivity(chooser)
                    } catch (_: ActivityNotFoundException) {
                        noPlayerFound = true
                        scope.launch {
                            snackbarHostState.showSnackbar(noPlayerFoundMessage)
                        }
                    }
                }
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.torrent_tester_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.dev_tools_database_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            item {
                OutlinedTextField(
                    value = state.input,
                    onValueChange = { onEvent(TorrentTesterEvent.InputChanged(it)) },
                    label = { Text(stringResource(R.string.torrent_tester_input_hint)) },
                    isError = state.inputInvalid,
                    supportingText = {
                        if (state.inputInvalid) {
                            Text(
                                text = stringResource(R.string.torrent_tester_input_error),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = NexusTheme.shapes.card,
                    singleLine = false,
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.md),
                ) {
                    NexusButton(
                        onClick = {
                            permissionGate {
                                onEvent(TorrentTesterEvent.Start)
                            }
                        },
                        enabled = !state.running && state.input.isNotBlank(),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.torrent_tester_start))
                    }

                    NexusOutlinedButton(
                        onClick = { onEvent(TorrentTesterEvent.Stop) },
                        enabled = state.running,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.torrent_tester_stop))
                    }
                }
            }

            val torrentState = state.state
            if (torrentState != null) {
                item {
                    TorrentStatusSection(
                        state = torrentState,
                        onSelectFile = { onEvent(TorrentTesterEvent.SelectFile(it)) },
                        onPlayInOtherApp = { onEvent(TorrentTesterEvent.PlayInOtherApp) },
                        isBlockedMetered = state.isBlockedMetered,
                        onAllowMeteredOnce = { onEvent(TorrentTesterEvent.AllowMeteredOnce) },
                        noPlayerFound = noPlayerFound,
                    )
                }
            }
        }
    }
}

@Composable
private fun TorrentStatusSection(
    state: TorrentState,
    onSelectFile: (Int) -> Unit,
    onPlayInOtherApp: () -> Unit,
    isBlockedMetered: Boolean,
    onAllowMeteredOnce: () -> Unit,
    noPlayerFound: Boolean,
) {
    val spacing = NexusTheme.spacing

    NexusCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            when (state) {
                is TorrentState.FetchingMetadata -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(spacing.md),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Text(
                            text = stringResource(R.string.torrent_tester_fetching_metadata),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }

                is TorrentState.NeedsFilePick -> {
                    val sortedFiles = remember(state.files) { state.files.sortedByDescending { it.sizeBytes } }
                    Text(
                        text = "Select a file",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    sortedFiles.forEach { file ->
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = file.path.substringAfterLast('/'),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            },
                            trailingContent = {
                                Text(
                                    text = formatBytes(file.sizeBytes),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectFile(file.index) },
                        )
                    }
                }

                is TorrentState.Buffering -> {
                    Text(
                        text = stringResource(R.string.torrent_tester_buffering, state.percent),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.torrent_tester_stats, formatSpeed(state.speedBps), state.peers),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                is TorrentState.Ready -> {
                    Text(
                        text =
                            stringResource(
                                R.string.torrent_tester_ready,
                                state.fileName,
                                formatBytes(state.fileSize),
                            ),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )

                    NexusButton(
                        onClick = onPlayInOtherApp,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.torrent_tester_play_in_other_app))
                    }

                    if (noPlayerFound) {
                        Text(
                            text = stringResource(R.string.torrent_tester_no_player_found),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                is TorrentState.Failed -> {
                    val message = formatFailReason(state.error)
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                    )

                    if (isBlockedMetered) {
                        NexusButton(
                            onClick = onAllowMeteredOnce,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.torrent_tester_allow_once))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun formatFailReason(error: AppError): String =
    when (error) {
        is AppError.MeteredBlocked -> {
            stringResource(R.string.torrent_tester_blocked_metered)
        }

        is AppError.Torrent -> {
            when (error.reason) {
                TorrentFailReason.INVALID_HASH -> stringResource(R.string.torrent_tester_error_invalid_hash)
                TorrentFailReason.METADATA_TIMEOUT -> stringResource(R.string.torrent_tester_error_metadata_timeout)
                TorrentFailReason.NO_VIDEO_FILE -> stringResource(R.string.torrent_tester_error_no_video_file)
                TorrentFailReason.STORAGE -> stringResource(R.string.torrent_tester_error_storage)
                TorrentFailReason.ENGINE_UNAVAILABLE -> stringResource(R.string.torrent_tester_error_engine_unavailable)
            }
        }

        else -> {
            stringResource(R.string.torrent_tester_error_unknown, error.toString())
        }
    }

private const val BYTES_IN_KB = 1024.0
private const val BYTES_IN_MB = 1024.0 * 1024.0
private const val BYTES_IN_GB = 1024.0 * 1024.0 * 1024.0

private fun formatBytes(bytes: Long): String =
    when {
        bytes >= BYTES_IN_GB -> String.format(Locale.US, "%.1f GB", bytes / BYTES_IN_GB)
        bytes >= BYTES_IN_MB -> String.format(Locale.US, "%.1f MB", bytes / BYTES_IN_MB)
        bytes >= BYTES_IN_KB -> String.format(Locale.US, "%.1f KB", bytes / BYTES_IN_KB)
        else -> "$bytes B"
    }

private fun formatSpeed(bytesPerSec: Long): String = "${formatBytes(bytesPerSec)}/s"

@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun TorrentTesterScreenPreview() {
    NexusTheme {
        Surface {
            TorrentTesterScreen(
                state =
                    TorrentTesterUiState(
                        input = "",
                        running = true,
                        state = TorrentState.Buffering(percent = 42, speedBps = 1_258_291L, peers = 14),
                    ),
                onEvent = {},
                onBack = {},
            )
        }
    }
}
