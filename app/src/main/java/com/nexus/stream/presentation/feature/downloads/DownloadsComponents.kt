package com.nexus.stream.presentation.feature.downloads

import android.content.Context
import android.content.res.Configuration
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
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.nexus.stream.R
import com.nexus.stream.domain.model.DownloadFailure
import com.nexus.stream.domain.model.DownloadItem
import com.nexus.stream.domain.model.DownloadKind
import com.nexus.stream.domain.model.DownloadLive
import com.nexus.stream.domain.model.DownloadSource
import com.nexus.stream.domain.model.DownloadState
import com.nexus.stream.domain.model.EpisodeLabel
import com.nexus.stream.domain.model.GalleryFailure
import com.nexus.stream.domain.model.GalleryState
import com.nexus.stream.domain.usecase.ActionIcon
import com.nexus.stream.domain.usecase.RowAction
import com.nexus.stream.domain.usecase.RowMenuItem
import com.nexus.stream.domain.usecase.StatusText
import com.nexus.stream.domain.usecase.StorageCardModel
import com.nexus.stream.domain.usecase.StorageHeaderAction
import com.nexus.stream.domain.usecase.actionDescription
import com.nexus.stream.domain.usecase.actionIcon
import com.nexus.stream.domain.usecase.episodeLabelOf
import com.nexus.stream.domain.usecase.primaryAction
import com.nexus.stream.domain.usecase.progressFraction
import com.nexus.stream.domain.usecase.rowMenu
import com.nexus.stream.domain.usecase.rowStatus
import com.nexus.stream.domain.usecase.rowTitle
import com.nexus.stream.presentation.common.NexusButton
import com.nexus.stream.presentation.common.NexusCard
import com.nexus.stream.presentation.common.NexusIconButton
import com.nexus.stream.presentation.common.NexusOutlinedButton
import com.nexus.stream.presentation.common.NexusTextButton
import com.nexus.stream.presentation.theme.NexusTheme

private const val POSTER_WIDTH_DP = 56
private const val POSTER_HEIGHT_DP = 84
private const val SKELETON_ROWS = 3
private const val ARGS_COUNT_THREE = 3
private const val ARG_INDEX_THREE = 3
private const val PREVIEW_USED_BYTES = 248_000_000L
private const val PREVIEW_FREE_BYTES = 12_000_000_000L
private const val PREVIEW_HIGH_USED_BYTES = 50_000_000_000L
private const val PREVIEW_LOW_FREE_BYTES = 800_000_000L
private const val PREVIEW_EMPTY_FREE_BYTES = 20_000_000_000L

@Composable
fun formatStatusText(status: StatusText): String {
    val context = LocalContext.current
    val formattedArgs =
        status.args.map { arg ->
            when (arg) {
                is Long -> {
                    Formatter.formatShortFileSize(context, arg)
                }

                is java.time.Instant -> {
                    DateUtils
                        .getRelativeTimeSpanString(
                            arg.toEpochMilli(),
                            System.currentTimeMillis(),
                            DateUtils.DAY_IN_MILLIS,
                        ).toString()
                }

                is StatusText -> {
                    formatStatusText(arg)
                }

                is Int -> {
                    if (status.stringRes == R.string.downloads_gallery_failed) {
                        stringResource(arg)
                    } else {
                        arg
                    }
                }

                else -> {
                    arg
                }
            }
        }
    val a = formattedArgs
    return when (a.size) {
        0 -> stringResource(status.stringRes)
        1 -> stringResource(status.stringRes, a[0])
        2 -> stringResource(status.stringRes, a[0], a[1])
        ARGS_COUNT_THREE -> stringResource(status.stringRes, a[0], a[1], a[2])
        else -> stringResource(status.stringRes, a[0], a[1], a[2], a[ARG_INDEX_THREE])
    }
}

