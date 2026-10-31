package com.nexus.stream.data.download

import com.nexus.stream.data.torrent.NetworkStatus
import com.nexus.stream.domain.model.DownloadDisplay
import com.nexus.stream.domain.model.DownloadEffect
import com.nexus.stream.domain.model.DownloadFailure
import com.nexus.stream.domain.model.DownloadRequest
import com.nexus.stream.domain.model.DownloadSource
import com.nexus.stream.domain.model.DownloadState
import com.nexus.stream.domain.model.EnqueueResult
import com.nexus.stream.domain.model.PauseReason
import com.nexus.stream.domain.repository.HttpResult
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
abstract class BaseDefaultDownloadManagerTest {
    protected abstract val suiteTag: String

    @get:Rule
    val tmpFolder = TemporaryFolder()

    protected fun createManager(
        scope: CoroutineScope,
        dispatcher: CoroutineDispatcher,
    ): Pair<DefaultDownloadManager, DownloadTestDependencies> {
        val deps = DownloadTestDependencies(fileStore = FakeDownloadFileStore(tmpFolder.root))
        return Pair(createTestDownloadManager(scope, dispatcher, deps), deps)
    }

    @Test
    fun DM1_enqueue_createsAQueuedItem_andStartsIt() =
        runTest {
            val (manager, deps) = createManager(backgroundScope, UnconfinedTestDispatcher(testScheduler))
            deps.network.statusFlow.value = NetworkStatus.UNMETERED

            val result = manager.enqueue(testRequest())
            assertTrue(result is EnqueueResult.Added)
            advanceUntilIdle()

            val items = deps.repo.observeAll().first()
            assertEquals(1, items.size)
            assertTrue(deps.serviceController.ensureRunningCalled > 0)
        }

    @Test
    fun DM2_enqueue_duplicate_returnsDuplicate() =
        runTest {
            val (manager, deps) = createManager(backgroundScope, UnconfinedTestDispatcher(testScheduler))
            val req = testRequest(metaId = "tt1", videoId = "v1")

            assertTrue(manager.enqueue(req) is EnqueueResult.Added)
            assertTrue(manager.enqueue(req) is EnqueueResult.Duplicate)
            assertEquals(
                1,
                deps.repo
                    .observeAll()
                    .first()
                    .size,
            )
        }

    @Test
    fun DM3_enqueue_rejectsBadSourcesAndTorrents() =
        runTest {
            val (manager, deps) = createManager(backgroundScope, UnconfinedTestDispatcher(testScheduler))

            val blankRes = manager.enqueue(testRequest(url = ""))
            assertEquals(EnqueueResult.Rejected(DownloadFailure.UNSUPPORTED_SOURCE), blankRes)
            assertEquals(
                EnqueueResult.Rejected(DownloadFailure.UNSUPPORTED_SOURCE),
                manager.enqueue(testRequest(url = "ftp://example.com/video.mp4")),
            )
            val torrentReq =
                DownloadRequest(
                    source = DownloadSource.Torrent("abc123hash"),
                    display = DownloadDisplay(metaId = "m1", videoId = "v1", title = "T", type = "movie"),
                )
            assertEquals(EnqueueResult.Rejected(DownloadFailure.UNSUPPORTED_SOURCE), manager.enqueue(torrentReq))
            assertEquals(
                0,
                deps.repo
                    .observeAll()
                    .first()
                    .size,
            )
        }

    @Test
    fun DM4_concurrency_startsOnlyTheConfiguredMax() =
        runTest {
            val (manager, deps) = createManager(backgroundScope, UnconfinedTestDispatcher(testScheduler))
            deps.prefs.maxConcurrent.value = 2

            val deferred = CompletableDeferred<HttpResult>()
            deps.downloader.handler = { _, _ -> deferred.await() }

            manager.enqueue(testRequest(videoId = "v1"))
            manager.enqueue(testRequest(videoId = "v2"))
            manager.enqueue(testRequest(videoId = "v3"))
            advanceUntilIdle()

            val items = deps.repo.observeAll().first()
            assertEquals(2, items.count { it.state == DownloadState.DOWNLOADING })
            assertEquals(1, items.count { it.state == DownloadState.QUEUED })
            deferred.complete(HttpResult.Completed(1000L))
        }

