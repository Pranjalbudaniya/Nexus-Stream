package com.nexus.stream.data.download

import com.nexus.stream.data.torrent.NetworkStatus
import com.nexus.stream.di.IoDispatcher
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
import com.nexus.stream.domain.model.NetworkGate
import com.nexus.stream.domain.model.PauseReason
import com.nexus.stream.domain.repository.DownloadClock
import com.nexus.stream.domain.repository.DownloadManager
import com.nexus.stream.domain.repository.DownloadPlatformComponents
import com.nexus.stream.domain.repository.DownloadPrefsRepository
import com.nexus.stream.domain.repository.DownloadRepository
import com.nexus.stream.domain.repository.HttpResult
import com.nexus.stream.domain.repository.HttpTask
import com.nexus.stream.domain.repository.StreamProxy
import com.nexus.stream.domain.usecase.SpeedMeter
import com.nexus.stream.domain.usecase.downloadFileExtension
import com.nexus.stream.domain.usecase.etaSeconds
import com.nexus.stream.domain.usecase.findDuplicate
import com.nexus.stream.domain.usecase.hasEnoughStorage
import com.nexus.stream.domain.usecase.networkGate
import com.nexus.stream.domain.usecase.pickToStart
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

private const val PROGRESS_PERSIST_INTERVAL_MS = 2000L
private const val PROGRESS_PERSIST_PERCENT = 1L
private const val PERCENT_MULTIPLIER = 100L

