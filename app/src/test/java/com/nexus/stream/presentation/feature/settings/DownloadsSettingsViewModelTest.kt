package com.nexus.stream.presentation.feature.settings

import app.cash.turbine.test
import com.nexus.stream.data.download.FakeDownloadManager
import com.nexus.stream.data.download.FakeDownloadPrefsRepository
import com.nexus.stream.data.download.createTestDownloadItem
import com.nexus.stream.domain.model.DownloadState
import com.nexus.stream.domain.model.GalleryState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadsSettingsViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var prefsRepository: FakeDownloadPrefsRepository
    private lateinit var downloadManager: FakeDownloadManager
    private lateinit var viewModel: DownloadsSettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        prefsRepository = FakeDownloadPrefsRepository()
        downloadManager = FakeDownloadManager()
        viewModel = DownloadsSettingsViewModel(prefsRepository, downloadManager)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun DZ1_state_mirrorsThePrefs() =
        runTest {
            viewModel.uiState.test {
                val state = awaitItem()
                assertEquals(true, state.wifiOnly)
                assertEquals(2, state.maxConcurrent)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun DZ2_setWifiOnly_callsTheRepository() =
        runTest {
            viewModel.onEvent(DownloadsSettingsEvent.SetWifiOnly(false))
            assertEquals(false, prefsRepository.wifiOnly.value)
        }

    @Test
    fun DZ3_setMaxConcurrent_callsTheRepository() =
        runTest {
            viewModel.onEvent(DownloadsSettingsEvent.SetMaxConcurrent(3))
            assertEquals(3, prefsRepository.maxConcurrent.value)
        }

    @Test
    fun DZ4_summary_followsTheValues() =
        runTest {
            assertEquals("Wi-Fi only · 2 at a time", downloadsSummary(wifiOnly = true, maxConcurrent = 2))
            assertEquals("Any network · 3 at a time", downloadsSummary(wifiOnly = false, maxConcurrent = 3))
        }

    @Test
    fun DZ5_state_includesSaveToGalleryAndTheUnsavedCount() =
        runTest {
            prefsRepository.setSaveToGallery(true)
            val item1 =
                createTestDownloadItem("c1", state = DownloadState.COMPLETED)
                    .copy(galleryState = GalleryState.SAVED)
            val item2 =
                createTestDownloadItem("c2", state = DownloadState.COMPLETED)
                    .copy(galleryState = GalleryState.NONE)
            val item3 =
                createTestDownloadItem("c3", state = DownloadState.COMPLETED)
                    .copy(galleryState = GalleryState.FAILED)
            downloadManager.itemsFlow.value = listOf(item1, item2, item3)

            viewModel.uiState.test {
                val state = awaitItem()
                assertTrue(state.saveToGallery)
                assertEquals(2, state.unsavedCount)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun DZ6_setSaveToGallery_callsThePrefs() =
        runTest {
            viewModel.onEvent(DownloadsSettingsEvent.SetSaveToGallery(true))
            assertEquals(true, prefsRepository.saveToGallery.value)
        }

    @Test
    fun DZ7_turnOn_withUnsavedDownloads_offersSavingThem() =
        runTest {
            val item1 = createTestDownloadItem("c1", state = DownloadState.COMPLETED)
            val item2 = createTestDownloadItem("c2", state = DownloadState.COMPLETED)
            downloadManager.itemsFlow.value = listOf(item1, item2)

            viewModel.onEvent(DownloadsSettingsEvent.SetSaveToGallery(true))

            viewModel.uiState.test {
                val state = awaitItem()
                assertTrue(state.showSaveExistingOffer)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun DZ8_saveExisting_callsTheManager() =
        runTest {
            val item = createTestDownloadItem("c1", state = DownloadState.COMPLETED)
            downloadManager.itemsFlow.value = listOf(item)
            viewModel.onEvent(DownloadsSettingsEvent.SetSaveToGallery(true))

            viewModel.onEvent(DownloadsSettingsEvent.SaveExisting)
            assertEquals(1, downloadManager.exportAllToGalleryCalls)
            viewModel.uiState.test {
                val state = awaitItem()
                assertFalse(state.showSaveExistingOffer)
                cancelAndIgnoreRemainingEvents()
            }

            // Second run with DeclineOffer
            viewModel.onEvent(DownloadsSettingsEvent.SetSaveToGallery(true))
            viewModel.onEvent(DownloadsSettingsEvent.DeclineOffer)
            assertEquals(1, downloadManager.exportAllToGalleryCalls)
            viewModel.uiState.test {
                val state = awaitItem()
                assertFalse(state.showSaveExistingOffer)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun DZ9_turnOn_withNothingToSave_showsNoOffer() =
        runTest {
            val item =
                createTestDownloadItem("c1", state = DownloadState.COMPLETED)
                    .copy(galleryState = GalleryState.SAVED)
            downloadManager.itemsFlow.value = listOf(item)

            viewModel.onEvent(DownloadsSettingsEvent.SetSaveToGallery(true))

            viewModel.uiState.test {
                val state = awaitItem()
                assertFalse(state.showSaveExistingOffer)
                cancelAndIgnoreRemainingEvents()
            }
        }
}
