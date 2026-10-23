package com.nexus.stream.presentation.feature.settings

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.stream.R
import com.nexus.stream.domain.model.SubtitleBackground
import com.nexus.stream.domain.model.SubtitleEdge
import com.nexus.stream.domain.model.SubtitleSize
import com.nexus.stream.domain.model.SubtitleStyle
import com.nexus.stream.domain.usecase.PREFERRED_LANGUAGE_OPTIONS
import com.nexus.stream.domain.usecase.SUBTITLES_OFF
import com.nexus.stream.presentation.common.NexusCard
import com.nexus.stream.presentation.common.NexusTextButton
import com.nexus.stream.presentation.theme.NexusTheme
import com.nexus.stream.presentation.theme.tokens.PlayerOverlayColors
import com.nexus.stream.presentation.theme.tokens.SubtitlePalette

private const val DIALOG_MAX_HEIGHT_DP = 360
private const val COLOR_INDICATOR_SIZE_DP = 14
private const val RADIO_COLOR_SIZE_DP = 16

private val PREVIEW_FONT_SIZES =
    mapOf(
        SubtitleSize.NANO to 14.sp,
        SubtitleSize.MICRO to 16.sp,
        SubtitleSize.SMALLEST to 18.sp,
        SubtitleSize.SMALL to 20.sp,
        SubtitleSize.NORMAL to 22.sp,
        SubtitleSize.BIG to 24.sp,
        SubtitleSize.HUGE to 28.sp,
    )

private val SIZE_LABELS =
    mapOf(
        SubtitleSize.NANO to R.string.settings_subtitle_size_nano,
        SubtitleSize.MICRO to R.string.settings_subtitle_size_micro,
        SubtitleSize.SMALLEST to R.string.settings_subtitle_size_smallest,
        SubtitleSize.SMALL to R.string.settings_subtitle_size_small,
        SubtitleSize.NORMAL to R.string.settings_subtitle_size_normal,
        SubtitleSize.BIG to R.string.settings_subtitle_size_big,
        SubtitleSize.HUGE to R.string.settings_subtitle_size_huge,
    )

private val BACKGROUND_LABELS =
    mapOf(
        SubtitleBackground.NONE to R.string.settings_subtitle_bg_none,
        SubtitleBackground.DIM to R.string.settings_subtitle_bg_dim,
        SubtitleBackground.SOLID to R.string.settings_subtitle_bg_solid,
    )

private val EDGE_LABELS =
    mapOf(
        SubtitleEdge.NONE to R.string.settings_subtitle_edge_none,
        SubtitleEdge.OUTLINE to R.string.settings_subtitle_edge_outline,
        SubtitleEdge.SHADOW to R.string.settings_subtitle_edge_shadow,
    )

private val COLOR_LABELS =
    mapOf(
        "white" to R.string.settings_subtitle_color_white,
        "yellow" to R.string.settings_subtitle_color_yellow,
        "cyan" to R.string.settings_subtitle_color_cyan,
        "green" to R.string.settings_subtitle_color_green,
        "orange" to R.string.settings_subtitle_color_orange,
    )

private enum class SubtitleDialogType {
    AUDIO_LANG,
    SUB_LANG,
    SIZE,
    COLOR,
    BACKGROUND,
    EDGE,
}

private val DIALOG_TITLES =
    mapOf(
        SubtitleDialogType.AUDIO_LANG to R.string.settings_audio_language_title,
        SubtitleDialogType.SUB_LANG to R.string.settings_subtitle_language_title,
        SubtitleDialogType.SIZE to R.string.settings_subtitle_size_title,
        SubtitleDialogType.COLOR to R.string.settings_subtitle_color_title,
        SubtitleDialogType.BACKGROUND to R.string.settings_subtitle_background_title,
        SubtitleDialogType.EDGE to R.string.settings_subtitle_edge_title,
    )

