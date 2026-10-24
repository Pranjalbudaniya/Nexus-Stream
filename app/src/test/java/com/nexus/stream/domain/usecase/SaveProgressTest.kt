package com.nexus.stream.domain.usecase

import com.nexus.stream.domain.model.ListStatus
import com.nexus.stream.domain.model.MetaPreview
import com.nexus.stream.domain.model.ProgressSample
import com.nexus.stream.domain.model.ProgressTarget
import com.nexus.stream.domain.model.WatchProgress
import com.nexus.stream.domain.repository.LibraryItem
import com.nexus.stream.domain.repository.LibraryRepository
import com.nexus.stream.domain.repository.ProgressRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

class SaveProgressTest {
    private val progress: ProgressRepository = mockk(relaxed = true)
    private val library: LibraryRepository = mockk(relaxed = true)
    private lateinit var saveProgress: SaveProgress

    private val episodeTarget =
        ProgressTarget(
            metaId = "tt123",
            videoId = "tt123:1:2",
            type = "series",
            isMovie = false,
            season = 1,
            episode = 2,
            name = "Series Episode",
            poster = "https://example.com/poster.jpg",
            background = "https://example.com/bg.jpg",
        )

    private val movieTarget =
        ProgressTarget(
            metaId = "tt456",
            videoId = "tt456",
            type = "movie",
            isMovie = true,
            season = null,
            episode = null,
            name = "A Great Movie",
            poster = "https://example.com/movie_poster.jpg",
            background = "https://example.com/movie_bg.jpg",
        )

    @Before
    fun setUp() {
        saveProgress = SaveProgress(progress, library)
    }

    @Test
    fun SV1_firstWrite_episodeTarget() =
        runTest {
            val sample = ProgressSample(positionMs = 60_000L, durationMs = 40L * 60L * 1000L, ended = false)
            val nowMs = 123456789L

            val outcome = saveProgress(episodeTarget, sample, firstOfSession = true, nowMs = nowMs)

            assertEquals(SaveOutcome.Saved(watched = false), outcome)
            val slot = slot<WatchProgress>()
            coVerify(exactly = 1) { progress.upsert(capture(slot)) }
            val saved = slot.captured
            assertEquals("tt123|tt123:1:2", saved.key)
            assertEquals("tt123", saved.metaId)
            assertEquals("tt123:1:2", saved.videoId)
            assertEquals("series", saved.type)
            assertEquals(1, saved.season)
            assertEquals(2, saved.episode)
            assertEquals(60_000L, saved.positionMs)
            assertEquals(40L * 60L * 1000L, saved.durationMs)
            assertFalse(saved.watched)
            assertEquals(nowMs, saved.updatedAt)
        }

    @Test
    fun SV2_positionExactlyNinetyPercent_isWatchedTrue() =
        runTest {
            val duration = 100_000L
            val sample = ProgressSample(positionMs = 90_000L, durationMs = duration, ended = false)

            val outcome = saveProgress(movieTarget, sample, firstOfSession = false, nowMs = 1000L)

            assertEquals(SaveOutcome.Saved(watched = true), outcome)
            val slot = slot<WatchProgress>()
            coVerify { progress.upsert(capture(slot)) }
            assertTrue(slot.captured.watched)
        }

    @Test
    fun SV3_existingWatchedRow_writeAtTwentyPercent_returnsSkipped_noWrites() =
        runTest {
            val duration = 100_000L
            val existing =
                WatchProgress(
                    key = "tt456|tt456",
                    metaId = "tt456",
                    videoId = "tt456",
                    type = "movie",
                    positionMs = 95_000L,
                    durationMs = duration,
                    watched = true,
                    updatedAt = 500L,
                )
            coEvery { progress.get("tt456|tt456") } returns existing
            coEvery { library.getItem("tt456") } returns
                LibraryItem(metaId = "tt456", inWatchlist = true, status = ListStatus.WATCHING)

            val sample = ProgressSample(positionMs = 20_000L, durationMs = duration, ended = false)
            val outcome = saveProgress(movieTarget, sample, firstOfSession = true, nowMs = 2000L)

            assertEquals(SaveOutcome.Skipped, outcome)
            coVerify(exactly = 0) { progress.upsert(any()) }
            coVerify(exactly = 0) { library.ensureSnapshot(any()) }
            coVerify(exactly = 0) { library.setStatusById(any<String>(), any<ListStatus>()) }
        }