@Composable
fun CompactStorageCard(
    card: StorageCardModel,
    onPauseAll: () -> Unit,
    onResumeAll: () -> Unit,
    onDeleteAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val spacing = NexusTheme.spacing
    val usedStr = Formatter.formatShortFileSize(context, card.usedBytes)
    val freeStr = Formatter.formatShortFileSize(context, card.freeBytes)

    NexusCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.md, vertical = spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val text =
                if (card.low) {
                    stringResource(R.string.downloads_storage_almost_full, freeStr)
                } else {
                    stringResource(R.string.downloads_storage_summary, usedStr, freeStr)
                }
            val textColor =
                if (card.low) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )

            when (card.headerAction) {
                StorageHeaderAction.PAUSE_ALL -> {
                    NexusIconButton(onClick = onPauseAll) {
                        Icon(
                            painter = painterResource(R.drawable.ic_pause),
                            contentDescription = stringResource(R.string.downloads_pause_all_desc),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                StorageHeaderAction.RESUME_ALL -> {
                    NexusIconButton(onClick = onResumeAll) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = stringResource(R.string.downloads_resume_all_desc),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                StorageHeaderAction.NONE -> {}
            }

            if (card.showDeleteAll) {
                Box {
                    var menuExpanded by remember { mutableStateOf(false) }
                    NexusIconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.downloads_more_desc),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.downloads_delete_all)) },
                            onClick = {
                                menuExpanded = false
                                onDeleteAll()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DownloadsBanner(
    text: String,
    actionText: String,
    onAction: () -> Unit,
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
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            NexusTextButton(onClick = onAction) {
                Text(actionText)
            }
        }
    }
}

private object DownloadRowContent {
    @Composable
    fun Poster(posterUrl: String?) {
        Box(
            modifier =
                Modifier
                    .size(POSTER_WIDTH_DP.dp, POSTER_HEIGHT_DP.dp)
                    .clip(NexusTheme.shapes.poster)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            if (!posterUrl.isNullOrBlank()) {
                AsyncImage(
                    model =
                        ImageRequest
                            .Builder(LocalPlatformContext.current)
                            .data(posterUrl)
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
    }

    fun subtitle(
        context: Context,
        item: DownloadItem,
    ): String {
        val parts = mutableListOf<String>()
        if (item.type == "series") {
            val label = episodeLabelOf(item.season, item.episode)
            if (label != null) {
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
        return parts.joinToString(" · ")
    }

    @Composable
    fun ProgressBar(item: DownloadItem) {
        if (item.state == DownloadState.DOWNLOADING || item.state == DownloadState.PAUSED) {
            val frac =
                if (item.totalBytes > 0L) {
                    progressFraction(item.downloadedBytes, item.totalBytes) ?: 0f
                } else {
                    0f
                }
            LinearProgressIndicator(
                progress = { frac },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(NexusTheme.shapes.button),
            )
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
    onChooseFile: (String) -> Unit = {},
    onSaveToGallery: (String) -> Unit = {},
    onRemoveFromGallery: (String) -> Unit = {},
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
        DownloadRowContent.Poster(item.posterUrl)

        Spacer(modifier = Modifier.width(spacing.md))

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

            val subtitleText = DownloadRowContent.subtitle(context, item)
            if (subtitleText.isNotEmpty()) {
                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            val status = rowStatus(item, live)
            val isError = item.state == DownloadState.FAILED || item.galleryState == GalleryState.FAILED
            Text(
                text = formatStatusText(status),
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            DownloadRowContent.ProgressBar(item)
        }

        DownloadRowActions(
            item = item,
            onPause = onPause,
            onResume = onResume,
            onRetry = onRetry,
            onFindNewLink = onFindNewLink,
            onRequestCancel = onRequestCancel,
            onRequestDelete = onRequestDelete,
            onChooseFile = onChooseFile,
            onSaveToGallery = onSaveToGallery,
            onRemoveFromGallery = onRemoveFromGallery,
        )
    }
}

private object DownloadRowActionsContent {
    @Composable
    fun ActionButton(
        item: DownloadItem,
        onPause: (String) -> Unit,
        onResume: (String) -> Unit,
        onRetry: (String) -> Unit,
        onFindNewLink: (DownloadItem) -> Unit,
        onRequestDelete: (String) -> Unit,
        onChooseFile: (String) -> Unit,
    ) {
        val action = primaryAction(item)
        val icon = actionIcon(action) ?: return
        val desc = stringResource(actionDescription(action))
        NexusIconButton(
            onClick = {
                when (action) {
                    RowAction.PAUSE -> onPause(item.id)
                    RowAction.RESUME -> onResume(item.id)
                    RowAction.RETRY -> onRetry(item.id)
                    RowAction.FIND_NEW_LINK -> onFindNewLink(item)
                    RowAction.REMOVE -> onRequestDelete(item.id)
                    RowAction.CHOOSE_FILE -> onChooseFile(item.id)
                    RowAction.NONE -> Unit
                }
            },
        ) {
            ActionIconRenderer(icon = icon, contentDescription = desc)
        }
    }

    @Composable
    fun ActionIconRenderer(
        icon: ActionIcon,
        contentDescription: String,
    ) {
        when (icon) {
            ActionIcon.PAUSE -> {
                Icon(
                    painter = painterResource(R.drawable.ic_pause),
                    contentDescription = contentDescription,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            ActionIcon.PLAY -> {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = contentDescription,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            ActionIcon.REFRESH -> {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = contentDescription,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            ActionIcon.SEARCH -> {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = contentDescription,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            ActionIcon.DELETE -> {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = contentDescription,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            ActionIcon.LIST -> {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.List,
                    contentDescription = contentDescription,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    @Composable
    fun OverflowMenu(
        item: DownloadItem,
        onRequestCancel: (String) -> Unit,
        onRequestDelete: (String) -> Unit,
        onSaveToGallery: (String) -> Unit,
        onRemoveFromGallery: (String) -> Unit,
    ) {
        var menuExpanded by remember { mutableStateOf(false) }
        Box {
            NexusIconButton(onClick = { menuExpanded = true }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.downloads_more_desc),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                rowMenu(item).forEach { menuItem ->
                    when (menuItem) {
                        RowMenuItem.SAVE_TO_GALLERY -> {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.downloads_menu_save_gallery)) },
                                onClick = {
                                    menuExpanded = false
                                    onSaveToGallery(item.id)
                                },
                            )
                        }

                        RowMenuItem.REMOVE_FROM_GALLERY -> {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.downloads_menu_remove_gallery)) },
                                onClick = {
                                    menuExpanded = false
                                    onRemoveFromGallery(item.id)
                                },
                            )
                        }

                        RowMenuItem.RETRY_GALLERY -> {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.downloads_menu_retry_gallery)) },
                                onClick = {
                                    menuExpanded = false
                                    onSaveToGallery(item.id)
                                },
                            )
                        }

                        RowMenuItem.DELETE -> {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.downloads_menu_delete)) },
                                onClick = {
                                    menuExpanded = false
                                    onRequestDelete(item.id)
                                },
                            )
                        }

                        RowMenuItem.CANCEL -> {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.downloads_menu_cancel)) },
                                onClick = {
                                    menuExpanded = false
                                    onRequestCancel(item.id)
                                },
                            )
                        }

                        RowMenuItem.REMOVE -> {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.downloads_action_remove)) },
                                onClick = {
                                    menuExpanded = false
                                    onRequestDelete(item.id)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DownloadRowActions(
    item: DownloadItem,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onRetry: (String) -> Unit,
    onFindNewLink: (DownloadItem) -> Unit,
    onRequestCancel: (String) -> Unit,
    onRequestDelete: (String) -> Unit,
    onChooseFile: (String) -> Unit,
    onSaveToGallery: (String) -> Unit,
    onRemoveFromGallery: (String) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
    ) {
        DownloadRowActionsContent.ActionButton(
            item = item,
            onPause = onPause,
            onResume = onResume,
            onRetry = onRetry,
            onFindNewLink = onFindNewLink,
            onRequestDelete = onRequestDelete,
            onChooseFile = onChooseFile,
        )
        DownloadRowActionsContent.OverflowMenu(
            item = item,
            onRequestCancel = onRequestCancel,
            onRequestDelete = onRequestDelete,
            onSaveToGallery = onSaveToGallery,
            onRemoveFromGallery = onRemoveFromGallery,
        )
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
    val body =
        stringResource(R.string.downloads_confirm_delete_body) + " " +
            stringResource(R.string.downloads_confirm_gallery_note)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.downloads_confirm_delete_title)) },
        text = { Text(body) },
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
    val body =
        stringResource(R.string.downloads_confirm_delete_all_body) + " " +
            stringResource(R.string.downloads_confirm_gallery_note)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.downloads_confirm_delete_all_title)) },
        text = { Text(body) },
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