@Suppress("TooManyFunctions")
@Singleton
class DefaultDownloadManager(
    private val repository: DownloadRepository,
    private val prefsRepository: DownloadPrefsRepository,
    private val streamProxy: StreamProxy,
    private val clock: DownloadClock,
    private val platform: DownloadPlatformComponents,
    private val scope: CoroutineScope,
) : DownloadManager {
    private val ioDispatcher: CoroutineDispatcher =
        requireNotNull(
            scope.coroutineContext[kotlin.coroutines.ContinuationInterceptor] as? CoroutineDispatcher,
        ) { "Scope must contain a CoroutineDispatcher" }

    @Inject
    constructor(
        repository: DownloadRepository,
        prefsRepository: DownloadPrefsRepository,
        streamProxy: StreamProxy,
        clock: DownloadClock,
        platform: DownloadPlatformComponents,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ) : this(
        repository = repository,
        prefsRepository = prefsRepository,
        streamProxy = streamProxy,
        clock = clock,
        platform = platform,
        scope = CoroutineScope(ioDispatcher + kotlinx.coroutines.SupervisorJob()),
    )

    private val _live = MutableStateFlow<Map<String, DownloadLive>>(emptyMap())
    private val _effects = MutableSharedFlow<DownloadEffect>(replay = 16, extraBufferCapacity = 16)
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val pendingResumes = ConcurrentHashMap.newKeySet<String>()
    private val speedMeters = ConcurrentHashMap<String, SpeedMeter>()
    private val lastPersistTimes = ConcurrentHashMap<String, Long>()
    private val lastPersistBytes = ConcurrentHashMap<String, Long>()
    private val lastProgressTimes = ConcurrentHashMap<String, Long>()
    private val lastProgressBytes = ConcurrentHashMap<String, Long>()
    private val schedulerMutex = Mutex()

    override val items: Flow<List<DownloadItem>> = repository.observeAll()
    override val live: StateFlow<Map<String, DownloadLive>> = _live.asStateFlow()
    override val effects: Flow<DownloadEffect> = _effects.asSharedFlow()

    init {
        scope.launch(ioDispatcher) {
            platform.network.status.collect { triggerScheduler() }
        }
        scope.launch(ioDispatcher) {
            prefsRepository.wifiOnly.collect { triggerScheduler() }
        }
        scope.launch(ioDispatcher) {
            prefsRepository.maxConcurrent.collect { triggerScheduler() }
        }
    }

    override suspend fun enqueue(request: DownloadRequest): EnqueueResult {
        val validationFailure = validateRequest(request)
        if (validationFailure != null) return EnqueueResult.Rejected(validationFailure)

        val httpSource = request.source as DownloadSource.Http
        val allItems = repository.getAll()
        val duplicate = findDuplicate(allItems, request.display.metaId, request.display.videoId)
        return if (duplicate != null) {
            EnqueueResult.Duplicate(duplicate.id, duplicate.state)
        } else {
            insertAndScheduleNewItem(request, httpSource)
        }
    }

    private suspend fun insertAndScheduleNewItem(
        request: DownloadRequest,
        httpSource: DownloadSource.Http,
    ): EnqueueResult {
        val id = UUID.randomUUID().toString()
        val ext = downloadFileExtension(request.display.fileNameHint, httpSource.url)
        val now = clock.now()
        val item = createEnqueuedItem(id, request, ext, now)
        repository.insert(item)

        try {
            platform.serviceController.ensureRunning()
        } catch (_: Exception) {
            // Refused foreground service start is swallowed
        }

        triggerScheduler()
        return EnqueueResult.Added(id)
    }

    override fun pause(id: String) {
        scope.launch(ioDispatcher) {
            cancelActiveJob(id)
            val item = repository.get(id)
            if (item != null && !item.supportsResume) {
                platform.fileStore.deletePart(id)
                repository.updateProgress(id, 0L, item.totalBytes, DownloadState.PAUSED, clock.now())
            } else {
                repository.updateState(id, DownloadState.PAUSED, clock.now(), pauseReason = PauseReason.USER)
            }
            triggerScheduler()
        }
    }

    override fun resume(id: String) {
        if (!pendingResumes.add(id)) return
        scope.launch(ioDispatcher) {
            try {
                if (activeJobs.containsKey(id)) return@launch
                val item = repository.get(id) ?: return@launch
                if (item.state == DownloadState.DOWNLOADING || item.state == DownloadState.QUEUED) return@launch
                repository.updateState(id, DownloadState.QUEUED, clock.now(), pauseReason = null, failure = null)
                triggerScheduler()
            } finally {
                pendingResumes.remove(id)
            }
        }
    }

    override fun pauseAll() {
        scope.launch(ioDispatcher) {
            val all = repository.getAll()
            val now = clock.now()
            for (item in all) {
                if (isPausableState(item.state)) {
                    cancelActiveJob(item.id)
                    if (!item.supportsResume) {
                        platform.fileStore.deletePart(item.id)
                        repository.updateProgress(item.id, 0L, item.totalBytes, DownloadState.PAUSED, now)
                    } else {
                        repository.updateState(item.id, DownloadState.PAUSED, now, pauseReason = PauseReason.USER)
                    }
                }
            }
            triggerScheduler()
        }
    }

    override fun resumeAll() {
        scope.launch(ioDispatcher) {
            val all = repository.getAll()
            val now = clock.now()
            for (item in all) {
                if (item.state == DownloadState.PAUSED && isResumableReason(item.pauseReason)) {
                    repository.updateState(item.id, DownloadState.QUEUED, now, pauseReason = null, failure = null)
                }
            }
            triggerScheduler()
        }
    }

    override fun allowMobileData(id: String?) {
        scope.launch(ioDispatcher) {
            val now = clock.now()
            if (id != null) {
                repository.setAllowMetered(id, true, now)
            } else {
                repository.getAll().filter { it.state == DownloadState.WAITING_FOR_WIFI }.forEach {
                    repository.setAllowMetered(it.id, true, now)
                }
            }
            triggerScheduler()
        }
    }

    override suspend fun cancel(id: String) {
        cancelActiveJob(id)
        platform.fileStore.deletePart(id)
        repository.delete(id)
        triggerScheduler()
    }

    override suspend fun delete(id: String) {
        cancelActiveJob(id)
        platform.fileStore.delete(id)
        repository.delete(id)
        triggerScheduler()
    }

    override suspend fun deleteCompleted() {
        val completed = repository.getAll().filter { it.state == DownloadState.COMPLETED }
        for (item in completed) {
            platform.fileStore.delete(item.id)
            repository.delete(item.id)
        }
        triggerScheduler()
    }

    override suspend fun replaceSource(
        id: String,
        source: DownloadSource,
    ) {
        val item = repository.get(id) ?: return
        if (item.state != DownloadState.FAILED || item.source !is DownloadSource.Http ||
            source !is DownloadSource.Http
        ) {
            return
        }
        repository.updateSource(id, source, DownloadState.QUEUED, clock.now())
        triggerScheduler()
    }

    override suspend fun onAppStart() {
        try {
            reconcileAtStart()
            triggerScheduler()
        } catch (_: Exception) {
            // onAppStart never throws
        }
    }

    override suspend fun onServiceTimeout() {
        val all = repository.getAll()
        val now = clock.now()
        for (item in all) {
            if (activeJobs.containsKey(item.id) || item.state == DownloadState.DOWNLOADING) {
                cancelActiveJob(item.id)
                repository.updateState(item.id, DownloadState.PAUSED, now, pauseReason = PauseReason.SYSTEM_TIME_LIMIT)
            }
        }
        _effects.emit(DownloadEffect.PausedBySystem)
        platform.serviceController.stop()
    }

    override fun fileOf(id: String): File? = platform.fileStore.fileOf(id)

    override suspend fun verifyCompleted(id: String): Boolean {
        val item = repository.get(id)
        val isCompleted = item?.state == DownloadState.COMPLETED
        val exists = isCompleted && platform.fileStore.fileOf(id)?.exists() == true
        if (isCompleted && !exists) {
            repository.updateState(id, DownloadState.FAILED, clock.now(), failure = DownloadFailure.FILE_MISSING)
        }
        return exists
    }

    private fun triggerScheduler() {
        scope.launch(ioDispatcher) {
            schedulerMutex.withLock {
                scheduleDownloads()
            }
        }
    }

    private suspend fun scheduleDownloads() {
        val allItems = repository.getAll()
        val netStatus = platform.network.status.value
        val wifiOnly = prefsRepository.wifiOnly.first()
        val maxConcurrent = prefsRepository.maxConcurrent.first()

        checkRunningGates(allItems, netStatus, wifiOnly)
        checkWaitingGates(allItems, netStatus, wifiOnly)
        checkQueuedGates(allItems, netStatus, wifiOnly)

        val updatedItems = repository.getAll()
        val toStartIds =
            pickToStart(updatedItems, maxConcurrent) { item ->
                networkGate(netStatus, wifiOnly, item.allowMetered)
            }
        for (id in toStartIds) {
            val item = updatedItems.firstOrNull { it.id == id } ?: continue
            startDownloadJob(item)
        }

        updateServiceState(repository.getAll())
    }

    private suspend fun checkRunningGates(
        items: List<DownloadItem>,
        net: NetworkStatus,
        wifiOnly: Boolean,
    ) {
        for (item in items) {
            if (activeJobs.containsKey(item.id)) {
                val gate = networkGate(net, wifiOnly, item.allowMetered)
                if (gate == NetworkGate.WaitForWifi) {
                    cancelActiveJob(item.id)
                    repository.updateState(item.id, DownloadState.WAITING_FOR_WIFI, clock.now())
                } else if (gate == NetworkGate.WaitForNetwork) {
                    cancelActiveJob(item.id)
                    repository.updateState(item.id, DownloadState.WAITING_FOR_NETWORK, clock.now())
                }
            }
        }
    }

    private suspend fun checkWaitingGates(
        items: List<DownloadItem>,
        net: NetworkStatus,
        wifiOnly: Boolean,
    ) {
        for (item in items) {
            if (item.state == DownloadState.WAITING_FOR_WIFI || item.state == DownloadState.WAITING_FOR_NETWORK) {
                val gate = networkGate(net, wifiOnly, item.allowMetered)
                if (gate == NetworkGate.Proceed) {
                    repository.updateState(item.id, DownloadState.QUEUED, clock.now())
                }
            }
        }
    }

    private suspend fun checkQueuedGates(
        items: List<DownloadItem>,
        net: NetworkStatus,
        wifiOnly: Boolean,
    ) {
        for (item in items) {
            if (item.state == DownloadState.QUEUED) {
                val gate = networkGate(net, wifiOnly, item.allowMetered)
                if (gate == NetworkGate.WaitForWifi) {
                    repository.updateState(item.id, DownloadState.WAITING_FOR_WIFI, clock.now())
                } else if (gate == NetworkGate.WaitForNetwork) {
                    repository.updateState(item.id, DownloadState.WAITING_FOR_NETWORK, clock.now())
                }
            }
        }
    }

    private fun startDownloadJob(item: DownloadItem) {
        if (activeJobs.containsKey(item.id)) return

        val job =
            scope.launch(ioDispatcher) {
                executeDownload(item)
            }
        activeJobs[item.id] = job
        job.invokeOnCompletion {
            activeJobs.remove(item.id)
            speedMeters.remove(item.id)
            _live.update { current -> current - item.id }
            triggerScheduler()
        }
    }

    private suspend fun executeDownload(item: DownloadItem) {
        val available = platform.storageInfo.availableBytes(platform.fileStore.root)
        val remaining = (item.totalBytes - item.downloadedBytes).coerceAtLeast(0L)
        if (!hasEnoughStorage(available, remaining)) {
            repository.updateState(
                item.id,
                DownloadState.FAILED,
                clock.now(),
                failure = DownloadFailure.INSUFFICIENT_STORAGE,
            )
            _effects.emit(DownloadEffect.Failed(item.id, DownloadFailure.INSUFFICIENT_STORAGE))
            return
        }

        repository.updateState(item.id, DownloadState.DOWNLOADING, clock.now())
        val partFile = platform.fileStore.partFile(item.id)
        val offset = if (partFile.exists()) partFile.length() else 0L

        val httpSource = item.source as? DownloadSource.Http ?: return
        val session =
            if (httpSource.headers.isNotEmpty()) {
                streamProxy.open(
                    httpSource.url,
                    httpSource.headers,
                )
            } else {
                null
            }
        val targetUrl = session?.url ?: httpSource.url

        try {
            val task =
                HttpTask(
                    url = targetUrl,
                    headers = httpSource.headers,
                    partFile = partFile,
                    offset = offset,
                    etag = item.etag,
                    lastModified = item.lastModified,
                    supportsResumeKnown = item.supportsResume,
                )
            val result =
                platform.downloader.download(task) { bytes, total ->
                    onProgressUpdate(item.id, bytes, total)
                }
            handleHttpResult(item, result, httpSource.url)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            repository.updateState(item.id, DownloadState.FAILED, clock.now(), failure = DownloadFailure.UNKNOWN)
        } finally {
            session?.close()
        }
    }

    private suspend fun handleHttpResult(
        item: DownloadItem,
        result: HttpResult,
        url: String,
    ) {
        when (result) {
            is HttpResult.Completed -> {
                handleDownloadCompleted(item, result, url)
            }

            is HttpResult.Failed -> {
                handleDownloadFailed(item, result)
            }

            is HttpResult.Cancelled -> {}
        }
    }

    private suspend fun handleDownloadCompleted(
        item: DownloadItem,
        result: HttpResult.Completed,
        url: String,
    ) {
        val ext = downloadFileExtension(item.fileNameHint, url)
        val finished = platform.fileStore.complete(item.id, ext)
        val now = clock.now()
        repository.updateCompleted(item.id, finished.absolutePath, result.total, now, now)
        _effects.emit(DownloadEffect.Completed(item.id))
    }

    private suspend fun handleDownloadFailed(
        item: DownloadItem,
        result: HttpResult.Failed,
    ) {
        val net = platform.network.status.value
        if (net == NetworkStatus.NONE && result.failure == DownloadFailure.NETWORK) {
            repository.updateState(item.id, DownloadState.WAITING_FOR_NETWORK, clock.now())
        } else {
            if (result.failure == DownloadFailure.NOT_A_VIDEO || result.failure == DownloadFailure.UNSUPPORTED_SOURCE) {
                platform.fileStore.deletePart(item.id)
            }
            repository.updateState(item.id, DownloadState.FAILED, clock.now(), failure = result.failure)
            _effects.emit(DownloadEffect.Failed(item.id, result.failure))
        }
    }

    private fun onProgressUpdate(
        id: String,
        bytes: Long,
        total: Long,
    ) {
        val now = clock.now()
        val meter = speedMeters.getOrPut(id) { SpeedMeter() }
        val prevTime = lastProgressTimes[id] ?: now
        val prevBytes = lastProgressBytes[id] ?: bytes
        val dt = (now - prevTime).coerceAtLeast(1L)
        val sampleBps = ((bytes - prevBytes) * 1000L / dt).coerceAtLeast(0L)
        val speed = meter.addSample(sampleBps)
        lastProgressTimes[id] = now
        lastProgressBytes[id] = bytes

        val eta = etaSeconds((total - bytes).coerceAtLeast(0L), speed)
        _live.update { current -> current + (id to DownloadLive(bytes, speed, eta)) }

        val lastTime = lastPersistTimes[id] ?: 0L
        val lastBytes = lastPersistBytes[id] ?: 0L
        val timeDue = now - lastTime >= PROGRESS_PERSIST_INTERVAL_MS
        val pctDue = total <= 0L || ((bytes - lastBytes) * PERCENT_MULTIPLIER / total) >= PROGRESS_PERSIST_PERCENT

        if (timeDue || pctDue) {
            lastPersistTimes[id] = now
            lastPersistBytes[id] = bytes
            scope.launch(ioDispatcher) {
                repository.updateProgress(id, bytes, total, DownloadState.DOWNLOADING, now)
            }
        }
    }

    private suspend fun reconcileAtStart() {
        val all = repository.getAll()
        val now = clock.now()
        val validIds = all.map { it.id }.toSet()

        for (item in all) {
            if (item.state == DownloadState.DOWNLOADING) {
                val partLen = platform.fileStore.partFile(item.id).length()
                repository.updateProgress(item.id, partLen, item.totalBytes, DownloadState.QUEUED, now)
            } else if (item.state == DownloadState.COMPLETED) {
                val file = platform.fileStore.fileOf(item.id)
                if (file == null || !file.exists()) {
                    repository.updateState(item.id, DownloadState.FAILED, now, failure = DownloadFailure.FILE_MISSING)
                }
            }
        }

        val allFiles = platform.fileStore.listAllFiles()
        for (file in allFiles) {
            val ownsFile = validIds.any { id -> file.name.startsWith("$id.") }
            if (!ownsFile) {
                file.delete()
            }
        }
    }

    private fun cancelActiveJob(id: String) {
        activeJobs.remove(id)?.cancel()
        speedMeters.remove(id)
        lastProgressTimes.remove(id)
        lastProgressBytes.remove(id)
        lastPersistTimes.remove(id)
        lastPersistBytes.remove(id)
        _live.update { current -> current - id }
    }

    private fun updateServiceState(items: List<DownloadItem>) {
        val hasActive =
            items.any {
                it.state == DownloadState.DOWNLOADING ||
                    it.state == DownloadState.QUEUED ||
                    it.state == DownloadState.WAITING_FOR_WIFI ||
                    it.state == DownloadState.WAITING_FOR_NETWORK
            }
        if (hasActive) {
            try {
                platform.serviceController.ensureRunning()
            } catch (_: Exception) {
            }
        } else {
            platform.serviceController.stop()
        }
    }
}