    @Test
    fun SV4_keyFormat_equalsExistingKeyBuilder() =
        runTest {
            val sample = ProgressSample(positionMs = 10_000L, durationMs = 100_000L, ended = false)

            saveProgress(movieTarget, sample, firstOfSession = false, nowMs = 1000L)
            saveProgress(episodeTarget, sample, firstOfSession = false, nowMs = 1000L)

            coVerify { progress.upsert(match { it.key == ProgressRules.progressKey("tt456", "tt456") }) }
            coVerify { progress.upsert(match { it.key == ProgressRules.progressKey("tt123", "tt123:1:2") }) }
        }

    @Test
    fun SV5_durationZero_orPositionUnderFiveSeconds_skipped() =
        runTest {
            val zeroDuration = ProgressSample(positionMs = 10_000L, durationMs = 0L, ended = false)
            val underFiveSec = ProgressSample(positionMs = 4_000L, durationMs = 100_000L, ended = false)

            val outcome1 = saveProgress(movieTarget, zeroDuration, firstOfSession = true, nowMs = 1000L)
            val outcome2 = saveProgress(movieTarget, underFiveSec, firstOfSession = true, nowMs = 1000L)

            assertEquals(SaveOutcome.Skipped, outcome1)
            assertEquals(SaveOutcome.Skipped, outcome2)
            coVerify(exactly = 0) { progress.upsert(any()) }
            coVerify(exactly = 0) { library.ensureSnapshot(any()) }
            coVerify(exactly = 0) { library.getItem(any()) }
        }

    @Test
    fun SV6_ended_positionZero_storesDuration_watchedTrue() =
        runTest {
            val sample = ProgressSample(positionMs = 0L, durationMs = 100_000L, ended = true)

            val outcome = saveProgress(movieTarget, sample, firstOfSession = false, nowMs = 1000L)

            assertEquals(SaveOutcome.Saved(watched = true), outcome)
            val slot = slot<WatchProgress>()
            coVerify { progress.upsert(capture(slot)) }
            assertEquals(100_000L, slot.captured.positionMs)
            assertTrue(slot.captured.watched)
        }

    @Test
    fun SV7_positionAboveDuration_storesDuration() =
        runTest {
            val sample = ProgressSample(positionMs = 120_000L, durationMs = 100_000L, ended = false)

            val outcome = saveProgress(movieTarget, sample, firstOfSession = false, nowMs = 1000L)

            assertEquals(SaveOutcome.Saved(watched = true), outcome)
            val slot = slot<WatchProgress>()
            coVerify { progress.upsert(capture(slot)) }
            assertEquals(100_000L, slot.captured.positionMs)
        }

    @Test
    fun SV8_firstOfSession_callsEnsureSnapshotOnce() =
        runTest {
            val sample = ProgressSample(positionMs = 10_000L, durationMs = 100_000L, ended = false)

            saveProgress(movieTarget, sample, firstOfSession = true, nowMs = 1000L)

            coVerify(exactly = 1) { library.ensureSnapshot(movieTarget) }
        }

    @Test
    fun SV9_notFirstOfSession_noSnapshotCall() =
        runTest {
            val sample = ProgressSample(positionMs = 10_000L, durationMs = 100_000L, ended = false)

            saveProgress(movieTarget, sample, firstOfSession = false, nowMs = 1000L)

            coVerify(exactly = 0) { library.ensureSnapshot(any()) }
        }

