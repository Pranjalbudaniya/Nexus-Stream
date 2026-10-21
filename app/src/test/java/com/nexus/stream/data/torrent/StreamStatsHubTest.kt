package com.nexus.stream.data.torrent

import com.nexus.stream.domain.model.StreamStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreamStatsHubTest {
    @Test
    fun HB1_publish_updatesStatsValue() {
        val hub = StreamStatsHub()
        assertNull(hub.stats.value)

        val stats =
            StreamStats(
                speedBps = 1_000_000L,
                peers = 12,
                aheadBytes = 16_000_000L,
                downloadedBytes = 50_000_000L,
                fileSizeBytes = 100_000_000L,
                waitMs = 0L,
            )
        hub.publish(stats)
        assertEquals(stats, hub.stats.value)
    }

    @Test
    fun HB2_clear_makesStatsNull() {
        val hub = StreamStatsHub()
        val stats =
            StreamStats(
                speedBps = 500_000L,
                peers = 5,
                aheadBytes = 8_000_000L,
                downloadedBytes = 20_000_000L,
                fileSizeBytes = 50_000_000L,
                waitMs = 1500L,
            )
        hub.publish(stats)
        assertEquals(stats, hub.stats.value)

        hub.clear()
        assertNull(hub.stats.value)
    }
}
