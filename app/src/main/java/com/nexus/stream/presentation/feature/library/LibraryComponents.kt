package com.nexus.stream.presentation.feature.library

import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nexus.stream.R
import com.nexus.stream.domain.model.EpisodeLabel
import com.nexus.stream.domain.model.HistoryBucket
import com.nexus.stream.domain.model.ListStatus
import com.nexus.stream.domain.model.MetaPreview
import com.nexus.stream.domain.model.PosterMark
import com.nexus.stream.domain.model.PosterShape
import com.nexus.stream.domain.model.WatchlistSort
import com.nexus.stream.presentation.common.LocalPosterActions
import com.nexus.stream.presentation.common.NexusButton
import com.nexus.stream.presentation.common.NexusOutlinedButton
import com.nexus.stream.presentation.common.NexusTextButton
import com.nexus.stream.presentation.common.PosterCard
import com.nexus.stream.presentation.feature.discover.DISCOVER_GRID_MIN_POSTER_DP
import com.nexus.stream.presentation.feature.discover.DISCOVER_SKELETON_COUNT
import com.nexus.stream.presentation.feature.discover.discoverTypeText
import com.nexus.stream.presentation.feature.streams.DropdownOption
import com.nexus.stream.presentation.feature.streams.FilterDropdownChip
import com.nexus.stream.presentation.theme.NexusTheme

private val LIBRARY_CHIP_MIN_HEIGHT = 36.dp
private const val POSTER_ASPECT_RATIO = 2f / 3f

@get:StringRes
val ListStatus.labelRes: Int
    get() =
        when (this) {
            ListStatus.WATCHING -> R.string.library_status_watching
            ListStatus.PLAN_TO_WATCH -> R.string.library_status_plan_to_watch
            ListStatus.COMPLETED -> R.string.library_status_completed
            ListStatus.ON_HOLD -> R.string.library_status_on_hold
            ListStatus.DROPPED -> R.string.library_status_dropped
        }

@get:StringRes
val WatchlistSort.labelRes: Int
    get() =
        when (this) {
            WatchlistSort.RECENTLY_ADDED -> R.string.library_sort_recently_added
            WatchlistSort.TITLE_AZ -> R.string.library_sort_title_az
            WatchlistSort.TITLE_ZA -> R.string.library_sort_title_za
        }

@get:StringRes
val HistoryBucket.labelRes: Int
    get() =
        when (this) {
            HistoryBucket.TODAY -> R.string.library_history_bucket_today
            HistoryBucket.YESTERDAY -> R.string.library_history_bucket_yesterday
            HistoryBucket.LAST_7_DAYS -> R.string.library_history_bucket_last_7_days
            HistoryBucket.LAST_30_DAYS -> R.string.library_history_bucket_last_30_days
            HistoryBucket.EARLIER -> R.string.library_history_bucket_earlier
        }

val EpisodeLabel.text: String
    get() =
        when (this) {
            is EpisodeLabel.SeasonEpisode -> "S$season E$episode"
            is EpisodeLabel.EpisodeOnly -> "E$episode"
            is EpisodeLabel.SeasonOnly -> "S$season"
        }

