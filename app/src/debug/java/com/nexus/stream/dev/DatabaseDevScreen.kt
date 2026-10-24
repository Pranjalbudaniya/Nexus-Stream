package com.nexus.stream.dev

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nexus.stream.R
import com.nexus.stream.presentation.common.NexusButton
import com.nexus.stream.presentation.theme.NexusTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatabaseDevScreen(
    state: DatabaseDebugState,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.dev_tools_database_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.dev_tools_database_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.dev_tools_database_refresh),
                        )
                    }
                },
            )
        },
        modifier = modifier,
    ) { innerPadding ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = NexusTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(NexusTheme.spacing.md),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = NexusTheme.spacing.sm),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.dev_tools_database_room_version, state.version),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    NexusButton(onClick = onRefresh) {
                        Text(stringResource(R.string.dev_tools_database_refresh))
                    }
                }
            }

            item {
                TableCountsCard(tableCounts = state.tableCounts)
            }

            item {
                SectionHeader(stringResource(R.string.dev_tools_database_library_items))
                if (state.libraryItems.isEmpty()) {
                    EmptyRowText()
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(NexusTheme.spacing.xs)) {
                        state.libraryItems.forEach { item ->
                            val sub =
                                "id=${item.metaId} | watch=${item.inWatchlist} | status=${item.listStatus}"
                            DebugItemCard(
                                title = "${item.name} (${item.type})",
                                subtitle = sub,
                            )
                        }
                    }
                }
            }

            item {
                SectionHeader(stringResource(R.string.dev_tools_database_watch_progress))
                if (state.watchProgress.isEmpty()) {
                    EmptyRowText()
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(NexusTheme.spacing.xs)) {
                        state.watchProgress.forEach { item ->
                            val key = "${item.metaId} | ${item.videoId}"
                            val pos = formatMmSs(item.positionMs)
                            val dur = formatMmSs(item.durationMs)
                            val status = item.libraryStatus ?: "N/A"
                            val sub = "$pos / $dur | watched=${item.watched} | status=$status | ${item.sourceText}"
                            DebugItemCard(
                                title = key,
                                subtitle = sub,
                            )
                        }
                    }
                }
            }

            item {
                SectionHeader(stringResource(R.string.dev_tools_database_meta_cache))
                if (state.metaCache.isEmpty()) {
                    EmptyRowText()
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(NexusTheme.spacing.xs)) {
                        state.metaCache.forEach { item ->
                            DebugItemCard(
                                title = "${item.metaId} (${item.type})",
                                subtitle = "json=${item.jsonSnippet}...",
                            )
                        }
                    }
                }
            }

            item {
                SectionHeader(stringResource(R.string.dev_tools_database_addons))
                if (state.addons.isEmpty()) {
                    EmptyRowText()
                } else {
                    Column(
                        modifier = Modifier.padding(bottom = NexusTheme.spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(NexusTheme.spacing.xs),
                    ) {
                        state.addons.forEach { item ->
                            val sub = "pos=${item.position} | enabled=${item.enabled} | host=${item.host}"
                            DebugItemCard(
                                title = item.name,
                                subtitle = sub,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = NexusTheme.spacing.xs),
    )
}

@Composable
private fun EmptyRowText() {
    Text(
        text = stringResource(R.string.dev_tools_database_empty),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = NexusTheme.spacing.xs),
    )
}

@Composable
private fun TableCountsCard(tableCounts: Map<String, Int>) {
    Card(
        shape = NexusTheme.shapes.card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(NexusTheme.spacing.sm)) {
            Text(
                text = stringResource(R.string.dev_tools_database_table_counts),
                style = MaterialTheme.typography.labelMedium,
            )
            tableCounts.forEach { (table, count) ->
                Text(
                    text = "$table: $count",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

@Composable
private fun DebugItemCard(
    title: String,
    subtitle: String,
) {
    Card(
        shape = NexusTheme.shapes.card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(all = 8.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

private const val SECONDS_PER_MINUTE = 60
private const val MS_PER_SECOND = 1000L

private fun formatMmSs(ms: Long): String {
    val totalSeconds = (ms / MS_PER_SECOND).coerceAtLeast(0)
    val minutes = totalSeconds / SECONDS_PER_MINUTE
    val seconds = totalSeconds % SECONDS_PER_MINUTE
    return String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds)
}

private const val PREVIEW_VERSION = 1
private const val PREVIEW_ADDONS_COUNT = 3
private const val PREVIEW_LIBRARY_COUNT = 5
private const val PREVIEW_ADDED_AT = 12_345L

@Preview(name = "Light")
@Composable
internal fun DatabaseDevScreenPreviewLight() {
    NexusTheme {
        DatabaseDevScreen(
            state =
                DatabaseDebugState(
                    version = PREVIEW_VERSION,
                    tableCounts = mapOf("addons" to PREVIEW_ADDONS_COUNT, "library_items" to PREVIEW_LIBRARY_COUNT),
                    libraryItems =
                        listOf(
                            LibraryItemDebugRow("m1", "movie", "Inception", true, "WATCHING", PREVIEW_ADDED_AT),
                        ),
                ),
            onRefresh = {},
            onBack = {},
        )
    }
}

@Preview(name = "Dark")
@Composable
internal fun DatabaseDevScreenPreviewDark() {
    NexusTheme {
        DatabaseDevScreen(
            state =
                DatabaseDebugState(
                    version = PREVIEW_VERSION,
                    tableCounts = mapOf("addons" to PREVIEW_ADDONS_COUNT, "library_items" to PREVIEW_LIBRARY_COUNT),
                ),
            onRefresh = {},
            onBack = {},
        )
    }
}
