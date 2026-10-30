package com.nexus.stream.domain.usecase

import com.nexus.stream.R
import com.nexus.stream.domain.model.DownloadFailure
import com.nexus.stream.domain.model.DownloadItem
import com.nexus.stream.domain.model.DownloadKind
import com.nexus.stream.domain.model.DownloadLive
import com.nexus.stream.domain.model.DownloadSource
import com.nexus.stream.domain.model.DownloadState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadNotificationRulesTest {
    private fun createItem(
        id: String = "id1",
        title: String = "Test Movie",
        state: DownloadState = DownloadState.DOWNLOADING,
        totalBytes: Long = 100_000_000L,
    ) = DownloadItem(
        id = id,
        kind = DownloadKind.HTTP,
        source = DownloadSource.Http("https://example.com/video.mp4"),
        metaId = "tt1",
        type = "movie",
        title = title,
        videoId = "tt1",
        fileName = "test.mp4",
        totalBytes = totalBytes,
        downloadedBytes = 0L,
        state = state,
        createdAt = 1000L,
        updatedAt = 1000L,
    )

    @Test
    fun NT1_oneActive_showsPercentAndSpeed() {
        val item = createItem(id = "1", totalBytes = 100_000_000L).copy(downloadedBytes = 42_000_000L)
        val live = mapOf("1" to DownloadLive(bytes = 42_000_000L, speedBps = 5 * 1024 * 1024L))

        val notif = aggregateNotification(listOf(item), live)
        assertNotNull(notif)
        assertEquals(R.string.download_notif_title_one, notif?.titleRes)
        assertEquals(42, notif?.percent)
        assertEquals("5 MB/s", notif?.speedText)
    }

    @Test
    fun NT2_manyActive_showCountAndOverallPercent() {
        val item1 = createItem(id = "1", totalBytes = 100L).copy(downloadedBytes = 50L)
        val item2 = createItem(id = "2", totalBytes = 300L).copy(downloadedBytes = 150L)
        val live =
            mapOf(
                "1" to DownloadLive(bytes = 50L, speedBps = 1000L),
                "2" to DownloadLive(bytes = 150L, speedBps = 1000L),
            )

        val notif = aggregateNotification(listOf(item1, item2), live)
        assertNotNull(notif)
        assertEquals(R.string.download_notif_title_many, notif?.titleRes)
        assertEquals(listOf(2), notif?.titleArgs)
        assertEquals(50, notif?.percent)
    }

    @Test
    fun NT3_waitingForWifiOnly_saysSo() {
        val item = createItem(id = "1", state = DownloadState.WAITING_FOR_WIFI)
        val notif = aggregateNotification(listOf(item))

        assertNotNull(notif)
        assertEquals(R.string.download_notif_waiting_wifi, notif?.titleRes)
        assertNull(notif?.percent)
        assertNull(notif?.textRes)
    }

    @Test
    fun NT4_waitingForNetworkOnly_saysSo() {
        val itemNet = createItem(id = "1", state = DownloadState.WAITING_FOR_NETWORK)
        val notifNet = aggregateNotification(listOf(itemNet))
        assertNotNull(notifNet)
        assertEquals(R.string.download_notif_waiting_network, notifNet?.titleRes)

        val itemWifi = createItem(id = "2", state = DownloadState.WAITING_FOR_WIFI)
        val notifBoth = aggregateNotification(listOf(itemNet, itemWifi))
        assertNotNull(notifBoth)
        assertEquals(R.string.download_notif_waiting_both, notifBoth?.titleRes)
    }

    @Test
    fun NT5_unknownTotals_areIndeterminate() {
        val item = createItem(id = "1", totalBytes = 0L).copy(downloadedBytes = 1000L)
        val notif = aggregateNotification(listOf(item))

        assertNotNull(notif)
        assertTrue(notif?.indeterminate == true)
    }

    @Test
    fun NT6_nothingActive_hasNoNotification() {
        val completed = createItem(id = "1", state = DownloadState.COMPLETED)
        val failed = createItem(id = "2", state = DownloadState.FAILED)
        val paused = createItem(id = "3", state = DownloadState.PAUSED)

        val notif = aggregateNotification(listOf(completed, failed, paused))
        assertNull(notif)
    }

    @Test
    fun NT7_actions_pauseWhenRunning_resumeWhenAllPaused() {
        val running = createItem(id = "1", state = DownloadState.DOWNLOADING)
        val runningNotif = aggregateNotification(listOf(running))
        assertNotNull(runningNotif)
        assertTrue(runningNotif?.pauseAllAction == true)

        val paused = createItem(id = "1", state = DownloadState.PAUSED)
        val pausedNotif = aggregateNotification(listOf(paused))
        assertNull(pausedNotif)

        val pausedEvent = pausedBySystemNotification()
        assertTrue(pausedEvent.resumeActionAvailable)
    }

    @Test
    fun NT8_completionEvent_usesTitleAndPublicVersionWithout() {
        val completed = createItem(id = "1", title = "Dune", state = DownloadState.COMPLETED)
        val event = eventNotification(completed)

        assertNotNull(event)
        assertTrue(event is DownloadEventNotification.Completed)
        val done = event as DownloadEventNotification.Completed
        assertTrue(done.privateText.contains("Dune"))
        assertFalse(done.publicText?.contains("Dune") == true)
        assertEquals(R.string.download_event_public_done, done.publicTitleRes)
    }

    @Test
    fun NT9_failureEvent_hasAReasonPerFailure() {
        DownloadFailure.entries.forEach { failure ->
            val item = createItem(id = "1", state = DownloadState.FAILED).copy(failure = failure)
            val event = eventNotification(item, failure)
            assertNotNull(event)
            assertTrue(event is DownloadEventNotification.Failed)
            val failed = event as DownloadEventNotification.Failed
            assertTrue(failed.reasonRes != 0)
        }
    }

    @Test
    fun NT10_publicVersions_neverContainTitleOrUrl() {
        val item = createItem(id = "12345", title = "Secret Title", state = DownloadState.COMPLETED)
        val completed = eventNotification(item) as DownloadEventNotification.Completed
        assertFalse(completed.publicText?.contains("Secret Title") == true)
        assertFalse(completed.publicText?.contains("12345") == true)
        assertFalse(completed.publicText?.contains("example.com") == true)

        val aggregate = aggregateNotification(listOf(createItem(id = "1", title = "Secret Title")))
        assertFalse(aggregate?.publicText?.contains("Secret Title") == true)
        assertFalse(aggregate?.publicText?.contains("example.com") == true)
    }
}
