package com.nexus.stream.presentation.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.nexus.stream.R
import com.nexus.stream.domain.model.MetaPreview
import com.nexus.stream.domain.model.PosterMark
import com.nexus.stream.domain.model.PosterShape
import com.nexus.stream.presentation.theme.NexusTheme

private val POSTER_WIDTH: Dp = 120.dp
private val POSTER_HEIGHT: Dp = 180.dp
private val SQUARE_SIZE: Dp = 120.dp
private val LANDSCAPE_WIDTH: Dp = 200.dp
private val LANDSCAPE_HEIGHT: Dp = 112.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PosterCard(
    item: MetaPreview,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    mark: PosterMark? = null,
    progress: Float? = null,
    subtitle: String? = null,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    fillWidth: Boolean = false,
) {
    val posterActions = LocalPosterActions.current
    val effectiveLongClick: () -> Unit = onLongClick ?: { posterActions.show(item, fromContinue = false) }
    val effectiveLongClickLabel = onLongClickLabel ?: stringResource(R.string.open_actions)

    val cardModifier =
        if (fillWidth) {
            modifier.fillMaxWidth()
        } else {
            val width = if (item.posterShape == PosterShape.LANDSCAPE) LANDSCAPE_WIDTH else POSTER_WIDTH
            modifier.width(width)
        }

    Column(
        modifier =
            cardModifier
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = effectiveLongClick,
                    onLongClickLabel = effectiveLongClickLabel,
                ).semantics {
                    customActions =
                        listOf(
                            CustomAccessibilityAction(label = effectiveLongClickLabel) {
                                effectiveLongClick()
                                true
                            },
                        )
                },
    ) {
        Box(
            modifier =
                posterCardBoxModifier(item.posterShape, fillWidth)
                    .clip(NexusTheme.shapes.poster)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            PosterImageOrPlaceholder(
                item = item,
                modifier = Modifier.fillMaxSize(),
            )

            if (progress != null) {
                val clampedProgress = progress.coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { clampedProgress },
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(NexusTheme.spacing.xs),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }

            if (mark != null) {
                PosterMarkBadge(
                    mark = mark,
                    modifier = Modifier.align(Alignment.TopEnd),
                )
            }
        }
        Spacer(modifier = Modifier.height(NexusTheme.spacing.xs))
        val textModifier = if (fillWidth) Modifier.fillMaxWidth() else Modifier
        Text(
            text = item.name,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = if (fillWidth) 1 else 2,
            overflow = TextOverflow.Ellipsis,
            modifier = textModifier,
        )
        if (!subtitle.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(NexusTheme.spacing.xxs))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = textModifier,
            )
        }
    }
}

private fun posterCardBoxModifier(
    shape: PosterShape,
    fillWidth: Boolean,
): Modifier =
    if (fillWidth) {
        val aspectRatio =
            when (shape) {
                PosterShape.POSTER -> 120f / 180f
                PosterShape.SQUARE -> 1f
                PosterShape.LANDSCAPE -> 200f / 112f
            }
        Modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
    } else {
        val (width, height) =
            when (shape) {
                PosterShape.POSTER -> POSTER_WIDTH to POSTER_HEIGHT
                PosterShape.SQUARE -> SQUARE_SIZE to SQUARE_SIZE
                PosterShape.LANDSCAPE -> LANDSCAPE_WIDTH to LANDSCAPE_HEIGHT
            }
        Modifier.size(width, height)
    }

@Composable
private fun PosterImageOrPlaceholder(
    item: MetaPreview,
    modifier: Modifier = Modifier,
) {
    val platformContext = LocalPlatformContext.current
    var isError by remember(item.poster) { mutableStateOf(false) }

    if (!item.poster.isNullOrBlank() && !isError) {
        AsyncImage(
            model =
                ImageRequest
                    .Builder(platformContext)
                    .data(item.poster)
                    .crossfade(true)
                    .build(),
            contentDescription = item.name,
            contentScale = ContentScale.Crop,
            modifier = modifier,
            onError = { isError = true },
        )
    } else {
        Text(
            text = item.name,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(NexusTheme.spacing.xs),
        )
    }
}

