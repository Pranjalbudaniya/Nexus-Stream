@file:Suppress("LongMethod")

package com.nexus.stream.dev

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import coil3.compose.AsyncImage
import com.nexus.stream.R
import com.nexus.stream.domain.model.AddonManifest
import com.nexus.stream.domain.model.CatalogSpec
import com.nexus.stream.domain.model.MetaDetail
import com.nexus.stream.domain.model.MetaPreview
import com.nexus.stream.domain.model.PosterShape
import com.nexus.stream.domain.model.Stream
import com.nexus.stream.domain.model.StreamSource
import com.nexus.stream.domain.model.genreOptions
import com.nexus.stream.domain.model.isBoardCatalog
import com.nexus.stream.domain.model.isSearchOnly
import com.nexus.stream.domain.model.supportsSearch
import com.nexus.stream.domain.model.supportsSkip
import com.nexus.stream.presentation.theme.NexusTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddonTesterScreen(
    state: AddonTesterUiState,
    onEvent: (AddonTesterEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = NexusTheme.spacing

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.addon_tester_title)) },
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
            // Transport URL & Manifest Section
            item {
                ManifestSection(
                    state = state,
                    onEvent = onEvent,
                )
            }

            // Manifest details & catalogs
            val manifest = state.manifest
            if (manifest != null) {
                item {
                    ManifestDetailsCard(
                        manifest = manifest,
                        selectedCatalog = state.selectedCatalog,
                        onSelectCatalog = { onEvent(AddonTesterEvent.SelectCatalog(it)) },
                    )
                }
            }

            // Selected Catalog section
            val catalog = state.selectedCatalog
            if (catalog != null) {
                item {
                    CatalogControlsSection(
                        catalog = catalog,
                        state = state,
                        onEvent = onEvent,
                    )
                }

                items(state.catalogItems, key = { it.id }) { item ->
                    CatalogItemRow(item = item)
                }
            }

            // Meta Section
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = spacing.sm))
                MetaSection(
                    state = state,
                    onEvent = onEvent,
                )
            }

            // Streams Section
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = spacing.sm))
                StreamsSection(
                    state = state,
                    onEvent = onEvent,
                )
            }
        }
    }
}

