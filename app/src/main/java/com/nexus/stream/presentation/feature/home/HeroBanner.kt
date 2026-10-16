package com.nexus.stream.presentation.feature.home

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.nexus.stream.R
import com.nexus.stream.domain.model.MetaPreview
import com.nexus.stream.domain.model.PosterShape
import com.nexus.stream.presentation.theme.NexusTheme
import kotlinx.coroutines.delay

const val HERO_ASPECT: Float = 0.8f
private const val AUTO_ADVANCE_DELAY_MS: Long = 6_000L

fun nextHeroPage(
    current: Int,
    size: Int,
): Int {
    if (size <= 1) return current
    return (current + 1) % size
}

private fun isAnimationScaleZero(context: Context): Boolean =
    try {
        val scale =
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1.0f,
            )
        scale == 0f
    } catch (_: Exception) {
        false
    }

internal fun formatHeroInfo(
    releaseInfo: String?,
    imdbRating: String?,
    genres: List<String>,
): String {
    val parts = mutableListOf<String>()
    if (!releaseInfo.isNullOrBlank()) {
        parts.add(releaseInfo.trim())
    }
    if (!imdbRating.isNullOrBlank()) {
        parts.add("★ ${imdbRating.trim()}")
    }
    val genrePart = genres.filter { it.isNotBlank() }.take(3).joinToString(", ")
    if (genrePart.isNotBlank()) {
        parts.add(genrePart)
    }
    return parts.joinToString(" • ")
}

@Composable
fun HeroSkeleton(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .aspectRatio(HERO_ASPECT)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    )
}

@Composable
fun HeroBanner(
    items: List<MetaPreview>,
    inWatchlist: (String) -> Boolean,
    onDetails: (type: String, id: String) -> Unit,
    onToggleWatchlist: (MetaPreview) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return

    val pagerState = rememberPagerState(initialPage = 0) { items.size }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isAnimZero = remember(context) { isAnimationScaleZero(context) }

    LaunchedEffect(items.size) {
        if (items.isNotEmpty() && pagerState.currentPage >= items.size) {
            pagerState.scrollToPage(items.size - 1)
        }
    }

    if (!isAnimZero && items.size > 1) {
        LaunchedEffect(pagerState.currentPage, pagerState.isScrollInProgress, items.size) {
            if (pagerState.isScrollInProgress) return@LaunchedEffect
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                delay(AUTO_ADVANCE_DELAY_MS)
                if (!pagerState.isScrollInProgress) {
                    val nextPage = nextHeroPage(pagerState.currentPage, items.size)
                    pagerState.animateScrollToPage(nextPage)
                }
            }
        }
    }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .aspectRatio(HERO_ASPECT),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { pageIndex ->
            val item = items[pageIndex]
            HeroPage(
                item = item,
                inWatchlist = inWatchlist(item.id),
                onDetails = { onDetails(item.type, item.id) },
                onToggleWatchlist = { onToggleWatchlist(item) },
            )
        }

        if (items.size > 1) {
            Row(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = NexusTheme.spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(NexusTheme.spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(items.size) { index ->
                    val isSelected = pagerState.currentPage == index
                    val dotSize = if (isSelected) 8.dp else 6.dp
                    val dotColor =
                        if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        }
                    Box(
                        modifier =
                            Modifier
                                .size(dotSize)
                                .clip(CircleShape)
                                .background(dotColor),
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroPage(
    item: MetaPreview,
    inWatchlist: Boolean,
    onDetails: () -> Unit,
    onToggleWatchlist: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val platformContext = LocalPlatformContext.current
    var isBackdropError by remember(item.background) { mutableStateOf(false) }
    var isLogoError by remember(item.logo) { mutableStateOf(false) }

    val backgroundColor = MaterialTheme.colorScheme.background
    val scrimBrush =
        remember(backgroundColor) {
            Brush.verticalGradient(
                0.0f to Color.Transparent,
                0.4f to Color.Transparent,
                1.0f to backgroundColor,
            )
        }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .clickable(onClick = onDetails),
    ) {
        if (!item.background.isNullOrBlank() && !isBackdropError) {
            AsyncImage(
                model =
                    ImageRequest
                        .Builder(platformContext)
                        .data(item.background)
                        .crossfade(true)
                        .build(),
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onError = { isBackdropError = true },
            )
        } else {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(NexusTheme.spacing.lg),
                )
            }
        }

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(scrimBrush),
        )

        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = NexusTheme.spacing.md, vertical = NexusTheme.spacing.lg)
                    .padding(bottom = NexusTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(NexusTheme.spacing.xs),
        ) {
            if (!item.logo.isNullOrBlank() && !isLogoError) {
                AsyncImage(
                    model =
                        ImageRequest
                            .Builder(platformContext)
                            .data(item.logo)
                            .crossfade(true)
                            .build(),
                    contentDescription = item.name,
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.BottomStart,
                    modifier =
                        Modifier
                            .heightIn(max = 56.dp)
                            .widthIn(max = 240.dp),
                    onError = { isLogoError = true },
                )
            } else {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            val infoLine = formatHeroInfo(item.releaseInfo, item.imdbRating, item.genres)
            if (infoLine.isNotEmpty()) {
                Text(
                    text = infoLine,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (!item.description.isNullOrBlank()) {
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.height(NexusTheme.spacing.xxs))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(NexusTheme.spacing.sm),
                verticalArrangement = Arrangement.spacedBy(NexusTheme.spacing.xs),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Button(
                    onClick = onDetails,
                ) {
                    Text(
                        text = stringResource(R.string.hero_btn_details),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                val watchlistLabel =
                    if (inWatchlist) {
                        stringResource(R.string.hero_btn_in_watchlist)
                    } else {
                        stringResource(R.string.hero_btn_add_watchlist)
                    }
                val watchlistDesc =
                    if (inWatchlist) {
                        stringResource(R.string.hero_watchlist_remove_desc, item.name)
                    } else {
                        stringResource(R.string.hero_watchlist_add_desc, item.name)
                    }

                OutlinedButton(
                    onClick = onToggleWatchlist,
                    modifier = Modifier.semantics { contentDescription = watchlistDesc },
                ) {
                    if (inWatchlist) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(NexusTheme.spacing.xs))
                    }
                    Text(
                        text = watchlistLabel,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Preview(name = "HeroSkeleton - Light")
@Composable
internal fun HeroSkeletonPreview() {
    NexusTheme {
        HeroSkeleton()
    }
}

@Preview(name = "HeroBanner - Light")
@Composable
internal fun HeroBannerPreview() {
    NexusTheme {
        HeroBanner(
            items =
                listOf(
                    MetaPreview(
                        id = "1",
                        type = "movie",
                        name = "Dune: Part Two",
                        poster = null,
                        posterShape = PosterShape.POSTER,
                        background = null,
                        logo = null,
                        description = "Paul Atreides unites with Chani and the Fremen while seeking revenge.",
                        releaseInfo = "2024",
                        imdbRating = "8.6",
                        genres = listOf("Action", "Adventure", "Sci-Fi"),
                    ),
                ),
            inWatchlist = { false },
            onDetails = { _, _ -> },
            onToggleWatchlist = {},
        )
    }
}