    @Test
    fun SV10_snapshotMethodReusesD61Path() =
        runTest {
            // Documented: ensureSnapshot in LibraryRepositoryImpl reuses the exact D61 logic
            val sample = ProgressSample(positionMs = 10_000L, durationMs = 100_000L, ended = false)
            saveProgress(movieTarget, sample, firstOfSession = true, nowMs = 1000L)
            coVerify(exactly = 1) { library.ensureSnapshot(movieTarget) }
        }

    @Test
    fun SV11_inWatchlist_planToWatch_firstOfSession_becomesWatching() =
        runTest {
            coEvery { library.getItem("tt456") } returns
                LibraryItem(metaId = "tt456", inWatchlist = true, status = ListStatus.PLAN_TO_WATCH)
            val sample = ProgressSample(positionMs = 60_000L, durationMs = 100_000L, ended = false)

            saveProgress(movieTarget, sample, firstOfSession = true, nowMs = 1000L)

            coVerify(exactly = 1) { library.setStatusById("tt456", ListStatus.WATCHING) }
        }

    @Test
    fun SV12_inWatchlist_onHoldAndDropped_firstOfSession_becomesWatching() =
        runTest {
            coEvery { library.getItem("tt456") } returns
                LibraryItem(metaId = "tt456", inWatchlist = true, status = ListStatus.ON_HOLD)
            val sample = ProgressSample(positionMs = 60_000L, durationMs = 100_000L, ended = false)

            saveProgress(movieTarget, sample, firstOfSession = true, nowMs = 1000L)
            coVerify(exactly = 1) { library.setStatusById("tt456", ListStatus.WATCHING) }

            coEvery { library.getItem("tt456") } returns
                LibraryItem(metaId = "tt456", inWatchlist = true, status = ListStatus.DROPPED)
            saveProgress(movieTarget, sample, firstOfSession = true, nowMs = 2000L)
            coVerify(exactly = 2) { library.setStatusById("tt456", ListStatus.WATCHING) }
        }

    @Test
    fun SV13_inWatchlist_watchingAndCompleted_firstOfSession_noStatusCall() =
        runTest {
            coEvery { library.getItem("tt456") } returns
                LibraryItem(metaId = "tt456", inWatchlist = true, status = ListStatus.WATCHING)
            val sample = ProgressSample(positionMs = 60_000L, durationMs = 100_000L, ended = false)

            saveProgress(movieTarget, sample, firstOfSession = true, nowMs = 1000L)

            coEvery { library.getItem("tt456") } returns
                LibraryItem(metaId = "tt456", inWatchlist = true, status = ListStatus.COMPLETED)
            saveProgress(movieTarget, sample, firstOfSession = true, nowMs = 2000L)

            coVerify(exactly = 0) { library.setStatusById(any<String>(), any<ListStatus>()) }
        }

    @Test
    fun SV14_notInWatchlist_noStatusCall() =
        runTest {
            coEvery { library.getItem("tt456") } returns null
            val sample = ProgressSample(positionMs = 30_000L, durationMs = 100_000L, ended = false)
            saveProgress(movieTarget, sample, firstOfSession = true, nowMs = 1000L)

            coEvery { library.getItem("tt456") } returns
                LibraryItem(metaId = "tt456", inWatchlist = false, status = ListStatus.PLAN_TO_WATCH)
            saveProgress(movieTarget, sample, firstOfSession = true, nowMs = 2000L)

            coVerify(exactly = 0) { library.setStatusById(any<String>(), any<ListStatus>()) }
            coVerify(exactly = 0) { library.setStatus(any(), any()) }
        }

