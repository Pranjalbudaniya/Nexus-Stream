package com.nexus.stream.presentation.feature.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nexus.stream.R
import com.nexus.stream.data.player.PlayerTrack
import com.nexus.stream.data.player.PlayerTracks
import com.nexus.stream.data.player.SPEED_OPTIONS
import com.nexus.stream.data.player.formatSpeed
import com.nexus.stream.presentation.common.NexusIconButton
import com.nexus.stream.presentation.theme.NexusTheme
import com.nexus.stream.presentation.theme.tokens.PlayerOverlayColors

private val PANEL_MIN_WIDTH = 320.dp
private val PANEL_MAX_WIDTH = 420.dp
private val ITEM_MIN_HEIGHT = 48.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlayerSidePanel(
    tracks: PlayerTracks,
    currentRate: Float,
    onSetSpeed: (Float) -> Unit,
    onSelectAudio: (String) -> Unit,
    onSelectSubtitle: (String?) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier =
            modifier
                .fillMaxHeight()
                .widthIn(min = PANEL_MIN_WIDTH, max = PANEL_MAX_WIDTH),
        shape = NexusTheme.shapes.card,
        colors = CardDefaults.cardColors(containerColor = PlayerOverlayColors.Card),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(NexusTheme.spacing.md)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(NexusTheme.spacing.md),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.player_action_tracks_and_speed),
                    style = MaterialTheme.typography.titleMedium,
                    color = PlayerOverlayColors.Content,
                    modifier = Modifier.weight(1f),
                )
                NexusIconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.player_panel_close),
                        tint = PlayerOverlayColors.Content,
                    )
                }
            }

            // Speed section
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(NexusTheme.spacing.xs),
            ) {
                Text(
                    text = stringResource(R.string.player_panel_section_speed),
                    style = MaterialTheme.typography.titleSmall,
                    color = PlayerOverlayColors.ContentSecondary,
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(NexusTheme.spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(NexusTheme.spacing.xs),
                ) {
                    SPEED_OPTIONS.forEach { speed ->
                        val isSelected = currentRate == speed
                        FilterChip(
                            selected = isSelected,
                            onClick = { onSetSpeed(speed) },
                            label = { Text(formatSpeed(speed)) },
                            shape = NexusTheme.shapes.chip,
                            colors =
                                FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    labelColor = PlayerOverlayColors.Content,
                                ),
                        )
                    }
                }
            }

            // Audio tracks section
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(NexusTheme.spacing.xs),
            ) {
                Text(
                    text = stringResource(R.string.player_panel_section_audio),
                    style = MaterialTheme.typography.titleSmall,
                    color = PlayerOverlayColors.ContentSecondary,
                )
                if (tracks.audio.isEmpty()) {
                    Text(
                        text = stringResource(R.string.player_tracks_empty_audio),
                        style = MaterialTheme.typography.bodyMedium,
                        color = PlayerOverlayColors.ContentSecondary,
                        modifier = Modifier.padding(vertical = NexusTheme.spacing.xs),
                    )
                } else {
                    tracks.audio.forEach { track ->
                        RadioOptionRow(
                            label = track.name,
                            selected = track.id == tracks.selectedAudioId,
                            onClick = { onSelectAudio(track.id) },
                        )
                    }
                }
            }

            // Subtitle tracks section
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(NexusTheme.spacing.xs),
            ) {
                Text(
                    text = stringResource(R.string.player_panel_section_subtitles),
                    style = MaterialTheme.typography.titleSmall,
                    color = PlayerOverlayColors.ContentSecondary,
                )
                RadioOptionRow(
                    label = stringResource(R.string.player_track_off),
                    selected = tracks.selectedSubtitleId == null,
                    onClick = { onSelectSubtitle(null) },
                )
                tracks.subtitles.forEach { track ->
                    RadioOptionRow(
                        label = track.name,
                        selected = track.id == tracks.selectedSubtitleId,
                        onClick = { onSelectSubtitle(track.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RadioOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = ITEM_MIN_HEIGHT)
                .selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = PlayerOverlayColors.Content,
            modifier = Modifier.weight(1f),
        )
        RadioButton(
            selected = selected,
            onClick = null,
            colors =
                RadioButtonDefaults.colors(
                    selectedColor = MaterialTheme.colorScheme.primary,
                    unselectedColor = PlayerOverlayColors.ContentSecondary,
                ),
        )
    }
}

@Preview(name = "PlayerSidePanel - Light")
@Composable
internal fun PlayerSidePanelPreviewLight() {
    MaterialTheme {
        PlayerSidePanel(
            tracks =
                PlayerTracks(
                    audio =
                        listOf(
                            PlayerTrack("1", "English [Original]"),
                            PlayerTrack("2", "Spanish"),
                        ),
                    subtitles =
                        listOf(
                            PlayerTrack("10", "English"),
                            PlayerTrack("11", "French"),
                        ),
                    selectedAudioId = "1",
                    selectedSubtitleId = "10",
                ),
            currentRate = 1.0f,
            onSetSpeed = {},
            onSelectAudio = {},
            onSelectSubtitle = {},
            onClose = {},
        )
    }
}

@Preview(name = "PlayerSidePanel - Dark")
@Composable
internal fun PlayerSidePanelPreviewDark() {
    MaterialTheme {
        PlayerSidePanel(
            tracks =
                PlayerTracks(
                    audio =
                        listOf(
                            PlayerTrack("1", "English"),
                        ),
                    selectedAudioId = "1",
                    selectedSubtitleId = null,
                ),
            currentRate = 1.25f,
            onSetSpeed = {},
            onSelectAudio = {},
            onSelectSubtitle = {},
            onClose = {},
        )
    }
}
