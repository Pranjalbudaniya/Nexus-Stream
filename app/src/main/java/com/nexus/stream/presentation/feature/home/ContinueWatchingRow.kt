package com.nexus.stream.presentation.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nexus.stream.R
import com.nexus.stream.domain.model.ContinueKind
import com.nexus.stream.domain.model.ContinueWatchingItem
import com.nexus.stream.domain.model.MetaPreview
import com.nexus.stream.domain.model.PosterShape
import com.nexus.stream.domain.model.canRemove
import com.nexus.stream.domain.model.progress
import com.nexus.stream.presentation.common.LocalPosterActions
import com.nexus.stream.presentation.common.PosterCard
import com.nexus.stream.presentation.theme.NexusTheme

private val POSTER_WIDTH: Dp = 120.dp
private val POSTER_HEIGHT: Dp = 180.dp

@Composable
fun ContinueWatchingRow(
    items: List<ContinueWatchingItem>,
    onItemClick: (type: String, id: String) -> Unit,
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
    modifier: Modifier = Modifier,
) {
    val posterActions = LocalPosterActions.current
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
        remember(item.kind, item.season, item.episode) {
            if (item.kind == ContinueKind.UP_NEXT) {
                null
            } else {
                when {
                    item.season != null && item.episode != null -> "S${item.season} E${item.episode}"
                    item.episode != null -> "E${item.episode}"
                    item.season != null -> "S${item.season}"
                    else -> null
                }
            }
        }

    val a11yDesc =
        if (item.kind == ContinueKind.UP_NEXT) {
            stringResource(R.string.home_continue_up_next_a11y, item.name)
        } else {
            null
        }

    val cardModifier =
        if (a11yDesc != null) {
            modifier.semantics { contentDescription = a11yDesc }
        } else {
            modifier
        }

    Box(modifier = cardModifier) {
        PosterCard(
            item = preview,
            progress = item.progress,
            subtitle = subtitle,
            onClick = onClick,
            onLongClick = { posterActions.show(preview, fromContinue = item.canRemove) },
            onLongClickLabel = stringResource(R.string.open_actions),
        )
        if (item.kind == ContinueKind.UP_NEXT) {
            Box(
                modifier =
                    Modifier
                        .size(width = POSTER_WIDTH, height = POSTER_HEIGHT)
                        .padding(NexusTheme.spacing.xs),
                contentAlignment = Alignment.BottomStart,
            ) {
                Text(
                    text = stringResource(R.string.home_continue_up_next),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier =
                        Modifier
                            .background(
                                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f),
                                shape = NexusTheme.shapes.chip,
                            ).padding(
                                horizontal = NexusTheme.spacing.xs,
                                vertical = NexusTheme.spacing.xxs,
                            ),
                )
            }
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
                        kind = ContinueKind.RESUME,
                    ),
                    ContinueWatchingItem(
                        metaId = "tt0903748",
                        type = "series",
                        name = "Better Call Saul",
                        poster = null,
                        videoId = "tt0903748:1:4",
                        season = 1,
                        episode = 4,
                        positionMs = 0L,
                        durationMs = 0L,
                        updatedAt = 1500L,
                        kind = ContinueKind.UP_NEXT,
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
                        kind = ContinueKind.RESUME,
                    ),
                ),
            onItemClick = { _, _ -> },
        )
    }
}
