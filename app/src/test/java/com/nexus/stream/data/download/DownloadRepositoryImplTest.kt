package com.nexus.stream.data.download

import com.nexus.stream.data.local.db.dao.DownloadDao
import com.nexus.stream.data.local.db.entity.DownloadEntity
import com.nexus.stream.domain.model.DownloadFailure
import com.nexus.stream.domain.model.DownloadItem
import com.nexus.stream.domain.model.DownloadKind
import com.nexus.stream.domain.model.DownloadSource
import com.nexus.stream.domain.model.DownloadState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadRepositoryImplTest {
    private class FakeDownloadDao : DownloadDao {
        val table = LinkedHashMap<String, DownloadEntity>()
        private val state = MutableStateFlow<List<DownloadEntity>>(emptyList())

        private fun sync() {
            state.value = table.values.sortedByDescending { it.createdAt }
        }

        override fun observeAll(): Flow<List<DownloadEntity>> = state

        override fun observeForMeta(metaId: String): Flow<List<DownloadEntity>> =
            state.map { list -> list.filter { it.metaId == metaId } }

        override suspend fun get(id: String): DownloadEntity? = table[id]

        override suspend fun getAll(): List<DownloadEntity> = table.values.sortedByDescending { it.createdAt }

        override suspend fun upsert(entity: DownloadEntity) {
            table[entity.id] = entity
            sync()
        }

        override suspend fun updateProgress(
            id: String,
            downloadedBytes: Long,
            totalBytes: Long,
            state: String,
            updatedAt: Long,
        ) {
            table[id]?.let { existing ->
                table[id] =
                    existing.copy(
                        downloadedBytes = downloadedBytes,
                        totalBytes = totalBytes,
                        state = state,
                        updatedAt = updatedAt,
                    )
                sync()
            }
        }

        override suspend fun updateState(
            id: String,
            state: String,
            updatedAt: Long,
            pauseReason: String?,
            failure: String?,
        ) {
            table[id]?.let { existing ->
                table[id] =
                    existing.copy(
                        state = state,
                        updatedAt = updatedAt,
                        pauseReason = pauseReason,
                        failure = failure,
                    )
                sync()
            }
        }

        override suspend fun updateCompleted(
            id: String,
            filePath: String,
            totalBytes: Long,
            completedAt: Long,
            updatedAt: Long,
        ) {
            table[id]?.let { existing ->
                table[id] =
                    existing.copy(
                        filePath = filePath,
                        totalBytes = totalBytes,
                        completedAt = completedAt,
                        updatedAt = updatedAt,
                        state = "COMPLETED",
                    )
                sync()
            }
        }

        override suspend fun setAllowMetered(
            id: String,
            allowMetered: Boolean,
            updatedAt: Long,
        ) {
            table[id]?.let { existing ->
                table[id] = existing.copy(allowMetered = allowMetered, updatedAt = updatedAt)
                sync()
            }
        }

        override suspend fun delete(id: String) {
            table.remove(id)
            sync()
        }
    }

    private fun createItem(
        id: String = "1",
        metaId: String = "tt12345",
        videoId: String = "tt12345:1:1",
    ) = DownloadItem(
        id = id,
        kind = DownloadKind.HTTP,
        source =
            DownloadSource.Http(
                url = "https://example.com/video.mp4",
                headers = mapOf("Authorization" to "Bearer 123", "Cookie" to "sid=abc"),
            ),
        metaId = metaId,
        type = "movie",
        title = "Test Movie",
        metaName = "Test Meta",
        posterUrl = "https://example.com/poster.jpg",
        videoId = videoId,
        season = 1,
        episode = 1,
        fileNameHint = "test.mp4",
        fileName = "test.mp4",
        totalBytes = 100_000_000L,
        downloadedBytes = 50_000_000L,
        state = DownloadState.DOWNLOADING,
        pauseReason = null,
        failure = null,
        filePath = null,
        supportsResume = true,
        etag = "etag-1",
        lastModified = "Wed, 21 Oct 2026 07:28:00 GMT",
        allowMetered = true,
        createdAt = 1000L,
        updatedAt = 1000L,
        completedAt = null,
    )

    @Test
    fun DB1_insertAndObserve_roundTripsEveryField() =
        runTest {
            val dao = FakeDownloadDao()
            val repo = DownloadRepositoryImpl(dao)
            val item = createItem()

            repo.insert(item)
            val observed = repo.observeAll().first()

            assertEquals(1, observed.size)
            assertEquals(item, observed.first())
        }

    @Test
    fun DB2_updateProgress_persistsBytesAndState() =
        runTest {
            val dao = FakeDownloadDao()
            val repo = DownloadRepositoryImpl(dao)
            val item = createItem().copy(state = DownloadState.QUEUED, downloadedBytes = 0L)

            repo.insert(item)
            repo.updateProgress(
                id = item.id,
                downloadedBytes = 500L,
                totalBytes = 1000L,
                state = DownloadState.DOWNLOADING,
                updatedAt = 2000L,
            )

            val updated = repo.get(item.id)
            assertNotNull(updated)
            assertEquals(500L, updated?.downloadedBytes)
            assertEquals(DownloadState.DOWNLOADING, updated?.state)
            assertEquals(2000L, updated?.updatedAt)
        }

    @Test
    fun DB3_observeForMeta_filtersByMetaId() =
        runTest {
            val dao = FakeDownloadDao()
            val repo = DownloadRepositoryImpl(dao)
            val item1 = createItem(id = "1", metaId = "tt1")
            val item2 = createItem(id = "2", metaId = "tt2")

            repo.insert(item1)
            repo.insert(item2)

            val observed = repo.observeForMeta("tt1").first()
            assertEquals(1, observed.size)
            assertEquals("tt1", observed.first().metaId)
        }

    @Test
    fun DB4_delete_removesTheRow() =
        runTest {
            val dao = FakeDownloadDao()
            val repo = DownloadRepositoryImpl(dao)
            val item = createItem()

            repo.insert(item)
            assertEquals(1, repo.observeAll().first().size)

            repo.delete(item.id)
            assertEquals(0, repo.observeAll().first().size)
        }

    @Test
    fun DB5_unknownStateOrFailureName_readsAsFailedUnknown() =
        runTest {
            val dao = FakeDownloadDao()
            val repo = DownloadRepositoryImpl(dao)

            val rawEntity =
                DownloadEntity(
                    id = "bad1",
                    kind = "HTTP",
                    url = "https://example.com/video.mp4",
                    headersJson = "{}",
                    infoHash = null,
                    trackersJson = null,
                    fileIdx = null,
                    metaId = "tt1",
                    type = "movie",
                    title = "Bad Entity",
                    metaName = null,
                    posterUrl = null,
                    videoId = "tt1",
                    season = null,
                    episode = null,
                    fileNameHint = null,
                    fileName = "bad.mp4",
                    totalBytes = 100L,
                    downloadedBytes = 0L,
                    state = "XYZ",
                    pauseReason = null,
                    failure = "FOOBAR",
                    filePath = null,
                    supportsResume = true,
                    etag = null,
                    lastModified = null,
                    allowMetered = false,
                    createdAt = 1000L,
                    updatedAt = 1000L,
                    completedAt = null,
                )
            dao.upsert(rawEntity)

            val observed = repo.observeAll().first().first()
            assertEquals(DownloadState.FAILED, observed.state)
            assertEquals(DownloadFailure.UNKNOWN, observed.failure)
        }

    @Test
    fun DB6_toString_neverPrintsTheUrlOrHeaders() {
        val item = createItem()
        val itemString = item.toString()
        val sourceString = item.source.toString()

        assertFalse(itemString.contains("example.com"))
        assertFalse(itemString.contains("Cookie"))
        assertFalse(itemString.contains("sid=abc"))
        assertFalse(sourceString.contains("example.com"))
        assertFalse(sourceString.contains("Cookie"))
        assertFalse(sourceString.contains("sid=abc"))
    }
}
