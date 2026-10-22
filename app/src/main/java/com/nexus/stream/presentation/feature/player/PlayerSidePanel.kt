package com.nexus.stream.presentation.feature.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nexus.stream.R
import com.nexus.stream.data.player.PlayerTracks
import com.nexus.stream.data.player.SPEED_OPTIONS
import com.nexus.stream.data.player.formatSpeed
import com.nexus.stream.domain.usecase.formatSubtitleDelay
import com.nexus.stream.presentation.common.NexusIconButton
import com.nexus.stream.presentation.common.NexusOutlinedButton
import com.nexus.stream.presentation.common.NexusTextButton
import com.nexus.stream.presentation.theme.NexusTheme
import com.nexus.stream.presentation.theme.tokens.PlayerOverlayColors

private const val DRAWER_WIDTH_AUDIO_SPEED = 0.40f
private const val DRAWER_WIDTH_SUBTITLES = 0.46f
private val ITEM_MIN_HEIGHT = 48.dp
private val DELAY_STEP_BUTTON_SIZE = 40.dp
private val PROGRESS_SPINNER_SIZE = 24.dp
private val ITEM_PROGRESS_SPINNER_SIZE = 16.dp
private val FILE_ICON_SIZE = 18.dp
private const val SUBTITLE_DELAY_STEP_MS = 100L

