package com.nexus.stream.domain.usecase

import com.nexus.stream.domain.model.ContinueKind
import com.nexus.stream.domain.model.ContinueRow
import com.nexus.stream.domain.model.ContinueWatchingItem
import com.nexus.stream.domain.model.ListStatus
import javax.inject.Inject

class ContinueWatchingRules
    @Inject
    constructor() {
        operator fun invoke(
            rows: List<ContinueRow>,
            now: Long,
        ): List<ContinueWatchingItem> = execute(rows, now)

        operator fun invoke(rows: List<ContinueRow>): List<ContinueWatchingItem> = execute(rows, 0L)

        companion object {
            const val UP_NEXT_MAX_AGE_MS = 60L * 24 * 60 * 60 * 1000
            private const val MOVIE_MIN_POSITION_MS = 60_000L
            private const val MAX_ITEMS = 20

            operator fun invoke(
                rows: List<ContinueRow>,
                now: Long,
            ): List<ContinueWatchingItem> = execute(rows, now)

            operator fun invoke(rows: List<ContinueRow>): List<ContinueWatchingItem> = execute(rows, 0L)

            private fun execute(
                rows: List<ContinueRow>,
                now: Long,
            ): List<ContinueWatchingItem> =
                rows
                    .groupBy { it.metaId }
                    .values
                    .mapNotNull { group -> group.maxByOrNull { it.updatedAt } }
                    .mapNotNull { row -> toItem(row, now) }
                    .sortedByDescending { it.updatedAt }
                    .take(MAX_ITEMS)

            private fun toItem(
                row: ContinueRow,
                now: Long,
            ): ContinueWatchingItem? {
                val name = row.name
                if (name.isNullOrBlank()) return null

                val isMovie = row.type.equals("movie", ignoreCase = true)
                return if (isMovie) {
                    if (!row.watched && row.positionMs > MOVIE_MIN_POSITION_MS) {
                        row.toItem(ContinueKind.RESUME, name)
                    } else {
                        null
                    }
                } else {
                    if (!row.watched) {
                        row.toItem(ContinueKind.RESUME, name)
                    } else if (row.qualifiesForUpNext(now)) {
                        row.toItem(ContinueKind.UP_NEXT, name, positionMs = 0L, durationMs = 0L)
                    } else {
                        null
                    }
                }
            }

            private fun ContinueRow.qualifiesForUpNext(now: Long): Boolean {
                if (season == 0 || !inWatchlist || listStatus != ListStatus.WATCHING) return false
                return (now - updatedAt) <= UP_NEXT_MAX_AGE_MS
            }

            private fun ContinueRow.toItem(
                kind: ContinueKind,
                name: String,
                positionMs: Long = this.positionMs,
                durationMs: Long = this.durationMs,
            ): ContinueWatchingItem =
                ContinueWatchingItem(
                    metaId = metaId,
                    type = type,
                    name = name,
                    poster = poster,
                    videoId = videoId,
                    season = season,
                    episode = episode,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    updatedAt = updatedAt,
                    kind = kind,
                )
        }
    }
