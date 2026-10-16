package com.nexus.stream.data.repository

import com.nexus.stream.data.local.db.TransactionRunner
import com.nexus.stream.data.local.db.dao.FakeLibraryDao
import com.nexus.stream.domain.model.ListStatus
import com.nexus.stream.domain.model.MetaPreview
import com.nexus.stream.domain.model.PosterShape
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryRepositoryImplTest {
    private val fakeDao = FakeLibraryDao()
    private val passThroughRunner =
        object : TransactionRunner {
            override suspend fun <T> run(block: suspend () -> T): T = block()
        }
    private val testDispatcher = UnconfinedTestDispatcher()
    private var currentTime = 1_000_000L

    private val repository =
        LibraryRepositoryImpl(
            libraryDao = fakeDao,
            transactionRunner = passThroughRunner,
            dispatcher = testDispatcher,
            clock = { currentTime },
        )

    private fun createPreview(
        id: String = "tt123",
        name: String = "Test Movie",
        poster: String? = "https://example.com/poster.jpg",
    ): MetaPreview =
        MetaPreview(
            id = id,
            type = "movie",
            name = name,
            poster = poster,
            posterShape = PosterShape.POSTER,
            background = "https://example.com/bg.jpg",
            logo = null,
            description = "Desc",
            releaseInfo = "2024",
            imdbRating = "8.5",
            genres = listOf("Drama"),
        )

    @Test
    fun `adding sets Plan to watch and creates snapshot`() =
        runTest(testDispatcher) {
            val preview = createPreview("tt1")
            currentTime = 123456L

            repository.setStatus(preview, ListStatus.PLAN_TO_WATCH)

            val row = fakeDao.get("tt1")
            assertNotNull(row)
            assertTrue(row!!.inWatchlist)
            assertEquals("PLAN_TO_WATCH", row.listStatus)
            assertEquals(123456L, row.addedAt)
            assertEquals("Test Movie", row.name)
            assertEquals("https://example.com/poster.jpg", row.poster)

            val statuses = repository.observeListStatuses().first()
            assertEquals(mapOf("tt1" to ListStatus.PLAN_TO_WATCH), statuses)
        }

    @Test
    fun `changing status keeps the row and updates listStatus`() =
        runTest(testDispatcher) {
            val preview = createPreview("tt1")
            repository.setStatus(preview, ListStatus.PLAN_TO_WATCH)

            repository.setStatus(preview, ListStatus.WATCHING)

            val row = fakeDao.get("tt1")
            assertNotNull(row)
            assertTrue(row!!.inWatchlist)
            assertEquals("WATCHING", row.listStatus)

            val statuses = repository.observeListStatuses().first()
            assertEquals(mapOf("tt1" to ListStatus.WATCHING), statuses)
        }

    @Test
    fun `removing sets inWatchlist false and keeps the row`() =
        runTest(testDispatcher) {
            val preview = createPreview("tt1")
            repository.setStatus(preview, ListStatus.PLAN_TO_WATCH)

            repository.setStatus(preview, null)

            val row = fakeDao.get("tt1")
            assertNotNull(row)
            assertFalse(row!!.inWatchlist)

            val statuses = repository.observeListStatuses().first()
            assertTrue(statuses.isEmpty())
        }

    @Test
    fun `re-adding refreshes snapshot fields`() =
        runTest(testDispatcher) {
            val initial = createPreview("tt1", name = "Old Name", poster = "https://old.jpg")
            repository.setStatus(initial, ListStatus.PLAN_TO_WATCH)

            repository.setStatus(initial, null)
            assertFalse(fakeDao.get("tt1")!!.inWatchlist)

            val updated = createPreview("tt1", name = "New Name", poster = "https://new.jpg")
            repository.setStatus(updated, ListStatus.COMPLETED)

            val row = fakeDao.get("tt1")
            assertNotNull(row)
            assertTrue(row!!.inWatchlist)
            assertEquals("COMPLETED", row.listStatus)
            assertEquals("New Name", row.name)
            assertEquals("https://new.jpg", row.poster)
        }

    @Test
    fun `observeListStatuses only includes listed titles`() =
        runTest(testDispatcher) {
            val item1 = createPreview("t1")
            val item2 = createPreview("t2")
            repository.setStatus(item1, ListStatus.WATCHING)
            repository.setStatus(item2, ListStatus.DROPPED)

            repository.setStatus(item1, null) // removed

            val statuses = repository.observeListStatuses().first()
            assertEquals(1, statuses.size)
            assertEquals(ListStatus.DROPPED, statuses["t2"])
            assertFalse(statuses.containsKey("t1"))
        }

    @Test
    fun `two quick calls end consistent`() =
        runTest(testDispatcher) {
            val preview = createPreview("tt1")
            repository.setStatus(preview, ListStatus.PLAN_TO_WATCH)
            repository.setStatus(preview, null)

            val statuses = repository.observeListStatuses().first()
            assertTrue(statuses.isEmpty())
            assertFalse(fakeDao.get("tt1")!!.inWatchlist)
        }
}
