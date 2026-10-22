package com.nexus.stream.presentation.feature.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.nexus.stream.R
import com.nexus.stream.presentation.theme.NexusTheme
import com.nexus.stream.presentation.theme.tokens.PlayerOverlayColors
import kotlinx.coroutines.launch

private const val RIPPLE_DURATION_MS = 450
private const val CHEVRON_DURATION_MS = 600f
private const val CHEVRON_STAGGER_MS = 120f
private const val TOTAL_CHEVRON_ANIM_MS = 840
private const val RIPPLE_MAX_ALPHA = 0.30f
private const val HALF_PROGRESS = 0.5f

@Composable
fun BoxScope.SeekFeedback(
    state: SeekChipState,
    zone: GestureZone,
    tapPoint: Offset,
    triggerCount: Int,
    modifier: Modifier = Modifier,
) {
    var lastNonNullLabel by remember { mutableStateOf<String?>(null) }
    val currentLabel = state.labelOrNull()
    if (currentLabel != null) {
        lastNonNullLabel = currentLabel
    }

    val rippleProgress = remember { Animatable(0f) }
    val chevronElapsed = remember { Animatable(TOTAL_CHEVRON_ANIM_MS.toFloat()) }

    LaunchedEffect(triggerCount) {
        if (triggerCount > 0) {
            launch {
                rippleProgress.snapTo(0f)
                rippleProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = RIPPLE_DURATION_MS, easing = FastOutSlowInEasing),
                )
            }
            launch {
                chevronElapsed.snapTo(0f)
                chevronElapsed.animateTo(
                    targetValue = TOTAL_CHEVRON_ANIM_MS.toFloat(),
                    animationSpec = tween(durationMillis = TOTAL_CHEVRON_ANIM_MS, easing = LinearEasing),
                )
            }
        }
    }

    val alignment = if (zone == GestureZone.LEFT) Alignment.CenterStart else Alignment.CenterEnd

    Box(
        modifier =
            modifier
                .fillMaxHeight()
                .fillMaxWidth(HALF_PROGRESS)
                .align(alignment)
                .clipToBounds(),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (rippleProgress.value < 1f && triggerCount > 0) {
                val halfWidth = size.width
                val radius = halfWidth * rippleProgress.value
                val alpha = (RIPPLE_MAX_ALPHA * (1f - rippleProgress.value)).coerceIn(0f, 1f)
                val localCenter =
                    Offset(
                        x = if (zone == GestureZone.LEFT) tapPoint.x else (tapPoint.x - halfWidth),
                        y = tapPoint.y,
                    )
                drawCircle(
                    color = PlayerOverlayColors.Content.copy(alpha = alpha),
                    radius = radius,
                    center = localCenter,
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            AnimatedVisibility(
                visible = state.visible,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(NexusTheme.spacing.xs),
                ) {
                    val elapsed = chevronElapsed.value
                    val c0Alpha = chevronAlpha(elapsed, 0)
                    val c1Alpha = chevronAlpha(elapsed, 1)
                    val c2Alpha = chevronAlpha(elapsed, 2)

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(NexusTheme.spacing.xxs),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier =
                            Modifier.graphicsLayer {
                                scaleX = if (zone == GestureZone.LEFT) -1f else 1f
                            },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_seek_chevron),
                            contentDescription = null,
                            tint = PlayerOverlayColors.Content.copy(alpha = c0Alpha),
                            modifier = Modifier.size(24.dp),
                        )
                        Icon(
                            painter = painterResource(R.drawable.ic_seek_chevron),
                            contentDescription = null,
                            tint = PlayerOverlayColors.Content.copy(alpha = c1Alpha),
                            modifier = Modifier.size(24.dp),
                        )
                        Icon(
                            painter = painterResource(R.drawable.ic_seek_chevron),
                            contentDescription = null,
                            tint = PlayerOverlayColors.Content.copy(alpha = c2Alpha),
                            modifier = Modifier.size(24.dp),
                        )
                    }

                    Text(
                        text = lastNonNullLabel.orEmpty(),
                        style = MaterialTheme.typography.titleMedium,
                        color = PlayerOverlayColors.Content,
                    )
                }
            }
        }
    }
}

private fun chevronAlpha(
    totalElapsedMs: Float,
    index: Int,
): Float {
    val startMs = index * CHEVRON_STAGGER_MS
    val elapsed = totalElapsedMs - startMs
    if (elapsed !in 0f..CHEVRON_DURATION_MS) return 0f
    val progress = elapsed / CHEVRON_DURATION_MS
    return if (progress <= HALF_PROGRESS) {
        progress * 2f
    } else {
        (1f - progress) * 2f
    }
}
