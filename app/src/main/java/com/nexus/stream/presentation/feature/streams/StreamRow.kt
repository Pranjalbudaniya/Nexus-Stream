package com.nexus.stream.presentation.feature.streams

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexus.stream.R
import com.nexus.stream.domain.model.Codec
import com.nexus.stream.domain.model.HdrKind
import com.nexus.stream.domain.model.ParsedStream
import com.nexus.stream.domain.model.ReleaseType
import com.nexus.stream.domain.model.StreamKind
import com.nexus.stream.domain.model.StreamMatch
import com.nexus.stream.domain.model.StreamSource
import com.nexus.stream.domain.usecase.MagnetLink
import com.nexus.stream.domain.usecase.StreamParsing
import com.nexus.stream.presentation.common.NexusCard
import com.nexus.stream.presentation.common.NexusIconButton
import com.nexus.stream.presentation.theme.NexusTheme
import java.util.Locale

private const val MIN_ROW_HEIGHT = 48
private const val BYTES_PER_MB = 1024L * 1024L
private const val BYTES_PER_GB = 1024L * 1024L * 1024L
private const val RES_4K = 2160

@Composable
fun StreamRow(
    stream: ParsedStream,
    onClick: () -> Unit,
    onShowDetails: () -> Unit,
    onCopyFileName: (String) -> Unit,
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
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                // Line 1: Addon name header
                val headerTitle = resolveHeaderTitle(stream)
                Text(
                    text = headerTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                // Line 2: Full file name (up to 3 lines)
                val fileNameText = stream.fileName ?: stream.displayName
                Text(
                    text = fileNameText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )

                // Line 3: 527 seeders · 359.77 MB · NyaaSi
                val statsLine = buildStatsLine(stream)
                if (statsLine != null) {
                    Text(
                        text = statsLine,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Line 4: Languages line (flags and names; omit if none)
                val languagesLine = buildLanguagesLine(stream)
                if (languagesLine != null) {
                    Text(
                        text = languagesLine,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Line 5: Badges row (shape shapes.chip)
                StreamBadgesRow(stream)

                // Line 6: via Addon and warning/header badges
                StreamFooterBadgesRow(stream)
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
                    onShowDetails = {
                        menuExpanded = false
                        onShowDetails()
                    },
                    onCopyFileName = {
                        menuExpanded = false
                        stream.fileName?.let { onCopyFileName(it) }
                    },
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
        val badges = remember(stream) { collectBadges(stream) }
        badges.forEach { StreamChipBadge(it) }
        if (stream.debrid?.cached == true) {
            StreamChipBadge(stringResource(R.string.streams_badge_cached, stream.debrid.service))
        }
        if (stream.isPack) {
            StreamChipBadge(stringResource(R.string.streams_badge_pack))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StreamFooterBadgesRow(stream: ParsedStream) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(NexusTheme.spacing.xs),
        verticalArrangement = Arrangement.spacedBy(NexusTheme.spacing.xs),
        modifier = Modifier.padding(top = NexusTheme.spacing.xxs),
    ) {
        val addonLabel =
            when {
                stream.addonNames.isEmpty() -> ""
                stream.addonNames.size == 1 -> "via ${stream.addonNames[0]}"
                else -> "via ${stream.addonNames[0]} (+${stream.addonNames.size - 1})"
            }
        if (addonLabel.isNotBlank()) {
            Text(
                text = addonLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
        }

        when (stream.kind) {
            StreamKind.Direct -> StreamChipBadge(stringResource(R.string.streams_badge_direct))
            StreamKind.Torrent -> StreamChipBadge(stringResource(R.string.streams_badge_torrent))
            StreamKind.ExternalLink -> StreamChipBadge(stringResource(R.string.streams_badge_link))
        }

        when (stream.match) {
            StreamMatch.WRONG_EPISODE -> {
                StreamWarningBadge(stringResource(R.string.streams_badge_wrong_episode))
            }

            StreamMatch.WRONG_SEASON -> {
                StreamWarningBadge(stringResource(R.string.streams_badge_wrong_season))
            }

            StreamMatch.WRONG_YEAR -> {
                StreamWarningBadge(stringResource(R.string.streams_badge_wrong_year))
            }

            StreamMatch.OK, StreamMatch.UNKNOWN -> {
            }
        }

        if (stream.releaseType == ReleaseType.CAM) {
            StreamWarningBadge(stringResource(R.string.streams_badge_cam))
        }

        if (stream.needsHeaders) {
            StreamChipBadge(stringResource(R.string.streams_badge_special_headers))
        }
    }
}

@Composable
private fun StreamChipBadge(
    text: String,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Surface(
        shape = NexusTheme.shapes.chip,
        color = containerColor,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = NexusTheme.spacing.xs, vertical = NexusTheme.spacing.xxs),
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
        )
    }
}

@Composable
private fun StreamWarningBadge(text: String) {
    StreamChipBadge(
        text = text,
        containerColor = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    )
}

@Composable
private fun StreamOverflowMenu(
    expanded: Boolean,
    stream: ParsedStream,
    hasMagnet: Boolean,
    onDismiss: () -> Unit,
    onShowDetails: () -> Unit,
    onCopyFileName: () -> Unit,
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
        DropdownMenuItem(
            text = { Text(stringResource(R.string.streams_menu_details)) },
            onClick = onShowDetails,
        )

        if (stream.fileName != null) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.streams_menu_copy_filename)) },
                onClick = onCopyFileName,
            )
        }

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

