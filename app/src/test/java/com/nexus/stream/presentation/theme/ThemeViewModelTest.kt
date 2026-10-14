package com.nexus.stream.presentation.theme

import com.nexus.stream.domain.model.ThemeSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ThemeViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeSettingsRepository
    private lateinit var viewModel: ThemeViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = FakeSettingsRepository()
        viewModel = ThemeViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun currentPreview(): Long? {
        val state = viewModel.uiState.value
        return (state as? ThemeUiState.Ready)?.previewAccent
    }

    @Test
    fun previewAccent_debouncesPreviewBy150ms() =
        runTest(testDispatcher) {
            testScheduler.runCurrent()
            val a = 0xFF00FF00L
            viewModel.onEvent(ThemeEvent.PreviewAccent(a))

            testScheduler.advanceTimeBy(149)
            testScheduler.runCurrent()
            assertEquals(null, currentPreview())

            testScheduler.advanceTimeBy(1)
            testScheduler.runCurrent()
            assertEquals(a, currentPreview())
        }

    @Test
    fun previewAccent_newerChangeCancelsPending() =
        runTest(testDispatcher) {
            testScheduler.runCurrent()
            val a = 0xFF00FF00L
            val b = 0xFFFF0000L
            viewModel.onEvent(ThemeEvent.PreviewAccent(a))

            testScheduler.advanceTimeBy(100)
            testScheduler.runCurrent()
            assertEquals(null, currentPreview())

            viewModel.onEvent(ThemeEvent.PreviewAccent(b))

            testScheduler.advanceTimeBy(50)
            testScheduler.runCurrent()
            assertEquals(null, currentPreview())

            testScheduler.advanceTimeBy(100)
            testScheduler.runCurrent()
            assertEquals(b, currentPreview())
        }

    @Test
    fun previewAccent_immediateTrue_updatesWithoutAdvancingTime() =
        runTest(testDispatcher) {
            testScheduler.runCurrent()
            val a = 0xFF00FF00L
            viewModel.onEvent(ThemeEvent.PreviewAccent(a, immediate = true))
            testScheduler.runCurrent()

            assertEquals(a, currentPreview())
            assertEquals(0L, testScheduler.currentTime)
        }

    @Test
    fun previewAccent_nullClearsPendingAndStaysNull() =
        runTest(testDispatcher) {
            testScheduler.runCurrent()
            val a = 0xFF00FF00L
            viewModel.onEvent(ThemeEvent.PreviewAccent(a))

            testScheduler.advanceTimeBy(100)
            assertEquals(null, currentPreview())

            viewModel.onEvent(ThemeEvent.PreviewAccent(null))
            testScheduler.runCurrent()
            assertEquals(null, currentPreview())

            testScheduler.advanceTimeBy(1000)
            assertEquals(null, currentPreview())
        }

    @Test
    fun setAccent_clearsPreviewAtOnce_andPersistsInRepository() =
        runTest(testDispatcher) {
            testScheduler.runCurrent()
            val a = 0xFF00FF00L
            val x = 0xFF123456L
            viewModel.onEvent(ThemeEvent.PreviewAccent(a, immediate = true))
            testScheduler.runCurrent()
            assertEquals(a, currentPreview())

            viewModel.onEvent(ThemeEvent.SetAccent(x, remember = true))
            testScheduler.runCurrent()

            assertEquals(null, currentPreview())
            assertEquals(x, repository.themeSettings.value.accentColor)
        }

    @Test
    fun resetAppearance_clearsPendingPreview_andRestoresDefaults() =
        runTest(testDispatcher) {
            testScheduler.runCurrent()
            val a = 0xFF00FF00L
            viewModel.onEvent(ThemeEvent.PreviewAccent(a))

            testScheduler.advanceTimeBy(100)
            assertEquals(null, currentPreview())

            viewModel.onEvent(ThemeEvent.ResetAppearance)
            testScheduler.runCurrent()

            assertEquals(null, currentPreview())
            testScheduler.advanceTimeBy(1000)
            assertEquals(null, currentPreview())
            assertEquals(ThemeSettings(), repository.themeSettings.value)
        }
}
