package com.nexus.stream.domain.usecase

import com.nexus.stream.domain.model.ListStatus
import com.nexus.stream.domain.model.ProgressSample
import com.nexus.stream.domain.model.ProgressTarget
import com.nexus.stream.domain.model.WatchProgress
import com.nexus.stream.domain.repository.LibraryRepository
import com.nexus.stream.domain.repository.ProgressRepository
import javax.inject.Inject

sealed interface SaveOutcome {
    data object Skipped : SaveOutcome

    data class Saved(
        val watched: Boolean,
    ) : SaveOutcome
}

class SaveProgress
    @Inject
    constructor(
        private val progress: ProgressRepository,
        private val library: LibraryRepository,
    ) {
        suspend operator fun invoke(
            target: ProgressTarget,
            sample: ProgressSample,
            firstOfSession: Boolean,
            nowMs: Long,
        ): SaveOutcome {
            if (!ProgressRules.shouldSave(sample)) return SaveOutcome.Skipped

            val d = sample.durationMs
            val p = if (sample.ended) d else sample.positionMs.coerceIn(0, d)
            val key = ProgressRules.progressKey(target.metaId, target.videoId)

            val existing = progress.get(key)
            val watchedBefore = existing?.watched == true
            val watchedNow = watchedBefore || sample.ended || ProgressRules.isWatched(p, d)

            progress.upsert(
                WatchProgress(
                    key = key,
                    metaId = target.metaId,
                    videoId = target.videoId,
                    type = target.type,
                    season = target.season,
                    episode = target.episode,
                    positionMs = p,
                    durationMs = d,
                    watched = watchedNow,
                    updatedAt = nowMs,
                ),
            )

            if (firstOfSession) {
                library.ensureSnapshot(target)
            }

            updateLibraryStatusIfNeeded(target, watchedBefore, watchedNow, firstOfSession)

            return SaveOutcome.Saved(watchedNow)
        }

        private suspend fun updateLibraryStatusIfNeeded(
            target: ProgressTarget,
            watchedBefore: Boolean,
            watchedNow: Boolean,
            firstOfSession: Boolean,
        ) {
            if (watchedBefore) return
            val item = library.getItem(target.metaId)
            if (item != null && item.inWatchlist) {
                val nextStatus =
                    when {
                        target.isMovie && watchedNow && item.status != ListStatus.COMPLETED -> {
                            ListStatus.COMPLETED
                        }

                        firstOfSession && item.status in ACTIVE_OR_ON_HOLD_STATUSES -> {
                            ListStatus.WATCHING
                        }

                        else -> {
                            null
                        }
                    }
                if (nextStatus != null) {
                    library.setStatusById(target.metaId, nextStatus)
                }
            }
        }

        private companion object {
            private val ACTIVE_OR_ON_HOLD_STATUSES =
                setOf(
                    ListStatus.PLAN_TO_WATCH,
                    ListStatus.ON_HOLD,
                    ListStatus.DROPPED,
                )
        }
    }
