package com.nexus.stream.presentation.feature.addons

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import com.nexus.stream.R
import com.nexus.stream.domain.model.Addon
import com.nexus.stream.domain.model.AddonCapabilities
import com.nexus.stream.domain.model.AddonCategory
import com.nexus.stream.domain.model.AddonManifest
import com.nexus.stream.domain.model.AddonSource
import com.nexus.stream.domain.model.ManifestHints
import com.nexus.stream.domain.model.ResourceSpec
import com.nexus.stream.domain.model.isBoardCatalog
import com.nexus.stream.domain.usecase.AddonFilters
import com.nexus.stream.presentation.common.NexusButton
import com.nexus.stream.presentation.common.NexusIconButton
import com.nexus.stream.presentation.common.NexusOutlinedButton
import com.nexus.stream.presentation.common.NexusTextButton
import com.nexus.stream.presentation.theme.NexusTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddonManagerScreen(
    onNavigateUp: () -> Unit,
    prefillUrl: String? = null,
    viewModel: AddonManagerViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }

    var handledPrefill by rememberSaveable(prefillUrl) { mutableStateOf(false) }
    LaunchedEffect(prefillUrl) {
        if (!prefillUrl.isNullOrBlank() && !handledPrefill) {
            handledPrefill = true
            viewModel.onEvent(AddonManagerEvent.OpenAdd(prefill = prefillUrl, autoCheck = true))
        }
    }

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
        onNavigateUp = onNavigateUp,
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
    onNavigateUp: () -> Unit,
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
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onEvent(AddonManagerEvent.OpenAdd()) },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.addon_manager_add_fab)) },
                shape = NexusTheme.shapes.button,
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
            AddonManagerToolbarRow(uiState = uiState, onEvent = onEvent)

            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { onEvent(AddonManagerEvent.SearchQueryChanged(it)) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.addon_search_placeholder)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                    )
                },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        NexusIconButton(onClick = { onEvent(AddonManagerEvent.ClearSearch) }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.addon_search_clear),
                            )
                        }
                    }
                },
                singleLine = true,
                shape = NexusTheme.shapes.button,
            )

            AddonCategoryChipsRow(
                selectedCategory = uiState.selectedCategory,
                categoryCounts = uiState.categoryCounts,
                totalCount =
                    if (uiState.searchQuery.isBlank()) {
                        uiState.addons.size
                    } else {
                        AddonFilters.filterInstalled(uiState.addons, uiState.searchQuery, null).size
                    },
                onSelectCategory = { onEvent(AddonManagerEvent.SelectCategory(it)) },
            )

            if (!uiState.capabilities.hasStreams) {
                WarningBanner(
                    message = stringResource(R.string.addon_banner_no_stream),
                    actionLabel = stringResource(R.string.addon_banner_find_streams),
                    onAction = { onEvent(AddonManagerEvent.OpenStore(AddonCategory.STREAMS)) },
                )
            }
            if (!uiState.capabilities.hasCatalogs) {
                WarningBanner(
                    message = stringResource(R.string.addon_banner_no_catalog),
                    actionLabel = stringResource(R.string.addon_banner_find_catalogs),
                    onAction = { onEvent(AddonManagerEvent.OpenStore(AddonCategory.CATALOGS)) },
                )
            }
            if (uiState.seedFailed) {
                SeedFailedCard(onRetry = { onEvent(AddonManagerEvent.RetrySeed) })
            }

            AddonManagerList(
                uiState = uiState,
                onEvent = onEvent,
            )
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

    if (uiState.isStoreOpen) {
        AddonStoreSheet(
            initialCategory = uiState.storeInitialCategory,
            onDismiss = { onEvent(AddonManagerEvent.DismissStore) },
        )
    }
}

@Composable
private fun AddonManagerToolbarRow(
    uiState: AddonManagerUiState,
    onEvent: (AddonManagerEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NexusButton(onClick = { onEvent(AddonManagerEvent.OpenStore()) }) {
            Text(stringResource(R.string.addon_toolbar_install))
        }
        val isRefreshing = uiState.isRefreshingAll
        NexusOutlinedButton(
            onClick = { onEvent(AddonManagerEvent.RefreshAll) },
            enabled = !isRefreshing && uiState.addons.isNotEmpty(),
        ) {
            val label =
                if (isRefreshing) {
                    val progress = uiState.refreshProgress ?: (0 to 0)
                    stringResource(
                        R.string.addon_refreshing_progress,
                        progress.first,
                        progress.second,
                    )
                } else {
                    stringResource(R.string.addon_toolbar_refresh_all)
                }
            Text(label)
        }
    }
}

@Composable
private fun AddonCategoryChipsRow(
    selectedCategory: AddonCategory?,
    categoryCounts: Map<AddonCategory, Int>,
    totalCount: Int,
    onSelectCategory: (AddonCategory?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val categoryOptions =
        listOf(
            null to stringResource(R.string.addon_category_all),
            AddonCategory.CATALOGS to stringResource(R.string.addon_category_catalogs),
            AddonCategory.STREAMS to stringResource(R.string.addon_category_streams),
            AddonCategory.SUBTITLES to stringResource(R.string.addon_category_subtitles),
            AddonCategory.OTHER to stringResource(R.string.addon_category_other),
        )

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(NexusTheme.spacing.xs),
    ) {
        items(categoryOptions, key = { it.first?.name ?: "ALL" }) { (category, title) ->
            val count = if (category == null) totalCount else (categoryCounts[category] ?: 0)
            FilterChip(
                selected = selectedCategory == category,
                onClick = { onSelectCategory(category) },
                label = {
                    Text(
                        text = stringResource(R.string.addon_category_chip_count, title, count),
                        style = MaterialTheme.typography.labelMedium,
                    )
                },
                shape = NexusTheme.shapes.chip,
            )
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
            NexusTextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.addon_dialog_remove),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            NexusTextButton(onClick = onCancel) {
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
            onNavigateUp = {},
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
            onNavigateUp = {},
        )
    }
}
