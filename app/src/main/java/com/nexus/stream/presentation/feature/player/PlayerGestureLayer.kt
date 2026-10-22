package com.nexus.stream.presentation.feature.player

import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import android.provider.Settings
import android.view.Window
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nexus.stream.R
import com.nexus.stream.presentation.theme.NexusTheme
import com.nexus.stream.presentation.theme.tokens.PlayerOverlayColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val PILL_DISMISS_DELAY_MS = 800L
private const val CHIP_DISMISS_DELAY_MS = 1200L
private const val DEFAULT_BRIGHTNESS_FALLBACK = 0.5f
private const val BRIGHTNESS_SCALE = 255f
private const val MIN_BRIGHTNESS = 0.02f
private val PILL_BAR_HEIGHT = 80.dp
private val PILL_BAR_WIDTH = 4.dp
private val PILL_ICON_SIZE = 20.dp
private val PILL_WIDTH = 72.dp
private val PILL_HEIGHT = 160.dp
private val PILL_PERCENT_BOX_WIDTH = 48.dp
private val HOLD_PILL_WIDTH = 72.dp
private val HOLD_PILL_HEIGHT = 32.dp

@Composable
fun PlayerGestureLayer(
    isActive: Boolean,
    isSeekable: Boolean,
    onToggleControls: () -> Unit,
    onSeekBy: (Long) -> Unit,
    modifier: Modifier = Modifier,
    seekStepMs: Long = 10_000L,
    holdSpeed: Boolean = false,
    onHoldSpeed: (Boolean) -> Unit = {},
    window: Window? = LocalContext.current.findActivity()?.window,
    clock: () -> Long = { SystemClock.uptimeMillis() },
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val state =
        remember(context, window, clock, coroutineScope, onToggleControls, onSeekBy, onHoldSpeed) {
            PlayerGestureState(
                context = context,
                window = window,
                clock = clock,
                coroutineScope = coroutineScope,
                onSeekBy = onSeekBy,
                onHoldSpeed = onHoldSpeed,
            )
        }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .pointerInput(isActive) {
                    if (!isActive) return@pointerInput
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            if (state.isHoldingSpeed) return@detectVerticalDragGestures
                            state.onDragStart(offset.y, offset.x, size.height.toFloat(), size.width.toFloat())
                        },
                        onDragEnd = { state.onDragFinish() },
                        onDragCancel = { state.onDragFinish() },
                        onVerticalDrag = { change, dragAmount ->
                            if (state.isHoldingSpeed) return@detectVerticalDragGestures
                            change.consume()
                            state.onDragMove(dragAmount, size.height.toFloat())
                        },
                    )
                }.pointerInput(isActive, isSeekable, seekStepMs) {
                    detectTapGestures(
                        onPress = {
                            tryAwaitRelease()
                            if (state.isHoldingSpeed) {
                                state.onHoldRelease()
                            }
                        },
                        onLongPress = {
                            if (isActive) {
                                state.onLongPress()
                            }
                        },
                        onTap = {
                            if (!state.isHoldingSpeed) {
                                onToggleControls()
                            }
                        },
                        onDoubleTap =
                            if (!isActive || !isSeekable) {
                                null
                            } else {
                                { offset ->
                                    if (!state.isHoldingSpeed) {
                                        state.onDoubleTap(offset.x, offset.y, size.width.toFloat(), seekStepMs)
                                    }
                                }
                            },
                    )
                },
    ) {
        SeekFeedback(
            state = state.seekChipState,
            zone = state.seekChipZone,
            tapPoint = state.seekTapPoint,
            triggerCount = state.seekTriggerCount,
        )
        IndicatorPillOverlay(
            visible = state.isPillVisible,
            zone = state.activePillZone,
            level = state.currentLevel,
        )
        HoldSpeedPillOverlay(
            visible = holdSpeed,
        )
    }
}

