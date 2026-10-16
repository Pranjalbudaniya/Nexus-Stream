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
import androidx.compose.ui.unit.Dp
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
const val HERO_AUTO_ADVANCE_MS: Long = 10_000L

private const val SCRIM_START_FRACTION: Float = 0.45f
private const val SCRIM_END_FRACTION: Float = 1.0f
private const val SCRIM_ALPHA: Float = 0.75f

private val DOT_SIZE_SELECTED: Dp = 8.dp
private val DOT_SIZE_UNSELECTED: Dp = 6.dp
private val LOGO_MAX_HEIGHT: Dp = 56.dp
private val LOGO_MAX_WIDTH: Dp = 240.dp
private val CHECK_ICON_SIZE: Dp = 18.dp

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
                .padding(horizontal = NexusTheme.spacing.lg)
                .aspectRatio(HERO_ASPECT)
                .clip(NexusTheme.shapes.card)
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
                delay(HERO_AUTO_ADVANCE_MS)
                if (!pagerState.isScrollInProgress) {
                    val nextPage = nextHeroPage(pagerState.currentPage, items.size)
                    pagerState.animateScrollToPage(nextPage)
                }
            }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = NexusTheme.spacing.lg)
                    .aspectRatio(HERO_ASPECT)
                    .clip(NexusTheme.shapes.card),
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
        }

        if (items.size > 1) {
            Spacer(modifier = Modifier.height(NexusTheme.spacing.sm))
            Row(
                horizontalArrangement = Arrangement.spacedBy(NexusTheme.spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(items.size) { index ->
                    val isSelected = pagerState.currentPage == index
                    val dotSize = if (isSelected) DOT_SIZE_SELECTED else DOT_SIZE_UNSELECTED
                    val dotColor =
                        if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
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

    val scrimBrush =
        remember {
            Brush.verticalGradient(
                0.0f to Color.Transparent,
                SCRIM_START_FRACTION to Color.Transparent,
                SCRIM_END_FRACTION to Color.Black.copy(alpha = SCRIM_ALPHA),
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
                    .padding(NexusTheme.spacing.lg),
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
                            .heightIn(max = LOGO_MAX_HEIGHT)
                            .widthIn(max = LOGO_MAX_WIDTH),
                    onError = { isLogoError = true },
                )
            } else {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            val infoLine = formatHeroInfo(item.releaseInfo, item.imdbRating, item.genres)
            if (infoLine.isNotEmpty()) {
                Text(
                    text = infoLine,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (!item.description.isNullOrBlank()) {
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
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
                            modifier = Modifier.size(CHECK_ICON_SIZE),
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
                    MetaPreview(
                        id = "2",
                        type = "movie",
                        name = "Oppenheimer",
                        poster = null,
                        posterShape = PosterShape.POSTER,
                        background = null,
                        logo = null,
                        description = "The story of J. Robert Oppenheimer's role in the Manhattan Project.",
                        releaseInfo = "2023",
                        imdbRating = "8.9",
                        genres = listOf("Biography", "Drama", "History"),
                    ),
                ),
            inWatchlist = { false },
            onDetails = { _, _ -> },
            onToggleWatchlist = {},
        )
    }
}
