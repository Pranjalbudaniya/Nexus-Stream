package com.nexus.stream.data.download

import com.nexus.stream.data.torrent.NetworkStatus
import com.nexus.stream.domain.model.DownloadDisplay
import com.nexus.stream.domain.model.DownloadEffect
import com.nexus.stream.domain.model.DownloadFailure
import com.nexus.stream.domain.model.DownloadItem
import com.nexus.stream.domain.model.DownloadKind
import com.nexus.stream.domain.model.DownloadLive
import com.nexus.stream.domain.model.DownloadRequest
import com.nexus.stream.domain.model.DownloadSource
import com.nexus.stream.domain.model.DownloadState
import com.nexus.stream.domain.model.EnqueueResult
import com.nexus.stream.domain.model.PauseReason
import com.nexus.stream.domain.repository.ContentPrefsRepository
import com.nexus.stream.domain.repository.DownloadClock
import com.nexus.stream.domain.repository.DownloadFileStore
import com.nexus.stream.domain.repository.DownloadManager
import com.nexus.stream.domain.repository.DownloadNetwork
import com.nexus.stream.domain.repository.DownloadNotifier
import com.nexus.stream.domain.repository.DownloadPlatformComponents
import com.nexus.stream.domain.repository.DownloadPrefsRepository
import com.nexus.stream.domain.repository.DownloadRepository
import com.nexus.stream.domain.repository.DownloadServiceController
import com.nexus.stream.domain.repository.HttpDownloader
import com.nexus.stream.domain.repository.HttpResult
import com.nexus.stream.domain.repository.HttpTask
import com.nexus.stream.domain.repository.ProxyDiagnostics
import com.nexus.stream.domain.repository.ProxySession
import com.nexus.stream.domain.repository.StorageInfo
import com.nexus.stream.domain.repository.StreamProxy
import com.nexus.stream.domain.usecase.AggregateNotificationModel
import com.nexus.stream.domain.usecase.DownloadEventNotification
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import java.io.File

class FakeDownloadRepository : DownloadRepository {
    val itemsMap = MutableStateFlow<Map<String, DownloadItem>>(emptyMap())
    var updateProgressCalls = 0

    override fun observeAll(): Flow<List<DownloadItem>> =
        itemsMap.map { it.values.toList().sortedByDescending { item -> item.createdAt } }

    override fun observeForMeta(metaId: String): Flow<List<DownloadItem>> =
        itemsMap.map { it.values.filter { item -> item.metaId == metaId } }

    override suspend fun get(id: String): DownloadItem? = itemsMap.value[id]

    override suspend fun getAll(): List<DownloadItem> = itemsMap.value.values.toList()

    override suspend fun insert(item: DownloadItem) {
        itemsMap.value += (item.id to item)
    }

    override suspend fun updateState(
        id: String,
        state: DownloadState,
        updatedAt: Long,
        pauseReason: PauseReason?,
        failure: DownloadFailure?,
    ) {
        val cur = itemsMap.value[id] ?: return
        itemsMap.value +=
            (id to cur.copy(state = state, pauseReason = pauseReason, failure = failure, updatedAt = updatedAt))
    }

    override suspend fun updateProgress(
        id: String,
        downloadedBytes: Long,
        totalBytes: Long,
        state: DownloadState,
        updatedAt: Long,
    ) {
        updateProgressCalls++
        val cur = itemsMap.value[id] ?: return
        itemsMap.value +=
            (
                id to
                    cur.copy(
                        downloadedBytes = downloadedBytes,
                        totalBytes = totalBytes,
                        state = state,
                        updatedAt = updatedAt,
                    )
            )
    }

    override suspend fun updateSource(
        id: String,
        source: DownloadSource,
        state: DownloadState,
        updatedAt: Long,
    ) {
        val cur = itemsMap.value[id] ?: return
        itemsMap.value += (id to cur.copy(source = source, state = state, updatedAt = updatedAt))
    }

    override suspend fun setAllowMetered(
        id: String,
        allowMetered: Boolean,
        updatedAt: Long,
    ) {
        val cur = itemsMap.value[id] ?: return
        itemsMap.value += (id to cur.copy(allowMetered = allowMetered, updatedAt = updatedAt))
    }

    override suspend fun updateCompleted(
        id: String,
        filePath: String,
        totalBytes: Long,
        completedAt: Long,
        updatedAt: Long,
    ) {
        val cur = itemsMap.value[id] ?: return
        itemsMap.value +=
            (
                id to
                    cur.copy(
                        filePath = filePath,
                        totalBytes = totalBytes,
                        downloadedBytes = totalBytes,
                        state = DownloadState.COMPLETED,
                        completedAt = completedAt,
                        updatedAt = updatedAt,
                    )
            )
    }

