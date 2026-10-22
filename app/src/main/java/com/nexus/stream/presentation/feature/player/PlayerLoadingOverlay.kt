package com.nexus.stream.presentation.feature.player

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import coil3.compose.AsyncImage
import com.nexus.stream.R
import com.nexus.stream.domain.model.StreamStats
import com.nexus.stream.domain.model.ThemeMode
import com.nexus.stream.domain.model.ThemeSettings
import com.nexus.stream.presentation.theme.NexusTheme
import com.nexus.stream.presentation.theme.tokens.PlayerOverlayColors

private const val PULSE_DURATION_MS = 1200
private const val LOGO_MAX_WIDTH_FRACTION = 0.20f
private const val LOGO_MAX_HEIGHT_FRACTION = 0.25f
private const val BACKGROUND_ALPHA = 0.35f
private const val SEEKING_SCRIM_ALPHA = 0.60f

@Composable
fun PlayerLoadingOverlay(
    title: String,
    logoUrl: String?,
    backgroundUrl: String?,
    phase: PlayerPhase,
    stats: StreamStats?,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = PULSE_DURATION_MS, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "logo_pulse",
    )

    val isPreparing = phase is PlayerPhase.Preparing
    val scrimAlpha = if (isPreparing) 1.0f else SEEKING_SCRIM_ALPHA

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(PlayerOverlayColors.Scrim),
        contentAlignment = Alignment.Center,
    ) {
        if (!backgroundUrl.isNullOrBlank()) {
            AsyncImage(
                model = backgroundUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = BACKGROUND_ALPHA },
            )
        }

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(PlayerOverlayColors.Scrim.copy(alpha = scrimAlpha)),
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(NexusTheme.spacing.lg),
        ) {
            var logoFailed by remember(logoUrl) { mutableStateOf(false) }

            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(LOGO_MAX_WIDTH_FRACTION)
                        .fillMaxHeight(LOGO_MAX_HEIGHT_FRACTION),
                contentAlignment = Alignment.Center,
            ) {
                if (!logoUrl.isNullOrBlank() && !logoFailed) {
                    AsyncImage(
                        model = logoUrl,
                        contentDescription = title,
                        contentScale = ContentScale.Fit,
                        onError = { logoFailed = true },
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer { alpha = pulseAlpha },
                    )
                } else {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = PlayerOverlayColors.Content,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.graphicsLayer { alpha = pulseAlpha },
                    )
                }
            }

            val statusText = buildStatusText(phase, stats)
            if (statusText.isNotEmpty()) {
                Spacer(modifier = Modifier.height(NexusTheme.spacing.md))
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = PlayerOverlayColors.ContentSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun buildStatusText(
    phase: PlayerPhase,
    stats: StreamStats?,
): String =
    when (phase) {
        is PlayerPhase.Preparing -> {
            when (phase.label) {
                PreparingLabel.OPENING -> {
                    stringResource(R.string.player_preparing)
                }

                PreparingLabel.FETCHING_METADATA -> {
                    stringResource(R.string.player_fetching_metadata)
                }

                PreparingLabel.BUFFERING -> {
                    val pct = phase.percent ?: 0
                    if (stats != null && stats.speedBps > 0) {
                        val speedStr = formatFileSize(stats.speedBps) + "/s"
                        stringResource(R.string.player_status_buffering_stats, pct, speedStr, stats.peers)
                    } else if (phase.peers != null && phase.peers > 0) {
                        stringResource(R.string.player_buffering, pct, phase.peers)
                    } else {
                        stringResource(R.string.player_buffering_no_peers, pct)
                    }
                }
            }
        }

        else -> {
            ""
        }
    }

@Preview(name = "Loading Overlay Dark", showBackground = true)
@Composable
internal fun PlayerLoadingOverlayDarkPreview() {
    NexusTheme(settings = ThemeSettings(mode = ThemeMode.DARK)) {
        PlayerLoadingOverlay(
            title = "Big Buck Bunny",
            logoUrl = null,
            backgroundUrl = null,
            phase = PlayerPhase.Preparing(PreparingLabel.BUFFERING, percent = 45, peers = 12),
            stats = null,
        )
    }
}

@Preview(name = "Loading Overlay Light", showBackground = true)
@Composable
internal fun PlayerLoadingOverlayLightPreview() {
    NexusTheme(settings = ThemeSettings(mode = ThemeMode.LIGHT)) {
        PlayerLoadingOverlay(
            title = "Big Buck Bunny",
            logoUrl = null,
            backgroundUrl = null,
            phase = PlayerPhase.Preparing(PreparingLabel.FETCHING_METADATA),
            stats = null,
        )
    }
}
