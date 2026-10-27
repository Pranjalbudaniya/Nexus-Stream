package com.nexus.stream.data.repository

import com.nexus.stream.data.local.db.dao.SearchHistoryDao
import com.nexus.stream.data.local.db.entity.SearchHistoryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

class SearchHistoryRepositoryTest {
    private lateinit var dao: FakeSearchHistoryDao
    private lateinit var repository: SearchHistoryRepositoryImpl
    private var currentTime = 1000L

    @Before
    fun setup() {
        dao = FakeSearchHistoryDao()
        currentTime = 1000L
        repository =
            SearchHistoryRepositoryImpl(
                dao = dao,
                dispatcher = Dispatchers.Unconfined,
                clock = {
                    currentTime += 10L
                    currentTime
                },
            )
    }

    @Test
    fun SH1_save_trimsAndStores() =
        runTest {
            repository.save("  dune ")
            assertEquals(listOf("dune"), repository.observeHistory().first())
        }

    @Test
    fun SH2_save_sameQueryOtherCase_movesToTopOnce() =
        runTest {
            repository.save("Dune")
            repository.save("alien")
            repository.save("dune")
            assertEquals(listOf("dune", "alien"), repository.observeHistory().first())
        }

    @Test
    fun SH3_save_capsAtTwenty_dropsTheOldest() =
        runTest {
            for (i in 1..21) {
                repository.save("query $i")
            }
            val history = repository.observeHistory().first()
            assertEquals(20, history.size)
            assertEquals("query 21", history.first())
            assertFalse(history.contains("query 1"))
        }

    @Test
    fun SH4_save_unsearchableQuery_isIgnored() =
        runTest {
            repository.save("")
            repository.save("   ")
            repository.save("a")
            assertEquals(emptyList<String>(), repository.observeHistory().first())
        }

    @Test
    fun SH5_observe_isNewestFirst() =
        runTest {
            repository.save("aa")
            repository.save("bb")
            repository.save("cc")
            assertEquals(listOf("cc", "bb", "aa"), repository.observeHistory().first())
        }

    @Test
    fun SH6_remove_deletesOnlyThatQuery() =
        runTest {
            repository.save("aa")
            repository.save("bb")
            repository.remove("AA")
            assertEquals(listOf("bb"), repository.observeHistory().first())
        }

    @Test
    fun SH7_clear_deletesAll() =
        runTest {
            repository.save("aa")
            repository.save("bb")
            repository.clear()
            assertEquals(emptyList<String>(), repository.observeHistory().first())
        }

    @Test
    fun SH8_save_collapsesInnerWhitespace() =
        runTest {
            repository.save("dune   part  two")
            assertEquals(listOf("dune part two"), repository.observeHistory().first())
        }

    private class FakeSearchHistoryDao : SearchHistoryDao {
        private val entries = MutableStateFlow<List<SearchHistoryEntity>>(emptyList())

        override fun observeAll(): Flow<List<SearchHistoryEntity>> = entries

        override suspend fun getAll(): List<SearchHistoryEntity> = entries.value

        override suspend fun upsert(entity: SearchHistoryEntity) {
            entries.update { list ->
                (list.filterNot { it.query == entity.query } + entity).sortedByDescending { it.lastUsedAt }
            }
        }

        override suspend fun delete(query: String) {
            entries.update { list -> list.filterNot { it.query == query } }
        }

        override suspend fun deleteAll() {
            entries.value = emptyList()
        }
    }
}
