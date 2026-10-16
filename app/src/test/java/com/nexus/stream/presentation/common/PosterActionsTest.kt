package com.nexus.stream.presentation.common

import com.nexus.stream.domain.model.ListStatus
import com.nexus.stream.domain.model.MetaDetail
import com.nexus.stream.domain.model.MetaPreview
import com.nexus.stream.domain.model.Outcome
import com.nexus.stream.domain.model.PosterShape
import com.nexus.stream.domain.repository.LibraryRepository
import com.nexus.stream.domain.repository.MetaRepository
import com.nexus.stream.domain.repository.MetaResult
import com.nexus.stream.domain.repository.MetaSource
import com.nexus.stream.domain.repository.ProgressRepository
import com.nexus.stream.domain.usecase.PosterAction
import com.nexus.stream.domain.usecase.TrailerState
import com.nexus.stream.domain.usecase.posterActions
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PosterActionsTest {
    private val testDispatcher = StandardTestDispatcher()
    private val metaRepository: MetaRepository = mockk()
    private val libraryRepository: LibraryRepository = mockk(relaxed = true)
    private val progressRepository: ProgressRepository = mockk(relaxed = true)

    private val listStatusesFlow = MutableStateFlow<Map<String, ListStatus>>(emptyMap())

    private val movieItem =
        MetaPreview(
            id = "tt0111161",
            type = "movie",
            name = "The Shawshank Redemption",
            poster = "https://example.com/poster.jpg",
            posterShape = PosterShape.POSTER,
            background = null,
            logo = null,
            description = null,
            releaseInfo = "1994",
            imdbRating = "9.3",
            genres = listOf("Drama"),
        )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { libraryRepository.observeListStatuses() } returns listStatusesFlow
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `posterActions for movie includes Play, Detail, Trailer, Watchlist`() {
        val actions =
            posterActions(
                type = "movie",
                trailer = TrailerState.AVAILABLE,
                status = null,
                fromContinue = false,
            )

        assertEquals(4, actions.size)
        assertTrue(actions[0] is PosterAction.Play)
        assertTrue(actions[1] is PosterAction.Detail)
        assertTrue(actions[2] is PosterAction.Trailer)
        assertTrue(actions[3] is PosterAction.Watchlist)
    }

    @Test
    fun `posterActions for series includes Detail, Trailer, Watchlist and no Play`() {
        val actions =
            posterActions(
                type = "series",
                trailer = TrailerState.CHECKING,
                status = ListStatus.WATCHING,
                fromContinue = false,
            )

        assertEquals(3, actions.size)
        assertTrue(actions[0] is PosterAction.Detail)
        assertTrue(actions[1] is PosterAction.Trailer)
        val watchlistAction = actions[2] as PosterAction.Watchlist
        assertEquals(ListStatus.WATCHING, watchlistAction.status)
    }

    @Test
    fun `posterActions from continue includes RemoveFromContinue as last action`() {
        val actions =
            posterActions(
                type = "movie",
                trailer = TrailerState.NONE,
                status = null,
                fromContinue = true,
            )

        assertEquals(5, actions.size)
        assertTrue(actions[4] is PosterAction.RemoveFromContinue)
    }

    @Test
    fun `viewModel show resolves trailer availability`() =
        runTest {
            val detail =
                MetaDetail(
                    id = movieItem.id,
                    type = movieItem.type,
                    name = movieItem.name,
                    poster = movieItem.poster,
                    posterShape = PosterShape.POSTER,
                    background = null,
                    logo = null,
                    description = "Two imprisoned men bond over a number of years...",
                    releaseInfo = "1994",
                    released = "1994-10-14",
                    runtime = "142 min",
                    imdbRating = "9.3",
                    genres = listOf("Drama"),
                    cast = emptyList(),
                    directors = emptyList(),
                    country = null,
                    language = null,
                    website = null,
                    links = emptyList(),
                    trailers = listOf("dQw4w9WgXcQ"),
                    videos = emptyList(),
                    defaultVideoId = null,
                )
            coEvery { metaRepository.getMeta("movie", movieItem.id) } returns
                Outcome.Success(MetaResult(meta = detail, source = MetaSource.FRESH))

            val viewModel = PosterActionsViewModel(metaRepository, libraryRepository, progressRepository)
            viewModel.show(movieItem, fromContinue = false)

            val initialState = viewModel.sheetState.value
            assertNotNull(initialState)
            assertEquals(TrailerState.CHECKING, initialState?.trailerState)

            advanceUntilIdle()

            val finalState = viewModel.sheetState.value
            assertNotNull(finalState)
            assertEquals(TrailerState.AVAILABLE, finalState?.trailerState)
            assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", finalState?.trailerUrl)
        }

    @Test
    fun `viewModel setStatus delegates to libraryRepository`() =
        runTest {
            val viewModel = PosterActionsViewModel(metaRepository, libraryRepository, progressRepository)

            viewModel.setStatus(movieItem, ListStatus.COMPLETED)
            advanceUntilIdle()

            coVerify { libraryRepository.setStatus(movieItem, ListStatus.COMPLETED) }
        }

    @Test
    fun `viewModel removeFromContinue delegates to progressRepository`() =
        runTest {
            val viewModel = PosterActionsViewModel(metaRepository, libraryRepository, progressRepository)

            viewModel.removeFromContinue(movieItem.id)
            advanceUntilIdle()

            coVerify { progressRepository.removeFromContinueWatching(movieItem.id) }
        }
}
