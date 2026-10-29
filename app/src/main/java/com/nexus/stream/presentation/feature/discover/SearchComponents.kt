package com.nexus.stream.presentation.feature.discover

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nexus.stream.R
import com.nexus.stream.domain.model.AppError
import com.nexus.stream.domain.model.DiscoverCatalog
import com.nexus.stream.domain.model.MetaPreview
import com.nexus.stream.domain.model.PosterShape
import com.nexus.stream.presentation.common.ContentRow
import com.nexus.stream.presentation.common.NexusTextButton
import com.nexus.stream.presentation.common.toUserMessage
import com.nexus.stream.presentation.feature.home.RowStatus
import com.nexus.stream.presentation.theme.NexusTheme

@Composable
fun SearchField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier =
            modifier
                .fillMaxWidth()
                .height(48.dp)
                .focusRequester(focusRequester)
                .onFocusChanged { onFocusChanged(it.isFocused) },
        singleLine = true,
        textStyle =
            MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface,
            ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions =
            KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Search,
            ),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        decorationBox = { innerTextField ->
            Row(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .clip(NexusTheme.shapes.button)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = NexusTheme.spacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(NexusTheme.spacing.sm),
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(NexusTheme.spacing.lg),
                )
                Box(modifier = Modifier.weight(1f)) {
                    if (value.text.isEmpty()) {
                        Text(
                            text = stringResource(R.string.search_hint),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    innerTextField()
                }
                if (value.text.isNotEmpty()) {
                    IconButton(
                        onClick = onClear,
                        modifier = Modifier.size(NexusTheme.spacing.xl),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.search_clear),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(NexusTheme.spacing.lg),
                        )
                    }
                }
            }
        },
    )
}

@Composable
fun RecentSearchesList(
    history: List<String>,
    onSelect: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = NexusTheme.spacing.lg),
    ) {
        item {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = NexusTheme.spacing.md, vertical = NexusTheme.spacing.sm),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.search_recent_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                NexusTextButton(onClick = onClearAll) {
                    Text(stringResource(R.string.search_recent_clear_all))
                }
            }
        }

        items(
            items = history,
            key = { it },
        ) { query ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(query) }
                        .padding(horizontal = NexusTheme.spacing.md, vertical = NexusTheme.spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(NexusTheme.spacing.md),
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(NexusTheme.spacing.md),
                )
                Text(
                    text = query,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = { onRemove(query) },
                    modifier = Modifier.size(NexusTheme.spacing.xl),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.search_recent_remove, query),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(NexusTheme.spacing.md),
                    )
                }
            }
        }
    }
}

@Composable
fun SearchBody(
    uiState: SearchUiState,
    onItemClick: (type: String, id: String) -> Unit,
    onNavigateToAddons: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    Box(modifier = modifier.fillMaxSize()) {
        when (val phase = uiState.phase) {
            is SearchPhase.Idle -> {}

            is SearchPhase.TooShort -> {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(NexusTheme.spacing.xl),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Text(
                        text = stringResource(R.string.search_too_short),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = NexusTheme.spacing.xl),
                    )
                }
            }

            is SearchPhase.NoSearchAddons -> {
                DiscoverMessage(
                    title = stringResource(R.string.search_unsupported),
                    buttonText = stringResource(R.string.discover_open_addons),
                    onButtonClick = onNavigateToAddons,
                )
            }

            is SearchPhase.Searching -> {
                SearchBodyContent.Searching()
            }

            is SearchPhase.Results -> {
                SearchBodyContent.Results(
                    uiState = uiState,
                    listState = listState,
                    onItemClick = onItemClick,
                    onRetry = onRetry,
                )
            }

            is SearchPhase.NoResults -> {
                SearchBodyContent.NoResults(
                    uiState = uiState,
                    onRetry = onRetry,
                )
            }

            is SearchPhase.Error -> {
                SearchBodyContent.Error(
                    error = phase.error,
                    onRetry = onRetry,
                )
            }
        }
    }
}

private object SearchBodyContent {
    @Composable
    fun Searching() {
        Column(modifier = Modifier.fillMaxSize()) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Spacer(modifier = Modifier.height(NexusTheme.spacing.sm))
            ContentRow(
                title = "",
                status = RowStatus.Loading,
                onRetry = {},
                onItemClick = { _, _ -> },
            )
            Spacer(modifier = Modifier.height(NexusTheme.spacing.md))
            ContentRow(
                title = "",
                status = RowStatus.Loading,
                onRetry = {},
                onItemClick = { _, _ -> },
            )
        }
    }

    @Composable
    fun Results(
        uiState: SearchUiState,
        listState: LazyListState,
        onItemClick: (type: String, id: String) -> Unit,
        onRetry: () -> Unit,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (uiState.isSearching) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = NexusTheme.spacing.lg),
                verticalArrangement = Arrangement.spacedBy(NexusTheme.spacing.md),
            ) {
                items(
                    items = uiState.rows,
                    key = { it.catalog.key },
                ) { row ->
                    val typeLabel = discoverTypeText(row.catalog.type)
                    ContentRow(
                        title = "${row.catalog.title} - $typeLabel",
                        status = RowStatus.Loaded(row.items),
                        onRetry = {},
                        onItemClick = onItemClick,
                        posterMarks = uiState.posterMarks,
                    )
                }

                if (!uiState.isSearching && uiState.failedCount > 0) {
                    item {
                        FailedCountFooter(failedCount = uiState.failedCount, onRetry = onRetry)
                    }
                }
            }
        }
    }

    @Composable
    fun NoResults(
        uiState: SearchUiState,
        onRetry: () -> Unit,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(NexusTheme.spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.search_no_results_title, uiState.normalizedQuery),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(NexusTheme.spacing.xs))
            Text(
                text = stringResource(R.string.search_no_results_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (!uiState.isSearching && uiState.failedCount > 0) {
                Spacer(modifier = Modifier.height(NexusTheme.spacing.md))
                FailedCountFooter(failedCount = uiState.failedCount, onRetry = onRetry)
            }
        }
    }

    @Composable
    fun FailedCountFooter(
        failedCount: Int,
        onRetry: () -> Unit,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        vertical = NexusTheme.spacing.md,
                        horizontal = NexusTheme.spacing.md,
                    ),
            horizontalArrangement = Arrangement.spacedBy(NexusTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text =
                    pluralStringResource(
                        R.plurals.search_failed_some,
                        failedCount,
                        failedCount,
                    ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            NexusTextButton(onClick = onRetry) {
                Text(stringResource(R.string.retry))
            }
        }
    }

    @Composable
    fun Error(
        error: AppError,
        onRetry: () -> Unit,
    ) {
        val userMsg = error.toUserMessage()
        val errorText =
            if (userMsg.formatArgs.isEmpty()) {
                stringResource(userMsg.resId)
            } else {
                stringResource(userMsg.resId, userMsg.formatArgs[0])
            }
        DiscoverMessage(
            title = errorText,
            buttonText = stringResource(R.string.retry),
            onButtonClick = onRetry,
            isOutlinedButton = true,
        )
    }
}

