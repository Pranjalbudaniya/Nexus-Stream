package com.nexus.stream.presentation.feature.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nexus.stream.R
import com.nexus.stream.domain.model.DiscoverCatalog
import com.nexus.stream.domain.model.GenreKind
import com.nexus.stream.domain.model.MetaPreview
import com.nexus.stream.domain.model.PosterMark
import com.nexus.stream.domain.model.PosterShape
import com.nexus.stream.domain.model.TypeDisplay
import com.nexus.stream.domain.usecase.typeDisplay
import com.nexus.stream.presentation.common.NexusButton
import com.nexus.stream.presentation.common.NexusOutlinedButton
import com.nexus.stream.presentation.common.NexusTextButton
import com.nexus.stream.presentation.common.PosterCard
import com.nexus.stream.presentation.feature.streams.DropdownOption
import com.nexus.stream.presentation.feature.streams.FilterDropdownChip
import com.nexus.stream.presentation.theme.NexusTheme

const val DISCOVER_PREFETCH_DISTANCE = 8
const val DISCOVER_SKELETON_COUNT = 12

private val CARD_POSTER_WIDTH = 120.dp
private val CARD_POSTER_HEIGHT = 180.dp
private val CARD_LANDSCAPE_WIDTH = 200.dp
private val CARD_LANDSCAPE_HEIGHT = 112.dp

@Composable
fun TypeChipsRow(
    types: List<String>,
    selectedType: String?,
    onSelectType: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val rowDescription = stringResource(R.string.discover_cd_type_row)

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .semantics { contentDescription = rowDescription }
                .padding(horizontal = NexusTheme.spacing.md, vertical = NexusTheme.spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(NexusTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        types.forEach { type ->
            val isSelected = type.equals(selectedType, ignoreCase = true)
            val label =
                when (val display = typeDisplay(type)) {
                    is TypeDisplay.Movie -> stringResource(R.string.discover_type_movie)
                    is TypeDisplay.Series -> stringResource(R.string.discover_type_series)
                    is TypeDisplay.Custom -> display.label
                }

            FilterChip(
                selected = isSelected,
                onClick = { onSelectType(type) },
                label = { Text(label) },
                shape = NexusTheme.shapes.chip,
                colors =
                    FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    ),
            )
        }
    }
}

@Composable
fun FiltersRow(
    catalogs: List<DiscoverCatalog>,
    selectedCatalogKey: String?,
    onSelectCatalog: (String) -> Unit,
    genreUi: DiscoverGenreUi?,
    onSelectGenre: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = NexusTheme.spacing.md, vertical = NexusTheme.spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(NexusTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (catalogs.size == 1) {
            Text(
                text = catalogs.first().title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        } else if (catalogs.size >= 2) {
            val currentCatalog = catalogs.find { it.key == selectedCatalogKey } ?: catalogs.first()
            val options =
                catalogs.map { catalog ->
                    DropdownOption(
                        value = catalog.key,
                        label = catalog.title,
                        selected = catalog.key == selectedCatalogKey,
                    )
                }

            FilterDropdownChip(
                selectedLabel = currentCatalog.title,
                isNonDefault = selectedCatalogKey != catalogs.firstOrNull()?.key,
                options = options,
                onSelect = { key -> if (key != null) onSelectCatalog(key) },
            )
        }

        if (genreUi != null) {
            val isYear = genreUi.kind == GenreKind.YEAR
            val allLabel =
                if (isYear) {
                    stringResource(R.string.discover_year_all)
                } else {
                    stringResource(R.string.discover_genre_all)
                }

            val options =
                if (genreUi.isRequired) {
                    genreUi.options.map { option ->
                        DropdownOption(
                            value = option,
                            label = option,
                            selected = option == genreUi.selectedGenre,
                        )
                    }
                } else {
                    listOf(
                        DropdownOption<String>(
                            value = null,
                            label = allLabel,
                            selected = genreUi.selectedGenre == null,
                        ),
                    ) +
                        genreUi.options.map { option ->
                            DropdownOption(
                                value = option,
                                label = option,
                                selected = option == genreUi.selectedGenre,
                            )
                        }
                }

            val selectedLabel = genreUi.selectedGenre ?: allLabel
            val isNonDefault =
                if (genreUi.isRequired) {
                    genreUi.selectedGenre != genreUi.options.firstOrNull()
                } else {
                    genreUi.selectedGenre != null
                }

            FilterDropdownChip(
                selectedLabel = selectedLabel,
                isNonDefault = isNonDefault,
                options = options,
                onSelect = onSelectGenre,
            )
        }
    }
}

@Composable
fun DiscoverGrid(
    items: List<MetaPreview>,
    gridShape: PosterShape,
    footer: DiscoverFooter,
    posterMarks: Map<String, PosterMark>,
    onItemClick: (type: String, id: String) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    state: LazyGridState,
    modifier: Modifier = Modifier,
) {
    val minSize =
        if (gridShape == PosterShape.LANDSCAPE) {
            CARD_LANDSCAPE_WIDTH
        } else {
            CARD_POSTER_WIDTH
        }

    val lastVisibleItemIndex by remember {
        derivedStateOf {
            state.layoutInfo.visibleItemsInfo
                .lastOrNull()
                ?.index ?: 0
        }
    }

    LaunchedEffect(lastVisibleItemIndex, items.size) {
        if (items.isNotEmpty() && lastVisibleItemIndex >= items.size - DISCOVER_PREFETCH_DISTANCE) {
            onLoadMore()
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = minSize),
        state = state,
        modifier = modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(
                start = NexusTheme.spacing.md,
                end = NexusTheme.spacing.md,
                bottom = NexusTheme.spacing.lg,
            ),
        horizontalArrangement = Arrangement.spacedBy(NexusTheme.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(NexusTheme.spacing.sm),
    ) {
        items(
            items = items,
            key = { it.id },
        ) { item ->
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.TopCenter,
            ) {
                PosterCard(
                    item = item,
                    onClick = { onItemClick(item.type, item.id) },
                    mark = posterMarks[item.id],
                )
            }
        }

        if (footer !is DiscoverFooter.None) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                FooterItem(footer = footer, onRetry = onRetry)
            }
        }
    }
}