@Composable
private fun ManifestSection(
    state: AddonTesterUiState,
    onEvent: (AddonTesterEvent) -> Unit,
) {
    val spacing = NexusTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        OutlinedTextField(
            value = state.transportUrl,
            onValueChange = { onEvent(AddonTesterEvent.UpdateTransportUrl(it)) },
            label = { Text(stringResource(R.string.addon_tester_transport_url)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = { onEvent(AddonTesterEvent.FetchManifest) },
                enabled = !state.manifestLoading,
            ) {
                Text(stringResource(R.string.addon_tester_fetch_manifest))
            }

            state.manifestStatus?.let { status ->
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (state.manifestLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ManifestDetailsCard(
    manifest: AddonManifest,
    selectedCatalog: CatalogSpec?,
    onSelectCatalog: (CatalogSpec) -> Unit,
) {
    val spacing = NexusTheme.spacing
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            Text(
                text = stringResource(R.string.addon_tester_manifest_name, manifest.name),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.addon_tester_manifest_version, manifest.version),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = stringResource(R.string.addon_tester_manifest_description, manifest.description),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = stringResource(R.string.addon_tester_manifest_types, manifest.types.joinToString(", ")),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text =
                    stringResource(
                        R.string.addon_tester_manifest_prefixes,
                        manifest.idPrefixes?.joinToString(", ") ?: stringResource(R.string.addon_tester_inherits),
                    ),
                style = MaterialTheme.typography.bodySmall,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = spacing.xs))

            Text(
                text = stringResource(R.string.addon_tester_resources),
                style = MaterialTheme.typography.labelLarge,
            )
            manifest.resources.forEach { res ->
                val typesStr = res.types?.joinToString(",") ?: stringResource(R.string.addon_tester_inherits)
                val prefixesStr = res.idPrefixes?.joinToString(",") ?: stringResource(R.string.addon_tester_inherits)
                Text(
                    text = "${res.name} ($typesStr | $prefixesStr)",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = spacing.xs))

            Text(
                text = stringResource(R.string.addon_tester_catalogs),
                style = MaterialTheme.typography.labelLarge,
            )

            manifest.catalogs.forEach { cat ->
                val isSelected = selectedCatalog?.id == cat.id && selectedCatalog.type == cat.type
                Card(
                    colors =
                        CardDefaults.cardColors(
                            containerColor =
                                if (isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainer
                                },
                        ),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSelectCatalog(cat) },
                ) {
                    Column(modifier = Modifier.padding(spacing.sm)) {
                        Text(
                            text = "${cat.type} / ${cat.id} / ${cat.name}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                            verticalArrangement = Arrangement.spacedBy(spacing.xxs),
                            modifier = Modifier.padding(top = spacing.xs),
                        ) {
                            if (cat.isBoardCatalog) {
                                BadgeChip(stringResource(R.string.addon_tester_badge_board))
                            }
                            if (cat.supportsSearch) {
                                BadgeChip(stringResource(R.string.addon_tester_badge_search))
                            }
                            if (cat.isSearchOnly) {
                                BadgeChip(stringResource(R.string.addon_tester_badge_search_only))
                            }
                            if (cat.supportsSkip) {
                                BadgeChip(stringResource(R.string.addon_tester_badge_skip))
                            }
                            if (cat.genreOptions.isNotEmpty()) {
                                BadgeChip(
                                    stringResource(R.string.addon_tester_badge_genres, cat.genreOptions.size),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BadgeChip(label: String) {
    Surface(
        shape = NexusTheme.shapes.chip,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = NexusTheme.spacing.xs, vertical = NexusTheme.spacing.xxs),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CatalogControlsSection(
    catalog: CatalogSpec,
    state: AddonTesterUiState,
    onEvent: (AddonTesterEvent) -> Unit,
) {
    val spacing = NexusTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Text(
            text = "${catalog.name} (${catalog.type}/${catalog.id})",
            style = MaterialTheme.typography.titleMedium,
        )

        if (catalog.genreOptions.isNotEmpty()) {
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded },
            ) {
                OutlinedTextField(
                    value = state.selectedGenre ?: stringResource(R.string.addon_tester_all_genres),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.addon_tester_genre)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier =
                        Modifier
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth(),
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.addon_tester_all_genres)) },
                        onClick = {
                            onEvent(AddonTesterEvent.SelectGenre(null))
                            expanded = false
                        },
                    )
                    catalog.genreOptions.forEach { genre ->
                        DropdownMenuItem(
                            text = { Text(genre) },
                            onClick = {
                                onEvent(AddonTesterEvent.SelectGenre(genre))
                                expanded = false
                            },
                        )
                    }
                }
            }
        }

        if (catalog.supportsSearch) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = { onEvent(AddonTesterEvent.UpdateSearchQuery(it)) },
                label = { Text(stringResource(R.string.addon_tester_search_query)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Button(
                onClick = { onEvent(AddonTesterEvent.LoadCatalogFirstPage) },
                enabled = !state.catalogLoading,
            ) {
                Text(stringResource(R.string.addon_tester_load_first_page))
            }

            OutlinedButton(
                onClick = { onEvent(AddonTesterEvent.LoadCatalogMore) },
                enabled = state.catalogItems.isNotEmpty() && !state.catalogEndReached && !state.catalogLoading,
            ) {
                Text(stringResource(R.string.addon_tester_load_more))
            }
        }

        if (state.catalogLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val counters =
                buildString {
                    append(stringResource(R.string.addon_tester_loaded_count, state.catalogLoadedCount))
                    append(" • ")
                    append(stringResource(R.string.addon_tester_skipped_count, state.catalogSkippedCount))
                    if (state.catalogEndReached) {
                        append(" • ")
                        append(stringResource(R.string.addon_tester_end_reached))
                    }
                }
            Text(
                text = counters,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            state.catalogStatus?.let { status ->
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CatalogItemRow(item: MetaPreview) {
    val spacing = NexusTheme.spacing
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = NexusTheme.shapes.card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier.padding(spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = item.poster,
                contentDescription = item.name,
                modifier =
                    Modifier
                        .width(spacing.xxl + spacing.lg)
                        .aspectRatio(POSTER_ASPECT_RATIO)
                        .clip(NexusTheme.shapes.poster),
                contentScale = ContentScale.Crop,
            )

            Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleSmall,
                )
                item.releaseInfo?.let { release ->
                    Text(
                        text = stringResource(R.string.addon_tester_released, release),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                item.imdbRating?.let { rating ->
                    Text(
                        text = stringResource(R.string.addon_tester_rating, rating),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun MetaSection(
    state: AddonTesterUiState,
    onEvent: (AddonTesterEvent) -> Unit,
) {
    val spacing = NexusTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Text(
            text = stringResource(R.string.addon_tester_meta_header),
            style = MaterialTheme.typography.titleMedium,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            OutlinedTextField(
                value = state.metaType,
                onValueChange = { onEvent(AddonTesterEvent.UpdateMetaType(it)) },
                label = { Text(stringResource(R.string.addon_tester_meta_type)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            OutlinedTextField(
                value = state.metaId,
                onValueChange = { onEvent(AddonTesterEvent.UpdateMetaId(it)) },
                label = { Text(stringResource(R.string.addon_tester_meta_id)) },
                modifier = Modifier.weight(2f),
                singleLine = true,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = { onEvent(AddonTesterEvent.FetchMeta) },
                enabled = !state.metaLoading,
            ) {
                Text(stringResource(R.string.addon_tester_fetch_meta))
            }

            state.metaStatus?.let { status ->
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (state.metaLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        state.metaDetail?.let { detail ->
            MetaDetailCard(detail = detail)
        }
    }
}

@Composable
private fun MetaDetailCard(detail: MetaDetail) {
    val spacing = NexusTheme.spacing
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            Text(
                text = stringResource(R.string.addon_tester_meta_name, detail.name),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text =
                    stringResource(
                        R.string.addon_tester_meta_runtime,
                        detail.runtime ?: stringResource(R.string.addon_tester_none),
                    ),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text =
                    stringResource(
                        R.string.addon_tester_meta_genres,
                        detail.genres.joinToString(", ").ifEmpty { stringResource(R.string.addon_tester_none) },
                    ),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = stringResource(R.string.addon_tester_meta_trailers, detail.trailers.size),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = stringResource(R.string.addon_tester_meta_videos, detail.videos.size),
                style = MaterialTheme.typography.bodySmall,
            )

            if (detail.videos.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.addon_tester_meta_first_videos),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = spacing.xs),
                )
                detail.videos.take(PREVIEW_VIDEOS_LIMIT).forEach { video ->
                    Text(
                        text = "• ${video.title} (S${video.season ?: 0}E${video.episode ?: 0})",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun StreamsSection(
    state: AddonTesterUiState,
    onEvent: (AddonTesterEvent) -> Unit,
) {
    val spacing = NexusTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Text(
            text = stringResource(R.string.addon_tester_streams_header),
            style = MaterialTheme.typography.titleMedium,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            OutlinedTextField(
                value = state.streamType,
                onValueChange = { onEvent(AddonTesterEvent.UpdateStreamType(it)) },
                label = { Text(stringResource(R.string.addon_tester_streams_type)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            OutlinedTextField(
                value = state.streamId,
                onValueChange = { onEvent(AddonTesterEvent.UpdateStreamId(it)) },
                label = { Text(stringResource(R.string.addon_tester_streams_id)) },
                modifier = Modifier.weight(2f),
                singleLine = true,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = { onEvent(AddonTesterEvent.FetchStreams) },
                enabled = !state.streamLoading,
            ) {
                Text(stringResource(R.string.addon_tester_fetch_streams))
            }

            state.streamStatus?.let { status ->
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (state.streamLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        val streams = state.streams
        if (streams != null) {
            Text(
                text = stringResource(R.string.addon_tester_streams_count, streams.size),
                style = MaterialTheme.typography.labelLarge,
            )

            streams.forEach { stream ->
                StreamItemCard(stream = stream)
            }
        }
    }
}

@Composable
private fun StreamItemCard(stream: Stream) {
    val spacing = NexusTheme.spacing
    val sourceKind =
        when (val s = stream.source) {
            is StreamSource.Url -> stringResource(R.string.addon_tester_source_url)
            is StreamSource.Torrent -> stringResource(R.string.addon_tester_source_torrent)
            is StreamSource.YouTube -> stringResource(R.string.addon_tester_source_youtube)
            is StreamSource.External -> stringResource(R.string.addon_tester_source_external)
            is StreamSource.Unsupported -> stringResource(R.string.addon_tester_source_unsupported, s.kind)
        }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.padding(spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.xxs),
        ) {
            Text(
                text = stringResource(R.string.addon_tester_stream_source, sourceKind),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            val nameText = stream.name ?: stream.filename ?: stringResource(R.string.addon_tester_none)
            Text(
                text = stringResource(R.string.addon_tester_stream_name, nameText),
                style = MaterialTheme.typography.bodyMedium,
            )
            val firstLine = stream.description?.lineSequence()?.firstOrNull()
            if (!firstLine.isNullOrBlank()) {
                Text(
                    text = stringResource(R.string.addon_tester_stream_desc, firstLine),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

private const val POSTER_ASPECT_RATIO = 2f / 3f
private const val PREVIEW_VIDEOS_LIMIT = 3

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun AddonTesterScreenPreview() {
    NexusTheme(
        settings =
            com.nexus.stream.domain.model
                .ThemeSettings(),
    ) {
        AddonTesterScreen(
            state =
                AddonTesterUiState(
                    manifest =
                        AddonManifest(
                            id = "org.stremio.cinemeta",
                            version = "3.0.14",
                            name = "Cinemeta",
                            description = "Official movie and series directory",
                            logo = null,
                            background = null,
                            types = listOf("movie", "series"),
                            idPrefixes = listOf("tt"),
                            resources = emptyList(),
                            catalogs =
                                listOf(
                                    CatalogSpec("movie", "top", "Popular", emptyList()),
                                ),
                            behaviorHints =
                                com.nexus.stream.domain.model.ManifestHints(
                                    adult = false,
                                    p2p = false,
                                    configurable = false,
                                    configurationRequired = false,
                                ),
                        ),
                ),
            onEvent = {},
            onBack = {},
        )
    }
}