    override suspend fun delete(id: String) {
        itemsMap.value -= id
    }
}

class FakeHttpDownloader : HttpDownloader {
    var handler: (suspend (HttpTask, (Long, Long) -> Unit) -> HttpResult)? = null
    val recordedTasks = mutableListOf<HttpTask>()

    override suspend fun download(
        task: HttpTask,
        onProgress: (bytes: Long, total: Long) -> Unit,
    ): HttpResult {
        recordedTasks.add(task)
        return handler?.invoke(task, onProgress) ?: HttpResult.Completed(total = 1000L)
    }
}

class FakeDownloadFileStore(
    override val root: File,
) : DownloadFileStore {
    override fun partFile(id: String): File = File(root, "$id.part")

    override fun finishedFile(
        id: String,
        extension: String,
    ): File = File(root, "$id.$extension")

    override fun fileOf(id: String): File? =
        root.listFiles()?.firstOrNull { it.name.startsWith("$id.") && !it.name.endsWith(".part") }

    override fun complete(
        id: String,
        extension: String,
    ): File {
        val part = partFile(id)
        val finished = finishedFile(id, extension)
        if (part.exists()) {
            part.renameTo(finished)
        } else {
            finished.createNewFile()
        }
        return finished
    }

    override fun delete(id: String) {
        root.listFiles()?.filter { it.name.startsWith("$id.") }?.forEach { it.delete() }
    }

    override fun deletePart(id: String) {
        partFile(id).delete()
    }

    override fun listAllFiles(): List<File> = root.listFiles()?.toList().orEmpty()
}

class FakeDownloadNetwork : DownloadNetwork {
    val statusFlow = MutableStateFlow(NetworkStatus.UNMETERED)
    override val status: StateFlow<NetworkStatus> = statusFlow
}

class FakeStorageInfo : StorageInfo {
    var available: Long = 10L * 1024 * 1024 * 1024

    override fun availableBytes(): Long = available

    override fun availableBytes(root: File): Long = available
}

class FakeDownloadNotifier : DownloadNotifier {
    val aggregateModels = mutableListOf<AggregateNotificationModel>()
    val eventNotifications = mutableListOf<DownloadEventNotification>()

    override fun showAggregate(model: AggregateNotificationModel) {
        aggregateModels.add(model)
    }

    override fun showEvent(notification: DownloadEventNotification) {
        eventNotifications.add(notification)
    }

    override fun cancelAggregate() {
        // No-op for test
    }

    override fun cancelEvent(downloadId: String) {
        // No-op for test
    }
}

class FakeDownloadServiceController : DownloadServiceController {
    var ensureRunningCalled = 0
    var stopCalled = 0
    var shouldThrowOnStart = false

    override fun ensureRunning() {
        ensureRunningCalled++
        if (shouldThrowOnStart) error("Service start refused")
    }

    override fun stop() {
        stopCalled++
    }
}

class FakeStreamProxy : StreamProxy {
    val openedUrls = mutableListOf<String>()
    val closedUrls = mutableListOf<String>()

    override fun open(
        url: String,
        headers: Map<String, String>,
    ): ProxySession {
        openedUrls.add(url)
        return ProxySession(
            url = "$url?proxied=true",
            diagnostics = MutableStateFlow(ProxyDiagnostics()),
            onClose = { closedUrls.add(url) },
        )
    }
}

class FakeDownloadClock : DownloadClock {
    var currentTime: Long = 1000L

    override fun now(): Long = currentTime
}

class FakeDownloadPrefsRepository : DownloadPrefsRepository {
    override val wifiOnly = MutableStateFlow(true)
    override val maxConcurrent = MutableStateFlow(2)
    override val introSeen = MutableStateFlow(false)

    override suspend fun setWifiOnly(enabled: Boolean) {
        wifiOnly.value = enabled
    }

    override suspend fun setMaxConcurrent(count: Int) {
        maxConcurrent.value = count
    }

    override suspend fun markIntroSeen() {
        introSeen.value = true
    }
}

class FakeContentPrefsRepository(
    initialFamilyMode: Boolean = false,
) : ContentPrefsRepository {
    val familyModeFlow = MutableStateFlow(initialFamilyMode)
    override val familyMode: Flow<Boolean> = familyModeFlow

    override suspend fun setFamilyMode(enabled: Boolean) {
        familyModeFlow.value = enabled
    }
}

data class DownloadTestDependencies(
    val repo: FakeDownloadRepository = FakeDownloadRepository(),
    val prefs: FakeDownloadPrefsRepository = FakeDownloadPrefsRepository(),
    val downloader: FakeHttpDownloader = FakeHttpDownloader(),
    val fileStore: FakeDownloadFileStore,
    val network: FakeDownloadNetwork = FakeDownloadNetwork(),
    val storageInfo: FakeStorageInfo = FakeStorageInfo(),
    val notifier: FakeDownloadNotifier = FakeDownloadNotifier(),
    val serviceController: FakeDownloadServiceController = FakeDownloadServiceController(),
    val streamProxy: FakeStreamProxy = FakeStreamProxy(),
    val clock: FakeDownloadClock = FakeDownloadClock(),
)

