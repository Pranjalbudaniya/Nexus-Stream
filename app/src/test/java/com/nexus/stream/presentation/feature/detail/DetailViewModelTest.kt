package com.nexus.stream.presentation.feature.detail

import androidx.lifecycle.SavedStateHandle
import com.nexus.stream.domain.model.AppError
import com.nexus.stream.domain.model.EpisodeProgress
import com.nexus.stream.domain.model.EpisodeRef
import com.nexus.stream.domain.model.MetaDetail
import com.nexus.stream.domain.model.Outcome
import com.nexus.stream.domain.model.PosterShape
import com.nexus.stream.domain.model.Video
import com.nexus.stream.domain.model.toPreview
import com.nexus.stream.domain.repository.LibraryRepository
import com.nexus.stream.domain.repository.MetaRepository
import com.nexus.stream.domain.repository.MetaResult
import com.nexus.stream.domain.repository.MetaSource
import com.nexus.stream.domain.repository.ProgressRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
        coEvery { libraryRepository.observeWatchlistIds() } returns flowOf(emptySet())
        coEvery { progressRepository.observeEpisodeProgress(any()) } returns flowOf(emptyMap())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(
        type: String = "movie",
        id: String = "m1",
    ): DetailViewModel {
        val handle = SavedStateHandle(mapOf("type" to type, "id" to id))
        return DetailViewModel(
            savedStateHandle = handle,
            metaRepository = metaRepository,
            libraryRepository = libraryRepository,
            progressRepository = progressRepository,
            clock = { fixedTime },
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
            coEvery { libraryRepository.observeWatchlistIds() } returns flowOf(setOf("m1"))

            val viewModel = createViewModel("movie", "m1")
            assertTrue((viewModel.uiState.value as DetailUiState.Content).inWatchlist)

            viewModel.onEvent(DetailEvent.ToggleWatchlist)

            coVerify(exactly = 1) {
                libraryRepository.toggleWatchlist(meta.toPreview())
            }
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
}