    @Test
    fun DM5_finishingOne_startsTheNextQueued() =
        runTest {
            val (manager, deps) = createManager(backgroundScope, UnconfinedTestDispatcher(testScheduler))
            deps.prefs.maxConcurrent.value = 2

            val deferred1 = CompletableDeferred<HttpResult>()
            val deferredOthers = CompletableDeferred<HttpResult>()
            var callCount = 0
            deps.downloader.handler = { _, _ ->
                callCount++
                if (callCount == 1) deferred1.await() else deferredOthers.await()
            }

            manager.enqueue(testRequest(videoId = "v1"))
            manager.enqueue(testRequest(videoId = "v2"))
            manager.enqueue(testRequest(videoId = "v3"))
            advanceUntilIdle()

            deferred1.complete(HttpResult.Completed(1000L))
            advanceUntilIdle()

            val items = deps.repo.observeAll().first()
            assertEquals(1, items.count { it.state == DownloadState.COMPLETED })
            assertEquals(2, items.count { it.state == DownloadState.DOWNLOADING })
            deferredOthers.complete(HttpResult.Completed(1000L))
        }

    @Test
    fun DM6_complete_marksCompleted_setsPath_emitsCompleted() =
        runTest {
            val (manager, deps) = createManager(backgroundScope, UnconfinedTestDispatcher(testScheduler))
            deps.downloader.handler = { _, _ -> HttpResult.Completed(total = 5000L) }

            val effects = mutableListOf<DownloadEffect>()
            val job = launch(UnconfinedTestDispatcher(testScheduler)) { manager.effects.collect { effects.add(it) } }

            val id = (manager.enqueue(testRequest()) as EnqueueResult.Added).id
            advanceUntilIdle()

            val item = deps.repo.get(id)
            assertNotNull(item)
            assertEquals(DownloadState.COMPLETED, item?.state)
            assertEquals(5000L, item?.totalBytes)
            assertNotNull(item?.filePath)
            assertNotNull(item?.completedAt)
            assertTrue(effects.any { it is DownloadEffect.Completed && it.id == id })
            job.cancel()
        }

    @Test
    fun DM7_failure_marksFailed_withReason_emitsFailed() =
        runTest {
            val (manager, deps) = createManager(backgroundScope, UnconfinedTestDispatcher(testScheduler))
            deps.downloader.handler = { _, _ -> HttpResult.Failed(DownloadFailure.SERVER_ERROR) }

            val effects = mutableListOf<DownloadEffect>()
            val job = launch(UnconfinedTestDispatcher(testScheduler)) { manager.effects.collect { effects.add(it) } }

            val id = (manager.enqueue(testRequest()) as EnqueueResult.Added).id
            advanceUntilIdle()

            val item = deps.repo.get(id)
            assertEquals(DownloadState.FAILED, item?.state)
            assertEquals(DownloadFailure.SERVER_ERROR, item?.failure)
            val hasFailedEffect =
                effects.any {
                    it is DownloadEffect.Failed && it.id == id && it.failure == DownloadFailure.SERVER_ERROR
                }
            assertTrue(hasFailedEffect)
            job.cancel()
        }

    @Test
    fun DM8_pause_stopsTheTransfer_keepsThePartFile() =
        runTest {
            val (manager, deps) = createManager(backgroundScope, UnconfinedTestDispatcher(testScheduler))
            val runningDeferred = CompletableDeferred<Unit>()

            deps.downloader.handler = { task, onProgress ->
                task.partFile.writeBytes(ByteArray(500))
                onProgress(500, 1000)
                runningDeferred.complete(Unit)
                CompletableDeferred<HttpResult>().await()
            }

            val id = (manager.enqueue(testRequest()) as EnqueueResult.Added).id
            runningDeferred.await()
            manager.pause(id)
            advanceUntilIdle()

            val item = deps.repo.get(id)
            assertEquals(DownloadState.PAUSED, item?.state)
            assertEquals(PauseReason.USER, item?.pauseReason)
            assertTrue(deps.fileStore.partFile(id).exists())
            assertEquals(500L, deps.fileStore.partFile(id).length())
        }

