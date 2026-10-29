package com.nexus.stream.presentation.feature.settings

import app.cash.turbine.test
import com.nexus.stream.domain.model.GITHUB_ISSUES_URL
import com.nexus.stream.domain.model.GITHUB_URL
import com.nexus.stream.domain.repository.AppInfoProvider
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class AboutViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val appInfoProvider = mockk<AppInfoProvider>()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { appInfoProvider.versionName } returns "1.0.0"
        every { appInfoProvider.versionCode } returns 10000L
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun BV1_state_hasTheVersionFromTheProvider() =
        runTest {
            val viewModel = AboutViewModel(appInfoProvider)
            advanceUntilIdle()

            assertEquals("1.0.0 (10000)", viewModel.uiState.value.versionText)
        }

    @Test
    fun BV2_openGithub_emitsOpenUrl_withTheRepoUrl() =
        runTest {
            val viewModel = AboutViewModel(appInfoProvider)
            advanceUntilIdle()

            viewModel.effects.test {
                viewModel.onEvent(AboutEvent.OpenGithub)
                assertEquals(AboutEffect.OpenUrl(GITHUB_URL), awaitItem())
            }
        }

    @Test
    fun BV3_openIssues_emitsOpenUrl_withTheIssuesUrl() =
        runTest {
            val viewModel = AboutViewModel(appInfoProvider)
            advanceUntilIdle()

            viewModel.effects.test {
                viewModel.onEvent(AboutEvent.OpenIssues)
                assertEquals(AboutEffect.OpenUrl(GITHUB_ISSUES_URL), awaitItem())
            }
        }

    @Test
    fun BV4_licenses_showAndHide() =
        runTest {
            val viewModel = AboutViewModel(appInfoProvider)
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.showLicenses)

            viewModel.onEvent(AboutEvent.OpenLicenses)
            assertTrue(viewModel.uiState.value.showLicenses)

            viewModel.onEvent(AboutEvent.DismissLicenses)
            assertFalse(viewModel.uiState.value.showLicenses)
        }
}