@Composable
fun PlayerSidePanel(
    drawer: PlayerDrawer,
    tracks: PlayerTracks,
    currentRate: Float,
    subtitleDelayMs: Long,
    onlineSubtitles: OnlineSubtitlesState,
    applyingSubtitleKey: String?,
    subtitleError: SubtitleError?,
    onSetSpeed: (Float) -> Unit,
    onSelectAudio: (String) -> Unit,
    onSelectSubtitle: (String?) -> Unit,
    onSelectOnlineSubtitle: (String) -> Unit,
    onRefreshOnlineSubtitles: () -> Unit,
    onAddSubtitleFile: () -> Unit,
    onSubtitleDelay: (Long) -> Unit,
    onResetSubtitleDelay: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val widthFraction =
        when (drawer) {
            PlayerDrawer.AUDIO, PlayerDrawer.SPEED -> DRAWER_WIDTH_AUDIO_SPEED
            PlayerDrawer.SUBTITLES -> DRAWER_WIDTH_SUBTITLES
        }

    val titleRes =
        when (drawer) {
            PlayerDrawer.AUDIO -> R.string.player_drawer_audio
            PlayerDrawer.SPEED -> R.string.player_drawer_speed
            PlayerDrawer.SUBTITLES -> R.string.player_drawer_subtitles
        }

    Card(
        modifier =
            modifier
                .fillMaxHeight()
                .fillMaxWidth(widthFraction),
        shape = NexusTheme.shapes.card,
        colors = CardDefaults.cardColors(containerColor = PlayerOverlayColors.Card),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(NexusTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(NexusTheme.spacing.md),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(titleRes),
                    style = MaterialTheme.typography.titleMedium,
                    color = PlayerOverlayColors.Content,
                    modifier = Modifier.weight(1f),
                )
                NexusIconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.player_drawer_close),
                        tint = PlayerOverlayColors.Content,
                    )
                }
            }

            when (drawer) {
                PlayerDrawer.AUDIO -> {
                    AudioDrawerContent(
                        tracks = tracks,
                        onSelectAudio = onSelectAudio,
                        modifier = Modifier.weight(1f),
                    )
                }

                PlayerDrawer.SPEED -> {
                    SpeedDrawerContent(
                        currentRate = currentRate,
                        onSetSpeed = onSetSpeed,
                        modifier = Modifier.weight(1f),
                    )
                }

                PlayerDrawer.SUBTITLES -> {
                    SubtitlesDrawerContent(
                        tracks = tracks,
                        subtitleDelayMs = subtitleDelayMs,
                        onlineSubtitles = onlineSubtitles,
                        applyingSubtitleKey = applyingSubtitleKey,
                        subtitleError = subtitleError,
                        onSelectSubtitle = onSelectSubtitle,
                        onSelectOnlineSubtitle = onSelectOnlineSubtitle,
                        onRefreshOnlineSubtitles = onRefreshOnlineSubtitles,
                        onAddSubtitleFile = onAddSubtitleFile,
                        onSubtitleDelay = onSubtitleDelay,
                        onResetSubtitleDelay = onResetSubtitleDelay,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun AudioDrawerContent(
    tracks: PlayerTracks,
    onSelectAudio: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(NexusTheme.spacing.xs),
    ) {
        if (tracks.audio.isEmpty()) {
            Text(
                text = stringResource(R.string.player_audio_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = PlayerOverlayColors.ContentSecondary,
                modifier = Modifier.padding(vertical = NexusTheme.spacing.sm),
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
}

@Composable
private fun SpeedDrawerContent(
    currentRate: Float,
    onSetSpeed: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(NexusTheme.spacing.xs),
    ) {
        SPEED_OPTIONS.forEach { speed ->
            RadioOptionRow(
                label = formatSpeed(speed),
                selected = currentRate == speed,
                onClick = { onSetSpeed(speed) },
            )
        }
    }
}

@Composable
private fun SubtitlesDrawerContent(
    tracks: PlayerTracks,
    subtitleDelayMs: Long,
    onlineSubtitles: OnlineSubtitlesState,
    applyingSubtitleKey: String?,
    subtitleError: SubtitleError?,
    onSelectSubtitle: (String?) -> Unit,
    onSelectOnlineSubtitle: (String) -> Unit,
    onRefreshOnlineSubtitles: () -> Unit,
    onAddSubtitleFile: () -> Unit,
    onSubtitleDelay: (Long) -> Unit,
    onResetSubtitleDelay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(NexusTheme.spacing.md),
    ) {
        EmbeddedSubtitlesSection(
            tracks = tracks,
            onSelectSubtitle = onSelectSubtitle,
        )

        NexusOutlinedButton(
            onClick = onAddSubtitleFile,
            modifier = Modifier.fillMaxWidth(),
            colors =
                ButtonDefaults.outlinedButtonColors(
                    contentColor = PlayerOverlayColors.Content,
                ),
            border = BorderStroke(1.dp, PlayerOverlayColors.ContentSecondary.copy(alpha = 0.5f)),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_file_open),
                contentDescription = null,
                modifier = Modifier.size(FILE_ICON_SIZE),
                tint = PlayerOverlayColors.Content,
            )
            Spacer(modifier = Modifier.width(NexusTheme.spacing.sm))
            Text(
                text = stringResource(R.string.player_subtitles_add_file),
                style = MaterialTheme.typography.labelLarge,
                color = PlayerOverlayColors.Content,
            )
        }

        OnlineSubtitlesSection(
            onlineSubtitles = onlineSubtitles,
            applyingSubtitleKey = applyingSubtitleKey,
            onSelectOnlineSubtitle = onSelectOnlineSubtitle,
            onRefreshOnlineSubtitles = onRefreshOnlineSubtitles,
        )

        SubtitleDelaySection(
            subtitleDelayMs = subtitleDelayMs,
            onSubtitleDelay = onSubtitleDelay,
            onResetSubtitleDelay = onResetSubtitleDelay,
        )

        subtitleError?.let { err ->
            val errorRes =
                when (err) {
                    SubtitleError.SUBTITLE_LOAD_FAILED -> R.string.player_subtitles_error_apply_failed
                    SubtitleError.BAD_TYPE -> R.string.player_subtitles_error_bad_type
                    SubtitleError.TOO_LARGE -> R.string.player_subtitles_error_too_large
                    SubtitleError.FAILED -> R.string.player_subtitles_error_failed
                }
            Text(
                text = stringResource(errorRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = NexusTheme.spacing.xs),
            )
        }
    }
}

@Composable
private fun EmbeddedSubtitlesSection(
    tracks: PlayerTracks,
    onSelectSubtitle: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(NexusTheme.spacing.xs),
    ) {
        Text(
            text = stringResource(R.string.player_subtitles_embedded),
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

@Composable
private fun OnlineSubtitlesSection(
    onlineSubtitles: OnlineSubtitlesState,
    applyingSubtitleKey: String?,
    onSelectOnlineSubtitle: (String) -> Unit,
    onRefreshOnlineSubtitles: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(NexusTheme.spacing.xs),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.player_subtitles_online),
                style = MaterialTheme.typography.titleSmall,
                color = PlayerOverlayColors.ContentSecondary,
            )
            NexusIconButton(onClick = onRefreshOnlineSubtitles) {
                Icon(
                    painter = painterResource(R.drawable.ic_refresh),
                    contentDescription = stringResource(R.string.player_subtitles_refresh),
                    tint = PlayerOverlayColors.Content,
                )
            }
        }

        when (onlineSubtitles) {
            OnlineSubtitlesState.Idle -> {
                // Idle: no online subtitles searched yet
            }

            OnlineSubtitlesState.Loading -> {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(NexusTheme.spacing.md),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(PROGRESS_SPINNER_SIZE),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp,
                    )
                }
            }

            is OnlineSubtitlesState.Empty -> {
                val msg =
                    if (onlineSubtitles.noAddonInstalled) {
                        stringResource(R.string.player_subtitles_empty_no_addon)
                    } else {
                        stringResource(R.string.player_subtitles_empty_none_found)
                    }
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodyMedium,
                    color = PlayerOverlayColors.ContentSecondary,
                    modifier = Modifier.padding(vertical = NexusTheme.spacing.xs),
                )
            }

            OnlineSubtitlesState.Error -> {
                Text(
                    text = stringResource(R.string.player_subtitles_error_load),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = NexusTheme.spacing.xs),
                )
            }

            is OnlineSubtitlesState.Loaded -> {
                OnlineSubtitlesList(
                    loadedState = onlineSubtitles,
                    applyingSubtitleKey = applyingSubtitleKey,
                    onSelectOnlineSubtitle = onSelectOnlineSubtitle,
                )
            }
        }
    }
}

