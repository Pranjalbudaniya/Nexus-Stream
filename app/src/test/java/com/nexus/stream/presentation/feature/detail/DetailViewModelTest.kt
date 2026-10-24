package com.nexus.stream.presentation.feature.detail

import androidx.lifecycle.SavedStateHandle
import com.nexus.stream.domain.model.AppError
import com.nexus.stream.domain.model.EpisodeProgress
import com.nexus.stream.domain.model.EpisodeRef
import com.nexus.stream.domain.model.ListStatus
import com.nexus.stream.domain.model.MetaDetail
import com.nexus.stream.domain.model.Outcome
import com.nexus.stream.domain.model.PlayIntent
import com.nexus.stream.domain.model.PosterShape
import com.nexus.stream.domain.model.Video
import com.nexus.stream.domain.model.toPreview
import com.nexus.stream.domain.repository.LibraryRepository
import com.nexus.stream.domain.repository.MetaRepository
import com.nexus.stream.domain.repository.MetaResult
import com.nexus.stream.domain.repository.MetaSource
import com.nexus.stream.domain.repository.ProgressRepository
import com.nexus.stream.domain.usecase.TimeLeft
import com.nexus.stream.domain.usecase.WatchAction
import com.nexus.stream.domain.usecase.timeLeftParts
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DetailViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private val metaRepository: MetaRepository = mockk()
    private val libraryRepository: LibraryRepository = mockk(relaxed = true)
    private val progressRepository: ProgressRepository = mockk(relaxed = true)
    private val fixedTime = 1_000_000L

    private fun testMeta(
        id: String = "m1",
        type: String = "movie",
        trailers: List<String> = emptyList(),
        videos: List<Video> = emptyList(),
    ) = MetaDetail(
        id = id,
        type = type,
        name = "Test Title",
        poster = "https://poster.jpg",
        posterShape = PosterShape.POSTER,
        background = null,
        logo = null,
        description = "Description",
        releaseInfo = "2024",
        released = "2024-01-01",
        runtime = "120 min",
        imdbRating = "8.0",
        genres = listOf("Action"),
        cast = listOf("Actor"),
        directors = listOf("Director"),
        country = null,
        language = null,
        website = null,
        links = emptyList(),
        trailers = trailers,
        videos = videos,
        defaultVideoId = null,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { libraryRepository.observeListStatuses() } returns flowOf(emptyMap())
        coEvery { libraryRepository.setStatus(any(), any()) } returns Unit
        coEvery { progressRepository.observeEpisodeProgress(any()) } returns flowOf(emptyMap())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(
        type: String = "movie",
        id: String = "m1",
        clock: () -> Long = { fixedTime },
    ): DetailViewModel {
        val handle = SavedStateHandle(mapOf("type" to type, "id" to id))
        return DetailViewModel(
            savedStateHandle = handle,
            metaRepository = metaRepository,
            libraryRepository = libraryRepository,
            progressRepository = progressRepository,
            clock = clock,
        )
    }

    @Test
    fun `initial load moves from Loading to Content on success`() =
        runTest(testDispatcher) {
            val meta = testMeta("m1")
            coEvery { metaRepository.getMeta("movie", "m1", false) } returns
                Outcome.Success(MetaResult(meta, MetaSource.FRESH))

            val viewModel = createViewModel("movie", "m1")

            val state = viewModel.uiState.value
            assertTrue(state is DetailUiState.Content)
            val content = state as DetailUiState.Content
            assertEquals("Test Title", content.meta.name)
            assertEquals(MetaSource.FRESH, content.source)
        }

    @Test
    fun `failure shows Error and Retry reloads`() =
        runTest(testDispatcher) {
            coEvery { metaRepository.getMeta("movie", "m1", false) } returns
                Outcome.Failure(AppError.Network)

            val viewModel = createViewModel("movie", "m1")

            val state = viewModel.uiState.value
            assertTrue(state is DetailUiState.Error)
            assertEquals(AppError.Network, (state as DetailUiState.Error).error)

            val meta = testMeta("m1")
            coEvery { metaRepository.getMeta("movie", "m1", false) } returns
                Outcome.Success(MetaResult(meta, MetaSource.FRESH))

            viewModel.onEvent(DetailEvent.Retry)

            val retryState = viewModel.uiState.value
            assertTrue(retryState is DetailUiState.Content)
        }

    @Test
    fun `ToggleWatchlist calls repository with inverted watchlist state`() =
        runTest(testDispatcher) {
            val meta = testMeta("m1")
            coEvery { metaRepository.getMeta("movie", "m1", false) } returns
                Outcome.Success(MetaResult(meta, MetaSource.FRESH))
            coEvery { libraryRepository.observeListStatuses() } returns flowOf(mapOf("m1" to ListStatus.PLAN_TO_WATCH))

            val viewModel = createViewModel("movie", "m1")
            assertTrue((viewModel.uiState.value as DetailUiState.Content).inWatchlist)

            viewModel.onEvent(DetailEvent.ToggleWatchlist)

            coVerify(exactly = 1) {
                libraryRepository.setStatus(meta.toPreview(), null)
            }
        }

    @Test
    fun `SetListStatus applies a status and updates button label`() =
        runTest(testDispatcher) {
            val meta = testMeta("m1")
            coEvery { metaRepository.getMeta("movie", "m1", false) } returns
                Outcome.Success(MetaResult(meta, MetaSource.FRESH))
            val statusesFlow = MutableStateFlow<Map<String, ListStatus>>(emptyMap())
            coEvery { libraryRepository.observeListStatuses() } returns statusesFlow

            val viewModel = createViewModel("movie", "m1")
            val initial = viewModel.uiState.value as DetailUiState.Content
            assertEquals(null, initial.listStatus)
            assertFalse(initial.inWatchlist)

            viewModel.onEvent(DetailEvent.SetListStatus(ListStatus.WATCHING))

            coVerify(exactly = 1) {
                libraryRepository.setStatus(meta.toPreview(), ListStatus.WATCHING)
            }

            statusesFlow.value = mapOf("m1" to ListStatus.WATCHING)
            val updated = viewModel.uiState.value as DetailUiState.Content
            assertEquals(ListStatus.WATCHING, updated.listStatus)
            assertTrue(updated.inWatchlist)
        }

    @Test
    fun `SelectSeason changes the shown season`() =
        runTest(testDispatcher) {
            val videos =
                listOf(
                    Video("s1e1", "Ep 1", "2020-01-01", season = 1, episode = 1, null, null),
                    Video("s2e1", "Ep 2", "2020-01-01", season = 2, episode = 1, null, null),
                )
            val meta = testMeta("s1", type = "series", videos = videos)
            coEvery { metaRepository.getMeta("series", "s1", false) } returns
                Outcome.Success(MetaResult(meta, MetaSource.FRESH))

            val viewModel = createViewModel("series", "s1")
            var content = viewModel.uiState.value as DetailUiState.Content
            assertEquals(1, content.selectedSeason)

            viewModel.onEvent(DetailEvent.SelectSeason(2))
            content = viewModel.uiState.value as DetailUiState.Content
            assertEquals(2, content.selectedSeason)
        }

    @Test
    fun `an invalid trailer id never emits an open-url effect`() =
        runTest(testDispatcher) {
            val meta = testMeta("m1", trailers = listOf("invalid!id"))
            coEvery { metaRepository.getMeta("movie", "m1", false) } returns
                Outcome.Success(MetaResult(meta, MetaSource.FRESH))

            val viewModel = createViewModel("movie", "m1")
            val content = viewModel.uiState.value as DetailUiState.Content
            assertEquals(null, content.trailerUrl)

            val effects = mutableListOf<DetailEffect>()
            val job = launch { viewModel.effects.toList(effects) }

            viewModel.onEvent(DetailEvent.OpenTrailer)

            assertTrue(effects.isEmpty())
            job.cancel()
        }

    @Test
    fun `unreleased episode emits message instead of opening sheet`() =
        runTest(testDispatcher) {
            val videos =
                listOf(
                    Video("s1e1", "Ep 1", "2099-01-01", season = 1, episode = 1, null, null),
                )
            val meta = testMeta("s1", type = "series", videos = videos)
            coEvery { metaRepository.getMeta("series", "s1", false) } returns
                Outcome.Success(MetaResult(meta, MetaSource.FRESH))

            val viewModel = createViewModel("series", "s1")

            val effects = mutableListOf<DetailEffect>()
            val job = launch { viewModel.effects.toList(effects) }

            viewModel.onEvent(DetailEvent.PlayEpisode("s1e1"))

            assertEquals(1, effects.size)
            assertTrue(effects.first() is DetailEffect.ShowMessage)
            assertEquals("Not released yet", (effects.first() as DetailEffect.ShowMessage).message)

            val content = viewModel.uiState.value as DetailUiState.Content
            assertFalse(content.showStreamsSheet)
            job.cancel()
        }

    @Test
    fun `ToggleWatched calls setWatched on progressRepository`() =
        runTest(testDispatcher) {
            val videos =
                listOf(
                    Video("s1e1", "Ep 1", "2020-01-01", season = 1, episode = 1, null, null),
                )
            val meta = testMeta("s1", type = "series", videos = videos)
            coEvery { metaRepository.getMeta("series", "s1", false) } returns
                Outcome.Success(MetaResult(meta, MetaSource.FRESH))
            coEvery { progressRepository.observeEpisodeProgress("s1") } returns
                flowOf(mapOf("s1e1" to EpisodeProgress("s1e1", 0L, 0L, watched = false, updatedAt = 1000L)))

            val viewModel = createViewModel("series", "s1")

            viewModel.onEvent(DetailEvent.ToggleWatched("s1e1"))

            coVerify(exactly = 1) {
                progressRepository.setWatched(
                    meta = meta.toPreview(),
                    episode = EpisodeRef(videoId = "s1e1", type = "series", season = 1, episode = 1),
                    watched = true,
                )
            }
        }

    @Test
    fun `OpenWatchOptions lists the actions in state`() =
        runTest(testDispatcher) {
            val videos =
                listOf(
                    Video("s1e1", "Ep 1", "1960-01-01", season = 1, episode = 1, null, null),
                    Video("s1e2", "Ep 2", "1960-01-01", season = 1, episode = 2, null, null),
                )
            val meta = testMeta("s1", type = "series", videos = videos)
            coEvery { metaRepository.getMeta("series", "s1", false) } returns
                Outcome.Success(MetaResult(meta, MetaSource.FRESH))

            val viewModel = createViewModel("series", "s1")
            viewModel.onEvent(DetailEvent.OpenWatchOptions)

            val content = viewModel.uiState.value as DetailUiState.Content
            assertTrue(content.showWatchOptionsSheet)
            assertFalse(content.watchActions.isEmpty())
            assertTrue(content.watchActions.any { it is WatchAction.ToggleWatchlist })
            assertTrue(content.watchActions.any { it is WatchAction.SetSeasonWatched })
        }

    @Test
    fun `a multi-episode unwatch needs confirmation and CancelPending changes nothing`() =
        runTest(testDispatcher) {
            val videos =
                listOf(
                    Video("s1e1", "Ep 1", "1960-01-01", season = 1, episode = 1, null, null),
                    Video("s1e2", "Ep 2", "1960-01-01", season = 1, episode = 2, null, null),
                )
            val meta = testMeta("s1", type = "series", videos = videos)
            coEvery { metaRepository.getMeta("series", "s1", false) } returns
                Outcome.Success(MetaResult(meta, MetaSource.FRESH))

            val viewModel = createViewModel("series", "s1")
            val action = WatchAction.SetAllWatched(watched = false, count = 2)

            viewModel.onEvent(DetailEvent.ApplyWatchAction(action))

            var content = viewModel.uiState.value as DetailUiState.Content
            assertEquals(action, content.pendingConfirmationAction)
            coVerify(exactly = 0) { progressRepository.setWatchedMany(any(), any(), any()) }

            viewModel.onEvent(DetailEvent.CancelPending)

            content = viewModel.uiState.value as DetailUiState.Content
            assertEquals(null, content.pendingConfirmationAction)
            coVerify(exactly = 0) { progressRepository.setWatchedMany(any(), any(), any()) }
        }

    @Test
    fun `a multi-episode unwatch confirmed runs and snackbar carries count`() =
        runTest(testDispatcher) {
            val videos =
                listOf(
                    Video("s1e1", "Ep 1", "1960-01-01", season = 1, episode = 1, null, null),
                    Video("s1e2", "Ep 2", "1960-01-01", season = 1, episode = 2, null, null),
                    Video("s1e3", "Ep 3", "1960-01-01", season = 1, episode = 3, null, null),
                )
            val meta = testMeta("s1", type = "series", videos = videos)
            coEvery { metaRepository.getMeta("series", "s1", false) } returns
                Outcome.Success(MetaResult(meta, MetaSource.FRESH))

            val viewModel = createViewModel("series", "s1")
            val effects = mutableListOf<DetailEffect>()
            val job = launch { viewModel.effects.toList(effects) }

            val action = WatchAction.SetSeasonWatched(season = 1, watched = false, count = 3)
            viewModel.onEvent(DetailEvent.ApplyWatchAction(action))
            viewModel.onEvent(DetailEvent.ConfirmPending)

            coVerify(exactly = 1) { progressRepository.setWatchedMany(any(), any(), false) }
            assertEquals(1, effects.size)
            assertEquals("Marked 3 episodes as unwatched", (effects.first() as DetailEffect.ShowMessage).message)

            job.cancel()
        }

    @Test
    fun `a single-item action runs without confirmation`() =
        runTest(testDispatcher) {
            val meta = testMeta("m1", type = "movie")
            coEvery { metaRepository.getMeta("movie", "m1", false) } returns
                Outcome.Success(MetaResult(meta, MetaSource.FRESH))

            val viewModel = createViewModel("movie", "m1")
            val effects = mutableListOf<DetailEffect>()
            val job = launch { viewModel.effects.toList(effects) }

            val action = WatchAction.SetMovieWatched(watched = true)
            viewModel.onEvent(DetailEvent.ApplyWatchAction(action))

            val content = viewModel.uiState.value as DetailUiState.Content
            assertEquals(null, content.pendingConfirmationAction)
            coVerify(exactly = 1) { progressRepository.setWatched(meta.toPreview(), any(), true) }
            assertEquals(1, effects.size)
            assertEquals("Marked as watched", (effects.first() as DetailEffect.ShowMessage).message)

            job.cancel()
        }

    @Test
    fun `download action emits not available message and nothing else`() =
        runTest(testDispatcher) {
            val meta = testMeta("m1", type = "movie")
            coEvery { metaRepository.getMeta("movie", "m1", false) } returns
                Outcome.Success(MetaResult(meta, MetaSource.FRESH))

            val viewModel = createViewModel("movie", "m1")
            val effects = mutableListOf<DetailEffect>()
            val job = launch { viewModel.effects.toList(effects) }

            viewModel.onEvent(DetailEvent.Download("v1"))

            assertEquals(1, effects.size)
            assertEquals(
                "Downloads aren't available yet.",
                (effects.first() as DetailEffect.ShowMessage).message,
            )

            val content = viewModel.uiState.value as DetailUiState.Content
            assertFalse(content.showStreamsSheet)
            job.cancel()
        }

    @Test
    fun DV1_progressFlowNotEmitted_stateNotReady_thenPlayAfterEmission_neverPlayThenResume() =
        runTest(testDispatcher) {
            val progressFlow = MutableSharedFlow<Map<String, EpisodeProgress>>()
            coEvery { progressRepository.observeEpisodeProgress("m1") } returns progressFlow
            val meta = testMeta("m1", type = "movie")
            coEvery { metaRepository.getMeta("movie", "m1", false) } returns
                Outcome.Success(MetaResult(meta, MetaSource.FRESH))

            val viewModel = createViewModel("movie", "m1")

            val initialContent = viewModel.uiState.value as DetailUiState.Content
            assertEquals(PrimaryPlayState.NotReady, initialContent.primaryPlay)

            progressFlow.emit(emptyMap())

            val afterEmission = viewModel.uiState.value as DetailUiState.Content
            val ready = afterEmission.primaryPlay as PrimaryPlayState.Ready
            assertEquals(PlayIntent.Start, ready.intent)
            assertEquals("m1", ready.targetVideoId)
            assertTrue(ready.isEnabled)
        }

    @Test
    fun DV2_movieWithResumableRow_resumeWithFractionAndTimeLeft() =
        runTest(testDispatcher) {
            val position = 15_000L
            val duration = 60_000L
            coEvery { progressRepository.observeEpisodeProgress("m1") } returns
                flowOf(mapOf("m1" to EpisodeProgress("m1", position, duration, watched = false, updatedAt = 1000L)))
            val meta = testMeta("m1", type = "movie")
            coEvery { metaRepository.getMeta("movie", "m1", false) } returns
                Outcome.Success(MetaResult(meta, MetaSource.FRESH))

            val viewModel = createViewModel("movie", "m1")

            val content = viewModel.uiState.value as DetailUiState.Content
            val ready = content.primaryPlay as PrimaryPlayState.Ready
            val resume = ready.intent as PlayIntent.Resume
            assertEquals("m1", resume.videoId)
            assertEquals(position, resume.positionMs)
            assertEquals(duration, resume.durationMs)
            val fraction = (resume.positionMs.toFloat() / resume.durationMs.toFloat()).coerceIn(0f, 1f)
            assertEquals(0.25f, fraction, 0.001f)
            assertEquals(TimeLeft(0, 1), timeLeftParts(resume.positionMs, resume.durationMs))
        }

    @Test
    fun DV3_seriesS1E4InProgress_resumeS1E4() =
        runTest(testDispatcher) {
            val videos =
                listOf(
                    Video("e1", "Ep 1", "2020-01-01", 1, 1, null, null),
                    Video("e2", "Ep 2", "2020-01-01", 1, 2, null, null),
                    Video("e3", "Ep 3", "2020-01-01", 1, 3, null, null),
                    Video("e4", "Ep 4", "2020-01-01", 1, 4, null, null),
                )
            val meta = testMeta("series1", type = "series", videos = videos)
            coEvery { metaRepository.getMeta("series", "series1", false) } returns
                Outcome.Success(MetaResult(meta, MetaSource.FRESH))
            coEvery { progressRepository.observeEpisodeProgress("series1") } returns
                flowOf(
                    mapOf(
                        "e4" to
                            EpisodeProgress(
                                "e4",
                                positionMs = 15_000L,
                                durationMs = 60_000L,
                                watched = false,
                                updatedAt = 1000L,
                            ),
                    ),
                )

            val viewModel = createViewModel("series", "series1")

            val content = viewModel.uiState.value as DetailUiState.Content
            val ready = content.primaryPlay as PrimaryPlayState.Ready
            val resume = ready.intent as PlayIntent.Resume
            assertEquals("e4", resume.videoId)
            assertEquals(1, resume.season)
            assertEquals(4, resume.episode)
            assertEquals("e4", content.activeVideoId)
        }

    @Test
    fun DV4_seriesS1E4Watched_nextS1E5() =
        runTest(testDispatcher) {
            val videos =
                listOf(
                    Video("e4", "Ep 4", "2020-01-01", 1, 4, null, null),
                    Video("e5", "Ep 5", "2020-01-01", 1, 5, null, null),
                )
            val meta = testMeta("series1", type = "series", videos = videos)
            coEvery { metaRepository.getMeta("series", "series1", false) } returns
                Outcome.Success(MetaResult(meta, MetaSource.FRESH))
            coEvery { progressRepository.observeEpisodeProgress("series1") } returns
                flowOf(
                    mapOf(
                        "e4" to
                            EpisodeProgress(
                                "e4",
                                positionMs = 45_000L,
                                durationMs = 50_000L,
                                watched = true,
                                updatedAt = 1000L,
                            ),
                    ),
                )

            val viewModel = createViewModel("series", "series1", clock = { 1_700_000_000_000L })

            val content = viewModel.uiState.value as DetailUiState.Content
            val ready = content.primaryPlay as PrimaryPlayState.Ready
            val next = ready.intent as PlayIntent.Next
            assertEquals("e5", next.videoId)
            assertEquals(1, next.season)
            assertEquals(5, next.episode)
            assertEquals("e5", content.activeVideoId)
        }

    @Test
    fun DV5_userSelectsAnotherSeasonTab_primaryStateUnchanged() =
        runTest(testDispatcher) {
            val videos =
                listOf(
                    Video("s1e4", "Ep 4", "2020-01-01", 1, 4, null, null),
                    Video("s2e1", "Ep 1", "2020-01-01", 2, 1, null, null),
                )
            val meta = testMeta("series1", type = "series", videos = videos)
            coEvery { metaRepository.getMeta("series", "series1", false) } returns
                Outcome.Success(MetaResult(meta, MetaSource.FRESH))
            coEvery { progressRepository.observeEpisodeProgress("series1") } returns
                flowOf(
                    mapOf(
                        "s1e4" to
                            EpisodeProgress(
                                "s1e4",
                                positionMs = 15_000L,
                                durationMs = 60_000L,
                                watched = false,
                                updatedAt = 1000L,
                            ),
                    ),
                )

            val viewModel = createViewModel("series", "series1")

            val initialContent = viewModel.uiState.value as DetailUiState.Content
            val initialPlay = initialContent.primaryPlay

            viewModel.onEvent(DetailEvent.SelectSeason(2))

            val updatedContent = viewModel.uiState.value as DetailUiState.Content
            assertEquals(2, updatedContent.selectedSeason)
            assertEquals(initialPlay, updatedContent.primaryPlay)
        }

    @Test
    fun DV6_progressFlowEmitsNewRowWhileDetailOpen_stateUpdatesWithoutReopening() =
        runTest(testDispatcher) {
            val progressFlow = MutableStateFlow<Map<String, EpisodeProgress>>(emptyMap())
            coEvery { progressRepository.observeEpisodeProgress("series1") } returns progressFlow
            val videos =
                listOf(
                    Video("e1", "Ep 1", "2020-01-01", 1, 1, null, null),
                    Video("e2", "Ep 2", "2020-01-01", 1, 2, null, null),
                )
            val meta = testMeta("series1", type = "series", videos = videos)
            coEvery { metaRepository.getMeta("series", "series1", false) } returns
                Outcome.Success(MetaResult(meta, MetaSource.FRESH))

            val viewModel = createViewModel("series", "series1")

            val initialContent = viewModel.uiState.value as DetailUiState.Content
            val initialReady = initialContent.primaryPlay as PrimaryPlayState.Ready
            assertEquals(PlayIntent.Start, initialReady.intent)

            progressFlow.value =
                mapOf(
                    "e1" to
                        EpisodeProgress(
                            "e1",
                            positionMs = 20_000L,
                            durationMs = 60_000L,
                            watched = false,
                            updatedAt = 2000L,
                        ),
                )

            val updatedContent = viewModel.uiState.value as DetailUiState.Content
            val updatedReady = updatedContent.primaryPlay as PrimaryPlayState.Ready
            val resume = updatedReady.intent as PlayIntent.Resume
            assertEquals("e1", resume.videoId)
        }

    @Test
    fun DV7_tapOnResume_movieAndEpisode_opensStreamsSheetWithTargetVideoId() =
        runTest(testDispatcher) {
            // Movie Resume tap
            coEvery { progressRepository.observeEpisodeProgress("m1") } returns
                flowOf(
                    mapOf(
                        "m1" to
                            EpisodeProgress(
                                "m1",
                                positionMs = 15_000L,
                                durationMs = 60_000L,
                                watched = false,
                                updatedAt = 1000L,
                            ),
                    ),
                )
            val movieMeta = testMeta("m1", type = "movie")
            coEvery { metaRepository.getMeta("movie", "m1", false) } returns
                Outcome.Success(MetaResult(movieMeta, MetaSource.FRESH))

            val movieVm = createViewModel("movie", "m1")
            movieVm.onEvent(DetailEvent.PlayMain)

            val movieContent = movieVm.uiState.value as DetailUiState.Content
            assertTrue(movieContent.showStreamsSheet)
            assertEquals("m1", movieContent.streamsSheetVideoId)

            // Episode Resume tap
            val episodeVideos =
                listOf(
                    Video("e4", "Ep 4", "2020-01-01", 1, 4, null, null),
                )
            val seriesMeta = testMeta("series1", type = "series", videos = episodeVideos)
            coEvery { metaRepository.getMeta("series", "series1", false) } returns
                Outcome.Success(MetaResult(seriesMeta, MetaSource.FRESH))
            coEvery { progressRepository.observeEpisodeProgress("series1") } returns
                flowOf(
                    mapOf(
                        "e4" to
                            EpisodeProgress(
                                "e4",
                                positionMs = 15_000L,
                                durationMs = 60_000L,
                                watched = false,
                                updatedAt = 1000L,
                            ),
                    ),
                )

            val seriesVm = createViewModel("series", "series1")
            seriesVm.onEvent(DetailEvent.PlayMain)

            val seriesContent = seriesVm.uiState.value as DetailUiState.Content
            assertTrue(seriesContent.showStreamsSheet)
            assertEquals("e4", seriesContent.streamsSheetVideoId)
        }

    @Test
    fun DV8_tapOnNext_opensStreamsSheetWithNextEpisodeVideoId() =
        runTest(testDispatcher) {
            val videos =
                listOf(
                    Video("e4", "Ep 4", "2020-01-01", 1, 4, null, null),
                    Video("e5", "Ep 5", "2020-01-01", 1, 5, null, null),
                )
            val meta = testMeta("series1", type = "series", videos = videos)
            coEvery { metaRepository.getMeta("series", "series1", false) } returns
                Outcome.Success(MetaResult(meta, MetaSource.FRESH))
            coEvery { progressRepository.observeEpisodeProgress("series1") } returns
                flowOf(
                    mapOf(
                        "e4" to
                            EpisodeProgress(
                                "e4",
                                positionMs = 45_000L,
                                durationMs = 50_000L,
                                watched = true,
                                updatedAt = 1000L,
                            ),
                    ),
                )

            val viewModel = createViewModel("series", "series1", clock = { 1_700_000_000_000L })
            viewModel.onEvent(DetailEvent.PlayMain)

            val content = viewModel.uiState.value as DetailUiState.Content
            assertTrue(content.showStreamsSheet)
            assertEquals("e5", content.streamsSheetVideoId)
        }

    @Test
    fun DV9_clock_fakeClockChangesReleasedEpisodeResult() =
        runTest(testDispatcher) {
            val videos =
                listOf(
                    Video("e1", "Ep 1", "2020-01-01", 1, 1, null, null),
                    Video("e2", "Ep 2", "2025-06-01", 1, 2, null, null),
                )
            val meta = testMeta("series1", type = "series", videos = videos)
            coEvery { metaRepository.getMeta("series", "series1", false) } returns
                Outcome.Success(MetaResult(meta, MetaSource.FRESH))
            coEvery { progressRepository.observeEpisodeProgress("series1") } returns
                flowOf(
                    mapOf(
                        "e1" to
                            EpisodeProgress(
                                "e1",
                                positionMs = 50_000L,
                                durationMs = 50_000L,
                                watched = true,
                                updatedAt = 1000L,
                            ),
                    ),
                )

            // Clock set before 2025-06-01 (e.g. 2024-01-01 epoch: 1704067200000L)
            val pastClock = { 1_704_067_200_000L }
            val pastVm = createViewModel("series", "series1", clock = pastClock)
            val pastContent = pastVm.uiState.value as DetailUiState.Content
            val pastReady = pastContent.primaryPlay as PrimaryPlayState.Ready
            // S1E2 not released yet -> Start
            assertEquals(PlayIntent.Start, pastReady.intent)

            // Clock set after 2025-06-01 (e.g. 2026-01-01 epoch: 1767225600000L)
            val futureClock = { 1_767_225_600_000L }
            val futureVm = createViewModel("series", "series1", clock = futureClock)
            val futureContent = futureVm.uiState.value as DetailUiState.Content
            val futureReady = futureContent.primaryPlay as PrimaryPlayState.Ready
            // S1E2 released -> Next("e2", 1, 2)
            assertEquals(PlayIntent.Next("e2", 1, 2), futureReady.intent)
        }
}
