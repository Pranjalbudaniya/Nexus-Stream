package com.nexus.stream.presentation.common

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import com.nexus.stream.R
import com.nexus.stream.domain.model.ListStatus
import com.nexus.stream.domain.model.MetaPreview
import com.nexus.stream.domain.model.Outcome
import com.nexus.stream.domain.repository.LibraryRepository
import com.nexus.stream.domain.repository.MetaRepository
import com.nexus.stream.domain.repository.ProgressRepository
import com.nexus.stream.domain.usecase.PosterAction
import com.nexus.stream.domain.usecase.TrailerState
import com.nexus.stream.domain.usecase.posterActions
import com.nexus.stream.presentation.theme.NexusTheme
import com.nexus.stream.util.youtubeWatchUrlOrNull
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

interface PosterActionsController {
    fun show(
        item: MetaPreview,
        fromContinue: Boolean = false,
    )
}

val LocalPosterActions =
    staticCompositionLocalOf<PosterActionsController> {
        object : PosterActionsController {
            override fun show(
                item: MetaPreview,
                fromContinue: Boolean,
            ) = Unit
        }
    }

data class PosterSheetState(
    val item: MetaPreview,
    val fromContinue: Boolean,
    val trailerState: TrailerState = TrailerState.CHECKING,
    val trailerUrl: String? = null,
)

@HiltViewModel
class PosterActionsViewModel
    @Inject
    constructor(
        private val metaRepository: MetaRepository,
        private val libraryRepository: LibraryRepository,
        private val progressRepository: ProgressRepository,
    ) : ViewModel() {
        private val _sheetState = MutableStateFlow<PosterSheetState?>(null)
        val sheetState: StateFlow<PosterSheetState?> = _sheetState.asStateFlow()

        private val _listStatuses = MutableStateFlow<Map<String, ListStatus>>(emptyMap())
        val listStatuses: StateFlow<Map<String, ListStatus>> = _listStatuses.asStateFlow()

        init {
            viewModelScope.launch {
                libraryRepository.observeListStatuses().collectLatest { statuses ->
                    _listStatuses.value = statuses
                }
            }
        }

        fun show(
            item: MetaPreview,
            fromContinue: Boolean,
        ) {
            _sheetState.value =
                PosterSheetState(
                    item = item,
                    fromContinue = fromContinue,
                    trailerState = TrailerState.CHECKING,
                )
            viewModelScope.launch {
                when (val outcome = metaRepository.getMeta(type = item.type, id = item.id)) {
                    is Outcome.Success -> {
                        val trailerUrl =
                            outcome.value.meta.trailers.firstNotNullOfOrNull {
                                youtubeWatchUrlOrNull(it)
                            }
                        _sheetState.update { current ->
                            if (current?.item?.id == item.id) {
                                current.copy(
                                    trailerState =
                                        if (trailerUrl != null) {
                                            TrailerState.AVAILABLE
                                        } else {
                                            TrailerState.NONE
                                        },
                                    trailerUrl = trailerUrl,
                                )
                            } else {
                                current
                            }
                        }
                    }

                    is Outcome.Failure -> {
                        _sheetState.update { current ->
                            if (current?.item?.id == item.id) {
                                current.copy(trailerState = TrailerState.NONE)
                            } else {
                                current
                            }
                        }
                    }
                }
            }
        }

        fun dismiss() {
            _sheetState.value = null
        }

        fun setStatus(
            item: MetaPreview,
            status: ListStatus?,
        ) {
            viewModelScope.launch {
                libraryRepository.setStatus(item, status)
            }
        }

        fun removeFromContinue(metaId: String) {
            viewModelScope.launch {
                progressRepository.removeFromContinueWatching(metaId)
            }
        }
    }

