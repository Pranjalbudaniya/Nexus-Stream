package com.nexus.stream.presentation.feature.player

import com.nexus.stream.domain.model.ParsedStream
import com.nexus.stream.domain.model.SourceFingerprint
import com.nexus.stream.domain.repository.PlayerPrefsRepository
import com.nexus.stream.domain.repository.SourceMemoryRepository
import com.nexus.stream.domain.usecase.NextEpisode
import com.nexus.stream.domain.usecase.NextEpisodeFinder
import com.nexus.stream.domain.usecase.NextPlanInput
import com.nexus.stream.domain.usecase.NextUi
import com.nexus.stream.domain.usecase.PrefetchState
import com.nexus.stream.domain.usecase.SameSourceFinder
import com.nexus.stream.domain.usecase.SameSourceReason
import com.nexus.stream.domain.usecase.SameSourceResult
import com.nexus.stream.domain.usecase.StartPlayback
import com.nexus.stream.domain.usecase.StartResult
import com.nexus.stream.domain.usecase.nextUi
import com.nexus.stream.domain.usecase.progressKeyFor
import com.nexus.stream.domain.usecase.remainingMs
import com.nexus.stream.domain.usecase.shouldPrefetch
import com.nexus.stream.domain.usecase.shouldRearm
import com.nexus.stream.presentation.common.PlaybackRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private const val COUNTDOWN_START_SECONDS = 5
private const val COUNTDOWN_END_SECONDS = 1
private const val COUNTDOWN_TICK_MS = 1_000L

data class NextEpisodeState(
    val nextUi: NextUi = NextUi.None,
    val nextEpisodeLabel: Pair<Int, Int>? = null,
    val autoPlayCountdownSeconds: Int? = null,
) {
    override fun toString(): String =
        "NextEpisodeState(nextUi=$nextUi, nextEpisodeLabel=$nextEpisodeLabel, countdown=$autoPlayCountdownSeconds)"
}

sealed interface NextEpisodeEffect {
    data object SwitchToNext : NextEpisodeEffect

    data class ChooseNextSource(
        val request: PlaybackRequest,
    ) : NextEpisodeEffect
}

