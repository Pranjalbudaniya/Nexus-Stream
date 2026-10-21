package com.nexus.stream.dev

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.nexus.stream.R
import com.nexus.stream.presentation.common.NexusButton
import com.nexus.stream.presentation.common.NexusOutlinedButton
import com.nexus.stream.presentation.theme.NexusTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch

private const val HEADERS_MIN_LINES = 3
private const val HEADERS_MAX_LINES = 6

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProxyTesterScreen(
    state: ProxyTesterUiState,
    onEvent: (ProxyTesterEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    effects: Flow<ProxyTesterEffect> = emptyFlow(),
) {
    val spacing = NexusTheme.spacing
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val noAppFoundMessage = stringResource(R.string.proxy_tester_no_app_found)

    LaunchedEffect(effects) {
        effects.collect { effect ->
            when (effect) {
                is ProxyTesterEffect.OpenUrl -> {
                    try {
                        val uri = Uri.parse(effect.url)
                        val intent =
                            if (effect.asVideo) {
                                val target =
                                    Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(uri, "video/*")
                                    }
                                Intent.createChooser(target, null)
                            } else {
                                Intent(Intent.ACTION_VIEW, uri)
                            }
                        context.startActivity(intent)
                    } catch (_: ActivityNotFoundException) {
                        scope.launch {
                            snackbarHostState.showSnackbar(noAppFoundMessage)
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
                title = { Text(stringResource(R.string.proxy_tester_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.addon_tester_back),
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
            item(key = "url_input") {
                OutlinedTextField(
                    value = state.url,
                    onValueChange = { onEvent(ProxyTesterEvent.UrlChanged(it)) },
                    label = { Text(stringResource(R.string.proxy_tester_url_label)) },
                    placeholder = { Text(stringResource(R.string.proxy_tester_url_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = NexusTheme.shapes.card,
                    singleLine = true,
                )
            }

            item(key = "headers_input") {
                OutlinedTextField(
                    value = state.headersText,
                    onValueChange = { onEvent(ProxyTesterEvent.HeadersChanged(it)) },
                    label = { Text(stringResource(R.string.proxy_tester_headers_label)) },
                    placeholder = { Text(stringResource(R.string.proxy_tester_headers_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = NexusTheme.shapes.card,
                    minLines = HEADERS_MIN_LINES,
                    maxLines = HEADERS_MAX_LINES,
                )
            }

            item(key = "counts_info") {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacing.md),
                    ) {
                        Text(
                            text = stringResource(R.string.proxy_tester_parsed_count, state.parsedHeaderCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = stringResource(R.string.proxy_tester_ignored_count, state.ignoredLineCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    if (state.statusText != null) {
                        Text(
                            text = stringResource(R.string.proxy_tester_status_label, state.statusText),
                            style = MaterialTheme.typography.bodyMedium,
                            color =
                                if (state.isSessionActive) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                        )
                    }
                }
            }

            item(key = "actions") {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    ) {
                        NexusButton(
                            onClick = { onEvent(ProxyTesterEvent.OpenInBrowser) },
                            enabled = state.url.isNotBlank(),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.proxy_tester_open_browser))
                        }

                        NexusButton(
                            onClick = { onEvent(ProxyTesterEvent.PlayInOtherApp) },
                            enabled = state.url.isNotBlank(),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.proxy_tester_play_other_app))
                        }
                    }

                    NexusButton(
                        onClick = { onEvent(ProxyTesterEvent.PlayInApp) },
                        enabled = state.url.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.proxy_tester_play_in_app))
                    }

                    NexusOutlinedButton(
                        onClick = { onEvent(ProxyTesterEvent.Stop) },
                        enabled = state.isSessionActive,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.proxy_tester_stop))
                    }
                }
            }
        }
    }
}

@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun ProxyTesterScreenPreview() {
    NexusTheme {
        Surface {
            ProxyTesterScreen(
                state =
                    ProxyTesterUiState(
                        url = "https://example.com/video.mp4",
                        parsedHeaderCount = 2,
                        ignoredLineCount = 1,
                        isSessionActive = true,
                        statusText = "Active",
                    ),
                onEvent = {},
                onBack = {},
            )
        }
    }
}
