package com.nexus.stream.data.repository

import com.nexus.stream.data.local.db.dao.FakeProgressDao
import com.nexus.stream.data.local.db.entity.WatchProgressEntity
import com.nexus.stream.domain.model.ContinueRow
import com.nexus.stream.domain.usecase.ContinueWatchingRules
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressRepositoryImplTest {
    private val fakeDao = FakeProgressDao()
    private val testDispatcher = UnconfinedTestDispatcher()
    private val rules = ContinueWatchingRules()

    private val repository =
        ProgressRepositoryImpl(
            progressDao = fakeDao,
            continueWatchingRules = rules,
            dispatcher = testDispatcher,
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
    fun `removeFromContinueWatching removes every progress row of that meta and leaves other titles`() =
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

            assertTrue(fakeDao.deletedMetaIds.contains("m1"))
            val remaining = fakeDao.progressEntities.value
            assertEquals(1, remaining.size)
            assertTrue(remaining.containsKey("m2|v1"))
        }
}
