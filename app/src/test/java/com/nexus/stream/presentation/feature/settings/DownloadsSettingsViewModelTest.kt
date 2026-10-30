package com.nexus.stream.presentation.feature.settings

import app.cash.turbine.test
import com.nexus.stream.data.download.FakeDownloadPrefsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadsSettingsViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var prefsRepository: FakeDownloadPrefsRepository
    private lateinit var viewModel: DownloadsSettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        prefsRepository = FakeDownloadPrefsRepository()
        viewModel = DownloadsSettingsViewModel(prefsRepository)
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
}
