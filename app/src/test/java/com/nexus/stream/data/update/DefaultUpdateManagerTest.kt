package com.nexus.stream.data.update

import android.content.pm.PackageInstaller
import com.nexus.stream.domain.model.ApkFacts
import com.nexus.stream.domain.model.InstalledFacts
import com.nexus.stream.domain.model.ReleaseAsset
import com.nexus.stream.domain.model.ReleaseInfo
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
import com.nexus.stream.domain.repository.UpdatePrefsRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
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
class DefaultUpdateManagerTest {
    @get:Rule
    val tmpFolder = TemporaryFolder()

    private var currentTime = 100_000_000L

    class FakeReleaseSource : ReleaseSource {
        var result: ReleaseResult = ReleaseResult.Failure(UpdateFailure.NoRelease)
        var callCount = 0
        var waitDeferred: CompletableDeferred<Unit>? = null

        override suspend fun fetchLatestRelease(): ReleaseResult {
            callCount++
            waitDeferred?.await()
            return result
        }
    }

    class FakeApkDownloader(
        private val storeDir: File,
    ) : ApkDownloader {
        var result: DownloadResult? = null
        var callCount = 0
        var waitDeferred: CompletableDeferred<Unit>? = null
        var emitProgress = true
        var cleanedKeep: String? = null

        override suspend fun download(
            asset: ReleaseAsset,
            onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit,
        ): DownloadResult {
            callCount++
            if (emitProgress) {
                onProgress(asset.sizeBytes / 2, asset.sizeBytes)
            }
            waitDeferred?.await()
            val file =
                File(storeDir, asset.name).apply {
                    if (!exists()) writeBytes(ByteArray(asset.sizeBytes.toInt()))
                }
            return result ?: DownloadResult.Success(file)
        }

        override fun cleanup(keep: String?) {
            cleanedKeep = keep
        }
    }

    class FakeApkFileStore(
        val folder: File,
    ) : ApkFileStore {
        override fun dir(): File = folder

        override fun cleanup(keep: String?) {
            folder.listFiles()?.forEach { file ->
                if (keep == null || file.name != keep) file.delete()
            }
        }
    }

    class FakePackageArchiveReader : PackageArchiveReader {
        var facts: ApkFacts? =
            ApkFacts(
                packageName = "com.nexus.stream",
                versionCode = 10001L,
                signerCertSha256 = setOf("trusted_cert"),
            )
        var installedPackage = "com.nexus.stream"
        var installedCode = 10000L
        var trustedCert = "trusted_cert"

        override fun readArchiveFacts(file: File): ApkFacts? = facts

        override fun getInstalledFacts(): InstalledFacts =
            InstalledFacts(
                packageName = installedPackage,
                versionCode = installedCode,
                trustsCert = { it == trustedCert },
            )
    }

    class FakeApkInstaller : ApkInstaller {
        var canInstallResult = true
        val installCalls = mutableListOf<File>()
        var staleSessionsAbandoned = false
        var throwOnInstall: Exception? = null

        override fun canInstall(): Boolean = canInstallResult

        override suspend fun install(file: File) {
            throwOnInstall?.let { throw it }
            installCalls.add(file)
        }

        override fun abandonStaleSessions() {
            staleSessionsAbandoned = true
        }
    }

    class FakeNetworkInfo : NetworkInfo {
        var isMeteredResult = false

        override fun isMetered(): Boolean = isMeteredResult
    }

    class FakeStorageInfo : StorageInfo {
        var availableBytesResult = 10_000_000_000L // 10 GB

        override fun availableBytes(): Long = availableBytesResult
    }

    class FakeUpdatePrefsRepository : UpdatePrefsRepository {
        val autoCheckFlow = MutableStateFlow(true)
        val lastCheckedAtFlow = MutableStateFlow<Long?>(null)
        val pendingInstallCodeFlow = MutableStateFlow<Long?>(null)

