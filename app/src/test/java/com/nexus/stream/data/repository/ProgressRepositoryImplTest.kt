package com.nexus.stream.data.repository

import com.nexus.stream.data.local.db.TransactionRunner
import com.nexus.stream.data.local.db.dao.FakeLibraryDao
import com.nexus.stream.data.local.db.dao.FakeProgressDao
import com.nexus.stream.data.local.db.entity.LibraryItemEntity
import com.nexus.stream.data.local.db.entity.WatchProgressEntity
import com.nexus.stream.domain.model.ContinueKind
import com.nexus.stream.domain.model.ContinueRow
import com.nexus.stream.domain.model.EpisodeRef
import com.nexus.stream.domain.model.ListStatus
import com.nexus.stream.domain.model.MetaPreview
import com.nexus.stream.domain.model.PosterShape
import com.nexus.stream.domain.usecase.ContinueWatchingRules
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressRepositoryImplTest {
    private val fakeDao = FakeProgressDao()
    private val fakeLibraryDao = FakeLibraryDao()
    private val testDispatcher = UnconfinedTestDispatcher()
    private val rules = ContinueWatchingRules()
    private var currentTime = 1000L

    private val transactionRunner =
        object : TransactionRunner {
            override suspend fun <T> run(block: suspend () -> T): T = block()
        }

    private val repository =
        ProgressRepositoryImpl(
            progressDao = fakeDao,
            libraryDao = fakeLibraryDao,
            transactionRunner = transactionRunner,
            continueWatchingRules = rules,
            dispatcher = testDispatcher,
            clock = { currentTime },
        )

    private fun testPreview(id: String = "m1") =
        MetaPreview(
            id = id,
            type = "series",
            name = "Test Series",
            poster = "https://poster.jpg",
            posterShape = PosterShape.POSTER,
            background = "https://bg.jpg",
            logo = null,
            description = "Desc",
            releaseInfo = "2024",
            imdbRating = "8.5",
            genres = listOf("Drama"),
        )

    @Test
    fun `observeContinueWatching applies the rules`() =
        runTest(testDispatcher) {
            val validRow =
                ContinueRow(
                    metaId = "m1",
                    type = "movie",
                    name = "Movie 1",
                    poster = "https://poster1.jpg",
                    videoId = "m1",
                    season = null,
                    episode = null,
                    positionMs = 120_000L,
                    durationMs = 600_000L,
                    watched = false,
                    updatedAt = 2000L,
                )
            val shortRow =
                ContinueRow(
                    metaId = "m2",
                    type = "movie",
                    name = "Movie 2",
                    poster = "https://poster2.jpg",
                    videoId = "m2",
                    season = null,
                    episode = null,
                    positionMs = 30_000L,
                    durationMs = 600_000L,
                    watched = false,
                    updatedAt = 1000L,
                )

            fakeDao.continueRows.value = listOf(validRow, shortRow)

            val result = repository.observeContinueWatching().first()
            assertEquals(1, result.size)
            assertEquals("m1", result[0].metaId)
            assertEquals("Movie 1", result[0].name)
        }

    @Test
    fun CR1_observeContinueWatching_passesTheClockToTheRules() =
        runTest(testDispatcher) {
            val watchedRow =
                ContinueRow(
                    metaId = "s1",
                    type = "series",
                    name = "Series 1",
                    poster = "https://poster.jpg",
                    videoId = "s1:1:1",
                    season = 1,
                    episode = 1,
                    positionMs = 1800_000L,
                    durationMs = 1800_000L,
                    watched = true,
                    updatedAt = 100_000L,
                    inWatchlist = true,
                    listStatus = ListStatus.WATCHING,
                )
            fakeDao.continueRows.value = listOf(watchedRow)

            currentTime = 100_000L + 5L * 24 * 60 * 60 * 1000L
            val result5Days = repository.observeContinueWatching().first()
            assertEquals(1, result5Days.size)
            assertEquals(ContinueKind.UP_NEXT, result5Days[0].kind)

            currentTime = 100_000L + 61L * 24 * 60 * 60 * 1000L
            val result61Days = repository.observeContinueWatching().first()
            assertTrue(result61Days.isEmpty())
        }

    @Test
    fun `removeFromContinueWatching removes only in-progress rows and keeps watched rows`() =
        runTest(testDispatcher) {
            fakeDao.upsert(
                WatchProgressEntity(
                    key = "m1|v1",
                    metaId = "m1",
                    videoId = "v1",
                    type = "series",
                    season = 1,
                    episode = 1,
                    positionMs = 100L,
                    durationMs = 200L,
                    watched = true,
                    updatedAt = 100L,
                ),
            )
            fakeDao.upsert(
                WatchProgressEntity(
                    key = "m1|v2",
                    metaId = "m1",
                    videoId = "v2",
                    type = "series",
                    season = 1,
                    episode = 2,
                    positionMs = 150L,
                    durationMs = 200L,
                    watched = false,
                    updatedAt = 200L,
                ),
            )
            fakeDao.upsert(
                WatchProgressEntity(
                    key = "m2|v1",
                    metaId = "m2",
                    videoId = "v1",
                    type = "movie",
                    season = null,
                    episode = null,
                    positionMs = 120_000L,
                    durationMs = 600_000L,
                    watched = false,
                    updatedAt = 300L,
                ),
            )

            repository.removeFromContinueWatching("m1")

            val remaining = fakeDao.progressEntities.value
            assertEquals(2, remaining.size)
            // Watched row for m1 is kept
            assertTrue(remaining.containsKey("m1|v1"))
            assertEquals(true, remaining["m1|v1"]?.watched)
            // In-progress row for m1 is removed
            assertFalse(remaining.containsKey("m1|v2"))
            // Other title is unaffected
            assertTrue(remaining.containsKey("m2|v1"))
        }

    @Test
    fun `clearInProgress removes only unwatched rows and keeps watched ones`() =
        runTest(testDispatcher) {
            fakeDao.upsert(
                WatchProgressEntity(
                    key = "m1|v1",
                    metaId = "m1",
                    videoId = "v1",
                    type = "series",
                    season = 1,
                    episode = 1,
                    positionMs = 0L,
                    durationMs = 0L,
                    watched = true,
                    updatedAt = 100L,
                ),
            )
            fakeDao.upsert(
                WatchProgressEntity(
                    key = "m1|v2",
                    metaId = "m1",
                    videoId = "v2",
                    type = "series",
                    season = 1,
                    episode = 2,
                    positionMs = 50L,
                    durationMs = 100L,
                    watched = false,
                    updatedAt = 200L,
                ),
            )

            repository.clearInProgress("m1")

            val remaining = fakeDao.progressEntities.value
            assertEquals(1, remaining.size)
            assertTrue(remaining.containsKey("m1|v1"))
            assertFalse(remaining.containsKey("m1|v2"))
        }

    @Test
    fun `series in progress leaves continue watching after clearInProgress while watched remain`() =
        runTest(testDispatcher) {
            fakeDao.upsert(
                WatchProgressEntity(
                    key = "s1|v1",
                    metaId = "s1",
                    videoId = "v1",
                    type = "series",
                    season = 1,
                    episode = 1,
                    positionMs = 0L,
                    durationMs = 0L,
                    watched = true,
                    updatedAt = 100L,
                ),
            )
            fakeDao.upsert(
                WatchProgressEntity(
                    key = "s1|v2",
                    metaId = "s1",
                    videoId = "v2",
                    type = "series",
                    season = 1,
                    episode = 2,
                    positionMs = 500_000L,
                    durationMs = 1_000_000L,
                    watched = false,
                    updatedAt = 200L,
                ),
            )
            fakeDao.continueRows.value =
                listOf(
                    ContinueRow(
                        metaId = "s1",
                        type = "series",
                        name = "Test Series",
                        poster = "https://poster.jpg",
                        videoId = "v2",
                        season = 1,
                        episode = 2,
                        positionMs = 500_000L,
                        durationMs = 1_000_000L,
                        watched = false,
                        updatedAt = 200L,
                    ),
                )

            val before = repository.observeContinueWatching().first()
            assertEquals(1, before.size)
            assertEquals("s1", before[0].metaId)

            repository.clearInProgress("s1")

            val after = repository.observeContinueWatching().first()
            assertTrue(after.isEmpty())

            val remaining = fakeDao.progressEntities.value
            assertEquals(1, remaining.size)
            assertTrue(remaining.containsKey("s1|v1"))
            assertEquals(true, remaining["s1|v1"]?.watched)
            assertFalse(remaining.containsKey("s1|v2"))
        }

    @Test
    fun `setWatchedMany true creates snapshot without changing inWatchlist and writes watched rows`() =
        runTest(testDispatcher) {
            currentTime = 6000L
            val preview = testPreview("s1")
            val episodes =
                listOf(
                    EpisodeRef(videoId = "s1:1:1", type = "series", season = 1, episode = 1),
                    EpisodeRef(videoId = "s1:1:2", type = "series", season = 1, episode = 2),
                )

            repository.setWatchedMany(meta = preview, episodes = episodes, watched = true)

            val snapshot = fakeLibraryDao.items.value["s1"]
            assertTrue(snapshot != null)
            assertEquals("Test Series", snapshot?.name)
            assertFalse(snapshot?.inWatchlist == true)

            val ep1 = fakeDao.progressEntities.value["s1|s1:1:1"]
            val ep2 = fakeDao.progressEntities.value["s1|s1:1:2"]
            assertEquals(true, ep1?.watched)
            assertEquals(true, ep2?.watched)
            assertEquals(6000L, ep1?.updatedAt)
            assertEquals(6000L, ep2?.updatedAt)
        }

    @Test
    fun `setWatchedMany false deletes only those video ids`() =
        runTest(testDispatcher) {
            fakeDao.upsert(
                WatchProgressEntity(
                    key = "s1|s1:1:1",
                    metaId = "s1",
                    videoId = "s1:1:1",
                    type = "series",
                    season = 1,
                    episode = 1,
                    positionMs = 0L,
                    durationMs = 0L,
                    watched = true,
                    updatedAt = 1000L,
                ),
            )
            fakeDao.upsert(
                WatchProgressEntity(
                    key = "s1|s1:1:2",
                    metaId = "s1",
                    videoId = "s1:1:2",
                    type = "series",
                    season = 1,
                    episode = 2,
                    positionMs = 0L,
                    durationMs = 0L,
                    watched = true,
                    updatedAt = 1000L,
                ),
            )
            fakeDao.upsert(
                WatchProgressEntity(
                    key = "s1|s1:1:3",
                    metaId = "s1",
                    videoId = "s1:1:3",
                    type = "series",
                    season = 1,
                    episode = 3,
                    positionMs = 0L,
                    durationMs = 0L,
                    watched = true,
                    updatedAt = 1000L,
                ),
            )

            val episodesToDelete =
                listOf(
                    EpisodeRef(videoId = "s1:1:1", type = "series", season = 1, episode = 1),
                    EpisodeRef(videoId = "s1:1:2", type = "series", season = 1, episode = 2),
                )

            repository.setWatchedMany(meta = testPreview("s1"), episodes = episodesToDelete, watched = false)

            val remaining = fakeDao.progressEntities.value
            assertEquals(1, remaining.size)
            assertNull(remaining["s1|s1:1:1"])
            assertNull(remaining["s1|s1:1:2"])
            assertTrue(remaining.containsKey("s1|s1:1:3"))
        }

    @Test
    fun `setWatched true creates snapshot without changing inWatchlist and writes watched row`() =
        runTest(testDispatcher) {
            currentTime = 5000L
            val preview = testPreview("s1")

            repository.setWatched(
                meta = preview,
                episode =
                    EpisodeRef(
                        videoId = "s1:1:1",
                        type = "series",
                        season = 1,
                        episode = 1,
                    ),
                watched = true,
            )

            val snapshot = fakeLibraryDao.items.value["s1"]
            assertTrue(snapshot != null)
            assertEquals("Test Series", snapshot?.name)
            assertFalse(snapshot?.inWatchlist == true)
            assertEquals(5000L, snapshot?.addedAt)

            val progress = fakeDao.progressEntities.value["s1|s1:1:1"]
            assertTrue(progress != null)
            assertEquals(true, progress?.watched)
            assertEquals(0L, progress?.positionMs)
            assertEquals(0L, progress?.durationMs)
            assertEquals(5000L, progress?.updatedAt)
        }

    @Test
    fun `setWatched true preserves existing inWatchlist state`() =
        runTest(testDispatcher) {
            fakeLibraryDao.upsert(
                LibraryItemEntity(
                    metaId = "s1",
                    type = "series",
                    name = "Existing Series",
                    poster = null,
                    background = null,
                    inWatchlist = true,
                    addedAt = 1000L,
                ),
            )

            repository.setWatched(
                meta = testPreview("s1"),
                episode =
                    EpisodeRef(
                        videoId = "s1:1:2",
                        type = "series",
                        season = 1,
                        episode = 2,
                    ),
                watched = true,
            )

            val snapshot = fakeLibraryDao.items.value["s1"]
            assertEquals(true, snapshot?.inWatchlist)
        }

    @Test
    fun `setWatched false deletes only that episode row`() =
        runTest(testDispatcher) {
            fakeDao.upsert(
                WatchProgressEntity(
                    key = "s1|s1:1:1",
                    metaId = "s1",
                    videoId = "s1:1:1",
                    type = "series",
                    season = 1,
                    episode = 1,
                    positionMs = 0L,
                    durationMs = 0L,
                    watched = true,
                    updatedAt = 1000L,
                ),
            )
            fakeDao.upsert(
                WatchProgressEntity(
                    key = "s1|s1:1:2",
                    metaId = "s1",
                    videoId = "s1:1:2",
                    type = "series",
                    season = 1,
                    episode = 2,
                    positionMs = 0L,
                    durationMs = 0L,
                    watched = true,
                    updatedAt = 1000L,
                ),
            )

            repository.setWatched(
                meta = testPreview("s1"),
                episode =
                    EpisodeRef(
                        videoId = "s1:1:1",
                        type = "series",
                        season = 1,
                        episode = 1,
                    ),
                watched = false,
            )

            assertTrue(fakeDao.deletedKeys.contains("s1|s1:1:1"))
            assertNull(fakeDao.progressEntities.value["s1|s1:1:1"])
            assertTrue(fakeDao.progressEntities.value.containsKey("s1|s1:1:2"))
        }

    @Test
    fun `observeEpisodeProgress maps rows by video id`() =
        runTest(testDispatcher) {
            fakeDao.upsert(
                WatchProgressEntity(
                    key = "s1|s1:1:1",
                    metaId = "s1",
                    videoId = "s1:1:1",
                    type = "series",
                    season = 1,
                    episode = 1,
                    positionMs = 30_000L,
                    durationMs = 120_000L,
                    watched = false,
                    updatedAt = 2000L,
                ),
            )
            fakeDao.upsert(
                WatchProgressEntity(
                    key = "s1|s1:1:2",
                    metaId = "s1",
                    videoId = "s1:1:2",
                    type = "series",
                    season = 1,
                    episode = 2,
                    positionMs = 0L,
                    durationMs = 0L,
                    watched = true,
                    updatedAt = 3000L,
                ),
            )

            val progressMap = repository.observeEpisodeProgress("s1").first()
            assertEquals(2, progressMap.size)
            assertEquals(30_000L, progressMap["s1:1:1"]?.positionMs)
            assertFalse(progressMap["s1:1:1"]?.watched == true)
            assertTrue(progressMap["s1:1:2"]?.watched == true)
        }
}
