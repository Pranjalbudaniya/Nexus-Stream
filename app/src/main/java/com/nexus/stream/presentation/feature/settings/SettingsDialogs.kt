package com.nexus.stream.presentation.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.nexus.stream.R
import com.nexus.stream.domain.usecase.LanguageCodes
import com.nexus.stream.presentation.common.NexusTextButton

private const val RES_4K = 2160
private const val RES_1080P = 1080
private const val RES_720P = 720
private const val RES_SD = 480
private const val DIALOG_MAX_HEIGHT_DP = 360

@Composable
internal fun SettingsDialogs(
    showDisableDialog: Boolean,
    showLanguageDialog: Boolean,
    showQualityDialog: Boolean,
    uiState: SettingsUiState,
    onEvent: (SettingsEvent) -> Unit,
    onDismissDisable: () -> Unit,
    onDismissLanguage: () -> Unit,
    onDismissQuality: () -> Unit,
) {
    if (showDisableDialog) {
        FamilyModeDisableDialog(
            onConfirm = {
                onDismissDisable()
                onEvent(SettingsEvent.SetFamilyMode(false))
            },
            onDismiss = onDismissDisable,
        )
    }

    if (showLanguageDialog) {
        LanguageSelectDialog(
            selectedCode = uiState.preferredLanguage,
            onSelect = {
                onEvent(SettingsEvent.SetPreferredLanguage(it))
                onDismissLanguage()
            },
            onDismiss = onDismissLanguage,
        )
    }

    if (showQualityDialog) {
        QualitySelectDialog(
            selectedQuality = uiState.preferredQuality,
            onSelect = {
                onEvent(SettingsEvent.SetPreferredQuality(it))
                onDismissQuality()
            },
            onDismiss = onDismissQuality,
        )
    }
}

@Composable
private fun FamilyModeDisableDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
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
            NexusTextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.settings_family_mode_turn_off),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            NexusTextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.btn_cancel))
            }
        },
    )
}

@Composable
private fun LanguageSelectDialog(
    selectedCode: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_player_preferred_language_title)) },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = DIALOG_MAX_HEIGHT_DP.dp)) {
                item {
                    RadioOptionRow(
                        label = stringResource(R.string.settings_player_preferred_language_none),
                        selected = selectedCode == null,
                        onClick = { onSelect(null) },
                    )
                }
                items(LanguageCodes.SUPPORTED_LANGUAGE_CODES, key = { it }) { code ->
                    RadioOptionRow(
                        label = LanguageCodes.languageDisplayName(code),
                        selected = selectedCode == code,
                        onClick = { onSelect(code) },
                    )
                }
            }
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
private fun QualitySelectDialog(
    selectedQuality: Int?,
    onSelect: (Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    val options =
        listOf(
            null to stringResource(R.string.settings_player_preferred_quality_auto),
            RES_4K to stringResource(R.string.settings_player_preferred_quality_4k),
            RES_1080P to stringResource(R.string.settings_player_preferred_quality_1080p),
            RES_720P to stringResource(R.string.settings_player_preferred_quality_720p),
            RES_SD to stringResource(R.string.settings_player_preferred_quality_sd),
        )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_player_preferred_quality_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                options.forEach { (quality, label) ->
                    RadioOptionRow(
                        label = label,
                        selected = selectedQuality == quality,
                        onClick = { onSelect(quality) },
                    )
                }
            }
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
                .heightIn(min = 48.dp)
                .selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        RadioButton(selected = selected, onClick = null)
    }
}