fun createTestDownloadManager(
    scope: CoroutineScope,
    dispatcher: CoroutineDispatcher,
    deps: DownloadTestDependencies,
): DefaultDownloadManager {
    val platform =
        DownloadPlatformComponents(
            downloader = deps.downloader,
            fileStore = deps.fileStore,
            network = deps.network,
            storageInfo = deps.storageInfo,
            notifier = deps.notifier,
            serviceController = deps.serviceController,
        )
    return DefaultDownloadManager(
        repository = deps.repo,
        prefsRepository = deps.prefs,
        streamProxy = deps.streamProxy,
        clock = deps.clock,
        platform = platform,
        scope = CoroutineScope(scope.coroutineContext + dispatcher),
    )
}

fun testRequest(
    url: String = "https://example.com/video.mp4",
    metaId: String = "tt123",
    videoId: String = "tt123:1:1",
    title: String = "Test Title",
    sizeHint: Long? = 1000L,
) = DownloadRequest(
    source = DownloadSource.Http(url),
    display =
        DownloadDisplay(
            metaId = metaId,
            videoId = videoId,
            title = title,
            type = "movie",
            fileNameHint = "video.mp4",
        ),
    sizeHint = sizeHint,
)

fun createTestDownloadItem(
    id: String,
    state: DownloadState = DownloadState.COMPLETED,
    sourceUrl: String = "https://example.com/v.mp4",
    filePath: String? = null,
    downloadedBytes: Long = 0L,
) = DownloadItem(
    id = id,
    kind = DownloadKind.HTTP,
    source = DownloadSource.Http(sourceUrl),
    metaId = "m1",
    type = "movie",
    title = "T",
    videoId = "v1",
    fileName = "$id.mp4",
    downloadedBytes = downloadedBytes,
    totalBytes = 1000L,
    state = state,
    failure = null,
    pauseReason = null,
    filePath = filePath,
    supportsResume = true,
    createdAt = 100L,
    updatedAt = 100L,
)

class FakeDownloadManager : DownloadManager {
    val itemsFlow = MutableStateFlow<List<DownloadItem>>(emptyList())
    override val items: Flow<List<DownloadItem>> = itemsFlow

    val liveFlow = MutableStateFlow<Map<String, DownloadLive>>(emptyMap())
    override val live: StateFlow<Map<String, DownloadLive>> = liveFlow

    val effectsFlow = MutableSharedFlow<DownloadEffect>()
    override val effects: Flow<DownloadEffect> = effectsFlow

    val enqueueCalls = mutableListOf<DownloadRequest>()
    var enqueueResult: EnqueueResult = EnqueueResult.Added("test-id")

    val pauseCalls = mutableListOf<String>()
    val resumeCalls = mutableListOf<String>()
    var pauseAllCalls = 0
    var resumeAllCalls = 0
    val allowMobileDataCalls = mutableListOf<String?>()
    val cancelCalls = mutableListOf<String>()
    val deleteCalls = mutableListOf<String>()
    var deleteCompletedCalls = 0
    val replaceSourceCalls = mutableListOf<Pair<String, DownloadSource>>()
    var fileOfResult: (String) -> File? = { null }
    var verifyCompletedResult: (String) -> Boolean = { true }

    override suspend fun enqueue(request: DownloadRequest): EnqueueResult {
        enqueueCalls.add(request)
        return enqueueResult
    }

    override fun pause(id: String) {
        pauseCalls.add(id)
    }

    override fun resume(id: String) {
        resumeCalls.add(id)
    }

    override fun pauseAll() {
        pauseAllCalls++
    }

    override fun resumeAll() {
        resumeAllCalls++
    }

    override fun allowMobileData(id: String?) {
        allowMobileDataCalls.add(id)
    }

    override suspend fun cancel(id: String) {
        cancelCalls.add(id)
    }

    override suspend fun delete(id: String) {
        deleteCalls.add(id)
    }

    override suspend fun deleteCompleted() {
        deleteCompletedCalls++
    }

    override suspend fun replaceSource(
        id: String,
        source: DownloadSource,
    ) {
        replaceSourceCalls.add(id to source)
    }

    override suspend fun onAppStart() {
        // No-op in test fake
    }

    override suspend fun onServiceTimeout() {
        // No-op in test fake
    }

    override suspend fun verifyCompleted(id: String): Boolean = verifyCompletedResult(id)

    override fun fileOf(id: String): File? = fileOfResult(id)
}
