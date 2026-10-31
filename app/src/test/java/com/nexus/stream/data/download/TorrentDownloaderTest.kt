package com.nexus.stream.data.download

import com.nexus.stream.data.torrent.BackendFile
import com.nexus.stream.data.torrent.DownloadSnapshot
import com.nexus.stream.data.torrent.TorrentBackendException
import com.nexus.stream.data.torrent.TorrentDownloadBackend
import com.nexus.stream.data.torrent.TorrentMetadata
import com.nexus.stream.domain.model.DownloadFailure
import com.nexus.stream.domain.model.DownloadPhase
import com.nexus.stream.domain.model.TorrentFailReason
import com.nexus.stream.domain.repository.DownloadFileStore
import com.nexus.stream.domain.repository.StorageInfo
import com.nexus.stream.domain.repository.TorrentResult
import com.nexus.stream.domain.repository.TorrentTask
import com.nexus.stream.domain.repository.TorrentUpdate
import com.nexus.stream.domain.usecase.TorrentSessionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class TorrentDownloaderTest {
    @get:Rule
    val tempDir = TemporaryFolder()

    private lateinit var rootDir: File
    private lateinit var fakeBackend: FakeTorrentDownloadBackend
    private lateinit var fakeStorage: FakeStorageInfo
    private lateinit var fakeFileStore: FakeDownloadFileStore

    @Before
    fun setUp() {
        rootDir = tempDir.newFolder("downloads_root")
        fakeBackend = FakeTorrentDownloadBackend()
        fakeStorage = FakeStorageInfo().apply { available = 50L * 1024 * 1024 * 1024 }
        fakeFileStore = FakeDownloadFileStore(rootDir)
    }

    @Test
    fun TD1_start_addsTheMagnetIntoTheDownloadFolder_notTheCache() =
        runTest {
            val testDispatcher = StandardTestDispatcher(testScheduler)
            val downloader =
                TorrentDownloaderImpl(
                    backend = fakeBackend,
                    storageInfo = fakeStorage,
                    fileStore = fakeFileStore,
                    ioDispatcher = testDispatcher,
                    nowMs = { testScheduler.currentTime },
                    delayFn = { kotlinx.coroutines.delay(it) },
                )
            val saveDir = File(rootDir, "torrents/item1")
            fakeBackend.metadataToReturn =
                TorrentMetadata(
                    files = listOf(BackendFile(0, "movie.mkv", 1024L * 1024 * 1024, 0L)),
                    pieceLength = 1024L * 1024,
                )
            fakeBackend.snapshotsToReturn =
                listOf(
                    DownloadSnapshot(1024L * 1024 * 1024, 1024L * 1024 * 1024, 1000L, 5, TorrentSessionState.FINISHED),
                )

            val task = TorrentTask(infoHash = "0123456789abcdef0123456789abcdef01234567", saveDir = saveDir)
            downloader.download(task) {}

            assertEquals(1, fakeBackend.addCalls.size)
            val call = fakeBackend.addCalls.first()
            assertEquals(saveDir, call.saveDir)
            assertFalse(call.saveDir.path.contains("cacheDir"))
        }

    @Test
    fun TD2_metadata_thenOnlyTheChosenFileGetsPriority() =
        runTest {
            val testDispatcher = StandardTestDispatcher(testScheduler)
            val downloader =
                TorrentDownloaderImpl(
                    backend = fakeBackend,
                    storageInfo = fakeStorage,
                    fileStore = fakeFileStore,
                    ioDispatcher = testDispatcher,
                    nowMs = { testScheduler.currentTime },
                    delayFn = { kotlinx.coroutines.delay(it) },
                )
            val saveDir = File(rootDir, "torrents/item1")
            fakeBackend.metadataToReturn =
                TorrentMetadata(
                    files =
                        listOf(
                            BackendFile(0, "Series.S01E01.mkv", 1024L * 1024 * 1024, 0L),
                            BackendFile(1, "Series.S01E02.mkv", 1024L * 1024 * 1024, 1024L),
                            BackendFile(2, "Series.S01E03.mkv", 1024L * 1024 * 1024, 2048L),
                        ),
                    pieceLength = 1024L * 1024,
                )
            fakeBackend.snapshotsToReturn =
                listOf(
                    DownloadSnapshot(1024L * 1024 * 1024, 1024L * 1024 * 1024, 1000L, 5, TorrentSessionState.FINISHED),
                )

            val task =
                TorrentTask(
                    infoHash = "0123456789abcdef0123456789abcdef01234567",
                    saveDir = saveDir,
                    season = 1,
                    episode = 2,
                )
            downloader.download(task) {}

            assertEquals(listOf(1), fakeBackend.selectedFiles)
        }

    @Test
    fun TD3_progress_reportsBytesSpeedPeersAndPhase() =
        runTest {
            val testDispatcher = StandardTestDispatcher(testScheduler)
            val downloader =
                TorrentDownloaderImpl(
                    backend = fakeBackend,
                    storageInfo = fakeStorage,
                    fileStore = fakeFileStore,
                    ioDispatcher = testDispatcher,
                    nowMs = { testScheduler.currentTime },
                    delayFn = { kotlinx.coroutines.delay(it) },
                )
            val saveDir = File(rootDir, "torrents/item1")
            val total = 1000L * 1024 * 1024
            fakeBackend.metadataToReturn =
                TorrentMetadata(
                    files = listOf(BackendFile(0, "movie.mkv", total, 0L)),
                    pieceLength = 1024L * 1024,
                )
            fakeBackend.snapshotsToReturn =
                listOf(
                    DownloadSnapshot(0L, total, 100L, 3, TorrentSessionState.DOWNLOADING),
                    DownloadSnapshot(250L * 1024 * 1024, total, 200L, 4, TorrentSessionState.DOWNLOADING),
                    DownloadSnapshot(500L * 1024 * 1024, total, 300L, 5, TorrentSessionState.DOWNLOADING),
                    DownloadSnapshot(total, total, 0L, 5, TorrentSessionState.FINISHED),
                )

            val updates = mutableListOf<TorrentUpdate>()
            val task = TorrentTask(infoHash = "0123456789abcdef0123456789abcdef01234567", saveDir = saveDir)
            downloader.download(task) { updates.add(it) }

            assertTrue(updates.size >= 3)
            assertEquals(DownloadPhase.TRANSFERRING, updates[0].phase)
            assertEquals(0L, updates[0].fileBytesDone)
            assertEquals(3, updates[0].peers)
            assertEquals(250L * 1024 * 1024, updates[1].fileBytesDone)
            assertEquals(4, updates[1].peers)
            assertEquals(500L * 1024 * 1024, updates[2].fileBytesDone)
            assertEquals(5, updates[2].peers)
        }

    @Test
    fun TD4_completion_removesKeepingFiles_andReturnsThePath() =
        runTest {
            val testDispatcher = StandardTestDispatcher(testScheduler)
            val downloader =
                TorrentDownloaderImpl(
                    backend = fakeBackend,
                    storageInfo = fakeStorage,
                    fileStore = fakeFileStore,
                    ioDispatcher = testDispatcher,
                    nowMs = { testScheduler.currentTime },
                    delayFn = { kotlinx.coroutines.delay(it) },
                )
            val saveDir = File(rootDir, "torrents/item1")
            val total = 1000L * 1024 * 1024
            fakeBackend.metadataToReturn =
                TorrentMetadata(
                    files = listOf(BackendFile(0, "movie.mkv", total, 0L)),
                    pieceLength = 1024L * 1024,
                )
            fakeBackend.snapshotsToReturn =
                listOf(
                    DownloadSnapshot(total, total, 0L, 5, TorrentSessionState.FINISHED),
                )

            val task = TorrentTask(infoHash = "0123456789abcdef0123456789abcdef01234567", saveDir = saveDir)
            val result = downloader.download(task) {}

            assertEquals(1, fakeBackend.removeKeepingFilesCalls)
            assertTrue(result is TorrentResult.Completed)
            val completed = result as TorrentResult.Completed
            assertEquals(File(saveDir, "movie.mkv"), completed.file)
            assertEquals(total, completed.totalBytes)
        }

    @Test
    fun TD5_pause_removesKeepingFiles_andReturnsPaused() =
        runTest {
            val testDispatcher = StandardTestDispatcher(testScheduler)
            val downloader =
                TorrentDownloaderImpl(
                    backend = fakeBackend,
                    storageInfo = fakeStorage,
                    fileStore = fakeFileStore,
                    ioDispatcher = testDispatcher,
                    nowMs = { testScheduler.currentTime },
                    delayFn = { kotlinx.coroutines.delay(it) },
                )
            val saveDir = File(rootDir, "torrents/item1")
            val total = 1000L * 1024 * 1024
            fakeBackend.metadataToReturn =
                TorrentMetadata(
                    files = listOf(BackendFile(0, "movie.mkv", total, 0L)),
                    pieceLength = 1024L * 1024,
                )
            fakeBackend.snapshotsToReturn =
                listOf(
                    DownloadSnapshot(500L * 1024 * 1024, total, 500L, 3, TorrentSessionState.DOWNLOADING),
                )
            fakeBackend.pauseAfterSnapshots = 1

            val task = TorrentTask(infoHash = "0123456789abcdef0123456789abcdef01234567", saveDir = saveDir)
            val result = downloader.download(task) {}

            assertEquals(1, fakeBackend.removeKeepingFilesCalls)
            assertTrue(result is TorrentResult.Paused)
            val paused = result as TorrentResult.Paused
            assertEquals(0, paused.resume.fileIndex)
            assertEquals(500L * 1024 * 1024, paused.resume.bytesDone)
        }

    @Test
    fun TD6_resume_readdsIntoTheSameFolder_andReportsChecking() =
        runTest {
            val testDispatcher = StandardTestDispatcher(testScheduler)
            val downloader =
                TorrentDownloaderImpl(
                    backend = fakeBackend,
                    storageInfo = fakeStorage,
                    fileStore = fakeFileStore,
                    ioDispatcher = testDispatcher,
                    nowMs = { testScheduler.currentTime },
                    delayFn = { kotlinx.coroutines.delay(it) },
                )
            val saveDir = File(rootDir, "torrents/item1")
            val total = 1000L * 1024 * 1024
            fakeBackend.metadataToReturn =
                TorrentMetadata(
                    files = listOf(BackendFile(0, "movie.mkv", total, 0L)),
                    pieceLength = 1024L * 1024,
                )
            fakeBackend.snapshotsToReturn =
                listOf(
                    DownloadSnapshot(200L * 1024 * 1024, total, 0L, 1, TorrentSessionState.CHECKING_FILES),
                    DownloadSnapshot(total, total, 100L, 2, TorrentSessionState.FINISHED),
                )

            val updates = mutableListOf<TorrentUpdate>()
            val task =
                TorrentTask(
                    infoHash = "0123456789abcdef0123456789abcdef01234567",
                    saveDir = saveDir,
                    knownFileIdx = 0,
                )
            downloader.download(task) { updates.add(it) }

            assertEquals(saveDir, fakeBackend.addCalls.first().saveDir)
            assertEquals(DownloadPhase.CHECKING_FILES, updates.first().phase)
        }

    @Test
    fun TD7_cancel_removesAndKeepsNothingToDo() =
        runTest {
            val testDispatcher = StandardTestDispatcher(testScheduler)
            val downloader =
                TorrentDownloaderImpl(
                    backend = fakeBackend,
                    storageInfo = fakeStorage,
                    fileStore = fakeFileStore,
                    ioDispatcher = testDispatcher,
                    nowMs = { testScheduler.currentTime },
                    delayFn = { kotlinx.coroutines.delay(it) },
                )
            val saveDir = File(rootDir, "torrents/item1")
            fakeBackend.metadataToReturn =
                TorrentMetadata(
                    files = listOf(BackendFile(0, "movie.mkv", 1000L * 1024 * 1024, 0L)),
                    pieceLength = 1024L * 1024,
                )
            fakeBackend.snapshotsToReturn =
                listOf(
                    DownloadSnapshot(100L * 1024 * 1024, 1000L * 1024 * 1024, 100L, 2, TorrentSessionState.DOWNLOADING),
                )

            val task = TorrentTask(infoHash = "0123456789abcdef0123456789abcdef01234567", saveDir = saveDir)
            val job = launch(testDispatcher) { downloader.download(task) {} }
            runCurrent()
            job.cancelAndJoin()

            assertEquals(1, fakeBackend.removeKeepingFilesCalls)
        }

    @Test
    fun TD8_metadataTimeout_failsNoPeers() =
        runTest {
            val testDispatcher = StandardTestDispatcher(testScheduler)
            var virtualTime = 0L
            val downloader =
                TorrentDownloaderImpl(
                    backend = fakeBackend,
                    storageInfo = fakeStorage,
                    fileStore = fakeFileStore,
                    ioDispatcher = testDispatcher,
                    nowMs = { virtualTime },
                    delayFn = { virtualTime += it },
                )
            val saveDir = File(rootDir, "torrents/item1")
            fakeBackend.neverEmitMetadata = true

            val task = TorrentTask(infoHash = "0123456789abcdef0123456789abcdef01234567", saveDir = saveDir)
            val result = downloader.download(task) {}

            assertEquals(1, fakeBackend.removeKeepingFilesCalls)
            assertEquals(TorrentResult.Failed(DownloadFailure.TORRENT_NO_PEERS), result)
        }

    @Test
    fun TD9_ambiguousFiles_returnNeedsUserPick() =
        runTest {
            val testDispatcher = StandardTestDispatcher(testScheduler)
            val downloader =
                TorrentDownloaderImpl(
                    backend = fakeBackend,
                    storageInfo = fakeStorage,
                    fileStore = fakeFileStore,
                    ioDispatcher = testDispatcher,
                    nowMs = { testScheduler.currentTime },
                    delayFn = { kotlinx.coroutines.delay(it) },
                )
            val saveDir = File(rootDir, "torrents/item1")
            fakeBackend.metadataToReturn =
                TorrentMetadata(
                    files =
                        listOf(
                            BackendFile(0, "video1.mkv", 1000L * 1024 * 1024, 0L),
                            BackendFile(1, "video2.mkv", 2000L * 1024 * 1024, 1000L),
                            BackendFile(2, "video3.mkv", 1500L * 1024 * 1024, 3000L),
                        ),
                    pieceLength = 1024L * 1024,
                )

            val task = TorrentTask(infoHash = "0123456789abcdef0123456789abcdef01234567", saveDir = saveDir)
            val result = downloader.download(task) {}

            assertEquals(1, fakeBackend.removeKeepingFilesCalls)
            assertTrue(result is TorrentResult.NeedsUserPick)
            val pick = result as TorrentResult.NeedsUserPick
            assertEquals(listOf(1, 2, 0), pick.files.map { it.index })
        }

    @Test
    fun TD10_noVideoFile_failsNoVideo() =
        runTest {
            val testDispatcher = StandardTestDispatcher(testScheduler)
            val downloader =
                TorrentDownloaderImpl(
                    backend = fakeBackend,
                    storageInfo = fakeStorage,
                    fileStore = fakeFileStore,
                    ioDispatcher = testDispatcher,
                    nowMs = { testScheduler.currentTime },
                    delayFn = { kotlinx.coroutines.delay(it) },
                )
            val saveDir = File(rootDir, "torrents/item1")
            fakeBackend.metadataToReturn =
                TorrentMetadata(
                    files = listOf(BackendFile(0, "archive.zip", 1000L * 1024 * 1024, 0L)),
                    pieceLength = 1024L * 1024,
                )

            val task = TorrentTask(infoHash = "0123456789abcdef0123456789abcdef01234567", saveDir = saveDir)
            val result = downloader.download(task) {}

            assertEquals(1, fakeBackend.removeKeepingFilesCalls)
            assertEquals(TorrentResult.Failed(DownloadFailure.TORRENT_NO_VIDEO_FILE), result)
        }

    @Test
    fun TD11_insufficientStorageAfterMetadata_fails() =
        runTest {
            val testDispatcher = StandardTestDispatcher(testScheduler)
            fakeStorage.available = 1L * 1024 * 1024 * 1024 // 1 GiB free
            val downloader =
                TorrentDownloaderImpl(
                    backend = fakeBackend,
                    storageInfo = fakeStorage,
                    fileStore = fakeFileStore,
                    ioDispatcher = testDispatcher,
                    nowMs = { testScheduler.currentTime },
                    delayFn = { kotlinx.coroutines.delay(it) },
                )
            val saveDir = File(rootDir, "torrents/item1")
            fakeBackend.metadataToReturn =
                TorrentMetadata(
                    files = listOf(BackendFile(0, "huge.mkv", 10L * 1024 * 1024 * 1024, 0L)), // 10 GiB file
                    pieceLength = 1024L * 1024,
                )

            val task = TorrentTask(infoHash = "0123456789abcdef0123456789abcdef01234567", saveDir = saveDir)
            val result = downloader.download(task) {}

            assertEquals(1, fakeBackend.removeKeepingFilesCalls)
            assertEquals(0, fakeBackend.selectedFiles.size)
            assertEquals(TorrentResult.Failed(DownloadFailure.INSUFFICIENT_STORAGE), result)
        }

    @Test
    fun TD12_hashActiveInTheStreamingCache_failsInUse() =
        runTest {
            val testDispatcher = StandardTestDispatcher(testScheduler)
            fakeBackend.activeInStreamingHashes.add("0123456789abcdef0123456789abcdef01234567")
            val downloader =
                TorrentDownloaderImpl(
                    backend = fakeBackend,
                    storageInfo = fakeStorage,
                    fileStore = fakeFileStore,
                    ioDispatcher = testDispatcher,
                    nowMs = { testScheduler.currentTime },
                    delayFn = { kotlinx.coroutines.delay(it) },
                )
            val saveDir = File(rootDir, "torrents/item1")

            val task = TorrentTask(infoHash = "0123456789abcdef0123456789abcdef01234567", saveDir = saveDir)
            val result = downloader.download(task) {}

            assertEquals(0, fakeBackend.addCalls.size)
            assertEquals(TorrentResult.Failed(DownloadFailure.TORRENT_IN_USE), result)
        }

    @Test
    fun TD13_backendException_mapsToTorrentEngine() =
        runTest {
            val testDispatcher = StandardTestDispatcher(testScheduler)
            fakeBackend.throwExceptionOnAdd = TorrentBackendException(TorrentFailReason.STORAGE)
            val downloader =
                TorrentDownloaderImpl(
                    backend = fakeBackend,
                    storageInfo = fakeStorage,
                    fileStore = fakeFileStore,
                    ioDispatcher = testDispatcher,
                    nowMs = { testScheduler.currentTime },
                    delayFn = { kotlinx.coroutines.delay(it) },
                )
            val saveDir = File(rootDir, "torrents/item1")

            val task = TorrentTask(infoHash = "0123456789abcdef0123456789abcdef01234567", saveDir = saveDir)
            val result = downloader.download(task) {}

            assertEquals(1, fakeBackend.removeKeepingFilesCalls)
            assertEquals(TorrentResult.Failed(DownloadFailure.TORRENT_ENGINE), result)
        }

    @Test
    fun TD14_cancellation_isNotSwallowed_andKeepsTheFiles() =
        runTest {
            val testDispatcher = StandardTestDispatcher(testScheduler)
            val downloader =
                TorrentDownloaderImpl(
                    backend = fakeBackend,
                    storageInfo = fakeStorage,
                    fileStore = fakeFileStore,
                    ioDispatcher = testDispatcher,
                    nowMs = { testScheduler.currentTime },
                    delayFn = { kotlinx.coroutines.delay(it) },
                )
            val saveDir = File(rootDir, "torrents/item1")
            fakeBackend.metadataToReturn =
                TorrentMetadata(
                    files = listOf(BackendFile(0, "movie.mkv", 1000L * 1024 * 1024, 0L)),
                    pieceLength = 1024L * 1024,
                )
            fakeBackend.snapshotsToReturn =
                listOf(
                    DownloadSnapshot(100L * 1024 * 1024, 1000L * 1024 * 1024, 100L, 2, TorrentSessionState.DOWNLOADING),
                )

            val task = TorrentTask(infoHash = "0123456789abcdef0123456789abcdef01234567", saveDir = saveDir)
            var caughtCancellation = false
            val job =
                launch(testDispatcher) {
                    try {
                        downloader.download(task) {}
                    } catch (e: CancellationException) {
                        caughtCancellation = true
                        throw e
                    }
                }
            runCurrent()
            job.cancelAndJoin()

            assertTrue(caughtCancellation)
            assertEquals(1, fakeBackend.removeKeepingFilesCalls)
        }
}

