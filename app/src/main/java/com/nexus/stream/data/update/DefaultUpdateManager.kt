package com.nexus.stream.data.update

import android.content.Context
import android.content.Intent
import com.nexus.stream.di.IoDispatcher
import com.nexus.stream.domain.model.Decision
import com.nexus.stream.domain.model.UpdateCandidate
import com.nexus.stream.domain.model.UpdateEffect
import com.nexus.stream.domain.model.UpdateFailure
import com.nexus.stream.domain.model.UpdateState
import com.nexus.stream.domain.repository.ApkDownloader
import com.nexus.stream.domain.repository.ApkFileStore
import com.nexus.stream.domain.repository.ApkInstaller
import com.nexus.stream.domain.repository.AppInfoProvider
import com.nexus.stream.domain.repository.Clock
import com.nexus.stream.domain.repository.DownloadResult
import com.nexus.stream.domain.repository.NetworkInfo
import com.nexus.stream.domain.repository.PackageArchiveReader
import com.nexus.stream.domain.repository.ReleaseResult
import com.nexus.stream.domain.repository.ReleaseSource
import com.nexus.stream.domain.repository.StorageInfo
import com.nexus.stream.domain.repository.UpdateManager
import com.nexus.stream.domain.repository.UpdatePrefsRepository
import com.nexus.stream.domain.usecase.canSelfUpdate
import com.nexus.stream.domain.usecase.decideUpdate
import com.nexus.stream.domain.usecase.hasEnoughSpace
import com.nexus.stream.domain.usecase.verifyApk
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

const val AUTO_CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000L
private const val BUFFER_SIZE = 8192
private const val EFFECTS_BUFFER = 16