@Composable
private fun OnlineSubtitlesList(
    loadedState: OnlineSubtitlesState.Loaded,
    applyingSubtitleKey: String?,
    onSelectOnlineSubtitle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(NexusTheme.spacing.xs),
    ) {
        loadedState.groups.forEach { group ->
            Text(
                text = group.language,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = NexusTheme.spacing.xs),
            )
            group.items.forEach { item ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = ITEM_MIN_HEIGHT)
                            .clickable { onSelectOnlineSubtitle(item.key) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = NexusTheme.spacing.sm)) {
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = PlayerOverlayColors.Content,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (item.addonName != item.label) {
                            Text(
                                text = item.addonName,
                                style = MaterialTheme.typography.bodySmall,
                                color = PlayerOverlayColors.ContentSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (applyingSubtitleKey == item.key) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(ITEM_PROGRESS_SPINNER_SIZE),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SubtitleDelaySection(
    subtitleDelayMs: Long,
    onSubtitleDelay: (Long) -> Unit,
    onResetSubtitleDelay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(NexusTheme.spacing.xs),
    ) {
        Text(
            text = stringResource(R.string.player_subtitles_delay),
            style = MaterialTheme.typography.titleSmall,
            color = PlayerOverlayColors.ContentSecondary,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NexusTheme.spacing.sm),
        ) {
            NexusOutlinedButton(
                onClick = { onSubtitleDelay(-SUBTITLE_DELAY_STEP_MS) },
                modifier = Modifier.size(DELAY_STEP_BUTTON_SIZE),
                contentPadding = PaddingValues(0.dp),
                border = BorderStroke(1.dp, PlayerOverlayColors.ContentSecondary.copy(alpha = 0.5f)),
            ) {
                Text(
                    text = "-",
                    style = MaterialTheme.typography.titleMedium,
                    color = PlayerOverlayColors.Content,
                )
            }

            Text(
                text = formatSubtitleDelay(subtitleDelayMs),
                style = MaterialTheme.typography.bodyMedium,
                color = PlayerOverlayColors.Content,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )

            NexusOutlinedButton(
                onClick = { onSubtitleDelay(SUBTITLE_DELAY_STEP_MS) },
                modifier = Modifier.size(DELAY_STEP_BUTTON_SIZE),
                contentPadding = PaddingValues(0.dp),
                border = BorderStroke(1.dp, PlayerOverlayColors.ContentSecondary.copy(alpha = 0.5f)),
            ) {
                Text(
                    text = "+",
                    style = MaterialTheme.typography.titleMedium,
                    color = PlayerOverlayColors.Content,
                )
            }

            NexusTextButton(
                onClick = onResetSubtitleDelay,
            ) {
                Text(
                    text = stringResource(R.string.player_subtitles_delay_reset),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
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
            drawer = PlayerDrawer.SUBTITLES,
            tracks =
                PlayerTracks(
                    audio = emptyList(),
                    subtitles = emptyList(),
                    selectedAudioId = null,
                    selectedSubtitleId = null,
                ),
            currentRate = 1.0f,
            subtitleDelayMs = 200L,
            onlineSubtitles = OnlineSubtitlesState.Empty(noAddonInstalled = false),
            applyingSubtitleKey = null,
            subtitleError = null,
            onSetSpeed = {},
            onSelectAudio = {},
            onSelectSubtitle = {},
            onSelectOnlineSubtitle = {},
            onRefreshOnlineSubtitles = {},
            onAddSubtitleFile = {},
            onSubtitleDelay = {},
            onResetSubtitleDelay = {},
            onClose = {},
        )
    }
}

@Preview(name = "PlayerSidePanel - Dark")
@Composable
internal fun PlayerSidePanelPreviewDark() {
    MaterialTheme {
        PlayerSidePanel(
            drawer = PlayerDrawer.SPEED,
            tracks = PlayerTracks(),
            currentRate = 1.25f,
            subtitleDelayMs = 0L,
            onlineSubtitles = OnlineSubtitlesState.Idle,
            applyingSubtitleKey = null,
            subtitleError = null,
            onSetSpeed = {},
            onSelectAudio = {},
            onSelectSubtitle = {},
            onSelectOnlineSubtitle = {},
            onRefreshOnlineSubtitles = {},
            onAddSubtitleFile = {},
            onSubtitleDelay = {},
            onResetSubtitleDelay = {},
            onClose = {},
        )
    }
}
