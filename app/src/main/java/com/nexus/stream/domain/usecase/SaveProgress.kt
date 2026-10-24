package com.nexus.stream.domain.usecase

import com.nexus.stream.domain.model.ListStatus
import com.nexus.stream.domain.model.MetaPreview
import com.nexus.stream.domain.model.PosterShape
import com.nexus.stream.domain.model.ProgressSample
import com.nexus.stream.domain.model.ProgressTarget
import com.nexus.stream.domain.model.WatchProgress
import com.nexus.stream.domain.repository.LibraryItem
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
            val key = ProgressRules.progressKey(target.metaId, target.videoId)
            return when {
                !ProgressRules.shouldSave(sample) -> SaveOutcome.Skipped
                progress.get(key)?.watched == true -> SaveOutcome.Skipped
                else -> saveSample(target, sample, firstOfSession, nowMs)
            }
        }

        private suspend fun saveSample(
            target: ProgressTarget,
            sample: ProgressSample,
            firstOfSession: Boolean,
            nowMs: Long,
        ): SaveOutcome {
            val d = sample.durationMs
            val p = if (sample.ended) d else sample.positionMs.coerceIn(0, d)
            val key = ProgressRules.progressKey(target.metaId, target.videoId)
            val watchedNow = sample.ended || ProgressRules.isWatched(p, d)

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

            updateLibraryStatusIfNeeded(
                target = target,
                positionMs = p,
                watchedBefore = false,
                watchedNow = watchedNow,
            )

            return SaveOutcome.Saved(watchedNow)
        }

        private suspend fun updateLibraryStatusIfNeeded(
            target: ProgressTarget,
            positionMs: Long,
            watchedBefore: Boolean,
            watchedNow: Boolean,
        ) {
            if (watchedBefore) return
            val qualifying = positionMs >= ProgressRules.STATUS_MIN_POSITION_MS || watchedNow
            if (!qualifying) return

            val item = library.getItem(target.metaId)
            if (item == null || !item.inWatchlist) {
                val status = if (target.isMovie && watchedNow) ListStatus.COMPLETED else ListStatus.WATCHING
                library.setStatus(target.toMetaPreview(), status)
            } else {
                val nextStatus = computeNextStatus(item, target.isMovie, watchedNow)
                if (nextStatus != null && nextStatus != item.status) {
                    library.setStatusById(target.metaId, nextStatus)
                }
            }
        }

        private fun computeNextStatus(
            item: LibraryItem,
            isMovie: Boolean,
            watchedNow: Boolean,
        ): ListStatus? =
            when {
                isMovie && watchedNow && item.status != ListStatus.COMPLETED -> ListStatus.COMPLETED
                !isMovie && item.status == ListStatus.COMPLETED -> ListStatus.WATCHING
                item.status in ACTIVE_OR_ON_HOLD_STATUSES -> ListStatus.WATCHING
                else -> null
            }

        private fun ProgressTarget.toMetaPreview(): MetaPreview =
            MetaPreview(
                id = metaId,
                type = type,
                name = name,
                poster = poster,
                posterShape = PosterShape.POSTER,
                background = background,
                logo = null,
                description = null,
                releaseInfo = null,
                imdbRating = null,
                genres = emptyList(),
            )

        private companion object {
            private val ACTIVE_OR_ON_HOLD_STATUSES =
                setOf(
                    ListStatus.PLAN_TO_WATCH,
                    ListStatus.ON_HOLD,
                    ListStatus.DROPPED,
                )
        }
    }
