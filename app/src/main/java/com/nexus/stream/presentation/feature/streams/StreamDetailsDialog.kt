package com.nexus.stream.presentation.feature.streams

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.nexus.stream.R
import com.nexus.stream.domain.model.ParsedStream
import com.nexus.stream.domain.model.StreamKind
import com.nexus.stream.domain.model.StreamMatch
import com.nexus.stream.domain.usecase.LanguageCodes
import com.nexus.stream.presentation.common.NexusTextButton
import com.nexus.stream.presentation.theme.NexusTheme

private const val LABEL_WEIGHT = 1.0f
private const val VALUE_WEIGHT = 1.5f

@Composable
fun StreamDetailsDialog(
    stream: ParsedStream,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.streams_details_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(NexusTheme.spacing.sm),
            ) {
                DetailRow(
                    label = stringResource(R.string.streams_details_addons),
                    value = stream.addonNames.joinToString(", ").ifBlank { "-" },
                )
                DetailRow(
                    label = stringResource(R.string.streams_details_quality),
                    value = stream.resolution?.let { "${it}p" } ?: "-",
                )
                DetailRow(
                    label = stringResource(R.string.streams_details_release_type),
                    value = stream.releaseType?.name ?: "-",
                )
                DetailRow(
                    label = stringResource(R.string.streams_details_filename),
                    value = stream.fileName ?: "-",
                )
                DetailRow(
                    label = stringResource(R.string.streams_details_size),
                    value = stream.sizeBytes?.let { formatBytes(it) } ?: "-",
                )
                DetailRow(
                    label = stringResource(R.string.streams_details_seeders),
                    value = stream.seeders?.toString() ?: "-",
                )
                DetailRow(
                    label = stringResource(R.string.streams_details_source_site),
                    value = stream.sourceSite ?: "-",
                )
                DetailRow(
                    label = stringResource(R.string.streams_details_languages),
                    value = formatLanguages(stream),
                )
                DetailRow(
                    label = stringResource(R.string.streams_details_language_codes),
                    value = formatLanguageCodes(stream),
                )
                DetailRow(
                    label = stringResource(R.string.streams_details_audio),
                    value = formatAudio(stream),
                )
                DetailRow(
                    label = stringResource(R.string.streams_details_codec_hdr),
                    value = formatCodecHdr(stream),
                )
                DetailRow(
                    label = stringResource(R.string.streams_details_type),
                    value = formatKind(stream.kind),
                )
                DetailRow(
                    label = stringResource(R.string.streams_details_cached),
                    value =
                        if (stream.debrid?.cached == true) {
                            stringResource(R.string.streams_details_yes)
                        } else {
                            stringResource(R.string.streams_details_no)
                        },
                )
                DetailRow(
                    label = stringResource(R.string.streams_details_pack),
                    value =
                        if (stream.isPack) {
                            stringResource(R.string.streams_details_yes)
                        } else {
                            stringResource(R.string.streams_details_no)
                        },
                )
                DetailRow(
                    label = stringResource(R.string.streams_details_match),
                    value = formatMatch(stream.match),
                )
                SpecialHeadersSection(needsHeaders = stream.needsHeaders)

                if (stream.infoLines.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(NexusTheme.spacing.xs))
                    Text(
                        text = stringResource(R.string.streams_details_raw_info),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SelectionContainer {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .background(
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = NexusTheme.shapes.card,
                                    ).padding(NexusTheme.spacing.sm),
                        ) {
                            Text(
                                text = stream.infoLines.joinToString("\n"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            NexusTextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.streams_details_close))
            }
        },
        modifier = modifier,
    )
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(LABEL_WEIGHT),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(VALUE_WEIGHT),
        )
    }
}

@Composable
private fun SpecialHeadersSection(
    needsHeaders: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        DetailRow(
            label = stringResource(R.string.streams_details_special_headers),
            value =
                if (needsHeaders) {
                    stringResource(R.string.streams_details_yes)
                } else {
                    stringResource(R.string.streams_details_no)
                },
        )
        if (needsHeaders) {
            Spacer(modifier = Modifier.height(NexusTheme.spacing.xxs))
            Text(
                text = stringResource(R.string.streams_details_special_headers_explanation),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun formatMatch(match: StreamMatch): String =
    when (match) {
        StreamMatch.OK -> stringResource(R.string.streams_match_ok)
        StreamMatch.WRONG_EPISODE -> stringResource(R.string.streams_badge_wrong_episode)
        StreamMatch.WRONG_SEASON -> stringResource(R.string.streams_badge_wrong_season)
        StreamMatch.WRONG_YEAR -> stringResource(R.string.streams_badge_wrong_year)
        StreamMatch.UNKNOWN -> stringResource(R.string.streams_match_unknown)
    }

@Composable
private fun formatKind(kind: StreamKind): String =
    when (kind) {
        StreamKind.Direct -> stringResource(R.string.streams_badge_direct)
        StreamKind.Torrent -> stringResource(R.string.streams_badge_torrent)
        StreamKind.ExternalLink -> stringResource(R.string.streams_badge_link)
    }

private fun formatLanguages(stream: ParsedStream): String {
    val parts = mutableListOf<String>()
    if (stream.languages.flags.isNotEmpty()) {
        parts.add(stream.languages.flags.joinToString(" "))
    }
    if (stream.languages.names.isNotEmpty()) {
        parts.add(stream.languages.names.joinToString(", "))
    }
    if (stream.languages.dualAudio) parts.add("Dual audio")
    if (stream.languages.dubbed) parts.add("Dubbed")
    if (stream.languages.multi) parts.add("Multi")
    return if (parts.isEmpty()) "-" else parts.joinToString(" · ")
}

private fun formatLanguageCodes(stream: ParsedStream): String {
    val codes = LanguageCodes.codes(stream.languages)
    return if (codes.isEmpty()) {
        "-"
    } else {
        codes.joinToString(", ") { LanguageCodes.languageDisplayName(it) }
    }
}

private fun formatAudio(stream: ParsedStream): String {
    val parts = mutableListOf<String>()
    if (stream.audio.isNotEmpty()) {
        parts.add(stream.audio.joinToString(", ") { it.name })
    }
    if (stream.channels != null) {
        parts.add(stream.channels)
    }
    return if (parts.isEmpty()) "-" else parts.joinToString(" ")
}

private fun formatCodecHdr(stream: ParsedStream): String {
    val parts = mutableListOf<String>()
    if (stream.codec != null) {
        parts.add(stream.codec.name)
    }
    if (stream.hdr.isNotEmpty()) {
        parts.add(stream.hdr.joinToString(", ") { it.name })
    }
    return if (parts.isEmpty()) "-" else parts.joinToString(" ")
}

private const val BYTES_IN_KB = 1024.0
private const val BYTES_IN_MB = 1024.0 * 1024.0
private const val BYTES_IN_GB = 1024.0 * 1024.0 * 1024.0

private fun formatBytes(bytes: Long): String =
    when {
        bytes >= BYTES_IN_GB -> String.format(java.util.Locale.US, "%.2f GB", bytes / BYTES_IN_GB)
        bytes >= BYTES_IN_MB -> String.format(java.util.Locale.US, "%.1f MB", bytes / BYTES_IN_MB)
        else -> String.format(java.util.Locale.US, "%.0f KB", bytes / BYTES_IN_KB)
    }
