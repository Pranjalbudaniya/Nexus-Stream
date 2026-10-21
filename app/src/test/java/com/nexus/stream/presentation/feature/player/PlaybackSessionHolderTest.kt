package com.nexus.stream.presentation.feature.player

import com.nexus.stream.domain.model.PlaybackSource
import com.nexus.stream.domain.model.TorrentRequest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackSessionHolderTest {
    private val holder = PlaybackSessionHolder()

    @Test
    fun PH1_start_setsCurrentAndEmitsOneOpenRequest() =
        runTest(UnconfinedTestDispatcher()) {
            val requests = mutableListOf<Unit>()
            val job =
                launch {
                    holder.openRequests.collect { requests.add(it) }
                }

            val selection =
                PlaybackSelection(
                    title = "Test Video",
                    source = PlaybackSource.Direct("https://example.com/stream.mp4"),
                    type = "movie",
                    metaId = "tt1234567",
                    videoId = "tt1234567",
                )

            holder.start(selection)

            assertEquals(selection, holder.current.value)
            assertEquals(1, requests.size)

            job.cancel()
        }

    @Test
    fun PH2_clear_emptiesCurrent() {
        val selection =
            PlaybackSelection(
                title = "Test Video",
                source = PlaybackSource.Direct("https://example.com/stream.mp4"),
                type = "movie",
                metaId = "tt1234567",
                videoId = "tt1234567",
            )
        holder.start(selection)
        assertEquals(selection, holder.current.value)

        holder.clear()
        assertNull(holder.current.value)
    }

    @Test
    fun PH3_twoStartCallsEmitTwoRequests_andToStringPrivacy() =
        runTest(UnconfinedTestDispatcher()) {
            val requests = mutableListOf<Unit>()
            val job =
                launch {
                    holder.openRequests.collect { requests.add(it) }
                }

            val secretUrl = "https://secret.example.com/video.mkv"
            val secretHeaderVal = "secret_auth_token_xyz"
            val secretHash = "0123456789abcdef0123456789abcdef01234567"

            val selection1 =
                PlaybackSelection(
                    title = "Movie 1",
                    source =
                        PlaybackSource.DirectWithHeaders(
                            url = secretUrl,
                            headers = mapOf("Authorization" to secretHeaderVal),
                        ),
                    type = "movie",
                    metaId = "tt0000001",
                    videoId = "tt0000001",
                )
            val selection2 =
                PlaybackSelection(
                    title = "Movie 2",
                    source =
                        PlaybackSource.Torrent(
                            TorrentRequest(infoHash = secretHash),
                        ),
                    type = "series",
                    metaId = "tt0000002",
                    videoId = "tt0000002:1:1",
                )

            holder.start(selection1)
            holder.start(selection2)

            assertEquals(2, requests.size)
            assertEquals(selection2, holder.current.value)

            val str1 = selection1.toString()
            assertFalse(str1.contains("secret", ignoreCase = true))
            assertFalse(str1.contains(secretUrl))
            assertFalse(str1.contains(secretHeaderVal))

            val str2 = selection2.toString()
            assertFalse(str2.contains(secretHash))

            job.cancel()
        }

    @Test
    fun PF1_formatPlaybackTime_zeroReturnsZeroZeroZero() {
        assertEquals("0:00", formatPlaybackTime(0L))
    }

    @Test
    fun PF2_formatPlaybackTime_sixtyFiveSecondsReturnsOneMinuteFive() {
        assertEquals("1:05", formatPlaybackTime(65_000L))
    }

    @Test
    fun PF3_formatPlaybackTime_overOneHourFormattedCorrectly() {
        assertEquals("1:02:05", formatPlaybackTime(3_725_000L))
    }

    @Test
    fun PF4_formatPlaybackTime_negativeReturnsZeroZeroZero() {
        assertEquals("0:00", formatPlaybackTime(-1_000L))
    }

    @Test
    fun PF5_formatPlaybackTime_fiftyNineSecondsReturnsZeroFiftyNine() {
        assertEquals("0:59", formatPlaybackTime(59_999L))
    }
}
