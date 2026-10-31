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
import com.nexus.stream.domain.model.GalleryFailure
import com.nexus.stream.domain.model.GalleryState
import com.nexus.stream.domain.model.NetworkGate
import com.nexus.stream.domain.model.PauseReason
import com.nexus.stream.domain.repository.DownloadClock
import com.nexus.stream.domain.repository.DownloadManager
import com.nexus.stream.domain.repository.DownloadPlatformComponents
import com.nexus.stream.domain.repository.DownloadPrefsRepository
import com.nexus.stream.domain.repository.DownloadRepository
import com.nexus.stream.domain.repository.GalleryExportResult
import com.nexus.stream.domain.repository.GalleryExporter
import com.nexus.stream.domain.repository.HttpResult
import com.nexus.stream.domain.repository.HttpTask
import com.nexus.stream.domain.repository.StreamProxy
import com.nexus.stream.domain.repository.TorrentDownloader
import com.nexus.stream.domain.repository.TorrentFileChoice
import com.nexus.stream.domain.repository.TorrentResult
import com.nexus.stream.domain.repository.TorrentTask
import com.nexus.stream.domain.repository.TorrentUpdate
import com.nexus.stream.domain.usecase.SpeedMeter
import com.nexus.stream.domain.usecase.TORRENT_SUBDIR
import com.nexus.stream.domain.usecase.downloadFileExtension
import com.nexus.stream.domain.usecase.etaSeconds
import com.nexus.stream.domain.usecase.findDuplicate
import com.nexus.stream.domain.usecase.galleryFileName
import com.nexus.stream.domain.usecase.galleryMimeType
import com.nexus.stream.domain.usecase.hasEnoughStorage
import com.nexus.stream.domain.usecase.hasEnoughStorageForExport
import com.nexus.stream.domain.usecase.networkGate
import com.nexus.stream.domain.usecase.pickToStart
import com.nexus.stream.domain.usecase.torrentSaveDir
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

internal const val PROGRESS_PERSIST_INTERVAL_MS = 2000L
internal const val PROGRESS_PERSIST_PERCENT = 1L
internal const val PERCENT_MULTIPLIER = 100L