@Composable
private fun PosterMarkBadge(
    mark: PosterMark,
    modifier: Modifier = Modifier,
) {
    val icon =
        when (mark) {
            PosterMark.WATCHED -> Icons.Default.Check
            PosterMark.WATCHING -> Icons.Default.PlayArrow
        }
    val contentDesc =
        when (mark) {
            PosterMark.WATCHED -> stringResource(R.string.poster_mark_watched)
            PosterMark.WATCHING -> stringResource(R.string.poster_mark_watching)
        }
    Box(
        modifier =
            modifier
                .padding(NexusTheme.spacing.xs)
                .size(24.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.tertiaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDesc,
            tint = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Preview(name = "PosterCard - Poster")
@Composable
internal fun PosterCardPosterPreview() {
    NexusTheme {
        PosterCard(
            item =
                MetaPreview(
                    id = "tt0111161",
                    type = "movie",
                    name = "The Shawshank Redemption",
                    poster = "https://example.com/poster.jpg",
                    posterShape = PosterShape.POSTER,
                    background = null,
                    logo = null,
                    description = null,
                    releaseInfo = "1994",
                    imdbRating = "9.3",
                    genres = listOf("Drama"),
                ),
            onClick = {},
        )
    }
}

@Preview(name = "PosterCard - With Progress and Subtitle")
@Composable
internal fun PosterCardWithProgressPreview() {
    NexusTheme {
        PosterCard(
            item =
                MetaPreview(
                    id = "tt0903747",
                    type = "series",
                    name = "Breaking Bad",
                    poster = "https://example.com/poster.jpg",
                    posterShape = PosterShape.POSTER,
                    background = null,
                    logo = null,
                    description = null,
                    releaseInfo = "2008",
                    imdbRating = "9.5",
                    genres = listOf("Crime", "Drama"),
                ),
            progress = 0.65f,
            subtitle = "S1 E3",
            onClick = {},
        )
    }
}

@Preview(name = "PosterCard - No Poster")
@Composable
internal fun PosterCardNoPosterPreview() {
    NexusTheme {
        PosterCard(
            item =
                MetaPreview(
                    id = "tt0111162",
                    type = "movie",
                    name = "The Movie Without Any Poster Image",
                    poster = null,
                    posterShape = PosterShape.POSTER,
                    background = null,
                    logo = null,
                    description = null,
                    releaseInfo = "2024",
                    imdbRating = null,
                    genres = emptyList(),
                ),
            onClick = {},
        )
    }
}

@Preview(name = "PosterCard - Square")
@Composable
internal fun PosterCardSquarePreview() {
    NexusTheme {
        PosterCard(
            item =
                MetaPreview(
                    id = "channel_1",
                    type = "channel",
                    name = "Square Music Channel",
                    poster = null,
                    posterShape = PosterShape.SQUARE,
                    background = null,
                    logo = null,
                    description = null,
                    releaseInfo = null,
                    imdbRating = null,
                    genres = emptyList(),
                ),
            onClick = {},
        )
    }
}

@Preview(name = "PosterCard - Landscape")
@Composable
internal fun PosterCardLandscapePreview() {
    NexusTheme {
        PosterCard(
            item =
                MetaPreview(
                    id = "yt_1",
                    type = "series",
                    name = "Landscape Episode Banner Title",
                    poster = null,
                    posterShape = PosterShape.LANDSCAPE,
                    background = null,
                    logo = null,
                    description = null,
                    releaseInfo = null,
                    imdbRating = null,
                    genres = emptyList(),
                ),
            onClick = {},
        )
    }
}

@Preview(name = "PosterCard - With Badge")
@Composable
internal fun PosterCardWithBadgePreview() {
    NexusTheme {
        PosterCard(
            item =
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
            mark = PosterMark.WATCHED,
            onClick = {},
        )
    }
}

@Preview(name = "PosterCard - Fill width")
@Composable
internal fun PosterCardFillWidthPreview() {
    NexusTheme {
        PosterCard(
            item =
                MetaPreview(
                    id = "tt0111161",
                    type = "movie",
                    name = "The Shawshank Redemption",
                    poster = "https://example.com/poster.jpg",
                    posterShape = PosterShape.POSTER,
                    background = null,
                    logo = null,
                    description = null,
                    releaseInfo = "1994",
                    imdbRating = "9.3",
                    genres = listOf("Drama"),
                ),
            fillWidth = true,
            onClick = {},
        )
    }
}