@Composable
fun LibraryTabRow(
    selectedTab: LibraryTab,
    onSelectTab: (LibraryTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    TabRow(
        selectedTabIndex = selectedTab.ordinal,
        modifier = modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.primary,
        divider = {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )
        },
    ) {
        Tab(
            selected = selectedTab == LibraryTab.WATCHLIST,
            onClick = { onSelectTab(LibraryTab.WATCHLIST) },
            text = {
                Text(
                    text = stringResource(R.string.library_tab_watchlist),
                    style = MaterialTheme.typography.titleSmall,
                )
            },
            selectedContentColor = MaterialTheme.colorScheme.primary,
            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Tab(
            selected = selectedTab == LibraryTab.HISTORY,
            onClick = { onSelectTab(LibraryTab.HISTORY) },
            text = {
                Text(
                    text = stringResource(R.string.library_tab_history),
                    style = MaterialTheme.typography.titleSmall,
                )
            },
            selectedContentColor = MaterialTheme.colorScheme.primary,
            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun WatchlistControlsRow(
    ui: WatchlistUi,
    onEvent: (LibraryEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val statusLabel =
        if (ui.status == null) {
            stringResource(R.string.library_filter_all_statuses)
        } else {
            stringResource(ui.status.labelRes)
        }
    val typeLabel =
        if (ui.type == null) {
            stringResource(R.string.library_filter_all_types)
        } else {
            discoverTypeText(ui.type)
        }
    val sortLabel = stringResource(ui.sort.labelRes)

    val localizedStatusOptions =
        ui.statusOptions.map { option ->
            val label =
                if (option.value == null) {
                    stringResource(R.string.library_filter_all_statuses)
                } else {
                    stringResource(option.value.labelRes)
                }
            DropdownOption(value = option.value, label = label, count = option.count, selected = option.selected)
        }

    val localizedTypeOptions =
        ui.typeOptions.map { option ->
            val label =
                if (option.value == null) {
                    stringResource(R.string.library_filter_all_types)
                } else {
                    discoverTypeText(option.value)
                }
            DropdownOption(value = option.value, label = label, count = option.count, selected = option.selected)
        }

    val sortOptions =
        listOf(
            DropdownOption(
                value = WatchlistSort.RECENTLY_ADDED,
                label = stringResource(R.string.library_sort_recently_added),
                selected = ui.sort == WatchlistSort.RECENTLY_ADDED,
            ),
            DropdownOption(
                value = WatchlistSort.TITLE_AZ,
                label = stringResource(R.string.library_sort_title_az),
                selected = ui.sort == WatchlistSort.TITLE_AZ,
            ),
            DropdownOption(
                value = WatchlistSort.TITLE_ZA,
                label = stringResource(R.string.library_sort_title_za),
                selected = ui.sort == WatchlistSort.TITLE_ZA,
            ),
        )

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = NexusTheme.spacing.md, vertical = NexusTheme.spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(NexusTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterDropdownChip(
            selectedLabel = statusLabel,
            isNonDefault = ui.status != null,
            options = localizedStatusOptions,
            onSelect = { onEvent(LibraryEvent.SelectStatus(it)) },
            minHeight = LIBRARY_CHIP_MIN_HEIGHT,
        )
        FilterDropdownChip(
            selectedLabel = typeLabel,
            isNonDefault = ui.type != null,
            options = localizedTypeOptions,
            onSelect = { onEvent(LibraryEvent.SelectWatchlistType(it)) },
            minHeight = LIBRARY_CHIP_MIN_HEIGHT,
        )
        FilterDropdownChip(
            selectedLabel = sortLabel,
            isNonDefault = ui.sort != WatchlistSort.RECENTLY_ADDED,
            options = sortOptions,
            onSelect = { it?.let { s -> onEvent(LibraryEvent.SelectSort(s)) } },
            minHeight = LIBRARY_CHIP_MIN_HEIGHT,
        )
    }
}

@Composable
fun HistoryControlsRow(
    ui: HistoryUi,
    hasEntries: Boolean,
    onEvent: (LibraryEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val typeLabel =
        if (ui.type == null) {
            stringResource(R.string.library_filter_all_types)
        } else {
            discoverTypeText(ui.type)
        }

    val localizedTypeOptions =
        ui.typeOptions.map { option ->
            val label =
                if (option.value == null) {
                    stringResource(R.string.library_filter_all_types)
                } else {
                    discoverTypeText(option.value)
                }
            DropdownOption(value = option.value, label = label, count = option.count, selected = option.selected)
        }

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = NexusTheme.spacing.md, vertical = NexusTheme.spacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterDropdownChip(
            selectedLabel = typeLabel,
            isNonDefault = ui.type != null,
            options = localizedTypeOptions,
            onSelect = { onEvent(LibraryEvent.SelectHistoryType(it)) },
            minHeight = LIBRARY_CHIP_MIN_HEIGHT,
        )
        NexusTextButton(
            onClick = { onEvent(LibraryEvent.RequestClear) },
            enabled = hasEntries,
        ) {
            Text(stringResource(R.string.library_history_clear_button))
        }
    }
}

@Composable
fun WatchlistGrid(
    cards: List<WatchlistCardUi>,
    gridState: LazyGridState,
    onItemClick: (type: String, id: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val localPosterActions = LocalPosterActions.current
    LazyVerticalGrid(
        columns = GridCells.Adaptive(DISCOVER_GRID_MIN_POSTER_DP),
        state = gridState,
        modifier = modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(
                horizontal = NexusTheme.spacing.md,
                vertical = NexusTheme.spacing.sm,
            ),
        horizontalArrangement = Arrangement.spacedBy(NexusTheme.spacing.xs),
        verticalArrangement = Arrangement.spacedBy(NexusTheme.spacing.xs),
    ) {
        items(
            items = cards,
            key = { it.metaId },
            contentType = { "watchlist_card" },
        ) { card ->
            val preview =
                remember(card) {
                    MetaPreview(
                        id = card.metaId,
                        type = card.type,
                        name = card.name,
                        poster = card.poster,
                        posterShape = PosterShape.POSTER,
                        background = null,
                        logo = null,
                        description = null,
                        releaseInfo = null,
                        imdbRating = null,
                        genres = emptyList(),
                    )
                }
            val subtitle = if (card.showStatusLabel) stringResource(card.status.labelRes) else null
            PosterCard(
                item = preview,
                onClick = { onItemClick(card.type, card.metaId) },
                mark = card.mark,
                subtitle = subtitle,
                fillWidth = true,
                onLongClick = {
                    localPosterActions.show(
                        item = preview,
                        onRemoveFromHistory = null,
                    )
                },
                modifier = Modifier.animateItem(),
            )
        }
    }
}

@Composable
fun HistoryGrid(
    sections: List<HistorySectionUi>,
    truncated: Boolean,
    gridState: LazyGridState,
    onItemClick: (type: String, id: String) -> Unit,
    onEvent: (LibraryEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val localPosterActions = LocalPosterActions.current
    LazyVerticalGrid(
        columns = GridCells.Adaptive(DISCOVER_GRID_MIN_POSTER_DP),
        state = gridState,
        modifier = modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(
                horizontal = NexusTheme.spacing.md,
                vertical = NexusTheme.spacing.sm,
            ),
        horizontalArrangement = Arrangement.spacedBy(NexusTheme.spacing.xs),
        verticalArrangement = Arrangement.spacedBy(NexusTheme.spacing.xs),
    ) {
        sections.forEach { section ->
            item(
                key = "header_${section.bucket}",
                span = { GridItemSpan(maxLineSpan) },
                contentType = "bucket_header",
            ) {
                Text(
                    text = stringResource(section.bucket.labelRes),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = NexusTheme.spacing.md, bottom = NexusTheme.spacing.xs),
                )
            }
            items(
                items = section.items,
                key = { it.metaId },
                contentType = { "history_card" },
            ) { card ->
                val preview =
                    remember(card) {
                        MetaPreview(
                            id = card.metaId,
                            type = card.entry.type,
                            name = card.entry.name.orEmpty(),
                            poster = card.entry.poster,
                            posterShape = PosterShape.POSTER,
                            background = null,
                            logo = null,
                            description = null,
                            releaseInfo = null,
                            imdbRating = null,
                            genres = emptyList(),
                        )
                    }
                PosterCard(
                    item = preview,
                    onClick = { onItemClick(card.entry.type, card.metaId) },
                    progress = card.progress,
                    mark = if (card.watched) PosterMark.WATCHED else null,
                    subtitle = card.episodeLabel?.text,
                    fillWidth = true,
                    onLongClick = {
                        localPosterActions.show(
                            item = preview,
                            onRemoveFromHistory = {
                                onEvent(LibraryEvent.RequestRemoveFromHistory(card.metaId))
                            },
                        )
                    },
                    modifier = Modifier.animateItem(),
                )
            }
        }
        if (truncated) {
            item(
                key = "history_truncated_footer",
                span = { GridItemSpan(maxLineSpan) },
                contentType = "truncated_footer",
            ) {
                Text(
                    text = stringResource(R.string.library_history_truncated_footer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = NexusTheme.spacing.md, bottom = NexusTheme.spacing.lg),
                )
            }
        }
    }
}

@Composable
fun LibraryEmptyState(
    title: String,
    body: String,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    isOutlinedAction: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(NexusTheme.spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(NexusTheme.spacing.xs))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionText != null && onAction != null) {
            Spacer(modifier = Modifier.height(NexusTheme.spacing.md))
            if (isOutlinedAction) {
                NexusOutlinedButton(onClick = onAction) {
                    Text(actionText)
                }
            } else {
                NexusButton(onClick = onAction) {
                    Text(actionText)
                }
            }
        }
    }
}

@Composable
fun LibrarySkeletonGrid(modifier: Modifier = Modifier) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(DISCOVER_GRID_MIN_POSTER_DP),
        modifier = modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(
                horizontal = NexusTheme.spacing.md,
                vertical = NexusTheme.spacing.sm,
            ),
        horizontalArrangement = Arrangement.spacedBy(NexusTheme.spacing.xs),
        verticalArrangement = Arrangement.spacedBy(NexusTheme.spacing.xs),
        userScrollEnabled = false,
    ) {
        items(DISCOVER_SKELETON_COUNT) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(POSTER_ASPECT_RATIO)
                        .clip(NexusTheme.shapes.poster)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        }
    }
}

@Preview(name = "LibraryTabRow - Light")
@Preview(name = "LibraryTabRow - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun LibraryTabRowPreview() {
    NexusTheme {
        LibraryTabRow(selectedTab = LibraryTab.WATCHLIST, onSelectTab = {})
    }
}

@Preview(name = "LibraryEmptyState - Light")
@Preview(name = "LibraryEmptyState - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun LibraryEmptyStatePreview() {
    NexusTheme {
        LibraryEmptyState(
            title = "Your watchlist is empty",
            body = "Save movies and series you want to watch later.",
            actionText = "Explore Discover",
            onAction = {},
        )
    }
}

@Preview(name = "LibrarySkeletonGrid - Light")
@Preview(name = "LibrarySkeletonGrid - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun LibrarySkeletonGridPreview() {
    NexusTheme {
        LibrarySkeletonGrid()
    }
}