    @Test
    fun DM9_resume_continuesFromThePartFile_andIgnoresRepeats() =
        runTest {
            val (manager, deps) = createManager(backgroundScope, UnconfinedTestDispatcher(testScheduler))
            val runningDeferred = CompletableDeferred<Unit>()
            deps.downloader.handler = { _, _ ->
                runningDeferred.complete(Unit)
                CompletableDeferred<HttpResult>().await()
            }
            val id = (manager.enqueue(testRequest()) as EnqueueResult.Added).id
            runningDeferred.await()

            manager.pause(id)
            advanceUntilIdle()

            deps.fileStore.partFile(id).writeBytes(ByteArray(500))
            deps.downloader.recordedTasks.clear()

            manager.resume(id)
            manager.resume(id)
            advanceUntilIdle()

            assertEquals(1, deps.downloader.recordedTasks.size)
            assertEquals(
                500L,
                deps.downloader.recordedTasks
                    .first()
                    .offset,
            )
        }

    @Test
    fun DM10_cancel_deletesThePartFileAndTheRow() =
        runTest {
            val (manager, deps) = createManager(backgroundScope, UnconfinedTestDispatcher(testScheduler))
            deps.downloader.handler = { task, _ ->
                task.partFile.writeBytes(ByteArray(300))
                CompletableDeferred<HttpResult>().await()
            }

            val id = (manager.enqueue(testRequest()) as EnqueueResult.Added).id
            advanceUntilIdle()

            manager.cancel(id)
            advanceUntilIdle()

            assertNull(deps.repo.get(id))
            assertFalse(deps.fileStore.partFile(id).exists())
        }

    @Test
    fun DM11_delete_completed_deletesTheFileAndTheRow() =
        runTest {
            val (manager, deps) = createManager(backgroundScope, UnconfinedTestDispatcher(testScheduler))
            deps.downloader.handler = { task, _ ->
                task.partFile.writeBytes(ByteArray(1000))
                HttpResult.Completed(1000L)
            }

            val id = (manager.enqueue(testRequest()) as EnqueueResult.Added).id
            advanceUntilIdle()

            assertEquals(DownloadState.COMPLETED, deps.repo.get(id)?.state)
            manager.delete(id)
            advanceUntilIdle()

            assertNull(deps.repo.get(id))
            assertNull(deps.fileStore.fileOf(id))
        }

    @Test
    fun DM12_delete_missingFile_stillRemovesTheRow() =
        runTest {
            val (manager, deps) = createManager(backgroundScope, UnconfinedTestDispatcher(testScheduler))
            deps.repo.insert(createTestDownloadItem("d12"))

            manager.delete("d12")
            advanceUntilIdle()

            assertNull(deps.repo.get("d12"))
        }

    @Test
    fun DM13_pauseAll_resumeAll() =
        runTest {
            val (manager, deps) = createManager(backgroundScope, UnconfinedTestDispatcher(testScheduler))
            deps.downloader.handler = { _, _ -> CompletableDeferred<HttpResult>().await() }

            val id1 = (manager.enqueue(testRequest(videoId = "v1")) as EnqueueResult.Added).id
            val id2 = (manager.enqueue(testRequest(videoId = "v2")) as EnqueueResult.Added).id
            advanceUntilIdle()

            manager.pauseAll()
            advanceUntilIdle()
            assertEquals(DownloadState.PAUSED, deps.repo.get(id1)?.state)
            assertEquals(DownloadState.PAUSED, deps.repo.get(id2)?.state)

            manager.resumeAll()
            advanceUntilIdle()
            assertEquals(DownloadState.DOWNLOADING, deps.repo.get(id1)?.state)
            assertEquals(DownloadState.DOWNLOADING, deps.repo.get(id2)?.state)
        }

    @Test
    fun DM14_wifiOnly_metered_waits_thenAllowMobileData_starts() =
        runTest {
            val (manager, deps) = createManager(backgroundScope, UnconfinedTestDispatcher(testScheduler))
            deps.downloader.handler = { _, _ -> CompletableDeferred<HttpResult>().await() }
            deps.network.statusFlow.value = NetworkStatus.METERED
            deps.prefs.wifiOnly.value = true

            val id = (manager.enqueue(testRequest()) as EnqueueResult.Added).id
            advanceUntilIdle()
            assertEquals(DownloadState.WAITING_FOR_WIFI, deps.repo.get(id)?.state)
            assertEquals(0, deps.downloader.recordedTasks.size)

            manager.allowMobileData(id)
            advanceUntilIdle()
            assertEquals(DownloadState.DOWNLOADING, deps.repo.get(id)?.state)
            assertTrue(deps.prefs.wifiOnly.value)
        }

