package com.nexus.stream.presentation.feature.discover

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import com.nexus.stream.R
import com.nexus.stream.domain.model.AppError
import com.nexus.stream.domain.model.DiscoverCatalog
import com.nexus.stream.domain.model.GenreKind
import com.nexus.stream.domain.model.GenreSpec
import com.nexus.stream.domain.model.MetaPreview
import com.nexus.stream.domain.model.PosterShape
import com.nexus.stream.presentation.common.NexusRefreshBox
import com.nexus.stream.presentation.common.toUserMessage
import com.nexus.stream.presentation.theme.NexusTheme

@Composable
fun DiscoverScreen(
    onItemClick: (type: String, id: String) -> Unit,
    onNavigateToAddons: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DiscoverViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val refreshFailedMessage = stringResource(R.string.discover_refresh_failed)

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is DiscoverEffect.RefreshFailed -> {
                    snackbarHostState.showSnackbar(message = refreshFailedMessage)
                }
            }
        }
    }

    DiscoverScreenContent(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        onItemClick = onItemClick,
        onNavigateToAddons = onNavigateToAddons,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

@Composable
fun DiscoverScreenContent(
    uiState: DiscoverUiState,
    onEvent: (DiscoverEvent) -> Unit,
    onItemClick: (type: String, id: String) -> Unit,
    onNavigateToAddons: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        NexusRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { onEvent(DiscoverEvent.Refresh) },
            modifier =
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(top = statusBarTop + NexusTheme.spacing.sm),
        ) {
            when (uiState.phase) {
                is DiscoverPhase.NoAddons -> {
                    DiscoverMessage(
                        title = stringResource(R.string.discover_no_addons),
                        buttonText = stringResource(R.string.discover_open_addons),
                        onButtonClick = onNavigateToAddons,
                    )
                }

                is DiscoverPhase.NoCatalogs -> {
                    DiscoverMessage(
                        title = stringResource(R.string.discover_no_catalogs),
                        buttonText = stringResource(R.string.discover_open_addons),
                        onButtonClick = onNavigateToAddons,
                    )
                }

                else -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        FiltersRow(
                            types = uiState.types,
                            selectedType = uiState.selectedType,
                            onSelectType = { onEvent(DiscoverEvent.SelectType(it)) },
                            catalogs = uiState.catalogs,
                            selectedCatalogKey = uiState.selectedCatalogKey,
                            onSelectCatalog = { onEvent(DiscoverEvent.SelectCatalog(it)) },
                            genreUi = uiState.genreUi,
                            onSelectGenre = { onEvent(DiscoverEvent.SelectGenre(it)) },
                        )

                        DiscoverPhaseBody(
                            uiState = uiState,
                            onEvent = onEvent,
                            onItemClick = onItemClick,
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscoverPhaseBody(
    uiState: DiscoverUiState,
    onEvent: (DiscoverEvent) -> Unit,
    onItemClick: (type: String, id: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        when (uiState.phase) {
            is DiscoverPhase.Loading -> {
                SkeletonGrid(gridShape = uiState.gridShape)
            }

            is DiscoverPhase.Empty -> {
                DiscoverMessage(
                    title = stringResource(R.string.discover_empty_title),
                    message = stringResource(R.string.discover_empty_body),
                )
            }

            is DiscoverPhase.Error -> {
                val userMsg = uiState.phase.error.toUserMessage()
                val errorText =
                    when (userMsg.formatArgs.size) {
                        0 -> {
                            stringResource(userMsg.resId)
                        }

                        1 -> {
                            stringResource(userMsg.resId, userMsg.formatArgs[0])
                        }

                        else -> {
                            stringResource(
                                userMsg.resId,
                                userMsg.formatArgs[0],
                                userMsg.formatArgs[1],
                            )
                        }
                    }
                DiscoverMessage(
                    title = errorText,
                    buttonText = stringResource(R.string.retry),
                    onButtonClick = { onEvent(DiscoverEvent.Retry) },
                    isOutlinedButton = true,
                )
            }

            is DiscoverPhase.Content -> {
                val gridState =
                    remember(uiState.selectedCatalogKey, uiState.genreUi?.selectedGenre) {
                        LazyGridState()
                    }

                DiscoverGrid(
                    items = uiState.items,
                    gridShape = uiState.gridShape,
                    footer = uiState.footer,
                    posterMarks = uiState.posterMarks,
                    onItemClick = onItemClick,
                    onLoadMore = { onEvent(DiscoverEvent.LoadMore) },
                    onRetry = { onEvent(DiscoverEvent.Retry) },
                    state = gridState,
                )
            }

            is DiscoverPhase.NoAddons, is DiscoverPhase.NoCatalogs -> {
                // Handled in parent layout
            }
        }
    }
}

private val previewCatalogs =
    listOf(
        DiscoverCatalog(
            key = "cinemeta|movie|top",
            type = "movie",
            catalogId = "top",
            transportUrl = "https://v3-cinemeta.strem.io",
            title = "Popular",
            genre = GenreSpec(options = listOf("Action", "Comedy", "Drama"), required = false),
            supportsSkip = true,
        ),
        DiscoverCatalog(
            key = "cinemeta|movie|featured",
            type = "movie",
            catalogId = "featured",
            transportUrl = "https://v3-cinemeta.strem.io",
            title = "Featured",
            genre = null,
            supportsSkip = true,
        ),
    )

private val previewItems =
    listOf(
        MetaPreview(
            id = "tt0111161",
            type = "movie",
            name = "The Shawshank Redemption",
            poster = null,
            posterShape = PosterShape.POSTER,
            background = null,
            logo = null,
            description = null,
            releaseInfo = "1994",
            imdbRating = "9.3",
            genres = listOf("Drama"),
        ),
        MetaPreview(
            id = "tt0068646",
            type = "movie",
            name = "The Godfather",
            poster = null,
            posterShape = PosterShape.POSTER,
            background = null,
            logo = null,
            description = null,
            releaseInfo = "1972",
            imdbRating = "9.2",
            genres = listOf("Crime", "Drama"),
        ),
    )

private val previewBaseState =
    DiscoverUiState(
        types = listOf("movie", "series"),
        selectedType = "movie",
        catalogs = previewCatalogs,
        selectedCatalogKey = previewCatalogs[0].key,
        genreUi =
            DiscoverGenreUi(
                options = listOf("Action", "Comedy", "Drama"),
                selectedGenre = null,
                isRequired = false,
                kind = GenreKind.GENRE,
            ),
    )

@Preview(name = "DiscoverScreen - Content Light")
@Preview(name = "DiscoverScreen - Content Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun DiscoverScreenContentPreview() {
    NexusTheme {
        DiscoverScreenContent(
            uiState = previewBaseState.copy(phase = DiscoverPhase.Content, items = previewItems),
            onEvent = {},
            onItemClick = { _, _ -> },
            onNavigateToAddons = {},
        )
    }
}

@Preview(name = "DiscoverScreen - Loading Light")
@Preview(name = "DiscoverScreen - Loading Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun DiscoverScreenLoadingPreview() {
    NexusTheme {
        DiscoverScreenContent(
            uiState = previewBaseState.copy(phase = DiscoverPhase.Loading),
            onEvent = {},
            onItemClick = { _, _ -> },
            onNavigateToAddons = {},
        )
    }
}

@Preview(name = "DiscoverScreen - Error Light")
@Preview(name = "DiscoverScreen - Error Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun DiscoverScreenErrorPreview() {
    NexusTheme {
        DiscoverScreenContent(
            uiState = previewBaseState.copy(phase = DiscoverPhase.Error(AppError.Network)),
            onEvent = {},
            onItemClick = { _, _ -> },
            onNavigateToAddons = {},
        )
    }
}

@Preview(name = "DiscoverScreen - Empty Light")
@Preview(name = "DiscoverScreen - Empty Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun DiscoverScreenEmptyPreview() {
    NexusTheme {
        DiscoverScreenContent(
            uiState = previewBaseState.copy(phase = DiscoverPhase.Empty),
            onEvent = {},
            onItemClick = { _, _ -> },
            onNavigateToAddons = {},
        )
    }
}

@Preview(name = "DiscoverScreen - 3 Controls Light")
@Composable
internal fun DiscoverScreenThreeControlsPreview() {
    NexusTheme {
        DiscoverScreenContent(
            uiState = previewBaseState.copy(phase = DiscoverPhase.Content, items = previewItems),
            onEvent = {},
            onItemClick = { _, _ -> },
            onNavigateToAddons = {},
        )
    }
}

@Preview(name = "DiscoverScreen - Text Only Controls Light")
@Composable
internal fun DiscoverScreenTextOnlyControlsPreview() {
    NexusTheme {
        DiscoverScreenContent(
            uiState =
                previewBaseState.copy(
                    types = listOf("movie"),
                    catalogs = listOf(previewCatalogs.first()),
                    genreUi = null,
                    phase = DiscoverPhase.Content,
                    items = previewItems,
                ),
            onEvent = {},
            onItemClick = { _, _ -> },
            onNavigateToAddons = {},
        )
    }
}
