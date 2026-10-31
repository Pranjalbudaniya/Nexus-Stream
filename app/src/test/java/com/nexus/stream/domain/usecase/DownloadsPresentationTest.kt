package com.nexus.stream.domain.usecase

import com.nexus.stream.R
import com.nexus.stream.domain.model.DownloadFailure
import com.nexus.stream.domain.model.DownloadItem
import com.nexus.stream.domain.model.DownloadKind
import com.nexus.stream.domain.model.DownloadLive
import com.nexus.stream.domain.model.DownloadPhase
import com.nexus.stream.domain.model.DownloadSource
import com.nexus.stream.domain.model.DownloadState
import com.nexus.stream.domain.model.GalleryFailure
import com.nexus.stream.domain.model.GalleryState
import com.nexus.stream.domain.model.PauseReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadsPresentationTest {
    private fun createItem(
        id: String = "id1",
        state: DownloadState = DownloadState.DOWNLOADING,
        title: String = "Test Movie",
        metaId: String = "tt1",
        videoId: String = "tt1:1:1",
    ): DownloadItem =
        DownloadItem(
            id = id,
            kind = DownloadKind.HTTP,
            source = DownloadSource.Http("https://example.com/v.mp4"),
            metaId = metaId,
            type = "movie",
            title = title,
            metaName = null,
            videoId = videoId,
            season = null,
            episode = null,
            fileName = "$id.mp4",
            totalBytes = 1000L,
            downloadedBytes = 0L,
            state = state,
            pauseReason = null,
            failure = null,
            createdAt = 100L,
            updatedAt = 100L,
            completedAt = null,
        )

    @Test
    fun DP1_sections_areInProgressProblemsReady_inThatOrder() {
        val items =
            listOf(
                createItem(id = "1", state = DownloadState.DOWNLOADING),
                createItem(id = "2", state = DownloadState.FAILED).copy(failure = DownloadFailure.NETWORK),
                createItem(id = "3", state = DownloadState.COMPLETED),
            )
        val sections = groupDownloads(items, familyMode = false)
        assertEquals(3, sections.size)
        assertEquals(DownloadSectionKind.IN_PROGRESS, sections[0].kind)
        assertEquals(DownloadSectionKind.PROBLEMS, sections[1].kind)
        assertEquals(DownloadSectionKind.READY, sections[2].kind)
    }

    @Test
    fun DP2_emptySections_areSkipped() {
        val items = listOf(createItem(id = "1", state = DownloadState.COMPLETED))
        val sections = groupDownloads(items, familyMode = false)
        assertEquals(1, sections.size)
        assertEquals(DownloadSectionKind.READY, sections[0].kind)
    }

    @Test
    fun DP3_inProgress_ordersActiveThenWaitingThenPaused() {
        val paused = createItem(id = "1", state = DownloadState.PAUSED).copy(createdAt = 1L)
        val queued = createItem(id = "2", state = DownloadState.QUEUED).copy(createdAt = 2L)
        val downloading = createItem(id = "3", state = DownloadState.DOWNLOADING).copy(createdAt = 3L)
        val waitingWifi = createItem(id = "4", state = DownloadState.WAITING_FOR_WIFI).copy(createdAt = 4L)

        val sections = groupDownloads(listOf(paused, queued, downloading, waitingWifi), familyMode = false)
        assertEquals(1, sections.size)
        val ids = sections[0].items.map { it.id }
        assertEquals(listOf("3", "2", "4", "1"), ids)
    }

    @Test
    fun DP4_ready_isNewestCompletedFirst() {
        val item1 = createItem(id = "1", state = DownloadState.COMPLETED).copy(completedAt = 100L)
        val item2 = createItem(id = "2", state = DownloadState.COMPLETED).copy(completedAt = 200L)

        val sections = groupDownloads(listOf(item1, item2), familyMode = false)
        assertEquals(1, sections.size)
        assertEquals(listOf("2", "1"), sections[0].items.map { it.id })
    }

    @Test
    fun DP5_status_downloading_percentSpeedEta() {
        val item = createItem(state = DownloadState.DOWNLOADING).copy(downloadedBytes = 420L, totalBytes = 1000L)
        val live = DownloadLive(bytes = 420L, speedBps = 5 * 1024 * 1024L, etaSeconds = 180L)

        val status = rowStatus(item, live)
        assertEquals(R.string.downloads_status_downloading, status.stringRes)
        assertEquals(42, status.args[0])
        assertNotNull(status.args[1])
        assertNotNull(status.args[2])
    }

    @Test
    fun DP6_status_downloading_unknownTotal() {
        val item = createItem(state = DownloadState.DOWNLOADING).copy(downloadedBytes = 300L, totalBytes = 0L)
        val live = DownloadLive(bytes = 300L, speedBps = 1000L)

        val status = rowStatus(item, live)
        assertEquals(R.string.downloads_status_downloading_unknown, status.stringRes)
        assertEquals(300L, status.args[0])
        assertEquals("1000 B/s", status.args[1])
    }

    @Test
    fun DP7_status_queued_andWaiting() {
        val queued = rowStatus(createItem(state = DownloadState.QUEUED))
        val waitingWifi = rowStatus(createItem(state = DownloadState.WAITING_FOR_WIFI))
        val waitingNet = rowStatus(createItem(state = DownloadState.WAITING_FOR_NETWORK))

        assertEquals(R.string.downloads_status_queued, queued.stringRes)
        assertEquals(R.string.downloads_status_wifi, waitingWifi.stringRes)
        assertEquals(R.string.downloads_status_network, waitingNet.stringRes)
    }

    @Test
    fun DP8_status_downloadingWithZeroSpeed_saysStarting() {
        val item = createItem(state = DownloadState.DOWNLOADING).copy(downloadedBytes = 100L, totalBytes = 1000L)
        val live = DownloadLive(bytes = 100L, speedBps = 0L)

        val status = rowStatus(item, live)
        assertEquals(R.string.downloads_status_starting, status.stringRes)
        assertEquals(10, status.args[0])
    }

    @Test
    fun DP9_status_waitingForNetwork_whenOffline() {
        val item =
            createItem(state = DownloadState.WAITING_FOR_NETWORK).copy(
                downloadedBytes = 500L,
                totalBytes = 1000L,
            )
        val status = rowStatus(item)
        assertEquals(R.string.downloads_status_network, status.stringRes)
        assertTrue(status.args.isEmpty())
    }

    @Test
    fun DP10_status_pausedByUser_hasPercent() {
        val item =
            createItem(state = DownloadState.PAUSED).copy(
                pauseReason = PauseReason.USER,
                downloadedBytes = 400L,
                totalBytes = 1000L,
            )
        val status = rowStatus(item)
        assertEquals(R.string.downloads_status_paused, status.stringRes)
        assertEquals(40, status.args[0])
    }

    @Test
    fun DP11_status_pausedBySystem_saysSo() {
        val item =
            createItem(state = DownloadState.PAUSED).copy(
                pauseReason = PauseReason.SYSTEM_TIME_LIMIT,
                downloadedBytes = 400L,
                totalBytes = 1000L,
            )
        val status = rowStatus(item)
        assertEquals(R.string.downloads_status_paused_system, status.stringRes)
        assertEquals(40, status.args[0])
    }

    @Test
    fun DP12_status_failed_hasAReasonPerFailure() {
        DownloadFailure.entries.forEach { failure ->
            val item = createItem(state = DownloadState.FAILED).copy(failure = failure)
            val status = rowStatus(item)
            assertTrue(status.stringRes != 0)
        }
    }

    @Test
    fun DP13_status_completed_sizeAndDate() {
        val t = 1_700_000_000_000L
        val item = createItem(state = DownloadState.COMPLETED).copy(totalBytes = 1_400_000_000L, completedAt = t)
        val status = rowStatus(item)
        assertEquals(R.string.downloads_status_done, status.stringRes)
        assertEquals(1_400_000_000L, status.args[0])
        assertEquals(t, status.args[1])
    }

    @Test
    fun DP14_primaryAction_perState() {
        assertEquals(RowAction.PAUSE, primaryAction(createItem(state = DownloadState.DOWNLOADING)))
        assertEquals(RowAction.PAUSE, primaryAction(createItem(state = DownloadState.QUEUED)))
        assertEquals(RowAction.PAUSE, primaryAction(createItem(state = DownloadState.WAITING_FOR_WIFI)))
        assertEquals(RowAction.RESUME, primaryAction(createItem(state = DownloadState.PAUSED)))
        assertEquals(
            RowAction.RETRY,
            primaryAction(createItem(state = DownloadState.FAILED).copy(failure = DownloadFailure.SERVER_ERROR)),
        )
        assertEquals(
            RowAction.FIND_NEW_LINK,
            primaryAction(createItem(state = DownloadState.FAILED).copy(failure = DownloadFailure.LINK_EXPIRED)),
        )
        assertEquals(
            RowAction.REMOVE,
            primaryAction(createItem(state = DownloadState.FAILED).copy(failure = DownloadFailure.FILE_MISSING)),
        )
        assertEquals(RowAction.NONE, primaryAction(createItem(state = DownloadState.COMPLETED)))
    }

    @Test
    fun DP15_rowTitle_usesTheSeriesNameWhenPresent() {
        val seriesItem = createItem(title = "Show S1 E2").copy(metaName = "Show")
        val movieItem = createItem(title = "Movie Title")

        assertEquals("Show", rowTitle(seriesItem))
        assertEquals("Movie Title", rowTitle(movieItem))
    }

    @Test
    fun DP16_storageSummary_usedAndLow() {
        val items =
            listOf(
                createItem(id = "1").copy(downloadedBytes = 1_000_000_000L),
                createItem(id = "2").copy(downloadedBytes = 2_000_000_000L),
            )
        val lowFree = 500L * 1024L * 1024L
        val highFree = 5L * 1024L * 1024L * 1024L

        val summaryLow = storageSummary(items, lowFree)
        assertEquals(3_000_000_000L, summaryLow.usedBytes)
        assertTrue(summaryLow.low)

        val summaryHigh = storageSummary(items, highFree)
        assertEquals(3_000_000_000L, summaryHigh.usedBytes)
        assertFalse(summaryHigh.low)
    }

    @Test
    fun DP17_familyFilter_hidesAdultNames() {
        val clean = createItem(id = "clean", title = "Dune")
        val adult = createItem(id = "adult", title = "Hot XXX Video")

        val withFamilyOn = groupDownloads(listOf(clean, adult), familyMode = true)
        val itemsOn = withFamilyOn.flatMap { it.items }
        assertTrue(itemsOn.any { it.id == "clean" })
        assertFalse(itemsOn.any { it.id == "adult" })

        val withFamilyOff = groupDownloads(listOf(clean, adult), familyMode = false)
        val itemsOff = withFamilyOff.flatMap { it.items }
        assertTrue(itemsOff.any { it.id == "clean" })
        assertTrue(itemsOff.any { it.id == "adult" })
    }

    @Test
    fun DP18_detailButtonState_perState() {
        assertEquals(DetailDownloadState.None, detailButtonState(emptyList(), "m1", "v1"))
        assertEquals(
            DetailDownloadState.Queued,
            detailButtonState(listOf(createItem(state = DownloadState.QUEUED)), "tt1", "tt1:1:1"),
        )
        val dlItem = createItem(state = DownloadState.DOWNLOADING).copy(downloadedBytes = 420L, totalBytes = 1000L)
        assertEquals(
            DetailDownloadState.Downloading(42),
            detailButtonState(listOf(dlItem), "tt1", "tt1:1:1"),
        )
        assertEquals(
            DetailDownloadState.Paused,
            detailButtonState(listOf(createItem(state = DownloadState.PAUSED)), "tt1", "tt1:1:1"),
        )
        assertEquals(
            DetailDownloadState.Failed,
            detailButtonState(listOf(createItem(state = DownloadState.FAILED)), "tt1", "tt1:1:1"),
        )
        assertEquals(
            DetailDownloadState.Completed,
            detailButtonState(listOf(createItem(state = DownloadState.COMPLETED)), "tt1", "tt1:1:1"),
        )
    }

    @Test
    fun DP19_detailButtonState_picksTheMostRelevantItem() {
        val failed = createItem(id = "1", state = DownloadState.FAILED)
        val downloading =
            createItem(id = "2", state = DownloadState.DOWNLOADING).copy(
                downloadedBytes = 500L,
                totalBytes = 1000L,
            )
        val completed = createItem(id = "3", state = DownloadState.COMPLETED)

        assertEquals(
            DetailDownloadState.Downloading(50),
            detailButtonState(listOf(failed, downloading), "tt1", "tt1:1:1"),
        )
        assertEquals(
            DetailDownloadState.Completed,
            detailButtonState(listOf(completed, failed), "tt1", "tt1:1:1"),
        )
    }

    @Test
    fun DP20_activeCount_countsDownloadingQueuedAndWaiting() {
        val items =
            listOf(
                createItem(id = "1", state = DownloadState.DOWNLOADING),
                createItem(id = "2", state = DownloadState.QUEUED),
                createItem(id = "3", state = DownloadState.WAITING_FOR_WIFI),
                createItem(id = "4", state = DownloadState.WAITING_FOR_NETWORK),
                createItem(id = "5", state = DownloadState.PAUSED),
                createItem(id = "6", state = DownloadState.FAILED),
                createItem(id = "7", state = DownloadState.COMPLETED),
            )
        assertEquals(4, activeCount(items))
    }

    @Test
    fun DP21_status_connectingAndMetadata() {
        val item = createItem(state = DownloadState.DOWNLOADING).copy(kind = DownloadKind.TORRENT)
        val liveConnecting = DownloadLive(bytes = 0L, speedBps = 0L, phase = DownloadPhase.CONNECTING)
        val statusConnecting = rowStatus(item, liveConnecting)
        assertEquals(R.string.downloads_status_peers_looking, statusConnecting.stringRes)

        val liveMetadata = DownloadLive(bytes = 0L, speedBps = 0L, phase = DownloadPhase.FETCHING_METADATA)
        val statusMetadata = rowStatus(item, liveMetadata)
        assertEquals(R.string.downloads_status_metadata, statusMetadata.stringRes)
    }

    @Test
    fun DP22_status_checkingFiles_withAndWithoutPercent() {
        val itemWithPercent =
            createItem(state = DownloadState.DOWNLOADING).copy(
                kind = DownloadKind.TORRENT,
                downloadedBytes = 400L,
                totalBytes = 1000L,
            )
        val liveChecking = DownloadLive(bytes = 400L, speedBps = 0L, phase = DownloadPhase.CHECKING_FILES)
        val statusPercent = rowStatus(itemWithPercent, liveChecking)
        assertEquals(R.string.downloads_status_checking_percent, statusPercent.stringRes)
        assertEquals(listOf(40), statusPercent.args)

        val itemWithoutPercent =
            createItem(state = DownloadState.DOWNLOADING).copy(
                kind = DownloadKind.TORRENT,
                downloadedBytes = 0L,
                totalBytes = 0L,
            )
        val statusNoPercent = rowStatus(itemWithoutPercent, liveChecking)
        assertEquals(R.string.downloads_status_checking, statusNoPercent.stringRes)
        assertTrue(statusNoPercent.args.isEmpty())
    }

    @Test
    fun DP23_status_stalled() {
        val item = createItem(state = DownloadState.DOWNLOADING).copy(kind = DownloadKind.TORRENT)
        val liveStalled = DownloadLive(bytes = 100L, speedBps = 0L, phase = DownloadPhase.STALLED)
        val status = rowStatus(item, liveStalled)
        assertEquals(R.string.downloads_status_stalled, status.stringRes)
        assertEquals(RowAction.PAUSE, primaryAction(item))
    }

    @Test
    fun DP24_status_torrentTransferring_includesPeers() {
        val item =
            createItem(state = DownloadState.DOWNLOADING).copy(
                kind = DownloadKind.TORRENT,
                downloadedBytes = 420L,
                totalBytes = 1000L,
            )
        val live =
            DownloadLive(
                bytes = 420L,
                speedBps = 5 * 1024 * 1024L,
                etaSeconds = 180L,
                peers = 5,
                phase = DownloadPhase.TRANSFERRING,
            )
        val status = rowStatus(item, live)
        assertEquals(R.string.downloads_status_torrent, status.stringRes)
        assertEquals(42, status.args[0])
        assertEquals("5 MB/s", status.args[1])
        assertEquals("3 min", status.args[2])
        assertEquals(5, status.args[3])

        val pickItem = createItem(state = DownloadState.NEEDS_FILE_PICK)
        val pickStatus = rowStatus(pickItem)
        assertEquals(R.string.downloads_action_choose_file, pickStatus.stringRes)
        assertEquals(RowAction.CHOOSE_FILE, primaryAction(pickItem))
    }

    @Test
    fun DP25_actionIcon_perAction() {
        assertEquals(ActionIcon.PAUSE, actionIcon(RowAction.PAUSE))
        assertEquals(ActionIcon.PLAY, actionIcon(RowAction.RESUME))
        assertEquals(ActionIcon.REFRESH, actionIcon(RowAction.RETRY))
        assertEquals(ActionIcon.SEARCH, actionIcon(RowAction.FIND_NEW_LINK))
        assertEquals(ActionIcon.DELETE, actionIcon(RowAction.REMOVE))
        assertEquals(ActionIcon.LIST, actionIcon(RowAction.CHOOSE_FILE))
        assertEquals(null, actionIcon(RowAction.NONE))
    }

    @Test
    fun DP26_actionIcon_everyActionHasAContentDescription() {
        val nonNoneActions = RowAction.entries.filter { it != RowAction.NONE }
        val stringIds = nonNoneActions.map { actionDescription(it) }
        assertTrue(stringIds.all { it != 0 })
        assertEquals(nonNoneActions.size, stringIds.toSet().size)
    }

    @Test
    fun DP27_storageCard_isOneLine_usedAndFree() {
        val items =
            listOf(
                createItem(state = DownloadState.COMPLETED).copy(totalBytes = 248L * 1024 * 1024),
            )
        val free12Gb = 12L * 1024 * 1024 * 1024
        val model = storageCardModel(items, free12Gb)
        assertEquals(248L * 1024 * 1024, model.usedBytes)
        assertEquals(free12Gb, model.freeBytes)
        assertFalse(model.low)
    }

    @Test
    fun DP28_storageCard_headerAction_pauseResumeOrNone() {
        val downloading = listOf(createItem(state = DownloadState.DOWNLOADING))
        val paused = listOf(createItem(state = DownloadState.PAUSED))
        val completed = listOf(createItem(state = DownloadState.COMPLETED))

        assertEquals(
            StorageHeaderAction.PAUSE_ALL,
            storageCardModel(downloading, 10L * 1024 * 1024 * 1024).headerAction,
        )
        assertEquals(
            StorageHeaderAction.RESUME_ALL,
            storageCardModel(paused, 10L * 1024 * 1024 * 1024).headerAction,
        )
        assertEquals(
            StorageHeaderAction.NONE,
            storageCardModel(completed, 10L * 1024 * 1024 * 1024).headerAction,
        )
    }

    @Test
    fun DP29_storageCard_deleteAll_onlyWithCompletedItems() {
        val withCompleted = listOf(createItem(state = DownloadState.COMPLETED))
        val withoutCompleted = listOf(createItem(state = DownloadState.DOWNLOADING))

        assertTrue(storageCardModel(withCompleted, 10L * 1024 * 1024 * 1024).showDeleteAll)
        assertFalse(storageCardModel(withoutCompleted, 10L * 1024 * 1024 * 1024).showDeleteAll)
    }

    @Test
    fun DP30_storageCard_lowStorage_warns() {
        val items = listOf(createItem(state = DownloadState.COMPLETED))
        val lowFree = 800L * 1024 * 1024
        val highFree = 5L * 1024 * 1024 * 1024

        assertTrue(storageCardModel(items, lowFree).low)
        assertFalse(storageCardModel(items, highFree).low)
    }

    @Test
    fun DP31_status_completed_inGallerySuffix() {
        val itemSaved = createItem(state = DownloadState.COMPLETED).copy(galleryState = GalleryState.SAVED)
        val statusSaved = rowStatus(itemSaved)
        assertEquals(R.string.downloads_status_in_gallery, statusSaved.stringRes)
        val doneStatus = statusSaved.args[0] as StatusText
        assertEquals(R.string.downloads_status_done, doneStatus.stringRes)

        val itemNone = createItem(state = DownloadState.COMPLETED).copy(galleryState = GalleryState.NONE)
        val statusNone = rowStatus(itemNone)
        assertEquals(R.string.downloads_status_done, statusNone.stringRes)
    }

    @Test
    fun DP32_status_saving_showsPercent() {
        val item =
            createItem(state = DownloadState.COMPLETED).copy(
                galleryState = GalleryState.SAVING,
                totalBytes = 1000L,
            )
        val live = DownloadLive(bytes = 1000L, speedBps = 0L, copiedBytes = 500L)
        val status = rowStatus(item, live)
        assertEquals(R.string.downloads_status_saving_gallery, status.stringRes)
        assertEquals(50, status.args[0])
    }

    @Test
    fun DP33_status_galleryFailed_hasASecondLineWithTheReason() {
        val failures =
            listOf(
                GalleryFailure.NO_SPACE to R.string.downloads_gallery_reason_space,
                GalleryFailure.WRITE_FAILED to R.string.downloads_gallery_reason_write,
                GalleryFailure.FILE_MISSING to R.string.downloads_gallery_reason_missing,
                GalleryFailure.UNAVAILABLE to R.string.downloads_gallery_reason_unavailable,
            )
        for ((failure, expectedReasonRes) in failures) {
            val item =
                createItem(state = DownloadState.COMPLETED).copy(
                    galleryState = GalleryState.FAILED,
                    galleryFailure = failure,
                )
            val status = rowStatus(item)
            assertEquals(R.string.downloads_gallery_failed, status.stringRes)
            assertEquals(expectedReasonRes, status.args[0])
        }
    }

    @Test
    fun DP34_rowMenu_perState() {
        val completedNone = createItem(state = DownloadState.COMPLETED).copy(galleryState = GalleryState.NONE)
        assertEquals(listOf(RowMenuItem.SAVE_TO_GALLERY, RowMenuItem.DELETE), rowMenu(completedNone))

        val completedSaved = createItem(state = DownloadState.COMPLETED).copy(galleryState = GalleryState.SAVED)
        assertEquals(listOf(RowMenuItem.REMOVE_FROM_GALLERY, RowMenuItem.DELETE), rowMenu(completedSaved))

        val completedFailed = createItem(state = DownloadState.COMPLETED).copy(galleryState = GalleryState.FAILED)
        assertEquals(listOf(RowMenuItem.RETRY_GALLERY, RowMenuItem.DELETE), rowMenu(completedFailed))

        val downloading = createItem(state = DownloadState.DOWNLOADING)
        assertEquals(listOf(RowMenuItem.CANCEL), rowMenu(downloading))

        val failed = createItem(state = DownloadState.FAILED)
        assertEquals(listOf(RowMenuItem.REMOVE), rowMenu(failed))
    }
}
