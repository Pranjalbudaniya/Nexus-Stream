package com.nexus.stream.domain.usecase

import com.nexus.stream.domain.model.ContinueRow
import com.nexus.stream.domain.model.ContinueWatchingItem
import javax.inject.Inject

class ContinueWatchingRules
    @Inject
    constructor() {
        operator fun invoke(rows: List<ContinueRow>): List<ContinueWatchingItem> = execute(rows)

        companion object {
            private const val MOVIE_MIN_POSITION_MS = 60_000L
            private const val MAX_ITEMS = 20

            operator fun invoke(rows: List<ContinueRow>): List<ContinueWatchingItem> = execute(rows)

            private fun execute(rows: List<ContinueRow>): List<ContinueWatchingItem> {
                val latestPerMeta =
                    rows
                        .groupBy { it.metaId }
                        .values
                        .mapNotNull { group ->
                            group.maxByOrNull { it.updatedAt }
                        }

                return latestPerMeta
                    .mapNotNull { row ->
                        val name = row.name
                        if (name.isNullOrBlank()) return@mapNotNull null

                        val qualifies =
                            if (row.type.equals("movie", ignoreCase = true)) {
                                !row.watched && row.positionMs > MOVIE_MIN_POSITION_MS
                            } else {
                                !row.watched
                            }

                        if (!qualifies) return@mapNotNull null

                        ContinueWatchingItem(
                            metaId = row.metaId,
                            type = row.type,
                            name = name,
                            poster = row.poster,
                            videoId = row.videoId,
                            season = row.season,
                            episode = row.episode,
                            positionMs = row.positionMs,
                            durationMs = row.durationMs,
                            updatedAt = row.updatedAt,
                        )
                    }.sortedByDescending { it.updatedAt }
                    .take(MAX_ITEMS)
            }
        }
    }
