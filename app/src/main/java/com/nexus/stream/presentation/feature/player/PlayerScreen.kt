package com.nexus.stream.presentation.feature.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.nexus.stream.R
import com.nexus.stream.domain.model.ThemeMode
import com.nexus.stream.domain.model.ThemeSettings
import com.nexus.stream.presentation.common.NexusButton
import com.nexus.stream.presentation.common.NexusCard
import com.nexus.stream.presentation.common.NexusIconButton
import com.nexus.stream.presentation.theme.NexusTheme
import com.nexus.stream.presentation.theme.tokens.AmoledTokens

private const val PICK_FILE_CARD_WIDTH_FRACTION = 0.7f
private val PICK_FILE_LIST_MAX_HEIGHT = 200.dp

@Composable
fun PlayerScreen(
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(context) {
        val activity = context.findActivity()
        val window = activity?.window

        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        if (window != null) {
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) {
                    viewModel.onEvent(PlayerEvent.AppStopped)
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    BackHandler {
        onNavigateUp()
    }

    if (uiState.phase is PlayerPhase.Empty) {
        LaunchedEffect(Unit) {
            onNavigateUp()
        }
    }

    val controller = remember { viewModel.getController() }

    PlayerScreenContent(
        uiState = uiState,
        onNavigateUp = onNavigateUp,
        onTogglePlay = { viewModel.onEvent(PlayerEvent.TogglePlay) },
        onSeekTo = { viewModel.onEvent(PlayerEvent.SeekTo(it)) },
        onToggleControls = { viewModel.onEvent(PlayerEvent.ToggleControls) },
        onPickFile = { viewModel.onEvent(PlayerEvent.PickFile(it)) },
        onAllowMeteredOnce = { viewModel.onEvent(PlayerEvent.AllowMeteredOnce) },
        modifier = modifier,
        surfaceFactory =
            controller?.let { ctrl ->
                { ctx -> ctrl.createSurface(ctx) }
            },
        surfaceRelease =
            controller?.let { ctrl ->
                { ctrl.detachSurface() }
            },
    )
}

@Composable
internal fun PlayerScreenContent(
    uiState: PlayerUiState,
    onNavigateUp: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onToggleControls: () -> Unit,
    onPickFile: (Int) -> Unit,
    onAllowMeteredOnce: () -> Unit,
    modifier: Modifier = Modifier,
    surfaceFactory: ((Context) -> android.view.View)? = null,
    surfaceRelease: (() -> Unit)? = null,
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(AmoledTokens.background),
    ) {
        if (surfaceFactory != null) {
            AndroidView(
                factory = surfaceFactory,
                onRelease = { surfaceRelease?.invoke() },
                modifier = Modifier.fillMaxSize(),
            )
        }

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        onToggleControls()
                    },
        )

        AnimatedVisibility(
            visible = uiState.controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            PlayerControlsOverlay(
                uiState = uiState,
                onNavigateUp = onNavigateUp,
                onTogglePlay = onTogglePlay,
                onSeekTo = onSeekTo,
            )
        }

        PlayerStateOverlay(
            phase = uiState.phase,
            onNavigateUp = onNavigateUp,
            onPickFile = onPickFile,
            onAllowMeteredOnce = onAllowMeteredOnce,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

@Composable
private fun PlayerControlsOverlay(
    uiState: PlayerUiState,
    onNavigateUp: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f)),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(NexusTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NexusIconButton(
                onClick = onNavigateUp,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.navigate_up),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(modifier = Modifier.width(NexusTheme.spacing.sm))
            Text(
                text = uiState.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }

        NexusIconButton(
            onClick = onTogglePlay,
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .size(64.dp),
        ) {
            if (uiState.isPlaying) {
                Icon(
                    painter = painterResource(R.drawable.ic_pause),
                    contentDescription = stringResource(R.string.player_pause),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(48.dp),
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = stringResource(R.string.player_play),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(48.dp),
                )
            }
        }

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(NexusTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = formatPlaybackTime(uiState.positionMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            val duration = uiState.durationMs
            var sliderProgress by remember(uiState.positionMs, duration) {
                mutableFloatStateOf(
                    if (duration > 0) {
                        (uiState.positionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                    } else {
                        0f
                    },
                )
            }
            Slider(
                value = sliderProgress,
                onValueChange = { sliderProgress = it },
                onValueChangeFinished = {
                    if (duration > 0) {
                        onSeekTo((sliderProgress * duration).toLong())
                    }
                },
                enabled = uiState.isSeekable && duration > 0,
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(horizontal = NexusTheme.spacing.sm),
            )

            Text(
                text = formatPlaybackTime(duration),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun PlayerStateOverlay(
    phase: PlayerPhase,
    onNavigateUp: () -> Unit,
    onPickFile: (Int) -> Unit,
    onAllowMeteredOnce: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (phase) {
        is PlayerPhase.Preparing -> {
            PlayerPreparingOverlay(phase = phase, modifier = modifier)
        }

        is PlayerPhase.PickFile -> {
            PlayerPickFileOverlay(phase = phase, onPickFile = onPickFile, modifier = modifier)
        }

        is PlayerPhase.Error -> {
            PlayerErrorOverlay(
                phase = phase,
                onNavigateUp = onNavigateUp,
                onAllowMeteredOnce = onAllowMeteredOnce,
                modifier = modifier,
            )
        }

        else -> {}
    }
}

@Composable
private fun PlayerPreparingOverlay(
    phase: PlayerPhase.Preparing,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(NexusTheme.spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(NexusTheme.spacing.md),
    ) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
        )
        val labelText =
            when (phase.label) {
                PreparingLabel.OPENING -> {
                    stringResource(R.string.player_preparing)
                }

                PreparingLabel.FETCHING_METADATA -> {
                    stringResource(R.string.player_fetching_metadata)
                }

                PreparingLabel.BUFFERING -> {
                    val pct = phase.percent ?: 0
                    val peers = phase.peers
                    if (peers != null && peers > 0) {
                        stringResource(R.string.player_buffering, pct, peers)
                    } else {
                        stringResource(R.string.player_buffering_no_peers, pct)
                    }
                }
            }
        Text(
            text = labelText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun PlayerPickFileOverlay(
    phase: PlayerPhase.PickFile,
    onPickFile: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    NexusCard(
        modifier =
            modifier
                .padding(NexusTheme.spacing.xl)
                .fillMaxWidth(PICK_FILE_CARD_WIDTH_FRACTION),
    ) {
        Column(
            modifier = Modifier.padding(NexusTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(NexusTheme.spacing.sm),
        ) {
            Text(
                text = stringResource(R.string.player_pick_file_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            LazyColumn(
                modifier = Modifier.heightIn(max = PICK_FILE_LIST_MAX_HEIGHT),
                verticalArrangement = Arrangement.spacedBy(NexusTheme.spacing.xs),
            ) {
                items(phase.files) { file ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable { onPickFile(file.index) }
                                .padding(NexusTheme.spacing.sm),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = file.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = formatFileSize(file.sizeBytes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerErrorOverlay(
    phase: PlayerPhase.Error,
    onNavigateUp: () -> Unit,
    onAllowMeteredOnce: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(NexusTheme.spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(NexusTheme.spacing.md),
    ) {
        val errorMessage =
            when (phase.kind) {
                PlayerError.UNPLAYABLE -> stringResource(R.string.error_player_unplayable)
                PlayerError.PLAYBACK_FAILED -> stringResource(R.string.error_player_failed)
                PlayerError.METERED_BLOCKED -> stringResource(R.string.error_metered_blocked)
                PlayerError.TORRENT_TIMEOUT -> stringResource(R.string.error_torrent_timeout)
                PlayerError.TORRENT_NO_VIDEO -> stringResource(R.string.error_torrent_no_video)
                PlayerError.TORRENT_STORAGE -> stringResource(R.string.error_torrent_storage)
                PlayerError.TORRENT_UNAVAILABLE -> stringResource(R.string.error_torrent_engine_unavailable)
                PlayerError.OTHER -> stringResource(R.string.error_player_other)
            }
        Text(
            text = errorMessage,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(NexusTheme.spacing.md),
        ) {
            NexusButton(
                onClick = onNavigateUp,
            ) {
                Text(text = stringResource(R.string.navigate_up))
            }
            if (phase.kind == PlayerError.METERED_BLOCKED) {
                NexusButton(
                    onClick = onAllowMeteredOnce,
                ) {
                    Text(text = stringResource(R.string.player_allow_metered_once))
                }
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

@Preview(name = "Player Controls Light", showBackground = true)
@Composable
internal fun PlayerControlsLightPreview() {
    NexusTheme(settings = ThemeSettings(mode = ThemeMode.LIGHT)) {
        PlayerScreenContent(
            uiState =
                PlayerUiState(
                    title = "Sample Video Title",
                    phase = PlayerPhase.Playing,
                    isPlaying = true,
                    isSeekable = true,
                    positionMs = 65_000,
                    durationMs = 180_000,
                    controlsVisible = true,
                ),
            onNavigateUp = {},
            onTogglePlay = {},
            onSeekTo = {},
            onToggleControls = {},
            onPickFile = {},
            onAllowMeteredOnce = {},
        )
    }
}

@Preview(name = "Player Controls Dark", showBackground = true)
@Composable
internal fun PlayerControlsDarkPreview() {
    NexusTheme(settings = ThemeSettings(mode = ThemeMode.DARK)) {
        PlayerScreenContent(
            uiState =
                PlayerUiState(
                    title = "Sample Video Title",
                    phase = PlayerPhase.Playing,
                    isPlaying = true,
                    isSeekable = true,
                    positionMs = 65_000,
                    durationMs = 180_000,
                    controlsVisible = true,
                ),
            onNavigateUp = {},
            onTogglePlay = {},
            onSeekTo = {},
            onToggleControls = {},
            onPickFile = {},
            onAllowMeteredOnce = {},
        )
    }
}

@Preview(name = "Player Error Dark", showBackground = true)
@Composable
internal fun PlayerErrorDarkPreview() {
    NexusTheme(settings = ThemeSettings(mode = ThemeMode.DARK)) {
        PlayerScreenContent(
            uiState =
                PlayerUiState(
                    title = "Sample Video Title",
                    phase = PlayerPhase.Error(PlayerError.METERED_BLOCKED),
                    controlsVisible = false,
                ),
            onNavigateUp = {},
            onTogglePlay = {},
            onSeekTo = {},
            onToggleControls = {},
            onPickFile = {},
            onAllowMeteredOnce = {},
        )
    }
}
