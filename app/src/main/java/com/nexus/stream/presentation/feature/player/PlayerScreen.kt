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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.nexus.stream.presentation.common.NexusIconButton
import com.nexus.stream.presentation.theme.NexusTheme
import com.nexus.stream.presentation.theme.tokens.PlayerOverlayColors

private const val PICK_FILE_CARD_WIDTH_FRACTION = 0.7f
private val PICK_FILE_LIST_MAX_HEIGHT = 200.dp
private val NETWORK_CARD_TOP_PADDING = 64.dp

@Composable
fun PlayerScreen(
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val activity = remember(context) { context.findActivity() }
    val window = activity?.window

    val restoreChrome =
        remember(activity, window) {
            {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                if (window != null) {
                    val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                    insetsController.show(WindowInsetsCompat.Type.systemBars())
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    val lp = window.attributes
                    lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                    window.attributes = lp
                }
            }
        }

    val handleBack =
        remember(restoreChrome, onNavigateUp) {
            {
                restoreChrome()
                onNavigateUp()
            }
        }

    DisposableEffect(context) {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        if (window != null) {
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
        }

        onDispose {
            restoreChrome()
        }
    }

    val shouldKeepScreenOn = uiState.isPlaying && uiState.phase is PlayerPhase.Playing
    DisposableEffect(shouldKeepScreenOn, window) {
        if (window != null) {
            if (shouldKeepScreenOn) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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
        handleBack()
    }

    val controller = viewModel.getController()

    PlayerScreenContent(
        uiState = uiState,
        onNavigateUp = handleBack,
        onTogglePlay = { viewModel.onEvent(PlayerEvent.TogglePlay) },
        onSeekTo = { viewModel.onEvent(PlayerEvent.SeekTo(it)) },
        onSeekBy = { viewModel.onEvent(PlayerEvent.SeekBy(it)) },
        onToggleControls = { viewModel.onEvent(PlayerEvent.ToggleControls) },
        onPickFile = { viewModel.onEvent(PlayerEvent.PickFile(it)) },
        onAllowMeteredOnce = { viewModel.onEvent(PlayerEvent.AllowMeteredOnce) },
        onRetry = { viewModel.onEvent(PlayerEvent.Retry) },
        onToggleNetworkCard = { viewModel.onEvent(PlayerEvent.ToggleNetworkCard) },
        onDismissNetworkCard = { viewModel.onEvent(PlayerEvent.DismissNetworkCard) },
        onHoldSpeed = { viewModel.onEvent(PlayerEvent.HoldSpeed(it)) },
        surfaceFactory = controller?.let { ctrl -> { ctx -> ctrl.createSurface(ctx) } },
        surfaceRelease = { controller?.detachSurface() },
        modifier = modifier,
    )
}

@Composable
internal fun PlayerScreenContent(
    uiState: PlayerUiState,
    onNavigateUp: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSeekBy: (Long) -> Unit = {},
    onHoldSpeed: (Boolean) -> Unit = {},
    onToggleControls: () -> Unit,
    onPickFile: (Int) -> Unit,
    onAllowMeteredOnce: () -> Unit,
    onRetry: () -> Unit,
    onToggleNetworkCard: () -> Unit,
    onDismissNetworkCard: () -> Unit,
    modifier: Modifier = Modifier,
    surfaceFactory: ((Context) -> android.view.View)? = null,
    surfaceRelease: (() -> Unit)? = null,
) {
    val showLoadingOverlay =
        uiState.phase is PlayerPhase.Preparing ||
            (uiState.phase is PlayerPhase.Playing && uiState.isBuffering)

    CompositionLocalProvider(LocalContentColor provides PlayerOverlayColors.Content) {
        Box(
            modifier =
                modifier
                    .fillMaxSize()
                    .background(PlayerOverlayColors.Scrim),
        ) {
            if (surfaceFactory != null) {
                AndroidView(
                    factory = surfaceFactory,
                    onRelease = { surfaceRelease?.invoke() },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            PlayerGestureLayer(
                isActive = uiState.phase is PlayerPhase.Playing || uiState.phase is PlayerPhase.Ended,
                isSeekable = uiState.isSeekable,
                seekStepMs = uiState.seekStepMs,
                holdSpeed = uiState.holdSpeed,
                onToggleControls = onToggleControls,
                onSeekBy = onSeekBy,
                onHoldSpeed = onHoldSpeed,
            )

            if (showLoadingOverlay) {
                PlayerLoadingOverlay(
                    title = uiState.title,
                    logoUrl = uiState.logoUrl,
                    backgroundUrl = uiState.backgroundUrl,
                    phase = uiState.phase,
                    stats = uiState.stats,
                    showArtwork = shouldShowLoadingArtwork(uiState.phase),
                )
            }

            AnimatedVisibility(
                visible = uiState.controlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize(),
            ) {
                PlayerControlsOverlay(
                    uiState = uiState,
                    showCenterPlay = !showLoadingOverlay,
                    onNavigateUp = onNavigateUp,
                    onTogglePlay = onTogglePlay,
                    onSeekTo = onSeekTo,
                    onToggleNetworkCard = onToggleNetworkCard,
                )
            }

            if (uiState.showNetworkCard && uiState.isTorrentSource) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(top = NETWORK_CARD_TOP_PADDING, end = NexusTheme.spacing.md),
                    contentAlignment = Alignment.TopEnd,
                ) {
                    NetworkStatusCard(
                        stats = uiState.stats,
                        onDismiss = onDismissNetworkCard,
                    )
                }
            }

            PlayerStateOverlay(
                phase = uiState.phase,
                onNavigateUp = onNavigateUp,
                onPickFile = onPickFile,
                onAllowMeteredOnce = onAllowMeteredOnce,
                onRetry = onRetry,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

@Composable
private fun PlayerControlsOverlay(
    uiState: PlayerUiState,
    showCenterPlay: Boolean,
    onNavigateUp: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onToggleNetworkCard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            colors =
                                listOf(
                                    PlayerOverlayColors.Scrim.copy(alpha = 0.60f),
                                    Color.Transparent,
                                ),
                        ),
                    ).padding(NexusTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NexusIconButton(
                onClick = onNavigateUp,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.navigate_up),
                    tint = PlayerOverlayColors.Content,
                )
            }
            Spacer(modifier = Modifier.width(NexusTheme.spacing.sm))
            Text(
                text = uiState.title,
                style = MaterialTheme.typography.titleMedium,
                color = PlayerOverlayColors.Content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (uiState.isTorrentSource) {
                Spacer(modifier = Modifier.width(NexusTheme.spacing.sm))
                NexusIconButton(
                    onClick = onToggleNetworkCard,
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = stringResource(R.string.player_diag_toggle),
                        tint = PlayerOverlayColors.Content,
                    )
                }
            }
        }

        if (showCenterPlay) {
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
                        tint = PlayerOverlayColors.Content,
                        modifier = Modifier.size(48.dp),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = stringResource(R.string.player_play),
                        tint = PlayerOverlayColors.Content,
                        modifier = Modifier.size(48.dp),
                    )
                }
            }
        }

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors =
                                listOf(
                                    Color.Transparent,
                                    PlayerOverlayColors.Scrim.copy(alpha = 0.60f),
                                ),
                        ),
                    ).padding(NexusTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = formatPlaybackTime(uiState.positionMs),
                style = MaterialTheme.typography.labelMedium,
                color = PlayerOverlayColors.Content,
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
                colors =
                    SliderDefaults.colors(
                        thumbColor = PlayerOverlayColors.Content,
                        activeTrackColor = PlayerOverlayColors.Content,
                        inactiveTrackColor = PlayerOverlayColors.Content.copy(alpha = 0.30f),
                    ),
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(horizontal = NexusTheme.spacing.sm),
            )

            Text(
                text = formatPlaybackTime(duration),
                style = MaterialTheme.typography.labelMedium,
                color = PlayerOverlayColors.Content,
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
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (phase) {
        is PlayerPhase.PickFile -> {
            PlayerPickFileOverlay(phase = phase, onPickFile = onPickFile, modifier = modifier)
        }

        is PlayerPhase.Error -> {
            PlayerErrorOverlay(
                phase = phase,
                onNavigateUp = onNavigateUp,
                onAllowMeteredOnce = onAllowMeteredOnce,
                onRetry = onRetry,
                modifier = modifier,
            )
        }

        else -> {}
    }
}