        override val autoCheck: Flow<Boolean> = autoCheckFlow
        override val lastCheckedAt: Flow<Long?> = lastCheckedAtFlow
        override val pendingInstallCode: Flow<Long?> = pendingInstallCodeFlow

        override suspend fun setAutoCheck(enabled: Boolean) {
            autoCheckFlow.value = enabled
        }

        override suspend fun setLastCheckedAt(timestamp: Long) {
            lastCheckedAtFlow.value = timestamp
        }

        override suspend fun setPendingInstallCode(versionCode: Long) {
            pendingInstallCodeFlow.value = versionCode
        }

        override suspend fun clearPendingInstall() {
            pendingInstallCodeFlow.value = null
        }
    }

    class FakeAppInfoProvider(
        override var versionName: String? = "1.0.0",
        override var versionCode: Long = 10000L,
        var packageName: String = "com.nexus.stream",
    ) : AppInfoProvider

    private fun createHarness(
        testScope: TestScope,
        installedPackage: String = "com.nexus.stream",
        installedCode: Long = 10000L,
        intentLauncher: (Any) -> Boolean = { true },
    ): TestHarness {
        val storeDir = tmpFolder.newFolder()
        val releaseSource = FakeReleaseSource()
        val downloader = FakeApkDownloader(storeDir)
        val fileStore = FakeApkFileStore(storeDir)
        val archiveReader =
            FakePackageArchiveReader().apply {
                this.installedPackage = installedPackage
                this.installedCode = installedCode
            }
        val installer = FakeApkInstaller()
        val networkInfo = FakeNetworkInfo()
        val storageInfo = FakeStorageInfo()
        val prefs = FakeUpdatePrefsRepository()
        val appInfo =
            FakeAppInfoProvider(versionName = "1.0.0", versionCode = installedCode, packageName = installedPackage)
        val clock = Clock { currentTime }

        val manager =
            DefaultUpdateManager(
                releaseSource = releaseSource,
                downloader = downloader,
                fileStore = fileStore,
                archiveReader = archiveReader,
                installer = installer,
                networkInfo = networkInfo,
                storageInfo = storageInfo,
                prefs = prefs,
                appInfoProvider = appInfo,
                clock = clock,
                coroutineScope = testScope.backgroundScope,
                ioDispatcher = UnconfinedTestDispatcher(testScope.testScheduler),
                intentLauncher = intentLauncher,
            )

        return TestHarness(
            manager = manager,
            releaseSource = releaseSource,
            downloader = downloader,
            fileStore = fileStore,
            archiveReader = archiveReader,
            installer = installer,
            networkInfo = networkInfo,
            storageInfo = storageInfo,
            prefs = prefs,
            appInfo = appInfo,
        )
    }

    data class TestHarness(
        val manager: DefaultUpdateManager,
        val releaseSource: FakeReleaseSource,
        val downloader: FakeApkDownloader,
        val fileStore: FakeApkFileStore,
        val archiveReader: FakePackageArchiveReader,
        val installer: FakeApkInstaller,
        val networkInfo: FakeNetworkInfo,
        val storageInfo: FakeStorageInfo,
        val prefs: FakeUpdatePrefsRepository,
        val appInfo: FakeAppInfoProvider,
    )

    private fun sampleRelease(tag: String = "v1.0.1"): ReleaseInfo {
        val apk = ReleaseAsset("x-arm64-v8a.apk", 1000L, "https://github.com/a/b/x.apk")
        return ReleaseInfo(
            tag = tag,
            title = "Release $tag",
            notes = "Notes",
            htmlUrl = "https://github.com/a/b/releases/$tag",
            assets = listOf(apk),
        )
    }

    @Test
    fun UM1_initial_isIdle() =
        runTest {
            val h = createHarness(this)
            assertTrue(h.manager.state.value is UpdateState.Idle)
            assertNull((h.manager.state.value as UpdateState.Idle).lastCheckedAt)
        }