class FakeTorrentDownloadBackend : TorrentDownloadBackend {
    data class AddCall(
        val infoHash: String,
        val magnetUri: String,
        val saveDir: File,
    )

    val addCalls = mutableListOf<AddCall>()
    val selectedFiles = mutableListOf<Int>()
    var removeKeepingFilesCalls = 0
    var removeAndDeleteCalls = 0

    val activeInStreamingHashes = mutableSetOf<String>()
    var metadataToReturn: TorrentMetadata? = null
    var neverEmitMetadata: Boolean = false
    var snapshotsToReturn = listOf<DownloadSnapshot>()
    private var snapshotIdx = 0
    var pauseAfterSnapshots: Int? = null
    var shouldPauseNow: Boolean = false
    var throwExceptionOnAdd: TorrentBackendException? = null

    override suspend fun addForDownload(
        infoHash: String,
        magnetUri: String,
        saveDir: File,
    ) {
        throwExceptionOnAdd?.let { throw it }
        addCalls.add(AddCall(infoHash, magnetUri, saveDir))
    }

    override suspend fun awaitMetadata(infoHash: String): TorrentMetadata {
        if (neverEmitMetadata) {
            kotlinx.coroutines.awaitCancellation()
        }
        return metadataToReturn ?: TorrentMetadata(emptyList(), 0L)
    }

    override suspend fun selectFile(
        infoHash: String,
        fileIndex: Int,
    ) {
        selectedFiles.add(fileIndex)
    }

    override suspend fun downloadSnapshot(
        infoHash: String,
        fileIndex: Int,
    ): DownloadSnapshot {
        if (snapshotsToReturn.isEmpty()) {
            return DownloadSnapshot(0L, 0L, 0L, 0, TorrentSessionState.UNKNOWN)
        }
        val snapshot = snapshotsToReturn[snapshotIdx.coerceAtMost(snapshotsToReturn.lastIndex)]
        snapshotIdx++
        if (pauseAfterSnapshots != null && snapshotIdx >= pauseAfterSnapshots!!) {
            shouldPauseNow = true
        }
        return snapshot
    }

    override suspend fun removeKeepingFiles(infoHash: String) {
        removeKeepingFilesCalls++
    }

    override suspend fun removeAndDelete(infoHash: String) {
        removeAndDeleteCalls++
    }

    override fun isStreamingActive(infoHash: String): Boolean = activeInStreamingHashes.contains(infoHash.lowercase())

    override fun isPaused(infoHash: String): Boolean = shouldPauseNow
}
