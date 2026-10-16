package com.nexus.stream.presentation.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nexus.stream.R
import com.nexus.stream.domain.model.AppError
import com.nexus.stream.domain.model.HomeRowSpec
import com.nexus.stream.presentation.common.ContentRow
import com.nexus.stream.presentation.common.NexusButton
import com.nexus.stream.presentation.common.NexusCard
import com.nexus.stream.presentation.common.NexusIconButton
import com.nexus.stream.presentation.common.NexusRefreshBox
import com.nexus.stream.presentation.theme.NexusTheme

@Composable
fun HomeScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToAddons: () -> Unit,
    onItemClick: (type: String, id: String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    HomeScreenContent(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        onNavigateToSettings = onNavigateToSettings,
        onNavigateToAddons = onNavigateToAddons,
        onItemClick = onItemClick,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeScreenContent(
    uiState: HomeUiState,
    onEvent: (HomeEvent) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToAddons: () -> Unit,
    onItemClick: (type: String, id: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lazyListState = rememberLazyListState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.home_title),
                        fontWeight = FontWeight.Bold,
                    )
                },
                actions = {
                    NexusIconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.settings_gear_description),
                        )
                    }
                },
            )
        },
    ) { padding ->
        NexusRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { onEvent(HomeEvent.Refresh) },
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        ) {
            when {
                !uiState.hasAddons -> {
                    if (uiState.seedFailed) {
                        EmptyStateView(
                            message = stringResource(R.string.home_seed_failed),
                            buttonText = stringResource(R.string.retry),
                            onButtonClick = { onEvent(HomeEvent.RetrySeed) },
                        )
                    } else {
                        EmptyStateView(
                            message = stringResource(R.string.home_no_addons),
                            buttonText = stringResource(R.string.home_btn_add_addon),
                            onButtonClick = onNavigateToAddons,
                        )
                    }
                }

                !uiState.hasCatalogRows -> {
                    EmptyStateView(
                        message = stringResource(R.string.home_no_catalog_addons),
                        buttonText = stringResource(R.string.home_btn_addons),
                        onButtonClick = onNavigateToAddons,
                    )
                }

                else -> {
                    val topPadding = NexusTheme.spacing.sm

                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding =
                            PaddingValues(
                                top = topPadding,
                                bottom = NexusTheme.spacing.lg,
                            ),
                    ) {
                        if (uiState.isFirstRowLoading) {
                            item(key = "hero_skeleton") {
                                HeroSkeleton()
                                Spacer(modifier = Modifier.height(NexusTheme.spacing.sm))
                            }
                        } else if (uiState.hero.isNotEmpty()) {
                            item(key = "hero_banner") {
                                HeroBanner(
                                    items = uiState.hero,
                                    inWatchlist = { id -> id in uiState.watchlistIds },
                                    onDetails = onItemClick,
                                    onToggleWatchlist = { onEvent(HomeEvent.ToggleWatchlist(it)) },
                                )
                                Spacer(modifier = Modifier.height(NexusTheme.spacing.sm))
                            }
                        }

                        if (uiState.continueWatching.isNotEmpty()) {
                            item(key = "continue_watching") {
                                ContinueWatchingRow(
                                    items = uiState.continueWatching,
                                    onItemClick = onItemClick,
                                    onRemoveItem = { metaId ->
                                        onEvent(HomeEvent.RemoveContinue(metaId))
                                    },
                                )
                                Spacer(modifier = Modifier.height(NexusTheme.spacing.sm))
                            }
                        }

                        if (uiState.offline) {
                            item(key = "offline_banner") {
                                OfflineBanner(
                                    onRetryAll = { onEvent(HomeEvent.Refresh) },
                                )
                            }
                        }
                        items(
                            items = uiState.rows,
                            key = { it.spec.key },
                        ) { row ->
                            ContentRow(
                                title = row.spec.title,
                                status = row.status,
                                onRetry = { onEvent(HomeEvent.Retry(row.spec.key)) },
                                onItemClick = onItemClick,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OfflineBanner(
    onRetryAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NexusCard(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = NexusTheme.spacing.lg, vertical = NexusTheme.spacing.sm),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(NexusTheme.spacing.md),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.home_offline_banner),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f).padding(end = NexusTheme.spacing.sm),
            )
            NexusButton(onClick = onRetryAll) {
                Text(stringResource(R.string.home_retry_all))
            }
        }
    }
}

@Composable
private fun EmptyStateView(
    message: String,
    buttonText: String,
    onButtonClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(NexusTheme.spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(NexusTheme.spacing.md))
        NexusButton(onClick = onButtonClick) {
            Text(buttonText)
        }
    }
}

@Preview(name = "HomeScreen - Populated")
@Composable
internal fun HomeScreenPopulatedPreview() {
    NexusTheme {
        HomeScreenContent(
            uiState =
                HomeUiState(
                    rows =
                        listOf(
                            HomeRow(
                                spec =
                                    HomeRowSpec(
                                        key = "cinemeta|movie|top",
                                        title = "Popular - Movies",
                                        transportUrl = "https://v3-cinemeta.strem.io",
                                        type = "movie",
                                        catalogId = "top",
                                    ),
                                status = RowStatus.Loading,
                            ),
                        ),
                    hasAddons = true,
                    hasCatalogRows = true,
                ),
            onEvent = {},
            onNavigateToSettings = {},
            onNavigateToAddons = {},
            onItemClick = { _, _ -> },
        )
    }
}

@Preview(name = "HomeScreen - Empty No Addons")
@Composable
internal fun HomeScreenEmptyNoAddonsPreview() {
    NexusTheme {
        HomeScreenContent(
            uiState =
                HomeUiState(
                    hasAddons = false,
                    hasCatalogRows = false,
                ),
            onEvent = {},
            onNavigateToSettings = {},
            onNavigateToAddons = {},
            onItemClick = { _, _ -> },
        )
    }
}

@Preview(name = "HomeScreen - Offline")
@Composable
internal fun HomeScreenOfflinePreview() {
    NexusTheme {
        HomeScreenContent(
            uiState =
                HomeUiState(
                    rows =
                        listOf(
                            HomeRow(
                                spec =
                                    HomeRowSpec(
                                        key = "cinemeta|movie|top",
                                        title = "Popular - Movies",
                                        transportUrl = "https://v3-cinemeta.strem.io",
                                        type = "movie",
                                        catalogId = "top",
                                    ),
                                status = RowStatus.Failed(AppError.Network),
                            ),
                            HomeRow(
                                spec =
                                    HomeRowSpec(
                                        key = "cinemeta|series|top",
                                        title = "Popular - Series",
                                        transportUrl = "https://v3-cinemeta.strem.io",
                                        type = "series",
                                        catalogId = "top",
                                    ),
                                status = RowStatus.Failed(AppError.Network),
                            ),
                        ),
                    hasAddons = true,
                    hasCatalogRows = true,
                    offline = true,
                ),
            onEvent = {},
            onNavigateToSettings = {},
            onNavigateToAddons = {},
            onItemClick = { _, _ -> },
        )
    }
}
