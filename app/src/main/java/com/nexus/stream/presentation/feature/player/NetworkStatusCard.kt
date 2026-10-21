package com.nexus.stream.presentation.feature.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nexus.stream.R
import com.nexus.stream.domain.model.StreamStats
import com.nexus.stream.domain.model.ThemeMode
import com.nexus.stream.domain.model.ThemeSettings
import com.nexus.stream.presentation.common.NexusIconButton
import com.nexus.stream.presentation.theme.NexusTheme
import com.nexus.stream.presentation.theme.tokens.AmoledTokens

private const val PERCENT_MULTIPLIER = 100
private const val WAIT_MS_THRESHOLD = 1000L
private const val MS_PER_SECOND = 1000L
private val CARD_MAX_WIDTH = 320.dp

@Composable
fun NetworkStatusCard(
    stats: StreamStats?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = NexusTheme.shapes.card,
        colors =
            CardDefaults.cardColors(
                containerColor = AmoledTokens.surfaceContainerHigh.copy(alpha = 0.92f),
            ),
        modifier = modifier.widthIn(max = CARD_MAX_WIDTH),
    ) {
        Column(
            modifier = Modifier.padding(NexusTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(NexusTheme.spacing.xs),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.player_diag_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                NexusIconButton(
                    onClick = onDismiss,
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.btn_close),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(NexusTheme.spacing.xxs))

            if (stats == null) {
                Text(
                    text = stringResource(R.string.player_status_waiting_for_stats),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val speedText = formatFileSize(stats.speedBps) + "/s"
                val aheadText = formatFileSize(stats.aheadBytes)
                val pct =
                    if (stats.fileSizeBytes > 0) {
                        ((stats.downloadedBytes * PERCENT_MULTIPLIER) / stats.fileSizeBytes).toInt()
                    } else {
                        0
                    }
                val downloadedText = "${formatFileSize(stats.downloadedBytes)} ($pct%)"

                StatRow(label = stringResource(R.string.diag_stat_speed_label), value = speedText)
                StatRow(label = stringResource(R.string.diag_stat_ahead_label), value = aheadText)
                StatRow(label = stringResource(R.string.diag_stat_downloaded_label), value = downloadedText)
                StatRow(label = stringResource(R.string.diag_stat_peers_label), value = stats.peers.toString())

                if (stats.waitMs > WAIT_MS_THRESHOLD) {
                    val waitSec = stats.waitMs / MS_PER_SECOND
                    StatRow(
                        label = stringResource(R.string.diag_stat_waiting_label),
                        value = "${waitSec}s",
                        isWarning = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatRow(
    label: String,
    value: String,
    isWarning: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(NexusTheme.spacing.sm))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = if (isWarning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Preview(name = "Network Status Card Light", showBackground = true)
@Composable
internal fun NetworkStatusCardLightPreview() {
    NexusTheme(settings = ThemeSettings(mode = ThemeMode.LIGHT)) {
        NetworkStatusCard(
            stats =
                StreamStats(
                    speedBps = 2_500_000L,
                    peers = 14,
                    aheadBytes = 32_000_000L,
                    downloadedBytes = 150_000_000L,
                    fileSizeBytes = 700_000_000L,
                    waitMs = 2500L,
                ),
            onDismiss = {},
        )
    }
}

@Preview(name = "Network Status Card Dark", showBackground = true)
@Composable
internal fun NetworkStatusCardDarkPreview() {
    NexusTheme(settings = ThemeSettings(mode = ThemeMode.DARK)) {
        NetworkStatusCard(
            stats =
                StreamStats(
                    speedBps = 1_200_000L,
                    peers = 8,
                    aheadBytes = 16_000_000L,
                    downloadedBytes = 80_000_000L,
                    fileSizeBytes = 500_000_000L,
                    waitMs = 0L,
                ),
            onDismiss = {},
        )
    }
}
