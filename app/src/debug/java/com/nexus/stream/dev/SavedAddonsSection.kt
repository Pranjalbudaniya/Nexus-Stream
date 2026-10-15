package com.nexus.stream.dev

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.nexus.stream.R
import com.nexus.stream.domain.model.Addon
import com.nexus.stream.presentation.theme.NexusTheme

@Composable
fun SavedAddonsHeader(
    count: Int,
    actionStatus: String?,
    isEmpty: Boolean,
) {
    val spacing = NexusTheme.spacing
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        HorizontalDivider(modifier = Modifier.padding(vertical = spacing.xs))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.addon_tester_saved_addons_header, count),
                style = MaterialTheme.typography.titleMedium,
            )
            actionStatus?.let { status ->
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (isEmpty) {
            Text(
                text = stringResource(R.string.addon_tester_saved_addons_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun SavedAddonCard(
    addon: Addon,
    index: Int,
    totalCount: Int,
    onEvent: (AddonTesterEvent) -> Unit,
) {
    val spacing = NexusTheme.spacing
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = addon.manifest.name,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.addon_tester_manifest_version, addon.manifest.version),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = addon.enabled,
                    onCheckedChange = { isChecked ->
                        onEvent(AddonTesterEvent.ToggleAddonEnabled(addon.transportUrl, isChecked))
                    },
                )
            }

            Text(
                text = addon.transportUrl,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = {
                        onEvent(AddonTesterEvent.MoveAddon(addon.transportUrl, index - 1))
                    },
                    enabled = index > 0,
                ) {
                    Text(
                        text = "▲",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }

                IconButton(
                    onClick = {
                        onEvent(AddonTesterEvent.MoveAddon(addon.transportUrl, index + 1))
                    },
                    enabled = index < totalCount - 1,
                ) {
                    Text(
                        text = "▼",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }

                TextButton(
                    onClick = { onEvent(AddonTesterEvent.UseAddon(addon.transportUrl)) },
                ) {
                    Text(stringResource(R.string.addon_tester_use))
                }

                TextButton(
                    onClick = { onEvent(AddonTesterEvent.RefreshAddon(addon.transportUrl)) },
                ) {
                    Text(stringResource(R.string.addon_tester_refresh))
                }

                TextButton(
                    onClick = { onEvent(AddonTesterEvent.DeleteAddon(addon.transportUrl)) },
                ) {
                    Text(
                        text = stringResource(R.string.addon_tester_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}
