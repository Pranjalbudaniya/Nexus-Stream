@file:Suppress("TooManyFunctions", "CyclomaticComplexMethod", "LongMethod")

package com.nexus.stream.dev

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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
import com.nexus.stream.presentation.common.NexusCard
import com.nexus.stream.presentation.common.NexusSlider
import com.nexus.stream.presentation.common.fontDisplayName
import com.nexus.stream.presentation.common.toHexString
import com.nexus.stream.presentation.theme.NexusTheme
import com.nexus.stream.presentation.theme.ThemeEvent
import com.nexus.stream.presentation.theme.accentSeed
import com.nexus.stream.presentation.theme.isDarkTheme
import com.nexus.stream.presentation.theme.tokens.fontFamilyFor
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeGalleryScreen(
    settings: ThemeSettings,
    onEvent: (ThemeEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showCustomSheet by rememberSaveable { mutableStateOf(false) }
    var showFontSheet by rememberSaveable { mutableStateOf(false) }
    var showResetDialog by rememberSaveable { mutableStateOf(false) }

    val systemDark = isSystemInDarkTheme()
    val isDark = isDarkTheme(settings.mode, systemDark)

    val effectiveAccentText =
        if (settings.dynamicColor) {
            stringResource(R.string.gallery_accent_from_wallpaper)
        } else {
            accentSeed(settings.accentColor).toHexString()
        }

    val appearanceResetMsg = stringResource(R.string.appearance_reset)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier.fillMaxSize(),
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.gallery_title),
                style = MaterialTheme.typography.headlineMedium,
            )

            Text(
                text = stringResource(R.string.gallery_effective_accent, effectiveAccentText),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
            )

            // Section: Colors
            SectionHeader(stringResource(R.string.gallery_section_colors))

            // Mode Selector
            val systemModeStr = stringResource(R.string.gallery_mode_system)
            val lightModeStr = stringResource(R.string.gallery_mode_light)
            val darkModeStr = stringResource(R.string.gallery_mode_dark)

            val stdContrastStr = stringResource(R.string.contrast_standard)
            val medContrastStr = stringResource(R.string.contrast_medium)
            val highContrastStr = stringResource(R.string.contrast_high)

            val uiSizeLabelPrefix = stringResource(R.string.gallery_ui_size)
            val textSizeLabelPrefix = stringResource(R.string.gallery_text_size)
            val roundnessLabelPrefix = stringResource(R.string.gallery_roundness)

            val sizeSmallStr = stringResource(R.string.size_small)
            val sizeNormalStr = stringResource(R.string.size_normal)
            val sizeLargeStr = stringResource(R.string.size_large)
            val sizeLargerStr = stringResource(R.string.size_larger)
            val sizeLargestStr = stringResource(R.string.size_largest)

            val roundnessSharpStr = stringResource(R.string.roundness_sharp)
            val roundnessSoftStr = stringResource(R.string.roundness_soft)
            val roundnessNormalStr = stringResource(R.string.roundness_normal)
            val roundnessRoundStr = stringResource(R.string.roundness_round)
            val roundnessExtraRoundStr = stringResource(R.string.roundness_extra_round)

            val cardFilledStr = stringResource(R.string.card_style_filled)
            val cardOutlinedStr = stringResource(R.string.card_style_outlined)
            val cardElevatedStr = stringResource(R.string.card_style_elevated)

            // Mode Selector
            Text(
                text = stringResource(R.string.gallery_mode),
                style = MaterialTheme.typography.titleMedium,
            )
            EnumSegmentedRow(
                options = listOf(ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.DARK),
                selected = settings.mode,
                label = { mode ->
                    when (mode) {
                        ThemeMode.SYSTEM -> systemModeStr
                        ThemeMode.LIGHT -> lightModeStr
                        ThemeMode.DARK -> darkModeStr
                    }
                },
                onSelect = { onEvent(ThemeEvent.SetMode(it)) },
            )

            // Dynamic Color Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.gallery_dynamic_color),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Switch(
                    checked = settings.dynamicColor,
                    onCheckedChange = { onEvent(ThemeEvent.SetDynamicColor(it)) },
                )
            }

            // AMOLED Switch
            val amoledEnabled = isDark
            val amoledCaption =
                when {
                    settings.mode == ThemeMode.LIGHT -> stringResource(R.string.amoled_only_dark)
                    settings.mode == ThemeMode.SYSTEM && !isDark -> stringResource(R.string.amoled_applies_when_dark)
                    else -> null
                }
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.gallery_amoled),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Switch(
                        checked = settings.amoled,
                        enabled = amoledEnabled,
                        onCheckedChange = { onEvent(ThemeEvent.SetAmoled(it)) },
                    )
                }
                if (amoledCaption != null) {
                    Text(
                        text = amoledCaption,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Accent Color Picker
            Text(
                text = stringResource(R.string.gallery_accent_color),
                style = MaterialTheme.typography.titleMedium,
            )
            AccentPicker(
                selectedAccent = settings.accentColor,
                recents = settings.recentAccents,
                enabled = !settings.dynamicColor,
                onPick = { onEvent(ThemeEvent.SetAccent(it, remember = false)) },
                onCustom = { showCustomSheet = true },
                onTurnOffDynamic = { onEvent(ThemeEvent.SetDynamicColor(false)) },
            )

            // Contrast Selector
            Text(
                text = stringResource(R.string.gallery_contrast),
                style = MaterialTheme.typography.titleMedium,
            )
            EnumSegmentedRow(
                options = listOf(ContrastLevel.STANDARD, ContrastLevel.MEDIUM, ContrastLevel.HIGH),
                selected = settings.contrast,
                label = { contrast ->
                    when (contrast) {
                        ContrastLevel.STANDARD -> stdContrastStr
                        ContrastLevel.MEDIUM -> medContrastStr
                        ContrastLevel.HIGH -> highContrastStr
                    }
                },
                onSelect = { onEvent(ThemeEvent.SetContrast(it)) },
                enabled = !settings.dynamicColor,
            )
            if (settings.dynamicColor) {
                Text(
                    text = stringResource(R.string.caption_contrast_dynamic),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            HorizontalDivider()

            // Section: Text and size
            SectionHeader(stringResource(R.string.gallery_section_text_size))

            // Font Selector Row
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showFontSheet = true }
                        .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.gallery_font),
                    style = MaterialTheme.typography.titleMedium,
                )
                val family = remember(settings.font) { fontFamilyFor(settings.font) }
                Text(
                    text = "${stringResource(fontDisplayName(settings.font))}  ›",
                    style = MaterialTheme.typography.bodyLarge.copy(fontFamily = family),
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            // UI Size Slider
            val uiSizes = remember { UiSize.entries }
            val currentUiIndex = uiSizes.indexOf(settings.uiSize).coerceAtLeast(0)
            Column {
                NexusSlider(
                    value = currentUiIndex.toFloat(),
                    onValueChange = { floatVal ->
                        val index = floatVal.roundToInt().coerceIn(0, uiSizes.size - 1)
                        if (index != currentUiIndex) {
                            onEvent(ThemeEvent.SetUiSize(uiSizes[index]))
                        }
                    },
                    valueRange = 0f..(uiSizes.size - 1).toFloat(),
                    steps = uiSizes.size - 2,
                    label = { floatVal ->
                        val index = floatVal.roundToInt().coerceIn(0, uiSizes.size - 1)
                        val name =
                            when (uiSizes[index]) {
                                UiSize.SMALL -> sizeSmallStr
                                UiSize.NORMAL -> sizeNormalStr
                                UiSize.LARGE -> sizeLargeStr
                                UiSize.LARGER -> sizeLargerStr
                                UiSize.LARGEST -> sizeLargestStr
                            }
                        "$uiSizeLabelPrefix: $name"
                    },
                )
                if (settings.uiSize.scale < 1.0f) {
                    Text(
                        text = stringResource(R.string.caption_ui_size_small),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Text Size Slider
            val textSizes = remember { TextSize.entries }
            val currentTextIndex = textSizes.indexOf(settings.textSize).coerceAtLeast(0)
            Column {
                NexusSlider(
                    value = currentTextIndex.toFloat(),
                    onValueChange = { floatVal ->
                        val index = floatVal.roundToInt().coerceIn(0, textSizes.size - 1)
                        if (index != currentTextIndex) {
                            onEvent(ThemeEvent.SetTextSize(textSizes[index]))
                        }
                    },
                    valueRange = 0f..(textSizes.size - 1).toFloat(),
                    steps = textSizes.size - 2,
                    label = { floatVal ->
                        val index = floatVal.roundToInt().coerceIn(0, textSizes.size - 1)
                        val name =
                            when (textSizes[index]) {
                                TextSize.SMALL -> sizeSmallStr
                                TextSize.NORMAL -> sizeNormalStr
                                TextSize.LARGE -> sizeLargeStr
                                TextSize.LARGER -> sizeLargerStr
                                TextSize.LARGEST -> sizeLargestStr
                            }
                        "$textSizeLabelPrefix: $name"
                    },
                )
                Text(
                    text = stringResource(R.string.caption_text_size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            HorizontalDivider()

            // Section: Shapes
            SectionHeader(stringResource(R.string.gallery_section_shapes))

            // Roundness Slider
            val roundnesses = remember { Roundness.entries }
            val currentRoundnessIndex = roundnesses.indexOf(settings.roundness).coerceAtLeast(0)
            NexusSlider(
                value = currentRoundnessIndex.toFloat(),
                onValueChange = { floatVal ->
                    val index = floatVal.roundToInt().coerceIn(0, roundnesses.size - 1)
                    if (index != currentRoundnessIndex) {
                        onEvent(ThemeEvent.SetRoundness(roundnesses[index]))
                    }
                },
                valueRange = 0f..(roundnesses.size - 1).toFloat(),
                steps = roundnesses.size - 2,
                label = { floatVal ->
                    val index = floatVal.roundToInt().coerceIn(0, roundnesses.size - 1)
                    val name =
                        when (roundnesses[index]) {
                            Roundness.SHARP -> roundnessSharpStr
                            Roundness.SOFT -> roundnessSoftStr
                            Roundness.NORMAL -> roundnessNormalStr
                            Roundness.ROUND -> roundnessRoundStr
                            Roundness.EXTRA_ROUND -> roundnessExtraRoundStr
                        }
                    "$roundnessLabelPrefix: $name"
                },
            )

            // Card Style
            Text(
                text = stringResource(R.string.gallery_card_style),
                style = MaterialTheme.typography.titleMedium,
            )
            EnumSegmentedRow(
                options = listOf(CardStyle.FILLED, CardStyle.OUTLINED, CardStyle.ELEVATED),
                selected = settings.cardStyle,
                label = { cardStyle ->
                    when (cardStyle) {
                        CardStyle.FILLED -> cardFilledStr
                        CardStyle.OUTLINED -> cardOutlinedStr
                        CardStyle.ELEVATED -> cardElevatedStr
                    }
                },
                onSelect = { onEvent(ThemeEvent.SetCardStyle(it)) },
            )

            // Reset appearance
            OutlinedButton(
                onClick = { showResetDialog = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.reset_appearance))
            }

            HorizontalDivider()

            // Previews
            // Section: Color Roles
            SectionHeader(stringResource(R.string.gallery_color_roles))
            ColorRolesGrid()

            HorizontalDivider()

            // Section: Extended Colors
            SectionHeader(stringResource(R.string.gallery_extended_colors))
            ExtendedColorsRow()

            HorizontalDivider()

            // Section: Typography
            SectionHeader(stringResource(R.string.gallery_typography))
            TypographySamples()

            HorizontalDivider()

            // Section: Shapes
            SectionHeader(stringResource(R.string.gallery_shapes))
            ShapeSamples()

            HorizontalDivider()

            // Section: Components
            SectionHeader(stringResource(R.string.gallery_components))
            ComponentSamples()

            HorizontalDivider()

            // Section: Cards
            SectionHeader(stringResource(R.string.gallery_cards))
            CardSamples()

            HorizontalDivider()

            // Section: Posters
            SectionHeader(stringResource(R.string.gallery_posters))
            PosterSamples()

            HorizontalDivider()

            // Section: Spacing
            SectionHeader(stringResource(R.string.gallery_spacing))
            SpacingSamples()
        }
    }

    if (showCustomSheet) {
        CustomColorSheet(
            initialColor = accentSeed(settings.accentColor),
            onDismiss = { showCustomSheet = false },
            onPreview = { onEvent(ThemeEvent.PreviewAccent(it)) },
            onApply = { argb, remember ->
                onEvent(ThemeEvent.SetAccent(argb, remember))
                showCustomSheet = false
            },
        )
    }

    if (showFontSheet) {
        FontPickerSheet(
            selected = settings.font,
            onSelect = {
                onEvent(ThemeEvent.SetFont(it))
                showFontSheet = false
            },
            onDismiss = { showFontSheet = false },
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(stringResource(R.string.reset_appearance_title)) },
            text = { Text(stringResource(R.string.reset_appearance_body)) },
            confirmButton = {
                Button(
                    onClick = {
                        showResetDialog = false
                        onEvent(ThemeEvent.ResetAppearance)
                        scope.launch {
                            snackbarHostState.showSnackbar(appearanceResetMsg)
                        }
                    },
                ) {
                    Text(stringResource(R.string.btn_reset))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showResetDialog = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            },
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun ColorRolesGrid() {
    val scheme = MaterialTheme.colorScheme
    val roles =
        listOf(
            "primary" to scheme.primary,
            "primaryContainer" to scheme.primaryContainer,
            "secondary" to scheme.secondary,
            "secondaryContainer" to scheme.secondaryContainer,
            "tertiary" to scheme.tertiary,
            "tertiaryContainer" to scheme.tertiaryContainer,
            "error" to scheme.error,
            "errorContainer" to scheme.errorContainer,
            "surface" to scheme.surface,
            "surfaceVariant" to scheme.surfaceVariant,
            "background" to scheme.background,
            "inverseSurface" to scheme.inverseSurface,
            "surfaceDim" to scheme.surfaceDim,
            "surfaceBright" to scheme.surfaceBright,
            "surfaceContainerLowest" to scheme.surfaceContainerLowest,
            "surfaceContainerLow" to scheme.surfaceContainerLow,
            "surfaceContainer" to scheme.surfaceContainer,
            "surfaceContainerHigh" to scheme.surfaceContainerHigh,
            "surfaceContainerHighest" to scheme.surfaceContainerHighest,
            "outline" to scheme.outline,
        )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(NexusTheme.spacing.sm),
    ) {
        roles.chunked(2).forEach { rowRoles ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(NexusTheme.spacing.sm),
            ) {
                rowRoles.forEach { (name, color) ->
                    val textColor = if (color.luminance() > 0.5f) Color.Black else Color.White
                    Surface(
                        modifier = Modifier.weight(1f),
                        color = color,
                        shape = NexusTheme.shapes.chip,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Column(
                            modifier = Modifier.padding(NexusTheme.spacing.sm),
                        ) {
                            Text(
                                text = name,
                                style = MaterialTheme.typography.labelSmall,
                                color = textColor,
                            )
                            Text(
                                text = color.toHexString(),
                                style = MaterialTheme.typography.bodySmall,
                                color = textColor,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExtendedColorsRow() {
    val extended = NexusTheme.extended
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            color = extended.ratingStar,
            contentColor = Color.White,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.padding(4.dp),
        ) {
            Text(
                text = "★ Rating 4.8",
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelMedium,
            )
        }
        Surface(
            color = extended.success,
            contentColor = extended.onSuccess,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.padding(4.dp),
        ) {
            Text(
                text = "Success Chip",
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelMedium,
            )
        }
        Surface(
            color = extended.warning,
            contentColor = extended.onWarning,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.padding(4.dp),
        ) {
            Text(
                text = "Warning Chip",
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun TypographySamples() {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Display Large", style = MaterialTheme.typography.displayLarge)
        Text("Headline Large", style = MaterialTheme.typography.headlineLarge)
        Text("Title Medium", style = MaterialTheme.typography.titleMedium)
        Text("Body Large", style = MaterialTheme.typography.bodyLarge)
        Text("Label Medium", style = MaterialTheme.typography.labelMedium)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.gallery_typography_sample),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ShapeSamples() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = NexusTheme.shapes.poster,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(50.dp, 75.dp),
        ) {
            Box(contentAlignment = Alignment.Center) { Text("Poster", style = MaterialTheme.typography.labelSmall) }
        }
        Surface(
            shape = NexusTheme.shapes.card,
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.size(60.dp),
        ) {
            Box(contentAlignment = Alignment.Center) { Text("Card", style = MaterialTheme.typography.labelSmall) }
        }
        Surface(
            shape = NexusTheme.shapes.chip,
            color = MaterialTheme.colorScheme.tertiaryContainer,
            modifier = Modifier.size(60.dp, 36.dp),
        ) {
            Box(contentAlignment = Alignment.Center) { Text("Chip", style = MaterialTheme.typography.labelSmall) }
        }
        Surface(
            shape = NexusTheme.shapes.button,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(60.dp, 36.dp),
        ) {
            Box(contentAlignment = Alignment.Center) { Text("Button", style = MaterialTheme.typography.labelSmall) }
        }
        Surface(
            shape = NexusTheme.shapes.sheet,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(60.dp),
        ) {
            Box(contentAlignment = Alignment.Center) { Text("Sheet", style = MaterialTheme.typography.labelSmall) }
        }
    }
}

@Composable
private fun ComponentSamples() {
    var sliderVal by remember { mutableFloatStateOf(0.5f) }
    var textVal by remember { mutableStateOf("Text input") }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {}) { Text("Filled") }
            FilledTonalButton(onClick = {}) { Text("Tonal") }
            OutlinedButton(onClick = {}) { Text("Outlined") }
            TextButton(onClick = {}) { Text("Text") }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AssistChip(onClick = {}, label = { Text("AssistChip") })
            FilterChip(selected = true, onClick = {}, label = { Text("FilterChip") })
            Switch(checked = true, onCheckedChange = {})
        }

        Slider(value = sliderVal, onValueChange = { sliderVal = it })
        LinearProgressIndicator(progress = { sliderVal }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            value = textVal,
            onValueChange = { textVal = it },
            label = { Text("OutlinedTextField") },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun CardSamples() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Card(
                modifier = Modifier.weight(1f),
                shape = NexusTheme.shapes.card,
            ) {
                Box(
                    modifier = Modifier.padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.gallery_card_sample_filled),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            OutlinedCard(
                modifier = Modifier.weight(1f),
                shape = NexusTheme.shapes.card,
            ) {
                Box(
                    modifier = Modifier.padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.gallery_card_sample_outlined),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            ElevatedCard(
                modifier = Modifier.weight(1f),
                shape = NexusTheme.shapes.card,
            ) {
                Box(
                    modifier = Modifier.padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.gallery_card_sample_elevated),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }

        NexusCard(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier.padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.gallery_card_sample_nexus),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun PosterSamples() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        listOf("Movie A", "Movie B", "Movie C").forEachIndexed { index, title ->
            Card(
                shape = NexusTheme.shapes.poster,
                modifier =
                    Modifier
                        .weight(1f)
                        .aspectRatio(POSTER_ASPECT_RATIO),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(8.dp),
                    )
                    if (index == 0) {
                        LinearProgressIndicator(
                            progress = { 0.65f },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(4.dp),
                        )
                    }
                }
            }
        }
    }
}

private const val POSTER_ASPECT_RATIO = 2f / 3f
private const val SPACING_BAR_SCALE = 4

@Composable
private fun SpacingSamples() {
    val spacing = NexusTheme.spacing
    val bars =
        listOf(
            "xxs (2 dp)" to spacing.xxs,
            "xs (4 dp)" to spacing.xs,
            "sm (8 dp)" to spacing.sm,
            "md (12 dp)" to spacing.md,
            "lg (16 dp)" to spacing.lg,
            "xl (24 dp)" to spacing.xl,
            "xxl (32 dp)" to spacing.xxl,
        )

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        bars.forEach { (label, dpVal) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.width(80.dp),
                )
                Box(
                    modifier =
                        Modifier
                            .height(8.dp)
                            .width(dpVal * SPACING_BAR_SCALE)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}

@Preview(name = "Light Mode")
@Composable
fun ThemeGalleryScreenLightPreview() {
    NexusTheme(settings = ThemeSettings(mode = ThemeMode.LIGHT)) {
        Surface {
            ThemeGalleryScreen(
                settings = ThemeSettings(mode = ThemeMode.LIGHT),
                onEvent = {},
            )
        }
    }
}

@Preview(name = "Dark Mode")
@Composable
fun ThemeGalleryScreenDarkPreview() {
    NexusTheme(settings = ThemeSettings(mode = ThemeMode.DARK, amoled = true)) {
        Surface {
            ThemeGalleryScreen(
                settings = ThemeSettings(mode = ThemeMode.DARK, amoled = true),
                onEvent = {},
            )
        }
    }
}
