package com.nexus.stream.presentation.feature.addons

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import com.nexus.stream.R
import com.nexus.stream.domain.model.Addon
import com.nexus.stream.domain.model.AddonCapabilities
import com.nexus.stream.domain.model.AddonManifest
import com.nexus.stream.domain.model.AddonSource
import com.nexus.stream.domain.model.ManifestHints
import com.nexus.stream.domain.model.ResourceSpec
import com.nexus.stream.domain.model.isBoardCatalog
import com.nexus.stream.presentation.common.NexusCard
import com.nexus.stream.presentation.theme.NexusTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddonManagerScreen(
    viewModel: AddonManagerViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is AddonManagerEffect.ShowMessage -> {
                    val message = formatMessage(context, effect.messageRes, effect.formatArgs)
                    snackbarHostState.showSnackbar(message)
                }

                is AddonManagerEffect.OpenUrl -> {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(effect.url))
                        context.startActivity(intent)
                    } catch (_: ActivityNotFoundException) {
                        // Handled safely without crashing when no browser exists
                    }
                }

                is AddonManagerEffect.CopyToClipboard -> {
                    clipboardManager.setText(AnnotatedString(effect.text))
                }
            }
        }
    }

    AddonManagerContent(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        snackbarHostState = snackbarHostState,
        onOpenUrl = { url ->
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (_: ActivityNotFoundException) {
                // Handled safely
            }
        },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddonManagerContent(
    uiState: AddonManagerUiState,
    onEvent: (AddonManagerEvent) -> Unit,
    snackbarHostState: SnackbarHostState,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = NexusTheme.spacing

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.addon_manager_title),
                        fontWeight = FontWeight.Bold,
                    )
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onEvent(AddonManagerEvent.OpenAdd()) },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.addon_manager_add_fab)) },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            if (!uiState.capabilities.hasStreams) {
                WarningBanner(
                    message = stringResource(R.string.addon_banner_no_stream),
                )
            }
            if (!uiState.capabilities.hasCatalogs) {
                WarningBanner(
                    message = stringResource(R.string.addon_banner_no_catalog),
                )
            }
            if (uiState.seedFailed) {
                SeedFailedCard(onRetry = { onEvent(AddonManagerEvent.RetrySeed) })
            }

            if (uiState.addons.isEmpty() && !uiState.seedLoading) {
                EmptyStateView(
                    onAddCinemeta = { onEvent(AddonManagerEvent.AddCinemeta) },
                    onAddAddon = { onEvent(AddonManagerEvent.OpenAdd()) },
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = spacing.xxl * 3),
                    verticalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    itemsIndexed(
                        items = uiState.addons,
                        key = { _, addon -> addon.transportUrl },
                    ) { index, addon ->
                        AddonCard(
                            addon = addon,
                            isFirst = index == 0,
                            isLast = index == uiState.addons.size - 1,
                            onToggleEnabled = { enabled ->
                                onEvent(AddonManagerEvent.SetEnabled(addon, enabled))
                            },
                            onMoveUp = { onEvent(AddonManagerEvent.MoveUp(addon)) },
                            onMoveDown = { onEvent(AddonManagerEvent.MoveDown(addon)) },
                            onRefresh = { onEvent(AddonManagerEvent.Refresh(addon)) },
                            onConfigure = { onEvent(AddonManagerEvent.OpenConfigure(addon)) },
                            onCopyUrl = { onEvent(AddonManagerEvent.CopyUrl(addon)) },
                            onRemove = { onEvent(AddonManagerEvent.RequestRemove(addon)) },
                        )
                    }
                }
            }
        }
    }

    uiState.removeTarget?.let { target ->
        RemoveConfirmDialog(
            target = target,
            allAddons = uiState.addons,
            onConfirm = { onEvent(AddonManagerEvent.ConfirmRemove) },
            onCancel = { onEvent(AddonManagerEvent.CancelRemove) },
        )
    }

    AddAddonSheet(
        state = uiState.addSheetState,
        onEvent = onEvent,
        onOpenUrl = onOpenUrl,
    )
}

