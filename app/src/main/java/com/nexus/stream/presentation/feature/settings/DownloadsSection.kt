package com.nexus.stream.presentation.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.nexus.stream.R
import com.nexus.stream.presentation.common.NexusSlider
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

        // 3. Location / uninstall caption
        Text(
            text = stringResource(R.string.downloads_setting_location),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = spacing.xs),
        )
    }
}

@Preview(name = "DownloadsSection - Light")
@Composable
internal fun DownloadsSectionPreviewLight() {
    NexusTheme {
        DownloadsSection(
            state = DownloadsSettingsUiState(wifiOnly = true, maxConcurrent = 2),
            onEvent = {},
        )
    }
}

@Preview(name = "DownloadsSection - Dark", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun DownloadsSectionPreviewDark() {
    NexusTheme {
        DownloadsSection(
            state = DownloadsSettingsUiState(wifiOnly = false, maxConcurrent = 1),
            onEvent = {},
        )
    }
}