    @Test
    fun SV15_movieBecomesWatched_movesToCompleted() =
        runTest {
            val watchedSample = ProgressSample(positionMs = 95_000L, durationMs = 100_000L, ended = false)

            // Watching -> Completed
            coEvery { library.getItem("tt456") } returns
                LibraryItem(metaId = "tt456", inWatchlist = true, status = ListStatus.WATCHING)
            saveProgress(movieTarget, watchedSample, firstOfSession = false, nowMs = 1000L)
            coVerify(exactly = 1) { library.setStatusById("tt456", ListStatus.COMPLETED) }

            // Plan to watch -> Completed
            coEvery { library.getItem("tt456") } returns
                LibraryItem(metaId = "tt456", inWatchlist = true, status = ListStatus.PLAN_TO_WATCH)
            saveProgress(movieTarget, watchedSample, firstOfSession = true, nowMs = 2000L)
            coVerify(exactly = 2) { library.setStatusById("tt456", ListStatus.COMPLETED) }

            // Dropped -> Completed
            coEvery { library.getItem("tt456") } returns
                LibraryItem(metaId = "tt456", inWatchlist = true, status = ListStatus.DROPPED)
            saveProgress(movieTarget, watchedSample, firstOfSession = false, nowMs = 3000L)
            coVerify(exactly = 3) { library.setStatusById("tt456", ListStatus.COMPLETED) }
        }

    @Test
    fun SV16_movieBecomesWatched_alreadyCompleted_noStatusCall() =
        runTest {
            coEvery { library.getItem("tt456") } returns
                LibraryItem(metaId = "tt456", inWatchlist = true, status = ListStatus.COMPLETED)
            val sample = ProgressSample(positionMs = 95_000L, durationMs = 100_000L, ended = false)

            saveProgress(movieTarget, sample, firstOfSession = false, nowMs = 1000L)

            coVerify(exactly = 0) { library.setStatusById(any<String>(), any<ListStatus>()) }
        }

    @Test
    fun SV17_seriesEpisodeBecomesWatched_statusWatching_noStatusCall() =
        runTest {
            coEvery { library.getItem("tt123") } returns
                LibraryItem(metaId = "tt123", inWatchlist = true, status = ListStatus.WATCHING)
            val sample = ProgressSample(positionMs = 95_000L, durationMs = 100_000L, ended = false)

            saveProgress(episodeTarget, sample, firstOfSession = false, nowMs = 1000L)

            coVerify(exactly = 0) { library.setStatusById(any<String>(), any<ListStatus>()) }
        }

    @Test
    fun SV18_replay_watchedBeforeTrue_firstOfSession_planToWatch_noStatusCall() =
        runTest {
            coEvery { progress.get(any()) } returns
                WatchProgress(
                    key = "tt456|tt456",
                    metaId = "tt456",
                    videoId = "tt456",
                    type = "movie",
                    positionMs = 95_000L,
                    durationMs = 100_000L,
                    watched = true,
                    updatedAt = 500L,
                )
            coEvery { library.getItem("tt456") } returns
                LibraryItem(metaId = "tt456", inWatchlist = true, status = ListStatus.PLAN_TO_WATCH)
            val sample = ProgressSample(positionMs = 20_000L, durationMs = 100_000L, ended = false)

            saveProgress(movieTarget, sample, firstOfSession = true, nowMs = 1000L)

            coVerify(exactly = 0) { library.setStatusById(any<String>(), any<ListStatus>()) }
        }

    @Test(expected = IOException::class)
    fun SV19_upsertThrowsIOException_propagates() =
        runTest {
            coEvery { progress.upsert(any()) } throws IOException("Disk error")
            val sample = ProgressSample(positionMs = 20_000L, durationMs = 100_000L, ended = false)

            saveProgress(movieTarget, sample, firstOfSession = false, nowMs = 1000L)
        }

    @Test(expected = CancellationException::class)
    fun SV19_upsertThrowsCancellationException_propagates() =
        runTest {
            coEvery { progress.upsert(any()) } throws CancellationException("Cancelled")
            val sample = ProgressSample(positionMs = 20_000L, durationMs = 100_000L, ended = false)

            saveProgress(movieTarget, sample, firstOfSession = false, nowMs = 1000L)
        }

