package com.nexus.stream.dev

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nexus.stream.R
import com.nexus.stream.domain.model.ThemeMode
import com.nexus.stream.domain.model.ThemeSettings
import com.nexus.stream.presentation.common.AccentPicker
import com.nexus.stream.presentation.common.CustomColorSheet
import com.nexus.stream.presentation.common.toHexString
import com.nexus.stream.presentation.theme.NexusTheme
import com.nexus.stream.presentation.theme.ThemeEvent
import com.nexus.stream.presentation.theme.accentSeed
import com.nexus.stream.presentation.theme.isDarkTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ThemeGalleryScreen(
    settings: ThemeSettings,
    onEvent: (ThemeEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showCustomSheet by rememberSaveable { mutableStateOf(false) }

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

            // Mode Selector
            Text(
                text = stringResource(R.string.gallery_mode),
                style = MaterialTheme.typography.titleMedium,
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val modes =
                    listOf(
                        ThemeMode.SYSTEM to stringResource(R.string.gallery_mode_system),
                        ThemeMode.LIGHT to stringResource(R.string.gallery_mode_light),
                        ThemeMode.DARK to stringResource(R.string.gallery_mode_dark),
                    )
                modes.forEachIndexed { index, (mode, label) ->
                    SegmentedButton(
                        selected = settings.mode == mode,
                        onClick = { onEvent(ThemeEvent.SetMode(mode)) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                    ) {
                        Text(label)
                    }
                }
            }

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

            // Reset appearance
            OutlinedButton(
                onClick = {
                    onEvent(ThemeEvent.ResetAppearance)
                    scope.launch {
                        snackbarHostState.showSnackbar(appearanceResetMsg)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.reset_appearance))
            }

            HorizontalDivider()

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
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorRolesGrid() {
    val scheme = MaterialTheme.colorScheme
    val roles =
        listOf(
            Pair("primary", scheme.primary to scheme.onPrimary),
            Pair("primaryContainer", scheme.primaryContainer to scheme.onPrimaryContainer),
            Pair("secondary", scheme.secondary to scheme.onSecondary),
            Pair("secondaryContainer", scheme.secondaryContainer to scheme.onSecondaryContainer),
            Pair("tertiary", scheme.tertiary to scheme.onTertiary),
            Pair("tertiaryContainer", scheme.tertiaryContainer to scheme.onTertiaryContainer),
            Pair("error", scheme.error to scheme.onError),
            Pair("errorContainer", scheme.errorContainer to scheme.onErrorContainer),
            Pair("surface", scheme.surface to scheme.onSurface),
            Pair("surfaceVariant", scheme.surfaceVariant to scheme.onSurfaceVariant),
            Pair("background", scheme.background to scheme.onBackground),
            Pair("inverseSurface", scheme.inverseSurface to scheme.inverseOnSurface),
        )

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        roles.forEach { (name, pair) ->
            Surface(
                color = pair.first,
                contentColor = pair.second,
                shape = RoundedCornerShape(8.dp),
                modifier =
                    Modifier
                        .width(160.dp)
                        .height(56.dp),
            ) {
                Box(
                    modifier = Modifier.padding(8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.labelSmall,
                    )
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

        Card(
            modifier = Modifier.fillMaxWidth(),
            border =
                if (NexusTheme.amoledActive) {
                    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                } else {
                    null
                },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Text(
                text = "Sample Card with U3 amoled border rule",
                modifier = Modifier.padding(16.dp),
            )
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
