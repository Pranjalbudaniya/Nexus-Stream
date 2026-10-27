package com.nexus.stream.domain.usecase

import com.nexus.stream.domain.model.ContinueRow
import com.nexus.stream.domain.model.EpisodeLabel
import com.nexus.stream.domain.model.HistoryBucket
import com.nexus.stream.domain.model.HistoryEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class HistoryRulesTest {
    private fun createRow(
        metaId: String,
        updatedAt: Long = 1000L,
        videoId: String = "v1",
        type: String = "series",
    ): ContinueRow =
        ContinueRow(
            metaId = metaId,
            type = type,
            name = "Title",
            poster = null,
            videoId = videoId,
            season = null,
            episode = null,
            positionMs = 0L,
            durationMs = 0L,
            watched = false,
            updatedAt = updatedAt,
        )

    private fun createEntry(
        metaId: String,
        name: String? = "Title",
        type: String = "movie",
        updatedAt: Long = 1000L,
    ): HistoryEntry =
        HistoryEntry(
            metaId = metaId,
            type = type,
            name = name,
            poster = null,
            season = null,
            episode = null,
            videoId = "v_$metaId",
            progress = null,
            watched = false,
            updatedAt = updatedAt,
        )

    @Test
    fun HS1_latestPerTitle_picksTheNewestRow() {
        val rows =
            listOf(
                createRow("t1", updatedAt = 100L, videoId = "v1"),
                createRow("t1", updatedAt = 300L, videoId = "v2"),
                createRow("t1", updatedAt = 200L, videoId = "v3"),
            )
        val result = latestPerTitle(rows)
        assertEquals(1, result.size)
        assertEquals("v2", result.first().videoId)
    }

    @Test
    fun HS2_latestPerTitle_tieBreaksByHigherSeasonThenEpisode() {
        val rows =
            listOf(
                createRow("t1", updatedAt = 100L, videoId = "s1e3").copy(season = 1, episode = 3),
                createRow("t1", updatedAt = 100L, videoId = "s2e1").copy(season = 2, episode = 1),
                createRow("t1", updatedAt = 100L, videoId = "s2e4").copy(season = 2, episode = 4),
            )
        val result = latestPerTitle(rows)
        assertEquals(1, result.size)
        assertEquals("s2e4", result.first().videoId)
    }

    @Test
    fun HS3_latestPerTitle_tieOnOneMovie_keepsOneRow() {
        val rows =
            listOf(
                createRow("m1", updatedAt = 100L, videoId = "v1", type = "movie"),
                createRow("m1", updatedAt = 100L, videoId = "v2", type = "movie"),
            )
        val result = latestPerTitle(rows)
        assertEquals(1, result.size)
    }

    @Test
    fun HS4_entry_inProgress_hasProgressFraction() {
        val row = createRow("m1").copy(positionMs = 30_000L, durationMs = 120_000L, watched = false)
        val entry = toHistoryEntry(row)
        assertEquals(0.25f, entry.progress ?: 0f, 0.001f)
        assertEquals(false, entry.watched)
    }

    @Test
    fun HS5_entry_watched_hasNoProgress() {
        val row = createRow("m1").copy(positionMs = 119_000L, durationMs = 120_000L, watched = true)
        val entry = toHistoryEntry(row)
        assertNull(entry.progress)
        assertTrue(entry.watched)
    }

    @Test
    fun HS6_entry_unknownDuration_hasNoProgress() {
        val row = createRow("m1").copy(positionMs = 10_000L, durationMs = 0L, watched = false)
        val entry = toHistoryEntry(row)
        assertNull(entry.progress)
    }

    @Test
    fun HS7_entry_progressIsClampedToOne() {
        val row = createRow("m1").copy(positionMs = 200_000L, durationMs = 120_000L, watched = false)
        val entry = toHistoryEntry(row)
        assertEquals(1.0f, entry.progress ?: 0f, 0.001f)
    }

    @Test
    fun HS8_bucket_sameLocalDay_isToday() {
        val zone = ZoneId.of("UTC")
        val now = Instant.parse("2026-10-28T12:00:00Z").toEpochMilli()
        val updated = Instant.parse("2026-10-28T08:00:00Z").toEpochMilli()
        assertEquals(HistoryBucket.TODAY, bucketOf(updated, now, zone))
    }

    @Test
    fun HS9_bucket_previousLocalDay_isYesterday() {
        val zone = ZoneId.of("UTC")
        val now = Instant.parse("2026-10-28T12:00:00Z").toEpochMilli()
        val updated = Instant.parse("2026-10-27T20:00:00Z").toEpochMilli()
        assertEquals(HistoryBucket.YESTERDAY, bucketOf(updated, now, zone))
    }

    @Test
    fun HS10_bucket_twoToSevenDays_isLast7Days() {
        val zone = ZoneId.of("UTC")
        val now = Instant.parse("2026-10-28T12:00:00Z").toEpochMilli()
        val twoDaysAgo = Instant.parse("2026-10-26T12:00:00Z").toEpochMilli()
        val sevenDaysAgo = Instant.parse("2026-10-21T12:00:00Z").toEpochMilli()
        assertEquals(HistoryBucket.LAST_7_DAYS, bucketOf(twoDaysAgo, now, zone))
        assertEquals(HistoryBucket.LAST_7_DAYS, bucketOf(sevenDaysAgo, now, zone))
    }

    @Test
    fun HS11_bucket_eightToThirtyDays_isLast30Days() {
        val zone = ZoneId.of("UTC")
        val now = Instant.parse("2026-10-28T12:00:00Z").toEpochMilli()
        val eightDaysAgo = Instant.parse("2026-10-20T12:00:00Z").toEpochMilli()
        val thirtyDaysAgo = Instant.parse("2026-09-28T12:00:00Z").toEpochMilli()
        assertEquals(HistoryBucket.LAST_30_DAYS, bucketOf(eightDaysAgo, now, zone))
        assertEquals(HistoryBucket.LAST_30_DAYS, bucketOf(thirtyDaysAgo, now, zone))
    }

    @Test
    fun HS12_bucket_olderThanThirtyDays_isEarlier() {
        val zone = ZoneId.of("UTC")
        val now = Instant.parse("2026-10-28T12:00:00Z").toEpochMilli()
        val thirtyOneDaysAgo = Instant.parse("2026-09-27T12:00:00Z").toEpochMilli()
        assertEquals(HistoryBucket.EARLIER, bucketOf(thirtyOneDaysAgo, now, zone))
    }

    @Test
    fun HS13_bucket_futureTimestamp_isToday() {
        val zone = ZoneId.of("UTC")
        val now = Instant.parse("2026-10-28T12:00:00Z").toEpochMilli()
        val future = Instant.parse("2026-10-28T13:00:00Z").toEpochMilli()
        assertEquals(HistoryBucket.TODAY, bucketOf(future, now, zone))
    }

    @Test
    fun HS14_bucket_usesLocalMidnight_notRolling24Hours() {
        val zone = ZoneId.of("Asia/Kolkata")
        val updated = Instant.parse("2026-10-27T18:00:00Z").toEpochMilli()
        val now = Instant.parse("2026-10-27T19:00:00Z").toEpochMilli()
        assertEquals(HistoryBucket.YESTERDAY, bucketOf(updated, now, zone))
    }

    @Test
    fun HS15_group_keepsOrder_andSkipsEmptyBuckets() {
        val zone = ZoneId.of("UTC")
        val now = Instant.parse("2026-10-28T12:00:00Z").toEpochMilli()
        val today1 = Instant.parse("2026-10-28T10:00:00Z").toEpochMilli()
        val today2 = Instant.parse("2026-10-28T09:00:00Z").toEpochMilli()
        val last30Days = Instant.parse("2026-10-15T12:00:00Z").toEpochMilli()

        val entries =
            listOf(
                createEntry("1", updatedAt = today1),
                createEntry("2", updatedAt = today2),
                createEntry("3", updatedAt = last30Days),
            )
        val sections = groupHistory(entries, now, zone)
        assertEquals(2, sections.size)
        assertEquals(HistoryBucket.TODAY, sections[0].first)
        assertEquals(listOf("1", "2"), sections[0].second.map { it.metaId })
        assertEquals(HistoryBucket.LAST_30_DAYS, sections[1].first)
        assertEquals(listOf("3"), sections[1].second.map { it.metaId })
    }

    @Test
    fun HS16_episodeLabel_seasonAndEpisode() {
        assertEquals(EpisodeLabel.SeasonEpisode(2, 5), episodeLabelOf(2, 5))
    }

    @Test
    fun HS17_episodeLabel_episodeOnly() {
        assertEquals(EpisodeLabel.EpisodeOnly(5), episodeLabelOf(null, 5))
    }

    @Test
    fun HS18_episodeLabel_seasonOnly() {
        assertEquals(EpisodeLabel.SeasonOnly(2), episodeLabelOf(2, null))
    }

    @Test
    fun HS19_episodeLabel_none_isNull() {
        assertNull(episodeLabelOf(null, null))
    }

    @Test
    fun HS20_typeFilter_keepsOnlyThatType() {
        val entries =
            listOf(
                createEntry("1", type = "movie"),
                createEntry("2", type = "series"),
                createEntry("3", type = "movie"),
            )
        val filtered = filterHistoryByType(entries, "movie")
        assertEquals(listOf("1", "3"), filtered.map { it.metaId })
        assertEquals(entries, filterHistoryByType(entries, null))
    }

    @Test
    fun HS21_familyFilter_hidesAdultNames_andKeepsUnknownNames() {
        val entries =
            listOf(
                createEntry("1", name = "Dune"),
                createEntry("2", name = "Hot XXX Video"),
                createEntry("3", name = null),
            )
        val filtered = filterHistoryFamily(entries, familyMode = true)
        assertEquals(listOf("1", "3"), filtered.map { it.metaId })
    }
}
