package com.nexus.stream.presentation.feature.downloads

import android.content.Context
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.nexus.stream.R
import com.nexus.stream.domain.model.DownloadItem
import com.nexus.stream.domain.model.DownloadLive
import com.nexus.stream.domain.model.DownloadState
import com.nexus.stream.domain.model.EpisodeLabel
import com.nexus.stream.domain.usecase.RowAction
import com.nexus.stream.domain.usecase.StatusText
import com.nexus.stream.domain.usecase.StorageSummary
import com.nexus.stream.domain.usecase.episodeLabelOf
import com.nexus.stream.domain.usecase.primaryAction
import com.nexus.stream.domain.usecase.progressFraction
import com.nexus.stream.domain.usecase.rowStatus
import com.nexus.stream.domain.usecase.rowTitle
import com.nexus.stream.presentation.common.NexusButton
import com.nexus.stream.presentation.common.NexusCard
import com.nexus.stream.presentation.common.NexusIconButton
import com.nexus.stream.presentation.common.NexusOutlinedButton
import com.nexus.stream.presentation.common.NexusTextButton
import com.nexus.stream.presentation.theme.NexusTheme
import java.time.Instant

private const val POSTER_WIDTH_DP = 56
private const val POSTER_HEIGHT_DP = 84
private const val SKELETON_ROWS = 3

@Composable
fun formatStatusText(status: StatusText): String {
    val context = LocalContext.current
    val formattedArgs =
        status.args.map { arg ->
            when (arg) {
                is Long -> {
                    Formatter.formatShortFileSize(context, arg)
                }

                is Instant -> {
                    DateUtils
                        .getRelativeTimeSpanString(
                            arg.toEpochMilli(),
                            System.currentTimeMillis(),
                            DateUtils.DAY_IN_MILLIS,
                        ).toString()
                }

                else -> {
                    arg
                }
            }
        }
    return when (formattedArgs.size) {
        0 -> stringResource(status.stringRes)
        1 -> stringResource(status.stringRes, formattedArgs[0])
        2 -> stringResource(status.stringRes, formattedArgs[0], formattedArgs[1])
        else -> stringResource(status.stringRes, formattedArgs[0], formattedArgs[1], formattedArgs[2])
    }
}

@Composable
fun StorageSummaryCard(
    summary: StorageSummary,
    canPauseAll: Boolean,
    canResumeAll: Boolean,
    canDeleteCompleted: Boolean,
    onPauseAll: () -> Unit,
    onResumeAll: () -> Unit,
    onDeleteAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val spacing = NexusTheme.spacing
    val usedStr = Formatter.formatShortFileSize(context, summary.usedBytes)
    val freeStr = Formatter.formatShortFileSize(context, summary.freeBytes)

    NexusCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Text(
                text = stringResource(R.string.downloads_storage_summary, usedStr, freeStr),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            if (summary.low) {
                Text(
                    text = stringResource(R.string.downloads_storage_low),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                if (canPauseAll) {
                    NexusTextButton(onClick = onPauseAll) {
                        Text(stringResource(R.string.downloads_pause_all))
                    }
                } else if (canResumeAll) {
                    NexusTextButton(onClick = onResumeAll) {
                        Text(stringResource(R.string.downloads_resume_all))
                    }
                }

                if (canDeleteCompleted) {
                    NexusTextButton(onClick = onDeleteAll) {
                        Text(stringResource(R.string.downloads_delete_all))
                    }
                }
            }
        }
    }
}

@Composable
fun WifiBanner(
    onUseMobileData: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = NexusTheme.spacing
    NexusCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.md, vertical = spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.downloads_banner_wifi),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            NexusTextButton(onClick = onUseMobileData) {
                Text(stringResource(R.string.downloads_use_mobile))
            }
        }
    }
}

@Composable
fun NotificationBanner(
    onTurnOn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = NexusTheme.spacing
    NexusCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.md, vertical = spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.downloads_banner_notifications),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            NexusTextButton(onClick = onTurnOn) {
                Text(stringResource(R.string.downloads_turn_on))
            }
        }
    }
}

@Composable
fun DownloadRow(
    item: DownloadItem,
    live: DownloadLive?,
    onTapRow: (DownloadItem) -> Unit,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onRetry: (String) -> Unit,
    onFindNewLink: (DownloadItem) -> Unit,
    onRequestCancel: (String) -> Unit,
    onRequestDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val spacing = NexusTheme.spacing

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable { onTapRow(item) }
                .padding(vertical = spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Poster (56x84)
        Box(
            modifier =
                Modifier
                    .size(POSTER_WIDTH_DP.dp, POSTER_HEIGHT_DP.dp)
                    .clip(NexusTheme.shapes.poster)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            if (!item.posterUrl.isNullOrBlank()) {
                AsyncImage(
                    model =
                        ImageRequest
                            .Builder(LocalPlatformContext.current)
                            .data(item.posterUrl)
                            .crossfade(true)
                            .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.ic_download),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(24.dp),
                )
            }
        }

        Spacer(modifier = Modifier.width(spacing.md))

        // Info and progress
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(spacing.xxs),
        ) {
            Text(
                text = rowTitle(item),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
            )

            // Subtitle: episode label or year + total size
            val parts = mutableListOf<String>()
            if (item.type == "series") {
                episodeLabelOf(item.season, item.episode)?.let { label ->
                    val labelStr =
                        when (label) {
                            is EpisodeLabel.SeasonEpisode -> "S${label.season} E${label.episode}"
                            is EpisodeLabel.EpisodeOnly -> "E${label.episode}"
                            is EpisodeLabel.SeasonOnly -> "S${label.season}"
                        }
                    parts.add(labelStr)
                }
            }
            if (item.totalBytes > 0L) {
                parts.add(Formatter.formatShortFileSize(context, item.totalBytes))
            }
            val subtitle = parts.joinToString(" · ")
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Status line
            val status = rowStatus(item, live)
            Text(
                text = formatStatusText(status),
                style = MaterialTheme.typography.bodySmall,
                color =
                    if (item.state == DownloadState.FAILED) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            // Progress bar
            if (item.state == DownloadState.DOWNLOADING || item.state == DownloadState.PAUSED) {
                if (item.totalBytes > 0L) {
                    val frac = progressFraction(item.downloadedBytes, item.totalBytes) ?: 0f
                    LinearProgressIndicator(
                        progress = { frac },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(NexusTheme.shapes.button),
                    )
                } else {
                    LinearProgressIndicator(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(NexusTheme.shapes.button),
                    )
                }
            }
        }

        DownloadRowActions(
            item = item,
            onPause = onPause,
            onResume = onResume,
            onRetry = onRetry,
            onFindNewLink = onFindNewLink,
            onRequestCancel = onRequestCancel,
            onRequestDelete = onRequestDelete,
        )
    }
}

