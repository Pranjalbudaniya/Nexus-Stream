package com.nexus.stream.domain.usecase

import com.nexus.stream.data.torrent.NetworkStatus
import com.nexus.stream.domain.model.DownloadItem
import com.nexus.stream.domain.model.DownloadKind
import com.nexus.stream.domain.model.DownloadSource
import com.nexus.stream.domain.model.DownloadState
import com.nexus.stream.domain.model.NetworkGate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadRulesTest {
    private fun createItem(
        id: String = "id1",
        state: DownloadState = DownloadState.QUEUED,
        createdAt: Long = 1000L,
        totalBytes: Long = 100_000L,
    ) = DownloadItem(
        id = id,
        kind = DownloadKind.HTTP,
        source = DownloadSource.Http("https://example.com/video.mp4"),
        metaId = "tt1",
        type = "movie",
        title = "Test Movie",
        videoId = "tt1",
        fileName = "test.mp4",
        totalBytes = totalBytes,
        downloadedBytes = 0L,
        state = state,
        allowMetered = false,
        createdAt = createdAt,
        updatedAt = createdAt,
    )

    @Test
    fun DR1_extension_fromTheHintFirst() {
        assertEquals("mkv", downloadFileExtension(hint = "Movie.2024.mkv", urlPath = "x.mp4"))
    }

    @Test
    fun DR2_extension_fromTheUrlPath_thenContentType() {
        assertEquals("webm", downloadFileExtension(hint = null, urlPath = "/a/b.webm"))
        assertEquals("mkv", downloadFileExtension(hint = null, urlPath = "/a/b", contentType = "video/x-matroska"))
    }

    @Test
    fun DR3_extension_unknown_defaultsToMp4() {
        assertEquals("mp4", downloadFileExtension(hint = null, urlPath = null, contentType = null))
    }

    @Test
    fun DR4_extension_isSanitised() {
        assertEquals("m4v", downloadFileExtension(hint = "x.m4v?token=1"))
        assertEquals("mp4", downloadFileExtension(hint = "evil.../../sh"))
        assertEquals("mp4", downloadFileExtension(hint = "a.toolongext"))
    }

    @Test
    fun DR5_rangeHeader_forOffset() {
        assertEquals("bytes=1024-", rangeHeader(1024L))
        assertNull(rangeHeader(0L))
    }

    @Test
    fun DR6_resume_206_matchingStart_appends() {
        assertEquals(ResumeDecision.Append, decideResume(206, 1000L, 1000L, 5000L))
    }

    @Test
    fun DR7_resume_206_wrongStart_restarts() {
        assertEquals(ResumeDecision.Restart, decideResume(206, 1000L, 0L, 5000L))
    }

    @Test
    fun DR8_resume_200_afterOffset_restartsFromZero() {
        assertEquals(ResumeDecision.Restart, decideResume(200, 1000L, null, 5000L))
        assertEquals(ResumeDecision.Fresh, decideResume(200, 0L, null, 5000L))
    }

    @Test
    fun DR9_resume_416_whenComplete_isAlreadyComplete() {
        assertEquals(ResumeDecision.AlreadyComplete, decideResume(416, 5000L, null, 5000L))
    }

    @Test
    fun DR10_resume_416_whenNotComplete_restarts() {
        assertEquals(ResumeDecision.Restart, decideResume(416, 5000L, null, 9000L))
        assertEquals(ResumeDecision.Restart, decideResume(416, 5000L, null, null))
    }

    @Test
    fun DR11_status_401_403_404_410_areLinkExpired() {
        listOf(401, 403, 404, 410).forEach { code ->
            assertEquals(StatusClass.LinkExpired, classifyStatus(code))
        }
    }

    @Test
    fun DR12_status_408_429_5xx_areRetryable() {
        listOf(408, 429, 500, 503).forEach { code ->
            assertEquals(StatusClass.Retryable, classifyStatus(code))
        }
        assertEquals(StatusClass.ServerError, classifyStatus(400))
        assertEquals(StatusClass.Ok, classifyStatus(200))
        assertEquals(StatusClass.Ok, classifyStatus(206))
    }

    @Test
    fun DR13_notAVideo_htmlJsonTextAndHls_areRejected() {
        assertTrue(looksLikeNotAVideo("text/html", 10_000_000L))
        assertTrue(looksLikeNotAVideo("application/json", 10_000_000L))
        assertTrue(looksLikeNotAVideo("text/plain; charset=utf-8", 10_000_000L))
        assertTrue(looksLikeNotAVideo("application/vnd.apple.mpegurl", 10_000_000L))
        assertTrue(looksLikeNotAVideo(null, 500 * 1024L))
    }

    @Test
    fun DR14_notAVideo_videoAndOctetStream_areAccepted() {
        assertFalse(looksLikeNotAVideo("video/mp4", 10_000_000L))
        assertFalse(looksLikeNotAVideo("application/octet-stream", 10_000_000L))
        assertFalse(looksLikeNotAVideo("video/x-matroska", 10_000_000L))
        assertFalse(looksLikeNotAVideo(null, 3L * 1024L * 1024L * 1024L))
    }

    @Test
    fun DR15_storage_needsRemainingPlusReserve() {
        val remaining = 1024L * 1024L * 1024L // 1 GiB
        val available14 = (1.4 * 1024 * 1024 * 1024).toLong()
        val available16 = (1.6 * 1024 * 1024 * 1024).toLong()
        assertFalse(hasEnoughStorage(available14, remaining))
        assertTrue(hasEnoughStorage(available16, remaining))
    }

    @Test
    fun DR16_storage_unknownSize_needsOneGiB() {
        val remaining = 0L
        val available900 = 900L * 1024L * 1024L
        val available1100 = 1100L * 1024L * 1024L
        assertFalse(hasEnoughStorage(available900, remaining))
        assertTrue(hasEnoughStorage(available1100, remaining))
    }

    @Test
    fun DR17_pickToStart_respectsMaxConcurrentAndFifo() {
        val item1 = createItem(id = "1", createdAt = 1L)
        val item2 = createItem(id = "2", createdAt = 2L)
        val item3 = createItem(id = "3", createdAt = 3L)

        val picked = pickToStart(listOf(item1, item2, item3), 2) { NetworkGate.Proceed }
        assertEquals(listOf("1", "2"), picked)

        val runningItem = createItem(id = "0", state = DownloadState.DOWNLOADING, createdAt = 0L)
        val pickedWithOneRunning = pickToStart(listOf(runningItem, item1, item2, item3), 2) { NetworkGate.Proceed }
        assertEquals(listOf("1"), pickedWithOneRunning)
    }

    @Test
    fun DR18_pickToStart_skipsPausedFailedCompletedAndFilePick() {
        val paused = createItem(id = "p", state = DownloadState.PAUSED)
        val failed = createItem(id = "f", state = DownloadState.FAILED)
        val completed = createItem(id = "c", state = DownloadState.COMPLETED)
        val filePick = createItem(id = "fp", state = DownloadState.NEEDS_FILE_PICK)
        val queued = createItem(id = "q", state = DownloadState.QUEUED)

        val picked = pickToStart(listOf(paused, failed, completed, filePick, queued), 2) { NetworkGate.Proceed }
        assertEquals(listOf("q"), picked)
    }

    @Test
    fun DR19_gate_meteredWithWifiOnly_waitsForWifi() {
        assertEquals(NetworkGate.WaitForWifi, networkGate(NetworkStatus.METERED, wifiOnly = true, allowMetered = false))
    }

    @Test
    fun DR20_gate_allowMetered_proceeds() {
        assertEquals(NetworkGate.Proceed, networkGate(NetworkStatus.METERED, wifiOnly = true, allowMetered = true))
    }

    @Test
    fun DR21_gate_noNetwork_waitsForNetwork() {
        assertEquals(NetworkGate.WaitForNetwork, networkGate(NetworkStatus.NONE, wifiOnly = true, allowMetered = false))
        assertEquals(NetworkGate.WaitForNetwork, networkGate(NetworkStatus.NONE, wifiOnly = false, allowMetered = true))
    }

    @Test
    fun DR22_gate_wifiOnlyOff_proceedsOnMetered() {
        assertEquals(NetworkGate.Proceed, networkGate(NetworkStatus.METERED, wifiOnly = false, allowMetered = false))
        assertEquals(NetworkGate.Proceed, networkGate(NetworkStatus.UNMETERED, wifiOnly = true, allowMetered = false))
    }

    @Test
    fun DR23_duplicate_sameMetaAndVideo_isFound() {
        val queued = createItem(id = "1", state = DownloadState.QUEUED).copy(metaId = "tt1", videoId = "tt1")
        val completed = createItem(id = "2", state = DownloadState.COMPLETED).copy(metaId = "tt2", videoId = "tt2")

        assertEquals(queued, findDuplicate(listOf(queued, completed), "tt1", "tt1"))
        assertEquals(completed, findDuplicate(listOf(queued, completed), "tt2", "tt2"))
    }

    @Test
    fun DR24_duplicate_failedItemDoesNotBlock() {
        val failed = createItem(id = "1", state = DownloadState.FAILED).copy(metaId = "tt1", videoId = "tt1")
        assertNull(findDuplicate(listOf(failed), "tt1", "tt1"))
    }

    @Test
    fun DR25_eta_fromSpeedAndRemaining() {
        val remaining = 100L * 1024L * 1024L
        val speed = 10L * 1024L * 1024L
        assertEquals(10L, etaSeconds(remaining, speed))
    }

    @Test
    fun DR26_eta_unknownWithoutSpeedOrTotal() {
        assertNull(etaSeconds(100L * 1024L * 1024L, 0L))
        assertNull(etaSeconds(0L, 10L * 1024L * 1024L))
    }

    @Test
    fun DR27_speedMeter_smoothsSamples() {
        val meter = SpeedMeter(0.3)
        meter.addSample(100L)
        meter.addSample(100L)
        val value = meter.addSample(1000L)
        assertTrue(value in 101L..499L)

        meter.reset()
        assertEquals(0L, meter.speed)
    }

    @Test
    fun DR28_progressFraction_clampsToOne() {
        assertEquals(1.0f, progressFraction(120L, 100L))
    }

    @Test
    fun DR29_progressFraction_unknownTotal_isNull() {
        assertNull(progressFraction(50L, 0L))
    }
}