private class PlayerGestureState(
    private val context: Context,
    private val window: Window?,
    private val clock: () -> Long,
    private val coroutineScope: CoroutineScope,
    private val onSeekBy: (Long) -> Unit,
    private val onHoldSpeed: (Boolean) -> Unit,
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    var seekChipState by mutableStateOf(SeekChipState())
    var seekChipZone by mutableStateOf(GestureZone.RIGHT)
    var seekTapPoint by mutableStateOf(Offset.Zero)
    var seekTriggerCount by mutableIntStateOf(0)
    var lastTapMs by mutableLongStateOf(0L)
    var alreadyAppliedDelta by mutableLongStateOf(0L)
    private var chipDismissJob: Job? = null

    var isHoldingSpeed by mutableStateOf(false)

    var isDragAllowed by mutableStateOf(false)
    private var dragStartLevel by mutableFloatStateOf(0f)
    private var totalDragDeltaY by mutableFloatStateOf(0f)
    private var dragZone by mutableStateOf(GestureZone.RIGHT)
    var activePillZone by mutableStateOf<GestureZone?>(null)
    var currentLevel by mutableFloatStateOf(0f)
    var isPillVisible by mutableStateOf(false)
    private var pillDismissJob: Job? = null

    fun onLongPress() {
        if (!isDragAllowed) {
            isHoldingSpeed = true
            onHoldSpeed(true)
        }
    }

    fun onHoldRelease() {
        if (isHoldingSpeed) {
            isHoldingSpeed = false
            onHoldSpeed(false)
        }
    }

    fun onDragStart(
        offsetY: Float,
        offsetX: Float,
        height: Float,
        width: Float,
    ) {
        if (isHoldingSpeed || !dragStartAllowed(offsetY, height)) {
            isDragAllowed = false
            return
        }
        isDragAllowed = true
        pillDismissJob?.cancel()
        val zone = zoneOf(offsetX, width)
        dragZone = zone
        totalDragDeltaY = 0f

        val initial =
            if (zone == GestureZone.LEFT) {
                getInitialBrightness(context, window)
            } else {
                getInitialVolume(audioManager)
            }
        dragStartLevel = initial
        currentLevel = initial
        activePillZone = zone
        isPillVisible = true
    }

    fun onDragMove(
        dragAmount: Float,
        height: Float,
    ) {
        if (!isDragAllowed || isHoldingSpeed) return
        totalDragDeltaY += dragAmount
        currentLevel =
            if (dragZone == GestureZone.LEFT) {
                updateBrightness(window, dragStartLevel, totalDragDeltaY, height)
            } else {
                updateVolume(audioManager, dragStartLevel, totalDragDeltaY, height)
            }
    }

    fun onDragFinish() {
        if (isDragAllowed) {
            isDragAllowed = false
            pillDismissJob?.cancel()
            pillDismissJob =
                coroutineScope.launch {
                    delay(PILL_DISMISS_DELAY_MS)
                    isPillVisible = false
                }
        }
    }

    fun onDoubleTap(
        offsetX: Float,
        offsetY: Float,
        width: Float,
        stepMs: Long,
    ) {
        if (isHoldingSpeed) return
        val zone = zoneOf(offsetX, width)
        val direction = if (zone == GestureZone.LEFT) -1 else 1
        val now = clock()
        val totalDelta =
            accumulatedSeek(
                prevDeltaMs = seekChipState.deltaMs,
                prevTapMs = lastTapMs,
                nowMs = now,
                direction = direction,
                stepMs = stepMs,
            )
        val isRestarted =
            (now - lastTapMs > CHIP_DISMISS_DELAY_MS) ||
                (seekChipState.deltaMs != 0L && (seekChipState.deltaMs > 0 != direction > 0))
        if (isRestarted) {
            alreadyAppliedDelta = 0L
        }

        val deltaToSend = totalDelta - alreadyAppliedDelta
        alreadyAppliedDelta = totalDelta
        lastTapMs = now

        seekChipZone = zone
        seekTapPoint = Offset(offsetX, offsetY)
        seekTriggerCount++
        seekChipState = seekChipState.onTap(totalDelta)
        onSeekBy(deltaToSend)

        chipDismissJob?.cancel()
        chipDismissJob =
            coroutineScope.launch {
                delay(CHIP_DISMISS_DELAY_MS)
                seekChipState = seekChipState.onTimeout()
                alreadyAppliedDelta = 0L
            }
    }
}

private fun getInitialBrightness(
    context: Context,
    window: Window?,
): Float {
    val currentWindowBrightness = window?.attributes?.screenBrightness ?: -1f
    return if (currentWindowBrightness >= 0f) {
        currentWindowBrightness
    } else {
        try {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / BRIGHTNESS_SCALE
        } catch (_: Settings.SettingNotFoundException) {
            DEFAULT_BRIGHTNESS_FALLBACK
        } catch (_: SecurityException) {
            DEFAULT_BRIGHTNESS_FALLBACK
        }
    }
}