    @Test
    fun DM15_networkLost_waitsForNetwork_andResumesWhenItReturns() =
        runTest {
            val (manager, deps) = createManager(backgroundScope, UnconfinedTestDispatcher(testScheduler))
            deps.downloader.handler = { task, onProgress ->
                task.partFile.writeBytes(ByteArray(400))
                onProgress(400, 1000)
                CompletableDeferred<HttpResult>().await()
            }

            val id = (manager.enqueue(testRequest()) as EnqueueResult.Added).id
            advanceUntilIdle()

            deps.network.statusFlow.value = NetworkStatus.NONE
            advanceUntilIdle()
            assertEquals(DownloadState.WAITING_FOR_NETWORK, deps.repo.get(id)?.state)

            deps.downloader.recordedTasks.clear()
            deps.network.statusFlow.value = NetworkStatus.UNMETERED
            advanceUntilIdle()

            assertEquals(DownloadState.DOWNLOADING, deps.repo.get(id)?.state)
            assertEquals(
                400L,
                deps.downloader.recordedTasks
                    .first()
                    .offset,
            )
        }

    @Test
    fun DM16_networkBecomesUnmetered_startsWaitingWifiItems() =
        runTest {
            val (manager, deps) = createManager(backgroundScope, UnconfinedTestDispatcher(testScheduler))
            deps.downloader.handler = { _, _ -> CompletableDeferred<HttpResult>().await() }
            deps.network.statusFlow.value = NetworkStatus.METERED
            deps.prefs.wifiOnly.value = true

            val id = (manager.enqueue(testRequest()) as EnqueueResult.Added).id
            advanceUntilIdle()
            assertEquals(DownloadState.WAITING_FOR_WIFI, deps.repo.get(id)?.state)

            deps.network.statusFlow.value = NetworkStatus.UNMETERED
            advanceUntilIdle()
            assertEquals(DownloadState.DOWNLOADING, deps.repo.get(id)?.state)
        }

    @Test
    fun DM17_linkExpired_failsWithLinkExpired_andKeepsThePartFile() =
        runTest {
            val (manager, deps) = createManager(backgroundScope, UnconfinedTestDispatcher(testScheduler))
            deps.downloader.handler = { task, _ ->
                task.partFile.writeBytes(ByteArray(250))
                HttpResult.Failed(DownloadFailure.LINK_EXPIRED)
            }

            val id = (manager.enqueue(testRequest()) as EnqueueResult.Added).id
            advanceUntilIdle()

            val item = deps.repo.get(id)
            assertEquals(DownloadState.FAILED, item?.state)
            assertEquals(DownloadFailure.LINK_EXPIRED, item?.failure)
            assertTrue(deps.fileStore.partFile(id).exists())
            assertEquals(250L, deps.fileStore.partFile(id).length())
        }

    @Test
    fun DM18_replaceSource_keepsProgress_andRequeues() =
        runTest {
            val (manager, deps) = createManager(backgroundScope, UnconfinedTestDispatcher(testScheduler))
            deps.downloader.handler = { _, _ -> CompletableDeferred<HttpResult>().await() }
            val item =
                createTestDownloadItem(
                    id = "d18",
                    state = DownloadState.FAILED,
                    sourceUrl = "https://old.com/video.mp4",
                    downloadedBytes = 400L,
                ).copy(failure = DownloadFailure.LINK_EXPIRED)
            deps.repo.insert(item)
            deps.fileStore.partFile("d18").writeBytes(ByteArray(400))

            val newSource = DownloadSource.Http("https://new.com/video.mp4")
            manager.replaceSource("d18", newSource)
            advanceUntilIdle()

            val updated = deps.repo.get("d18")
            assertEquals(newSource, updated?.source)
            assertEquals(400L, updated?.downloadedBytes)
            assertEquals(DownloadState.DOWNLOADING, updated?.state)

            val completedItem = item.copy(id = "comp18", state = DownloadState.COMPLETED)
            deps.repo.insert(completedItem)
            manager.replaceSource("comp18", newSource)
            advanceUntilIdle()
            assertEquals(DownloadState.COMPLETED, deps.repo.get("comp18")?.state)
        }

