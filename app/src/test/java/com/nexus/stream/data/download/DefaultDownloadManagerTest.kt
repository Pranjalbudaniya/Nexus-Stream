package com.nexus.stream.data.download

import com.nexus.stream.data.torrent.NetworkStatus
import com.nexus.stream.domain.model.DownloadDisplay
import com.nexus.stream.domain.model.DownloadEffect
import com.nexus.stream.domain.model.DownloadFailure
import com.nexus.stream.domain.model.DownloadPhase
import com.nexus.stream.domain.model.DownloadRequest
import com.nexus.stream.domain.model.DownloadSource
import com.nexus.stream.domain.model.DownloadState
import com.nexus.stream.domain.model.EnqueueResult
import com.nexus.stream.domain.model.PauseReason
import com.nexus.stream.domain.repository.HttpResult
import com.nexus.stream.domain.repository.TorrentFileChoice
import com.nexus.stream.domain.repository.TorrentResult
import com.nexus.stream.domain.repository.TorrentUpdate
import com.nexus.stream.domain.usecase.TorrentSessionState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultDownloadManagerTest : BaseDefaultDownloadManagerTest() {
    override val suiteTag: String = "DefaultDownloadManagerTest"

    @Test
    fun DM25_serviceTimeout_pausesRunningItems_andStopsTheService() =
        runTest {
            val (manager, deps) = createManager(backgroundScope, UnconfinedTestDispatcher(testScheduler))
            deps.downloader.handler = { _, _ -> CompletableDeferred<HttpResult>().await() }

            val id1 = (manager.enqueue(testRequest(videoId = "v1")) as EnqueueResult.Added).id
            val id2 = (manager.enqueue(testRequest(videoId = "v2")) as EnqueueResult.Added).id
            advanceUntilIdle()

            val effects = mutableListOf<DownloadEffect>()
            val job = launch(UnconfinedTestDispatcher(testScheduler)) { manager.effects.collect { effects.add(it) } }

            manager.onServiceTimeout()
            advanceUntilIdle()

            assertEquals(DownloadState.PAUSED, deps.repo.get(id1)?.state)
            assertEquals(PauseReason.SYSTEM_TIME_LIMIT, deps.repo.get(id1)?.pauseReason)
            assertEquals(DownloadState.PAUSED, deps.repo.get(id2)?.state)
            assertEquals(PauseReason.SYSTEM_TIME_LIMIT, deps.repo.get(id2)?.pauseReason)
            assertTrue(effects.contains(DownloadEffect.PausedBySystem))
            assertTrue(deps.serviceController.stopCalled > 0)
            job.cancel()
        }

    @Test
    fun DM26_progress_isPersistedAtMostEveryTwoSecondsOrOnePercent() =
        runTest {
            val (manager, deps) = createManager(backgroundScope, UnconfinedTestDispatcher(testScheduler))
            deps.downloader.handler = { _, onProgress ->
                for (i in 1..1000) {
                    deps.clock.currentTime += 10L
                    onProgress(i * 1000L, 10_000_000L)
                }
                HttpResult.Completed(10_000_000L)
            }

            manager.enqueue(testRequest())
            advanceUntilIdle()

            assertTrue(deps.repo.updateProgressCalls in 1..20)
        }

    @Test
    fun DM27_maxConcurrentChange_appliesToTheQueue() =
        runTest {
            val (manager, deps) = createManager(backgroundScope, UnconfinedTestDispatcher(testScheduler))
            deps.prefs.maxConcurrent.value = 1
            deps.downloader.handler = { _, _ -> CompletableDeferred<HttpResult>().await() }

            val id1 = (manager.enqueue(testRequest(videoId = "v1")) as EnqueueResult.Added).id
            val id2 = (manager.enqueue(testRequest(videoId = "v2")) as EnqueueResult.Added).id
            advanceUntilIdle()

            assertEquals(DownloadState.DOWNLOADING, deps.repo.get(id1)?.state)
            assertEquals(DownloadState.QUEUED, deps.repo.get(id2)?.state)

            deps.prefs.maxConcurrent.value = 2
            advanceUntilIdle()

            assertEquals(DownloadState.DOWNLOADING, deps.repo.get(id2)?.state)
        }

    @Test
    fun DM28_pauseWithoutResumeSupport_restartsFromZero() =
        runTest {
            val (manager, deps) = createManager(backgroundScope, UnconfinedTestDispatcher(testScheduler))
            val deferred = CompletableDeferred<Unit>()

            deps.downloader.handler = { task, onProgress ->
                task.partFile.writeBytes(ByteArray(500))
                onProgress(500, 1000)
                deferred.complete(Unit)
                CompletableDeferred<HttpResult>().await()
            }

            val id = (manager.enqueue(testRequest()) as EnqueueResult.Added).id
            deferred.await()

            deps.repo.itemsMap.value += (id to deps.repo.get(id)!!.copy(supportsResume = false))
            manager.pause(id)
            advanceUntilIdle()

            assertFalse(deps.fileStore.partFile(id).exists())

            deps.downloader.recordedTasks.clear()
            manager.resume(id)
            advanceUntilIdle()

            assertEquals(1, deps.downloader.recordedTasks.size)
            assertEquals(
                0L,
                deps.downloader.recordedTasks
                    .first()
                    .offset,
            )
        }

    @Test
    fun DM29_verifyCompleted_missingFile_marksFileMissing() =
        runTest {
            val (manager, deps) = createManager(backgroundScope, UnconfinedTestDispatcher(testScheduler))
            val item =
                createTestDownloadItem(id = "d29", state = DownloadState.COMPLETED, filePath = "/missing/d29.mp4")
            deps.repo.insert(item)

            val result = manager.verifyCompleted("d29")
            assertFalse(result)
            advanceUntilIdle()

            val updated = deps.repo.get("d29")
            assertEquals(DownloadState.FAILED, updated?.state)
            assertEquals(DownloadFailure.FILE_MISSING, updated?.failure)
        }

    @Test
    fun DM30_verifyCompleted_existingFile_returnsTrue() =
        runTest {
            val (manager, deps) = createManager(backgroundScope, UnconfinedTestDispatcher(testScheduler))
            val file = File(deps.fileStore.root, "d30.mp4")
            file.writeBytes(ByteArray(100))
            val item = createTestDownloadItem(id = "d30", state = DownloadState.COMPLETED, filePath = file.absolutePath)
            deps.repo.insert(item)

            val result = manager.verifyCompleted("d30")
            assertTrue(result)
            advanceUntilIdle()

            val updated = deps.repo.get("d30")
            assertEquals(DownloadState.COMPLETED, updated?.state)
        }

    @Test
    fun TM1_enqueueTorrent_isAccepted_andRejectsBadHashes() =
        runTest {
            val (manager, _) = createManager(backgroundScope, UnconfinedTestDispatcher(testScheduler))

            val goodReq = testTorrentRequest(hash = "0123456789abcdef0123456789abcdef01234567")
            val goodResult = manager.enqueue(goodReq)
            assertTrue(goodResult is EnqueueResult.Added)

            val blankReq = testTorrentRequest(hash = "", videoId = "vid2")
            val blankResult = manager.enqueue(blankReq)
            assertEquals(EnqueueResult.Rejected(DownloadFailure.UNSUPPORTED_SOURCE), blankResult)

            val badReq = testTorrentRequest(hash = "xyz", videoId = "vid3")
            val badResult = manager.enqueue(badReq)
            assertEquals(EnqueueResult.Rejected(DownloadFailure.UNSUPPORTED_SOURCE), badResult)
        }

    @Test
    fun TM2_torrent_startsWhenTheGateProceeds() =
        runTest {
            val (manager, deps) = createManager(backgroundScope, UnconfinedTestDispatcher(testScheduler))
            deps.network.statusFlow.value = NetworkStatus.UNMETERED
            deps.torrentDownloader.onDownloadBlock = { _, _ ->
                kotlinx.coroutines.awaitCancellation()
            }

            val req = testTorrentRequest()
            val result = manager.enqueue(req)
            assertTrue(result is EnqueueResult.Added)
            advanceUntilIdle()

            val item = deps.repo.get((result as EnqueueResult.Added).id)
            assertEquals(DownloadState.DOWNLOADING, item?.state)
            assertEquals(1, deps.torrentDownloader.downloadCalls.size)
            val call = deps.torrentDownloader.downloadCalls.first()
            assertEquals(File(deps.fileStore.root, "torrents/${item?.id}"), call.saveDir)
        }

    @Test
    fun TM3_torrent_waitsForWifi_likeHttp() =
        runTest {
            val (manager, deps) = createManager(backgroundScope, UnconfinedTestDispatcher(testScheduler))
            deps.network.statusFlow.value = NetworkStatus.METERED
            deps.prefs.setWifiOnly(true)
            deps.torrentDownloader.onDownloadBlock = { _, _ -> kotlinx.coroutines.awaitCancellation() }

            val req = testTorrentRequest()
            val result = manager.enqueue(req)
            assertTrue(result is EnqueueResult.Added)
            advanceUntilIdle()

            val id = (result as EnqueueResult.Added).id
            assertEquals(DownloadState.WAITING_FOR_WIFI, deps.repo.get(id)?.state)

            manager.allowMobileData(id)
            advanceUntilIdle()

            assertEquals(DownloadState.DOWNLOADING, deps.repo.get(id)?.state)
        }

    @Test
    fun TM4_needsFilePick_setsTheState_andSelectResumesWithThatFile() =
        runTest {
            val (manager, deps) = createManager(backgroundScope, UnconfinedTestDispatcher(testScheduler))
            deps.network.statusFlow.value = NetworkStatus.UNMETERED
            deps.torrentDownloader.resultToReturn =
                TorrentResult.NeedsUserPick(
                    listOf(
                        TorrentFileChoice(1, "video1.mkv", 1000L),
                        TorrentFileChoice(3, "video3.mkv", 3000L),
                    ),
                )

            val req = testTorrentRequest()
            val result = manager.enqueue(req)
            val id = (result as EnqueueResult.Added).id
            advanceUntilIdle()

            assertEquals(DownloadState.NEEDS_FILE_PICK, deps.repo.get(id)?.state)

            deps.torrentDownloader.resultToReturn =
                TorrentResult.Completed(File(deps.fileStore.root, "torrents/$id/video3.mkv"), 3000L)
            manager.selectTorrentFile(id, 3)
            advanceUntilIdle()

            val updated = deps.repo.get(id)
            assertEquals(DownloadState.COMPLETED, updated?.state)
            val torrentSource = updated?.source as DownloadSource.Torrent
            assertEquals(3, torrentSource.fileIdx)
        }

    @Test
    fun TM5_torrentComplete_marksCompleted_withTheFilePath() =
        runTest {
            val (manager, deps) = createManager(backgroundScope, UnconfinedTestDispatcher(testScheduler))
            deps.network.statusFlow.value = NetworkStatus.UNMETERED
            val completedFile = File(deps.fileStore.root, "torrents/item1/movie.mkv")
            deps.torrentDownloader.resultToReturn = TorrentResult.Completed(completedFile, 2048L)

            val effects = mutableListOf<DownloadEffect>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                manager.effects.collect { effects.add(it) }
            }

            val result = manager.enqueue(testTorrentRequest())
            val id = (result as EnqueueResult.Added).id
            advanceUntilIdle()

            val item = deps.repo.get(id)
            assertEquals(DownloadState.COMPLETED, item?.state)
            assertEquals(completedFile.absolutePath, item?.filePath)
            assertEquals(2048L, item?.totalBytes)
            assertTrue(effects.any { it is DownloadEffect.Completed && it.id == id })
        }

    @Test
    fun TM6_torrentFailure_noPeers_marksFailed() =
        runTest {
            val (manager, deps) = createManager(backgroundScope, UnconfinedTestDispatcher(testScheduler))
            deps.network.statusFlow.value = NetworkStatus.UNMETERED
            deps.torrentDownloader.resultToReturn = TorrentResult.Failed(DownloadFailure.TORRENT_NO_PEERS)

            val effects = mutableListOf<DownloadEffect>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                manager.effects.collect { effects.add(it) }
            }

            val result = manager.enqueue(testTorrentRequest())
            val id = (result as EnqueueResult.Added).id
            advanceUntilIdle()

            val item = deps.repo.get(id)
            assertEquals(DownloadState.FAILED, item?.state)
            assertEquals(DownloadFailure.TORRENT_NO_PEERS, item?.failure)
            assertTrue(
                effects.any {
                    it is DownloadEffect.Failed && it.id == id &&
                        it.failure == DownloadFailure.TORRENT_NO_PEERS
                },
            )
        }

    @Test
    fun TM7_pause_resume_torrent() =
        runTest {
            val (manager, deps) = createManager(backgroundScope, UnconfinedTestDispatcher(testScheduler))
            deps.network.statusFlow.value = NetworkStatus.UNMETERED
            deps.torrentDownloader.onDownloadBlock = { _, _ -> kotlinx.coroutines.awaitCancellation() }

            val result = manager.enqueue(testTorrentRequest())
            val id = (result as EnqueueResult.Added).id
            advanceUntilIdle()
            assertEquals(DownloadState.DOWNLOADING, deps.repo.get(id)?.state)

            manager.pause(id)
            advanceUntilIdle()
            assertEquals(DownloadState.PAUSED, deps.repo.get(id)?.state)
            assertEquals(PauseReason.USER, deps.repo.get(id)?.pauseReason)

            deps.torrentDownloader.downloadCalls.clear()
            manager.resume(id)
            advanceUntilIdle()

            assertEquals(DownloadState.DOWNLOADING, deps.repo.get(id)?.state)
            assertEquals(1, deps.torrentDownloader.downloadCalls.size)
            assertEquals(
                File(deps.fileStore.root, "torrents/$id"),
                deps.torrentDownloader.downloadCalls
                    .first()
                    .saveDir,
            )
        }

    @Test
    fun TM8_cancel_torrent_deletesTheFolder() =
        runTest {
            val (manager, deps) = createManager(backgroundScope, UnconfinedTestDispatcher(testScheduler))
            deps.network.statusFlow.value = NetworkStatus.UNMETERED
            deps.torrentDownloader.onDownloadBlock = { _, _ -> kotlinx.coroutines.awaitCancellation() }

            val result = manager.enqueue(testTorrentRequest())
            val id = (result as EnqueueResult.Added).id
            advanceUntilIdle()

            val folder = File(deps.fileStore.root, "torrents/$id")
            folder.mkdirs()
            File(folder, "partial.mkv").writeBytes(ByteArray(100))
            assertTrue(folder.exists())

            manager.cancel(id)
            advanceUntilIdle()

            assertNull(deps.repo.get(id))
            assertFalse(folder.exists())
        }

    @Test
    fun TM9_delete_completedTorrent_deletesTheWholeFolder() =
        runTest {
            val (manager, deps) = createManager(backgroundScope, UnconfinedTestDispatcher(testScheduler))
            val folder = File(deps.fileStore.root, "torrents/item1")
            folder.mkdirs()
            val completedFile = File(folder, "movie.mkv")
            completedFile.writeBytes(ByteArray(200))

            val item =
                createTestTorrentDownloadItem(
                    id = "item1",
                    state = DownloadState.COMPLETED,
                    filePath = completedFile.absolutePath,
                )
            deps.repo.insert(item)

            manager.delete("item1")
            advanceUntilIdle()

            assertNull(deps.repo.get("item1"))
            assertFalse(folder.exists())
        }

    @Test
    fun TM10_onAppStart_torrentDownloading_becomesQueued_andResumes() =
        runTest {
            val (manager, deps) = createManager(backgroundScope, UnconfinedTestDispatcher(testScheduler))
            deps.network.statusFlow.value = NetworkStatus.UNMETERED
            deps.torrentDownloader.onDownloadBlock = { _, _ -> kotlinx.coroutines.awaitCancellation() }

            val item =
                createTestTorrentDownloadItem(
                    id = "item1",
                    state = DownloadState.DOWNLOADING,
                )
            deps.repo.insert(item)

            manager.onAppStart()
            advanceUntilIdle()

            val updated = deps.repo.get("item1")
            assertEquals(DownloadState.DOWNLOADING, updated?.state)
            assertEquals(1, deps.torrentDownloader.downloadCalls.size)
        }

    @Test
    fun TM11_onAppStart_orphanTorrentFolders_areDeleted() =
        runTest {
            val (manager, deps) = createManager(backgroundScope, UnconfinedTestDispatcher(testScheduler))
            val torrentsRoot = File(deps.fileStore.root, "torrents")
            val dirA1 = File(torrentsRoot, "a1").apply { mkdirs() }
            val dirZz = File(torrentsRoot, "zz").apply { mkdirs() }

            val item =
                createTestTorrentDownloadItem(
                    id = "a1",
                    state = DownloadState.COMPLETED,
                )
            deps.repo.insert(item)

            manager.onAppStart()
            advanceUntilIdle()

            assertTrue(dirA1.exists())
            assertFalse(dirZz.exists())
        }

    @Test
    fun TM12_onAppStart_completedTorrentFileMissing_marksFileMissing() =
        runTest {
            val (manager, deps) = createManager(backgroundScope, UnconfinedTestDispatcher(testScheduler))
            val item =
                createTestTorrentDownloadItem(
                    id = "item1",
                    state = DownloadState.COMPLETED,
                    filePath = "/missing/torrents/item1/movie.mkv",
                )
            deps.repo.insert(item)

            manager.onAppStart()
            advanceUntilIdle()

            val updated = deps.repo.get("item1")
            assertEquals(DownloadState.FAILED, updated?.state)
            assertEquals(DownloadFailure.FILE_MISSING, updated?.failure)
        }

    @Test
    fun TM13_duplicate_sameInfoHashOtherEpisode_isAllowed() =
        runTest {
            val (manager, deps) = createManager(backgroundScope, UnconfinedTestDispatcher(testScheduler))
            deps.network.statusFlow.value = NetworkStatus.UNMETERED

            val req1 = testTorrentRequest(videoId = "vid:1:1", season = 1, episode = 1)
            val res1 = manager.enqueue(req1)
            assertTrue(res1 is EnqueueResult.Added)

            val req2 = testTorrentRequest(videoId = "vid:1:2", season = 1, episode = 2)
            val res2 = manager.enqueue(req2)
            assertTrue(res2 is EnqueueResult.Added)

            val req1Again = testTorrentRequest(videoId = "vid:1:1", season = 1, episode = 1)
            val resDup = manager.enqueue(req1Again)
            assertTrue(resDup is EnqueueResult.Duplicate)
        }

    @Test
    fun TM14_liveProgress_carriesPeersAndPhase() =
        runTest {
            val (manager, deps) = createManager(backgroundScope, UnconfinedTestDispatcher(testScheduler))
            deps.network.statusFlow.value = NetworkStatus.UNMETERED
            deps.torrentDownloader.updatesToEmit =
                listOf(
                    TorrentUpdate(DownloadPhase.CHECKING_FILES, 100L, 1000L, 0L, 5),
                    TorrentUpdate(DownloadPhase.TRANSFERRING, 500L, 1000L, 100L, 5),
                )
            deps.torrentDownloader.onDownloadBlock = { _, _ -> kotlinx.coroutines.awaitCancellation() }

            val result = manager.enqueue(testTorrentRequest())
            val id = (result as EnqueueResult.Added).id
            advanceUntilIdle()

            val live = manager.live.value[id]
            assertNotNull(live)
            assertEquals(5, live?.peers)
            assertEquals(DownloadPhase.TRANSFERRING, live?.phase)
        }

    @Test
    fun TM15_httpAndTorrent_shareTheConcurrencyLimit_andSameHashRunsOneAtATime() =
        runTest {
            val (manager, deps) = createManager(backgroundScope, UnconfinedTestDispatcher(testScheduler))
            deps.network.statusFlow.value = NetworkStatus.UNMETERED
            deps.prefs.setMaxConcurrent(2)

            val httpStarted = CompletableDeferred<Unit>()
            deps.downloader.handler = { _, _ ->
                httpStarted.complete(Unit)
                kotlinx.coroutines.awaitCancellation()
            }
            val torrent1Started = CompletableDeferred<Unit>()
            var torrent2Started = false
            deps.torrentDownloader.onDownloadBlock = { task, _ ->
                if (task.episode == 1) {
                    torrent1Started.complete(Unit)
                    kotlinx.coroutines.awaitCancellation()
                } else {
                    torrent2Started = true
                    kotlinx.coroutines.awaitCancellation()
                }
            }

            manager.enqueue(testRequest(url = "https://example.com/http.mp4", videoId = "http1"))
            manager.enqueue(testTorrentRequest(videoId = "tor1", season = 1, episode = 1))
            manager.enqueue(testTorrentRequest(videoId = "tor2", season = 1, episode = 2))
            advanceUntilIdle()

            assertTrue(httpStarted.isCompleted)
            assertTrue(torrent1Started.isCompleted)
            assertFalse(torrent2Started)
        }

    private fun testTorrentRequest(
        hash: String = "0123456789abcdef0123456789abcdef01234567",
        metaId: String = "tt123",
        videoId: String = "tt123:1:1",
        season: Int? = 1,
        episode: Int? = 1,
    ) = DownloadRequest(
        source = DownloadSource.Torrent(hash, emptyList(), null),
        display =
            DownloadDisplay(
                metaId = metaId,
                type = "series",
                title = "Torrent Show",
                videoId = videoId,
                season = season,
                episode = episode,
            ),
    )
}
