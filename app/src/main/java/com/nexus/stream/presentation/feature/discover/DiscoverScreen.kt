package com.nexus.stream.presentation.feature.discover

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import com.nexus.stream.R
import com.nexus.stream.domain.model.AppError
import com.nexus.stream.domain.model.DiscoverCatalog
import com.nexus.stream.domain.model.GenreKind
import com.nexus.stream.domain.model.GenreSpec
import com.nexus.stream.domain.model.MetaPreview
import com.nexus.stream.domain.model.PosterShape
import com.nexus.stream.domain.usecase.DiscoverBodyMode
import com.nexus.stream.domain.usecase.discoverBodyMode
import com.nexus.stream.presentation.common.NexusRefreshBox
import com.nexus.stream.presentation.common.toUserMessage
import com.nexus.stream.presentation.navigation.TopLevelDestination
import com.nexus.stream.presentation.theme.NexusTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

@Composable
fun DiscoverScreen(
    onItemClick: (type: String, id: String) -> Unit,
    onNavigateToAddons: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DiscoverViewModel = hiltViewModel(),
    searchViewModel: SearchViewModel = hiltViewModel(),
    reselectEvents: Flow<TopLevelDestination> = emptyFlow(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val searchUiState by searchViewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val refreshFailedMessage = stringResource(R.string.discover_refresh_failed)

    var textFieldValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue())
    }
    var isFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) {
        if (textFieldValue.text.isNotEmpty()) {
            searchViewModel.onEvent(SearchEvent.QueryChanged(textFieldValue.text))
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is DiscoverEffect.RefreshFailed -> {
                    snackbarHostState.showSnackbar(message = refreshFailedMessage)
                }
            }
        }
    }

    LaunchedEffect(reselectEvents) {
        reselectEvents.collect {
            focusRequester.requestFocus()
            keyboardController?.show()
            textFieldValue =
                textFieldValue.copy(
                    selection = TextRange(textFieldValue.text.length),
                )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            focusManager.clearFocus()
        }
    }

    val backEnabled = isFocused || textFieldValue.text.isNotEmpty()
    BackHandler(enabled = backEnabled) {
        if (isFocused) {
            focusManager.clearFocus()
        } else if (textFieldValue.text.isNotEmpty()) {
            textFieldValue = TextFieldValue("")
            searchViewModel.onEvent(SearchEvent.ClearQuery)
        }
    }

    DiscoverScreenContent(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        onItemClick = onItemClick,
        onNavigateToAddons = onNavigateToAddons,
        searchUiState = searchUiState,
        onSearchEvent = searchViewModel::onEvent,
        textFieldValue = textFieldValue,
        onValueChange = { newValue ->
            textFieldValue = newValue
            searchViewModel.onEvent(SearchEvent.QueryChanged(newValue.text))
        },
        isFocused = isFocused,
        onFocusChanged = { isFocused = it },
        focusRequester = focusRequester,
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
    searchUiState: SearchUiState = SearchUiState(),
    onSearchEvent: (SearchEvent) -> Unit = {},
    textFieldValue: TextFieldValue = TextFieldValue(),
    onValueChange: (TextFieldValue) -> Unit = {},
    isFocused: Boolean = false,
    onFocusChanged: (Boolean) -> Unit = {},
    focusRequester: FocusRequester = remember { FocusRequester() },
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    val bodyMode =
        discoverBodyMode(
            rawQuery = textFieldValue.text,
            isFocused = isFocused,
            hasHistory = searchUiState.history.isNotEmpty(),
        )

    val gridState =
        remember(uiState.selectedCatalogKey, uiState.genreUi?.selectedGenre) {
            LazyGridState()
        }
    val resultsListState =
        remember(searchUiState.normalizedQuery) {
            LazyListState()
        }
    val recentsListState = rememberLazyListState()

    val isResultsScrollInProgress by remember { derivedStateOf { resultsListState.isScrollInProgress } }
    val isRecentsScrollInProgress by remember { derivedStateOf { recentsListState.isScrollInProgress } }
    val isGridScrollInProgress by remember { derivedStateOf { gridState.isScrollInProgress } }

    LaunchedEffect(isResultsScrollInProgress, isRecentsScrollInProgress, isGridScrollInProgress) {
        if (isResultsScrollInProgress || isRecentsScrollInProgress || isGridScrollInProgress) {
            focusManager.clearFocus()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        Column(
            modifier =
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(top = statusBarTop)
                    .imePadding(),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = NexusTheme.spacing.md,
                            vertical = NexusTheme.spacing.sm,
                        ),
            ) {
                SearchField(
                    value = textFieldValue,
                    onValueChange = onValueChange,
                    onSearch = {
                        onSearchEvent(SearchEvent.Submit)
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    },
                    onClear = {
                        onValueChange(TextFieldValue(""))
                        onSearchEvent(SearchEvent.ClearQuery)
                    },
                    onFocusChanged = onFocusChanged,
                    focusRequester = focusRequester,
                )
            }

            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
            ) {
                when (bodyMode) {
                    DiscoverBodyMode.Search -> {
                        SearchBody(
                            uiState = searchUiState,
                            onItemClick = { type, id ->
                                focusManager.clearFocus()
                                keyboardController?.hide()
                                onSearchEvent(SearchEvent.ResultOpened(type, id))
                                onItemClick(type, id)
                            },
                            onNavigateToAddons = onNavigateToAddons,
                            onRetry = { onSearchEvent(SearchEvent.Retry) },
                            listState = resultsListState,
                        )
                    }

                    DiscoverBodyMode.Recents -> {
                        RecentSearchesList(
                            history = searchUiState.history,
                            onSelect = { query ->
                                val newValue =
                                    TextFieldValue(
                                        text = query,
                                        selection = TextRange(query.length),
                                    )
                                onValueChange(newValue)
                                onSearchEvent(SearchEvent.UseRecent(query))
                                focusManager.clearFocus()
                                keyboardController?.hide()
                            },
                            onRemove = { onSearchEvent(SearchEvent.RemoveRecent(it)) },
                            onClearAll = { onSearchEvent(SearchEvent.ClearHistory) },
                            listState = recentsListState,
                        )
                    }

                    DiscoverBodyMode.Browse -> {
                        NexusRefreshBox(
                            isRefreshing = uiState.isRefreshing,
                            onRefresh = { onEvent(DiscoverEvent.Refresh) },
                            modifier = Modifier.fillMaxSize(),
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
                                            gridState = gridState,
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
            }
        }
    }
}

@Composable
private fun DiscoverPhaseBody(
    uiState: DiscoverUiState,
    onEvent: (DiscoverEvent) -> Unit,
    onItemClick: (type: String, id: String) -> Unit,
    gridState: LazyGridState,
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
                    if (userMsg.formatArgs.isEmpty()) {
                        stringResource(userMsg.resId)
                    } else {
                        stringResource(userMsg.resId, userMsg.formatArgs[0])
                    }
                DiscoverMessage(
                    title = errorText,
                    buttonText = stringResource(R.string.retry),
                    onButtonClick = { onEvent(DiscoverEvent.Retry) },
                    isOutlinedButton = true,
                )
            }

            is DiscoverPhase.Content -> {
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
            description = "Two imprisoned men bond over a number of years...",
            releaseInfo = "1994",
            imdbRating = "9.3",
            genres = listOf("Drama"),
        ),
    )

