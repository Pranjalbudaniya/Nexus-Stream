package com.nexus.stream.presentation.feature.detail

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.nexus.stream.R
import com.nexus.stream.domain.model.MetaDetail
import com.nexus.stream.presentation.theme.NexusTheme

private const val ASPECT_RATIO_16_9: Float = 16f / 9f
private const val GRADIENT_STOP_THRESHOLD: Float = 0.55f
private const val LOGO_WIDTH_FRACTION: Float = 0.7f

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailHeader(
    meta: MetaDetail,
    inWatchlist: Boolean,
    isSynopsisExpanded: Boolean,
    trailerUrl: String?,
    playActionTitle: String,
    isPlayEnabled: Boolean,
    onPlayMain: () -> Unit,
    onToggleWatchlist: () -> Unit,
    onOpenTrailer: () -> Unit,
    onToggleSynopsis: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        BackdropSection(
            meta = meta,
            modifier = Modifier.fillMaxWidth(),
        )

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = NexusTheme.spacing.lg),
        ) {
            Spacer(modifier = Modifier.height(NexusTheme.spacing.sm))

            TitleAndInfo(meta = meta)

            Spacer(modifier = Modifier.height(NexusTheme.spacing.md))

            ActionButtonsRow(
                meta = meta,
                inWatchlist = inWatchlist,
                trailerUrl = trailerUrl,
                playActionTitle = playActionTitle,
                isPlayEnabled = isPlayEnabled,
                onPlayMain = onPlayMain,
                onToggleWatchlist = onToggleWatchlist,
                onOpenTrailer = onOpenTrailer,
            )

            if (!meta.description.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(NexusTheme.spacing.md))
                SynopsisSection(
                    description = meta.description,
                    isExpanded = isSynopsisExpanded,
                    onToggleSynopsis = onToggleSynopsis,
                )
            }

            if (meta.genres.isNotEmpty()) {
                Spacer(modifier = Modifier.height(NexusTheme.spacing.sm))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(NexusTheme.spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(NexusTheme.spacing.xs),
                ) {
                    meta.genres.forEach { genre ->
                        AssistChip(
                            onClick = {},
                            label = { Text(genre, style = MaterialTheme.typography.labelMedium) },
                        )
                    }
                }
            }

            if (meta.directors.isNotEmpty()) {
                Spacer(modifier = Modifier.height(NexusTheme.spacing.sm))
                val label =
                    if (meta.directors.size > 1) {
                        stringResource(R.string.detail_directors, meta.directors.joinToString(", "))
                    } else {
                        stringResource(R.string.detail_director, meta.directors.first())
                    }
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (meta.cast.isNotEmpty()) {
                Spacer(modifier = Modifier.height(NexusTheme.spacing.sm))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(NexusTheme.spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(NexusTheme.spacing.xs),
                ) {
                    meta.cast.forEach { actor ->
                        AssistChip(
                            onClick = {},
                            label = { Text(actor, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BackdropSection(
    meta: MetaDetail,
    modifier: Modifier = Modifier,
) {
    val backdropUrl = meta.background ?: meta.poster
    val gradientColor = MaterialTheme.colorScheme.background

    Box(
        modifier =
            modifier
                .aspectRatio(ASPECT_RATIO_16_9)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        if (!backdropUrl.isNullOrBlank()) {
            AsyncImage(
                model =
                    ImageRequest
                        .Builder(LocalPlatformContext.current)
                        .data(backdropUrl)
                        .crossfade(true)
                        .build(),
                contentDescription = meta.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = meta.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.0f to Color.Transparent,
                            GRADIENT_STOP_THRESHOLD to Color.Transparent,
                            1.0f to gradientColor,
                        ),
                    ),
        )
    }
}

@Composable
private fun TitleAndInfo(
    meta: MetaDetail,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (!meta.logo.isNullOrBlank()) {
            AsyncImage(
                model =
                    ImageRequest
                        .Builder(LocalPlatformContext.current)
                        .data(meta.logo)
                        .crossfade(true)
                        .build(),
                contentDescription = meta.name,
                contentScale = ContentScale.Fit,
                modifier =
                    Modifier
                        .fillMaxWidth(LOGO_WIDTH_FRACTION)
                        .heightIn(max = 64.dp),
                alignment = Alignment.CenterStart,
            )
            Spacer(modifier = Modifier.height(NexusTheme.spacing.xs))
        } else {
            Text(
                text = meta.name,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(NexusTheme.spacing.xs))
        }

        val infoParts =
            listOfNotNull(
                meta.releaseInfo?.takeIf { it.isNotBlank() },
                meta.runtime?.takeIf { it.isNotBlank() },
                meta.imdbRating?.takeIf { it.isNotBlank() }?.let { "★ $it" },
                meta.genres
                    .take(3)
                    .takeIf { it.isNotEmpty() }
                    ?.joinToString(", "),
            )
        if (infoParts.isNotEmpty()) {
            Text(
                text = infoParts.joinToString(" • "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActionButtonsRow(
    meta: MetaDetail,
    inWatchlist: Boolean,
    trailerUrl: String?,
    playActionTitle: String,
    isPlayEnabled: Boolean,
    onPlayMain: () -> Unit,
    onToggleWatchlist: () -> Unit,
    onOpenTrailer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(NexusTheme.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(NexusTheme.spacing.sm),
    ) {
        Button(
            onClick = onPlayMain,
            enabled = isPlayEnabled,
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize),
            )
            Spacer(modifier = Modifier.width(NexusTheme.spacing.xs))
            Text(text = playActionTitle)
        }

        val watchlistDesc =
            if (inWatchlist) {
                stringResource(R.string.detail_watchlist_remove_desc, meta.name)
            } else {
                stringResource(R.string.detail_watchlist_add_desc, meta.name)
            }

        if (inWatchlist) {
            FilledTonalButton(
                onClick = onToggleWatchlist,
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = watchlistDesc,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(modifier = Modifier.width(NexusTheme.spacing.xs))
                Text(text = stringResource(R.string.detail_watchlist_in))
            }
        } else {
            OutlinedButton(
                onClick = onToggleWatchlist,
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = watchlistDesc,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(modifier = Modifier.width(NexusTheme.spacing.xs))
                Text(text = stringResource(R.string.detail_watchlist_add))
            }
        }

        if (trailerUrl != null) {
            OutlinedButton(
                onClick = onOpenTrailer,
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(modifier = Modifier.width(NexusTheme.spacing.xs))
                Text(text = stringResource(R.string.detail_trailer))
            }
        }
    }
}

@Composable
private fun SynopsisSection(
    description: String,
    isExpanded: Boolean,
    onToggleSynopsis: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var canOverflow by remember { mutableStateOf(false) }
    var hasMeasured by remember { mutableStateOf(false) }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .animateContentSize(),
    ) {
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = if (isExpanded) Int.MAX_VALUE else 4,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { result ->
                if (!hasMeasured) {
                    canOverflow = result.hasVisualOverflow || result.lineCount > 4
                    hasMeasured = true
                }
            },
        )

        if (canOverflow) {
            Text(
                text =
                    stringResource(
                        if (isExpanded) R.string.detail_read_less else R.string.detail_read_more,
                    ),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier =
                    Modifier
                        .clickable(onClick = onToggleSynopsis)
                        .padding(vertical = NexusTheme.spacing.xs),
            )
        }
    }
}
