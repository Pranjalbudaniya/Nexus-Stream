package com.nexus.stream.data.repository

import com.nexus.stream.data.local.db.TransactionRunner
import com.nexus.stream.data.local.db.dao.FakeLibraryDao
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
    fun `first toggle inserts with inWatchlist true and addedAt now`() =
        runTest(testDispatcher) {
            val preview = createPreview("tt1")
            currentTime = 123456L

            repository.toggleWatchlist(preview)

            val row = fakeDao.get("tt1")
            assertNotNull(row)
            assertTrue(row!!.inWatchlist)
            assertEquals(123456L, row.addedAt)
            assertEquals("Test Movie", row.name)
            assertEquals("https://example.com/poster.jpg", row.poster)

            val watchlistIds = repository.observeWatchlistIds().first()
            assertEquals(setOf("tt1"), watchlistIds)
        }

    @Test
    fun `second toggle flips to false and keeps the row`() =
        runTest(testDispatcher) {
            val preview = createPreview("tt1")
            repository.toggleWatchlist(preview)

            repository.toggleWatchlist(preview)

            val row = fakeDao.get("tt1")
            assertNotNull(row)
            assertFalse(row!!.inWatchlist)

            val watchlistIds = repository.observeWatchlistIds().first()
            assertTrue(watchlistIds.isEmpty())
        }

    @Test
    fun `toggling on again refreshes name and poster from the preview`() =
        runTest(testDispatcher) {
            val initial = createPreview("tt1", name = "Old Name", poster = "https://old.jpg")
            repository.toggleWatchlist(initial)

            // Turn off
            repository.toggleWatchlist(initial)
            assertFalse(fakeDao.get("tt1")!!.inWatchlist)

            // Turn on with updated metadata
            val updated = createPreview("tt1", name = "New Name", poster = "https://new.jpg")
            repository.toggleWatchlist(updated)

            val row = fakeDao.get("tt1")
            assertNotNull(row)
            assertTrue(row!!.inWatchlist)
            assertEquals("New Name", row.name)
            assertEquals("https://new.jpg", row.poster)
        }

    @Test
    fun `two sequential toggles end consistent`() =
        runTest(testDispatcher) {
            val preview = createPreview("tt1")

            repository.toggleWatchlist(preview)
            repository.toggleWatchlist(preview)

            assertEquals(emptySet<String>(), repository.observeWatchlistIds().first())
            assertFalse(fakeDao.get("tt1")!!.inWatchlist)
        }
}
