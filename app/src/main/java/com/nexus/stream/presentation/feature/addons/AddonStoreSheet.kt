package com.nexus.stream.presentation.feature.addons

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nexus.stream.R
import com.nexus.stream.domain.model.AddonCategory
import com.nexus.stream.domain.model.AppError
import com.nexus.stream.domain.model.StoreAddon
import com.nexus.stream.presentation.common.NexusButton
import com.nexus.stream.presentation.common.NexusCard
import com.nexus.stream.presentation.common.NexusIconButton
import com.nexus.stream.presentation.common.NexusTextButton
import com.nexus.stream.presentation.common.toUserMessage
import com.nexus.stream.presentation.theme.NexusTheme

const val STORE_SHEET_GESTURES_ENABLED = true

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddonStoreSheet(
    initialCategory: AddonCategory? = null,
    onDismiss: () -> Unit,
    viewModel: AddonStoreViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(initialCategory) {
        if (initialCategory != null) {
            viewModel.setInitialCategory(initialCategory)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is AddonStoreEffect.ShowMessage -> {
                    val message = formatStoreMessage(context, effect.messageRes, effect.formatArgs)
                    snackbarHostState.showSnackbar(message)
                }
            }
        }
    }

    val configuration = LocalConfiguration.current
    val maxHeight = (configuration.screenHeightDp * 0.9f).dp

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetGesturesEnabled = STORE_SHEET_GESTURES_ENABLED,
        shape = NexusTheme.shapes.sheet,
        modifier = modifier.imePadding(),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxHeight),
        ) {
            AddonStoreSheetContent(
                uiState = uiState,
                onEvent = viewModel::onEvent,
                onDismiss = onDismiss,
            )
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }

    if (uiState.pendingHttpAddon != null) {
        HttpConfirmationDialog(
            onConfirm = { viewModel.onEvent(AddonStoreEvent.ConfirmHttpInstall) },
            onDismiss = { viewModel.onEvent(AddonStoreEvent.DismissHttpDialog) },
        )
    }
}

@Composable
private fun AddonStoreSheetContent(
    uiState: AddonStoreUiState,
    onEvent: (AddonStoreEvent) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = NexusTheme.spacing

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        StoreSheetHeader(onRefresh = { onEvent(AddonStoreEvent.Refresh) }, onDismiss = onDismiss)

        StoreSearchField(
            query = uiState.searchQuery,
            onQueryChange = { onEvent(AddonStoreEvent.SearchQueryChanged(it)) },
            onClear = { onEvent(AddonStoreEvent.ClearSearch) },
        )

        StoreCategoryChipsRow(
            selectedCategory = uiState.selectedCategory,
            totalCount = uiState.totalCount,
            categoryCounts = uiState.categoryCounts,
            onSelectCategory = { onEvent(AddonStoreEvent.SelectCategory(it)) },
        )

        StoreAddonList(
            uiState = uiState,
            onEvent = onEvent,
        )
    }
}

@Composable
private fun StoreSheetHeader(
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.addon_store_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            NexusIconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.addon_store_refresh),
                )
            }
            if (!STORE_SHEET_GESTURES_ENABLED) {
                NexusTextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.btn_close))
                }
            }
        }
    }
}

@Composable
private fun StoreSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(stringResource(R.string.addon_search_placeholder)) },
        leadingIcon = {
            Icon(imageVector = Icons.Default.Search, contentDescription = null)
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                NexusIconButton(onClick = onClear) {
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
}

@Composable
private fun StoreCategoryChipsRow(
    selectedCategory: AddonCategory?,
    totalCount: Int,
    categoryCounts: Map<AddonCategory, Int>,
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
private fun StoreAddonList(
    uiState: AddonStoreUiState,
    onEvent: (AddonStoreEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = NexusTheme.spacing

    if (uiState.isLoading && uiState.rawAddons.isEmpty()) {
        Column(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            repeat(SKELETON_COUNT) {
                StoreSkeletonRow()
            }
        }
    } else if (uiState.error != null && uiState.rawAddons.isEmpty()) {
        StoreErrorCard(
            error = uiState.error,
            onRetry = { onEvent(AddonStoreEvent.Refresh) },
            modifier = modifier,
        )
    } else if (uiState.filteredAddons.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize().padding(top = spacing.xl),
            contentAlignment = Alignment.TopCenter,
        ) {
            Text(
                text = stringResource(R.string.addon_search_no_matches),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            items(uiState.filteredAddons, key = { it.transportUrl }) { addon ->
                StoreAddonRow(
                    addon = addon,
                    isInstalling = uiState.installingUrl == addon.transportUrl,
                    onInstall = { onEvent(AddonStoreEvent.Install(it)) },
                )
            }
        }
    }
}

@Composable
private fun StoreErrorCard(
    error: AppError,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = NexusTheme.spacing
    val context = LocalContext.current
    val userMsg = error.toUserMessage()
    val errorText = formatStoreMessage(context, userMsg.resId, userMsg.formatArgs)

    NexusCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = errorText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            NexusButton(onClick = onRetry) {
                Text(stringResource(R.string.addon_manager_retry))
            }
        }
    }
}

@Composable
private fun HttpConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.addon_http_dialog_title))
        },
        text = {
            Text(stringResource(R.string.addon_http_dialog_body))
        },
        confirmButton = {
            NexusTextButton(onClick = onConfirm) {
                Text(stringResource(R.string.addon_http_dialog_confirm))
            }
        },
        dismissButton = {
            NexusTextButton(onClick = onDismiss) {
                Text(stringResource(R.string.addon_dialog_cancel))
            }
        },
    )
}

private fun formatStoreMessage(
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

private const val SKELETON_COUNT = 4