@Composable
fun SkeletonGrid(
    gridShape: PosterShape,
    modifier: Modifier = Modifier,
) {
    val (width, height) =
        if (gridShape == PosterShape.LANDSCAPE) {
            CARD_LANDSCAPE_WIDTH to CARD_LANDSCAPE_HEIGHT
        } else {
            CARD_POSTER_WIDTH to CARD_POSTER_HEIGHT
        }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = width),
        modifier = modifier.fillMaxSize(),
        userScrollEnabled = false,
        contentPadding =
            PaddingValues(
                start = NexusTheme.spacing.md,
                end = NexusTheme.spacing.md,
                bottom = NexusTheme.spacing.lg,
            ),
        horizontalArrangement = Arrangement.spacedBy(NexusTheme.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(NexusTheme.spacing.sm),
    ) {
        items(DISCOVER_SKELETON_COUNT) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.TopCenter,
            ) {
                Box(
                    modifier =
                        Modifier
                            .width(width)
                            .height(height)
                            .clip(NexusTheme.shapes.poster)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                )
            }
        }
    }
}

@Composable
fun FooterItem(
    footer: DiscoverFooter,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = NexusTheme.spacing.md),
        contentAlignment = Alignment.Center,
    ) {
        when (footer) {
            is DiscoverFooter.Loading -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(NexusTheme.spacing.xl),
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            is DiscoverFooter.Error -> {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(NexusTheme.spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.discover_more_failed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    NexusTextButton(onClick = onRetry) {
                        Text(stringResource(R.string.retry))
                    }
                }
            }

            is DiscoverFooter.None -> {
                // No footer
            }
        }
    }
}

@Composable
fun DiscoverMessage(
    title: String,
    message: String? = null,
    buttonText: String? = null,
    onButtonClick: (() -> Unit)? = null,
    isOutlinedButton: Boolean = false,
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
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        if (message != null) {
            Spacer(modifier = Modifier.height(NexusTheme.spacing.xs))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (buttonText != null && onButtonClick != null) {
            Spacer(modifier = Modifier.height(NexusTheme.spacing.md))
            if (isOutlinedButton) {
                NexusOutlinedButton(onClick = onButtonClick) {
                    Text(buttonText)
                }
            } else {
                NexusButton(onClick = onButtonClick) {
                    Text(buttonText)
                }
            }
        }
    }
}
