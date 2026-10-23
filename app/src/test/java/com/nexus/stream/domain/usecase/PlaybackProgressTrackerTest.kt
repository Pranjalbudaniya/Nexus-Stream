package com.nexus.stream.domain.usecase

import com.nexus.stream.domain.model.ProgressSample
import com.nexus.stream.domain.model.ProgressTarget
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackProgressTrackerTest {
    private val saveProgress: SaveProgress = mockk(relaxed = true)
    private val currentTime: Long = 1000L
    private val clock: () -> Long = { currentTime }

    private val target =
        ProgressTarget(
            metaId = "m1",
            videoId = "v1",
            type = "movie",
            isMovie = true,
            season = null,
            episode = null,
            name = "Movie",
            poster = null,
            background = null,
        )

    private fun sampleInput(
        positionMs: Long,
        durationMs: Long = 60_000L,
        playing: Boolean = true,
        ended: Boolean = false,
        failed: Boolean = false,
    ) = PlaybackProgressTracker.Input(positionMs, durationMs, playing, ended, failed)

    private fun TestScope.createTrackerScope(): CoroutineScope =
        CoroutineScope(backgroundScope.coroutineContext + SupervisorJob(backgroundScope.coroutineContext.job))

    @Before
    fun setUp() {
        coEvery { saveProgress(any(), any(), any(), any()) } returns SaveOutcome.Saved(watched = false)
    }

    @Test
    fun TR1_playingTwelveSeconds_twoWritesAtFiveAndTenSeconds() =
        runTest {
            val trackerScope = createTrackerScope()
            val tracker = PlaybackProgressTracker(target, saveProgress, trackerScope, clock)
            val inputs = MutableSharedFlow<PlaybackProgressTracker.Input>(replay = 1)
            tracker.attach(inputs)

            inputs.emit(sampleInput(1000L))
            runCurrent()

            // Advance towards 5s, position updates to 5000ms just before the 5s tick
            advanceTimeBy(4_999L)
            inputs.emit(sampleInput(5000L))
            runCurrent()
            advanceTimeBy(1L)
            runCurrent()

            // Advance towards 10s, position updates to 10000ms just before the 10s tick
            advanceTimeBy(4_999L)
            inputs.emit(sampleInput(10000L))
            runCurrent()
            advanceTimeBy(1L)
            runCurrent()

            // At 12 s, advance 2 s
            advanceTimeBy(2_000L)
            runCurrent()

            val s5 = ProgressSample(positionMs = 5000L, durationMs = 60_000L, ended = false)
            val s10 = ProgressSample(positionMs = 10000L, durationMs = 60_000L, ended = false)
            coVerify(exactly = 1) { saveProgress(target, s5, any(), any()) }
            coVerify(exactly = 1) { saveProgress(target, s10, any(), any()) }
            coVerify(exactly = 2) { saveProgress(any(), any(), any(), any()) }
        }

    @Test
    fun TR2_pauseAtSevenSeconds_writesAtOnce_laterTicksDoNotWrite() =
        runTest {
            val trackerScope = createTrackerScope()
            val tracker = PlaybackProgressTracker(target, saveProgress, trackerScope, clock)
            val inputs = MutableSharedFlow<PlaybackProgressTracker.Input>(replay = 1)
            tracker.attach(inputs)

            // Start playing
            inputs.emit(
                PlaybackProgressTracker.Input(
                    positionMs = 1000L,
                    durationMs = 60_000L,
                    playing = true,
                    ended = false,
                    failed = false,
                ),
            )
            runCurrent()

            // Advance 5 s -> 1st tick write
            advanceTimeBy(5_000L)
            inputs.emit(
                PlaybackProgressTracker.Input(
                    positionMs = 5000L,
                    durationMs = 60_000L,
                    playing = true,
                    ended = false,
                    failed = false,
                ),
            )
            runCurrent()

            // At 7 s, pause
            advanceTimeBy(2_000L)
            inputs.emit(
                PlaybackProgressTracker.Input(
                    positionMs = 7000L,
                    durationMs = 60_000L,
                    playing = false,
                    ended = false,
                    failed = false,
                ),
            )
            runCurrent()

            // Pause triggered immediate write of 7000 ms
            coVerify(exactly = 1) {
                saveProgress(
                    target,
                    ProgressSample(positionMs = 7000L, durationMs = 60_000L, ended = false),
                    any(),
                    any(),
                )
            }

            // Advance another 10 s while paused -> no additional writes
            advanceTimeBy(10_000L)
            runCurrent()

            coVerify(exactly = 2) { saveProgress(any(), any(), any(), any()) }
        }

    @Test
    fun TR3_samePositionAtTwoTicks_onlyOneWrite() =
        runTest {
            val trackerScope = createTrackerScope()
            val tracker = PlaybackProgressTracker(target, saveProgress, trackerScope, clock)
            val inputs = MutableSharedFlow<PlaybackProgressTracker.Input>(replay = 1)
            tracker.attach(inputs)

            inputs.emit(
                PlaybackProgressTracker.Input(
                    positionMs = 5000L,
                    durationMs = 60_000L,
                    playing = true,
                    ended = false,
                    failed = false,
                ),
            )
            runCurrent()

            advanceTimeBy(5_000L)
            runCurrent()
            coVerify(exactly = 1) { saveProgress(any(), any(), any(), any()) }

            // Position did not advance
            advanceTimeBy(5_000L)
            runCurrent()
            coVerify(exactly = 1) { saveProgress(any(), any(), any(), any()) }
        }

    @Test
    fun TR4_flushAfterInputsCancelled_oneFinalWrite_scopeCancelled_secondFlushNoWrite() =
        runTest {
            val trackerScope = createTrackerScope()
            val tracker = PlaybackProgressTracker(target, saveProgress, trackerScope, clock)
            val inputs = MutableSharedFlow<PlaybackProgressTracker.Input>(replay = 1)
            tracker.attach(inputs)

            inputs.emit(
                PlaybackProgressTracker.Input(
                    positionMs = 8000L,
                    durationMs = 60_000L,
                    playing = true,
                    ended = false,
                    failed = false,
                ),
            )
            runCurrent()

            tracker.flush()
            runCurrent()

            coVerify(exactly = 1) {
                saveProgress(
                    target,
                    ProgressSample(positionMs = 8000L, durationMs = 60_000L, ended = false),
                    any(),
                    any(),
                )
            }
            assertFalse(trackerScope.isActive)

            tracker.flush()
            runCurrent()
            coVerify(exactly = 1) { saveProgress(any(), any(), any(), any()) }
        }

    @Test
    fun TR5_endedTrue_writesWithEndedTrue_laterTicksNoWrites() =
        runTest {
            val trackerScope = createTrackerScope()
            val tracker = PlaybackProgressTracker(target, saveProgress, trackerScope, clock)
            val inputs = MutableSharedFlow<PlaybackProgressTracker.Input>(replay = 1)
            tracker.attach(inputs)

            inputs.emit(
                PlaybackProgressTracker.Input(
                    positionMs = 60_000L,
                    durationMs = 60_000L,
                    playing = true,
                    ended = true,
                    failed = false,
                ),
            )
            runCurrent()

            coVerify(exactly = 1) {
                saveProgress(
                    target,
                    ProgressSample(positionMs = 60_000L, durationMs = 60_000L, ended = true),
                    any(),
                    any(),
                )
            }

            advanceTimeBy(20_000L)
            runCurrent()
            coVerify(exactly = 1) { saveProgress(any(), any(), any(), any()) }
        }

    @Test
    fun TR6_failedTrue_thenZeroAndZeroInput_oneWriteOfLastGoodInput() =
        runTest {
            val trackerScope = createTrackerScope()
            val tracker = PlaybackProgressTracker(target, saveProgress, trackerScope, clock)
            val inputs = MutableSharedFlow<PlaybackProgressTracker.Input>(replay = 1)
            tracker.attach(inputs)

            inputs.emit(
                PlaybackProgressTracker.Input(
                    positionMs = 8000L,
                    durationMs = 60_000L,
                    playing = true,
                    ended = false,
                    failed = false,
                ),
            )
            runCurrent()

            inputs.emit(
                PlaybackProgressTracker.Input(
                    positionMs = 8000L,
                    durationMs = 60_000L,
                    playing = false,
                    ended = false,
                    failed = true,
                ),
            )
            runCurrent()

            // Released controller inputs of 0, 0
            inputs.emit(
                PlaybackProgressTracker.Input(
                    positionMs = 0L,
                    durationMs = 0L,
                    playing = false,
                    ended = false,
                    failed = false,
                ),
            )
            runCurrent()

            coVerify(exactly = 1) {
                saveProgress(
                    target,
                    ProgressSample(positionMs = 8000L, durationMs = 60_000L, ended = false),
                    any(),
                    any(),
                )
            }
        }

    @Test
    fun TR7_inputsWithZeroDurationAfterGood_ignored_flushWritesLastGood() =
        runTest {
            val trackerScope = createTrackerScope()
            val tracker = PlaybackProgressTracker(target, saveProgress, trackerScope, clock)
            val inputs = MutableSharedFlow<PlaybackProgressTracker.Input>(replay = 1)
            tracker.attach(inputs)

            inputs.emit(
                PlaybackProgressTracker.Input(
                    positionMs = 8000L,
                    durationMs = 60_000L,
                    playing = true,
                    ended = false,
                    failed = false,
                ),
            )
            runCurrent()

            inputs.emit(
                PlaybackProgressTracker.Input(
                    positionMs = 0L,
                    durationMs = 0L,
                    playing = false,
                    ended = false,
                    failed = false,
                ),
            )
            runCurrent()

            tracker.flush()
            runCurrent()

            coVerify(exactly = 1) {
                saveProgress(
                    target,
                    ProgressSample(positionMs = 8000L, durationMs = 60_000L, ended = false),
                    any(),
                    any(),
                )
            }
        }

    @Test
    fun TR8_saveProgressThrowsIOExceptionOnce_nextTriggerRetriesAndFirstOfSessionTrue() =
        runTest {
            coEvery { saveProgress(any(), any(), true, any()) } throws IOException("IO failure") andThen
                SaveOutcome.Saved(watched = false)

            val trackerScope = createTrackerScope()
            val tracker = PlaybackProgressTracker(target, saveProgress, trackerScope, clock)
            val inputs = MutableSharedFlow<PlaybackProgressTracker.Input>(replay = 1)
            tracker.attach(inputs)

            inputs.emit(
                PlaybackProgressTracker.Input(
                    positionMs = 5000L,
                    durationMs = 60_000L,
                    playing = true,
                    ended = false,
                    failed = false,
                ),
            )
            runCurrent()

            // First tick: throws IOException, swallowed
            advanceTimeBy(5_000L)
            runCurrent()

            // Second tick: position advanced, retries with firstOfSession = true
            inputs.emit(
                PlaybackProgressTracker.Input(
                    positionMs = 10000L,
                    durationMs = 60_000L,
                    playing = true,
                    ended = false,
                    failed = false,
                ),
            )
            advanceTimeBy(5_000L)
            runCurrent()

            coVerify(exactly = 1) {
                saveProgress(
                    target,
                    ProgressSample(positionMs = 5000L, durationMs = 60_000L, ended = false),
                    true,
                    any(),
                )
            }
            coVerify(exactly = 1) {
                saveProgress(
                    target,
                    ProgressSample(positionMs = 10000L, durationMs = 60_000L, ended = false),
                    true,
                    any(),
                )
            }
        }

    @Test
    fun TR9_firstSkipped_thenSaved_thenAnotherWrite_firstOfSessionSequence() =
        runTest {
            coEvery { saveProgress(any(), any(), any(), any()) } returnsMany
                listOf(SaveOutcome.Skipped, SaveOutcome.Saved(watched = false), SaveOutcome.Saved(watched = false))

            val trackerScope = createTrackerScope()
            val tracker = PlaybackProgressTracker(target, saveProgress, trackerScope, clock)
            val inputs = MutableSharedFlow<PlaybackProgressTracker.Input>(replay = 1)
            tracker.attach(inputs)

            // 1st write: Skipped -> firstOfSession stays true
            inputs.emit(sampleInput(5000L))
            advanceTimeBy(5_000L)
            runCurrent()

            // 2nd write: Saved -> firstOfSession was true, now false
            inputs.emit(sampleInput(10000L))
            advanceTimeBy(5_000L)
            runCurrent()

            // 3rd write: Saved -> firstOfSession is false
            inputs.emit(sampleInput(15000L))
            advanceTimeBy(5_000L)
            runCurrent()

            val s5 = ProgressSample(positionMs = 5000L, durationMs = 60_000L, ended = false)
            val s10 = ProgressSample(positionMs = 10000L, durationMs = 60_000L, ended = false)
            val s15 = ProgressSample(positionMs = 15000L, durationMs = 60_000L, ended = false)
            coVerify(exactly = 1) { saveProgress(target, s5, true, any()) }
            coVerify(exactly = 1) { saveProgress(target, s10, true, any()) }
            coVerify(exactly = 1) { saveProgress(target, s15, false, any()) }
        }

    @Test
    fun TR10_simultaneousTriggers_writesNeverOverlap() =
        runTest {
            val maxConcurrency = AtomicInteger(0)
            val activeCount = AtomicInteger(0)

            coEvery { saveProgress(any(), any(), any(), any()) } coAnswers {
                val current = activeCount.incrementAndGet()
                maxConcurrency.updateAndGet { prev -> maxOf(prev, current) }
                delay(50L)
                activeCount.decrementAndGet()
                SaveOutcome.Saved(watched = false)
            }

            val trackerScope = createTrackerScope()
            val tracker = PlaybackProgressTracker(target, saveProgress, trackerScope, clock)
            val inputs = MutableSharedFlow<PlaybackProgressTracker.Input>(replay = 1)
            tracker.attach(inputs)

            inputs.emit(sampleInput(5000L))
            advanceTimeBy(5_000L)
            // Trigger pause at the exact same moment
            inputs.emit(sampleInput(positionMs = 7000L, playing = false))
            advanceTimeBy(100L)
            runCurrent()

            assertEquals(1, maxConcurrency.get())
        }

    @Test
    fun TR11_saveProgressThrowsCancellationException_rethrows() =
        runTest {
            coEvery { saveProgress(any(), any(), any(), any()) } throws CancellationException("Job cancelled")

            val trackerScope = createTrackerScope()
            val tracker = PlaybackProgressTracker(target, saveProgress, trackerScope, clock)
            val inputs = MutableSharedFlow<PlaybackProgressTracker.Input>(replay = 1)
            tracker.attach(inputs)

            inputs.emit(
                PlaybackProgressTracker.Input(
                    positionMs = 5000L,
                    durationMs = 60_000L,
                    playing = true,
                    ended = false,
                    failed = false,
                ),
            )
            advanceTimeBy(5_000L)
            runCurrent()

            coVerify(exactly = 1) { saveProgress(any(), any(), any(), any()) }
        }

    @Test
    fun TR12_attachTwice_firstCancelled_noDuplicateWriteForSamePosition() =
        runTest {
            val trackerScope = createTrackerScope()
            val tracker = PlaybackProgressTracker(target, saveProgress, trackerScope, clock)
            val inputs1 = MutableSharedFlow<PlaybackProgressTracker.Input>(replay = 1)
            val inputs2 = MutableSharedFlow<PlaybackProgressTracker.Input>(replay = 1)

            tracker.attach(inputs1)
            inputs1.emit(
                PlaybackProgressTracker.Input(
                    positionMs = 5000L,
                    durationMs = 60_000L,
                    playing = true,
                    ended = false,
                    failed = false,
                ),
            )
            advanceTimeBy(5_000L)
            runCurrent()
            coVerify(exactly = 1) { saveProgress(any(), any(), any(), any()) }

            // Reopen / second attach
            tracker.attach(inputs2)
            // Emit from old flow should be ignored/cancelled
            inputs1.emit(
                PlaybackProgressTracker.Input(
                    positionMs = 6000L,
                    durationMs = 60_000L,
                    playing = true,
                    ended = false,
                    failed = false,
                ),
            )
            runCurrent()

            // Emit same position 5000 from new flow -> no duplicate write
            inputs2.emit(
                PlaybackProgressTracker.Input(
                    positionMs = 5000L,
                    durationMs = 60_000L,
                    playing = true,
                    ended = false,
                    failed = false,
                ),
            )
            advanceTimeBy(5_000L)
            runCurrent()

            coVerify(exactly = 1) { saveProgress(any(), any(), any(), any()) }
        }
}
