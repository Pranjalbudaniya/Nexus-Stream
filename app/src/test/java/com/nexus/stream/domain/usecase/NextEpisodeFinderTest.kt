package com.nexus.stream.domain.usecase

import com.nexus.stream.domain.model.AppError
import com.nexus.stream.domain.model.MetaDetail
import com.nexus.stream.domain.model.Outcome
import com.nexus.stream.domain.model.Video
import com.nexus.stream.domain.repository.MetaRepository
import com.nexus.stream.domain.repository.MetaResult
import com.nexus.stream.domain.repository.MetaSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class NextEpisodeFinderTest {
    private val metaRepository = mockk<MetaRepository>()
    private val clockTime = 1_800_000_000_000L
    private val finder = NextEpisodeFinder(metaRepository, clock = { clockTime })

    private fun testMeta(
        videos: List<Video>,
        type: String = "series",
    ) = MetaDetail(
        id = "s1",
        type = type,
        name = "Series",
        poster = null,
        posterShape = com.nexus.stream.domain.model.PosterShape.POSTER,
        background = null,
        logo = null,
        description = null,
        releaseInfo = "2020",
        released = "2020-01-01",
        runtime = null,
        imdbRating = null,
        genres = emptyList(),
        cast = emptyList(),
        directors = emptyList(),
        country = null,
        language = null,
        website = null,
        links = emptyList(),
        trailers = emptyList(),
        videos = videos,
        defaultVideoId = null,
    )

    private fun video(
        season: Int?,
        episode: Int?,
        released: String? = "2020-01-01T00:00:00.000Z",
        id: String = "s1:$season:$episode",
        title: String = "Ep $episode",
    ) = Video(
        id = id,
        title = title,
        released = released,
        season = season,
        episode = episode,
        thumbnail = null,
        overview = null,
    )

    @Test
    fun NF1_series_cachedMeta_returnsTheNextEpisode() =
        runTest {
            val v1 = video(1, 1)
            val v2 = video(1, 2)
            val meta = testMeta(listOf(v1, v2))
            coEvery { metaRepository.getMeta("series", "s1", forceRefresh = false) } returns
                Outcome.Success(MetaResult(meta, MetaSource.CACHE))

            val next = finder.find("series", "s1", "s1:1:1")
            assertNotNull(next)
            assertEquals("s1:1:2", next?.videoId)
            assertEquals(1, next?.season)
            assertEquals(2, next?.episode)
            assertEquals("Ep 2", next?.episodeTitle)
        }

    @Test
    fun NF2_movie_returnsNull_withoutCallingTheRepository() =
        runTest {
            val nextMovie = finder.find("movie", "m1", "m1")
            val nextMovieUpper = finder.find("MOVIE", "m1", "m1")

            assertNull(nextMovie)
            assertNull(nextMovieUpper)
            coVerify(exactly = 0) { metaRepository.getMeta(any(), any(), any()) }
        }

    @Test
    fun NF3_metaFailure_returnsNull() =
        runTest {
            coEvery { metaRepository.getMeta("series", "s1", forceRefresh = false) } returns
                Outcome.Failure(AppError.Network)

            val next = finder.find("series", "s1", "s1:1:1")
            assertNull(next)
        }

    @Test
    fun NF4_noNextEpisode_returnsNull() =
        runTest {
            val v1 = video(1, 1)
            val meta = testMeta(listOf(v1))
            coEvery { metaRepository.getMeta("series", "s1", forceRefresh = false) } returns
                Outcome.Success(MetaResult(meta, MetaSource.FRESH))

            val next = finder.find("series", "s1", "s1:1:1")
            assertNull(next)
        }

    @Test
    fun NF5_forceRefreshIsFalse() =
        runTest {
            val v1 = video(1, 1)
            val v2 = video(1, 2)
            val meta = testMeta(listOf(v1, v2))
            coEvery { metaRepository.getMeta("series", "s1", false) } returns
                Outcome.Success(MetaResult(meta, MetaSource.FRESH))

            finder.find("series", "s1", "s1:1:1")
            coVerify(exactly = 1) { metaRepository.getMeta("series", "s1", forceRefresh = false) }
        }

    @Test(expected = CancellationException::class)
    fun NF6_cancellation_propagates() =
        runTest {
            coEvery { metaRepository.getMeta("series", "s1", false) } throws
                CancellationException("Cancelled")

            finder.find("series", "s1", "s1:1:1")
        }

    @Test
    fun NF7_staleMeta_isStillUsed() =
        runTest {
            val v1 = video(1, 1)
            val v2 = video(1, 2)
            val meta = testMeta(listOf(v1, v2))
            coEvery { metaRepository.getMeta("series", "s1", false) } returns
                Outcome.Success(MetaResult(meta, MetaSource.STALE))

            val next = finder.find("series", "s1", "s1:1:1")
            assertNotNull(next)
            assertEquals("s1:1:2", next?.videoId)
        }
}