@Composable
private fun PlayerPickFileOverlay(
    phase: PlayerPhase.PickFile,
    onPickFile: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = NexusTheme.shapes.card,
        colors =
            CardDefaults.cardColors(
                containerColor = PlayerOverlayColors.Card,
            ),
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
                color = PlayerOverlayColors.Content,
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
                            color = PlayerOverlayColors.Content,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = formatFileSize(file.sizeBytes),
                            style = MaterialTheme.typography.bodySmall,
                            color = PlayerOverlayColors.ContentSecondary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun resolveErrorMessage(phase: PlayerPhase.Error): String {
    val resId = playerErrorMessageRes(phase.kind)
    val code = phase.statusCode
    return if (code != null && hasStatusPlaceholder(phase.kind)) {
        stringResource(resId, code)
    } else {
        stringResource(resId)
    }
}

@Composable
private fun PlayerErrorOverlay(
    phase: PlayerPhase.Error,
    onNavigateUp: () -> Unit,
    onAllowMeteredOnce: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = NexusTheme.shapes.card,
        colors =
            CardDefaults.cardColors(
                containerColor = PlayerOverlayColors.Card,
            ),
        modifier = modifier.padding(NexusTheme.spacing.xl),
    ) {
        Column(
            modifier = Modifier.padding(NexusTheme.spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(NexusTheme.spacing.md),
        ) {
            val errorMessage = resolveErrorMessage(phase)
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodyLarge,
                color = PlayerOverlayColors.Content,
                textAlign = TextAlign.Center,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(NexusTheme.spacing.md),
            ) {
                NexusButton(
                    onClick = onNavigateUp,
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = PlayerOverlayColors.Content,
                            contentColor = PlayerOverlayColors.OnContent,
                        ),
                ) {
                    Text(text = stringResource(R.string.navigate_up))
                }
                NexusButton(
                    onClick = onRetry,
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = PlayerOverlayColors.Content,
                            contentColor = PlayerOverlayColors.OnContent,
                        ),
                ) {
                    Text(text = stringResource(R.string.player_retry))
                }
                if (phase.kind == PlayerError.METERED_BLOCKED) {
                    NexusButton(
                        onClick = onAllowMeteredOnce,
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = PlayerOverlayColors.Content,
                                contentColor = PlayerOverlayColors.OnContent,
                            ),
                    ) {
                        Text(text = stringResource(R.string.player_allow_metered_once))
                    }
                }
            }
        }
    }
}

internal tailrec fun Context.findActivity(): Activity? =
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
                    isTorrentSource = true,
                ),
            onNavigateUp = {},
            onTogglePlay = {},
            onSeekTo = {},
            onToggleControls = {},
            onPickFile = {},
            onAllowMeteredOnce = {},
            onRetry = {},
            onToggleNetworkCard = {},
            onDismissNetworkCard = {},
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
                    isPlaying = false,
                    isSeekable = true,
                    positionMs = 10_000,
                    durationMs = 180_000,
                    controlsVisible = true,
                    isTorrentSource = false,
                ),
            onNavigateUp = {},
            onTogglePlay = {},
            onSeekTo = {},
            onToggleControls = {},
            onPickFile = {},
            onAllowMeteredOnce = {},
            onRetry = {},
            onToggleNetworkCard = {},
            onDismissNetworkCard = {},
        )
    }
}