private fun getInitialVolume(audioManager: AudioManager): Float {
    val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
    return if (maxVol > 0) curVol.toFloat() / maxVol.toFloat() else 0f
}

private fun updateBrightness(
    window: Window?,
    startLevel: Float,
    dragDeltaY: Float,
    height: Float,
): Float {
    val newLevel =
        adjustLevel(
            startLevel = startLevel,
            dragDeltaPx = dragDeltaY,
            heightPx = height,
            minLevel = MIN_BRIGHTNESS,
        )
    window?.let { w ->
        val lp = w.attributes
        lp.screenBrightness = newLevel
        w.attributes = lp
    }
    return newLevel
}

private fun updateVolume(
    audioManager: AudioManager,
    startLevel: Float,
    dragDeltaY: Float,
    height: Float,
): Float {
    val newLevel =
        adjustLevel(
            startLevel = startLevel,
            dragDeltaPx = dragDeltaY,
            heightPx = height,
            minLevel = 0f,
        )
    val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    val steps = volumeSteps(newLevel, maxVol)
    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, steps, 0)
    return newLevel
}

@Composable
private fun BoxScope.HoldSpeedPillOverlay(visible: Boolean) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier =
            Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(top = 24.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = NexusTheme.shapes.card,
                color = PlayerOverlayColors.Card,
                modifier = Modifier.width(HOLD_PILL_WIDTH).height(HOLD_PILL_HEIGHT),
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "2x",
                        style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                        color = PlayerOverlayColors.Content,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        painter = painterResource(R.drawable.ic_seek_chevron),
                        contentDescription = null,
                        tint = PlayerOverlayColors.Content,
                        modifier = Modifier.size(12.dp),
                    )
                    Icon(
                        painter = painterResource(R.drawable.ic_seek_chevron),
                        contentDescription = null,
                        tint = PlayerOverlayColors.Content,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun BoxScope.IndicatorPillOverlay(
    visible: Boolean,
    zone: GestureZone?,
    level: Float,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
    ) {
        val gestureZone = zone ?: GestureZone.RIGHT
        val displayZone = indicatorSide(gestureZone)
        val alignment = if (displayZone == GestureZone.LEFT) Alignment.CenterStart else Alignment.CenterEnd

        Box(
            contentAlignment = alignment,
            modifier = Modifier.fillMaxSize(),
        ) {
            Surface(
                shape = NexusTheme.shapes.card,
                color = PlayerOverlayColors.Card,
                modifier = Modifier.width(PILL_WIDTH).height(PILL_HEIGHT),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(NexusTheme.spacing.sm),
                    modifier =
                        Modifier.padding(
                            horizontal = NexusTheme.spacing.sm,
                            vertical = NexusTheme.spacing.md,
                        ),
                ) {
                    if (gestureZone == GestureZone.LEFT) {
                        Icon(
                            painter = painterResource(R.drawable.ic_brightness),
                            contentDescription = stringResource(R.string.player_gesture_brightness),
                            tint = PlayerOverlayColors.Content,
                            modifier = Modifier.size(PILL_ICON_SIZE),
                        )
                    } else {
                        Icon(
                            painter = painterResource(R.drawable.ic_volume),
                            contentDescription = stringResource(R.string.player_gesture_volume),
                            tint = PlayerOverlayColors.Content,
                            modifier = Modifier.size(PILL_ICON_SIZE),
                        )
                    }

                    Box(
                        modifier =
                            Modifier
                                .height(PILL_BAR_HEIGHT)
                                .width(PILL_BAR_WIDTH)
                                .clip(NexusTheme.shapes.card)
                                .background(PlayerOverlayColors.ContentSecondary.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(level.coerceIn(0f, 1f))
                                    .background(PlayerOverlayColors.Content),
                        )
                    }

                    Box(
                        modifier = Modifier.width(PILL_PERCENT_BOX_WIDTH),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "${levelPercent(level)}%",
                            style =
                                MaterialTheme.typography.labelSmall.copy(
                                    fontFeatureSettings = "tnum",
                                ),
                            color = PlayerOverlayColors.Content,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}
