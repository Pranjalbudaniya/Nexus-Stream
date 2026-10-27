package com.nexus.stream.domain.usecase

import com.nexus.stream.domain.model.Addon
import com.nexus.stream.domain.model.AddonManifest
import com.nexus.stream.domain.model.AddonSource
import com.nexus.stream.domain.model.ManifestHints
import com.nexus.stream.domain.model.ResourceSpec
import com.nexus.stream.domain.model.StreamKind
import com.nexus.stream.domain.model.hashAddonKey
import com.nexus.stream.domain.repository.AddonRepository
import com.nexus.stream.domain.repository.StreamRepository
import com.nexus.stream.domain.repository.StreamsState
import com.nexus.stream.presentation.common.PlaybackRequest
import io.mockk.Called
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SameSourceFinderTest {
    private val streamRepository = mockk<StreamRepository>()
    private val addonRepository = mockk<AddonRepository>()
    private val finder = SameSourceFinder(streamRepository, addonRepository)

    private val sampleRequest =
        PlaybackRequest(
            type = "series",
            metaId = "tt123",
            videoId = "tt123:1:4",
            title = "Episode 4",
            season = 1,
            episode = 4,
        )

    private fun createTestAddon(
        id: String,
        name: String,
        transportUrl: String = "https://$id.com/manifest.json",
        types: List<String> = listOf("movie", "series"),
    ): Addon =
        Addon(
            transportUrl = transportUrl,
            manifest =
                AddonManifest(
                    id = id,
                    version = "1.0.0",
                    name = name,
                    description = "desc",
                    logo = null,
                    background = null,
                    types = types,
                    idPrefixes = null,
                    resources = listOf(ResourceSpec(name = "stream", types = types, idPrefixes = null)),
                    catalogs = emptyList(),
                    behaviorHints =
                        ManifestHints(
                            adult = false,
                            p2p = false,
                            configurable = false,
                            configurationRequired = false,
                        ),
                ),
            enabled = true,
            position = 0,
            source = AddonSource.LOCAL,
            installedAt = 0L,
        )

    @Test
    fun SS1_matchInFirstEmission_found() =
        runTest {
            val addonUrl = "https://addon1.com/manifest.json"
            every { addonRepository.observeActive() } returns
                flowOf(listOf(createTestAddon("addon-1", "Addon 1", addonUrl)))
            val fingerprint = fp(addons = listOf(hashAddonKey(addonUrl)), binge = "g")
            val matchingStream = cand(key = "hit", addon = addonUrl, binge = "g")

            every { streamRepository.streams(sampleRequest.type, sampleRequest.videoId, false, null) } returns
                flowOf(
                    StreamsState(
                        items = listOf(matchingStream),
                        answered = 1,
                        total = 1,
                        failures = emptyList(),
                        done = true,
                    ),
                )

            val result = finder.find(sampleRequest, fingerprint)
            assertTrue(result is SameSourceResult.Found)
            assertEquals("hit", (result as SameSourceResult.Found).stream.key)
        }

    @Test
    fun SS2_matchInLaterEmission_found() =
        runTest {
            val addonUrl = "https://addon1.com/manifest.json"
            every { addonRepository.observeActive() } returns
                flowOf(listOf(createTestAddon("addon-1", "Addon 1", addonUrl)))
            val fingerprint = fp(addons = listOf(hashAddonKey(addonUrl)), binge = "g")
            val missStream = cand(key = "miss", addon = addonUrl, binge = "other", res = 720)
            val matchingStream = cand(key = "hit", addon = addonUrl, binge = "g")

            val streamFlow = MutableSharedFlow<StreamsState>()
            every { streamRepository.streams(sampleRequest.type, sampleRequest.videoId, false, null) } returns
                streamFlow

            val deferred = async { finder.find(sampleRequest, fingerprint) }
            runCurrent()

            // Emission 1: non-matching, not done
            streamFlow.emit(
                StreamsState(items = listOf(missStream), answered = 0, total = 1, failures = emptyList(), done = false),
            )
            runCurrent()
            assertTrue(deferred.isActive)

            // Emission 2: matching stream
            streamFlow.emit(
                StreamsState(
                    items = listOf(missStream, matchingStream),
                    answered = 1,
                    total = 1,
                    failures = emptyList(),
                    done = true,
                ),
            )
            runCurrent()

            val result = deferred.await()
            assertTrue(result is SameSourceResult.Found)
            assertEquals("hit", (result as SameSourceResult.Found).stream.key)
        }

    @Test
    fun SS3_addonAnsweredWithoutMatch_noMatch() =
        runTest {
            val addonUrl = "https://addon1.com/manifest.json"
            every { addonRepository.observeActive() } returns
                flowOf(listOf(createTestAddon("addon-1", "Addon 1", addonUrl)))
            val fingerprint = fp(addons = listOf(hashAddonKey(addonUrl)), binge = "g")
            val missStream = cand(key = "miss", addon = addonUrl, binge = "other", res = 720)

            every { streamRepository.streams(sampleRequest.type, sampleRequest.videoId, false, null) } returns
                flowOf(
                    StreamsState(
                        items = listOf(missStream),
                        answered = 1,
                        total = 1,
                        failures = emptyList(),
                        done = true,
                    ),
                )

            val result = finder.find(sampleRequest, fingerprint)
            assertEquals(SameSourceResult.NotFound(SameSourceReason.NO_MATCH), result)
        }

    @Test
    fun SS4_addonNotEnabled_unavailable_withoutLoading() =
        runTest {
            val activeAddonUrl = "https://addon2.com/manifest.json"
            every { addonRepository.observeActive() } returns
                flowOf(listOf(createTestAddon("addon-2", "Addon 2", activeAddonUrl)))
            val fingerprint = fp(addons = listOf(hashAddonKey("https://addon1.com/manifest.json")), binge = "g")

            val result = finder.find(sampleRequest, fingerprint)
            assertEquals(SameSourceResult.NotFound(SameSourceReason.ADDON_UNAVAILABLE), result)
            verify { streamRepository wasNot Called }
        }

    @Test
    fun SS5_noAnswerWithinTwelveSeconds_timeout() =
        runTest {
            val addonUrl = "https://addon1.com/manifest.json"
            every { addonRepository.observeActive() } returns
                flowOf(listOf(createTestAddon("addon-1", "Addon 1", addonUrl)))
            val fingerprint = fp(addons = listOf(hashAddonKey(addonUrl)), binge = "g")

            val streamFlow = MutableSharedFlow<StreamsState>()
            every { streamRepository.streams(sampleRequest.type, sampleRequest.videoId, false, null) } returns
                streamFlow

            val deferred = async { finder.find(sampleRequest, fingerprint) }
            runCurrent()

            advanceTimeBy(12_001L)
            runCurrent()

            val result = deferred.await()
            assertEquals(SameSourceResult.NotFound(SameSourceReason.TIMEOUT), result)
        }

    @Test
    fun SS6_loaderThrows_loadFailed() =
        runTest {
            val addonUrl = "https://addon1.com/manifest.json"
            every { addonRepository.observeActive() } returns
                flowOf(listOf(createTestAddon("addon-1", "Addon 1", addonUrl)))
            val fingerprint = fp(addons = listOf(hashAddonKey(addonUrl)), binge = "g")

            every { streamRepository.streams(sampleRequest.type, sampleRequest.videoId, false, null) } returns
                flow { throw IOException("Connection reset") }

            val result = finder.find(sampleRequest, fingerprint)
            assertEquals(SameSourceResult.NotFound(SameSourceReason.LOAD_FAILED), result)
        }

    @Test(expected = CancellationException::class)
    fun SS7_cancellation_propagates() =
        runTest {
            val addonUrl = "https://addon1.com/manifest.json"
            every { addonRepository.observeActive() } returns
                flowOf(listOf(createTestAddon("addon-1", "Addon 1", addonUrl)))
            val fingerprint = fp(addons = listOf(hashAddonKey(addonUrl)), binge = "g")

            every { streamRepository.streams(sampleRequest.type, sampleRequest.videoId, false, null) } returns
                flow { throw CancellationException("Cancelled") }

            finder.find(sampleRequest, fingerprint)
        }

    @Test
    fun SS8_usesDecideResumeAndMatchSourceUnchanged() =
        runTest {
            val addonUrl = "https://addon1.com/manifest.json"
            every { addonRepository.observeActive() } returns
                flowOf(listOf(createTestAddon("addon-1", "Addon 1", addonUrl)))
            val fingerprint = fp(addons = listOf(hashAddonKey(addonUrl)), binge = "g")

            // External stream matching fingerprint binge but ignored by matchSource
            val externalStream = cand(key = "ext", addon = addonUrl, binge = "g", external = true)
            // Stream with wrong season/not OK matching fingerprint binge but ignored by matchSource
            val notOkStream = cand(key = "bad", addon = addonUrl, binge = "g", ok = false)

            every { streamRepository.streams(sampleRequest.type, sampleRequest.videoId, false, null) } returns
                flowOf(
                    StreamsState(
                        items = listOf(externalStream, notOkStream),
                        answered = 1,
                        total = 1,
                        failures = emptyList(),
                        done = true,
                    ),
                )

            val result = finder.find(sampleRequest, fingerprint)
            assertEquals(SameSourceResult.NotFound(SameSourceReason.NO_MATCH), result)
        }

    @Test
    fun NG6_sameSource_ignoresNotOkAndExternalStreams() =
        runTest {
            val addonUrl = "https://addon1.com/manifest.json"
            every { addonRepository.observeActive() } returns
                flowOf(listOf(createTestAddon("addon-1", "Addon 1", addonUrl)))
            val fingerprint = fp(addons = listOf(hashAddonKey(addonUrl)), binge = "g")

            val externalStream = cand(key = "ext", addon = addonUrl, binge = "g", external = true)
            val notOkStream = cand(key = "bad", addon = addonUrl, binge = "g", ok = false)

            every { streamRepository.streams(sampleRequest.type, sampleRequest.videoId, false, null) } returns
                flowOf(
                    StreamsState(
                        items = listOf(externalStream, notOkStream),
                        answered = 1,
                        total = 1,
                        failures = emptyList(),
                        done = true,
                    ),
                )

            val result = finder.find(sampleRequest, fingerprint)
            assertEquals(SameSourceResult.NotFound(SameSourceReason.NO_MATCH), result)
        }
}