@Suppress("TooManyFunctions", "LongParameterList")
@Singleton
class DefaultDownloadManager(
    internal val repository: DownloadRepository,
    internal val prefsRepository: DownloadPrefsRepository,
    internal val streamProxy: StreamProxy,
    internal val clock: DownloadClock,
    internal val platform: DownloadPlatformComponents,
    internal val galleryExporter: GalleryExporter,
    internal val scope: CoroutineScope,
) : DownloadManager {
    internal val torrentDownloader: TorrentDownloader get() = platform.torrentDownloader

    internal val ioDispatcher: CoroutineDispatcher =
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
        galleryExporter: GalleryExporter,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ) : this(
        repository = repository,
        prefsRepository = prefsRepository,
        streamProxy = streamProxy,
        clock = clock,
        platform = platform,
        galleryExporter = galleryExporter,
        scope = CoroutineScope(ioDispatcher + kotlinx.coroutines.SupervisorJob()),
    )

    private val _live = MutableStateFlow<Map<String, DownloadLive>>(emptyMap())
    private val _effects = MutableSharedFlow<DownloadEffect>(replay = 16, extraBufferCapacity = 16)

    internal fun updateLive(transform: (Map<String, DownloadLive>) -> Map<String, DownloadLive>) {
        _live.update(transform)
    }

    internal suspend fun emitEffect(effect: DownloadEffect) {
        _effects.emit(effect)
    }

    internal val activeJobs = ConcurrentHashMap<String, Job>()
    internal val fileChoices = ConcurrentHashMap<String, List<TorrentFileChoice>>()
    private val pendingResumes = ConcurrentHashMap.newKeySet<String>()
    internal val speedMeters = ConcurrentHashMap<String, SpeedMeter>()
    internal val lastPersistTimes = ConcurrentHashMap<String, Long>()
    internal val lastPersistBytes = ConcurrentHashMap<String, Long>()
    internal val lastProgressTimes = ConcurrentHashMap<String, Long>()
    internal val lastProgressBytes = ConcurrentHashMap<String, Long>()
    private val schedulerMutex = Mutex()
    private val galleryMutex = Mutex()

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

        val allItems = repository.getAll()
        val duplicate = findDuplicate(allItems, request.display.metaId, request.display.videoId)
        return if (duplicate != null) {
            EnqueueResult.Duplicate(duplicate.id, duplicate.state)
        } else {
            insertAndScheduleNewItem(request)
        }
    }

    private suspend fun insertAndScheduleNewItem(request: DownloadRequest): EnqueueResult {
        val id = UUID.randomUUID().toString()
        val now = clock.now()
        val item = createEnqueuedItem(id, request, now)
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
                val pausable =
                    item.state == DownloadState.DOWNLOADING ||
                        item.state == DownloadState.QUEUED ||
                        item.state == DownloadState.WAITING_FOR_WIFI ||
                        item.state == DownloadState.WAITING_FOR_NETWORK
                if (pausable) {
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
                val resumable =
                    item.pauseReason == PauseReason.USER ||
                        item.pauseReason == PauseReason.SYSTEM_TIME_LIMIT
                if (item.state == DownloadState.PAUSED && resumable) {
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
        val item = repository.get(id)
        if (item != null && item.kind == DownloadKind.TORRENT) {
            val torrentFolder = File(File(platform.fileStore.root, TORRENT_SUBDIR), id)
            torrentFolder.deleteRecursively()
        } else {
            platform.fileStore.deletePart(id)
        }
        fileChoices.remove(id)
        repository.delete(id)
        triggerScheduler()
    }

    override suspend fun delete(id: String) {
        cancelActiveJob(id)
        val item = repository.get(id)
        if (item?.galleryUri != null) {
            galleryExporter.delete(item.galleryUri)
        }
        if (item != null && item.kind == DownloadKind.TORRENT) {
            val torrentFolder = File(File(platform.fileStore.root, TORRENT_SUBDIR), id)
            torrentFolder.deleteRecursively()
        } else {
            platform.fileStore.delete(id)
        }
        fileChoices.remove(id)
        repository.delete(id)
        triggerScheduler()
    }

    override suspend fun deleteCompleted() {
        val completed = repository.getAll().filter { it.state == DownloadState.COMPLETED }
        for (item in completed) {
            if (item.galleryUri != null) {
                galleryExporter.delete(item.galleryUri)
            }
            if (item.kind == DownloadKind.TORRENT) {
                val torrentFolder = File(File(platform.fileStore.root, TORRENT_SUBDIR), item.id)
                torrentFolder.deleteRecursively()
            } else {
                platform.fileStore.delete(item.id)
            }
            fileChoices.remove(item.id)
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
            galleryExporter.cleanupPending()
            val all = repository.getAll()
            val saveOn = prefsRepository.saveToGallery.first()
            val now = clock.now()

            for (item in all) {
                if (item.galleryState == GalleryState.SAVING) {
                    repository.insert(
                        item.copy(
                            galleryState = GalleryState.NONE,
                            galleryUri = null,
                            galleryFailure = null,
                            updatedAt = now,
                        ),
                    )
                    if (saveOn) {
                        exportToGallery(item.id)
                    }
                } else if (item.galleryState == GalleryState.SAVED) {
                    val uri = item.galleryUri
                    if (uri == null || !galleryExporter.exists(uri)) {
                        repository.insert(
                            item.copy(
                                galleryState = GalleryState.NONE,
                                galleryUri = null,
                                galleryFailure = null,
                                updatedAt = now,
                            ),
                        )
                    }
                }
            }
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

    override suspend fun exportToGallery(id: String) {
        val preItem = repository.get(id) ?: return
        if (preItem.galleryState == GalleryState.SAVING || preItem.galleryState == GalleryState.SAVED) return
        galleryMutex.withLock {
            val item = repository.get(id) ?: return@withLock
            if (item.galleryState == GalleryState.SAVING || item.galleryState == GalleryState.SAVED) return@withLock
            if (item.state != DownloadState.COMPLETED) return@withLock

            val file = resolveExportFile(item) ?: return@withLock
            if (!checkExportStorage(item, file)) return@withLock

            repository.insert(
                item.copy(
                    galleryState = GalleryState.SAVING,
                    galleryUri = null,
                    galleryFailure = null,
                    updatedAt = clock.now(),
                ),
            )
            updateServiceState(repository.getAll())

            try {
                runGalleryExport(item, file)
            } catch (e: CancellationException) {
                repository.insert(
                    item.copy(
                        galleryState = GalleryState.NONE,
                        galleryUri = null,
                        galleryFailure = null,
                        updatedAt = clock.now(),
                    ),
                )
                throw e
            } catch (_: Exception) {
                repository.insert(
                    item.copy(
                        galleryState = GalleryState.FAILED,
                        galleryUri = null,
                        galleryFailure = GalleryFailure.WRITE_FAILED,
                        updatedAt = clock.now(),
                    ),
                )
                emitEffect(DownloadEffect.GalleryFailed(item.id, GalleryFailure.WRITE_FAILED))
            } finally {
                updateLive { current -> current - item.id }
                updateServiceState(repository.getAll())
            }
        }
    }

    private suspend fun resolveExportFile(item: DownloadItem): File? {
        val file =
            if (item.kind == DownloadKind.TORRENT) {
                item.filePath?.let { File(it) }
            } else {
                platform.fileStore.fileOf(item.id)
            }
        if (file == null || !file.exists()) {
            repository.insert(
                item.copy(
                    galleryState = GalleryState.FAILED,
                    galleryUri = null,
                    galleryFailure = GalleryFailure.FILE_MISSING,
                    updatedAt = clock.now(),
                ),
            )
            emitEffect(DownloadEffect.GalleryFailed(item.id, GalleryFailure.FILE_MISSING))
            return null
        }
        return file
    }

    private suspend fun checkExportStorage(
        item: DownloadItem,
        file: File,
    ): Boolean {
        val available = platform.storageInfo.availableBytes(platform.fileStore.root)
        val fileSize = file.length()
        if (!hasEnoughStorageForExport(available, fileSize)) {
            repository.insert(
                item.copy(
                    galleryState = GalleryState.FAILED,
                    galleryUri = null,
                    galleryFailure = GalleryFailure.NO_SPACE,
                    updatedAt = clock.now(),
                ),
            )
            emitEffect(DownloadEffect.GalleryFailed(item.id, GalleryFailure.NO_SPACE))
            return false
        }
        return true
    }

    private suspend fun runGalleryExport(
        item: DownloadItem,
        file: File,
    ) {
        val fileName =
            galleryFileName(
                title = item.title,
                metaName = item.metaName,
                season = item.season,
                episode = item.episode,
                extension = file.extension,
            )
        val mimeType = galleryMimeType(file.extension)
        val result =
            galleryExporter.export(file, fileName, mimeType) { copied, _ ->
                updateLive { current ->
                    val cur = current[item.id] ?: DownloadLive()
                    current + (item.id to cur.copy(copiedBytes = copied))
                }
            }
        when (result) {
            is GalleryExportResult.Saved -> {
                repository.insert(
                    item.copy(
                        galleryState = GalleryState.SAVED,
                        galleryUri = result.uri,
                        galleryFailure = null,
                        updatedAt = clock.now(),
                    ),
                )
            }

            is GalleryExportResult.Failed -> {
                repository.insert(
                    item.copy(
                        galleryState = GalleryState.FAILED,
                        galleryUri = null,
                        galleryFailure = result.failure,
                        updatedAt = clock.now(),
                    ),
                )
                emitEffect(DownloadEffect.GalleryFailed(item.id, result.failure))
            }
        }
    }

    override suspend fun removeFromGallery(id: String) {
        val item = repository.get(id) ?: return
        val uri = item.galleryUri
        if (uri != null) {
            galleryExporter.delete(uri)
        }
        repository.insert(
            item.copy(
                galleryState = GalleryState.NONE,
                galleryUri = null,
                galleryFailure = null,
                updatedAt = clock.now(),
            ),
        )
    }

    override suspend fun exportAllToGallery() {
        val completed =
            repository.getAll().filter {
                it.state == DownloadState.COMPLETED &&
                    (it.galleryState == GalleryState.NONE || it.galleryState == GalleryState.FAILED)
            }
        for (item in completed) {
            exportToGallery(item.id)
        }
    }

    internal fun triggerScheduler() {
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

    override suspend fun selectTorrentFile(
        id: String,
        fileIndex: Int,
    ) {
        fileChoices.remove(id)
        val item = repository.get(id) ?: return
        val torrentSource = item.source as? DownloadSource.Torrent ?: return
        val updatedSource = torrentSource.copy(fileIdx = fileIndex)
        repository.updateSource(id, updatedSource, DownloadState.QUEUED, clock.now())
        triggerScheduler()
    }

    override fun getTorrentFileChoices(id: String): List<TorrentFileChoice> = fileChoices[id].orEmpty()
}

internal suspend fun DefaultDownloadManager.reconcileAtStart() {
    val all = repository.getAll()
    val now = clock.now()
    val validIds = all.map { it.id }.toSet()

    for (item in all) {
        reconcileItem(item, now)
    }
    cleanupOrphanFiles(validIds)
    cleanupOrphanTorrentFolders(validIds)
}

private suspend fun DefaultDownloadManager.reconcileItem(
    item: DownloadItem,
    now: Long,
) {
    when (item.state) {
        DownloadState.DOWNLOADING -> {
            reconcileDownloadingItem(item, now)
        }

        DownloadState.COMPLETED -> {
            reconcileCompletedItem(item, now)
        }

        else -> {}
    }
}

private suspend fun DefaultDownloadManager.reconcileDownloadingItem(
    item: DownloadItem,
    now: Long,
) {
    if (item.kind == DownloadKind.TORRENT) {
        repository.updateState(item.id, DownloadState.QUEUED, now)
    } else {
        val partLen = platform.fileStore.partFile(item.id).length()
        repository.updateProgress(item.id, partLen, item.totalBytes, DownloadState.QUEUED, now)
    }
}

private suspend fun DefaultDownloadManager.reconcileCompletedItem(
    item: DownloadItem,
    now: Long,
) {
    val file =
        if (item.kind == DownloadKind.TORRENT) {
            item.filePath?.let { File(it) }
        } else {
            platform.fileStore.fileOf(item.id)
        }
    if (file == null || !file.exists()) {
        repository.updateState(item.id, DownloadState.FAILED, now, failure = DownloadFailure.FILE_MISSING)
    }
}

private fun DefaultDownloadManager.cleanupOrphanFiles(validIds: Set<String>) {
    val allFiles = platform.fileStore.listAllFiles()
    for (file in allFiles) {
        val ownsFile = validIds.any { id -> file.name.startsWith("$id.") }
        if (!ownsFile) {
            file.delete()
        }
    }
}

private fun DefaultDownloadManager.cleanupOrphanTorrentFolders(validIds: Set<String>) {
    val torrentsRoot = File(platform.fileStore.root, TORRENT_SUBDIR)
    if (torrentsRoot.isDirectory) {
        val folders = torrentsRoot.listFiles { f -> f.isDirectory } ?: emptyArray()
        for (folder in folders) {
            if (folder.name !in validIds) {
                folder.deleteRecursively()
            }
        }
    }
}

internal fun DefaultDownloadManager.cancelActiveJob(id: String) {
    activeJobs.remove(id)?.cancel()
    speedMeters.remove(id)
    lastProgressTimes.remove(id)
    lastProgressBytes.remove(id)
    lastPersistTimes.remove(id)
    lastPersistBytes.remove(id)
    updateLive { current -> current - id }
}

internal fun DefaultDownloadManager.updateServiceState(items: List<DownloadItem>) {
    val hasActive =
        items.any {
            it.state == DownloadState.DOWNLOADING ||
                it.state == DownloadState.QUEUED ||
                it.state == DownloadState.WAITING_FOR_WIFI ||
                it.state == DownloadState.WAITING_FOR_NETWORK ||
                it.galleryState == GalleryState.SAVING
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

private val HEX_40_REGEX = Regex("^[0-9a-fA-F]{40}$")
private val BASE32_32_REGEX = Regex("^[2-7a-zA-Z]{32}$")

private fun validateRequest(request: DownloadRequest): DownloadFailure? =
    when (val source = request.source) {
        is DownloadSource.Http -> {
            val url = source.url.trim()
            val isHttp = url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)
            if (url.isBlank() || !isHttp) DownloadFailure.UNSUPPORTED_SOURCE else null
        }

        is DownloadSource.Torrent -> {
            val hash = source.infoHash.trim()
            if (!HEX_40_REGEX.matches(hash) && !BASE32_32_REGEX.matches(hash)) {
                DownloadFailure.UNSUPPORTED_SOURCE
            } else {
                null
            }
        }
    }

private fun createEnqueuedItem(
    id: String,
    request: DownloadRequest,
    now: Long,
): DownloadItem {
    val (kind, fileName) =
        when (val source = request.source) {
            is DownloadSource.Http -> {
                val ext = downloadFileExtension(request.display.fileNameHint, source.url)
                DownloadKind.HTTP to "$id.$ext"
            }

            is DownloadSource.Torrent -> {
                DownloadKind.TORRENT to (request.display.fileNameHint ?: "$id.mkv")
            }
        }
    return DownloadItem(
        id = id,
        kind = kind,
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
        fileName = fileName,
        totalBytes = request.sizeHint ?: 0L,
        downloadedBytes = 0L,
        state = DownloadState.QUEUED,
        allowMetered = false,
        createdAt = now,
        updatedAt = now,
    )
}
