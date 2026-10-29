package com.nexus.stream.presentation.feature.settings

import app.cash.turbine.test
import com.nexus.stream.R
import com.nexus.stream.domain.model.ReleaseAsset
import com.nexus.stream.domain.model.UpdateCandidate
import com.nexus.stream.domain.model.UpdateEffect
import com.nexus.stream.domain.model.UpdateFailure
import com.nexus.stream.domain.model.UpdateState
import com.nexus.stream.domain.repository.UpdateManager
import com.nexus.stream.domain.repository.UpdatePrefsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateViewModelTest {
    class FakeUpdateManager : UpdateManager {
        val stateFlow = MutableStateFlow<UpdateState>(UpdateState.Idle(null))
        val effectsFlow = MutableSharedFlow<UpdateEffect>(extraBufferCapacity = 16)

        override val state: MutableStateFlow<UpdateState> = stateFlow
        override val effects: Flow<UpdateEffect> = effectsFlow

        var checkNowCalls = 0
        val confirmDownloadCalls = mutableListOf<Boolean>()
        var cancelDownloadCalls = 0
        var installCalls = 0
        var continueConfirmationCalls = 0
        var dismissFailureCalls = 0
        var openReleasePageCalls = 0
        var onInstallPermissionResultCalls = 0
        var autoCheckIfDueCalls = 0
        var onAppStartCalls = 0

        override fun checkNow() {
            checkNowCalls++
        }

        override fun confirmDownload(allowMetered: Boolean) {
            confirmDownloadCalls.add(allowMetered)
        }

        override fun cancelDownload() {
            cancelDownloadCalls++
        }

        override fun install() {
            installCalls++
        }

        override fun continueConfirmation() {
            continueConfirmationCalls++
        }

        override fun dismissFailure() {
            dismissFailureCalls++
        }

        override fun openReleasePage() {
            openReleasePageCalls++
        }

        override fun onInstallPermissionResult() {
            onInstallPermissionResultCalls++
        }

        override fun onInstallResult(
            status: Int,
            confirmIntent: Any?,
        ) {
            // Ignored in test
        }

        override fun autoCheckIfDue() {
            autoCheckIfDueCalls++
        }

        override fun onAppStart() {
            onAppStartCalls++
        }
    }

    class FakeUpdatePrefsRepository : UpdatePrefsRepository {
        val autoCheckFlow = MutableStateFlow(true)
        val lastCheckedAtFlow = MutableStateFlow<Long?>(null)
        val pendingInstallCodeFlow = MutableStateFlow<Long?>(null)

        override val autoCheck: Flow<Boolean> = autoCheckFlow
        override val lastCheckedAt: Flow<Long?> = lastCheckedAtFlow
        override val pendingInstallCode: Flow<Long?> = pendingInstallCodeFlow

        val setAutoCheckCalls = mutableListOf<Boolean>()

        override suspend fun setAutoCheck(enabled: Boolean) {
            setAutoCheckCalls.add(enabled)
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

    private fun sampleCandidate(
        tag: String = "v1.0.1",
        sizeBytes: Long = 98L * 1024 * 1024,
        notes: String? = "What's new in 1.0.1",
    ): UpdateCandidate =
        UpdateCandidate(
            tag = tag,
            title = "Release $tag",
            notes = notes,
            releaseUrl = "https://github.com/org/repo/releases/tag/$tag",
            asset =
                ReleaseAsset(
                    name = "NexusStream-$tag.apk",
                    sizeBytes = sizeBytes,
                    downloadUrl = "https://github.com/org/repo/releases/download/$tag/app.apk",
                ),
        )

    @Test
    fun UV1_idleNeverChecked_showsCheckButton() =
        runTest {
            val manager = FakeUpdateManager()
            val prefs = FakeUpdatePrefsRepository()
            manager.stateFlow.value = UpdateState.Idle(null)

            val viewModel =
                UpdateViewModel(
                    updateManager = manager,
                    updatePrefsRepository = prefs,
                    packageName = "com.nexus.stream",
                    ioDispatcher = UnconfinedTestDispatcher(testScheduler),
                )
            advanceUntilIdle()

            val ui = viewModel.uiState.value
            assertEquals(R.string.update_not_checked, ui.status.resId)
            assertEquals(1, ui.bigButtons.size)
            val btn = ui.bigButtons.first()
            assertTrue(btn.isPrimary)
            assertEquals(R.string.update_check, btn.textResId)
            assertEquals(UpdateEvent.CheckNow, btn.event)
            assertFalse(ui.summaryFlag)
        }

    @Test
    fun UV2_upToDate_showsCheckedTimeAndCheckAgain() =
        runTest {
            val manager = FakeUpdateManager()
            val prefs = FakeUpdatePrefsRepository()
            val checkedTime = 123456789L
            manager.stateFlow.value = UpdateState.UpToDate(checkedTime)

            val viewModel =
                UpdateViewModel(
                    updateManager = manager,
                    updatePrefsRepository = prefs,
                    packageName = "com.nexus.stream",
                    ioDispatcher = UnconfinedTestDispatcher(testScheduler),
                )
            advanceUntilIdle()

            val ui = viewModel.uiState.value
            assertEquals(R.string.update_up_to_date, ui.status.resId)
            assertEquals(listOf(checkedTime), ui.status.formatArgs)
            assertEquals(1, ui.bigButtons.size)
            val btn = ui.bigButtons.first()
            assertTrue(btn.isPrimary)
            assertEquals(R.string.update_check_again, btn.textResId)
            assertEquals(UpdateEvent.CheckNow, btn.event)
            assertFalse(ui.summaryFlag)
        }

    @Test
    fun UV3_available_showsVersionSizeNotesAndButtons() =
        runTest {
            val manager = FakeUpdateManager()
            val prefs = FakeUpdatePrefsRepository()
            val size = 98L * 1024 * 1024
            val candidate = sampleCandidate(tag = "v1.0.1", sizeBytes = size, notes = "Awesome new features")
            manager.stateFlow.value = UpdateState.Available(candidate)

            val viewModel =
                UpdateViewModel(
                    updateManager = manager,
                    updatePrefsRepository = prefs,
                    packageName = "com.nexus.stream",
                    ioDispatcher = UnconfinedTestDispatcher(testScheduler),
                )
            advanceUntilIdle()

            val ui = viewModel.uiState.value
            assertEquals(R.string.update_available, ui.status.resId)
            assertEquals("1.0.1", ui.status.formatArgs.first())
            assertEquals(size, ui.status.sizeBytes)
            assertEquals("Awesome new features", ui.notes)
            assertTrue(ui.summaryFlag)

            val primary = ui.bigButtons.first { it.isPrimary }
            assertEquals(R.string.update_download, primary.textResId)
            assertEquals(UpdateEvent.Download, primary.event)

            val secondary = ui.bigButtons.first { !it.isPrimary }
            assertEquals(R.string.update_view_release, secondary.textResId)
            assertEquals(UpdateEvent.OpenRelease, secondary.event)
        }

    @Test
    fun UV4_downloading_showsPercent_orIndeterminateWhenTotalUnknown() =
        runTest {
            val manager = FakeUpdateManager()
            val prefs = FakeUpdatePrefsRepository()
            val candidate = sampleCandidate(tag = "v1.0.1")

            manager.stateFlow.value = UpdateState.Downloading(candidate, downloadedBytes = 42L, totalBytes = 100L)

            val viewModel =
                UpdateViewModel(
                    updateManager = manager,
                    updatePrefsRepository = prefs,
                    packageName = "com.nexus.stream",
                    ioDispatcher = UnconfinedTestDispatcher(testScheduler),
                )
            advanceUntilIdle()

            val ui1 = viewModel.uiState.value
            assertEquals(Progress.Determinate(0.42f), ui1.progress)
            assertEquals(R.string.update_downloading, ui1.status.resId)
            assertEquals("1.0.1", ui1.status.formatArgs[0])
            assertEquals(42, ui1.status.formatArgs[1])

            // Total is 0 / unknown
            manager.stateFlow.value = UpdateState.Downloading(candidate, downloadedBytes = 42L, totalBytes = 0L)
            advanceUntilIdle()

            val ui2 = viewModel.uiState.value
            assertEquals(Progress.Indeterminate, ui2.progress)
            assertEquals(R.string.update_downloading_indeterminate, ui2.status.resId)
            assertEquals(1, ui2.status.formatArgs.size)
            assertEquals("1.0.1", ui2.status.formatArgs[0])
        }

    @Test
    fun UV5_failed_everyFailureHasANonEmptyMessage() {
        val failures =
            listOf(
                UpdateFailure.Network,
                UpdateFailure.Timeout,
                UpdateFailure.RateLimited(12345L),
                UpdateFailure.NoRelease,
                UpdateFailure.NoApk,
                UpdateFailure.BadResponse,
                UpdateFailure.InsufficientStorage,
                UpdateFailure.DownloadFailed,
                UpdateFailure.SizeMismatch,
                UpdateFailure.ChecksumMismatch,
                UpdateFailure.InvalidApk,
                UpdateFailure.PackageMismatch,
                UpdateFailure.NotNewer,
                UpdateFailure.SignatureMismatch,
                UpdateFailure.InstallBlocked,
                UpdateFailure.InstallIncompatible,
                UpdateFailure.InstallConflict,
                UpdateFailure.InstallStorage,
                UpdateFailure.InstallInvalid,
                UpdateFailure.InstallFailed,
                UpdateFailure.Unsupported,
            )

        for (failure in failures) {
            val msg = failureMessage(failure)
            assertNotEquals(0, msg.resId)
        }
    }

    @Test
    fun UV6_failed_signatureMismatch_primaryIsViewRelease() =
        runTest {
            val manager = FakeUpdateManager()
            val prefs = FakeUpdatePrefsRepository()
            val candidate = sampleCandidate()

            val viewReleaseOnlyFailures =
                listOf(
                    UpdateFailure.SignatureMismatch,
                    UpdateFailure.InstallBlocked,
                    UpdateFailure.InstallConflict,
                    UpdateFailure.InstallIncompatible,
                )

            val viewModel =
                UpdateViewModel(
                    updateManager = manager,
                    updatePrefsRepository = prefs,
                    packageName = "com.nexus.stream",
                    ioDispatcher = UnconfinedTestDispatcher(testScheduler),
                )

            for (failure in viewReleaseOnlyFailures) {
                manager.stateFlow.value = UpdateState.Failed(failure, candidate)
                advanceUntilIdle()

                val ui = viewModel.uiState.value
                assertEquals(1, ui.bigButtons.size)
                val primary = ui.bigButtons.first()
                assertTrue(primary.isPrimary)
                assertEquals(R.string.update_view_release, primary.textResId)
                assertEquals(UpdateEvent.OpenRelease, primary.event)
                assertNull(ui.bigButtons.find { it.textResId == R.string.update_try_again })
            }

            // For Network, primary is Try again
            manager.stateFlow.value = UpdateState.Failed(UpdateFailure.Network, candidate)
            advanceUntilIdle()

            val networkUi = viewModel.uiState.value
            val primaryNetwork = networkUi.bigButtons.first { it.isPrimary }
            assertEquals(R.string.update_try_again, primaryNetwork.textResId)
            assertEquals(UpdateEvent.DismissFailure, primaryNetwork.event)
            val secondaryNetwork = networkUi.bigButtons.first { !it.isPrimary }
            assertEquals(R.string.update_view_release, secondaryNetwork.textResId)
            assertEquals(UpdateEvent.OpenRelease, secondaryNetwork.event)
        }

    @Test
    fun UV7_events_callTheManager() =
        runTest {
            val manager = FakeUpdateManager()
            val prefs = FakeUpdatePrefsRepository()
            val candidate = sampleCandidate()

            val viewModel =
                UpdateViewModel(
                    updateManager = manager,
                    updatePrefsRepository = prefs,
                    packageName = "com.nexus.stream",
                    ioDispatcher = UnconfinedTestDispatcher(testScheduler),
                )
            advanceUntilIdle()

            // CheckNow while Idle works
            manager.stateFlow.value = UpdateState.Idle(null)
            viewModel.onEvent(UpdateEvent.CheckNow)
            assertEquals(1, manager.checkNowCalls)

            // CheckNow while Checking, Downloading, Verifying, Installing does nothing
            manager.stateFlow.value = UpdateState.Checking
            viewModel.onEvent(UpdateEvent.CheckNow)
            manager.stateFlow.value = UpdateState.Downloading(candidate, 0L, 100L)
            viewModel.onEvent(UpdateEvent.CheckNow)
            manager.stateFlow.value = UpdateState.Verifying(candidate)
            viewModel.onEvent(UpdateEvent.CheckNow)
            manager.stateFlow.value = UpdateState.Installing(candidate)
            viewModel.onEvent(UpdateEvent.CheckNow)
            assertEquals(1, manager.checkNowCalls)

            // Download
            viewModel.onEvent(UpdateEvent.Download)
            assertEquals(listOf(false), manager.confirmDownloadCalls)

            // ConfirmMetered
            viewModel.onEvent(UpdateEvent.ConfirmMetered)
            assertEquals(listOf(false, true), manager.confirmDownloadCalls)

            // Cancel
            viewModel.onEvent(UpdateEvent.Cancel)
            assertEquals(1, manager.cancelDownloadCalls)

            // Install
            viewModel.onEvent(UpdateEvent.Install)
            assertEquals(1, manager.installCalls)

            // Continue
            viewModel.onEvent(UpdateEvent.Continue)
            assertEquals(1, manager.continueConfirmationCalls)

            // DismissFailure
            viewModel.onEvent(UpdateEvent.DismissFailure)
            assertEquals(1, manager.dismissFailureCalls)

            // OpenRelease
            viewModel.onEvent(UpdateEvent.OpenRelease)
            assertEquals(1, manager.openReleasePageCalls)
        }

    @Test
    fun UV8_openInstallSettings_emitsTheEffectWithThePackageName() =
        runTest {
            val manager = FakeUpdateManager()
            val prefs = FakeUpdatePrefsRepository()
            val candidate = sampleCandidate()
            manager.stateFlow.value = UpdateState.NeedsInstallPermission(candidate)

            val viewModel =
                UpdateViewModel(
                    updateManager = manager,
                    updatePrefsRepository = prefs,
                    packageName = "com.nexus.stream",
                    ioDispatcher = UnconfinedTestDispatcher(testScheduler),
                )
            advanceUntilIdle()

            viewModel.effects.test {
                viewModel.onEvent(UpdateEvent.OpenInstallSettings)
                assertEquals(UpdateUiEffect.OpenInstallPermissionSettings("com.nexus.stream"), awaitItem())
            }
        }

    @Test
    fun UV9_resumed_callsTheManagerOnlyWhenPermissionIsNeeded() =
        runTest {
            val manager = FakeUpdateManager()
            val prefs = FakeUpdatePrefsRepository()
            val candidate = sampleCandidate()

            val viewModel =
                UpdateViewModel(
                    updateManager = manager,
                    updatePrefsRepository = prefs,
                    packageName = "com.nexus.stream",
                    ioDispatcher = UnconfinedTestDispatcher(testScheduler),
                )
            advanceUntilIdle()

            // Resumed while NeedsInstallPermission calls manager.onInstallPermissionResult()
            manager.stateFlow.value = UpdateState.NeedsInstallPermission(candidate)
            viewModel.onEvent(UpdateEvent.Resumed)
            assertEquals(1, manager.onInstallPermissionResultCalls)

            // Resumed while Idle does nothing
            manager.stateFlow.value = UpdateState.Idle(null)
            viewModel.onEvent(UpdateEvent.Resumed)
            assertEquals(1, manager.onInstallPermissionResultCalls)
        }

    @Test
    fun UV10_autoCheckSwitch_callsThePrefs() =
        runTest {
            val manager = FakeUpdateManager()
            val prefs = FakeUpdatePrefsRepository()
            prefs.autoCheckFlow.value = true

            val viewModel =
                UpdateViewModel(
                    updateManager = manager,
                    updatePrefsRepository = prefs,
                    packageName = "com.nexus.stream",
                    ioDispatcher = UnconfinedTestDispatcher(testScheduler),
                )
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.autoCheck)

            viewModel.onEvent(UpdateEvent.SetAutoCheck(false))
            advanceUntilIdle()

            assertEquals(listOf(false), prefs.setAutoCheckCalls)
            assertFalse(viewModel.uiState.value.autoCheck)
        }

    @Test
    fun UV11_updatedEffect_isForwardedAsShowUpdated() =
        runTest {
            val manager = FakeUpdateManager()
            val prefs = FakeUpdatePrefsRepository()

            val viewModel =
                UpdateViewModel(
                    updateManager = manager,
                    updatePrefsRepository = prefs,
                    packageName = "com.nexus.stream",
                    ioDispatcher = UnconfinedTestDispatcher(testScheduler),
                )
            advanceUntilIdle()

            viewModel.effects.test {
                manager.effectsFlow.emit(UpdateEffect.Updated("1.0.1"))
                assertEquals(UpdateUiEffect.ShowUpdated("1.0.1"), awaitItem())
            }
        }

    @Test
    fun UV12_openUrlEffect_isForwarded() =
        runTest {
            val manager = FakeUpdateManager()
            val prefs = FakeUpdatePrefsRepository()

            val viewModel =
                UpdateViewModel(
                    updateManager = manager,
                    updatePrefsRepository = prefs,
                    packageName = "com.nexus.stream",
                    ioDispatcher = UnconfinedTestDispatcher(testScheduler),
                )
            advanceUntilIdle()

            val targetUrl = "https://github.com/org/repo/releases/tag/v1.0.1"
            viewModel.effects.test {
                manager.effectsFlow.emit(UpdateEffect.OpenUrl(targetUrl))
                assertEquals(UpdateUiEffect.OpenUrl(targetUrl), awaitItem())
            }
        }

    @Test
    fun UV13_permissionDialog_showsOnlyForNeedsInstallPermission_andNotNowHidesIt() =
        runTest {
            val manager = FakeUpdateManager()
            val prefs = FakeUpdatePrefsRepository()
            val candidate = sampleCandidate()

            manager.stateFlow.value = UpdateState.NeedsInstallPermission(candidate)

            val viewModel =
                UpdateViewModel(
                    updateManager = manager,
                    updatePrefsRepository = prefs,
                    packageName = "com.nexus.stream",
                    ioDispatcher = UnconfinedTestDispatcher(testScheduler),
                )
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.showPermissionDialog)

            // Dismiss dialog via Not now
            viewModel.onEvent(UpdateEvent.DismissPermissionDialog)
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.showPermissionDialog)
            // The row keeps the "Allow and install" button
            val btn =
                viewModel.uiState.value.bigButtons
                    .find { it.textResId == R.string.update_allow_install }
            assertNotNull(btn)

            // A new NeedsInstallPermission instance shows the dialog again
            manager.stateFlow.value = UpdateState.Idle(null)
            advanceUntilIdle()
            assertFalse(viewModel.uiState.value.showPermissionDialog)

            manager.stateFlow.value = UpdateState.NeedsInstallPermission(candidate)
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.showPermissionDialog)
        }

    @Test
    fun UV14_insufficientStorage_messageCarriesTheNeededBytes() =
        runTest {
            val manager = FakeUpdateManager()
            val prefs = FakeUpdatePrefsRepository()
            val size = 100L * 1024 * 1024
            val candidate = sampleCandidate(sizeBytes = size)

            manager.stateFlow.value = UpdateState.Failed(UpdateFailure.InsufficientStorage, candidate)

            val viewModel =
                UpdateViewModel(
                    updateManager = manager,
                    updatePrefsRepository = prefs,
                    packageName = "com.nexus.stream",
                    ioDispatcher = UnconfinedTestDispatcher(testScheduler),
                )
            advanceUntilIdle()

            val ui = viewModel.uiState.value
            assertEquals(R.string.update_failure_insufficient_storage, ui.status.resId)
            val expectedNeeded = 2 * size + (50L * 1024 * 1024)
            assertEquals(listOf(expectedNeeded), ui.status.formatArgs)
        }

    @Test
    fun UV15_unsupported_hasNoButtons() =
        runTest {
            val manager = FakeUpdateManager()
            val prefs = FakeUpdatePrefsRepository()
            manager.stateFlow.value = UpdateState.Unsupported

            val viewModel =
                UpdateViewModel(
                    updateManager = manager,
                    updatePrefsRepository = prefs,
                    packageName = "com.nexus.stream",
                    ioDispatcher = UnconfinedTestDispatcher(testScheduler),
                )
            advanceUntilIdle()

            val ui = viewModel.uiState.value
            assertEquals(R.string.update_unsupported, ui.status.resId)
            assertTrue(ui.bigButtons.isEmpty())
            assertFalse(ui.summaryFlag)
        }

    @Test
    fun UV16_available_withUnknownSize_hidesTheSize() =
        runTest {
            val manager = FakeUpdateManager()
            val prefs = FakeUpdatePrefsRepository()
            val candidate = sampleCandidate(sizeBytes = 0L)
            manager.stateFlow.value = UpdateState.Available(candidate)

            val viewModel =
                UpdateViewModel(
                    updateManager = manager,
                    updatePrefsRepository = prefs,
                    packageName = "com.nexus.stream",
                    ioDispatcher = UnconfinedTestDispatcher(testScheduler),
                )
            advanceUntilIdle()

            val ui = viewModel.uiState.value
            assertEquals(R.string.update_available, ui.status.resId)
            assertNull(ui.status.sizeBytes)
        }
}
