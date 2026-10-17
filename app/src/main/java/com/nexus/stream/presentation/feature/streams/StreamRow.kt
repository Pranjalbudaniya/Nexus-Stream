package com.nexus.stream.presentation.feature.streams

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nexus.stream.R
import com.nexus.stream.domain.model.Codec
import com.nexus.stream.domain.model.HdrKind
import com.nexus.stream.domain.model.ParsedStream
import com.nexus.stream.domain.model.StreamKind
import com.nexus.stream.domain.model.StreamSource
import com.nexus.stream.domain.usecase.MagnetLink
import com.nexus.stream.presentation.common.NexusCard
import com.nexus.stream.presentation.common.NexusIconButton
import com.nexus.stream.presentation.theme.NexusTheme
import java.util.Locale

private const val MIN_ROW_HEIGHT = 48
private const val SKELETON_WIDTH_FRACTION = 0.7f
private const val BYTES_PER_MB = 1024L * 1024L
private const val BYTES_PER_GB = 1024L * 1024L * 1024L
private const val RES_4K = 2160

@Composable
fun StreamRow(
    stream: ParsedStream,
    onClick: () -> Unit,
    onCopyLink: () -> Unit,
    onCopyMagnet: () -> Unit,
    onOpenMagnet: () -> Unit,
    onOpenLink: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = NexusTheme.spacing
    var menuExpanded by remember { mutableStateOf(false) }

    val hasMagnet =
        remember(stream) {
            val torrent = stream.stream.source as? StreamSource.Torrent
            torrent != null && MagnetLink.buildMagnet(torrent.infoHash, stream.displayName, torrent.trackers) != null
        }

    NexusCard(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = MIN_ROW_HEIGHT.dp)
                .clickable(onClick = onClick),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                StreamBadgesRow(stream)

                Text(
                    text = stream.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                StreamDetailsRow(stream)
            }

            Box {
                NexusIconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(MIN_ROW_HEIGHT.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.streams_more_options, stream.displayName),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                StreamOverflowMenu(
                    expanded = menuExpanded,
                    stream = stream,
                    hasMagnet = hasMagnet,
                    onDismiss = { menuExpanded = false },
                    onCopyLink = {
                        menuExpanded = false
                        onCopyLink()
                    },
                    onCopyMagnet = {
                        menuExpanded = false
                        onCopyMagnet()
                    },
                    onOpenMagnet = {
                        menuExpanded = false
                        onOpenMagnet()
                    },
                    onOpenLink = {
                        menuExpanded = false
                        onOpenLink()
                    },
                    onOpenExternalPlayer = {
                        menuExpanded = false
                        onClick()
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StreamBadgesRow(stream: ParsedStream) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(NexusTheme.spacing.xs),
        verticalArrangement = Arrangement.spacedBy(NexusTheme.spacing.xs),
    ) {
        stream.resolution?.let { res ->
            StreamChipBadge(if (res == RES_4K) "4K" else "${res}p")
        }
        stream.hdr.forEach { hdr ->
            StreamChipBadge(
                when (hdr) {
                    HdrKind.HDR -> "HDR"
                    HdrKind.HDR10 -> "HDR10"
                    HdrKind.HDR10_PLUS -> "HDR10+"
                    HdrKind.DOLBY_VISION -> "DV"
                },
            )
        }
        stream.codec?.let { codec ->
            StreamChipBadge(
                when (codec) {
                    Codec.HEVC -> "HEVC"
                    Codec.AVC -> "AVC"
                    Codec.AV1 -> "AV1"
                },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StreamDetailsRow(stream: ParsedStream) {
    val spacing = NexusTheme.spacing
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        verticalArrangement = Arrangement.spacedBy(spacing.xxs),
    ) {
        val sizeFormatted = formatFileSize(stream.sizeBytes)
        if (sizeFormatted != null) {
            Text(
                text = sizeFormatted,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        stream.seeders?.let { seeders ->
            Text(
                text = stringResource(R.string.streams_seeders, seeders),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        val addonLabel = buildAddonLabel(stream.addonNames)
        Text(
            text = addonLabel,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )

        when (stream.kind) {
            StreamKind.Direct -> StreamChipBadge(stringResource(R.string.streams_badge_direct))
            StreamKind.Torrent -> StreamChipBadge(stringResource(R.string.streams_badge_torrent))
            StreamKind.ExternalLink -> StreamChipBadge(stringResource(R.string.streams_badge_link))
        }

        if (stream.needsHeaders) {
            StreamChipBadge(stringResource(R.string.streams_badge_headers))
        }
    }
}

@Composable
private fun StreamChipBadge(text: String) {
    Surface(
        shape = NexusTheme.shapes.chip,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = NexusTheme.spacing.xs, vertical = NexusTheme.spacing.xxs),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StreamOverflowMenu(
    expanded: Boolean,
    stream: ParsedStream,
    hasMagnet: Boolean,
    onDismiss: () -> Unit,
    onCopyLink: () -> Unit,
    onCopyMagnet: () -> Unit,
    onOpenMagnet: () -> Unit,
    onOpenLink: () -> Unit,
    onOpenExternalPlayer: () -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
    ) {
        when (stream.kind) {
            StreamKind.Direct -> {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.streams_menu_open_external)) },
                    onClick = onOpenExternalPlayer,
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.streams_menu_copy_link)) },
                    onClick = onCopyLink,
                )
            }

            StreamKind.Torrent -> {
                if (hasMagnet) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.streams_menu_copy_magnet)) },
                        onClick = onCopyMagnet,
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.streams_menu_open_magnet)) },
                        onClick = onOpenMagnet,
                    )
                }
            }

            StreamKind.ExternalLink -> {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.streams_menu_open_link)) },
                    onClick = onOpenLink,
                )
            }
        }
    }
}

@Composable
fun StreamSkeletonRow(modifier: Modifier = Modifier) {
    val spacing = NexusTheme.spacing
    NexusCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(width = 80.dp, height = 16.dp)
                            .clip(NexusTheme.shapes.chip)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                )
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth(SKELETON_WIDTH_FRACTION)
                            .height(18.dp)
                            .clip(NexusTheme.shapes.button)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                )
                Box(
                    modifier =
                        Modifier
                            .size(width = 140.dp, height = 14.dp)
                            .clip(NexusTheme.shapes.button)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                )
            }
        }
    }
}

private fun formatFileSize(bytes: Long?): String? {
    if (bytes == null || bytes <= 0) return null
    return if (bytes >= BYTES_PER_GB) {
        val gb = bytes.toDouble() / BYTES_PER_GB
        String.format(Locale.US, "%.1f GB", gb)
    } else {
        val mb = bytes.toDouble() / BYTES_PER_MB
        String.format(Locale.US, "%.0f MB", mb)
    }
}

private fun buildAddonLabel(addonNames: List<String>): String =
    if (addonNames.isEmpty()) {
        ""
    } else if (addonNames.size == 1) {
        addonNames[0]
    } else {
        "${addonNames[0]} +${addonNames.size - 1}"
    }
