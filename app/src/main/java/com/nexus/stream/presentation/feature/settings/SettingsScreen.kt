package com.nexus.stream.presentation.feature.settings

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
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
import com.nexus.stream.presentation.common.NexusCard
import com.nexus.stream.presentation.common.NexusIconButton
import com.nexus.stream.presentation.common.NexusTextButton
import com.nexus.stream.presentation.theme.NexusTheme

private const val DISABLED_ALPHA = 0.5f

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
                    .padding(horizontal = spacing.md, vertical = spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            NexusCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = onNavigateToAddons,
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.settings_addons_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            NexusCard(
                modifier = Modifier.fillMaxWidth(),
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
                    Switch(
                        checked = uiState.familyMode,
                        onCheckedChange = { checked ->
                            if (checked) {
                                onEvent(SettingsEvent.SetFamilyMode(true))
                            } else {
                                showDisableDialog = true
                            }
                        },
                    )
                }
            }

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

        if (showDisableDialog) {
            AlertDialog(
                onDismissRequest = { showDisableDialog = false },
                title = {
                    Text(
                        text = stringResource(R.string.settings_family_mode_disable_confirm_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                text = {
                    Text(
                        text = stringResource(R.string.settings_family_mode_disable_confirm_message),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                confirmButton = {
                    NexusTextButton(
                        onClick = {
                            showDisableDialog = false
                            onEvent(SettingsEvent.SetFamilyMode(false))
                        },
                    ) {
                        Text(
                            text = stringResource(R.string.settings_family_mode_turn_off),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                },
                dismissButton = {
                    NexusTextButton(
                        onClick = { showDisableDialog = false },
                    ) {
                        Text(text = stringResource(R.string.btn_cancel))
                    }
                },
            )
        }
    }
}

@Preview(name = "SettingsScreen - Light")
@Composable
internal fun SettingsScreenLightPreview() {
    NexusTheme {
        SettingsContent(
            uiState = SettingsUiState(familyMode = true, hiddenAdultAddonsCount = 2),
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
            uiState = SettingsUiState(familyMode = false, hiddenAdultAddonsCount = 0),
            onEvent = {},
            onNavigateUp = {},
            onNavigateToAddons = {},
        )
    }
}
