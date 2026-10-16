package com.nexus.stream.presentation.feature.detail

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexus.stream.R
import com.nexus.stream.domain.model.AppError
import com.nexus.stream.domain.model.MetaDetail
import com.nexus.stream.domain.model.PosterShape
import com.nexus.stream.domain.repository.MetaSource
import com.nexus.stream.presentation.common.NexusRefreshBox
import com.nexus.stream.presentation.common.toUserMessage
import com.nexus.stream.presentation.theme.NexusTheme

private const val ASPECT_RATIO_16_9: Float = 16f / 9f
private const val SKELETON_LINE_COUNT: Int = 4

@Composable
fun DetailScreen(
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenAddons: () -> Unit = {},
    viewModel: DetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val trailerNotFoundMessage = stringResource(R.string.detail_trailer_app_not_found)

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is DetailEffect.OpenUrl -> {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(effect.url))
                        context.startActivity(intent)
                    } catch (_: ActivityNotFoundException) {
                        snackbarHostState.showSnackbar(trailerNotFoundMessage)
                    }
                }

                is DetailEffect.ShowMessage -> {
                    snackbarHostState.showSnackbar(effect.message)
                }
            }
        }
    }

    DetailScreenContent(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        onNavigateUp = onNavigateUp,
        onOpenAddons = onOpenAddons,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreenContent(
    uiState: DetailUiState,
    onEvent: (DetailEvent) -> Unit,
    onNavigateUp: () -> Unit,
    onOpenAddons: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(modifier = modifier.fillMaxSize()) {
        when (val state = uiState) {
            is DetailUiState.Loading -> {
                DetailSkeleton(modifier = Modifier.fillMaxSize())
            }

            is DetailUiState.Error -> {
                DetailErrorContent(
                    error = state.error,
                    onRetry = { onEvent(DetailEvent.Retry) },
                    onOpenAddons = onOpenAddons,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            is DetailUiState.Content -> {
                NexusRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh = { onEvent(DetailEvent.Refresh) },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding =
                            PaddingValues(
                                bottom = navBarBottom + NexusTheme.spacing.lg,
                            ),
                    ) {
                        item(key = "header") {
                            DetailHeader(
                                meta = state.meta,
                                inWatchlist = state.inWatchlist,
                                isSynopsisExpanded = state.isSynopsisExpanded,
                                trailerUrl = state.trailerUrl,
                                playActionTitle = state.playActionTitle,
                                isPlayEnabled = state.isPlayEnabled,
                                onPlayMain = { onEvent(DetailEvent.PlayMain) },
                                onToggleWatchlist = { onEvent(DetailEvent.ToggleWatchlist) },
                                onOpenTrailer = { onEvent(DetailEvent.OpenTrailer) },
                                onToggleSynopsis = { onEvent(DetailEvent.ToggleSynopsis) },
                            )
                        }

                        if (state.source == MetaSource.STALE) {
                            item(key = "stale_banner") {
                                StaleBanner(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(
                                                horizontal = NexusTheme.spacing.lg,
                                                vertical = NexusTheme.spacing.xs,
                                            ),
                                )
                            }
                        }

                        if (state.meta.type == "series") {
                            item(key = "series_content") {
                                SeriesEpisodesSection(
                                    content = state,
                                    onSelectSeason = { onEvent(DetailEvent.SelectSeason(it)) },
                                    onPlayEpisode = { onEvent(DetailEvent.PlayEpisode(it)) },
                                    onToggleWatched = { onEvent(DetailEvent.ToggleWatched(it)) },
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(top = NexusTheme.spacing.md),
                                )
                            }
                        }
                    }
                }

                if (state.showStreamsSheet && state.streamsSheetVideoId != null) {
                    StreamsSheetPlaceholder(
                        title = state.streamsSheetTitle ?: state.meta.name,
                        videoId = state.streamsSheetVideoId,
                        onDismissRequest = { onEvent(DetailEvent.DismissStreamsSheet) },
                    )
                }
            }
        }

        // Overlay back button with status-bar inset applied to the button only
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.6f),
            modifier =
                Modifier
                    .statusBarsPadding()
                    .padding(start = NexusTheme.spacing.sm, top = NexusTheme.spacing.xs),
        ) {
            IconButton(onClick = onNavigateUp) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.navigate_up),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = navBarBottom + NexusTheme.spacing.md),
        )
    }
}