@Suppress("TooManyFunctions")
open class NextEpisodeController(
    private val finder: NextEpisodeFinder,
    private val sameSource: SameSourceFinder,
    private val sourceMemory: SourceMemoryRepository,
    private val startPlayback: StartPlayback,
    private val autoPlayNext: Flow<Boolean>,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val _state = MutableStateFlow(NextEpisodeState())
    open val state: StateFlow<NextEpisodeState> = _state.asStateFlow()

    private val _effects = Channel<NextEpisodeEffect>(Channel.BUFFERED)
    open val effects: Flow<NextEpisodeEffect> = _effects.receiveAsFlow()

    private var currentSelection: PlaybackSelection? = null
    private var hasNext: Boolean = false
    private var nextEpisode: NextEpisode? = null
    private var prefetch: PrefetchState = PrefetchState.IDLE
    private var foundStream: ParsedStream? = null
    private var dismissed: Boolean = false
    private var wantSwitch: Boolean = false
    private var countdownJob: Job? = null
    private var switched: Boolean = false
    private var isAutoPlayEnabled: Boolean = true
    private var prefetchJob: Job? = null
    private var resolveJob: Job? = null
    private var playbackStartedSeen: Boolean = false

    private var lastDurationMs: Long = 0L
    private var lastPositionMs: Long = 0L
    private var lastEnded: Boolean = false

    init {
        scope.launch {
            autoPlayNext.collect { isAutoPlayEnabled = it }
        }
    }

    open fun onPlaybackStarted(selection: PlaybackSelection) {
        if (playbackStartedSeen) return
        playbackStartedSeen = true
        currentSelection = selection

        if (selection.request == null || selection.type.equals("movie", ignoreCase = true)) {
            hasNext = false
            nextEpisode = null
            updateState()
            return
        }

        resolveJob =
            scope.launch {
                val next =
                    try {
                        finder.find(selection.type, selection.metaId, selection.videoId)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        null
                    }
                if (next != null) {
                    hasNext = true
                    nextEpisode = next
                } else {
                    hasNext = false
                    nextEpisode = null
                }
                updateState()
            }
    }

    open fun onSnapshot(
        durationMs: Long,
        positionMs: Long,
        ended: Boolean,
    ) {
        lastDurationMs = durationMs
        lastPositionMs = positionMs
        lastEnded = ended

        val remaining = remainingMs(durationMs, positionMs)
        if (shouldRearm(dismissed, remaining)) {
            dismissed = false
        }

        val input = createPlanInput()
        if (shouldPrefetch(input)) {
            prefetch = PrefetchState.SEARCHING
            prefetchJob =
                scope.launch {
                    executePrefetch()
                }
        }

        val ui = nextUi(createPlanInput())
        if (ui is NextUi.Ready && shouldStartCountdown(ended)) {
            startCountdown()
        }

        updateState()
    }

    private fun shouldStartCountdown(ended: Boolean): Boolean =
        ended && isAutoPlayEnabled && !dismissed && countdownJob == null && !switched

    private suspend fun executePrefetch() {
        val sel = currentSelection
        val next = nextEpisode
        if (sel == null || next == null) {
            prefetch = PrefetchState.NOT_FOUND
            updateState()
            return
        }
        val currentKey = progressKeyFor(sel.type, sel.metaId, sel.videoId)
        val fingerprint = sel.sourceFingerprint ?: sourceMemory.get(currentKey)
        val nextReq = buildNextRequest(sel, next)
        if (fingerprint == null) {
            handlePrefetchNotFound(nextReq)
        } else {
            handlePrefetchWithFingerprint(nextReq, fingerprint)
        }
        updateState()
    }

    private suspend fun handlePrefetchWithFingerprint(
        nextReq: PlaybackRequest,
        fingerprint: SourceFingerprint,
    ) {
        val result =
            try {
                sameSource.find(nextReq, fingerprint)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                SameSourceResult.NotFound(SameSourceReason.LOAD_FAILED)
            }
        when (result) {
            is SameSourceResult.Found -> handlePrefetchFound(result)
            is SameSourceResult.NotFound -> handlePrefetchNotFound(nextReq)
        }
    }

    private fun handlePrefetchFound(result: SameSourceResult.Found) {
        prefetch = PrefetchState.FOUND
        foundStream = result.stream
        if (wantSwitch) {
            doSwitch()
        } else if (shouldStartCountdown(lastEnded)) {
            startCountdown()
        }
    }

    private fun handlePrefetchNotFound(nextReq: PlaybackRequest) {
        prefetch = PrefetchState.NOT_FOUND
        if (wantSwitch) {
            emitEffect(NextEpisodeEffect.ChooseNextSource(nextReq))
        }
    }

    private fun startCountdown() {
        countdownJob =
            scope.launch {
                for (sec in COUNTDOWN_START_SECONDS downTo COUNTDOWN_END_SECONDS) {
                    _state.value = _state.value.copy(autoPlayCountdownSeconds = sec)
                    delay(COUNTDOWN_TICK_MS)
                }
                _state.value = _state.value.copy(autoPlayCountdownSeconds = 0)
                if (!dismissed) {
                    doSwitch()
                }
            }
    }

    open fun onNextTapped() {
        val sel = currentSelection ?: return
        val next = nextEpisode ?: return
        val nextReq = buildNextRequest(sel, next)
        when (prefetch) {
            PrefetchState.FOUND -> {
                doSwitch()
            }

            PrefetchState.IDLE,
            PrefetchState.SEARCHING,
            -> {
                wantSwitch = true
            }

            PrefetchState.NOT_FOUND -> {
                emitEffect(NextEpisodeEffect.ChooseNextSource(nextReq))
            }
        }
    }

    open fun onDismiss() {
        dismissed = true
        countdownJob?.cancel()
        countdownJob = null
        updateState()
    }

    private fun doSwitch() {
        val sel = currentSelection
        val next = nextEpisode
        if (switched || sel == null || next == null) return
        switched = true
        countdownJob?.cancel()
        countdownJob = null
        val found = foundStream
        val nextReq = buildNextRequest(sel, next)
        val effect =
            if (found != null) {
                when (startPlayback(nextReq, found)) {
                    is StartResult.Started -> NextEpisodeEffect.SwitchToNext
                    else -> NextEpisodeEffect.ChooseNextSource(nextReq)
                }
            } else {
                NextEpisodeEffect.ChooseNextSource(nextReq)
            }
        emitEffect(effect)
        updateState()
    }

    private fun emitEffect(effect: NextEpisodeEffect) {
        scope.launch {
            _effects.send(effect)
        }
    }

    open fun clear() {
        countdownJob?.cancel()
        countdownJob = null
        prefetchJob?.cancel()
        prefetchJob = null
        resolveJob?.cancel()
        resolveJob = null
    }

    private fun createPlanInput() =
        NextPlanInput(
            hasNext = hasNext,
            durationMs = lastDurationMs,
            positionMs = lastPositionMs,
            ended = lastEnded,
            dismissed = dismissed,
            prefetch = prefetch,
        )

    private fun updateState() {
        val ui = nextUi(createPlanInput())
        val label =
            nextEpisode?.let {
                if (it.season != null && it.episode != null) Pair(it.season, it.episode) else null
            }
        val countdown = if (dismissed || ui !is NextUi.Ready) null else _state.value.autoPlayCountdownSeconds
        _state.value =
            NextEpisodeState(
                nextUi = ui,
                nextEpisodeLabel = label,
                autoPlayCountdownSeconds = countdown,
            )
    }

    private fun buildNextRequest(
        sel: PlaybackSelection,
        next: NextEpisode,
    ): PlaybackRequest {
        val req = sel.request
        val baseTitle = req?.metaName ?: sel.metaName ?: req?.title ?: sel.title
        val episodeTitle = next.episodeTitle
        val title =
            if (!episodeTitle.isNullOrBlank()) {
                "$baseTitle - $episodeTitle"
            } else {
                baseTitle
            }
        return (
            req ?: PlaybackRequest(
                type = sel.type,
                metaId = sel.metaId,
                videoId = next.videoId,
                title = title,
                season = next.season,
                episode = next.episode,
                logoUrl = sel.logoUrl,
                backgroundUrl = sel.backgroundUrl,
                posterUrl = sel.posterUrl,
                metaName = sel.metaName,
            )
        ).copy(
            videoId = next.videoId,
            season = next.season,
            episode = next.episode,
            title = title,
            autoResume = false,
            sourceHintKey = progressKeyFor(sel.type, sel.metaId, sel.videoId),
        )
    }

    @Singleton
    open class Factory
        @Inject
        constructor(
            private val finder: NextEpisodeFinder,
            private val sameSource: SameSourceFinder,
            private val sourceMemory: SourceMemoryRepository,
            private val startPlayback: StartPlayback,
            private val playerPrefsRepository: PlayerPrefsRepository,
        ) {
            open fun create(scope: CoroutineScope): NextEpisodeController =
                NextEpisodeController(
                    finder = finder,
                    sameSource = sameSource,
                    sourceMemory = sourceMemory,
                    startPlayback = startPlayback,
                    autoPlayNext = playerPrefsRepository.autoPlayNext,
                    scope = scope,
                )
        }
}
