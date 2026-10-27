package com.nexus.stream.presentation.feature.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.nexus.stream.R
import com.nexus.stream.domain.usecase.NextUi
import com.nexus.stream.presentation.common.NexusTextButton
import com.nexus.stream.presentation.theme.NexusTheme
import com.nexus.stream.presentation.theme.tokens.PlayerOverlayColors

private const val MAX_COUNTDOWN_SECONDS = 5f
private val OVERLAY_CARD_MIN_WIDTH = 160.dp
private val OVERLAY_SPINNER_SIZE = 16.dp
private val PROGRESS_BAR_HEIGHT = 2.dp

fun formatNextEpisodeLabel(
    season: Int?,
    episode: Int?,
): String? =
    if (season != null && episode != null) {
        "S$season E$episode"
    } else {
        null
    }

fun formatNextEpisodeA11y(
    season: Int?,
    episode: Int?,
): String =
    if (season != null && episode != null) {
        "Next episode, season $season episode $episode"
    } else {
        "Next episode"
    }

@Composable
fun NextEpisodeOverlay(
    nextUi: NextUi,
    nextEpisodeLabel: Pair<Int, Int>?,
    countdownSeconds: Int?,
    onNextTapped: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (nextUi == NextUi.None) return

    val a11yDesc = formatNextEpisodeA11y(nextEpisodeLabel?.first, nextEpisodeLabel?.second)
    val season = nextEpisodeLabel?.first
    val episode = nextEpisodeLabel?.second
    val formattedLabel = formatNextEpisodeLabel(season, episode)

    Card(
        shape = NexusTheme.shapes.card,
        colors =
            CardDefaults.cardColors(
                containerColor = PlayerOverlayColors.Card,
            ),
        modifier =
            modifier
                .widthIn(min = OVERLAY_CARD_MIN_WIDTH)
                .semantics { contentDescription = a11yDesc }
                .clickable(onClick = onNextTapped),
    ) {
        Column(
            modifier =
                Modifier
                    .padding(
                        horizontal = NexusTheme.spacing.md,
                        vertical = NexusTheme.spacing.sm,
                    ),
            verticalArrangement = Arrangement.spacedBy(NexusTheme.spacing.xs),
        ) {
            when (nextUi) {
                NextUi.Searching -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(NexusTheme.spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.player_next_episode),
                            style = MaterialTheme.typography.labelLarge,
                            color = PlayerOverlayColors.Content,
                        )
                        CircularProgressIndicator(
                            modifier = Modifier.size(OVERLAY_SPINNER_SIZE),
                            color = PlayerOverlayColors.Content,
                            strokeWidth = PROGRESS_BAR_HEIGHT,
                        )
                    }
                }

                NextUi.Ready -> {
                    if (countdownSeconds != null) {
                        Text(
                            text = stringResource(R.string.player_next_episode_in, countdownSeconds),
                            style = MaterialTheme.typography.labelLarge,
                            color = PlayerOverlayColors.Content,
                        )
                        LinearProgressIndicator(
                            progress = { (countdownSeconds.toFloat() / MAX_COUNTDOWN_SECONDS).coerceIn(0f, 1f) },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(PROGRESS_BAR_HEIGHT),
                            color = PlayerOverlayColors.Content,
                            trackColor = PlayerOverlayColors.ContentSecondary.copy(alpha = 0.3f),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            NexusTextButton(
                                onClick = onDismiss,
                                contentPadding =
                                    PaddingValues(
                                        horizontal = NexusTheme.spacing.sm,
                                        vertical = 0.dp,
                                    ),
                            ) {
                                Text(
                                    text = stringResource(R.string.player_next_episode_cancel),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = PlayerOverlayColors.ContentSecondary,
                                )
                            }
                        }
                    } else {
                        Text(
                            text = stringResource(R.string.player_next_episode),
                            style = MaterialTheme.typography.labelLarge,
                            color = PlayerOverlayColors.Content,
                        )
                        if (formattedLabel != null) {
                            Text(
                                text = formattedLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = PlayerOverlayColors.ContentSecondary,
                            )
                        }
                    }
                }

                NextUi.ChooseSource -> {
                    Text(
                        text = stringResource(R.string.player_next_episode),
                        style = MaterialTheme.typography.labelLarge,
                        color = PlayerOverlayColors.Content,
                    )
                    Text(
                        text = stringResource(R.string.player_next_choose_source),
                        style = MaterialTheme.typography.bodySmall,
                        color = PlayerOverlayColors.ContentSecondary,
                    )
                }

                NextUi.None -> {}
            }
        }
    }
}
