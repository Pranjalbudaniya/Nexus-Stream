package com.nexus.stream.dev

import androidx.lifecycle.ViewModel
import com.nexus.stream.domain.repository.ProxySession
import com.nexus.stream.domain.repository.StreamProxy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
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
class ProxyTesterViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val fakeProxy = FakeStreamProxy()
    private lateinit var viewModel: ProxyTesterViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = ProxyTesterViewModel(fakeProxy)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun PV1_parsingHeadersText_validLinesAndIgnoredCount() =
        runTest(testDispatcher) {
            val headersInput =
                """
                X-Test: abc
                Referer: https://example.org/
                User-Agent: TestAgent/1
                ignored line without colon
                : empty header name
                
                """.trimIndent()

            val (parsedDirect, ignoredDirect) = parseHeadersText(headersInput)
            assertEquals(3, parsedDirect.size)
            assertEquals("abc", parsedDirect["X-Test"])
            assertEquals("https://example.org/", parsedDirect["Referer"])
            assertEquals("TestAgent/1", parsedDirect["User-Agent"])
            assertEquals(2, ignoredDirect)

            viewModel.onEvent(ProxyTesterEvent.HeadersChanged(headersInput))
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(3, state.parsedHeaderCount)
            assertEquals(2, state.ignoredLineCount)
            assertEquals(headersInput, state.headersText)
        }

    @Test
    fun PV2_openInBrowser_emitsOpenUrlFalse_uiStateToStringContainsNoUrlTokenOrHeader() =
        runTest(testDispatcher) {
            val effects = mutableListOf<ProxyTesterEffect>()
            val effectsJob =
                launch {
                    viewModel.effects.collect { effects.add(it) }
                }

            val targetUrl = "https://upstream.example.com/movie.mp4"
            val headersInput = "X-Secret: mySecretToken\nCookie: session=12345"
            val sessionProxyUrl = "http://127.0.0.1:45678/p/abc123token/stream.mp4"

            fakeProxy.nextSessionUrl = sessionProxyUrl
            viewModel.onEvent(ProxyTesterEvent.UrlChanged(targetUrl))
            viewModel.onEvent(ProxyTesterEvent.HeadersChanged(headersInput))
            viewModel.onEvent(ProxyTesterEvent.OpenInBrowser)
            advanceUntilIdle()

            assertEquals(1, effects.size)
            assertEquals(ProxyTesterEffect.OpenUrl(sessionProxyUrl, asVideo = false), effects[0])
            assertTrue(viewModel.uiState.value.isSessionActive)
            assertEquals("Active", viewModel.uiState.value.statusText)

            val stateString = viewModel.uiState.value.toString()
            assertFalse(stateString.contains("upstream.example.com"))
            assertFalse(stateString.contains("mySecretToken"))
            assertFalse(stateString.contains("session=12345"))
            assertFalse(stateString.contains("abc123token"))
            assertFalse(stateString.contains("45678"))
            assertFalse(stateString.contains("http://"))

            effectsJob.cancel()
        }

    @Test
    fun PV3_playInOtherApp_emitsOpenUrlTrue() =
        runTest(testDispatcher) {
            val effects = mutableListOf<ProxyTesterEffect>()
            val effectsJob =
                launch {
                    viewModel.effects.collect { effects.add(it) }
                }

            val targetUrl = "https://upstream.example.com/movie.mp4"
            val sessionProxyUrl = "http://127.0.0.1:45678/p/abc123token/stream.mp4"

            fakeProxy.nextSessionUrl = sessionProxyUrl
            viewModel.onEvent(ProxyTesterEvent.UrlChanged(targetUrl))
            viewModel.onEvent(ProxyTesterEvent.PlayInOtherApp)
            advanceUntilIdle()

            assertEquals(1, effects.size)
            assertEquals(ProxyTesterEffect.OpenUrl(sessionProxyUrl, asVideo = true), effects[0])

            effectsJob.cancel()
        }

    @Test
    fun PV4_stopClosesSession_recordedByFakeProxy() =
        runTest(testDispatcher) {
            viewModel.onEvent(ProxyTesterEvent.UrlChanged("https://example.com/video.mp4"))
            viewModel.onEvent(ProxyTesterEvent.OpenInBrowser)
            advanceUntilIdle()

            assertEquals(0, fakeProxy.closedCount)
            assertTrue(viewModel.uiState.value.isSessionActive)

            viewModel.onEvent(ProxyTesterEvent.Stop)
            advanceUntilIdle()

            assertEquals(1, fakeProxy.closedCount)
            assertFalse(viewModel.uiState.value.isSessionActive)
            assertEquals("Stopped", viewModel.uiState.value.statusText)
        }

    @Test
    fun PV5_openingAgainClosesPreviousSessionFirst() =
        runTest(testDispatcher) {
            viewModel.onEvent(ProxyTesterEvent.UrlChanged("https://example.com/video1.mp4"))
            viewModel.onEvent(ProxyTesterEvent.OpenInBrowser)
            advanceUntilIdle()

            assertEquals(0, fakeProxy.closedCount)
            assertEquals(1, fakeProxy.openCalls.size)

            viewModel.onEvent(ProxyTesterEvent.UrlChanged("https://example.com/video2.mp4"))
            viewModel.onEvent(ProxyTesterEvent.PlayInOtherApp)
            advanceUntilIdle()

            assertEquals(1, fakeProxy.closedCount)
            assertEquals(2, fakeProxy.openCalls.size)
        }

    @Test
    fun PV6_invalidUrl_errorStatusNoSessionNoEffect() =
        runTest(testDispatcher) {
            val effects = mutableListOf<ProxyTesterEffect>()
            val effectsJob =
                launch {
                    viewModel.effects.collect { effects.add(it) }
                }

            viewModel.onEvent(ProxyTesterEvent.UrlChanged("not-a-valid-http-url"))
            viewModel.onEvent(ProxyTesterEvent.OpenInBrowser)
            advanceUntilIdle()

            assertTrue(effects.isEmpty())
            assertFalse(viewModel.uiState.value.isSessionActive)
            assertEquals("Error: invalid URL", viewModel.uiState.value.statusText)
            assertEquals(0, fakeProxy.openCalls.size)

            viewModel.onEvent(ProxyTesterEvent.UrlChanged(""))
            viewModel.onEvent(ProxyTesterEvent.PlayInOtherApp)
            advanceUntilIdle()

            assertTrue(effects.isEmpty())
            assertFalse(viewModel.uiState.value.isSessionActive)
            assertEquals("Error: invalid URL", viewModel.uiState.value.statusText)
            assertEquals(0, fakeProxy.openCalls.size)

            effectsJob.cancel()
        }

    @Test
    fun PV7_onClearedClosesActiveSession() =
        runTest(testDispatcher) {
            viewModel.onEvent(ProxyTesterEvent.UrlChanged("https://example.com/video.mp4"))
            viewModel.onEvent(ProxyTesterEvent.OpenInBrowser)
            advanceUntilIdle()
            assertEquals(0, fakeProxy.closedCount)

            val clearMethod = ViewModel::class.java.getDeclaredMethod("onCleared")
            clearMethod.isAccessible = true
            clearMethod.invoke(viewModel)
            advanceUntilIdle()

            assertEquals(1, fakeProxy.closedCount)
        }

    private class FakeStreamProxy : StreamProxy {
        val openCalls = mutableListOf<Pair<String, Map<String, String>>>()
        var closedCount = 0
        var nextSessionUrl = "http://127.0.0.1:45678/p/fake-token/stream.mp4"

        override fun open(
            url: String,
            headers: Map<String, String>,
        ): ProxySession {
            require(url.startsWith("http://") || url.startsWith("https://")) {
                "Invalid URL: must be http or https"
            }
            openCalls.add(url to headers)
            return ProxySession(nextSessionUrl) {
                closedCount++
            }
        }
    }
}
