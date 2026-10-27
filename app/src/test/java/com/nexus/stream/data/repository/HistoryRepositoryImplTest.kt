package com.nexus.stream.data.repository

import com.nexus.stream.data.local.db.dao.ProgressDao
import com.nexus.stream.data.local.db.entity.WatchProgressEntity
import com.nexus.stream.domain.model.ContinueRow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class HistoryRepositoryImplTest {
    private val historyRowsFlow = MutableStateFlow<List<ContinueRow>>(emptyList())
    private var lastRequestedLimit: Int? = null
    private var deletedMetaId: String? = null
    private var deleteAllCalledCount = 0

    private val fakeDao =
        object : ProgressDao {
            override fun observeContinueRows(): Flow<List<ContinueRow>> = error("unused")

            override suspend fun get(key: String): WatchProgressEntity? = error("unused")

            override suspend fun upsert(entity: WatchProgressEntity) = error("unused")

            override suspend fun upsertAll(entities: List<WatchProgressEntity>) = error("unused")

            override suspend fun deleteByMeta(metaId: String) {
                deletedMetaId = metaId
            }

            override fun observeByMeta(metaId: String): Flow<List<WatchProgressEntity>> = error("unused")

            override suspend fun deleteByKey(key: String) = error("unused")

            override suspend fun clearWatched(
                metaId: String,
                videoIds: List<String>,
            ) = error("unused")

            override suspend fun clearInProgress(metaId: String) = error("unused")

            override fun observeWatchedMovieIds(): Flow<List<String>> = error("unused")

            override fun observeHistoryRows(limit: Int): Flow<List<ContinueRow>> {
                lastRequestedLimit = limit
                return historyRowsFlow
            }

            override suspend fun deleteAll() {
                deleteAllCalledCount++
            }
        }

    private lateinit var repository: HistoryRepositoryImpl

    @Before
    fun setup() {
        repository = HistoryRepositoryImpl(fakeDao)
    }

    private fun createRow(
        metaId: String,
        videoId: String = "v1",
        season: Int? = null,
        episode: Int? = null,
        updatedAt: Long = 1000L,
    ): ContinueRow =
        ContinueRow(
            metaId = metaId,
            type = "series",
            name = "Title $metaId",
            poster = null,
            videoId = videoId,
            season = season,
            episode = episode,
            positionMs = 1000L,
            durationMs = 2000L,
            watched = false,
            updatedAt = updatedAt,
        )

    @Test
    fun HR1_observeHistory_returnsOneEntryPerTitle_newestFirst() =
        runTest {
            val rows =
                listOf(
                    createRow("t1", videoId = "s1e1", season = 1, episode = 1, updatedAt = 500L),
                    createRow("t1", videoId = "s1e2", season = 1, episode = 2, updatedAt = 500L),
                    createRow("t2", videoId = "s1e1", season = 1, episode = 1, updatedAt = 600L),
                )
            historyRowsFlow.value = rows

            val entries = repository.observeHistory(10).first()
            assertEquals(2, entries.size)
            assertEquals("t2", entries[0].metaId)
            assertEquals("t1", entries[1].metaId)
            assertEquals("s1e2", entries[1].videoId)
        }

    @Test
    fun HR2_remove_deletesAllRowsOfThatTitle() =
        runTest {
            repository.remove("tt1")
            assertEquals("tt1", deletedMetaId)
        }

    @Test
    fun HR3_clear_deletesAll() =
        runTest {
            repository.clear()
            assertEquals(1, deleteAllCalledCount)
        }

    @Test
    fun HR4_observeHistory_passesTheLimitToTheDao() =
        runTest {
            val flow = repository.observeHistory(500)
            assertEquals(500, lastRequestedLimit)
            assertEquals(emptyList<ContinueRow>(), flow.first())
        }
}
