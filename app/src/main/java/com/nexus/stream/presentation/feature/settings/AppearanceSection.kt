package com.nexus.stream.presentation.feature.settings

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.nexus.stream.R
import com.nexus.stream.domain.model.CardStyle
import com.nexus.stream.domain.model.ContrastLevel
import com.nexus.stream.domain.model.Roundness
import com.nexus.stream.domain.model.TextSize
import com.nexus.stream.domain.model.ThemeMode
import com.nexus.stream.domain.model.ThemeSettings
import com.nexus.stream.domain.model.UiSize
import com.nexus.stream.presentation.common.AccentPicker
import com.nexus.stream.presentation.common.CustomColorSheet
import com.nexus.stream.presentation.common.EnumSegmentedRow
import com.nexus.stream.presentation.common.FontPickerSheet
import com.nexus.stream.presentation.common.NexusButton
import com.nexus.stream.presentation.common.NexusOutlinedButton
import com.nexus.stream.presentation.common.NexusSlider
import com.nexus.stream.presentation.common.NexusTextButton
import com.nexus.stream.presentation.common.fontDisplayName
import com.nexus.stream.presentation.theme.NexusTheme
import com.nexus.stream.presentation.theme.tokens.BrandAccent
import kotlin.math.roundToInt

private const val SLIDER_STEPS = 3
private const val SLIDER_MAX = 4f

@Composable
fun AppearanceSection(
    state: AppearanceUiState,
    onEvent: (AppearanceEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = NexusTheme.spacing

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        // 1. Theme
        AppearanceThemeRow(
            selectedMode = state.settings.mode,
            onSelect = { onEvent(AppearanceEvent.SetThemeMode(it)) },
        )

        // 2. Dynamic color
        SettingsSwitchRow(
            title = stringResource(R.string.settings_appearance_dynamic),
            subtitle = stringResource(R.string.settings_appearance_dynamic_desc),
            checked = state.settings.dynamicColor,
            onCheckedChange = { onEvent(AppearanceEvent.SetDynamicColor(it)) },
        )

        // 3. AMOLED black
        val amoledNote =
            if (state.amoledAppliesInDarkOnlyNote) {
                stringResource(R.string.settings_appearance_amoled_light_note)
            } else {
                stringResource(R.string.settings_appearance_amoled_desc)
            }
        SettingsSwitchRow(
            title = stringResource(R.string.settings_appearance_amoled),
            subtitle = amoledNote,
            checked = state.settings.amoled,
            onCheckedChange = { onEvent(AppearanceEvent.SetAmoled(it)) },
        )

        // 4. Accent color
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            Text(
                text = stringResource(R.string.settings_appearance_accent),
                style = MaterialTheme.typography.bodyLarge,
            )
            AccentPicker(
                selectedAccent = state.settings.accentColor,
                recents = state.settings.recentAccents,
                enabled = state.accentEnabled,
                onPick = { onEvent(AppearanceEvent.PickAccent(it)) },
                onCustom = { onEvent(AppearanceEvent.OpenCustomColor) },
                onTurnOffDynamic = { onEvent(AppearanceEvent.TurnOffDynamic) },
            )
        }

        // 5. Contrast
        AppearanceContrastRow(
            selectedContrast = state.settings.contrast,
            enabled = state.contrastEnabled,
            onSelect = { onEvent(AppearanceEvent.SetContrast(it)) },
        )

        // 6. Font
        SettingsValueRow(
            title = stringResource(R.string.settings_appearance_font),
            value = stringResource(fontDisplayName(state.settings.font)),
            onClick = { onEvent(AppearanceEvent.OpenFontPicker) },
        )

        // 7. UI size
        AppearanceUiSizeSlider(
            uiSize = state.settings.uiSize,
            onSelect = { onEvent(AppearanceEvent.SetUiSize(it)) },
        )

        // 8. Text size
        AppearanceTextSizeSlider(
            textSize = state.settings.textSize,
            onSelect = { onEvent(AppearanceEvent.SetTextSize(it)) },
        )

        // 9. Roundness
        AppearanceRoundnessSlider(
            roundness = state.settings.roundness,
            onSelect = { onEvent(AppearanceEvent.SetRoundness(it)) },
        )

        // 10. Card style
        AppearanceCardStyleRow(
            selectedStyle = state.settings.cardStyle,
            onSelect = { onEvent(AppearanceEvent.SetCardStyle(it)) },
        )

        // 11. Reset appearance
        NexusOutlinedButton(
            onClick = { onEvent(AppearanceEvent.RequestReset) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.settings_appearance_reset_btn))
        }
    }

    AppearanceDialogs(state = state, onEvent = onEvent)
}