@Composable
fun SubtitleSettingsSection(
    uiState: SettingsUiState,
    onEvent: (SettingsEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = NexusTheme.spacing
    var activeDialog by remember { mutableStateOf<SubtitleDialogType?>(null) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        val audioSubtitle =
            uiState.preferredAudioLanguage?.let { code ->
                PREFERRED_LANGUAGE_OPTIONS.firstOrNull { it.code == code }?.name ?: code
            } ?: stringResource(R.string.settings_language_dont_change)
        SettingClickableCard(
            title = stringResource(R.string.settings_audio_language_title),
            subtitle = audioSubtitle,
            onClick = { activeDialog = SubtitleDialogType.AUDIO_LANG },
        )

        val subSubtitle =
            when (val code = uiState.preferredSubtitleLanguage) {
                null -> stringResource(R.string.settings_language_dont_change)
                SUBTITLES_OFF -> stringResource(R.string.settings_subtitle_language_off)
                else -> PREFERRED_LANGUAGE_OPTIONS.firstOrNull { it.code == code }?.name ?: code
            }
        SettingClickableCard(
            title = stringResource(R.string.settings_subtitle_language_title),
            subtitle = subSubtitle,
            onClick = { activeDialog = SubtitleDialogType.SUB_LANG },
        )

        Text(
            text = stringResource(R.string.settings_subtitles_group_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = spacing.xs),
        )

        val sizeLabelRes = SIZE_LABELS.getValue(uiState.subtitleStyle.size)
        SettingClickableCard(
            title = stringResource(R.string.settings_subtitle_size_title),
            subtitle = stringResource(sizeLabelRes),
            onClick = { activeDialog = SubtitleDialogType.SIZE },
        )

        NexusCard(
            modifier = Modifier.fillMaxWidth(),
            onClick = { activeDialog = SubtitleDialogType.COLOR },
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(NexusTheme.spacing.md),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(NexusTheme.spacing.xxs)) {
                    Text(
                        text = stringResource(R.string.settings_subtitle_color_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(NexusTheme.spacing.xs),
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .size(COLOR_INDICATOR_SIZE_DP.dp)
                                    .clip(CircleShape)
                                    .background(SubtitlePalette.colorFor(uiState.subtitleStyle.colorId)),
                        )
                        val colorLabelRes =
                            COLOR_LABELS[uiState.subtitleStyle.colorId.lowercase()]
                                ?: R.string.settings_subtitle_color_white
                        Text(
                            text = stringResource(colorLabelRes),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        val bgLabelRes = BACKGROUND_LABELS.getValue(uiState.subtitleStyle.background)
        SettingClickableCard(
            title = stringResource(R.string.settings_subtitle_background_title),
            subtitle = stringResource(bgLabelRes),
            onClick = { activeDialog = SubtitleDialogType.BACKGROUND },
        )

        val edgeLabelRes = EDGE_LABELS.getValue(uiState.subtitleStyle.edge)
        SettingClickableCard(
            title = stringResource(R.string.settings_subtitle_edge_title),
            subtitle = stringResource(edgeLabelRes),
            onClick = { activeDialog = SubtitleDialogType.EDGE },
        )

        NexusCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(NexusTheme.spacing.md),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.settings_subtitle_bold_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Switch(
                    checked = uiState.subtitleStyle.bold,
                    onCheckedChange = { onEvent(SettingsEvent.SetSubtitleBold(it)) },
                )
            }
        }

        SubtitlePreviewBox(style = uiState.subtitleStyle)
    }

    activeDialog?.let { dialogType ->
        SubtitleChoiceDialog(
            dialogType = dialogType,
            uiState = uiState,
            onEvent = onEvent,
            onDismiss = { activeDialog = null },
        )
    }
}

@Composable
private fun SettingClickableCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    NexusCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(NexusTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(NexusTheme.spacing.xxs),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun SubtitlePreviewBox(
    style: SubtitleStyle,
    modifier: Modifier = Modifier,
) {
    val fontSize = PREVIEW_FONT_SIZES[style.size] ?: 22.sp

    val backgroundColor =
        when (style.background) {
            SubtitleBackground.NONE -> Color.Transparent
            SubtitleBackground.DIM -> Color.Black.copy(alpha = 0.5f)
            SubtitleBackground.SOLID -> Color.Black
        }

    val shadow =
        when (style.edge) {
            SubtitleEdge.NONE -> {
                Shadow.None
            }

            SubtitleEdge.OUTLINE -> {
                Shadow(
                    color = Color.Black,
                    offset = Offset(1.5f, 1.5f),
                    blurRadius = 1.5f,
                )
            }

            SubtitleEdge.SHADOW -> {
                Shadow(
                    color = Color.Black.copy(alpha = 0.8f),
                    offset = Offset(3f, 3f),
                    blurRadius = 6f,
                )
            }
        }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(NexusTheme.spacing.xs),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(NexusTheme.shapes.card)
                    .background(PlayerOverlayColors.Card)
                    .padding(vertical = NexusTheme.spacing.lg, horizontal = NexusTheme.spacing.md),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.settings_subtitle_preview_sample),
                style =
                    TextStyle(
                        fontSize = fontSize,
                        fontWeight = if (style.bold) FontWeight.Bold else FontWeight.Normal,
                        color = SubtitlePalette.colorFor(style.colorId),
                        background = backgroundColor,
                        shadow = shadow,
                    ),
                modifier = Modifier.padding(horizontal = NexusTheme.spacing.xs, vertical = NexusTheme.spacing.xxs),
            )
        }
        Text(
            text = stringResource(R.string.settings_subtitle_preview_caption),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SubtitleChoiceDialog(
    dialogType: SubtitleDialogType,
    uiState: SettingsUiState,
    onEvent: (SettingsEvent) -> Unit,
    onDismiss: () -> Unit,
) {
    val titleRes = DIALOG_TITLES.getValue(dialogType)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(titleRes)) },
        text = {
            SubtitleDialogContent(
                dialogType = dialogType,
                uiState = uiState,
                onEvent = onEvent,
                onDismiss = onDismiss,
            )
        },
        confirmButton = {},
        dismissButton = {
            NexusTextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel))
            }
        },
    )
}

