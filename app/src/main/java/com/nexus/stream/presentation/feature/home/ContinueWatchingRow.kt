package com.nexus.stream.presentation.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.nexus.stream.R
import com.nexus.stream.domain.model.ContinueWatchingItem
import com.nexus.stream.domain.model.MetaPreview
import com.nexus.stream.domain.model.PosterShape
import com.nexus.stream.domain.model.progress
import com.nexus.stream.presentation.common.PosterCard
import com.nexus.stream.presentation.theme.NexusTheme

@Composable
fun ContinueWatchingRow(
    items: List<ContinueWatchingItem>,
    onItemClick: (type: String, id: String) -> Unit,
    onRemoveItem: (metaId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = NexusTheme.spacing.sm),
    ) {
        Text(
            text = stringResource(R.string.continue_watching_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = NexusTheme.spacing.lg),
        )
        Spacer(modifier = Modifier.height(NexusTheme.spacing.sm))

        LazyRow(
            contentPadding = PaddingValues(horizontal = NexusTheme.spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(NexusTheme.spacing.sm),
        ) {
            items(
                items = items,
                key = { it.metaId },
            ) { item ->
                ContinueWatchingCard(
                    item = item,
                    onClick = { onItemClick(item.type, item.metaId) },
                    onRemove = { onRemoveItem(item.metaId) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

@Composable
private fun ContinueWatchingCard(
    item: ContinueWatchingItem,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    val preview =
        remember(item) {
            MetaPreview(
                id = item.metaId,
                type = item.type,
                name = item.name,
                poster = item.poster,
                posterShape = PosterShape.POSTER,
                background = null,
                logo = null,
                description = null,
                releaseInfo = null,
                imdbRating = null,
                genres = emptyList(),
            )
        }

    val subtitle =
        remember(item.season, item.episode) {
            when {
                item.season != null && item.episode != null -> "S${item.season} E${item.episode}"
                item.episode != null -> "E${item.episode}"
                item.season != null -> "S${item.season}"
                else -> null
            }
        }

    Box(modifier = modifier) {
        PosterCard(
            item = preview,
            progress = item.progress,
            subtitle = subtitle,
            onClick = onClick,
            onLongClick = { menuExpanded = true },
            onLongClickLabel = stringResource(R.string.continue_watching_remove),
        )

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.continue_watching_remove)) },
                onClick = {
                    menuExpanded = false
                    onRemove()
                },
            )
        }
    }
}

@Preview(name = "ContinueWatchingRow - Light")
@Composable
internal fun ContinueWatchingRowPreview() {
    NexusTheme {
        ContinueWatchingRow(
            items =
                listOf(
                    ContinueWatchingItem(
                        metaId = "tt0903747",
                        type = "series",
                        name = "Breaking Bad",
                        poster = "https://example.com/poster.jpg",
                        videoId = "tt0903747:1:3",
                        season = 1,
                        episode = 3,
                        positionMs = 1_800_000L,
                        durationMs = 3_000_000L,
                        updatedAt = 1000L,
                    ),
                    ContinueWatchingItem(
                        metaId = "tt0111161",
                        type = "movie",
                        name = "The Shawshank Redemption",
                        poster = null,
                        videoId = "tt0111161",
                        season = null,
                        episode = null,
                        positionMs = 3_600_000L,
                        durationMs = 7_200_000L,
                        updatedAt = 2000L,
                    ),
                ),
            onItemClick = { _, _ -> },
            onRemoveItem = {},
        )
    }
}
