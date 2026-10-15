package com.nexus.stream.presentation.feature.addons

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nexus.stream.R
import com.nexus.stream.data.remote.addon.AddonUrlBuilder
import com.nexus.stream.domain.model.Addon
import com.nexus.stream.domain.model.AddonManifest
import com.nexus.stream.domain.model.AddonSource
import com.nexus.stream.domain.model.ManifestHints
import com.nexus.stream.domain.model.ResourceSpec
import com.nexus.stream.presentation.common.NexusCard
import com.nexus.stream.presentation.theme.NexusTheme

private val LOGO_SIZE: Dp = 48.dp
private const val STREAM_RESOURCE = "stream"
private const val META_RESOURCE = "meta"
private const val SUBTITLES_RESOURCE = "subtitles"
private const val DISABLED_ALPHA = 0.5f

@Composable
fun AddonLogo(
    logoUrl: String?,
    addonName: String,
    modifier: Modifier = Modifier,
    size: Dp = LOGO_SIZE,
) {
    var isError by remember(logoUrl) { mutableStateOf(logoUrl.isNullOrBlank()) }
    val initial = addonName.firstOrNull()?.uppercaseChar()?.toString() ?: "?"

    Box(
        modifier =
            modifier
                .size(size)
                .clip(NexusTheme.shapes.chip)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (!isError && !logoUrl.isNullOrBlank()) {
            AsyncImage(
                model = logoUrl,
                contentDescription = stringResource(R.string.addon_logo_description, addonName),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onError = { isError = true },
            )
        }
        if (isError || logoUrl.isNullOrBlank()) {
            Text(
                text = initial,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddonCard(
    addon: Addon,
    isFirst: Boolean,
    isLast: Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRefresh: () -> Unit,
    onConfigure: () -> Unit,
    onCopyUrl: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = NexusTheme.spacing
    var menuExpanded by remember { mutableStateOf(false) }
    val host =
        remember(addon.transportUrl) {
            addon.transportUrl.toDisplayHost()
        }

    val contentAlpha = if (addon.enabled) 1f else DISABLED_ALPHA

    NexusCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .alpha(contentAlpha),
                horizontalArrangement = Arrangement.spacedBy(spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AddonLogo(
                    logoUrl = addon.manifest.logo,
                    addonName = addon.manifest.name,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(spacing.xxs),
                ) {
                    Text(
                        text = addon.manifest.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "v${addon.manifest.version}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (addon.manifest.description.isNotBlank()) {
                Text(
                    text = addon.manifest.description,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.alpha(contentAlpha),
                )
            }

            FlowRow(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .alpha(contentAlpha),
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                if (addon.manifest.catalogs.isNotEmpty()) {
                    SuggestionChip(
                        onClick = {},
                        label = { Text(stringResource(R.string.addon_catalogs_chip)) },
                    )
                }
                if (addon.manifest.resources.any { it.name == META_RESOURCE }) {
                    SuggestionChip(
                        onClick = {},
                        label = { Text(stringResource(R.string.addon_meta_chip)) },
                    )
                }
                if (addon.manifest.resources.any { it.name == STREAM_RESOURCE }) {
                    SuggestionChip(
                        onClick = {},
                        label = { Text(stringResource(R.string.addon_streams_chip)) },
                    )
                }
                if (addon.manifest.resources.any { it.name == SUBTITLES_RESOURCE }) {
                    SuggestionChip(
                        onClick = {},
                        label = { Text(stringResource(R.string.addon_subtitles_chip)) },
                    )
                }
                AssistChip(
                    onClick = {},
                    label = { Text(host) },
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    val enabledDescription =
                        stringResource(
                            R.string.addon_enabled_label,
                            addon.manifest.name,
                        )
                    Switch(
                        checked = addon.enabled,
                        onCheckedChange = onToggleEnabled,
                        modifier =
                            Modifier.semantics {
                                contentDescription = enabledDescription
                            },
                    )
                }

                Box {
                    val menuDesc =
                        stringResource(
                            R.string.addon_overflow_description,
                            addon.manifest.name,
                        )
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier =
                            Modifier.semantics {
                                contentDescription = menuDesc
                            },
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = null,
                        )
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.addon_move_up)) },
                            onClick = {
                                menuExpanded = false
                                onMoveUp()
                            },
                            enabled = !isFirst,
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.addon_move_down)) },
                            onClick = {
                                menuExpanded = false
                                onMoveDown()
                            },
                            enabled = !isLast,
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.addon_refresh)) },
                            onClick = {
                                menuExpanded = false
                                onRefresh()
                            },
                        )
                        if (addon.manifest.behaviorHints.configurable) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.addon_configure)) },
                                onClick = {
                                    menuExpanded = false
                                    onConfigure()
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.addon_copy_url)) },
                            onClick = {
                                menuExpanded = false
                                onCopyUrl()
                            },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = stringResource(R.string.addon_remove),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onRemove()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Preview(name = "AddonCard - Enabled")
@Composable
internal fun AddonCardEnabledPreview() {
    NexusTheme {
        AddonCard(
            addon =
                Addon(
                    transportUrl = "https://v3-cinemeta.strem.io/",
                    manifest =
                        AddonManifest(
                            id = "org.stremio.cinemeta",
                            version = "3.0.14",
                            name = "Cinemeta",
                            description = "Official movie and series directory for Stremio",
                            logo = null,
                            background = null,
                            types = listOf("movie", "series"),
                            idPrefixes = listOf("tt"),
                            resources =
                                listOf(
                                    ResourceSpec("catalog", null, null),
                                    ResourceSpec("meta", null, null),
                                ),
                            catalogs = emptyList(),
                            behaviorHints =
                                ManifestHints(
                                    adult = false,
                                    p2p = false,
                                    configurable = true,
                                    configurationRequired = false,
                                ),
                        ),
                    enabled = true,
                    position = 0,
                    source = AddonSource.LOCAL,
                    installedAt = 0L,
                ),
            isFirst = true,
            isLast = false,
            onToggleEnabled = {},
            onMoveUp = {},
            onMoveDown = {},
            onRefresh = {},
            onConfigure = {},
            onCopyUrl = {},
            onRemove = {},
        )
    }
}

@Preview(name = "AddonCard - Disabled")
@Composable
internal fun AddonCardDisabledPreview() {
    NexusTheme {
        AddonCard(
            addon =
                Addon(
                    transportUrl = "https://torrent-stream.local/",
                    manifest =
                        AddonManifest(
                            id = "org.torrent.stream",
                            version = "1.0.0",
                            name = "Torrent Streamer",
                            description = "P2P torrent streaming addon",
                            logo = null,
                            background = null,
                            types = listOf("movie"),
                            idPrefixes = null,
                            resources = listOf(ResourceSpec("stream", null, null)),
                            catalogs = emptyList(),
                            behaviorHints =
                                ManifestHints(
                                    adult = false,
                                    p2p = true,
                                    configurable = false,
                                    configurationRequired = false,
                                ),
                        ),
                    enabled = false,
                    position = 1,
                    source = AddonSource.LOCAL,
                    installedAt = 0L,
                ),
            isFirst = false,
            isLast = true,
            onToggleEnabled = {},
            onMoveUp = {},
            onMoveDown = {},
            onRefresh = {},
            onConfigure = {},
            onCopyUrl = {},
            onRemove = {},
        )
    }
}