    @Test
    fun UM2_check_noNewerRelease_isUpToDate_andStoresTheTime() =
        runTest {
            val h = createHarness(this)
            h.releaseSource.result = ReleaseResult.Success(sampleRelease("v1.0.0"))

            h.manager.checkNow()
            runCurrent()

            assertTrue(h.manager.state.value is UpdateState.UpToDate)
            assertEquals(currentTime, (h.manager.state.value as UpdateState.UpToDate).checkedAt)
            assertEquals(currentTime, h.prefs.lastCheckedAt.first())
        }

    @Test
    fun UM3_check_newerRelease_isAvailable() =
        runTest {
            val h = createHarness(this)
            h.releaseSource.result = ReleaseResult.Success(sampleRelease("v1.0.1"))

            h.manager.checkNow()
            runCurrent()

            assertTrue(h.manager.state.value is UpdateState.Available)
            val candidate = (h.manager.state.value as UpdateState.Available).candidate
            assertEquals("v1.0.1", candidate.tag)
            assertEquals(1000L, candidate.asset.sizeBytes)
        }

    @Test
    fun UM4_check_failure_isFailed_withTheReason() =
        runTest {
            val h = createHarness(this)
            h.releaseSource.result = ReleaseResult.Failure(UpdateFailure.Network)

            h.manager.checkNow()
            runCurrent()

            assertTrue(h.manager.state.value is UpdateState.Failed)
            assertEquals(UpdateFailure.Network, (h.manager.state.value as UpdateState.Failed).failure)
        }

    @Test
    fun UM5_check_whileChecking_isIgnored() =
        runTest {
            val h = createHarness(this)
            val wait = CompletableDeferred<Unit>()
            h.releaseSource.waitDeferred = wait
            h.releaseSource.result = ReleaseResult.Success(sampleRelease("v1.0.1"))

            h.manager.checkNow()
            runCurrent()
            assertTrue(h.manager.state.value is UpdateState.Checking)

            h.manager.checkNow()
            runCurrent()
            assertEquals(1, h.releaseSource.callCount)

            wait.complete(Unit)
            runCurrent()
            assertTrue(h.manager.state.value is UpdateState.Available)
        }

    @Test
    fun UM6_check_whileDownloading_isIgnored() =
        runTest {
            val h = createHarness(this)
            h.releaseSource.result = ReleaseResult.Success(sampleRelease("v1.0.1"))
            h.manager.checkNow()
            runCurrent()

            val wait = CompletableDeferred<Unit>()
            h.downloader.waitDeferred = wait

            h.manager.confirmDownload(allowMetered = false)
            runCurrent()
            assertTrue(h.manager.state.value is UpdateState.Downloading)

            h.manager.checkNow()
            runCurrent()
            assertEquals(1, h.releaseSource.callCount)
            assertTrue(h.manager.state.value is UpdateState.Downloading)

            wait.complete(Unit)
            runCurrent()
        }

    @Test
    fun UM7_debugPackage_isUnsupported_noNetwork() =
        runTest {
            val h = createHarness(this, installedPackage = "com.nexus.stream.debug")

            h.manager.checkNow()
            runCurrent()

            assertTrue(h.manager.state.value is UpdateState.Unsupported)
            assertEquals(0, h.releaseSource.callCount)
        }

    @Test
    fun UM8_download_onUnmetered_startsAtOnce_andReportsProgress() =
        runTest {
            val h = createHarness(this)
            h.releaseSource.result = ReleaseResult.Success(sampleRelease("v1.0.1"))
            h.manager.checkNow()
            runCurrent()

            h.manager.confirmDownload(allowMetered = false)
            runCurrent()

            assertEquals(1, h.downloader.callCount)
            assertEquals(1, h.installer.installCalls.size)
        }

    @Test
    fun UM9_download_onMetered_requiresConfirmFirst() =
        runTest {
            val h = createHarness(this)
            h.networkInfo.isMeteredResult = true
            h.releaseSource.result = ReleaseResult.Success(sampleRelease("v1.0.1"))
            h.manager.checkNow()
            runCurrent()

            h.manager.confirmDownload(allowMetered = false)
            runCurrent()
            assertTrue(h.manager.state.value is UpdateState.ConfirmMetered)
            assertEquals(0, h.downloader.callCount)

            h.manager.confirmDownload(allowMetered = true)
            runCurrent()
            assertEquals(1, h.downloader.callCount)
        }

