package com.nexus.stream.data.repository

import com.nexus.stream.data.local.db.dao.FakeLibraryDao
import com.nexus.stream.data.local.db.entity.LibraryItemEntity
import com.nexus.stream.domain.model.ListStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class WatchlistRepositoryImplTest {
    private val fakeDao = FakeLibraryDao()
    private lateinit var repository: WatchlistRepositoryImpl

    @Before
    fun setup() {
        repository = WatchlistRepositoryImpl(fakeDao)
    }

    @Test
    fun WR1_observeWatchlist_mapsRowsToEntries() =
        runTest {
            val entities =
                listOf(
                    LibraryItemEntity(
                        metaId = "m1",
                        type = "movie",
                        name = "Movie 1",
                        poster = "https://example.com/p1.jpg",
                        background = "https://example.com/b1.jpg",
                        inWatchlist = true,
                        addedAt = 1000L,
                        listStatus = "WATCHING",
                    ),
                    LibraryItemEntity(
                        metaId = "m2",
                        type = "series",
                        name = "Series 1",
                        poster = "https://example.com/p2.jpg",
                        background = null,
                        inWatchlist = true,
                        addedAt = 2000L,
                        listStatus = "COMPLETED",
                    ),
                )
            fakeDao.items.value = entities.associateBy { it.metaId }

            val result = repository.observeWatchlist().first()
            assertEquals(2, result.size)
            assertEquals("m1", result[0].metaId)
            assertEquals("movie", result[0].type)
            assertEquals("Movie 1", result[0].name)
            assertEquals("https://example.com/p1.jpg", result[0].poster)
            assertEquals("https://example.com/b1.jpg", result[0].background)
            assertEquals(ListStatus.WATCHING, result[0].status)
            assertEquals(1000L, result[0].addedAt)

            assertEquals("m2", result[1].metaId)
            assertEquals("series", result[1].type)
            assertEquals("Series 1", result[1].name)
            assertEquals("https://example.com/p2.jpg", result[1].poster)
            assertNull(result[1].background)
            assertEquals(ListStatus.COMPLETED, result[1].status)
            assertEquals(2000L, result[1].addedAt)
        }

    @Test
    fun WR2_unknownStatusName_becomesPlanToWatch() =
        runTest {
            val entities =
                listOf(
                    LibraryItemEntity(
                        metaId = "m1",
                        type = "movie",
                        name = "Movie 1",
                        poster = null,
                        background = null,
                        inWatchlist = true,
                        addedAt = 1000L,
                        listStatus = "NOPE",
                    ),
                )
            fakeDao.items.value = entities.associateBy { it.metaId }

            val result = repository.observeWatchlist().first()
            assertEquals(ListStatus.PLAN_TO_WATCH, result.first().status)
        }

    @Test
    fun WR3_nullPoster_isKept() =
        runTest {
            val entities =
                listOf(
                    LibraryItemEntity(
                        metaId = "m1",
                        type = "movie",
                        name = "Movie 1",
                        poster = null,
                        background = null,
                        inWatchlist = true,
                        addedAt = 1000L,
                        listStatus = "PLAN_TO_WATCH",
                    ),
                )
            fakeDao.items.value = entities.associateBy { it.metaId }

            val result = repository.observeWatchlist().first()
            assertNull(result.first().poster)
        }
}