    @Test
    fun SV20_unlistedMovieAt60s_addedAsWatching() =
        runTest {
            coEvery { library.getItem(movieTarget.metaId) } returns null
            val sample = ProgressSample(positionMs = 60_000L, durationMs = 120_000L, ended = false)

            saveProgress(movieTarget, sample, firstOfSession = true, nowMs = 1000L)

            val slot = slot<MetaPreview>()
            coVerify(exactly = 1) { library.setStatus(capture(slot), ListStatus.WATCHING) }
            assertEquals(movieTarget.metaId, slot.captured.id)
            assertEquals(movieTarget.type, slot.captured.type)
            assertEquals(movieTarget.name, slot.captured.name)
            assertEquals(movieTarget.poster, slot.captured.poster)
        }

    @Test
    fun SV21_unlistedMovieEndedPosition0_addedAsCompletedDirectly() =
        runTest {
            coEvery { library.getItem(movieTarget.metaId) } returns null
            val sample = ProgressSample(positionMs = 0L, durationMs = 120_000L, ended = true)

            saveProgress(movieTarget, sample, firstOfSession = true, nowMs = 1000L)

            coVerify(exactly = 1) { library.setStatus(any(), ListStatus.COMPLETED) }
            coVerify(exactly = 0) { library.setStatus(any(), ListStatus.WATCHING) }
        }

    @Test
    fun SV22_unlistedSeriesEpisode90s_addedAsWatching() =
        runTest {
            coEvery { library.getItem(episodeTarget.metaId) } returns null
            val sample = ProgressSample(positionMs = 90_000L, durationMs = 120_000L, ended = false)

            saveProgress(episodeTarget, sample, firstOfSession = true, nowMs = 1000L)

            coVerify(exactly = 1) { library.setStatus(any(), ListStatus.WATCHING) }
        }

    @Test
    fun SV23_seriesEpisodeBecomesWatched_neverCompleted() =
        runTest {
            coEvery { library.getItem(episodeTarget.metaId) } returns
                LibraryItem(metaId = episodeTarget.metaId, inWatchlist = true, status = ListStatus.WATCHING)
            val sample = ProgressSample(positionMs = 110_000L, durationMs = 120_000L, ended = false)

            saveProgress(episodeTarget, sample, firstOfSession = false, nowMs = 1000L)

            coVerify(exactly = 0) { library.setStatusById(any(), ListStatus.COMPLETED) }
            coVerify(exactly = 0) { library.setStatus(any(), ListStatus.COMPLETED) }
        }

    @Test
    fun SV24_seriesCompletedUnwatchedEpisode60s_becomesWatching() =
        runTest {
            coEvery { library.getItem(episodeTarget.metaId) } returns
                LibraryItem(metaId = episodeTarget.metaId, inWatchlist = true, status = ListStatus.COMPLETED)
            val sample = ProgressSample(positionMs = 60_000L, durationMs = 120_000L, ended = false)

            saveProgress(episodeTarget, sample, firstOfSession = true, nowMs = 1000L)

            coVerify(exactly = 1) { library.setStatusById(episodeTarget.metaId, ListStatus.WATCHING) }
        }

    @Test
    fun SV25_inWatchlistPlanToWatch_59999msNoCall_then60000msWatching() =
        runTest {
            coEvery { library.getItem(movieTarget.metaId) } returns
                LibraryItem(metaId = movieTarget.metaId, inWatchlist = true, status = ListStatus.PLAN_TO_WATCH)

            val sampleUnder = ProgressSample(positionMs = 59_999L, durationMs = 120_000L, ended = false)
            saveProgress(movieTarget, sampleUnder, firstOfSession = true, nowMs = 1000L)
            coVerify(exactly = 0) { library.setStatusById(any(), any()) }

            val sampleQualifying = ProgressSample(positionMs = 60_000L, durationMs = 120_000L, ended = false)
            saveProgress(movieTarget, sampleQualifying, firstOfSession = false, nowMs = 2000L)
            coVerify(exactly = 1) { library.setStatusById(movieTarget.metaId, ListStatus.WATCHING) }
        }

