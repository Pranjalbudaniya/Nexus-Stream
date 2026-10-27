package com.nexus.stream.domain.usecase

import com.nexus.stream.domain.model.ContinueRow
import com.nexus.stream.domain.model.EpisodeLabel
import com.nexus.stream.domain.model.HistoryBucket
import com.nexus.stream.domain.model.HistoryEntry
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

fun latestPerTitle(rows: List<ContinueRow>): List<ContinueRow> {
    val byMeta = LinkedHashMap<String, MutableList<ContinueRow>>()
    for (row in rows) {
        byMeta.getOrPut(row.metaId) { mutableListOf() }.add(row)
    }
    val comparator =
        compareBy<ContinueRow> { it.updatedAt }
            .thenBy { it.season ?: -1 }
            .thenBy { it.episode ?: -1 }
    return byMeta.values.map { it.maxWith(comparator) }
}

fun toHistoryEntry(row: ContinueRow): HistoryEntry {
    val progress =
        if (row.watched || row.durationMs <= 0L) {
            null
        } else {
            (row.positionMs.toFloat() / row.durationMs.toFloat()).coerceIn(0f, 1f)
        }
    return HistoryEntry(
        metaId = row.metaId,
        type = row.type,
        name = row.name,
        poster = row.poster,
        season = row.season,
        episode = row.episode,
        videoId = row.videoId,
        progress = progress,
        watched = row.watched,
        updatedAt = row.updatedAt,
    )
}

private const val DAYS_IN_WEEK = 7L
private const val DAYS_IN_MONTH = 30L

fun bucketOf(
    updatedAt: Long,
    nowMs: Long,
    zone: ZoneId,
): HistoryBucket {
    val nowDate = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
    val updatedDate = Instant.ofEpochMilli(updatedAt).atZone(zone).toLocalDate()
    val daysBetween = ChronoUnit.DAYS.between(updatedDate, nowDate)
    return when {
        daysBetween <= 0L -> HistoryBucket.TODAY
        daysBetween == 1L -> HistoryBucket.YESTERDAY
        daysBetween <= DAYS_IN_WEEK -> HistoryBucket.LAST_7_DAYS
        daysBetween <= DAYS_IN_MONTH -> HistoryBucket.LAST_30_DAYS
        else -> HistoryBucket.EARLIER
    }
}

fun groupHistory(
    entries: List<HistoryEntry>,
    nowMs: Long,
    zone: ZoneId,
): List<Pair<HistoryBucket, List<HistoryEntry>>> {
    val byBucket = entries.groupBy { bucketOf(it.updatedAt, nowMs, zone) }
    val result = mutableListOf<Pair<HistoryBucket, List<HistoryEntry>>>()
    for (bucket in HistoryBucket.entries) {
        val list = byBucket[bucket]
        if (!list.isNullOrEmpty()) {
            val sortedList =
                list.sortedWith(
                    compareByDescending<HistoryEntry> { it.updatedAt }
                        .thenBy { it.metaId },
                )
            result.add(bucket to sortedList)
        }
    }
    return result
}

fun episodeLabelOf(
    season: Int?,
    episode: Int?,
): EpisodeLabel? =
    when {
        season != null && episode != null -> EpisodeLabel.SeasonEpisode(season, episode)
        episode != null -> EpisodeLabel.EpisodeOnly(episode)
        season != null -> EpisodeLabel.SeasonOnly(season)
        else -> null
    }

fun filterHistoryByType(
    entries: List<HistoryEntry>,
    type: String?,
): List<HistoryEntry> =
    if (type == null) {
        entries
    } else {
        entries.filter { it.type.equals(type, ignoreCase = true) }
    }

fun filterHistoryFamily(
    entries: List<HistoryEntry>,
    familyMode: Boolean,
): List<HistoryEntry> =
    if (!familyMode) {
        entries
    } else {
        entries.filterNot { it.name != null && isAdultText(it.name) }
    }