@Composable
private fun SeedFailedCard(onRetry: () -> Unit) {
    val spacing = NexusTheme.spacing
    NexusCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(spacing.md),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.addon_manager_seed_failed_title),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRetry) {
                Text(
                    text = stringResource(R.string.addon_manager_retry),
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun EmptyStateView(
    onAddCinemeta: () -> Unit,
    onAddAddon: () -> Unit,
) {
    val spacing = NexusTheme.spacing
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(spacing.xl),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Text(
                text = stringResource(R.string.addon_manager_empty_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.addon_manager_empty_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(spacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                OutlinedButton(onClick = onAddCinemeta) {
                    Text(stringResource(R.string.addon_manager_add_cinemeta))
                }
                Button(onClick = onAddAddon) {
                    Text(stringResource(R.string.addon_manager_add_fab))
                }
            }
        }
    }
}

@Composable
private fun RemoveConfirmDialog(
    target: Addon,
    allAddons: List<Addon>,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val targetHasBoardCatalog =
        target.enabled && target.manifest.catalogs.any { it.isBoardCatalog }
    val otherCatalogsCount =
        allAddons.count { addon ->
            addon.transportUrl != target.transportUrl &&
                addon.enabled &&
                addon.manifest.catalogs.any { it.isBoardCatalog }
        }
    val isLastCatalog = targetHasBoardCatalog && otherCatalogsCount == 0

    val bodyText =
        buildString {
            append(stringResource(R.string.addon_remove_dialog_body))
            if (isLastCatalog) {
                append("\n\n")
                append(stringResource(R.string.addon_remove_dialog_body_last_catalog))
            }
        }

    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Text(
                text =
                    stringResource(
                        R.string.addon_remove_dialog_title,
                        target.manifest.name,
                    ),
            )
        },
        text = { Text(bodyText) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.addon_dialog_remove),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.addon_dialog_cancel))
            }
        },
    )
}

private fun formatMessage(
    context: Context,
    resId: Int,
    args: List<Any>,
): String =
    if (args.isEmpty()) {
        context.getString(resId)
    } else if (args.size == 1) {
        context.getString(resId, args[0])
    } else {
        context.getString(resId, args[0], args[1])
    }

@Preview(name = "AddonManagerScreen - Populated")
@Composable
internal fun AddonManagerScreenPopulatedPreview() {
    NexusTheme {
        AddonManagerContent(
            uiState =
                AddonManagerUiState(
                    addons =
                        listOf(
                            Addon(
                                transportUrl = "https://v3-cinemeta.strem.io/",
                                manifest =
                                    AddonManifest(
                                        id = "org.stremio.cinemeta",
                                        version = "3.0.14",
                                        name = "Cinemeta",
                                        description = "Official movie directory",
                                        logo = null,
                                        background = null,
                                        types = listOf("movie"),
                                        idPrefixes = null,
                                        resources = listOf(ResourceSpec("catalog", null, null)),
                                        catalogs = emptyList(),
                                        behaviorHints =
                                            ManifestHints(
                                                adult = false,
                                                p2p = false,
                                                configurable = false,
                                                configurationRequired = false,
                                            ),
                                    ),
                                enabled = true,
                                position = 0,
                                source = AddonSource.LOCAL,
                                installedAt = 0L,
                            ),
                        ),
                    capabilities = AddonCapabilities(hasCatalogs = true, hasStreams = false),
                ),
            onEvent = {},
            snackbarHostState = remember { SnackbarHostState() },
            onOpenUrl = {},
        )
    }
}

@Preview(name = "AddonManagerScreen - Empty")
@Composable
internal fun AddonManagerScreenEmptyPreview() {
    NexusTheme {
        AddonManagerContent(
            uiState =
                AddonManagerUiState(
                    addons = emptyList(),
                    capabilities = AddonCapabilities(hasCatalogs = false, hasStreams = false),
                ),
            onEvent = {},
            snackbarHostState = remember { SnackbarHostState() },
            onOpenUrl = {},
        )
    }
}

@Preview(name = "AddonManagerScreen - SeedFailed")
@Composable
internal fun AddonManagerScreenSeedFailedPreview() {
    NexusTheme {
        AddonManagerContent(
            uiState =
                AddonManagerUiState(
                    addons = emptyList(),
                    capabilities = AddonCapabilities(hasCatalogs = false, hasStreams = false),
                    seedFailed = true,
                ),
            onEvent = {},
            snackbarHostState = remember { SnackbarHostState() },
            onOpenUrl = {},
        )
    }
}