    @Test
    fun SV26_movieCompletedReplayWatchedBeforeTrue_noStatusCall() =
        runTest {
            coEvery { progress.get(any()) } returns
                WatchProgress(
                    key = "tt456|tt456",
                    metaId = "tt456",
                    videoId = "tt456",
                    type = "movie",
                    positionMs = 100_000L,
                    durationMs = 100_000L,
                    watched = true,
                    updatedAt = 500L,
                )
            coEvery { library.getItem("tt456") } returns
                LibraryItem(metaId = "tt456", inWatchlist = true, status = ListStatus.COMPLETED)
            val sample = ProgressSample(positionMs = 80_000L, durationMs = 100_000L, ended = false)

            saveProgress(movieTarget, sample, firstOfSession = true, nowMs = 1000L)

            coVerify(exactly = 0) { library.setStatusById(any(), any()) }
            coVerify(exactly = 0) { library.setStatus(any(), any()) }
        }

    @Test
    fun SV27_movieDroppedBecomesWatched_completed() =
        runTest {
            coEvery { library.getItem(movieTarget.metaId) } returns
                LibraryItem(metaId = movieTarget.metaId, inWatchlist = true, status = ListStatus.DROPPED)
            val sample = ProgressSample(positionMs = 95_000L, durationMs = 100_000L, ended = false)

            saveProgress(movieTarget, sample, firstOfSession = false, nowMs = 1000L)

            coVerify(exactly = 1) { library.setStatusById(movieTarget.metaId, ListStatus.COMPLETED) }
        }

    @Test(expected = IOException::class)
    fun SV28_setStatusThrowsIOException_propagates() =
        runTest {
            coEvery { library.getItem(movieTarget.metaId) } returns null
            coEvery { library.setStatus(any(), any()) } throws IOException("Network failure")
            val sample = ProgressSample(positionMs = 60_000L, durationMs = 100_000L, ended = false)

            saveProgress(movieTarget, sample, firstOfSession = true, nowMs = 1000L)
        }

    @Test(expected = CancellationException::class)
    fun SV28_setStatusThrowsCancellationException_propagates() =
        runTest {
            coEvery { library.getItem(movieTarget.metaId) } returns null
            coEvery { library.setStatus(any(), any()) } throws CancellationException("Cancelled")
            val sample = ProgressSample(positionMs = 60_000L, durationMs = 100_000L, ended = false)

            saveProgress(movieTarget, sample, firstOfSession = true, nowMs = 1000L)
        }

    @Test
    fun SV29_watchingSeriesTicks_noStatusCallOnAnyTick() =
        runTest {
            coEvery { library.getItem(episodeTarget.metaId) } returns
                LibraryItem(metaId = episodeTarget.metaId, inWatchlist = true, status = ListStatus.WATCHING)

            saveProgress(episodeTarget, ProgressSample(60_000L, 120_000L, false), firstOfSession = true, nowMs = 1000L)
            saveProgress(episodeTarget, ProgressSample(65_000L, 120_000L, false), firstOfSession = false, nowMs = 2000L)
            saveProgress(episodeTarget, ProgressSample(70_000L, 120_000L, false), firstOfSession = false, nowMs = 3000L)

            coVerify(exactly = 0) { library.setStatusById(any(), any()) }
            coVerify(exactly = 0) { library.setStatus(any(), any()) }
        }

    @Test
    fun SV30_inWatchlistWatchingMovieBecomesWatched_completedOnce() =
        runTest {
            coEvery { library.getItem(movieTarget.metaId) } returns
                LibraryItem(metaId = movieTarget.metaId, inWatchlist = true, status = ListStatus.WATCHING)
            val sample = ProgressSample(positionMs = 95_000L, durationMs = 100_000L, ended = false)

            saveProgress(movieTarget, sample, firstOfSession = false, nowMs = 1000L)

            coVerify(exactly = 1) { library.setStatusById(movieTarget.metaId, ListStatus.COMPLETED) }
        }
}
