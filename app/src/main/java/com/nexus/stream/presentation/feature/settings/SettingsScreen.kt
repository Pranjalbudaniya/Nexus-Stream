package com.nexus.stream.presentation.feature.settings

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexus.stream.R
import com.nexus.stream.domain.usecase.LanguageCodes
import com.nexus.stream.presentation.common.NexusCard
import com.nexus.stream.presentation.common.NexusIconButton
import com.nexus.stream.presentation.theme.NexusTheme

private const val DISABLED_ALPHA = 0.5f
private const val RES_4K = 2160
private const val RES_1080P = 1080
private const val RES_720P = 720
private const val RES_SD = 480

@Composable
fun SettingsScreen(
    onNavigateUp: () -> Unit,
    onNavigateToAddons: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    SettingsContent(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        onNavigateUp = onNavigateUp,
        onNavigateToAddons = onNavigateToAddons,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsContent(
    uiState: SettingsUiState,
    onEvent: (SettingsEvent) -> Unit,
    onNavigateUp: () -> Unit,
    onNavigateToAddons: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = NexusTheme.spacing
    var showDisableDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showQualityDialog by remember { mutableStateOf(false) }
    var showSkipLengthDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_title),
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    NexusIconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_up),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = spacing.md, vertical = spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            AddonsNavigationCard(onNavigateToAddons = onNavigateToAddons)
            FamilyModeCard(
                uiState = uiState,
                onToggle = { checked ->
                    if (checked) {
                        onEvent(SettingsEvent.SetFamilyMode(true))
                    } else {
                        showDisableDialog = true
                    }
                },
            )
            Text(
                text = stringResource(R.string.settings_player_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = spacing.xs),
            )
            QuickPlayCard(
                quickPlay = uiState.quickPlay,
                onToggle = { onEvent(SettingsEvent.SetQuickPlay(it)) },
            )
            PreferredLanguageCard(
                preferredLanguage = uiState.preferredLanguage,
                onClick = { showLanguageDialog = true },
            )
            PreferredQualityCard(
                preferredQuality = uiState.preferredQuality,
                onClick = { showQualityDialog = true },
            )
            HideCamCard(
                hideCam = uiState.hideCam,
                onToggle = { onEvent(SettingsEvent.SetHideCam(it)) },
            )
            SkipLengthCard(
                seekStepSeconds = uiState.seekStepSeconds,
                onClick = { showSkipLengthDialog = true },
            )
            NexusCard(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .alpha(DISABLED_ALPHA),
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(spacing.md),
                    verticalArrangement = Arrangement.spacedBy(spacing.xxs),
                ) {
                    Text(
                        text = stringResource(R.string.settings_appearance_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.settings_appearance_coming_soon),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        SettingsDialogs(
            showDisableDialog = showDisableDialog,
            showLanguageDialog = showLanguageDialog,
            showQualityDialog = showQualityDialog,
            showSkipLengthDialog = showSkipLengthDialog,
            uiState = uiState,
            onEvent = onEvent,
            onDismissDisable = { showDisableDialog = false },
            onDismissLanguage = { showLanguageDialog = false },
            onDismissQuality = { showQualityDialog = false },
            onDismissSkipLength = { showSkipLengthDialog = false },
        )
    }
}

@Composable
private fun AddonsNavigationCard(onNavigateToAddons: () -> Unit) {
    NexusCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onNavigateToAddons,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(NexusTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.settings_addons_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun FamilyModeCard(
    uiState: SettingsUiState,
    onToggle: (Boolean) -> Unit,
) {
    val spacing = NexusTheme.spacing
    NexusCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier = Modifier.weight(1f).padding(end = spacing.md),
                verticalArrangement = Arrangement.spacedBy(spacing.xxs),
            ) {
                Text(
                    text = stringResource(R.string.settings_family_mode_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                val subtitle =
                    if (uiState.familyMode && uiState.hiddenAdultAddonsCount > 0) {
                        if (uiState.hiddenAdultAddonsCount == 1) {
                            stringResource(
                                R.string.settings_family_mode_subtitle_hidden,
                                uiState.hiddenAdultAddonsCount,
                            )
                        } else {
                            stringResource(
                                R.string.settings_family_mode_subtitle_hidden_plural,
                                uiState.hiddenAdultAddonsCount,
                            )
                        }
                    } else {
                        stringResource(R.string.settings_family_mode_subtitle)
                    }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = uiState.familyMode, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun QuickPlayCard(
    quickPlay: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val spacing = NexusTheme.spacing
    NexusCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier = Modifier.weight(1f).padding(end = spacing.md),
                verticalArrangement = Arrangement.spacedBy(spacing.xxs),
            ) {
                Text(
                    text = stringResource(R.string.settings_quick_play_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.settings_quick_play_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = quickPlay, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun PreferredLanguageCard(
    preferredLanguage: String?,
    onClick: () -> Unit,
) {
    val spacing = NexusTheme.spacing
    NexusCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier = Modifier.weight(1f).padding(end = spacing.md),
                verticalArrangement = Arrangement.spacedBy(spacing.xxs),
            ) {
                Text(
                    text = stringResource(R.string.settings_player_preferred_language_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                val label =
                    preferredLanguage?.let { LanguageCodes.languageDisplayName(it) }
                        ?: stringResource(R.string.settings_player_preferred_language_none)
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PreferredQualityCard(
    preferredQuality: Int?,
    onClick: () -> Unit,
) {
    val spacing = NexusTheme.spacing
    NexusCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier = Modifier.weight(1f).padding(end = spacing.md),
                verticalArrangement = Arrangement.spacedBy(spacing.xxs),
            ) {
                Text(
                    text = stringResource(R.string.settings_player_preferred_quality_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                val label =
                    when (preferredQuality) {
                        RES_4K -> stringResource(R.string.settings_player_preferred_quality_4k)
                        RES_1080P -> stringResource(R.string.settings_player_preferred_quality_1080p)
                        RES_720P -> stringResource(R.string.settings_player_preferred_quality_720p)
                        RES_SD -> stringResource(R.string.settings_player_preferred_quality_sd)
                        else -> stringResource(R.string.settings_player_preferred_quality_auto)
                    }
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun HideCamCard(
    hideCam: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val spacing = NexusTheme.spacing
    NexusCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier = Modifier.weight(1f).padding(end = spacing.md),
                verticalArrangement = Arrangement.spacedBy(spacing.xxs),
            ) {
                Text(
                    text = stringResource(R.string.settings_player_hide_cam_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.settings_player_hide_cam_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = hideCam, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun SkipLengthCard(
    seekStepSeconds: Int,
    onClick: () -> Unit,
) {
    val spacing = NexusTheme.spacing
    NexusCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier = Modifier.weight(1f).padding(end = spacing.md),
                verticalArrangement = Arrangement.spacedBy(spacing.xxs),
            ) {
                Text(
                    text = stringResource(R.string.settings_player_skip_length_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.settings_player_skip_length_seconds, seekStepSeconds),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Preview(name = "SettingsScreen - Light")
@Composable
internal fun SettingsScreenLightPreview() {
    NexusTheme {
        SettingsContent(
            uiState =
                SettingsUiState(
                    familyMode = true,
                    hiddenAdultAddonsCount = 2,
                    quickPlay = false,
                    preferredLanguage = "en",
                    preferredQuality = 1080,
                    hideCam = false,
                ),
            onEvent = {},
            onNavigateUp = {},
            onNavigateToAddons = {},
        )
    }
}

@Preview(name = "SettingsScreen - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun SettingsScreenDarkPreview() {
    NexusTheme {
        SettingsContent(
            uiState =
                SettingsUiState(
                    familyMode = false,
                    hiddenAdultAddonsCount = 0,
                    quickPlay = true,
                    preferredLanguage = null,
                    preferredQuality = null,
                    hideCam = true,
                ),
            onEvent = {},
            onNavigateUp = {},
            onNavigateToAddons = {},
        )
    }
}
