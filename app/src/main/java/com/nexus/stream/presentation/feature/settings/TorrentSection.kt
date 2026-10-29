package com.nexus.stream.presentation.feature.settings

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import com.nexus.stream.R
import com.nexus.stream.presentation.common.NexusButton
import com.nexus.stream.presentation.common.NexusOutlinedButton
import com.nexus.stream.presentation.common.NexusTextButton
import com.nexus.stream.presentation.theme.NexusTheme

private const val PREVIEW_CACHE_250_MB = 262144000L
private const val PREVIEW_CACHE_500_MB = 524288000L

@Composable
fun TorrentSection(
    state: TorrentSettingsUiState,
    onEvent: (TorrentSettingsEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val spacing = NexusTheme.spacing

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        // 1. Wi-Fi only
        SettingsSwitchRow(
            title = stringResource(R.string.settings_torrent_wifi_only),
            subtitle = stringResource(R.string.settings_torrent_wifi_only_desc),
            checked = state.wifiOnly,
            onCheckedChange = { onEvent(TorrentSettingsEvent.SetWifiOnly(it)) },
        )

        // 2. Cache limit
        SettingsValueRow(
            title = stringResource(R.string.settings_torrent_cache_limit),
            value = stringResource(R.string.settings_torrent_cache_limit_gb, state.cacheLimitGb),
            onClick = { onEvent(TorrentSettingsEvent.OpenLimitDialog) },
        )

        // 3. Clear cache on exit
        SettingsSwitchRow(
            title = stringResource(R.string.settings_torrent_clear_on_exit),
            subtitle = stringResource(R.string.settings_torrent_clear_on_exit_desc),
            checked = state.clearCacheOnExit,
            onCheckedChange = { onEvent(TorrentSettingsEvent.SetClearOnExit(it)) },
        )

        // 4. Cache usage
        val cacheSizeText =
            when (val size = state.cacheSize) {
                is CacheSizeState.Ready -> {
                    stringResource(
                        R.string.settings_torrent_cache_usage_using,
                        Formatter.formatShortFileSize(context, size.bytes),
                    )
                }

                CacheSizeState.Unknown -> {
                    stringResource(R.string.settings_torrent_cache_unknown)
                }

                CacheSizeState.Idle, CacheSizeState.Loading -> {
                    stringResource(R.string.settings_torrent_cache_loading)
                }
            }
        SettingsValueRow(
            title = stringResource(R.string.settings_torrent_cache_usage),
            value = cacheSizeText,
        )

        // 5. Clear cache now
        NexusOutlinedButton(
            onClick = { onEvent(TorrentSettingsEvent.RequestClear) },
            enabled = !state.isClearing,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.settings_torrent_clear_cache_now))
        }
    }

    if (state.showLimitDialog) {
        AlertDialog(
            onDismissRequest = { onEvent(TorrentSettingsEvent.DismissLimitDialog) },
            title = { Text(stringResource(R.string.settings_torrent_cache_limit)) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    state.limitOptions.forEach { limitGb ->
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = (limitGb == state.cacheLimitGb),
                                        onClick = {
                                            onEvent(TorrentSettingsEvent.SetCacheLimit(limitGb))
                                            onEvent(TorrentSettingsEvent.DismissLimitDialog)
                                        },
                                        role = Role.RadioButton,
                                    ).padding(vertical = spacing.xs),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = (limitGb == state.cacheLimitGb),
                                onClick = null,
                            )
                            Spacer(modifier = Modifier.width(spacing.sm))
                            Text(
                                text = stringResource(R.string.settings_torrent_cache_limit_gb, limitGb),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                NexusTextButton(onClick = { onEvent(TorrentSettingsEvent.DismissLimitDialog) }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            },
        )
    }

    if (state.showClearConfirm) {
        val sizeString =
            when (val size = state.cacheSize) {
                is CacheSizeState.Ready -> Formatter.formatShortFileSize(context, size.bytes)
                else -> stringResource(R.string.settings_torrent_cache_unknown)
            }
        AlertDialog(
            onDismissRequest = { onEvent(TorrentSettingsEvent.DismissClear) },
            title = { Text(stringResource(R.string.settings_torrent_clear_confirm_title)) },
            text = {
                Text(stringResource(R.string.settings_torrent_clear_confirm_message, sizeString))
            },
            confirmButton = {
                NexusButton(
                    onClick = { onEvent(TorrentSettingsEvent.ConfirmClear) },
                    enabled = !state.isClearing,
                ) {
                    Text(stringResource(R.string.settings_torrent_clear_confirm_action))
                }
            },
            dismissButton = {
                NexusTextButton(onClick = { onEvent(TorrentSettingsEvent.DismissClear) }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            },
        )
    }
}

@Preview(name = "TorrentSection - Expanded")
@Composable
internal fun TorrentSectionExpandedPreview() {
    NexusTheme {
        TorrentSection(
            state =
                TorrentSettingsUiState(
                    cacheSize = CacheSizeState.Ready(PREVIEW_CACHE_250_MB),
                ),
            onEvent = {},
        )
    }
}

@Preview(name = "TorrentSection - Clear Confirm Dialog")
@Composable
internal fun TorrentSectionClearDialogPreview() {
    NexusTheme {
        TorrentSection(
            state =
                TorrentSettingsUiState(
                    showClearConfirm = true,
                    cacheSize = CacheSizeState.Ready(PREVIEW_CACHE_500_MB),
                ),
            onEvent = {},
        )
    }
}