    @Test
    fun DM19_insufficientStorage_failsBeforeAnyTransfer() =
        runTest {
            val (manager, deps) = createManager(backgroundScope, UnconfinedTestDispatcher(testScheduler))
            deps.storageInfo.available = 100L

            val id = (manager.enqueue(testRequest(sizeHint = 1_000_000_000L)) as EnqueueResult.Added).id
            advanceUntilIdle()

            val item = deps.repo.get(id)
            assertEquals(DownloadState.FAILED, item?.state)
            assertEquals(DownloadFailure.INSUFFICIENT_STORAGE, item?.failure)
            assertEquals(0, deps.downloader.recordedTasks.size)
        }

    @Test
    fun DM20_headers_areDownloadedThroughTheProxy_andClosedAfterwards() =
        runTest {
            val (manager, deps) = createManager(backgroundScope, UnconfinedTestDispatcher(testScheduler))
            val req =
                DownloadRequest(
                    source = DownloadSource.Http("https://example.com/video.mp4", mapOf("Cookie" to "user=1")),
                    display = DownloadDisplay(metaId = "m1", videoId = "v1", title = "T", type = "movie"),
                )

            manager.enqueue(req)
            advanceUntilIdle()

            assertTrue(deps.streamProxy.openedUrls.contains("https://example.com/video.mp4"))
            assertTrue(deps.streamProxy.closedUrls.contains("https://example.com/video.mp4"))
        }

    @Test
    fun DM21_start_interruptedDownloading_becomesQueued_andResumes() =
        runTest {
            val (manager, deps) = createManager(backgroundScope, UnconfinedTestDispatcher(testScheduler))
            deps.downloader.handler = { _, _ -> CompletableDeferred<HttpResult>().await() }
            val item =
                createTestDownloadItem(
                    id = "d21",
                    sourceUrl = "https://example.com/video.mp4",
                    state = DownloadState.DOWNLOADING,
                    downloadedBytes = 100L,
                )
            deps.repo.insert(item)
            deps.fileStore.partFile("d21").writeBytes(ByteArray(700))

            manager.onAppStart()
            advanceUntilIdle()

            assertEquals(DownloadState.DOWNLOADING, deps.repo.get("d21")?.state)
            assertEquals(
                700L,
                deps.downloader.recordedTasks
                    .first()
                    .offset,
            )
        }

    @Test
    fun DM22_start_missingCompletedFile_marksFileMissing() =
        runTest {
            val (manager, deps) = createManager(backgroundScope, UnconfinedTestDispatcher(testScheduler))
            val item =
                createTestDownloadItem(
                    id = "d22",
                    state = DownloadState.COMPLETED,
                    filePath = "/path/not/exist/d22.mp4",
                )
            deps.repo.insert(item)

            manager.onAppStart()
            advanceUntilIdle()

            val updated = deps.repo.get("d22")
            assertEquals(DownloadState.FAILED, updated?.state)
            assertEquals(DownloadFailure.FILE_MISSING, updated?.failure)
        }

    @Test
    fun DM23_start_orphanFiles_areDeleted() =
        runTest {
            val (manager, deps) = createManager(backgroundScope, UnconfinedTestDispatcher(testScheduler))
            val orphanPart = File(deps.fileStore.root, "orphan.part")
            orphanPart.writeBytes(ByteArray(100))
            val orphanMkv = File(deps.fileStore.root, "orphan.mkv")
            orphanMkv.writeBytes(ByteArray(100))

            val ownedItem = createTestDownloadItem("owned")
            deps.repo.insert(ownedItem)
            val ownedFile = File(deps.fileStore.root, "owned.mp4")
            ownedFile.writeBytes(ByteArray(200))

            manager.onAppStart()
            advanceUntilIdle()

            assertFalse(orphanPart.exists())
            assertFalse(orphanMkv.exists())
            assertTrue(ownedFile.exists())
        }

    @Test
    fun DM24_serviceController_followsActivity_andSwallowsRefusedStarts() =
        runTest {
            val (manager, deps) = createManager(backgroundScope, UnconfinedTestDispatcher(testScheduler))
            deps.serviceController.shouldThrowOnStart = true

            val res = manager.enqueue(testRequest())
            assertTrue(res is EnqueueResult.Added)
            advanceUntilIdle()

            assertTrue(deps.serviceController.ensureRunningCalled > 0)
        }
}