    @Test
    fun UM10_download_notEnoughSpace_failsBeforeDownloading() =
        runTest {
            val h = createHarness(this)
            h.storageInfo.availableBytesResult = 1000L // far too small
            h.releaseSource.result = ReleaseResult.Success(sampleRelease("v1.0.1"))
            h.manager.checkNow()
            runCurrent()

            h.manager.confirmDownload(allowMetered = false)
            runCurrent()

            assertTrue(h.manager.state.value is UpdateState.Failed)
            assertEquals(UpdateFailure.InsufficientStorage, (h.manager.state.value as UpdateState.Failed).failure)
            assertEquals(0, h.downloader.callCount)
        }

    @Test
    fun UM11_download_cancel_returnsToAvailable_andDeletesThePartFile() =
        runTest {
            val h = createHarness(this)
            h.releaseSource.result = ReleaseResult.Success(sampleRelease("v1.0.1"))
            h.manager.checkNow()
            runCurrent()

            val wait = CompletableDeferred<Unit>()
            h.downloader.waitDeferred = wait

            h.manager.confirmDownload(allowMetered = false)
            runCurrent()
            assertTrue(h.manager.state.value is UpdateState.Downloading)

            h.manager.cancelDownload()
            runCurrent()

            assertTrue(h.manager.state.value is UpdateState.Available)
        }

    @Test
    fun UM12_download_failure_isFailed_andRetryRestarts() =
        runTest {
            val h = createHarness(this)
            h.releaseSource.result = ReleaseResult.Success(sampleRelease("v1.0.1"))
            h.manager.checkNow()
            runCurrent()

            h.downloader.result = DownloadResult.Failure(UpdateFailure.DownloadFailed)
            h.manager.confirmDownload(allowMetered = false)
            runCurrent()

            assertTrue(h.manager.state.value is UpdateState.Failed)
            assertEquals(UpdateFailure.DownloadFailed, (h.manager.state.value as UpdateState.Failed).failure)

            h.manager.dismissFailure()
            runCurrent()
            assertTrue(h.manager.state.value is UpdateState.Available)

            h.downloader.result = null
            h.manager.confirmDownload(allowMetered = false)
            runCurrent()
            assertEquals(2, h.downloader.callCount)
        }

    @Test
    fun UM13_afterDownload_signatureMismatch_failsAndDeletesTheFile() =
        runTest {
            val h = createHarness(this)
            h.releaseSource.result = ReleaseResult.Success(sampleRelease("v1.0.1"))
            h.archiveReader.facts =
                ApkFacts(
                    packageName = "com.nexus.stream",
                    versionCode = 10001L,
                    signerCertSha256 = setOf("untrusted_cert"),
                )
            h.manager.checkNow()
            runCurrent()

            h.manager.confirmDownload(allowMetered = false)
            runCurrent()

            assertTrue(h.manager.state.value is UpdateState.Failed)
            assertEquals(UpdateFailure.SignatureMismatch, (h.manager.state.value as UpdateState.Failed).failure)
            assertEquals(0, h.installer.installCalls.size)
        }

    @Test
    fun UM14_afterVerify_withoutPermission_needsInstallPermission() =
        runTest {
            val h = createHarness(this)
            h.releaseSource.result = ReleaseResult.Success(sampleRelease("v1.0.1"))
            h.installer.canInstallResult = false

            h.manager.checkNow()
            runCurrent()

            h.manager.confirmDownload(allowMetered = false)
            runCurrent()

            assertTrue(h.manager.state.value is UpdateState.NeedsInstallPermission)
            assertEquals(0, h.installer.installCalls.size)
        }