@Suppress("LongParameterList", "TooManyFunctions")
@Singleton
class DefaultUpdateManager(
    private val releaseSource: ReleaseSource,
    private val downloader: ApkDownloader,
    private val fileStore: ApkFileStore,
    private val archiveReader: PackageArchiveReader,
    private val installer: ApkInstaller,
    private val networkInfo: NetworkInfo,
    private val storageInfo: StorageInfo,
    private val prefs: UpdatePrefsRepository,
    private val appInfoProvider: AppInfoProvider,
    private val clock: Clock,
    private val coroutineScope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
    private val intentLauncher: (Any) -> Boolean,
) : UpdateManager {
    @Inject
    constructor(
        releaseSource: ReleaseSource,
        downloader: ApkDownloader,
        fileStore: ApkFileStore,
        archiveReader: PackageArchiveReader,
        installer: ApkInstaller,
        networkInfo: NetworkInfo,
        storageInfo: StorageInfo,
        prefs: UpdatePrefsRepository,
        appInfoProvider: AppInfoProvider,
        clock: Clock,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
        @ApplicationContext context: Context,
    ) : this(
        releaseSource = releaseSource,
        downloader = downloader,
        fileStore = fileStore,
        archiveReader = archiveReader,
        installer = installer,
        networkInfo = networkInfo,
        storageInfo = storageInfo,
        prefs = prefs,
        appInfoProvider = appInfoProvider,
        clock = clock,
        coroutineScope = CoroutineScope(ioDispatcher + SupervisorJob()),
        ioDispatcher = ioDispatcher,
        intentLauncher = { intent ->
            try {
                if (intent is Intent) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    true
                } else {
                    false
                }
            } catch (_: Exception) {
                false
            }
        },
    )

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle())
    override val state: StateFlow<UpdateState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<UpdateEffect>(extraBufferCapacity = EFFECTS_BUFFER)
    override val effects: Flow<UpdateEffect> = _effects.asSharedFlow()

    private var checkJob: Job? = null
    private var downloadJob: Job? = null
    private var activeCandidate: UpdateCandidate? = null
    private var downloadedFile: File? = null
    private var pendingConfirmIntent: Any? = null

    override fun checkNow() {
        val current = _state.value
        if (!isCheckableState(current) || checkJob?.isActive == true || downloadJob?.isActive == true) {
            return
        }

        val installedPkg = archiveReader.getInstalledFacts().packageName
        if (!canSelfUpdate(installedPkg)) {
            _state.value = UpdateState.Unsupported
        } else {
            _state.value = UpdateState.Checking
            checkJob = coroutineScope.launch(ioDispatcher) { runCheck() }
        }
    }

    private suspend fun runCheck() {
        try {
            when (val result = releaseSource.fetchLatestRelease()) {
                is ReleaseResult.Failure -> {
                    _state.value = UpdateState.Failed(result.failure, activeCandidate)
                }

                is ReleaseResult.Success -> {
                    processSuccessfulRelease(result)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            _state.value = UpdateState.Failed(UpdateFailure.BadResponse, activeCandidate)
        }
    }

    private suspend fun processSuccessfulRelease(result: ReleaseResult.Success) {
        val currentVersion = appInfoProvider.versionName ?: "1.0.0"
        when (val decision = decideUpdate(currentVersion, result.release, listOf("arm64-v8a"))) {
            is Decision.UpToDate -> {
                val now = clock.currentTimeMillis()
                prefs.setLastCheckedAt(now)
                _state.value = UpdateState.UpToDate(now)
            }

            is Decision.Available -> {
                activeCandidate = decision.candidate
                _state.value = UpdateState.Available(decision.candidate)
            }

            is Decision.Failure -> {
                _state.value = UpdateState.Failed(decision.failure, activeCandidate)
            }
        }
    }

    override fun confirmDownload(allowMetered: Boolean) {
        if (downloadJob?.isActive == true) return
        val candidate = activeCandidate ?: return

        when {
            networkInfo.isMetered() && !allowMetered -> {
                _state.value = UpdateState.ConfirmMetered(candidate)
            }

            !hasEnoughSpace(storageInfo.availableBytes(), candidate.asset.sizeBytes) -> {
                _state.value = UpdateState.Failed(UpdateFailure.InsufficientStorage, candidate)
            }

            else -> {
                startDownload(candidate)
            }
        }
    }

    private fun startDownload(candidate: UpdateCandidate) {
        _state.value = UpdateState.Downloading(candidate, 0L, candidate.asset.sizeBytes)
        downloadJob =
            coroutineScope.launch(ioDispatcher) {
                try {
                    val result =
                        downloader.download(candidate.asset) { down, total ->
                            _state.value = UpdateState.Downloading(candidate, down, total)
                        }
                    handleDownloadResult(result, candidate)
                } catch (e: CancellationException) {
                    _state.value = UpdateState.Available(candidate)
                    throw e
                } catch (_: Exception) {
                    _state.value = UpdateState.Failed(UpdateFailure.DownloadFailed, candidate)
                }
            }
    }

    private suspend fun handleDownloadResult(
        result: DownloadResult,
        candidate: UpdateCandidate,
    ) {
        when (result) {
            is DownloadResult.Failure -> {
                _state.value = UpdateState.Failed(result.failure, candidate)
            }

            is DownloadResult.Success -> {
                downloadedFile = result.file
                verifyAndProceed(result.file, candidate)
            }
        }
    }

    private suspend fun verifyAndProceed(
        file: File,
        candidate: UpdateCandidate,
    ) {
        _state.value = UpdateState.Verifying(candidate)
        val apkFacts = archiveReader.readArchiveFacts(file)
        val actualSha = computeSha256(file)
        val failure =
            verifyApk(
                apk = apkFacts,
                installed = archiveReader.getInstalledFacts(),
                expectedSize = candidate.asset.sizeBytes,
                actualSize = file.length(),
                expectedSha256 = candidate.asset.sha256,
                actualSha256 = actualSha,
            )

        if (failure != null) {
            file.delete()
            downloadedFile = null
            _state.value = UpdateState.Failed(failure, candidate)
            return
        }

        if (installer.canInstall()) {
            installInternal(file, candidate, apkFacts?.versionCode ?: 0L)
        } else {
            _state.value = UpdateState.NeedsInstallPermission(candidate)
        }
    }

    private suspend fun installInternal(
        file: File,
        candidate: UpdateCandidate,
        versionCode: Long,
    ) {
        _state.value = UpdateState.Installing(candidate)
        prefs.setPendingInstallCode(versionCode)
        try {
            installer.install(file)
        } catch (_: Exception) {
            prefs.clearPendingInstall()
            _state.value = UpdateState.Failed(UpdateFailure.InstallFailed, candidate)
        }
    }

    override fun onInstallPermissionResult() {
        val file = downloadedFile ?: return
        val candidate = activeCandidate ?: return
        if (_state.value is UpdateState.NeedsInstallPermission && installer.canInstall()) {
            coroutineScope.launch(ioDispatcher) {
                val facts = archiveReader.readArchiveFacts(file)
                installInternal(file, candidate, facts?.versionCode ?: 0L)
            }
        }
    }

    override fun install() {
        val file = downloadedFile ?: return
        val candidate = activeCandidate ?: return
        coroutineScope.launch(ioDispatcher) {
            val facts = archiveReader.readArchiveFacts(file)
            installInternal(file, candidate, facts?.versionCode ?: 0L)
        }
    }

    override fun continueConfirmation() {
        val intent = pendingConfirmIntent
        val launched = if (intent != null) intentLauncher(intent) else false
        if (launched) {
            val candidate = activeCandidate ?: return
            _state.value = UpdateState.Installing(candidate)
        }
    }

    override fun cancelDownload() {
        downloadJob?.cancel()
        downloader.cleanup(keep = null)
        val candidate = activeCandidate ?: return
        _state.value = UpdateState.Available(candidate)
    }

    override fun dismissFailure() {
        val candidate = activeCandidate
        if (candidate != null) {
            _state.value = UpdateState.Available(candidate)
        } else {
            coroutineScope.launch(ioDispatcher) {
                val last = prefs.lastCheckedAt.first()
                _state.value = UpdateState.Idle(last)
            }
        }
    }

    override fun openReleasePage() {
        val candidate = activeCandidate ?: return
        coroutineScope.launch(ioDispatcher) {
            _effects.emit(UpdateEffect.OpenUrl(candidate.releaseUrl))
        }
    }

    override fun onInstallResult(
        status: Int,
        confirmIntent: Any?,
    ) {
        when (val mapped = InstallStatusMapper.map(status)) {
            is InstallStatusResult.Success -> {
                // Process ending
            }

            is InstallStatusResult.NeedsConfirmation -> {
                handleNeedsConfirmation(confirmIntent)
            }

            is InstallStatusResult.Cancelled -> {
                coroutineScope.launch(ioDispatcher) { prefs.clearPendingInstall() }
                val candidate = activeCandidate ?: return
                _state.value = UpdateState.Available(candidate)
            }

            is InstallStatusResult.Failure -> {
                coroutineScope.launch(ioDispatcher) { prefs.clearPendingInstall() }
                _state.value = UpdateState.Failed(mapped.failure, activeCandidate)
            }
        }
    }

    private fun handleNeedsConfirmation(confirmIntent: Any?) {
        pendingConfirmIntent = confirmIntent
        val launched = if (confirmIntent != null) intentLauncher(confirmIntent) else false
        if (!launched) {
            val candidate = activeCandidate ?: return
            _state.value = UpdateState.AwaitingConfirmation(candidate)
        }
    }

    override fun autoCheckIfDue() {
        coroutineScope.launch(ioDispatcher) {
            if (!shouldAutoCheck()) return@launch

            val now = clock.currentTimeMillis()
            val prev = _state.value
            try {
                when (val result = releaseSource.fetchLatestRelease()) {
                    is ReleaseResult.Success -> processAutoCheckRelease(result, now, prev)
                    is ReleaseResult.Failure -> _state.value = prev
                }
            } catch (_: Exception) {
                _state.value = prev
            }
        }
    }

    private suspend fun shouldAutoCheck(): Boolean {
        val current = _state.value
        val isAllowedState = current is UpdateState.Idle || current is UpdateState.UpToDate
        val enabled = prefs.autoCheck.first()
        val canUpdate = canSelfUpdate(archiveReader.getInstalledFacts().packageName)
        val lastChecked = prefs.lastCheckedAt.first() ?: 0L
        val intervalElapsed = clock.currentTimeMillis() - lastChecked >= AUTO_CHECK_INTERVAL_MS

        return enabled && canUpdate && isAllowedState && intervalElapsed
    }

    private suspend fun processAutoCheckRelease(
        result: ReleaseResult.Success,
        now: Long,
        fallback: UpdateState,
    ) {
        val currentVersion = appInfoProvider.versionName ?: "1.0.0"
        when (val decision = decideUpdate(currentVersion, result.release, listOf("arm64-v8a"))) {
            is Decision.UpToDate -> {
                prefs.setLastCheckedAt(now)
                _state.value = UpdateState.UpToDate(now)
            }

            is Decision.Available -> {
                activeCandidate = decision.candidate
                _state.value = UpdateState.Available(decision.candidate)
            }

            is Decision.Failure -> {
                _state.value = fallback
            }
        }
    }

    override fun onAppStart() {
        coroutineScope.launch(ioDispatcher) {
            try {
                installer.abandonStaleSessions()
                handlePendingInstallMarker()
            } catch (_: Exception) {
                // Must never throw
            }
        }
    }

    private suspend fun handlePendingInstallMarker() {
        val pending = prefs.pendingInstallCode.first()
        if (pending != null) {
            val installedFacts = archiveReader.getInstalledFacts()
            if (installedFacts.versionCode >= pending) {
                val versionName = appInfoProvider.versionName.orEmpty()
                _effects.emit(UpdateEffect.Updated(versionName))
                prefs.clearPendingInstall()
                fileStore.cleanup(keep = null)
            } else {
                prefs.clearPendingInstall()
            }
        } else {
            fileStore.cleanup(keep = null)
        }
    }
}

private fun isCheckableState(state: UpdateState): Boolean =
    state is UpdateState.Idle ||
        state is UpdateState.UpToDate ||
        state is UpdateState.Available ||
        state is UpdateState.Failed ||
        state is UpdateState.Unsupported

private fun computeSha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(BUFFER_SIZE)
        var read: Int
        while (input.read(buffer).also { read = it } != -1) {
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { java.lang.String.format(Locale.US, "%02x", it) }
}
