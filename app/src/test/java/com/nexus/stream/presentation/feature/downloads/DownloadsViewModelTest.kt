package com.nexus.stream.presentation.feature.downloads

import app.cash.turbine.test
import com.nexus.stream.R
import com.nexus.stream.data.download.FakeContentPrefsRepository
import com.nexus.stream.data.download.FakeDownloadManager
import com.nexus.stream.data.download.createTestDownloadItem
import com.nexus.stream.domain.model.DownloadEffect
import com.nexus.stream.domain.model.DownloadFailure
import com.nexus.stream.domain.model.DownloadLive
import com.nexus.stream.domain.model.DownloadState
import com.nexus.stream.domain.repository.TorrentFileChoice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("LargeClass")
class DownloadsViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var downloadManager: FakeDownloadManager
    private lateinit var contentPrefsRepository: FakeContentPrefsRepository
    private var freeSpace = 41L * 1024 * 1024 * 1024
    private var notificationGranted = true
    private lateinit var viewModel: DownloadsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        downloadManager = FakeDownloadManager()
        contentPrefsRepository = FakeContentPrefsRepository()
        freeSpace = 41L * 1024 * 1024 * 1024
        notificationGranted = true
        viewModel =
            DownloadsViewModel(
                downloadManager = downloadManager,
                contentPrefsRepository = contentPrefsRepository,
                freeSpaceProvider = { freeSpace },
                notificationPermissionGranted = { notificationGranted },
            )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun DW1_state_groupsItems_andShowsStorage() =
        runTest {
            val item1 = createTestDownloadItem("1", state = DownloadState.DOWNLOADING)
            val item2 = createTestDownloadItem("2", state = DownloadState.FAILED)
            val item3 = createTestDownloadItem("3", state = DownloadState.COMPLETED)
            downloadManager.itemsFlow.value = listOf(item1, item2, item3)

            val state = viewModel.uiState.value
            assertEquals(3, state.sections.size)
            assertNotNull(state.storageSummary)
            assertFalse(state.isEmpty)
        }

    @Test
    fun DW2_noItems_showsEmptyAndNoStorageCard() =
        runTest {
            downloadManager.itemsFlow.value = emptyList()

            val state = viewModel.uiState.value
            assertTrue(state.isEmpty)
            assertNull(state.storageSummary)
        }

    @Test
    fun DW3_rowActions_callTheManager() =
        runTest {
            viewModel.onEvent(DownloadsEvent.Pause("id1"))
            assertEquals(listOf("id1"), downloadManager.pauseCalls)

            viewModel.onEvent(DownloadsEvent.Resume("id2"))
            assertEquals(listOf("id2"), downloadManager.resumeCalls)

            viewModel.onEvent(DownloadsEvent.Retry("id3"))
            assertEquals(listOf("id2", "id3"), downloadManager.resumeCalls)
        }

    @Test
    fun DW4_requestCancel_showsConfirm_thenCancels() =
        runTest {
            viewModel.onEvent(DownloadsEvent.RequestCancel("id1"))
            assertEquals(ConfirmDialog.Cancel("id1"), viewModel.uiState.value.confirmDialog)

            viewModel.onEvent(DownloadsEvent.ConfirmCancel("id1"))
            assertEquals(listOf("id1"), downloadManager.cancelCalls)
            assertNull(viewModel.uiState.value.confirmDialog)
        }

    @Test
    fun DW5_requestDelete_showsConfirm_thenDeletes() =
        runTest {
            viewModel.onEvent(DownloadsEvent.RequestDelete("id1"))
            assertEquals(ConfirmDialog.Delete("id1"), viewModel.uiState.value.confirmDialog)

            viewModel.onEvent(DownloadsEvent.ConfirmDelete("id1"))
            assertEquals(listOf("id1"), downloadManager.deleteCalls)
            assertNull(viewModel.uiState.value.confirmDialog)
        }

    @Test
    fun DW6_dismissConfirm_doesNothing() =
        runTest {
            viewModel.onEvent(DownloadsEvent.RequestCancel("id1"))
            assertNotNull(viewModel.uiState.value.confirmDialog)

            viewModel.onEvent(DownloadsEvent.DismissConfirm)
            assertNull(viewModel.uiState.value.confirmDialog)
            assertTrue(downloadManager.cancelCalls.isEmpty())
        }

    @Test
    fun DW7_deleteAll_confirmsThenDeletesCompleted() =
        runTest {
            viewModel.onEvent(DownloadsEvent.RequestDeleteAll)
            assertEquals(ConfirmDialog.DeleteAll, viewModel.uiState.value.confirmDialog)

            viewModel.onEvent(DownloadsEvent.ConfirmDeleteAll)
            assertEquals(1, downloadManager.deleteCompletedCalls)
            assertNull(viewModel.uiState.value.confirmDialog)
        }

    @Test
    fun DW8_waitingForWifi_showsBanner_andUseMobileDataCallsTheManager() =
        runTest {
            val item = createTestDownloadItem("1", state = DownloadState.WAITING_FOR_WIFI)
            downloadManager.itemsFlow.value = listOf(item)

            assertTrue(viewModel.uiState.value.showWifiBanner)

            viewModel.onEvent(DownloadsEvent.UseMobileData)
            assertEquals(listOf<String?>(null), downloadManager.allowMobileDataCalls)

            downloadManager.itemsFlow.value = emptyList()
            assertFalse(viewModel.uiState.value.showWifiBanner)
        }

    @Test
    fun DW9_playCompleted_verifiesTheFile_thenEmitsPlay() =
        runTest {
            val item = createTestDownloadItem("1", state = DownloadState.COMPLETED)
            downloadManager.verifyCompletedResult = { true }

            viewModel.effects.test {
                viewModel.onEvent(DownloadsEvent.Play(item))
                val effect = awaitItem()
                assertEquals(DownloadsEffect.Play(item), effect)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun DW10_playCompleted_missingFile_emitsMessage_andNoPlay() =
        runTest {
            val item = createTestDownloadItem("1", state = DownloadState.COMPLETED)
            downloadManager.verifyCompletedResult = { false }

            viewModel.effects.test {
                viewModel.onEvent(DownloadsEvent.Play(item))
                val effect = awaitItem()
                assertTrue(effect is DownloadsEffect.ShowMessage)
                assertEquals(R.string.download_file_missing, (effect as DownloadsEffect.ShowMessage).messageRes)
                expectNoEvents()
            }
        }

    @Test
    fun DW11_tapInProgressRow_opensDetail() =
        runTest {
            val itemInProgress = createTestDownloadItem("1", state = DownloadState.DOWNLOADING)
            viewModel.effects.test {
                viewModel.onEvent(DownloadsEvent.TapRow(itemInProgress))
                val effect = awaitItem()
                assertEquals(DownloadsEffect.OpenDetail(itemInProgress.type, itemInProgress.metaId), effect)

                val itemCompleted = createTestDownloadItem("2", state = DownloadState.COMPLETED)
                downloadManager.verifyCompletedResult = { true }
                viewModel.onEvent(DownloadsEvent.TapRow(itemCompleted))
                val playEffect = awaitItem()
                assertEquals(DownloadsEffect.Play(itemCompleted), playEffect)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun DW12_findNewLink_opensTheDownloadSheetForThatItem() =
        runTest {
            val item =
                createTestDownloadItem("1", state = DownloadState.FAILED).copy(
                    failure = DownloadFailure.LINK_EXPIRED,
                )

            viewModel.effects.test {
                viewModel.onEvent(DownloadsEvent.FindNewLink(item))
                val effect = awaitItem()
                assertTrue(effect is DownloadsEffect.OpenDownloadSheet)
                val sheet = effect as DownloadsEffect.OpenDownloadSheet
                assertEquals("1", sheet.replaceDownloadId)
                assertEquals(item.videoId, sheet.request.videoId)
                assertTrue(sheet.request.downloadMode)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun DW13_notificationsOff_bannerOnlyWhenSomethingIsActive() =
        runTest {
            notificationGranted = false
            viewModel =
                DownloadsViewModel(
                    downloadManager = downloadManager,
                    contentPrefsRepository = contentPrefsRepository,
                    freeSpaceProvider = { freeSpace },
                    notificationPermissionGranted = { notificationGranted },
                )

            val item = createTestDownloadItem("1", state = DownloadState.DOWNLOADING)
            downloadManager.itemsFlow.value = listOf(item)
            assertTrue(viewModel.uiState.value.showNotificationsBanner)

            downloadManager.itemsFlow.value = emptyList()
            assertFalse(viewModel.uiState.value.showNotificationsBanner)

            notificationGranted = true
            downloadManager.itemsFlow.value = listOf(item)
            assertFalse(viewModel.uiState.value.showNotificationsBanner)
        }

    @Test
    fun DW14_dismissBanner_hidesItForTheSession() =
        runTest {
            notificationGranted = false
            viewModel =
                DownloadsViewModel(
                    downloadManager = downloadManager,
                    contentPrefsRepository = contentPrefsRepository,
                    freeSpaceProvider = { freeSpace },
                    notificationPermissionGranted = { notificationGranted },
                )

            val item = createTestDownloadItem("1", state = DownloadState.DOWNLOADING)
            downloadManager.itemsFlow.value = listOf(item)
            assertTrue(viewModel.uiState.value.showNotificationsBanner)

            viewModel.onEvent(DownloadsEvent.DismissNotificationsBanner)
            assertFalse(viewModel.uiState.value.showNotificationsBanner)

            downloadManager.itemsFlow.value = listOf(item, createTestDownloadItem("2", state = DownloadState.QUEUED))
            assertFalse(viewModel.uiState.value.showNotificationsBanner)
        }

    @Test
    fun DW15_familyMode_hidesAdultNames() =
        runTest {
            contentPrefsRepository.familyModeFlow.value = true
            val normal = createTestDownloadItem("1", state = DownloadState.DOWNLOADING).copy(title = "Dune")
            val adult =
                createTestDownloadItem("2", state = DownloadState.DOWNLOADING).copy(
                    title = "Hot XXX Video",
                    downloadedBytes = 500L,
                )
            downloadManager.itemsFlow.value = listOf(normal, adult)

            val state = viewModel.uiState.value
            val allItems = state.sections.flatMap { it.items }
            assertEquals(1, allItems.size)
            assertEquals("Dune", allItems[0].title)
            assertEquals(500L, state.storageSummary?.usedBytes)
        }

    @Test
    fun DW16_liveProgress_updatesRowsWithoutReorderingSections() =
        runTest {
            val item =
                createTestDownloadItem("1", state = DownloadState.DOWNLOADING).copy(
                    downloadedBytes = 100L,
                    totalBytes = 1000L,
                )
            downloadManager.itemsFlow.value = listOf(item)
            val initialStatus = viewModel.uiState.value.rowStatus(item)

            downloadManager.liveFlow.value =
                mapOf("1" to DownloadLive(bytes = 100L, speedBps = 500L, etaSeconds = 2L))
            val updatedStatus = viewModel.uiState.value.rowStatus(item)

            assertFalse(initialStatus == updatedStatus)
            assertEquals(1, viewModel.uiState.value.sections.size)
        }

    @Test
    fun DW17_managerEffects_areForwardedAsMessages() =
        runTest {
            viewModel.effects.test {
                downloadManager.effectsFlow.emit(DownloadEffect.Completed("1"))
                val effect1 = awaitItem()
                assertTrue(effect1 is DownloadsEffect.ShowMessage)
                assertEquals(R.string.download_event_done_title, (effect1 as DownloadsEffect.ShowMessage).messageRes)

                downloadManager.effectsFlow.emit(DownloadEffect.Failed("1", DownloadFailure.LINK_EXPIRED))
                val effect2 = awaitItem()
                assertTrue(effect2 is DownloadsEffect.ShowMessage)
                assertEquals(
                    R.string.download_failure_link_expired,
                    (effect2 as DownloadsEffect.ShowMessage).messageRes,
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun DW18_lowStorage_flagsTheWarning() =
        runTest {
            val item = createTestDownloadItem("1", state = DownloadState.COMPLETED)
            downloadManager.itemsFlow.value = listOf(item)

            freeSpace = 500L * 1024 * 1024
            downloadManager.itemsFlow.value = listOf(item.copy(title = "Refreshed"))
            assertTrue(
                viewModel.uiState.value.storageSummary
                    ?.low == true,
            )

            freeSpace = 5L * 1024 * 1024 * 1024
            downloadManager.itemsFlow.value = listOf(item.copy(title = "Refreshed2"))
            assertFalse(
                viewModel.uiState.value.storageSummary
                    ?.low == true,
            )
        }

    @Test
    fun DW19_pauseAllResumeAll_followTheStates() =
        runTest {
            val itemDownloading = createTestDownloadItem("1", state = DownloadState.DOWNLOADING)
            downloadManager.itemsFlow.value = listOf(itemDownloading)

            assertTrue(viewModel.uiState.value.canPauseAll)
            assertFalse(viewModel.uiState.value.canResumeAll)

            viewModel.onEvent(DownloadsEvent.PauseAll)
            assertEquals(1, downloadManager.pauseAllCalls)

            val itemPaused = createTestDownloadItem("1", state = DownloadState.PAUSED)
            downloadManager.itemsFlow.value = listOf(itemPaused)

            assertFalse(viewModel.uiState.value.canPauseAll)
            assertTrue(viewModel.uiState.value.canResumeAll)

            viewModel.onEvent(DownloadsEvent.ResumeAll)
            assertEquals(1, downloadManager.resumeAllCalls)
        }

    @Test
    fun DW20_needsFilePick_exposesTheChoicesOnTap() =
        runTest {
            val item = createTestDownloadItem("t1", state = DownloadState.NEEDS_FILE_PICK)
            downloadManager.itemsFlow.value = listOf(item)
            val c1 = TorrentFileChoice(index = 0, name = "Sample.mkv", sizeBytes = 50_000_000L)
            val c2 = TorrentFileChoice(index = 1, name = "Movie.mkv", sizeBytes = 2_000_000_000L)
            val c3 = TorrentFileChoice(index = 2, name = "Extra.mkv", sizeBytes = 500_000_000L)
            downloadManager.fileChoicesMap["t1"] = listOf(c1, c2, c3)

            viewModel.onEvent(DownloadsEvent.ChooseFile("t1"))

            val dialog = viewModel.uiState.value.filePickDialog
            assertNotNull(dialog)
            assertEquals("t1", dialog?.downloadId)
            assertEquals(listOf(c2, c3, c1), dialog?.candidates)
        }

    @Test
    fun DW21_selectFile_callsTheManager() =
        runTest {
            val item = createTestDownloadItem("t1", state = DownloadState.NEEDS_FILE_PICK)
            downloadManager.itemsFlow.value = listOf(item)
            val c1 = TorrentFileChoice(index = 0, name = "Movie.mkv", sizeBytes = 2_000_000_000L)
            downloadManager.fileChoicesMap["t1"] = listOf(c1)

            viewModel.onEvent(DownloadsEvent.ChooseFile("t1"))
            assertNotNull(viewModel.uiState.value.filePickDialog)

            viewModel.onEvent(DownloadsEvent.SelectFile(fileIndex = 0))

            assertEquals(listOf("t1" to 0), downloadManager.selectTorrentFileCalls)
            assertNull(viewModel.uiState.value.filePickDialog)
        }

    @Test
    fun DW22_dismissPick_keepsTheItemWaiting() =
        runTest {
            val item = createTestDownloadItem("t1", state = DownloadState.NEEDS_FILE_PICK)
            downloadManager.itemsFlow.value = listOf(item)
            val c1 = TorrentFileChoice(index = 0, name = "Movie.mkv", sizeBytes = 2_000_000_000L)
            downloadManager.fileChoicesMap["t1"] = listOf(c1)

            viewModel.onEvent(DownloadsEvent.ChooseFile("t1"))
            assertNotNull(viewModel.uiState.value.filePickDialog)

            viewModel.onEvent(DownloadsEvent.DismissPick)

            assertTrue(downloadManager.selectTorrentFileCalls.isEmpty())
            assertNull(viewModel.uiState.value.filePickDialog)
            assertEquals(
                DownloadState.NEEDS_FILE_PICK,
                viewModel.uiState.value.sections
                    .first()
                    .items
                    .first()
                    .state,
            )
        }
}