    @Test
    fun UM15_permissionGranted_continuesToInstall_withoutRedownload() =
        runTest {
            val h = createHarness(this)
            h.releaseSource.result = ReleaseResult.Success(sampleRelease("v1.0.1"))
            h.installer.canInstallResult = false

            h.manager.checkNow()
            runCurrent()
            h.manager.confirmDownload(allowMetered = false)
            runCurrent()
            assertTrue(h.manager.state.value is UpdateState.NeedsInstallPermission)

            h.installer.canInstallResult = true
            h.manager.onInstallPermissionResult()
            runCurrent()

            assertTrue(h.manager.state.value is UpdateState.Installing)
            assertEquals(1, h.installer.installCalls.size)
            assertEquals(1, h.downloader.callCount)
        }

    @Test
    fun UM16_install_commitsTheSession_andStoresThePendingMarker() =
        runTest {
            val h = createHarness(this)
            h.releaseSource.result = ReleaseResult.Success(sampleRelease("v1.0.1"))
            h.archiveReader.facts = ApkFacts("com.nexus.stream", 10001L, setOf("trusted_cert"))

            h.manager.checkNow()
            runCurrent()
            h.manager.confirmDownload(allowMetered = false)
            runCurrent()

            assertTrue(h.manager.state.value is UpdateState.Installing)
            assertEquals(1, h.installer.installCalls.size)
            assertEquals(10001L, h.prefs.pendingInstallCode.first())
        }

    @Test
    fun UM17_pendingUserAction_launchesConfirmation_orAwaits() =
        runTest {
            var launchSuccess = true
            val h = createHarness(this, intentLauncher = { launchSuccess })
            h.releaseSource.result = ReleaseResult.Success(sampleRelease("v1.0.1"))

            h.manager.checkNow()
            runCurrent()
            h.manager.confirmDownload(allowMetered = false)
            runCurrent()
            assertTrue(h.manager.state.value is UpdateState.Installing)

            // Launcher works
            h.manager.onInstallResult(PackageInstaller.STATUS_PENDING_USER_ACTION, "dummy_intent")
            runCurrent()
            assertTrue(h.manager.state.value is UpdateState.Installing)

            // Launcher throws or returns false
            launchSuccess = false
            h.manager.onInstallResult(PackageInstaller.STATUS_PENDING_USER_ACTION, "dummy_intent")
            runCurrent()
            assertTrue(h.manager.state.value is UpdateState.AwaitingConfirmation)

            // continueConfirmation launches again
            launchSuccess = true
            h.manager.continueConfirmation()
            runCurrent()
            assertTrue(h.manager.state.value is UpdateState.Installing)
        }

    @Test
    fun UM18_cancelledByUser_returnsToAvailable_fileKept() =
        runTest {
            val h = createHarness(this)
            h.releaseSource.result = ReleaseResult.Success(sampleRelease("v1.0.1"))

            h.manager.checkNow()
            runCurrent()
            h.manager.confirmDownload(allowMetered = false)
            runCurrent()

            h.manager.onInstallResult(PackageInstaller.STATUS_FAILURE_ABORTED)
            runCurrent()

            assertTrue(h.manager.state.value is UpdateState.Available)
            assertNull(h.prefs.pendingInstallCode.first())
        }

    @Test
    fun UM19_installFailure_failsWithTheMappedReason() =
        runTest {
            val h = createHarness(this)
            h.releaseSource.result = ReleaseResult.Success(sampleRelease("v1.0.1"))

            h.manager.checkNow()
            runCurrent()
            h.manager.confirmDownload(allowMetered = false)
            runCurrent()

            h.manager.onInstallResult(PackageInstaller.STATUS_FAILURE_CONFLICT)
            runCurrent()

            assertTrue(h.manager.state.value is UpdateState.Failed)
            assertEquals(UpdateFailure.InstallConflict, (h.manager.state.value as UpdateState.Failed).failure)
            assertNull(h.prefs.pendingInstallCode.first())
        }

