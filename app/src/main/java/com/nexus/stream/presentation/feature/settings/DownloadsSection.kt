package com.nexus.stream.presentation.feature.settings

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.nexus.stream.R
import com.nexus.stream.presentation.common.NexusButton
import com.nexus.stream.presentation.common.NexusSlider
import com.nexus.stream.presentation.common.NexusTextButton
import com.nexus.stream.presentation.theme.NexusTheme
import kotlin.math.roundToInt

@Composable
fun DownloadsSection(
    state: DownloadsSettingsUiState,
    onEvent: (DownloadsSettingsEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = NexusTheme.spacing

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        // 1. Wi-Fi only switch
        SettingsSwitchRow(
            title = stringResource(R.string.downloads_setting_wifi),
            subtitle = stringResource(R.string.downloads_setting_wifi_caption),
            checked = state.wifiOnly,
            onCheckedChange = { onEvent(DownloadsSettingsEvent.SetWifiOnly(it)) },
        )

        // 2. Downloads at the same time slider
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            Text(
                text = stringResource(R.string.downloads_setting_concurrent) + ": ${state.maxConcurrent}",
                style = MaterialTheme.typography.bodyLarge,
            )
            NexusSlider(
                value = state.maxConcurrent.toFloat(),
                onValueChange = { onEvent(DownloadsSettingsEvent.SetMaxConcurrent(it.roundToInt())) },
                valueRange = 1f..3f,
                steps = 1,
            )
        }

        // 3. Save downloads to Gallery switch
        SettingsSwitchRow(
            title = stringResource(R.string.downloads_setting_gallery),
            subtitle = stringResource(R.string.downloads_setting_gallery_caption),
            checked = state.saveToGallery,
            onCheckedChange = { onEvent(DownloadsSettingsEvent.SetSaveToGallery(it)) },
        )

        // 4. Save existing downloads now (n)
        if (state.saveToGallery && state.unsavedCount > 0) {
            SettingsRow(
                title = stringResource(R.string.downloads_setting_gallery_existing, state.unsavedCount),
                onClick = { onEvent(DownloadsSettingsEvent.SaveExisting) },
            )
        }

        // 5. Location / uninstall caption
        Text(
            text = stringResource(R.string.downloads_setting_location),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = spacing.xs),
        )

        // Offer dialog
        if (state.showSaveExistingOffer) {
            AlertDialog(
                onDismissRequest = { onEvent(DownloadsSettingsEvent.DeclineOffer) },
                title = { Text(stringResource(R.string.downloads_gallery_offer_title)) },
                confirmButton = {
                    NexusButton(onClick = { onEvent(DownloadsSettingsEvent.SaveExisting) }) {
                        Text(stringResource(R.string.downloads_gallery_offer_all))
                    }
                },
                dismissButton = {
                    NexusTextButton(onClick = { onEvent(DownloadsSettingsEvent.DeclineOffer) }) {
                        Text(stringResource(R.string.downloads_gallery_offer_new))
                    }
                },
            )
        }
    }
}

@Preview(name = "DownloadsSection - Switch Off - Light")
@Preview(name = "DownloadsSection - Switch Off - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun DownloadsSectionSwitchOffPreview() {
    NexusTheme {
        DownloadsSection(
            state = DownloadsSettingsUiState(wifiOnly = true, maxConcurrent = 2, saveToGallery = false),
            onEvent = {},
        )
    }
}

@Preview(name = "DownloadsSection - Switch On - Light")
@Preview(name = "DownloadsSection - Switch On - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun DownloadsSectionSwitchOnPreview() {
    NexusTheme {
        DownloadsSection(
            state =
                DownloadsSettingsUiState(
                    wifiOnly = true,
                    maxConcurrent = 2,
                    saveToGallery = true,
                    unsavedCount = 3,
                ),
            onEvent = {},
        )
    }
}

@Preview(name = "DownloadsSection - Offer Dialog - Light")
@Preview(name = "DownloadsSection - Offer Dialog - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun DownloadsSectionOfferDialogPreview() {
    NexusTheme {
        DownloadsSection(
            state =
                DownloadsSettingsUiState(
                    wifiOnly = true,
                    maxConcurrent = 2,
                    saveToGallery = true,
                    unsavedCount = 3,
                    showSaveExistingOffer = true,
                ),
            onEvent = {},
        )
    }
}