@Composable
private fun SubtitleDialogContent(
    dialogType: SubtitleDialogType,
    uiState: SettingsUiState,
    onEvent: (SettingsEvent) -> Unit,
    onDismiss: () -> Unit,
) {
    when (dialogType) {
        SubtitleDialogType.AUDIO_LANG -> {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = DIALOG_MAX_HEIGHT_DP.dp),
            ) {
                item {
                    SubtitleDialogRow(
                        label = stringResource(R.string.settings_language_dont_change),
                        selected = uiState.preferredAudioLanguage == null,
                        onClick = {
                            onEvent(SettingsEvent.SetPreferredAudioLanguage(null))
                            onDismiss()
                        },
                    )
                }
                items(PREFERRED_LANGUAGE_OPTIONS, key = { it.code }) { option ->
                    SubtitleDialogRow(
                        label = option.name,
                        selected = uiState.preferredAudioLanguage == option.code,
                        onClick = {
                            onEvent(SettingsEvent.SetPreferredAudioLanguage(option.code))
                            onDismiss()
                        },
                    )
                }
            }
        }

        SubtitleDialogType.SUB_LANG -> {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = DIALOG_MAX_HEIGHT_DP.dp),
            ) {
                item {
                    SubtitleDialogRow(
                        label = stringResource(R.string.settings_language_dont_change),
                        selected = uiState.preferredSubtitleLanguage == null,
                        onClick = {
                            onEvent(SettingsEvent.SetPreferredSubtitleLanguage(null))
                            onDismiss()
                        },
                    )
                }
                item {
                    SubtitleDialogRow(
                        label = stringResource(R.string.settings_subtitle_language_off),
                        selected = uiState.preferredSubtitleLanguage == SUBTITLES_OFF,
                        onClick = {
                            onEvent(SettingsEvent.SetPreferredSubtitleLanguage(SUBTITLES_OFF))
                            onDismiss()
                        },
                    )
                }
                items(PREFERRED_LANGUAGE_OPTIONS, key = { it.code }) { option ->
                    SubtitleDialogRow(
                        label = option.name,
                        selected = uiState.preferredSubtitleLanguage == option.code,
                        onClick = {
                            onEvent(SettingsEvent.SetPreferredSubtitleLanguage(option.code))
                            onDismiss()
                        },
                    )
                }
            }
        }

        SubtitleDialogType.SIZE -> {
            Column(modifier = Modifier.fillMaxWidth()) {
                SubtitleSize.entries.forEach { size ->
                    SubtitleDialogRow(
                        label = stringResource(SIZE_LABELS.getValue(size)),
                        selected = uiState.subtitleStyle.size == size,
                        onClick = {
                            onEvent(SettingsEvent.SetSubtitleSize(size))
                            onDismiss()
                        },
                    )
                }
            }
        }

        SubtitleDialogType.COLOR -> {
            Column(modifier = Modifier.fillMaxWidth()) {
                SubtitlePalette.colors.forEach { item ->
                    val colorLabelRes =
                        COLOR_LABELS[item.id.lowercase()] ?: R.string.settings_subtitle_color_white
                    SubtitleDialogRow(
                        label = stringResource(colorLabelRes),
                        color = SubtitlePalette.colorFor(item.id),
                        selected = uiState.subtitleStyle.colorId.equals(item.id, ignoreCase = true),
                        onClick = {
                            onEvent(SettingsEvent.SetSubtitleColor(item.id))
                            onDismiss()
                        },
                    )
                }
            }
        }

        SubtitleDialogType.BACKGROUND -> {
            Column(modifier = Modifier.fillMaxWidth()) {
                SubtitleBackground.entries.forEach { bg ->
                    SubtitleDialogRow(
                        label = stringResource(BACKGROUND_LABELS.getValue(bg)),
                        selected = uiState.subtitleStyle.background == bg,
                        onClick = {
                            onEvent(SettingsEvent.SetSubtitleBackground(bg))
                            onDismiss()
                        },
                    )
                }
            }
        }

        SubtitleDialogType.EDGE -> {
            Column(modifier = Modifier.fillMaxWidth()) {
                SubtitleEdge.entries.forEach { edge ->
                    SubtitleDialogRow(
                        label = stringResource(EDGE_LABELS.getValue(edge)),
                        selected = uiState.subtitleStyle.edge == edge,
                        onClick = {
                            onEvent(SettingsEvent.SetSubtitleEdge(edge))
                            onDismiss()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SubtitleDialogRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    color: Color? = null,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .selectable(
                    selected = selected,
                    role = Role.RadioButton,
                    onClick = onClick,
                ).padding(vertical = NexusTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
        )
        if (color != null) {
            Box(
                modifier =
                    Modifier
                        .padding(start = NexusTheme.spacing.sm)
                        .size(RADIO_COLOR_SIZE_DP.dp)
                        .clip(CircleShape)
                        .background(color),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = NexusTheme.spacing.sm),
        )
    }
}

@Preview(name = "Subtitle Preview Light", showBackground = true)
@Composable
internal fun SubtitlePreviewLight() {
    NexusTheme {
        SubtitlePreviewBox(style = SubtitleStyle())
    }
}

@Preview(name = "Subtitle Preview Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun SubtitlePreviewDark() {
    NexusTheme {
        SubtitlePreviewBox(
            style =
                SubtitleStyle(
                    size = SubtitleSize.BIG,
                    colorId = "yellow",
                    background = SubtitleBackground.DIM,
                    edge = SubtitleEdge.SHADOW,
                    bold = true,
                ),
        )
    }
}