@Composable
private fun StaleBanner(modifier: Modifier = Modifier) {
    Card(
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(NexusTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(NexusTheme.spacing.xs))
            Text(
                text = stringResource(R.string.detail_stale_banner),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DetailErrorContent(
    error: AppError,
    onRetry: () -> Unit,
    onOpenAddons: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .padding(NexusTheme.spacing.xl),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(NexusTheme.spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (error is AppError.NotFound) {
                    Text(
                        text = stringResource(R.string.detail_no_addons_meta),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(NexusTheme.spacing.md))
                    Button(onClick = onOpenAddons) {
                        Text(stringResource(R.string.detail_btn_addons))
                    }
                } else {
                    val message = error.toUserMessage()
                    val errorText = formatErrorMessage(LocalContext.current, message.resId, message.formatArgs)
                    Text(
                        text = errorText,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(NexusTheme.spacing.md))
                    Button(onClick = onRetry) {
                        Text(stringResource(R.string.retry))
                    }
                }
            }
        }
    }
}

private fun formatErrorMessage(
    context: android.content.Context,
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

@Composable
private fun DetailSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(ASPECT_RATIO_16_9)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        )
        Spacer(modifier = Modifier.height(NexusTheme.spacing.md))
        Column(
            modifier = Modifier.padding(horizontal = NexusTheme.spacing.lg),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(width = 220.dp, height = 28.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = MaterialTheme.shapes.small,
                        ),
            )
            Spacer(modifier = Modifier.height(NexusTheme.spacing.xs))
            Box(
                modifier =
                    Modifier
                        .size(width = 160.dp, height = 16.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = MaterialTheme.shapes.small,
                        ),
            )
            Spacer(modifier = Modifier.height(NexusTheme.spacing.md))
            Row(horizontalArrangement = Arrangement.spacedBy(NexusTheme.spacing.sm)) {
                Box(
                    modifier =
                        Modifier
                            .size(width = 100.dp, height = 40.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceContainerHigh,
                                shape = MaterialTheme.shapes.small,
                            ),
                )
                Box(
                    modifier =
                        Modifier
                            .size(width = 120.dp, height = 40.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceContainerHigh,
                                shape = MaterialTheme.shapes.small,
                            ),
                )
            }
            Spacer(modifier = Modifier.height(NexusTheme.spacing.md))
            repeat(SKELETON_LINE_COUNT) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(14.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceContainerHigh,
                                shape = MaterialTheme.shapes.extraSmall,
                            ),
                )
                Spacer(modifier = Modifier.height(NexusTheme.spacing.xs))
            }
        }
    }
}

@Composable
internal fun SeriesEpisodesSection(
    content: DetailUiState.Content,
    onSelectSeason: (Int?) -> Unit,
    onPlayEpisode: (String) -> Unit,
    onToggleWatched: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    EpisodeList(
        meta = content.meta,
        seasons = content.seasons,
        selectedSeason = content.selectedSeason,
        episodeProgress = content.episodeProgress,
        onSelectSeason = onSelectSeason,
        onPlayEpisode = onPlayEpisode,
        onToggleWatched = onToggleWatched,
        modifier = modifier,
    )
}

@Preview(name = "DetailScreen - Dark")
@Composable
internal fun DetailScreenDarkPreview() {
    NexusTheme {
        DetailScreenContent(
            uiState =
                DetailUiState.Content(
                    meta =
                        MetaDetail(
                            id = "tt0111161",
                            type = "movie",
                            name = "The Shawshank Redemption",
                            poster = null,
                            posterShape = PosterShape.POSTER,
                            background = null,
                            logo = null,
                            description = "Over the course of several years, two convicts form a friendship.",
                            releaseInfo = "1994",
                            released = "1994-10-14",
                            runtime = "142 min",
                            imdbRating = "9.3",
                            genres = listOf("Drama", "Crime"),
                            cast = listOf("Tim Robbins", "Morgan Freeman"),
                            directors = listOf("Frank Darabont"),
                            country = null,
                            language = null,
                            website = null,
                            links = emptyList(),
                            trailers = emptyList(),
                            videos = emptyList(),
                            defaultVideoId = null,
                        ),
                    source = MetaSource.FRESH,
                ),
            onEvent = {},
            onNavigateUp = {},
            onOpenAddons = {},
            snackbarHostState = remember { SnackbarHostState() },
        )
    }
}

@Preview(name = "DetailScreen - Light")
@Composable
internal fun DetailScreenLightPreview() {
    NexusTheme {
        DetailScreenContent(
            uiState =
                DetailUiState.Content(
                    meta =
                        MetaDetail(
                            id = "tt0903747",
                            type = "series",
                            name = "Breaking Bad",
                            poster = null,
                            posterShape = PosterShape.POSTER,
                            background = null,
                            logo = null,
                            description = "A chemistry teacher diagnosed with cancer turns to manufacturing meth.",
                            releaseInfo = "2008-2013",
                            released = "2008-01-20",
                            runtime = "49 min",
                            imdbRating = "9.5",
                            genres = listOf("Crime", "Drama", "Thriller"),
                            cast = listOf("Bryan Cranston", "Aaron Paul"),
                            directors = listOf("Vince Gilligan"),
                            country = null,
                            language = null,
                            website = null,
                            links = emptyList(),
                            trailers = emptyList(),
                            videos = emptyList(),
                            defaultVideoId = null,
                        ),
                    source = MetaSource.STALE,
                ),
            onEvent = {},
            onNavigateUp = {},
            onOpenAddons = {},
            snackbarHostState = remember { SnackbarHostState() },
        )
    }
}
