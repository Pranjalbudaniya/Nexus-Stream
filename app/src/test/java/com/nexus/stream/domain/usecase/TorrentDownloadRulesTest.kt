package com.nexus.stream.domain.usecase

import com.nexus.stream.domain.model.DownloadDisplay
import com.nexus.stream.domain.model.DownloadItem
import com.nexus.stream.domain.model.DownloadKind
import com.nexus.stream.domain.model.DownloadPhase
import com.nexus.stream.domain.model.DownloadSource
import com.nexus.stream.domain.model.DownloadState
import com.nexus.stream.domain.model.TorrentFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TorrentDownloadRulesTest {
    @Test
    fun TZ1_pickFile_singleVideo_ignoresSamplesAndExtras() {
        val files =
            listOf(
                TorrentFile(0, "movie.mkv", 4L * 1024 * 1024 * 1024),
                TorrentFile(1, "sample.mkv", 20L * 1024 * 1024),
                TorrentFile(2, "notes.txt", 1024L),
                TorrentFile(3, "poster.jpg", 200L * 1024),
            )
        val result = chooseTorrentFile(files)
        assertEquals(FileChoice.Chosen(0), result)
    }

    @Test
    fun TZ2_pickFile_usesTheEpisodeHintInASeasonPack() {
        val files =
            (1..10).map { ep ->
                val epStr = ep.toString().padStart(2, '0')
                TorrentFile(ep - 1, "Series.S01E$epStr.mkv", 1024L * 1024 * 1024)
            }
        val result = chooseTorrentFile(files, season = 1, episode = 4)
        assertEquals(FileChoice.Chosen(3), result)
    }

    @Test
    fun TZ3_pickFile_ambiguous_needsTheUser() {
        val files =
            listOf(
                TorrentFile(0, "feature1.mkv", 1000L * 1024 * 1024),
                TorrentFile(1, "feature2.mkv", 2000L * 1024 * 1024),
                TorrentFile(2, "feature3.mkv", 1500L * 1024 * 1024),
            )
        val result = chooseTorrentFile(files)
        assertTrue(result is FileChoice.NeedsUser)
        val candidates = (result as FileChoice.NeedsUser).candidates
        assertEquals(listOf(1, 2, 0), candidates.map { it.index })
    }

    @Test
    fun TZ4_pickFile_noVideo_isNoVideo() {
        val files =
            listOf(
                TorrentFile(0, "archive.zip", 100L * 1024 * 1024),
                TorrentFile(1, "readme.txt", 500L),
            )
        val result = chooseTorrentFile(files)
        assertEquals(FileChoice.NoVideo, result)
    }

    @Test
    fun TZ5_pickFile_requestedIndex_isUsedWhenItIsAVideo() {
        val files =
            listOf(
                TorrentFile(0, "video1.mkv", 1000L * 1024 * 1024),
                TorrentFile(1, "video2.mkv", 1000L * 1024 * 1024),
                TorrentFile(2, "video3.mkv", 2000L * 1024 * 1024),
            )
        val result = chooseTorrentFile(files, requestedIndex = 2)
        assertEquals(FileChoice.Chosen(2), result)
    }

    @Test
    fun TZ6_pickFile_invalidRequestedIndex_fallsBackToTheRules() {
        val files =
            listOf(
                TorrentFile(0, "movie.mkv", 2000L * 1024 * 1024),
                TorrentFile(1, "notes.txt", 1000L),
            )
        val res99 = chooseTorrentFile(files, requestedIndex = 99)
        assertEquals(FileChoice.Chosen(0), res99)

        val resTxt = chooseTorrentFile(files, requestedIndex = 1)
        assertEquals(FileChoice.Chosen(0), resTxt)
    }

    @Test
    fun TZ7_progress_isBytesDoneOverTotal() {
        val p1 = torrentProgress(250L, 1000L)
        assertNotNull(p1)
        assertEquals(0.25f, p1!!, 0.001f)

        val p2 = torrentProgress(0L, 0L)
        assertNull(p2)
    }

    @Test
    fun TZ8_phase_noMetadata_isFetchingMetadata() {
        val phase =
            phaseOf(
                state = TorrentSessionState.DOWNLOADING_METADATA,
                hasMetadata = false,
                peers = 0,
                bytesDone = 0L,
                lastProgressAtMs = 0L,
                nowMs = 1000L,
            )
        assertEquals(DownloadPhase.FETCHING_METADATA, phase)
    }

    @Test
    fun TZ9_phase_checkingFiles() {
        val phase =
            phaseOf(
                state = TorrentSessionState.CHECKING_FILES,
                hasMetadata = true,
                peers = 2,
                bytesDone = 100L,
                lastProgressAtMs = 1000L,
                nowMs = 1000L,
            )
        assertEquals(DownloadPhase.CHECKING_FILES, phase)
    }

    @Test
    fun TZ10_phase_transferringWithPeers_isTransferring() {
        val p1 =
            phaseOf(
                state = TorrentSessionState.DOWNLOADING,
                hasMetadata = true,
                peers = 5,
                bytesDone = 500L,
                lastProgressAtMs = 1000L,
                nowMs = 2000L,
            )
        assertEquals(DownloadPhase.TRANSFERRING, p1)

        val p2 =
            phaseOf(
                state = TorrentSessionState.DOWNLOADING,
                hasMetadata = true,
                peers = 0,
                bytesDone = 0L,
                lastProgressAtMs = 1000L,
                nowMs = 2000L,
            )
        assertEquals(DownloadPhase.CONNECTING, p2)
    }

    @Test
    fun TZ11_phase_stalledAfterFiveMinutesWithoutProgress() {
        val pStalled =
            phaseOf(
                state = TorrentSessionState.DOWNLOADING,
                hasMetadata = true,
                peers = 2,
                bytesDone = 1000L,
                lastProgressAtMs = 1000L,
                nowMs = 1000L + 301_000L,
            )
        assertEquals(DownloadPhase.STALLED, pStalled)

        val pActive =
            phaseOf(
                state = TorrentSessionState.DOWNLOADING,
                hasMetadata = true,
                peers = 2,
                bytesDone = 1000L,
                lastProgressAtMs = 1000L,
                nowMs = 1000L + 299_000L,
            )
        assertEquals(DownloadPhase.TRANSFERRING, pActive)
    }

    @Test
    fun TZ12_phase_progressResetsTheStallClock() {
        val phase =
            phaseOf(
                state = TorrentSessionState.DOWNLOADING,
                hasMetadata = true,
                peers = 2,
                bytesDone = 2000L,
                lastProgressAtMs = 400_000L,
                nowMs = 410_000L,
            )
        assertEquals(DownloadPhase.TRANSFERRING, phase)
    }

    @Test
    fun TZ13_metadataTimeout_after120Seconds() {
        val t119 = metadataTimedOut(startedAtMs = 1000L, nowMs = 1000L + 119_000L, hasMetadata = false)
        assertFalse(t119)

        val t121 = metadataTimedOut(startedAtMs = 1000L, nowMs = 1000L + 121_000L, hasMetadata = false)
        assertTrue(t121)

        val tWithMeta = metadataTimedOut(startedAtMs = 1000L, nowMs = 1000L + 121_000L, hasMetadata = true)
        assertFalse(tWithMeta)
    }

    @Test
    fun TZ14_saveDir_isTheDownloadId_underTorrents() {
        val root = File("/r")
        val dir = torrentSaveDir(root, "abc")
        assertEquals(File("/r/torrents/abc"), dir)
        assertFalse(dir.path.contains("hash"))
    }

    @Test
    fun TZ15_completedPath_rejectsTraversalAndAbsolutePaths() {
        val saveDir = File("/downloads/torrents/123")

        val valid = completedTorrentPath(saveDir, "Show/S01E01.mkv")
        assertEquals(File(saveDir, "Show/S01E01.mkv"), valid)

        assertNull(completedTorrentPath(saveDir, "../x.mkv"))
        assertNull(completedTorrentPath(saveDir, "/etc/x"))
        assertNull(completedTorrentPath(saveDir, ""))
        assertNull(completedTorrentPath(saveDir, "a/../../b"))
    }

    @Test
    fun TZ16_runnableTorrentIds_dropsAHashThatIsAlreadyRunning() {
        val itemA1 = makeTorrentItem("1", "hashA")
        val itemA2 = makeTorrentItem("2", "hashA")
        val itemB = makeTorrentItem("3", "hashB")

        val runnable = runnableTorrentIds(listOf(itemA1, itemA2, itemB), setOf("hashA"))
        assertEquals(listOf("3"), runnable)
    }

    private fun makeTorrentItem(
        id: String,
        hash: String,
    ) = DownloadItem(
        id = id,
        kind = DownloadKind.TORRENT,
        source = DownloadSource.Torrent(hash),
        metaId = "meta_$id",
        type = "series",
        title = "Title",
        videoId = "vid_$id",
        fileName = "file.mkv",
        state = DownloadState.QUEUED,
        downloadedBytes = 0L,
        totalBytes = 0L,
        createdAt = 1000L,
        updatedAt = 1000L,
    )
}