private fun resolveHeaderTitle(stream: ParsedStream): String {
    val lines =
        stream.stream.name
            ?.lines()
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
    val withoutRes = lines?.filter { StreamParsing.parseResolution(it) == null }
    return if (!withoutRes.isNullOrEmpty()) {
        withoutRes.joinToString(" · ")
    } else {
        stream.addonNames.firstOrNull() ?: stream.displayName
    }
}

@Composable
private fun buildStatsLine(stream: ParsedStream): String? {
    val parts = mutableListOf<String>()
    stream.seeders?.let {
        parts.add(stringResource(R.string.streams_seeders_count, it))
    }
    stream.sizeBytes?.takeIf { it > 0 }?.let { bytes ->
        val sizeStr =
            if (bytes >= BYTES_PER_GB) {
                String.format(Locale.US, "%.2f GB", bytes.toDouble() / BYTES_PER_GB)
            } else {
                String.format(Locale.US, "%.1f MB", bytes.toDouble() / BYTES_PER_MB)
            }
        parts.add(sizeStr)
    }
    stream.sourceSite?.let {
        parts.add(it)
    }
    return if (parts.isEmpty()) null else parts.joinToString(" · ")
}

private fun buildLanguagesLine(stream: ParsedStream): String? {
    val flags = stream.languages.flags.joinToString(" ")
    val textParts = mutableListOf<String>()
    if (stream.languages.dubbed) textParts.add("Dubbed")
    if (stream.languages.dualAudio) textParts.add("Dual audio")
    if (stream.languages.multi) textParts.add("Multi")
    if (stream.languages.names.isNotEmpty()) {
        textParts.add(stream.languages.names.joinToString(", "))
    }
    val text = textParts.joinToString(" · ")
    return when {
        flags.isNotBlank() && text.isNotBlank() -> "$flags  $text"
        flags.isNotBlank() -> flags
        text.isNotBlank() -> text
        else -> null
    }
}

private fun buildAudioBadge(stream: ParsedStream): String? {
    val parts = mutableListOf<String>()
    if (stream.audio.isNotEmpty()) {
        parts.add(stream.audio.joinToString(" ") { it.name.replace('_', ' ') })
    }
    if (stream.channels != null) {
        parts.add(stream.channels)
    }
    return if (parts.isEmpty()) null else parts.joinToString(" ")
}

private fun collectBadges(stream: ParsedStream): List<String> {
    val result = mutableListOf<String>()
    stream.resolution?.let { res ->
        result.add(if (res == RES_4K) "4K" else "${res}p")
    }
    stream.releaseType?.let { rel ->
        if (rel != ReleaseType.CAM) {
            result.add(rel.name)
        }
    }
    stream.codec?.let { result.add(it.name) }
    stream.hdr.forEach { hdr ->
        val label =
            when (hdr) {
                HdrKind.HDR -> "HDR"
                HdrKind.HDR10 -> "HDR10"
                HdrKind.HDR10_PLUS -> "HDR10+"
                HdrKind.DOLBY_VISION -> "DV"
            }
        result.add(label)
    }
    buildAudioBadge(stream)?.let { result.add(it) }
    return result
}
