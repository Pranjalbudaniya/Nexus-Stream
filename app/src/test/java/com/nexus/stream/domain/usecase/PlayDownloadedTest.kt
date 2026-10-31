package com.nexus.stream.domain.usecase

import com.nexus.stream.domain.model.DownloadItem
import com.nexus.stream.domain.model.DownloadKind
import com.nexus.stream.domain.model.DownloadSource
import com.nexus.stream.domain.model.DownloadState
import com.nexus.stream.domain.model.PlaybackSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PlayDownloadedTest {
    @get:Rule
    val tmpFolder = TemporaryFolder()

    private fun createItem(
        id: String = "d1",
        state: DownloadState = DownloadState.COMPLETED,
        filePath: String? = null,
        title: String = "Test Episode",
        posterUrl: String? = "https://example.com/poster.jpg",
    ): DownloadItem =
        DownloadItem(
            id = id,
            kind = DownloadKind.HTTP,
            source = DownloadSource.Http("https://example.com/video.mp4"),
            metaId = "tt123",
            type = "series",
            title = title,
            metaName = "Test Series",
            posterUrl = posterUrl,
            videoId = "tt123:1:1",
            season = 1,
            episode = 1,
            fileName = "$id.mp4",
            filePath = filePath,
            state = state,
            createdAt = 100L,
            updatedAt = 100L,
        )

    @Test
    fun PL1_completedItem_buildsALocalSelection() {
        val file = tmpFolder.newFile("episode.mp4")
        val item = createItem(state = DownloadState.COMPLETED, filePath = file.absolutePath)
        val playDownloaded = PlayDownloaded()

        val result = playDownloaded(item, file)
        assertTrue(result is PlayResult.Ready)
        val ready = result as PlayResult.Ready
        val selection = ready.selection

        assertTrue(selection.source is PlaybackSource.Local)
        assertEquals(file.absolutePath, (selection.source as PlaybackSource.Local).filePath)
        assertEquals("tt123", selection.metaId)
        assertEquals("tt123:1:1", selection.videoId)
        assertEquals(1, selection.season)
        assertEquals(1, selection.episode)
        assertEquals("Test Episode", selection.title)
        assertEquals("https://example.com/poster.jpg", selection.posterUrl)
        assertNull(selection.sourceFingerprint)
        assertNull(selection.request)
    }

    @Test
    fun PL2_missingFile_returnsFileMissing() {
        val nonExistentFile = File(tmpFolder.root, "does_not_exist.mp4")
        val item = createItem(state = DownloadState.COMPLETED, filePath = nonExistentFile.absolutePath)
        val playDownloaded = PlayDownloaded()

        val result = playDownloaded(item, nonExistentFile)
        assertEquals(PlayResult.FileMissing, result)
    }

    @Test
    fun PL3_notCompleted_returnsNotReady() {
        val file = tmpFolder.newFile("test.mp4")
        val playDownloaded = PlayDownloaded()

        val downloadingItem = createItem(state = DownloadState.DOWNLOADING, filePath = file.absolutePath)
        val pausedItem = createItem(state = DownloadState.PAUSED, filePath = file.absolutePath)

        assertEquals(PlayResult.NotReady, playDownloaded(downloadingItem, file))
        assertEquals(PlayResult.NotReady, playDownloaded(pausedItem, file))
    }

    @Test
    fun PL4_localSource_toStringHidesThePath() {
        val source = PlaybackSource.Local("/a/b/c.mp4")
        val str = source.toString()
        assertFalse(str.contains("/a/b/c.mp4"))
        assertFalse(str.contains("mp4"))
        assertEquals("PlaybackSource.Local", str)
    }
}