    @Test
    fun UM20_autoCheck_runsOnlyWhenEnabledAndDue() =
        runTest {
            val h = createHarness(this)
            h.releaseSource.result = ReleaseResult.Success(sampleRelease("v1.0.0"))
            currentTime = 100_000_000L

            // 1. autoCheck on, last check 25 h ago -> checks
            h.prefs.setAutoCheck(true)
            h.prefs.setLastCheckedAt(currentTime - 25 * 3600 * 1000L)
            h.manager.autoCheckIfDue()
            runCurrent()
            assertEquals(1, h.releaseSource.callCount)

            // 2. then 23 h ago -> does not check
            h.prefs.setLastCheckedAt(currentTime - 23 * 3600 * 1000L)
            h.manager.autoCheckIfDue()
            runCurrent()
            assertEquals(1, h.releaseSource.callCount)

            // 3. then autoCheck off -> does not check
            h.prefs.setAutoCheck(false)
            h.prefs.setLastCheckedAt(currentTime - 30 * 3600 * 1000L)
            h.manager.autoCheckIfDue()
            runCurrent()
            assertEquals(1, h.releaseSource.callCount)
        }

    @Test
    fun UM21_autoCheck_failureIsSilent() =
        runTest {
            val h = createHarness(this)
            currentTime = 100_000_000L
            h.prefs.setAutoCheck(true)
            val pastTime = currentTime - 25 * 3600 * 1000L
            h.prefs.setLastCheckedAt(pastTime)
            h.releaseSource.result = ReleaseResult.Failure(UpdateFailure.Network)

            h.manager.autoCheckIfDue()
            runCurrent()

            // State stays Idle, no Failed state
            assertTrue(h.manager.state.value is UpdateState.Idle)
            assertEquals(pastTime, h.prefs.lastCheckedAt.first())
        }

    @Test
    fun UM22_start_markerMatchesInstalledVersion_reportsUpdated_andClears() =
        runTest {
            val h = createHarness(this, installedCode = 10001L)
            h.prefs.setPendingInstallCode(10001L)
            h.appInfo.versionName = "1.0.1"

            val effects = mutableListOf<UpdateEffect>()
            val job = launch(UnconfinedTestDispatcher(testScheduler)) { h.manager.effects.toList(effects) }

            h.manager.onAppStart()
            runCurrent()

            assertEquals(1, effects.size)
            assertEquals(UpdateEffect.Updated("1.0.1"), effects.first())
            assertNull(h.prefs.pendingInstallCode.first())
            job.cancel()
        }

    @Test
    fun UM23_start_markerNotInstalled_clearsSilently() =
        runTest {
            val h = createHarness(this, installedCode = 10000L)
            h.prefs.setPendingInstallCode(10001L)

            val effects = mutableListOf<UpdateEffect>()
            val job = launch(UnconfinedTestDispatcher(testScheduler)) { h.manager.effects.toList(effects) }

            h.manager.onAppStart()
            runCurrent()

            assertTrue(effects.isEmpty())
            assertNull(h.prefs.pendingInstallCode.first())
            job.cancel()
        }

    @Test
    fun UM24_start_cleansOldApkFiles() =
        runTest {
            val h = createHarness(this)
            val dummyFile = File(h.fileStore.dir(), "old.apk").apply { writeText("old") }
            assertTrue(dummyFile.exists())

            h.manager.onAppStart()
            runCurrent()

            assertFalse(dummyFile.exists())
        }

    @Test
    fun UM25_openReleasePage_emitsOpenUrl() =
        runTest {
            val h = createHarness(this)
            h.releaseSource.result = ReleaseResult.Success(sampleRelease("v1.0.1"))
            h.manager.checkNow()
            runCurrent()

            val effects = mutableListOf<UpdateEffect>()
            val job = launch(UnconfinedTestDispatcher(testScheduler)) { h.manager.effects.toList(effects) }

            h.manager.openReleasePage()
            runCurrent()

            assertEquals(1, effects.size)
            assertEquals(UpdateEffect.OpenUrl("https://github.com/a/b/releases/v1.0.1"), effects.first())
            job.cancel()
        }
}