@Composable
private fun AppearanceThemeRow(
    selectedMode: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
) {
    val systemModeLabel = stringResource(R.string.settings_appearance_mode_system)
    val lightModeLabel = stringResource(R.string.settings_appearance_mode_light)
    val darkModeLabel = stringResource(R.string.settings_appearance_mode_dark)
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(NexusTheme.spacing.xs),
    ) {
        Text(
            text = stringResource(R.string.settings_appearance_theme_mode),
            style = MaterialTheme.typography.bodyLarge,
        )
        EnumSegmentedRow(
            options = ThemeMode.entries,
            selected = selectedMode,
            label = { mode ->
                when (mode) {
                    ThemeMode.SYSTEM -> systemModeLabel
                    ThemeMode.LIGHT -> lightModeLabel
                    ThemeMode.DARK -> darkModeLabel
                }
            },
            onSelect = onSelect,
        )
    }
}

@Composable
private fun AppearanceContrastRow(
    selectedContrast: ContrastLevel,
    enabled: Boolean,
    onSelect: (ContrastLevel) -> Unit,
) {
    val standardContrastLabel = stringResource(R.string.settings_appearance_contrast_standard)
    val mediumContrastLabel = stringResource(R.string.settings_appearance_contrast_medium)
    val highContrastLabel = stringResource(R.string.settings_appearance_contrast_high)
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(NexusTheme.spacing.xs),
    ) {
        Text(
            text = stringResource(R.string.settings_appearance_contrast),
            style = MaterialTheme.typography.bodyLarge,
        )
        if (!enabled) {
            Text(
                text = stringResource(R.string.settings_appearance_contrast_disabled_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        EnumSegmentedRow(
            options = ContrastLevel.entries,
            selected = selectedContrast,
            enabled = enabled,
            label = { level ->
                when (level) {
                    ContrastLevel.STANDARD -> standardContrastLabel
                    ContrastLevel.MEDIUM -> mediumContrastLabel
                    ContrastLevel.HIGH -> highContrastLabel
                }
            },
            onSelect = onSelect,
        )
    }
}

@Composable
private fun AppearanceCardStyleRow(
    selectedStyle: CardStyle,
    onSelect: (CardStyle) -> Unit,
) {
    val filledCardLabel = stringResource(R.string.settings_appearance_card_style_filled)
    val outlinedCardLabel = stringResource(R.string.settings_appearance_card_style_outlined)
    val elevatedCardLabel = stringResource(R.string.settings_appearance_card_style_elevated)
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(NexusTheme.spacing.xs),
    ) {
        Text(
            text = stringResource(R.string.settings_appearance_card_style),
            style = MaterialTheme.typography.bodyLarge,
        )
        EnumSegmentedRow(
            options = CardStyle.entries,
            selected = selectedStyle,
            label = { style ->
                when (style) {
                    CardStyle.FILLED -> filledCardLabel
                    CardStyle.OUTLINED -> outlinedCardLabel
                    CardStyle.ELEVATED -> elevatedCardLabel
                }
            },
            onSelect = onSelect,
        )
    }
}

@Composable
private fun AppearanceUiSizeSlider(
    uiSize: UiSize,
    onSelect: (UiSize) -> Unit,
) {
    var localIndex by remember(uiSize) {
        mutableFloatStateOf(uiSize.ordinal.toFloat())
    }
    val labels =
        listOf(
            stringResource(R.string.settings_appearance_size_small),
            stringResource(R.string.settings_appearance_size_normal),
            stringResource(R.string.settings_appearance_size_large),
            stringResource(R.string.settings_appearance_size_larger),
            stringResource(R.string.settings_appearance_size_largest),
        )
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(NexusTheme.spacing.xxs),
    ) {
        Text(
            text = stringResource(R.string.settings_appearance_ui_size),
            style = MaterialTheme.typography.bodyLarge,
        )
        NexusSlider(
            value = localIndex,
            onValueChange = { localIndex = it },
            valueRange = 0f..SLIDER_MAX,
            steps = SLIDER_STEPS,
            label = { value ->
                val idx = value.roundToInt().coerceIn(0, labels.lastIndex)
                labels[idx]
            },
            onValueChangeFinished = {
                val idx = localIndex.roundToInt().coerceIn(0, UiSize.entries.lastIndex)
                onSelect(UiSize.entries[idx])
            },
        )
    }
}

