package com.nexus.stream.domain.usecase

import com.nexus.stream.domain.model.DownloadItem
import com.nexus.stream.domain.model.DownloadKind
import com.nexus.stream.domain.model.DownloadSource
import com.nexus.stream.domain.model.DownloadState
import com.nexus.stream.domain.model.GalleryState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GalleryRulesTest {
    @Test
    fun GR1_fileName_movie_usesTheTitle() {
        val name =
            galleryFileName(
                title = "Dune",
                metaName = null,
                season = null,
                episode = null,
                extension = "mkv",
            )
        assertEquals("Dune.mkv", name)
    }

    @Test
    fun GR2_fileName_episode_usesSeriesAndPaddedEpisode() {
        val ep1 =
            galleryFileName(
                title = "Pilot",
                metaName = "Dead-End Job",
                season = 1,
                episode = 1,
                extension = "mkv",
            )
        assertEquals("Dead-End Job - S01E01.mkv", ep1)

        val ep105 =
            galleryFileName(
                title = "Special",
                metaName = "Dead-End Job",
                season = 2,
                episode = 105,
                extension = "mkv",
            )
        assertEquals("Dead-End Job - S02E105.mkv", ep105)
    }

    @Test
    fun GR3_fileName_replacesIllegalCharacters() {
        val name =
            galleryFileName(
                title = "A/B:C*D?E\"F<G>H|I",
                metaName = null,
                season = null,
                episode = null,
                extension = "mp4",
            )
        assertFalse(name.contains('/'))
        assertFalse(name.contains('\\'))
        assertFalse(name.contains(':'))
        assertFalse(name.contains('*'))
        assertFalse(name.contains('?'))
        assertFalse(name.contains('"'))
        assertFalse(name.contains('<'))
        assertFalse(name.contains('>'))
        assertFalse(name.contains('|'))
        assertFalse(name.contains("  "))
        assertEquals("A B C D E F G H I.mp4", name)
    }

    @Test
    fun GR4_fileName_isCappedBeforeTheExtension() {
        val longTitle = "a".repeat(300)
        val name =
            galleryFileName(
                title = longTitle,
                metaName = null,
                season = null,
                episode = null,
                extension = "mp4",
            )
        val baseName = name.removeSuffix(".mp4")
        assertEquals(120, baseName.length)
        assertTrue(name.endsWith(".mp4"))
    }

    @Test
    fun GR5_fileName_blankTitle_fallsBack() {
        val blank =
            galleryFileName(
                title = "  ",
                metaName = null,
                season = null,
                episode = null,
                extension = "mp4",
            )
        assertEquals("Nexus Stream video.mp4", blank)

        val dots =
            galleryFileName(
                title = "...",
                metaName = null,
                season = null,
                episode = null,
                extension = "mp4",
            )
        assertEquals("Nexus Stream video.mp4", dots)
    }

    @Test
    fun GR6_fileName_badExtension_becomesMp4() {
        val tooLong =
            galleryFileName(
                title = "Title",
                metaName = null,
                season = null,
                episode = null,
                extension = "toolongext",
            )
        assertTrue(tooLong.endsWith(".mp4"))

        val badChar =
            galleryFileName(
                title = "Title",
                metaName = null,
                season = null,
                episode = null,
                extension = "m\$v",
            )
        assertTrue(badChar.endsWith(".mp4"))

        val empty =
            galleryFileName(
                title = "Title",
                metaName = null,
                season = null,
                episode = null,
                extension = "",
            )
        assertTrue(empty.endsWith(".mp4"))
    }

    @Test
    fun GR7_mime_knownExtensionsAndFallback() {
        assertEquals("video/mp4", galleryMimeType("mp4"))
        assertEquals("video/x-matroska", galleryMimeType("mkv"))
        assertEquals("video/webm", galleryMimeType("webm"))
        assertEquals("video/x-msvideo", galleryMimeType("avi"))
        assertEquals("video/quicktime", galleryMimeType("mov"))
        assertEquals("video/x-m4v", galleryMimeType("m4v"))
        assertEquals("video/mp2t", galleryMimeType("ts"))
        assertEquals("video/x-ms-wmv", galleryMimeType("wmv"))
        assertEquals("video/mp4", galleryMimeType("xyz"))
    }

    @Test
    fun GR8_exportStorage_needsSizePlusReserve() {
        val oneGib = 1024L * 1024 * 1024
        val availableBelow = (1.4 * oneGib).toLong()
        val availableAbove = (1.6 * oneGib).toLong()

        assertFalse(hasEnoughStorageForExport(availableBytes = availableBelow, fileSizeBytes = oneGib))
        assertTrue(hasEnoughStorageForExport(availableBytes = availableAbove, fileSizeBytes = oneGib))
    }

    @Test
    fun GR9_galleryAction_perState() {
        val itemNone = createItem(DownloadState.COMPLETED, GalleryState.NONE)
        val itemSaving = createItem(DownloadState.COMPLETED, GalleryState.SAVING)
        val itemSaved = createItem(DownloadState.COMPLETED, GalleryState.SAVED)
        val itemFailed = createItem(DownloadState.COMPLETED, GalleryState.FAILED)
        val itemDownloading = createItem(DownloadState.DOWNLOADING, GalleryState.NONE)

        assertEquals(GalleryAction.SAVE, galleryActionFor(itemNone))
        assertEquals(GalleryAction.SAVING, galleryActionFor(itemSaving))
        assertEquals(GalleryAction.REMOVE, galleryActionFor(itemSaved))
        assertEquals(GalleryAction.TRY_AGAIN, galleryActionFor(itemFailed))
        assertEquals(GalleryAction.NONE, galleryActionFor(itemDownloading))
    }

    private fun createItem(
        state: DownloadState,
        galleryState: GalleryState,
    ): DownloadItem =
        DownloadItem(
            id = "id",
            kind = DownloadKind.HTTP,
            source = DownloadSource.Http("https://example.com/v.mp4"),
            metaId = "tt1",
            type = "movie",
            title = "Movie",
            videoId = "tt1",
            fileName = "v.mp4",
            state = state,
            galleryState = galleryState,
            createdAt = 1000L,
            updatedAt = 1000L,
        )
}
