package com.nexus.stream.presentation.feature.detail

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.nexus.stream.R
import com.nexus.stream.domain.model.MetaDetail
import com.nexus.stream.presentation.common.NexusButton
import com.nexus.stream.presentation.common.NexusOutlinedButton
import com.nexus.stream.presentation.theme.NexusTheme

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
    onOpenWatchOptions: () -> Unit,
    onOpenTrailer: () -> Unit,
    onDownload: () -> Unit,
    onToggleSynopsis: () -> Unit,
    onOpenAllGenres: () -> Unit,
    onOpenAllCast: () -> Unit,
    onOpenAllDirectors: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val configuration = LocalConfiguration.current
    val headerHeight = headerHeightDp(configuration.screenHeightDp.toFloat()).dp

    Column(modifier = modifier.fillMaxWidth()) {
        BackdropSection(
            meta = meta,
            modifier = Modifier.fillMaxWidth().height(headerHeight),
        )

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = NexusTheme.spacing.lg),
        ) {
            Spacer(modifier = Modifier.height(NexusTheme.spacing.sm))

            InfoLine(meta = meta)

            Spacer(modifier = Modifier.height(NexusTheme.spacing.md))

            NexusButton(
                onClick = onPlayMain,
                enabled = isPlayEnabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(modifier = Modifier.width(NexusTheme.spacing.xs))
                Text(text = playActionTitle)
            }

            Spacer(modifier = Modifier.height(NexusTheme.spacing.sm))

            ActionButtonsRow(
                meta = meta,
                inWatchlist = inWatchlist,
                trailerUrl = trailerUrl,
                onOpenWatchOptions = onOpenWatchOptions,
                onOpenTrailer = onOpenTrailer,
                onDownload = onDownload,
            )

            if (!meta.description.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(NexusTheme.spacing.md))
                SynopsisSection(
                    description = meta.description,
                    isExpanded = isSynopsisExpanded,
                    onToggleSynopsis = onToggleSynopsis,
                )
            }
        }

        Spacer(modifier = Modifier.height(NexusTheme.spacing.md))
        ChipRowSection(
            title = stringResource(R.string.detail_genres),
            items = meta.genres,
            onAll = onOpenAllGenres,
        )

        Spacer(modifier = Modifier.height(NexusTheme.spacing.sm))
        ChipRowSection(
            title = stringResource(R.string.detail_cast),
            items = meta.cast,
            onAll = onOpenAllCast,
        )

        Spacer(modifier = Modifier.height(NexusTheme.spacing.sm))
        ChipRowSection(
            title = stringResource(R.string.detail_directors_heading),
            items = meta.directors,
            onAll = onOpenAllDirectors,
        )
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
                alignment = Alignment.TopCenter,
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

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = NexusTheme.spacing.lg, vertical = NexusTheme.spacing.sm),
            contentAlignment = Alignment.BottomStart,
        ) {
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
                    alignment = Alignment.BottomStart,
                )
            } else {
                Text(
                    text = meta.name,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun InfoLine(
    meta: MetaDetail,
    modifier: Modifier = Modifier,
) {
    val infoParts =
        listOfNotNull(
            meta.releaseInfo?.takeIf { it.isNotBlank() },
            meta.runtime?.takeIf { it.isNotBlank() },
            meta.imdbRating?.takeIf { it.isNotBlank() }?.let { "★ $it" },
        )
    if (infoParts.isNotEmpty()) {
        Text(
            text = infoParts.joinToString(" • "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActionButtonsRow(
    meta: MetaDetail,
    inWatchlist: Boolean,
    trailerUrl: String?,
    onOpenWatchOptions: () -> Unit,
    onOpenTrailer: () -> Unit,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(NexusTheme.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(NexusTheme.spacing.sm),
        maxItemsInEachRow = 3,
    ) {
        val watchlistDesc =
            if (inWatchlist) {
                stringResource(R.string.detail_watchlist_remove_desc, meta.name)
            } else {
                stringResource(R.string.detail_watchlist_add_desc, meta.name)
            }

        NexusOutlinedButton(
            onClick = onOpenWatchOptions,
            modifier = Modifier.weight(1f),
        ) {
            Icon(
                imageVector = if (inWatchlist) Icons.Default.Check else Icons.Default.Add,
                contentDescription = watchlistDesc,
                modifier = Modifier.size(ButtonDefaults.IconSize),
            )
            Spacer(modifier = Modifier.width(NexusTheme.spacing.xs))
            Text(
                text = stringResource(R.string.detail_watchlist),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (trailerUrl != null) {
            NexusOutlinedButton(
                onClick = onOpenTrailer,
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(modifier = Modifier.width(NexusTheme.spacing.xs))
                Text(
                    text = stringResource(R.string.detail_trailer),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (meta.type == "movie") {
            NexusOutlinedButton(
                onClick = onDownload,
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_download),
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(modifier = Modifier.width(NexusTheme.spacing.xs))
                Text(
                    text = stringResource(R.string.detail_download),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
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