@Composable
private fun AppearanceTextSizeSlider(
    textSize: TextSize,
    onSelect: (TextSize) -> Unit,
) {
    var localIndex by remember(textSize) {
        mutableFloatStateOf(textSize.ordinal.toFloat())
    }
    val labels =
        listOf(
            stringResource(R.string.settings_appearance_size_small),
            stringResource(R.string.settings_appearance_size_normal),
            stringResource(R.string.settings_appearance_size_large),
            stringResource(R.string.settings_appearance_size_larger),
            stringResource(R.string.settings_appearance_size_largest),
        )
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(NexusTheme.spacing.xxs),
    ) {
        Text(
            text = stringResource(R.string.settings_appearance_text_size),
            style = MaterialTheme.typography.bodyLarge,
        )
        NexusSlider(
            value = localIndex,
            onValueChange = { localIndex = it },
            valueRange = 0f..SLIDER_MAX,
            steps = SLIDER_STEPS,
            label = { value ->
                val idx = value.roundToInt().coerceIn(0, labels.lastIndex)
                labels[idx]
            },
            onValueChangeFinished = {
                val idx = localIndex.roundToInt().coerceIn(0, TextSize.entries.lastIndex)
                onSelect(TextSize.entries[idx])
            },
        )
    }
}

@Composable
private fun AppearanceRoundnessSlider(
    roundness: Roundness,
    onSelect: (Roundness) -> Unit,
) {
    var localIndex by remember(roundness) {
        mutableFloatStateOf(roundness.ordinal.toFloat())
    }
    val labels =
        listOf(
            stringResource(R.string.settings_appearance_roundness_sharp),
            stringResource(R.string.settings_appearance_roundness_soft),
            stringResource(R.string.settings_appearance_roundness_normal),
            stringResource(R.string.settings_appearance_roundness_round),
            stringResource(R.string.settings_appearance_roundness_extra_round),
        )
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(NexusTheme.spacing.xxs),
    ) {
        Text(
            text = stringResource(R.string.settings_appearance_roundness),
            style = MaterialTheme.typography.bodyLarge,
        )
        NexusSlider(
            value = localIndex,
            onValueChange = { localIndex = it },
            valueRange = 0f..SLIDER_MAX,
            steps = SLIDER_STEPS,
            label = { value ->
                val idx = value.roundToInt().coerceIn(0, labels.lastIndex)
                labels[idx]
            },
            onValueChangeFinished = {
                val idx = localIndex.roundToInt().coerceIn(0, Roundness.entries.lastIndex)
                onSelect(Roundness.entries[idx])
            },
        )
    }
}

@Composable
private fun AppearanceDialogs(
    state: AppearanceUiState,
    onEvent: (AppearanceEvent) -> Unit,
) {
    if (state.showResetConfirm) {
        AlertDialog(
            onDismissRequest = { onEvent(AppearanceEvent.DismissReset) },
            title = { Text(stringResource(R.string.settings_appearance_reset_confirm_title)) },
            text = { Text(stringResource(R.string.settings_appearance_reset_confirm_message)) },
            confirmButton = {
                NexusButton(onClick = { onEvent(AppearanceEvent.ConfirmReset) }) {
                    Text(stringResource(R.string.settings_appearance_reset_confirm_action))
                }
            },
            dismissButton = {
                NexusTextButton(onClick = { onEvent(AppearanceEvent.DismissReset) }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            },
        )
    }

    if (state.showCustomColorSheet) {
        val initialColor = state.settings.accentColor?.let { Color(it.toInt()) } ?: BrandAccent
        CustomColorSheet(
            initialColor = initialColor,
            onDismiss = { onEvent(AppearanceEvent.DismissCustomColor) },
            onPreview = { _, _ -> },
            onApply = { argb, _ -> onEvent(AppearanceEvent.ApplyCustomAccent(argb)) },
        )
    }

    if (state.showFontPickerSheet) {
        FontPickerSheet(
            selected = state.settings.font,
            onSelect = {
                onEvent(AppearanceEvent.SetFont(it))
                onEvent(AppearanceEvent.DismissFontPicker)
            },
            onDismiss = { onEvent(AppearanceEvent.DismissFontPicker) },
        )
    }
}

@Preview(name = "AppearanceSection - Expanded")
@Composable
internal fun AppearanceSectionExpandedPreview() {
    NexusTheme {
        AppearanceSection(
            state = AppearanceUiState(settings = ThemeSettings()),
            onEvent = {},
        )
    }
}

@Preview(name = "AppearanceSection - Dynamic Color On", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun AppearanceSectionDynamicColorPreview() {
    NexusTheme {
        AppearanceSection(
            state = AppearanceUiState(settings = ThemeSettings(dynamicColor = true)),
            onEvent = {},
        )
    }
}

@Preview(name = "AppearanceSection - Reset Dialog")
@Composable
internal fun AppearanceSectionResetDialogPreview() {
    NexusTheme {
        AppearanceSection(
            state = AppearanceUiState(showResetConfirm = true),
            onEvent = {},
        )
    }
}
