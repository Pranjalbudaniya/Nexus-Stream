package com.nexus.stream.presentation.common

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nexus.stream.R
import com.nexus.stream.domain.model.AppError
import com.nexus.stream.domain.model.MetaPreview
import com.nexus.stream.domain.model.PosterShape
import com.nexus.stream.presentation.feature.home.RowStatus
import com.nexus.stream.presentation.theme.NexusTheme

private const val SKELETON_COUNT = 6
private val SKELETON_WIDTH = 120.dp
private val SKELETON_HEIGHT = 180.dp

@Composable
fun ContentRow(
    title: String,
    status: RowStatus,
    onRetry: () -> Unit,
    onItemClick: (type: String, id: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (status is RowStatus.Loaded && status.items.isEmpty()) {
        return
    }

    val context = LocalContext.current

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = NexusTheme.spacing.sm),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = NexusTheme.spacing.lg),
        )
        Spacer(modifier = Modifier.height(NexusTheme.spacing.sm))

        when (status) {
            is RowStatus.Loading -> {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = NexusTheme.spacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(NexusTheme.spacing.sm),
                    userScrollEnabled = false,
                ) {
                    items(SKELETON_COUNT) {
                        Box(
                            modifier =
                                Modifier
                                    .width(SKELETON_WIDTH)
                                    .height(SKELETON_HEIGHT)
                                    .clip(NexusTheme.shapes.poster)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        )
                    }
                }
            }

            is RowStatus.Loaded -> {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = NexusTheme.spacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(NexusTheme.spacing.sm),
                ) {
                    items(
                        items = status.items,
                        key = { it.id },
                    ) { item ->
                        PosterCard(
                            item = item,
                            onClick = { onItemClick(item.type, item.id) },
                        )
                    }
                }
            }

            is RowStatus.Failed -> {
                val userMsg = status.error.toUserMessage()
                val errorText = formatErrorMessage(context, userMsg.resId, userMsg.formatArgs)

                NexusCard(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = NexusTheme.spacing.lg),
                ) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(NexusTheme.spacing.md),
                        verticalArrangement = Arrangement.spacedBy(NexusTheme.spacing.sm),
                    ) {
                        Text(
                            text = errorText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        NexusButton(onClick = onRetry) {
                            Text(stringResource(R.string.retry))
                        }
                    }
                }
            }
        }
    }
}

private fun formatErrorMessage(
    context: Context,
    resId: Int,
    formatArgs: List<Any>,
): String =
    if (formatArgs.isEmpty()) {
        context.getString(resId)
    } else if (formatArgs.size == 1) {
        context.getString(resId, formatArgs[0])
    } else {
        context.getString(resId, formatArgs[0], formatArgs[1])
    }

@Preview(name = "ContentRow - Loading")
@Composable
internal fun ContentRowLoadingPreview() {
    NexusTheme {
        ContentRow(
            title = "Popular - Movies",
            status = RowStatus.Loading,
            onRetry = {},
            onItemClick = { _, _ -> },
        )
    }
}

@Preview(name = "ContentRow - Loaded")
@Composable
internal fun ContentRowLoadedPreview() {
    NexusTheme {
        ContentRow(
            title = "Featured - Movies",
            status =
                RowStatus.Loaded(
                    listOf(
                        MetaPreview(
                            id = "1",
                            type = "movie",
                            name = "Interstellar",
                            poster = null,
                            posterShape = PosterShape.POSTER,
                            background = null,
                            logo = null,
                            description = null,
                            releaseInfo = "2014",
                            imdbRating = "8.7",
                            genres = emptyList(),
                        ),
                        MetaPreview(
                            id = "2",
                            type = "movie",
                            name = "Inception",
                            poster = null,
                            posterShape = PosterShape.POSTER,
                            background = null,
                            logo = null,
                            description = null,
                            releaseInfo = "2010",
                            imdbRating = "8.8",
                            genres = emptyList(),
                        ),
                    ),
                ),
            onRetry = {},
            onItemClick = { _, _ -> },
        )
    }
}

@Preview(name = "ContentRow - Failed")
@Composable
internal fun ContentRowFailedPreview() {
    NexusTheme {
        ContentRow(
            title = "Popular - Series",
            status = RowStatus.Failed(AppError.Network),
            onRetry = {},
            onItemClick = { _, _ -> },
        )
    }
}
