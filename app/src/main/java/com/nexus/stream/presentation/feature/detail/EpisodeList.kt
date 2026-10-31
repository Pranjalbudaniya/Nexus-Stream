package com.nexus.stream.presentation.feature.detail

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.nexus.stream.R
import com.nexus.stream.domain.model.EpisodeProgress
import com.nexus.stream.domain.model.MetaDetail
import com.nexus.stream.domain.model.SeasonGroup
import com.nexus.stream.domain.model.Video
import com.nexus.stream.domain.usecase.DetailDownloadState
import com.nexus.stream.domain.usecase.isReleased
import com.nexus.stream.presentation.common.NexusIconButton
import com.nexus.stream.presentation.theme.NexusTheme
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private const val ASPECT_RATIO_16_9: Float = 16f / 9f

@Composable
fun EpisodeList(
    meta: MetaDetail,
    seasons: List<SeasonGroup>,
    selectedSeason: Int?,
    episodeProgress: Map<String, EpisodeProgress>,
    onSelectSeason: (Int?) -> Unit,
    onPlayEpisode: (String) -> Unit,
    onToggleWatched: (String) -> Unit,
    onDownload: (String) -> Unit,
    modifier: Modifier = Modifier,
    now: Long = System.currentTimeMillis(),
    downloadStates: Map<String, DetailDownloadState> = emptyMap(),
) {
    if (meta.videos.isEmpty() || seasons.isEmpty()) {
        Box(
            modifier =
                modifier
                    .fillMaxWidth()
                    .padding(NexusTheme.spacing.lg),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.detail_no_episodes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    Column(modifier = modifier.fillMaxWidth()) {
        SeasonChipsRow(
            seasons = seasons,
            selectedSeason = selectedSeason,
            onSelectSeason = onSelectSeason,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = NexusTheme.spacing.lg),
        )

        Spacer(modifier = Modifier.height(NexusTheme.spacing.sm))

        val activeGroup = seasons.find { it.season == selectedSeason } ?: seasons.first()
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = NexusTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(NexusTheme.spacing.sm),
        ) {
            activeGroup.episodes.forEach { video ->
                val progress = episodeProgress[video.id]
                EpisodeItem(
                    video = video,
                    progress = progress,
                    isReleased = video.isReleased(now),
                    downloadState = downloadStates[video.id] ?: DetailDownloadState.None,
                    onPlay = { onPlayEpisode(video.id) },
                    onToggleWatched = { onToggleWatched(video.id) },
                    onDownload = { onDownload(video.id) },
                )
            }
        }
    }
}

@Composable
private fun SeasonChipsRow(
    seasons: List<SeasonGroup>,
    selectedSeason: Int?,
    onSelectSeason: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(NexusTheme.spacing.xs),
    ) {
        items(seasons, key = { it.season ?: -1 }) { group ->
            val label =
                when (val s = group.season) {
                    null -> stringResource(R.string.detail_season_episodes)
                    0 -> stringResource(R.string.detail_season_specials)
                    else -> stringResource(R.string.detail_season_label, s)
                }
            FilterChip(
                selected = selectedSeason == group.season,
                onClick = { onSelectSeason(group.season) },
                label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                shape = NexusTheme.shapes.chip,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EpisodeItem(
    video: Video,
    progress: EpisodeProgress?,
    isReleased: Boolean,
    onPlay: () -> Unit,
    onToggleWatched: () -> Unit,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier,
    downloadState: DetailDownloadState = DetailDownloadState.None,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val isWatched = progress?.watched == true
    val toggleLabel =
        stringResource(
            if (isWatched) R.string.detail_mark_as_unwatched else R.string.detail_mark_as_watched,
        )

    val itemAlpha = if (isReleased) 1f else 0.5f

    Card(
        shape = NexusTheme.shapes.card,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        modifier =
            modifier
                .fillMaxWidth()
                .alpha(itemAlpha)
                .semantics {
                    customActions =
                        listOf(
                            CustomAccessibilityAction(toggleLabel) {
                                onToggleWatched()
                                true
                            },
                        )
                }.combinedClickable(
                    onClick = onPlay,
                    onLongClick = { menuExpanded = true },
                    onLongClickLabel = toggleLabel,
                ),
    ) {
        Row(
            modifier = Modifier.padding(NexusTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ThumbnailBox(
                video = video,
                isWatched = isWatched,
                progress = progress,
                modifier =
                    Modifier
                        .width(112.dp)
                        .aspectRatio(ASPECT_RATIO_16_9),
            )

            Spacer(modifier = Modifier.width(NexusTheme.spacing.sm))

            Column(
                modifier = Modifier.weight(1f),
            ) {
                val titleText =
                    if (video.episode != null) {
                        stringResource(R.string.detail_episode_title, video.episode, video.title)
                    } else {
                        video.title
                    }
                Text(
                    text = titleText,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                val formattedDate = remember(video.released) { formatReleaseDate(video.released) }
                if (formattedDate != null) {
                    Text(
                        text = formattedDate,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (!video.overview.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(NexusTheme.spacing.xs))
                    Text(
                        text = video.overview,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            NexusIconButton(
                onClick = onDownload,
                enabled = isReleased,
                modifier = Modifier.size(48.dp),
            ) {
                when (downloadState) {
                    DetailDownloadState.None -> {
                        Icon(
                            painter = painterResource(R.drawable.ic_download),
                            contentDescription = stringResource(R.string.detail_download_desc, video.title),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    DetailDownloadState.Queued -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                    }

                    is DetailDownloadState.Downloading -> {
                        val fraction = downloadState.percent?.let { it / 100f }
                        if (fraction != null) {
                            CircularProgressIndicator(
                                progress = { fraction },
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                    }

                    DetailDownloadState.Paused -> {
                        Icon(
                            painter = painterResource(R.drawable.ic_download),
                            contentDescription = stringResource(R.string.detail_download_desc, video.title),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                        )
                    }

                    DetailDownloadState.Failed -> {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = stringResource(R.string.detail_download_desc, video.title),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }

                    DetailDownloadState.Completed -> {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = stringResource(R.string.detail_download_desc, video.title),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            Box {
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(toggleLabel) },
                        onClick = {
                            menuExpanded = false
                            onToggleWatched()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ThumbnailBox(
    video: Video,
    isWatched: Boolean,
    progress: EpisodeProgress?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .clip(NexusTheme.shapes.poster)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        if (!video.thumbnail.isNullOrBlank()) {
            AsyncImage(
                model =
                    ImageRequest
                        .Builder(LocalPlatformContext.current)
                        .data(video.thumbnail)
                        .crossfade(true)
                        .build(),
                contentDescription = video.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
            }
        }

        if (isWatched) {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(NexusTheme.spacing.xs),
                shape = NexusTheme.shapes.chip,
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        if (progress != null && !isWatched) {
            val position = progress.positionMs
            val duration = progress.durationMs
            if (position > 0L && duration > 0L) {
                val fraction = (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .height(3.dp),
                )
            }
        }
    }
}

private fun formatReleaseDate(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    return try {
        val instant =
            try {
                Instant.parse(raw)
            } catch (_: Exception) {
                try {
                    OffsetDateTime.parse(raw).toInstant()
                } catch (_: Exception) {
                    LocalDate.parse(raw).atStartOfDay(ZoneId.of("UTC")).toInstant()
                }
            }
        val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault())
        formatter.format(instant)
    } catch (_: Exception) {
        null
    }
}
