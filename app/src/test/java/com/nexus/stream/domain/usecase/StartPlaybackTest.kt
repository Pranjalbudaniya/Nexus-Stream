package com.nexus.stream.domain.usecase

import com.nexus.stream.domain.model.EpisodeHint
import com.nexus.stream.domain.model.ParsedStream
import com.nexus.stream.domain.model.PlaybackCapabilities
import com.nexus.stream.domain.model.PlaybackSource
import com.nexus.stream.domain.model.Stream
import com.nexus.stream.domain.model.StreamKind
import com.nexus.stream.domain.model.StreamSource
import com.nexus.stream.domain.model.TorrentRequest
import com.nexus.stream.domain.model.fingerprintOf
import com.nexus.stream.presentation.common.PlaybackRequest
import com.nexus.stream.presentation.feature.player.PlaybackSessionHolder
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StartPlaybackTest {
    private val holder = PlaybackSessionHolder()
    private val defaultCapabilities = PlaybackCapabilities(supportsTorrent = true)

    private val sampleRequest =
        PlaybackRequest(
            type = "series",
            metaId = "tt123",
            videoId = "tt123:1:4",
            title = "Episode 4",
            season = 1,
            episode = 4,
            posterUrl = "https://example.com/poster.jpg",
            metaName = "Sample Series",
        )

    private fun createParsedStream(
        key: String,
        source: StreamSource,
        fileName: String? = null,
        needsHeaders: Boolean = false,
    ): ParsedStream {
        val kind =
            when (source) {
                is StreamSource.Url -> StreamKind.Direct
                is StreamSource.Torrent -> StreamKind.Torrent
                is StreamSource.External -> StreamKind.ExternalLink
                else -> StreamKind.Direct
            }
        return ParsedStream(
            key = key,
            stream =
                Stream(
                    source = source,
                    name = "Stream $key",
                    description = null,
                    subtitles = emptyList(),
                    bingeGroup = "group1",
                    notWebReady = false,
                    videoSize = 1000L,
                    filename = fileName,
                    videoHash = null,
                ),
            addonNames = listOf("Addon 1"),
            addonKeys = listOf("https://addon1.com/manifest.json"),
            resolution = 1080,
            hdr = emptySet(),
            codec = null,
            sizeBytes = 1000L,
            seeders = 10,
            kind = kind,
            needsHeaders = needsHeaders,
            displayName = "Stream $key",
            fileName = fileName,
        )
    }

    @Test
    fun SP1_torrentSupported_started_withAllSelectionFields() =
        runTest {
            val stream =
                createParsedStream(
                    "t1",
                    StreamSource.Torrent(
                        "0123456789abcdef0123456789abcdef01234567",
                        0,
                        listOf("udp://tracker.opentrackr.org:1337"),
                    ),
                )
            val useCase = StartPlayback(defaultCapabilities, holder)
            val result = useCase(sampleRequest, stream)

            assertTrue(result is StartResult.Started)
            val selection = (result as StartResult.Started).selection
            assertEquals(sampleRequest.metaId, selection.metaId)
            assertEquals(sampleRequest.videoId, selection.videoId)
            assertEquals(sampleRequest.type, selection.type)
            assertEquals(sampleRequest.season, selection.season)
            assertEquals(sampleRequest.episode, selection.episode)
            assertEquals(sampleRequest.posterUrl, selection.posterUrl)
            assertEquals(sampleRequest.metaName, selection.metaName)
            assertEquals(sampleRequest.title, selection.title)
            assertEquals(fingerprintOf(stream), selection.sourceFingerprint)
            assertEquals(selection, holder.current.value)
        }

    @Test
    fun SP2_directStream_started() =
        runTest {
            val stream = createParsedStream("d1", StreamSource.Url("https://example.com/video.mp4", emptyMap()))
            val useCase = StartPlayback(defaultCapabilities, holder)
            val result = useCase(sampleRequest, stream)

            assertTrue(result is StartResult.Started)
            val selection = (result as StartResult.Started).selection
            assertTrue(selection.source is PlaybackSource.Direct)
            assertEquals("https://example.com/video.mp4", (selection.source as PlaybackSource.Direct).url)
            assertEquals(selection, holder.current.value)
        }

    @Test
    fun SP3_streamWithHeaders_started_asDirectWithHeaders() =
        runTest {
            val headers = mapOf("Authorization" to "Bearer secret", "User-Agent" to "TestAgent")
            val stream = createParsedStream("h1", StreamSource.Url("https://example.com/video.mp4", headers))
            val useCase = StartPlayback(defaultCapabilities, holder)
            val result = useCase(sampleRequest, stream)

            assertTrue(result is StartResult.Started)
            val selection = (result as StartResult.Started).selection
            assertTrue(selection.source is PlaybackSource.DirectWithHeaders)
            val directWithHeaders = selection.source as PlaybackSource.DirectWithHeaders
            assertEquals(headers, directWithHeaders.headers)
            assertFalse(selection.toString().contains("secret"))
        }

    @Test
    fun SP4_externalLink_externalResult() =
        runTest {
            val stream = createParsedStream("e1", StreamSource.External("https://example.com/ext.mp4"))
            val useCase = StartPlayback(defaultCapabilities, holder)
            val result = useCase(sampleRequest, stream)

            assertTrue(result is StartResult.External)
            assertEquals("https://example.com/ext.mp4", (result as StartResult.External).url)
            assertNull(holder.current.value)
        }

    @Test
    fun SP5_torrentUnsupported_unsupported() =
        runTest {
            val stream =
                createParsedStream(
                    "t1",
                    StreamSource.Torrent(
                        "0123456789abcdef0123456789abcdef01234567",
                        0,
                        listOf("udp://tracker.opentrackr.org:1337"),
                    ),
                )
            val useCase = StartPlayback(PlaybackCapabilities(supportsTorrent = false), holder)
            val result = useCase(sampleRequest, stream)

            assertTrue(result is StartResult.Unsupported)
            assertNull(holder.current.value)
        }

    @Test
    fun SP6_torrentOnMobileData_needsGate() =
        runTest {
            val stream =
                createParsedStream(
                    "t1",
                    StreamSource.Torrent(
                        "0123456789abcdef0123456789abcdef01234567",
                        0,
                        listOf("udp://tracker.opentrackr.org:1337"),
                    ),
                )
            val useCase = StartPlayback(defaultCapabilities, holder, gateCheck = { true })
            val result = useCase(sampleRequest, stream)

            assertEquals(StartResult.NeedsGate, result)
            assertNull(holder.current.value)
        }

    @Test
    fun SP7_packEpisode_hintIsBuiltFromSeasonAndEpisode() =
        runTest {
            val stream =
                createParsedStream(
                    "t1",
                    StreamSource.Torrent(
                        "0123456789abcdef0123456789abcdef01234567",
                        null,
                        listOf("udp://tracker.opentrackr.org:1337"),
                    ),
                )
            val useCase = StartPlayback(defaultCapabilities, holder)

            // With season 1 episode 4: hint is built
            val resWithHint = useCase(sampleRequest, stream)
            assertTrue(resWithHint is StartResult.Started)
            val srcWithHint = (resWithHint as StartResult.Started).selection.source as PlaybackSource.Torrent
            assertEquals(EpisodeHint(1, 4), srcWithHint.request.episodeHint)

            // With null season / episode: no hint
            val reqNoSeason = sampleRequest.copy(season = null, episode = null)
            val resNoHint = useCase(reqNoSeason, stream)
            assertTrue(resNoHint is StartResult.Started)
            val srcNoHint = (resNoHint as StartResult.Started).selection.source as PlaybackSource.Torrent
            assertNull(srcNoHint.request.episodeHint)
        }
}