@Composable
fun PosterActionsHost(
    onNavigateToDetail: (String, String) -> Unit,
    viewModel: PosterActionsViewModel = hiltViewModel(),
    content: @Composable () -> Unit,
) {
    val sheetState by viewModel.sheetState.collectAsState()
    val listStatuses by viewModel.listStatuses.collectAsState()
    val playbackLauncher = LocalPlayback.current
    val context = LocalContext.current
    var showStatusDialog by remember { mutableStateOf(false) }

    val controller =
        remember(viewModel) {
            object : PosterActionsController {
                override fun show(
                    item: MetaPreview,
                    fromContinue: Boolean,
                ) {
                    viewModel.show(item, fromContinue)
                }
            }
        }

    CompositionLocalProvider(LocalPosterActions provides controller) {
        content()

        sheetState?.let { state ->
            val item = state.item
            val status = listStatuses[item.id]
            val actions = posterActions(item.type, state.trailerState, status, state.fromContinue)

            PosterActionsSheet(
                item = item,
                actions = actions,
                trailerUrl = state.trailerUrl,
                onDismiss = { viewModel.dismiss() },
                onPlay = {
                    viewModel.dismiss()
                    playbackLauncher.open(
                        PlaybackRequest(
                            type = item.type,
                            metaId = item.id,
                            videoId = item.id,
                            title = item.name,
                        ),
                    )
                },
                onDetail = {
                    viewModel.dismiss()
                    onNavigateToDetail(item.type, item.id)
                },
                onAddWatchlist = {
                    viewModel.setStatus(item, ListStatus.PLAN_TO_WATCH)
                },
                onOpenStatusDialog = {
                    showStatusDialog = true
                },
                onRemoveFromContinue = {
                    viewModel.removeFromContinue(item.id)
                    viewModel.dismiss()
                },
                onLaunchTrailer = { url ->
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        context.startActivity(intent)
                    } catch (_: ActivityNotFoundException) {
                    }
                },
            )

            if (showStatusDialog) {
                ListStatusDialog(
                    current = status,
                    onSelect = { newStatus ->
                        viewModel.setStatus(item, newStatus)
                        showStatusDialog = false
                    },
                    onRemove = {
                        viewModel.setStatus(item, null)
                        showStatusDialog = false
                    },
                    onDismiss = {
                        showStatusDialog = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PosterActionsSheet(
    item: MetaPreview,
    actions: List<PosterAction>,
    trailerUrl: String?,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onDetail: () -> Unit,
    onAddWatchlist: () -> Unit,
    onOpenStatusDialog: () -> Unit,
    onRemoveFromContinue: () -> Unit,
    onLaunchTrailer: (String) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = NexusTheme.spacing.lg),
        ) {
            PosterSheetHeader(item = item)
            Spacer(modifier = Modifier.height(NexusTheme.spacing.md))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(NexusTheme.spacing.xs))
            PosterActionsList(
                actions = actions,
                trailerUrl = trailerUrl,
                onPlay = onPlay,
                onDetail = onDetail,
                onAddWatchlist = onAddWatchlist,
                onOpenStatusDialog = onOpenStatusDialog,
                onRemoveFromContinue = onRemoveFromContinue,
                onLaunchTrailer = onLaunchTrailer,
            )
        }
    }
}

@Composable
private fun PosterSheetHeader(item: MetaPreview) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = NexusTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(width = 48.dp, height = 72.dp)
                    .clip(NexusTheme.shapes.poster)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            if (!item.poster.isNullOrBlank()) {
                AsyncImage(
                    model = item.poster,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
            }
        }
        Spacer(modifier = Modifier.width(NexusTheme.spacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val yearAndType =
                listOfNotNull(
                    item.releaseInfo,
                    item.type.replaceFirstChar { it.uppercase() },
                ).joinToString(" • ")
            if (yearAndType.isNotBlank()) {
                Spacer(modifier = Modifier.height(NexusTheme.spacing.xxs))
                Text(
                    text = yearAndType,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun PosterActionsList(
    actions: List<PosterAction>,
    trailerUrl: String?,
    onPlay: () -> Unit,
    onDetail: () -> Unit,
    onAddWatchlist: () -> Unit,
    onOpenStatusDialog: () -> Unit,
    onRemoveFromContinue: () -> Unit,
    onLaunchTrailer: (String) -> Unit,
) {
    actions.forEach { action ->
        when (action) {
            is PosterAction.Play -> {
                PosterActionRow(
                    leading = {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    label = stringResource(R.string.poster_action_play),
                    onClick = onPlay,
                )
            }

            is PosterAction.Detail -> {
                PosterActionRow(
                    leading = {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    label = stringResource(R.string.poster_action_detail),
                    onClick = onDetail,
                )
            }

            is PosterAction.Trailer -> {
                TrailerActionRow(
                    state = action.state,
                    trailerUrl = trailerUrl,
                    onLaunchTrailer = onLaunchTrailer,
                )
            }

            is PosterAction.Watchlist -> {
                WatchlistActionRow(
                    status = action.status,
                    onAddWatchlist = onAddWatchlist,
                    onOpenStatusDialog = onOpenStatusDialog,
                )
            }

            is PosterAction.RemoveFromContinue -> {
                PosterActionRow(
                    leading = {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                    },
                    label = stringResource(R.string.poster_action_remove_continue),
                    labelColor = MaterialTheme.colorScheme.error,
                    onClick = onRemoveFromContinue,
                )
            }
        }
    }
}

@Composable
private fun TrailerActionRow(
    state: TrailerState,
    trailerUrl: String?,
    onLaunchTrailer: (String) -> Unit,
) {
    when (state) {
        TrailerState.CHECKING -> {
            PosterActionRow(
                leading = {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                },
                label = stringResource(R.string.poster_action_trailer_checking),
                enabled = false,
                onClick = {},
            )
        }

        TrailerState.AVAILABLE -> {
            PosterActionRow(
                leading = {
                    Icon(
                        painter = painterResource(R.drawable.ic_movie),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                },
                label = stringResource(R.string.poster_action_trailer_available),
                onClick = {
                    trailerUrl?.let(onLaunchTrailer)
                },
            )
        }

        TrailerState.NONE -> {
            PosterActionRow(
                leading = {
                    Icon(
                        painter = painterResource(R.drawable.ic_movie),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    )
                },
                label = stringResource(R.string.poster_action_trailer_none),
                enabled = false,
                onClick = {},
            )
        }
    }
}

@Composable
private fun WatchlistActionRow(
    status: ListStatus?,
    onAddWatchlist: () -> Unit,
    onOpenStatusDialog: () -> Unit,
) {
    if (status == null) {
        PosterActionRow(
            leading = {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            },
            label = stringResource(R.string.poster_action_add_watchlist),
            onClick = onAddWatchlist,
        )
    } else {
        PosterActionRow(
            leading = {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
            label =
                stringResource(
                    R.string.watchlist_in_status,
                    stringResource(status.labelResId()),
                ),
            onClick = onOpenStatusDialog,
        )
    }
}

@Composable
private fun PosterActionRow(
    leading: @Composable () -> Unit,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    labelColor: androidx.compose.ui.graphics.Color =
        if (enabled) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        },
) {
    val clickableModifier =
        if (enabled) {
            Modifier.clickable(role = Role.Button, onClick = onClick)
        } else {
            Modifier
        }

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .then(clickableModifier)
                .padding(horizontal = NexusTheme.spacing.md, vertical = NexusTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            leading()
        }
        Spacer(modifier = Modifier.width(NexusTheme.spacing.md))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = labelColor,
        )
    }
}