@Composable
private fun DownloadRowActions(
    item: DownloadItem,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onRetry: (String) -> Unit,
    onFindNewLink: (DownloadItem) -> Unit,
    onRequestCancel: (String) -> Unit,
    onRequestDelete: (String) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
    ) {
        when (primaryAction(item)) {
            RowAction.PAUSE -> {
                NexusTextButton(onClick = { onPause(item.id) }) {
                    Text(stringResource(R.string.downloads_action_pause))
                }
            }

            RowAction.RESUME -> {
                NexusTextButton(onClick = { onResume(item.id) }) {
                    Text(stringResource(R.string.downloads_action_resume))
                }
            }

            RowAction.RETRY -> {
                NexusTextButton(onClick = { onRetry(item.id) }) {
                    Text(stringResource(R.string.downloads_action_retry))
                }
            }

            RowAction.FIND_NEW_LINK -> {
                NexusTextButton(onClick = { onFindNewLink(item) }) {
                    Text(stringResource(R.string.downloads_action_new_link))
                }
            }

            RowAction.REMOVE -> {
                NexusTextButton(onClick = { onRequestDelete(item.id) }) {
                    Text(stringResource(R.string.downloads_action_remove))
                }
            }

            RowAction.NONE -> {}
        }

        Box {
            NexusIconButton(onClick = { menuExpanded = true }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                when (item.state) {
                    DownloadState.COMPLETED -> {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.downloads_menu_delete)) },
                            onClick = {
                                menuExpanded = false
                                onRequestDelete(item.id)
                            },
                        )
                    }

                    DownloadState.FAILED -> {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.downloads_action_remove)) },
                            onClick = {
                                menuExpanded = false
                                onRequestDelete(item.id)
                            },
                        )
                    }

                    else -> {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.downloads_menu_cancel)) },
                            onClick = {
                                menuExpanded = false
                                onRequestCancel(item.id)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ConfirmCancelDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.downloads_confirm_cancel_title)) },
        text = { Text(stringResource(R.string.downloads_confirm_cancel_body)) },
        confirmButton = {
            NexusButton(onClick = onConfirm) {
                Text(stringResource(R.string.downloads_confirm_cancel_ok))
            }
        },
        dismissButton = {
            NexusTextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel))
            }
        },
    )
}

@Composable
fun ConfirmDeleteDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.downloads_confirm_delete_title)) },
        text = { Text(stringResource(R.string.downloads_confirm_delete_body)) },
        confirmButton = {
            NexusButton(onClick = onConfirm) {
                Text(stringResource(R.string.downloads_confirm_ok))
            }
        },
        dismissButton = {
            NexusTextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel))
            }
        },
    )
}

@Composable
fun ConfirmDeleteAllDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.downloads_confirm_delete_all_title)) },
        text = { Text(stringResource(R.string.downloads_confirm_delete_all_body)) },
        confirmButton = {
            NexusButton(onClick = onConfirm) {
                Text(stringResource(R.string.downloads_confirm_ok))
            }
        },
        dismissButton = {
            NexusTextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel))
            }
        },
    )
}

@Composable
fun DownloadsEmptyState(
    onNavigateToDiscover: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = NexusTheme.spacing

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_download),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(64.dp),
        )

        Spacer(modifier = Modifier.height(spacing.md))

        Text(
            text = stringResource(R.string.downloads_empty_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(modifier = Modifier.height(spacing.xs))

        Text(
            text = stringResource(R.string.downloads_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(spacing.lg))

        NexusOutlinedButton(onClick = onNavigateToDiscover) {
            Text(stringResource(R.string.downloads_browse))
        }
    }
}

@Composable
fun DownloadsSkeleton(modifier: Modifier = Modifier) {
    val spacing = NexusTheme.spacing

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        repeat(SKELETON_ROWS) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(POSTER_WIDTH_DP.dp, POSTER_HEIGHT_DP.dp)
                            .clip(NexusTheme.shapes.poster)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                )
                Spacer(modifier = Modifier.width(spacing.md))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(width = 160.dp, height = 16.dp)
                                .clip(NexusTheme.shapes.button)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    )
                    Box(
                        modifier =
                            Modifier
                                .size(width = 90.dp, height = 12.dp)
                                .clip(NexusTheme.shapes.button)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    )
                    Box(
                        modifier =
                            Modifier
                                .size(width = 120.dp, height = 12.dp)
                                .clip(NexusTheme.shapes.button)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    )
                }
            }
        }
    }
}
