package com.nexus.stream.presentation.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexus.stream.data.player.PlayerController
import com.nexus.stream.data.player.PlayerControllerFactory
import com.nexus.stream.di.MainDispatcher
import com.nexus.stream.domain.model.AppError
import com.nexus.stream.domain.model.PlaybackSource
import com.nexus.stream.domain.model.TorrentFailReason
import com.nexus.stream.domain.model.TorrentRequest
import com.nexus.stream.domain.model.TorrentState
import com.nexus.stream.domain.repository.ProxySession
import com.nexus.stream.domain.repository.StreamProxy
import com.nexus.stream.domain.repository.TorrentEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val CONTROLS_HIDE_DELAY_MS = 3_000L

sealed interface PlayerPhase {
    data object Empty : PlayerPhase

    data class Preparing(
        val label: PreparingLabel,
        val percent: Int? = null,
        val peers: Int? = null,
    ) : PlayerPhase

    data class PickFile(
        val files: List<TorrentFileUi>,
    ) : PlayerPhase

    data object Playing : PlayerPhase

    data object Ended : PlayerPhase

    data class Error(
        val kind: PlayerError,
    ) : PlayerPhase
}

enum class PreparingLabel {
    OPENING,
    FETCHING_METADATA,
    BUFFERING,
}

enum class PlayerError {
    UNPLAYABLE,
    PLAYBACK_FAILED,
    TORRENT_NO_VIDEO,
    TORRENT_TIMEOUT,
    TORRENT_STORAGE,
    TORRENT_UNAVAILABLE,
    METERED_BLOCKED,
    OTHER,
}

data class TorrentFileUi(
    val index: Int,
    val name: String,
    val sizeBytes: Long,
)

data class PlayerUiState(
    val title: String = "",
    val phase: PlayerPhase = PlayerPhase.Empty,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val isSeekable: Boolean = false,
    val controlsVisible: Boolean = true,
) {
    override fun toString(): String =
        "PlayerUiState(title=$title, phase=$phase, pos=$positionMs, dur=$durationMs, " +
            "playing=$isPlaying, buffering=$isBuffering, seekable=$isSeekable, controls=$controlsVisible)"
}

sealed interface PlayerEvent {
    data object TogglePlay : PlayerEvent

    data class SeekTo(
        val ms: Long,
    ) : PlayerEvent

    data object ToggleControls : PlayerEvent

    data object AppStopped : PlayerEvent

    data class PickFile(
        val index: Int,
    ) : PlayerEvent

    data object AllowMeteredOnce : PlayerEvent

    data object Retry : PlayerEvent
}

