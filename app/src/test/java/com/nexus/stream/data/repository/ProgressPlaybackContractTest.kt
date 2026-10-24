package com.nexus.stream.data.repository

import com.nexus.stream.data.local.db.TransactionRunner
import com.nexus.stream.data.local.db.dao.FakeLibraryDao
import com.nexus.stream.data.local.db.dao.FakeProgressDao
import com.nexus.stream.data.local.db.entity.LibraryItemEntity
import com.nexus.stream.domain.model.EpisodeRef
import com.nexus.stream.domain.model.ListStatus
import com.nexus.stream.domain.model.MetaDetail
import com.nexus.stream.domain.model.MetaPreview
import com.nexus.stream.domain.model.PlaybackSource
import com.nexus.stream.domain.model.PosterShape
import com.nexus.stream.domain.model.ProgressSample
import com.nexus.stream.domain.model.Video
import com.nexus.stream.domain.usecase.ContinueWatchingRules
import com.nexus.stream.domain.usecase.SaveProgress
import com.nexus.stream.presentation.common.buildPlaybackRequest
import com.nexus.stream.presentation.feature.player.PlaybackSelection
import com.nexus.stream.presentation.feature.player.toProgressTarget
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressPlaybackContractTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private val fakeLibraryDao = FakeLibraryDao()
    private val fakeProgressDao = FakeProgressDao(fakeLibraryDao)

    private val transactionRunner =
        object : TransactionRunner {
            override suspend fun <T> run(block: suspend () -> T): T = block()
        }

    private var currentTime = 1000L

    private val progressRepo =
        ProgressRepositoryImpl(
            progressDao = fakeProgressDao,
            libraryDao = fakeLibraryDao,
            transactionRunner = transactionRunner,
            continueWatchingRules = ContinueWatchingRules(),
            dispatcher = testDispatcher,
            clock = { currentTime },
        )

    private val libraryRepo =
        LibraryRepositoryImpl(
            libraryDao = fakeLibraryDao,
            transactionRunner = transactionRunner,
            dispatcher = testDispatcher,
            clock = { currentTime },
        )

    private val saveProgress =
        SaveProgress(
            progress = progressRepo,
            library = libraryRepo,
        )

    @Before
    fun setUp() {
        currentTime = 1000L
    }

    private fun createMetaDetail(
        id: String,
        type: String,
        name: String,
        poster: String? = "https://poster.jpg",
        videos: List<Video> = emptyList(),
    ): MetaDetail =
        MetaDetail(
            id = id,
            type = type,
            name = name,
            poster = poster,
            posterShape = PosterShape.POSTER,
            background = null,
            logo = null,
            description = "Test description",
            releaseInfo = "2024",
            released = "2024-01-01",
            runtime = "120 min",
            imdbRating = "8.0",
            genres = emptyList(),
            cast = emptyList(),
            directors = emptyList(),
            country = null,
            language = null,
            website = null,
            links = emptyList(),
            trailers = emptyList(),
            videos = videos,
            defaultVideoId = videos.firstOrNull()?.id,
        )

    private fun MetaDetail.toMetaPreview(): MetaPreview =
        MetaPreview(
            id = id,
            type = type,
            name = name,
            poster = poster,
            posterShape = posterShape,
            background = background,
            logo = logo,
            description = description,
            releaseInfo = releaseInfo,
            imdbRating = imdbRating,
            genres = genres,
        )

    private fun createVideo(
        id: String,
        title: String,
        season: Int? = null,
        episode: Int? = null,
    ): Video =
        Video(
            id = id,
            title = title,
            released = "2024-01-01",
            season = season,
            episode = episode,
            thumbnail = null,
            overview = null,
        )

    @Test
    fun CT1_seriesEpisodePlayedToNinetyPercent_showsAsWatchedInObserveEpisodeProgress() =
        runTest(testDispatcher) {
            val meta =
                createMetaDetail(
                    id = "series1",
                    type = "series",
                    name = "Series 1",
                    poster = "https://poster.jpg",
                    videos =
                        listOf(
                            createVideo(
                                id = "series1:1:1",
                                title = "Episode 1",
                                season = 1,
                                episode = 1,
                            ),
                        ),
                )
            val video = meta.videos.first()
            val req = buildPlaybackRequest(meta, video.id, "${meta.name} - ${video.title}")
            val selection =
                PlaybackSelection(
                    title = req.title,
                    source = PlaybackSource.Direct("https://example.com/v.mp4"),
                    type = req.type,
                    metaId = req.metaId,
                    videoId = req.videoId,
                    logoUrl = req.logoUrl,
                    backgroundUrl = req.backgroundUrl,
                    subtitleHints = null,
                    season = req.season,
                    episode = req.episode,
                    posterUrl = req.logoUrl,
                )
            val target = selection.toProgressTarget()!!

            // Play to 90% (90s of 100s)
            val sample = ProgressSample(positionMs = 90_000L, durationMs = 100_000L, ended = false)
            saveProgress(target, sample, firstOfSession = true, nowMs = currentTime)

            val progressMap = progressRepo.observeEpisodeProgress(meta.id).first()
            val episodeProgress = progressMap[video.id]
            assertNotNull("Episode should be in progress map", episodeProgress)
            assertTrue("Episode should be marked watched", episodeProgress!!.watched)
        }

    @Test
    fun CT2_moviePlayedToNinetyPercent_observeWatchedMovieIdsContainsId() =
        runTest(testDispatcher) {
            val meta =
                createMetaDetail(
                    id = "movie1",
                    type = "movie",
                    name = "Movie 1",
                    poster = "https://poster.jpg",
                )
            val req = buildPlaybackRequest(meta, meta.id, meta.name)
            val selection =
                PlaybackSelection(
                    title = req.title,
                    source = PlaybackSource.Direct("https://example.com/v.mp4"),
                    type = req.type,
                    metaId = req.metaId,
                    videoId = req.videoId,
                    logoUrl = req.logoUrl,
                    backgroundUrl = req.backgroundUrl,
                    subtitleHints = null,
                    season = req.season,
                    episode = req.episode,
                    posterUrl = req.logoUrl,
                )
            val target = selection.toProgressTarget()!!

            val sample = ProgressSample(positionMs = 90_000L, durationMs = 100_000L, ended = false)
            saveProgress(target, sample, firstOfSession = true, nowMs = currentTime)

            val watchedIds = progressRepo.observeWatchedMovieIds().first()
            assertTrue("Watched movie ids should contain movie1", watchedIds.contains("movie1"))
        }

    @Test
    fun CT3_movieAtNinetySecondsWithNameAndPoster_returnsWithNonBlankPoster() =
        runTest(testDispatcher) {
            val selection =
                PlaybackSelection(
                    title = "Movie 1",
                    source = PlaybackSource.Direct("https://example.com/v.mp4"),
                    type = "movie",
                    metaId = "movie1",
                    videoId = "movie1",
                    logoUrl = null,
                    backgroundUrl = null,
                    subtitleHints = null,
                    season = null,
                    episode = null,
                    posterUrl = "https://poster.jpg",
                )
            val target = selection.toProgressTarget()!!

            val sample = ProgressSample(positionMs = 90_000L, durationMs = 120_000L, ended = false)
            saveProgress(target, sample, firstOfSession = true, nowMs = currentTime)

            val continueItems = progressRepo.observeContinueWatching().first()
            val item = continueItems.find { it.metaId == "movie1" }
            assertNotNull("Continue watching item should exist", item)
            assertEquals("Name should match", "Movie 1", item!!.name)
            assertNotNull("Poster should not be null", item.poster)
            assertEquals("Poster URL should match", "https://poster.jpg", item.poster)
        }

    @Test
    fun CT4_markedWatchedThenPlayed_exactlyOneRow() =
        runTest(testDispatcher) {
            val metaPreview =
                MetaPreview(
                    id = "series1",
                    type = "series",
                    name = "Series 1",
                    poster = "https://poster.jpg",
                    posterShape = PosterShape.POSTER,
                    background = null,
                    logo = null,
                    description = "Desc",
                    releaseInfo = "2024",
                    imdbRating = "8.0",
                    genres = listOf("Drama"),
                )
            val episodeRef = EpisodeRef(videoId = "series1:1:1", type = "series", season = 1, episode = 1)

            // Step 1: Mark watched via setWatched (D61)
            progressRepo.setWatched(metaPreview, episodeRef, true)

            // Step 2: Play the same episode
            val selection =
                PlaybackSelection(
                    title = "Series 1 - Episode 1",
                    source = PlaybackSource.Direct("https://example.com/v.mp4"),
                    type = "series",
                    metaId = "series1",
                    videoId = "series1:1:1",
                    logoUrl = null,
                    backgroundUrl = null,
                    subtitleHints = null,
                    season = 1,
                    episode = 1,
                    posterUrl = "https://poster.jpg",
                )
            val target = selection.toProgressTarget()!!
            val sample = ProgressSample(positionMs = 50_000L, durationMs = 100_000L, ended = false)
            saveProgress(target, sample, firstOfSession = true, nowMs = currentTime + 1000L)

            val rows =
                fakeProgressDao.progressEntities.value.values
                    .filter { it.metaId == "series1" }
            assertEquals("Should have exactly one row for this episode", 1, rows.size)
        }

    @Test
    fun CT5_episodePlayedToNinetyPercent_thenSetWatchedFalse_rowDeletedAndResumeNull() =
        runTest(testDispatcher) {
            val meta =
                createMetaDetail(
                    id = "series_ct5",
                    type = "series",
                    name = "Series CT5",
                    poster = "https://poster.jpg",
                    videos =
                        listOf(
                            createVideo(
                                id = "series_ct5:1:1",
                                title = "Episode 1",
                                season = 1,
                                episode = 1,
                            ),
                        ),
                )
            val video = meta.videos.first()
            val req = buildPlaybackRequest(meta, video.id, "${meta.name} - ${video.title}")
            val selection =
                PlaybackSelection(
                    title = req.title,
                    source = PlaybackSource.Direct("https://example.com/v.mp4"),
                    type = req.type,
                    metaId = req.metaId,
                    videoId = req.videoId,
                    logoUrl = req.logoUrl,
                    backgroundUrl = req.backgroundUrl,
                    subtitleHints = null,
                    season = req.season,
                    episode = req.episode,
                    posterUrl = req.posterUrl,
                )
            val target = selection.toProgressTarget()!!

            // Play to 90%
            val sample = ProgressSample(positionMs = 90_000L, durationMs = 100_000L, ended = false)
            saveProgress(target, sample, firstOfSession = true, nowMs = currentTime)

            var epProgress = progressRepo.observeEpisodeProgress(meta.id).first()[video.id]
            assertNotNull("Episode should be recorded", epProgress)
            assertTrue("Episode should be watched", epProgress!!.watched)

            // Unwatch via setWatched(..., false)
            val episodeRef =
                EpisodeRef(videoId = video.id, type = meta.type, season = video.season, episode = video.episode)
            progressRepo.setWatched(meta.toMetaPreview(), episodeRef, false)

            val row = fakeProgressDao.get("series_ct5|series_ct5:1:1")
            assertNull("Row should be deleted", row)

            epProgress = progressRepo.observeEpisodeProgress(meta.id).first()[video.id]
            assertTrue("Episode should not be watched", epProgress == null || !epProgress.watched)

            val saved = progressRepo.get("series_ct5|series_ct5:1:1")
            assertNull("WatchProgress should be null after unwatch", saved)
            val resume =
                com.nexus.stream.domain.usecase.ProgressRules
                    .resumePositionMs(saved, 100_000L)
            assertNull("Resume position should be null", resume)
        }

    @Test
    fun CT6_unlistedMoviePlayedNinetySecondsThenToNinetyFivePercent_watchingThenCompleted() =
        runTest(testDispatcher) {
            val selection =
                PlaybackSelection(
                    title = "Unlisted Movie",
                    source = PlaybackSource.Direct("https://example.com/v.mp4"),
                    type = "movie",
                    metaId = "movie_unlisted",
                    videoId = "movie_unlisted",
                    logoUrl = null,
                    backgroundUrl = null,
                    subtitleHints = null,
                    season = null,
                    episode = null,
                    posterUrl = "https://poster.jpg",
                )
            val target = selection.toProgressTarget()!!

            // Step 1: 90s (qualifying sample >= 60s)
            val sample90s = ProgressSample(positionMs = 90_000L, durationMs = 120_000L, ended = false)
            saveProgress(target, sample90s, firstOfSession = true, nowMs = currentTime)

            var statuses = libraryRepo.observeListStatuses().first()
            assertEquals("Should be WATCHING after 90s", ListStatus.WATCHING, statuses["movie_unlisted"])

            // Step 2: 95% (watched sample)
            val sample95Percent = ProgressSample(positionMs = 114_000L, durationMs = 120_000L, ended = false)
            saveProgress(target, sample95Percent, firstOfSession = false, nowMs = currentTime + 1000L)

            statuses = libraryRepo.observeListStatuses().first()
            assertEquals("Should be COMPLETED after 95%", ListStatus.COMPLETED, statuses["movie_unlisted"])
        }

    @Test
    fun CT7_existingLibraryRowDropped_ensureSnapshotPreservesWatchlistStatusAndRefreshesFields() =
        runTest(testDispatcher) {
            val addedAt = 12345L
            fakeLibraryDao.upsert(
                LibraryItemEntity(
                    metaId = "m_ct7",
                    type = "movie",
                    name = "",
                    poster = null,
                    background = null,
                    inWatchlist = true,
                    listStatus = ListStatus.DROPPED.name,
                    addedAt = addedAt,
                ),
            )

            val selection =
                PlaybackSelection(
                    title = "Movie CT7",
                    source = PlaybackSource.Direct("https://example.com/v.mp4"),
                    type = "movie",
                    metaId = "m_ct7",
                    videoId = "m_ct7",
                    logoUrl = null,
                    backgroundUrl = "https://bg.jpg",
                    subtitleHints = null,
                    season = null,
                    episode = null,
                    posterUrl = "https://poster.jpg",
                )
            val target = selection.toProgressTarget()!!

            libraryRepo.ensureSnapshot(target)

            val entity = fakeLibraryDao.get("m_ct7")
            assertNotNull("Entity should exist", entity)
            assertTrue("inWatchlist must stay true", entity!!.inWatchlist)
            assertEquals("listStatus must stay DROPPED", ListStatus.DROPPED.name, entity.listStatus)
            assertEquals("addedAt must stay unchanged", addedAt, entity.addedAt)
            assertEquals("name should be refreshed", "Movie CT7", entity.name)
            assertEquals("poster should be refreshed", "https://poster.jpg", entity.poster)
            assertEquals("background should be refreshed", "https://bg.jpg", entity.background)
        }

    @Test
    fun CT8_existingRowWithPosterA_ensureSnapshotWithPosterNull_posterStaysA() =
        runTest(testDispatcher) {
            fakeLibraryDao.upsert(
                LibraryItemEntity(
                    metaId = "m_ct8",
                    type = "movie",
                    name = "Movie CT8",
                    poster = "https://poster-a.jpg",
                    background = null,
                    inWatchlist = false,
                    listStatus = ListStatus.PLAN_TO_WATCH.name,
                    addedAt = 5000L,
                ),
            )

            val selection =
                PlaybackSelection(
                    title = "Movie CT8",
                    source = PlaybackSource.Direct("https://example.com/v.mp4"),
                    type = "movie",
                    metaId = "m_ct8",
                    videoId = "m_ct8",
                    logoUrl = null,
                    backgroundUrl = null,
                    subtitleHints = null,
                    season = null,
                    episode = null,
                    posterUrl = null,
                )
            val target = selection.toProgressTarget()!!

            libraryRepo.ensureSnapshot(target)

            val entity = fakeLibraryDao.get("m_ct8")
            assertNotNull("Entity should exist", entity)
            assertEquals("Poster should stay https://poster-a.jpg", "https://poster-a.jpg", entity!!.poster)
        }
}
