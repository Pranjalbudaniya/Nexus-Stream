package com.nexus.stream.domain.usecase

import com.nexus.stream.domain.model.ProgressSample
import com.nexus.stream.domain.model.ProgressTarget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

open class PlaybackProgressTracker(
    private val target: ProgressTarget,
    private val saveProgress: SaveProgress,
    private val scope: CoroutineScope,
    private val clock: () -> Long,
    private val intervalMs: Long = ProgressRules.SAVE_INTERVAL_MS,
) {
    data class Input(
        val positionMs: Long,
        val durationMs: Long,
        val playing: Boolean,
        val ended: Boolean,
        val failed: Boolean,
    )

    private val writeMutex = Mutex()
    private var lastWrittenPositionMs: Long? = null
    private var firstOfSession: Boolean = true
    private var hasEnded: Boolean = false
    private var flushed: Boolean = false

    private var latestInput: Input? = null

    private var collectionJob: Job? = null
    private var tickerJob: Job? = null

    open fun attach(inputs: Flow<Input>) {
        if (flushed || hasEnded) return

        collectionJob?.cancel()

        if (tickerJob == null) {
            tickerJob =
                scope.launch {
                    while (isActive) {
                        delay(intervalMs)
                        val current = latestInput
                        if (current != null && current.playing && !hasEnded) {
                            writeIfChanged(current, isEnded = false)
                        }
                    }
                }
        }

        collectionJob =
            scope.launch {
                inputs.collect { input ->
                    handleInput(input)
                }
            }
    }

    private suspend fun handleInput(input: Input) {
        if (hasEnded || flushed) return

        val isGoodInput = input.durationMs > 0 || input.ended
        if (isGoodInput) {
            val prev = latestInput
            latestInput = input

            if (input.ended) {
                hasEnded = true
                tickerJob?.cancel()
                collectionJob?.cancel()
                writeIfChanged(input, isEnded = true)
            } else if (input.failed) {
                writeIfChanged(input, isEnded = false)
            } else if (prev != null && prev.playing && !input.playing) {
                writeIfChanged(input, isEnded = false)
            }
        } else if (input.failed) {
            latestInput?.let { good ->
                writeIfChanged(good, isEnded = false)
            }
        }
    }

    private suspend fun writeIfChanged(
        input: Input,
        isEnded: Boolean,
    ) {
        writeMutex.withLock {
            if (!isEnded && lastWrittenPositionMs == input.positionMs) {
                return
            }

            val sample =
                ProgressSample(
                    positionMs = input.positionMs,
                    durationMs = input.durationMs,
                    ended = isEnded,
                )

            try {
                when (saveProgress(target, sample, firstOfSession, clock())) {
                    is SaveOutcome.Saved -> {
                        firstOfSession = false
                        lastWrittenPositionMs = input.positionMs
                    }

                    SaveOutcome.Skipped -> {
                        // Nothing changes; firstOfSession stays true
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Swallowed; nothing changes; next trigger retries
            }
        }
    }

    open fun flush() {
        if (flushed) return
        flushed = true

        collectionJob?.cancel()
        tickerJob?.cancel()

        scope.launch {
            try {
                latestInput?.let { input ->
                    if (!hasEnded) {
                        writeIfChanged(input, isEnded = false)
                    }
                }
            } finally {
                scope.cancel()
            }
        }
    }
}
