package com.nexus.stream.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nexus.stream.domain.model.MetaPreview
import com.nexus.stream.domain.model.PosterShape
import com.nexus.stream.presentation.theme.NexusTheme

private val POSTER_WIDTH: Dp = 120.dp
private val POSTER_HEIGHT: Dp = 180.dp
private val SQUARE_SIZE: Dp = 120.dp
private val LANDSCAPE_WIDTH: Dp = 200.dp
private val LANDSCAPE_HEIGHT: Dp = 112.dp

@Composable
fun PosterCard(
    item: MetaPreview,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (width: Dp, height: Dp) =
        when (item.posterShape) {
            PosterShape.POSTER -> POSTER_WIDTH to POSTER_HEIGHT
            PosterShape.SQUARE -> SQUARE_SIZE to SQUARE_SIZE
            PosterShape.LANDSCAPE -> LANDSCAPE_WIDTH to LANDSCAPE_HEIGHT
        }

    var isError by remember(item.poster) { mutableStateOf(false) }

    Column(
        modifier =
            modifier
                .width(width)
                .clickable(onClick = onClick),
    ) {
        Box(
            modifier =
                Modifier
                    .size(width, height)
                    .clip(NexusTheme.shapes.poster)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            if (!item.poster.isNullOrBlank() && !isError) {
                AsyncImage(
                    model = item.poster,
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
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
        Spacer(modifier = Modifier.height(NexusTheme.spacing.xs))
        Text(
            text = item.name,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
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