@Preview(name = "DownloadsComponents - Light")
@Preview(name = "DownloadsComponents - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun DownloadsComponentsPreview() {
    val itemBase =
        DownloadItem(
            id = "p1",
            type = "movie",
            metaId = "m1",
            videoId = "v1",
            title = "Preview Movie",
            posterUrl = null,
            fileName = "p1.mp4",
            totalBytes = 1_000_000_000L,
            downloadedBytes = 500_000_000L,
            state = DownloadState.DOWNLOADING,
            kind = DownloadKind.HTTP,
            source = DownloadSource.Http("https://example.com/p1.mp4"),
            createdAt = 0L,
            updatedAt = 0L,
        )
    NexusTheme {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CompactStorageCard(
                card =
                    StorageCardModel(
                        PREVIEW_USED_BYTES,
                        PREVIEW_FREE_BYTES,
                        false,
                        StorageHeaderAction.PAUSE_ALL,
                        true,
                    ),
                onPauseAll = {},
                onResumeAll = {},
                onDeleteAll = {},
            )
            CompactStorageCard(
                card =
                    StorageCardModel(
                        PREVIEW_USED_BYTES,
                        PREVIEW_FREE_BYTES,
                        false,
                        StorageHeaderAction.RESUME_ALL,
                        true,
                    ),
                onPauseAll = {},
                onResumeAll = {},
                onDeleteAll = {},
            )
            CompactStorageCard(
                card =
                    StorageCardModel(
                        PREVIEW_HIGH_USED_BYTES,
                        PREVIEW_LOW_FREE_BYTES,
                        true,
                        StorageHeaderAction.NONE,
                        true,
                    ),
                onPauseAll = {},
                onResumeAll = {},
                onDeleteAll = {},
            )
            CompactStorageCard(
                card =
                    StorageCardModel(
                        0L,
                        PREVIEW_EMPTY_FREE_BYTES,
                        false,
                        StorageHeaderAction.PAUSE_ALL,
                        false,
                    ),
                onPauseAll = {},
                onResumeAll = {},
                onDeleteAll = {},
            )
            DownloadRow(
                item = itemBase.copy(id = "pause", state = DownloadState.DOWNLOADING),
                live = null,
                onTapRow = {},
                onPause = {},
                onResume = {},
                onRetry = {},
                onFindNewLink = {},
                onRequestCancel = {},
                onRequestDelete = {},
            )
            DownloadRow(
                item = itemBase.copy(id = "saved", state = DownloadState.COMPLETED, galleryState = GalleryState.SAVED),
                live = null,
                onTapRow = {},
                onPause = {},
                onResume = {},
                onRetry = {},
                onFindNewLink = {},
                onRequestCancel = {},
                onRequestDelete = {},
            )
            DownloadRow(
                item =
                    itemBase.copy(
                        id = "failed",
                        state = DownloadState.COMPLETED,
                        galleryState = GalleryState.FAILED,
                        galleryFailure = GalleryFailure.NO_SPACE,
                    ),
                live = null,
                onTapRow = {},
                onPause = {},
                onResume = {},
                onRetry = {},
                onFindNewLink = {},
                onRequestCancel = {},
                onRequestDelete = {},
            )
        }
    }
}