// Previews
private val previewCatalog =
    DiscoverCatalog(
        key = "top|movie",
        type = "movie",
        catalogId = "top",
        transportUrl = "https://addon.com/manifest.json",
        title = "Popular",
        genre = null,
        supportsSkip = false,
    )

private val previewItems =
    listOf(
        MetaPreview(
            id = "m1",
            type = "movie",
            name = "Dune: Part Two",
            poster = null,
            posterShape = PosterShape.POSTER,
            background = null,
            logo = null,
            description = null,
            releaseInfo = "2024",
            imdbRating = "8.6",
            genres = listOf("Sci-Fi", "Adventure"),
        ),
    )

@Preview(name = "Field - Light")
@Preview(name = "Field - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun SearchFieldPreview() {
    NexusTheme {
        Surface {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SearchField(
                    value = TextFieldValue(""),
                    onValueChange = {},
                    onSearch = {},
                    onClear = {},
                    onFocusChanged = {},
                    focusRequester = FocusRequester(),
                )
                SearchField(
                    value = TextFieldValue("dune"),
                    onValueChange = {},
                    onSearch = {},
                    onClear = {},
                    onFocusChanged = {},
                    focusRequester = FocusRequester(),
                )
            }
        }
    }
}

@Preview(name = "Recents - Light")
@Preview(name = "Recents - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun SearchRecentsPreview() {
    NexusTheme {
        Surface {
            RecentSearchesList(
                history = listOf("dune", "alien", "blade runner"),
                onSelect = {},
                onRemove = {},
                onClearAll = {},
            )
        }
    }
}

@Preview(name = "Searching - Light")
@Preview(name = "Searching - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun SearchSearchingPreview() {
    NexusTheme {
        Surface {
            SearchBody(
                uiState = SearchUiState(phase = SearchPhase.Searching, isSearching = true),
                onItemClick = { _, _ -> },
                onNavigateToAddons = {},
                onRetry = {},
            )
        }
    }
}

@Preview(name = "Results with failed footer - Light")
@Preview(name = "Results with failed footer - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun SearchResultsPreview() {
    NexusTheme {
        Surface {
            SearchBody(
                uiState =
                    SearchUiState(
                        phase = SearchPhase.Results,
                        isSearching = false,
                        failedCount = 1,
                        rows = listOf(SearchRow(catalog = previewCatalog, items = previewItems)),
                    ),
                onItemClick = { _, _ -> },
                onNavigateToAddons = {},
                onRetry = {},
            )
        }
    }
}

@Preview(name = "No Results - Light")
@Preview(name = "No Results - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun SearchNoResultsPreview() {
    NexusTheme {
        Surface {
            SearchBody(
                uiState =
                    SearchUiState(
                        phase = SearchPhase.NoResults,
                        normalizedQuery = "zzzzqq",
                        failedCount = 1,
                    ),
                onItemClick = { _, _ -> },
                onNavigateToAddons = {},
                onRetry = {},
            )
        }
    }
}

@Preview(name = "Too Short - Light")
@Preview(name = "Too Short - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun SearchTooShortPreview() {
    NexusTheme {
        Surface {
            SearchBody(
                uiState = SearchUiState(phase = SearchPhase.TooShort, normalizedQuery = "a"),
                onItemClick = { _, _ -> },
                onNavigateToAddons = {},
                onRetry = {},
            )
        }
    }
}

@Preview(name = "No Search Addons - Light")
@Preview(name = "No Search Addons - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun SearchNoSearchAddonsPreview() {
    NexusTheme {
        Surface {
            SearchBody(
                uiState = SearchUiState(phase = SearchPhase.NoSearchAddons, normalizedQuery = "dune"),
                onItemClick = { _, _ -> },
                onNavigateToAddons = {},
                onRetry = {},
            )
        }
    }
}

@Preview(name = "Error - Light")
@Preview(name = "Error - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun SearchErrorPreview() {
    NexusTheme {
        Surface {
            SearchBody(
                uiState =
                    SearchUiState(
                        phase = SearchPhase.Error(AppError.Network),
                        normalizedQuery = "dune",
                    ),
                onItemClick = { _, _ -> },
                onNavigateToAddons = {},
                onRetry = {},
            )
        }
    }
}
