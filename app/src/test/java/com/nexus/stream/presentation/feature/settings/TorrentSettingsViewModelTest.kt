package com.nexus.stream.presentation.feature.settings

import app.cash.turbine.test
import com.nexus.stream.domain.repository.TorrentCache
import com.nexus.stream.domain.repository.TorrentPrefsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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
class TorrentSettingsViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val torrentPrefsRepository = mockk<TorrentPrefsRepository>(relaxed = true)
    private val torrentCache = mockk<TorrentCache>(relaxed = true)

    private val wifiOnlyFlow = MutableStateFlow(true)
    private val cacheLimitGbFlow = MutableStateFlow(2)
    private val clearCacheOnExitFlow = MutableStateFlow(true)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { torrentPrefsRepository.wifiOnly } returns wifiOnlyFlow
        every { torrentPrefsRepository.cacheLimitGb } returns cacheLimitGbFlow
        every { torrentPrefsRepository.clearCacheOnExit } returns clearCacheOnExitFlow

        coEvery { torrentPrefsRepository.setWifiOnly(any()) } answers {
            wifiOnlyFlow.value = firstArg()
        }
        coEvery { torrentPrefsRepository.setCacheLimitGb(any()) } answers {
            cacheLimitGbFlow.value = firstArg()
        }
        coEvery { torrentPrefsRepository.setClearCacheOnExit(any()) } answers {
            clearCacheOnExitFlow.value = firstArg()
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun TV1_state_mirrorsPrefs() =
        runTest {
            wifiOnlyFlow.value = true
            cacheLimitGbFlow.value = 2
            clearCacheOnExitFlow.value = true

            val viewModel = TorrentSettingsViewModel(torrentPrefsRepository, torrentCache, testDispatcher)
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.wifiOnly)
            assertEquals(2, viewModel.uiState.value.cacheLimitGb)
            assertTrue(viewModel.uiState.value.clearCacheOnExit)
        }

    @Test
    fun TV2_setWifiOnly_callsRepository() =
        runTest {
            val viewModel = TorrentSettingsViewModel(torrentPrefsRepository, torrentCache, testDispatcher)
            advanceUntilIdle()

            viewModel.onEvent(TorrentSettingsEvent.SetWifiOnly(false))
            advanceUntilIdle()

            coVerify(exactly = 1) { torrentPrefsRepository.setWifiOnly(false) }
            assertFalse(viewModel.uiState.value.wifiOnly)
        }

    @Test
    fun TV3_setClearOnExit_callsRepository() =
        runTest {
            val viewModel = TorrentSettingsViewModel(torrentPrefsRepository, torrentCache, testDispatcher)
            advanceUntilIdle()

            viewModel.onEvent(TorrentSettingsEvent.SetClearOnExit(false))
            advanceUntilIdle()

            coVerify(exactly = 1) { torrentPrefsRepository.setClearCacheOnExit(false) }
            assertFalse(viewModel.uiState.value.clearCacheOnExit)
        }

    @Test
    fun TV4_setCacheLimit_callsRepository_withAListedValue() =
        runTest {
            val viewModel = TorrentSettingsViewModel(torrentPrefsRepository, torrentCache, testDispatcher)
            advanceUntilIdle()

            viewModel.onEvent(TorrentSettingsEvent.SetCacheLimit(5))
            advanceUntilIdle()

            coVerify(exactly = 1) { torrentPrefsRepository.setCacheLimitGb(5) }
            assertEquals(5, viewModel.uiState.value.cacheLimitGb)
        }

    @Test
    fun TV5_limitOptions_includeTheCurrentValueWhenCustom() =
        runTest {
            cacheLimitGbFlow.value = 3
            val viewModel = TorrentSettingsViewModel(torrentPrefsRepository, torrentCache, testDispatcher)
            advanceUntilIdle()

            assertEquals(listOf(1, 2, 3, 5, 10, 20), viewModel.uiState.value.limitOptions)

            cacheLimitGbFlow.value = 2
            advanceUntilIdle()
            assertEquals(listOf(1, 2, 5, 10, 20), viewModel.uiState.value.limitOptions)
        }

    @Test
    fun TV6_sectionOpened_loadsTheCacheSize() =
        runTest {
            val bytes = 412L * 1024L * 1024L
            coEvery { torrentCache.cacheSizeBytes() } returns bytes

            val viewModel = TorrentSettingsViewModel(torrentPrefsRepository, torrentCache, testDispatcher)
            advanceUntilIdle()

            viewModel.onEvent(TorrentSettingsEvent.SectionOpened)
            advanceUntilIdle()

            assertEquals(CacheSizeState.Ready(bytes), viewModel.uiState.value.cacheSize)
            coVerify(exactly = 1) { torrentCache.cacheSizeBytes() }
        }

    @Test
    fun TV7_cacheSizeFailure_showsUnknown() =
        runTest {
            coEvery { torrentCache.cacheSizeBytes() } throws RuntimeException("IO error")

            val viewModel = TorrentSettingsViewModel(torrentPrefsRepository, torrentCache, testDispatcher)
            advanceUntilIdle()

            viewModel.onEvent(TorrentSettingsEvent.SectionOpened)
            advanceUntilIdle()

            assertEquals(CacheSizeState.Unknown, viewModel.uiState.value.cacheSize)
            assertTrue(viewModel.uiState.value.wifiOnly)
        }

    @Test
    fun TV8_requestClear_showsConfirm_thenClears_andReloadsSize() =
        runTest {
            val bytes = 412L * 1024L * 1024L
            coEvery { torrentCache.cacheSizeBytes() } returnsMany listOf(bytes, 0L)
            coEvery { torrentCache.clearCache() } returns Unit

            val viewModel = TorrentSettingsViewModel(torrentPrefsRepository, torrentCache, testDispatcher)
            advanceUntilIdle()

            viewModel.onEvent(TorrentSettingsEvent.SectionOpened)
            advanceUntilIdle()
            assertEquals(CacheSizeState.Ready(bytes), viewModel.uiState.value.cacheSize)

            viewModel.effects.test {
                viewModel.onEvent(TorrentSettingsEvent.RequestClear)
                assertTrue(viewModel.uiState.value.showClearConfirm)

                viewModel.onEvent(TorrentSettingsEvent.ConfirmClear)
                advanceUntilIdle()

                coVerify(exactly = 1) { torrentCache.clearCache() }
                assertEquals(CacheSizeState.Ready(0L), viewModel.uiState.value.cacheSize)
                assertEquals(TorrentSettingsEffect.CacheCleared, awaitItem())
                assertFalse(viewModel.uiState.value.showClearConfirm)
            }
        }

    @Test
    fun TV9_dismissClear_doesNothing() =
        runTest {
            val viewModel = TorrentSettingsViewModel(torrentPrefsRepository, torrentCache, testDispatcher)
            advanceUntilIdle()

            viewModel.onEvent(TorrentSettingsEvent.RequestClear)
            assertTrue(viewModel.uiState.value.showClearConfirm)

            viewModel.onEvent(TorrentSettingsEvent.DismissClear)
            assertFalse(viewModel.uiState.value.showClearConfirm)
            coVerify(exactly = 0) { torrentCache.clearCache() }
        }

    @Test
    fun TV10_clearTwice_isSingleFlight() =
        runTest {
            val deferred = CompletableDeferred<Unit>()
            coEvery { torrentCache.clearCache() } coAnswers { deferred.await() }
            coEvery { torrentCache.cacheSizeBytes() } returns 0L

            val viewModel = TorrentSettingsViewModel(torrentPrefsRepository, torrentCache, testDispatcher)
            advanceUntilIdle()

            viewModel.onEvent(TorrentSettingsEvent.ConfirmClear)
            testDispatcher.scheduler.runCurrent()

            assertTrue(viewModel.uiState.value.isClearing)

            // Second trigger while in progress
            viewModel.onEvent(TorrentSettingsEvent.ConfirmClear)
            testDispatcher.scheduler.runCurrent()

            coVerify(exactly = 1) { torrentCache.clearCache() }

            deferred.complete(Unit)
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.isClearing)
        }

    @Test
    fun TV11_clearFailure_keepsSize_andEmitsClearFailed() =
        runTest {
            val bytes = 412L * 1024L * 1024L
            coEvery { torrentCache.cacheSizeBytes() } returns bytes
            coEvery { torrentCache.clearCache() } throws RuntimeException("Disk failed")

            val viewModel = TorrentSettingsViewModel(torrentPrefsRepository, torrentCache, testDispatcher)
            advanceUntilIdle()

            viewModel.onEvent(TorrentSettingsEvent.SectionOpened)
            advanceUntilIdle()

            viewModel.effects.test {
                viewModel.onEvent(TorrentSettingsEvent.ConfirmClear)
                advanceUntilIdle()

                assertEquals(CacheSizeState.Ready(bytes), viewModel.uiState.value.cacheSize)
                assertEquals(TorrentSettingsEffect.ClearFailed, awaitItem())
                assertFalse(viewModel.uiState.value.isClearing)
            }
        }
}