@Preview(name = "Content - Light")
@Preview(name = "Content - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun DiscoverContentPreview() {
    NexusTheme {
        DiscoverScreenContent(
            uiState =
                DiscoverUiState(
                    types = listOf("movie", "series"),
                    selectedType = "movie",
                    catalogs = previewCatalogs,
                    selectedCatalogKey = "cinemeta|movie|top",
                    phase = DiscoverPhase.Content,
                    items = previewItems,
                ),
            onEvent = {},
            onItemClick = { _, _ -> },
            onNavigateToAddons = {},
        )
    }
}

@Preview(name = "Loading - Light")
@Preview(name = "Loading - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun DiscoverLoadingPreview() {
    NexusTheme {
        DiscoverScreenContent(
            uiState =
                DiscoverUiState(
                    types = listOf("movie"),
                    selectedType = "movie",
                    catalogs = previewCatalogs,
                    selectedCatalogKey = "cinemeta|movie|top",
                    phase = DiscoverPhase.Loading,
                ),
            onEvent = {},
            onItemClick = { _, _ -> },
            onNavigateToAddons = {},
        )
    }
}

@Preview(name = "Empty - Light")
@Preview(name = "Empty - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun DiscoverEmptyPreview() {
    NexusTheme {
        DiscoverScreenContent(
            uiState =
                DiscoverUiState(
                    types = listOf("movie"),
                    selectedType = "movie",
                    catalogs = previewCatalogs,
                    selectedCatalogKey = "cinemeta|movie|top",
                    phase = DiscoverPhase.Empty,
                ),
            onEvent = {},
            onItemClick = { _, _ -> },
            onNavigateToAddons = {},
        )
    }
}

@Preview(name = "Error - Light")
@Preview(name = "Error - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun DiscoverErrorPreview() {
    NexusTheme {
        DiscoverScreenContent(
            uiState =
                DiscoverUiState(
                    types = listOf("movie"),
                    selectedType = "movie",
                    catalogs = previewCatalogs,
                    selectedCatalogKey = "cinemeta|movie|top",
                    phase = DiscoverPhase.Error(AppError.Network),
                ),
            onEvent = {},
            onItemClick = { _, _ -> },
            onNavigateToAddons = {},
        )
    }
}

@Preview(name = "No Addons - Light")
@Preview(name = "No Addons - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun DiscoverNoAddonsPreview() {
    NexusTheme {
        DiscoverScreenContent(
            uiState =
                DiscoverUiState(
                    phase = DiscoverPhase.NoAddons,
                ),
            onEvent = {},
            onItemClick = { _, _ -> },
            onNavigateToAddons = {},
        )
    }
}

@Preview(name = "No Catalogs - Light")
@Preview(name = "No Catalogs - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun DiscoverNoCatalogsPreview() {
    NexusTheme {
        DiscoverScreenContent(
            uiState =
                DiscoverUiState(
                    phase = DiscoverPhase.NoCatalogs,
                ),
            onEvent = {},
            onItemClick = { _, _ -> },
            onNavigateToAddons = {},
        )
    }
}
