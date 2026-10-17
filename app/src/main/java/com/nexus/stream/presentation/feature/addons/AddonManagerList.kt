package com.nexus.stream.presentation.feature.addons

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.nexus.stream.domain.model.Addon
import com.nexus.stream.domain.model.AddonCategory
import com.nexus.stream.presentation.theme.NexusTheme

@Composable
fun AddonManagerList(
    uiState: AddonManagerUiState,
    onEvent: (AddonManagerEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedCat = uiState.selectedCategory
    val currentVisibleList =
        if (selectedCat == null) {
            uiState.flattenedVisibleAddons
        } else {
            uiState.visibleAddons
        }

    if (uiState.addons.isEmpty() && !uiState.seedLoading) {
        EmptyStateView(
            onAddCinemeta = { onEvent(AddonManagerEvent.AddCinemeta) },
            modifier = modifier,
        )
    } else if (uiState.searchQuery.isNotBlank() && currentVisibleList.isEmpty()) {
        EmptySearchMatchesView(modifier = modifier)
    } else if (selectedCat != null && uiState.visibleAddons.isEmpty()) {
        EmptyCategoryView(
            category = selectedCat,
            onInstall = { onEvent(AddonManagerEvent.OpenStore(selectedCat)) },
            modifier = modifier,
        )
    } else {
        AddonLazyColumn(
            uiState = uiState,
            currentVisibleList = currentVisibleList,
            onEvent = onEvent,
            modifier = modifier,
        )
    }
}

@Composable
private fun AddonLazyColumn(
    uiState: AddonManagerUiState,
    currentVisibleList: List<Addon>,
    onEvent: (AddonManagerEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = NexusTheme.spacing

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = spacing.xxl * 3),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        if (uiState.selectedCategory == null) {
            for ((category, sectionAddons) in uiState.sections) {
                if (sectionAddons.isNotEmpty()) {
                    item(key = "header_${category.name}") {
                        AddonSectionHeader(category = category)
                    }
                    itemsIndexed(
                        items = sectionAddons,
                        key = { _, addon -> addon.transportUrl },
                    ) { _, addon ->
                        AddonItemRow(
                            addon = addon,
                            uiState = uiState,
                            visibleList = currentVisibleList,
                            onEvent = onEvent,
                        )
                    }
                }
            }
        } else {
            itemsIndexed(
                items = uiState.visibleAddons,
                key = { _, addon -> addon.transportUrl },
            ) { _, addon ->
                AddonItemRow(
                    addon = addon,
                    uiState = uiState,
                    visibleList = currentVisibleList,
                    onEvent = onEvent,
                )
            }
        }
    }
}

@Composable
private fun AddonItemRow(
    addon: Addon,
    uiState: AddonManagerUiState,
    visibleList: List<Addon>,
    onEvent: (AddonManagerEvent) -> Unit,
) {
    val visibleIndex = visibleList.indexOfFirst { it.transportUrl == addon.transportUrl }
    AddonCard(
        addon = addon,
        isFirst = visibleIndex == 0,
        isLast = visibleIndex == visibleList.size - 1,
        failedRefresh = addon.manifest.name in uiState.failedRefreshAddons,
        onToggleEnabled = { enabled -> onEvent(AddonManagerEvent.SetEnabled(addon, enabled)) },
        onMoveUp = { onEvent(AddonManagerEvent.MoveUp(addon)) },
        onMoveDown = { onEvent(AddonManagerEvent.MoveDown(addon)) },
        onRefresh = { onEvent(AddonManagerEvent.Refresh(addon)) },
        onConfigure = { onEvent(AddonManagerEvent.OpenConfigure(addon)) },
        onCopyUrl = { onEvent(AddonManagerEvent.CopyUrl(addon)) },
        onRemove = { onEvent(AddonManagerEvent.RequestRemove(addon)) },
        isFamilyMode = uiState.isFamilyMode,
    )
}