private fun validateRequest(request: DownloadRequest): DownloadFailure? {
    if (request.source !is DownloadSource.Http) return DownloadFailure.UNSUPPORTED_SOURCE
    val url = request.source.url.trim()
    val isHttp = url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)
    return if (url.isBlank() || !isHttp) DownloadFailure.UNSUPPORTED_SOURCE else null
}

private fun createEnqueuedItem(
    id: String,
    request: DownloadRequest,
    ext: String,
    now: Long,
) = DownloadItem(
    id = id,
    kind = DownloadKind.HTTP,
    source = request.source,
    metaId = request.display.metaId,
    type = request.display.type,
    title = request.display.title,
    metaName = request.display.metaName,
    posterUrl = request.display.posterUrl,
    videoId = request.display.videoId,
    season = request.display.season,
    episode = request.display.episode,
    fileNameHint = request.display.fileNameHint,
    fileName = "$id.$ext",
    totalBytes = request.sizeHint ?: 0L,
    downloadedBytes = 0L,
    state = DownloadState.QUEUED,
    allowMetered = false,
    createdAt = now,
    updatedAt = now,
)

private fun isPausableState(state: DownloadState): Boolean =
    state == DownloadState.DOWNLOADING ||
        state == DownloadState.QUEUED ||
        state == DownloadState.WAITING_FOR_WIFI ||
        state == DownloadState.WAITING_FOR_NETWORK

private fun isResumableReason(reason: PauseReason?): Boolean =
    reason == PauseReason.USER || reason == PauseReason.SYSTEM_TIME_LIMIT