@HiltViewModel
@Suppress("TooManyFunctions")
class PlayerViewModel
    @Inject
    constructor(
        private val holder: PlaybackSessionHolder,
        private val controllerFactory: PlayerControllerFactory,
        private val streamProxy: StreamProxy,
        private val torrentEngine: TorrentEngine,
        @MainDispatcher private val mainDispatcher: CoroutineDispatcher,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(PlayerUiState())
        val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

        private var controller: PlayerController? = null
        private var proxySession: ProxySession? = null
        private var torrentJob: Job? = null
        private var controlsTimerJob: Job? = null
        private var activeTorrentRequest: TorrentRequest? = null
        private var playedOnce = false

        init {
            val selection = holder.current.value
            if (selection == null) {
                _uiState.update { it.copy(phase = PlayerPhase.Empty) }
            } else {
                _uiState.update { it.copy(title = selection.title) }
                startPlayback(selection)
            }
        }

        fun getController(): PlayerController? = controller

        fun onEvent(event: PlayerEvent) {
            when (event) {
                PlayerEvent.TogglePlay -> handleTogglePlay()
                is PlayerEvent.SeekTo -> handleSeekTo(event.ms)
                PlayerEvent.ToggleControls -> handleToggleControls()
                PlayerEvent.AppStopped -> handleAppStopped()
                is PlayerEvent.PickFile -> handlePickFile(event.index)
                PlayerEvent.AllowMeteredOnce -> handleAllowMeteredOnce()
                PlayerEvent.Retry -> handleRetry()
            }
        }

        private fun handleTogglePlay() {
            controller?.let { ctrl ->
                if (ctrl.snapshot.value.isPlaying) {
                    ctrl.pause()
                } else {
                    ctrl.resume()
                }
            }
        }

        private fun handleSeekTo(ms: Long) {
            controller?.let { ctrl ->
                if (ctrl.snapshot.value.isSeekable) {
                    ctrl.seekTo(ms)
                }
            }
        }

        private fun handleToggleControls() {
            val newVisible = !_uiState.value.controlsVisible
            _uiState.update { it.copy(controlsVisible = newVisible) }
            if (newVisible) {
                handleControlsTimer()
            } else {
                controlsTimerJob?.cancel()
            }
        }

        private fun handleAppStopped() {
            controller?.let { ctrl ->
                if (ctrl.snapshot.value.isPlaying) {
                    ctrl.pause()
                }
            }
        }

        private fun handlePickFile(index: Int) {
            activeTorrentRequest?.let { req ->
                viewModelScope.launch(mainDispatcher) {
                    torrentEngine.selectFile(req.infoHash, index)
                }
            }
        }

        private fun handleAllowMeteredOnce() {
            activeTorrentRequest?.let { req ->
                val allowedReq = req.copy(allowMetered = true)
                activeTorrentRequest = allowedReq
                startTorrentPlayback(allowedReq)
            }
        }

        private fun handleRetry() {
            holder.current.value?.let { selection ->
                playedOnce = false
                startPlayback(selection)
            }
        }

        private fun startPlayback(selection: PlaybackSelection) {
            when (val source = selection.source) {
                is PlaybackSource.Direct -> {
                    val ctrl = controllerFactory.create()
                    controller = ctrl
                    observeSnapshot(ctrl)
                    _uiState.update { it.copy(phase = PlayerPhase.Preparing(PreparingLabel.OPENING)) }
                    ctrl.play(source.url)
                }

                is PlaybackSource.DirectWithHeaders -> {
                    try {
                        val session = streamProxy.open(source.url, source.headers)
                        proxySession = session
                        val ctrl = controllerFactory.create()
                        controller = ctrl
                        observeSnapshot(ctrl)
                        _uiState.update { it.copy(phase = PlayerPhase.Preparing(PreparingLabel.OPENING)) }
                        ctrl.play(session.url)
                    } catch (_: IllegalArgumentException) {
                        _uiState.update { it.copy(phase = PlayerPhase.Error(PlayerError.UNPLAYABLE)) }
                    }
                }

                is PlaybackSource.Torrent -> {
                    activeTorrentRequest = source.request
                    startTorrentPlayback(source.request)
                }

                is PlaybackSource.External, is PlaybackSource.Unsupported -> {
                    _uiState.update { it.copy(phase = PlayerPhase.Error(PlayerError.UNPLAYABLE)) }
                }
            }
        }

        private fun startTorrentPlayback(request: TorrentRequest) {
            torrentJob?.cancel()
            _uiState.update { it.copy(phase = PlayerPhase.Preparing(PreparingLabel.FETCHING_METADATA)) }
            torrentJob =
                viewModelScope.launch(mainDispatcher) {
                    torrentEngine.prepare(request).collect { state ->
                        handleTorrentState(state)
                    }
                }
        }

        private fun handleTorrentState(state: TorrentState) {
            when (state) {
                is TorrentState.FetchingMetadata -> {
                    _uiState.update { it.copy(phase = PlayerPhase.Preparing(PreparingLabel.FETCHING_METADATA)) }
                }

                is TorrentState.Buffering -> {
                    _uiState.update {
                        it.copy(
                            phase =
                                PlayerPhase.Preparing(
                                    label = PreparingLabel.BUFFERING,
                                    percent = state.percent,
                                    peers = state.peers,
                                ),
                        )
                    }
                }

                is TorrentState.NeedsFilePick -> {
                    val fileUis = state.files.map { TorrentFileUi(it.index, it.path, it.sizeBytes) }
                    _uiState.update { it.copy(phase = PlayerPhase.PickFile(fileUis)) }
                }

                is TorrentState.Ready -> {
                    handleTorrentReady(state)
                }

                is TorrentState.Failed -> {
                    handleTorrentFailed(state.error)
                }
            }
        }

        private fun handleTorrentReady(state: TorrentState.Ready) {
            val localUrl = state.localUrl
            if (localUrl != null) {
                if (!playedOnce) {
                    playedOnce = true
                    val ctrl =
                        controller ?: controllerFactory.create().also {
                            controller = it
                            observeSnapshot(it)
                        }
                    ctrl.play(localUrl)
                }
            } else {
                _uiState.update { it.copy(phase = PlayerPhase.Error(PlayerError.OTHER)) }
            }
        }

        private fun handleTorrentFailed(error: AppError) {
            val errorKind = mapTorrentError(error)
            _uiState.update { it.copy(phase = PlayerPhase.Error(errorKind)) }
        }

        private fun mapTorrentError(error: AppError): PlayerError =
            when (error) {
                is AppError.Torrent -> {
                    when (error.reason) {
                        TorrentFailReason.NO_VIDEO_FILE -> PlayerError.TORRENT_NO_VIDEO
                        TorrentFailReason.METADATA_TIMEOUT -> PlayerError.TORRENT_TIMEOUT
                        TorrentFailReason.STORAGE -> PlayerError.TORRENT_STORAGE
                        TorrentFailReason.ENGINE_UNAVAILABLE -> PlayerError.TORRENT_UNAVAILABLE
                        else -> PlayerError.OTHER
                    }
                }

                is AppError.MeteredBlocked -> {
                    PlayerError.METERED_BLOCKED
                }

                else -> {
                    PlayerError.OTHER
                }
            }

        private fun observeSnapshot(ctrl: PlayerController) {
            viewModelScope.launch(mainDispatcher) {
                ctrl.snapshot.collect { snap ->
                    _uiState.update { current ->
                        val newPhase =
                            when {
                                snap.failed -> {
                                    PlayerPhase.Error(PlayerError.PLAYBACK_FAILED)
                                }

                                snap.ended -> {
                                    PlayerPhase.Ended
                                }

                                snap.isPlaying || snap.isBuffering -> {
                                    if (current.phase is PlayerPhase.Preparing ||
                                        current.phase is PlayerPhase.PickFile
                                    ) {
                                        PlayerPhase.Playing
                                    } else {
                                        current.phase
                                    }
                                }

                                else -> {
                                    current.phase
                                }
                            }

                        current.copy(
                            positionMs = snap.positionMs,
                            durationMs = snap.durationMs,
                            isPlaying = snap.isPlaying,
                            isBuffering = snap.isBuffering,
                            isSeekable = snap.isSeekable,
                            phase = newPhase,
                        )
                    }
                    handleControlsTimer()
                }
            }
        }

        private fun handleControlsTimer() {
            controlsTimerJob?.cancel()
            val state = _uiState.value
            val shouldAutoFade = state.phase is PlayerPhase.Playing && state.isPlaying && !state.isBuffering

            if (!shouldAutoFade) {
                _uiState.update { it.copy(controlsVisible = true) }
            } else if (state.controlsVisible) {
                controlsTimerJob =
                    viewModelScope.launch(mainDispatcher) {
                        delay(CONTROLS_HIDE_DELAY_MS)
                        _uiState.update { it.copy(controlsVisible = false) }
                    }
            }
        }

        override fun onCleared() {
            super.onCleared()
            controlsTimerJob?.cancel()
            torrentJob?.cancel()
            proxySession?.close()
            proxySession = null
            controller?.release()
            controller = null
            holder.clear()
        }
    }
