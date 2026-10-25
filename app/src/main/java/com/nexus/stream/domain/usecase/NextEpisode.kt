package com.nexus.stream.domain.usecase

import com.nexus.stream.domain.model.Outcome
import com.nexus.stream.domain.model.Video
import com.nexus.stream.domain.repository.MetaRepository
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

data class NextEpisode(
    val videoId: String,
    val season: Int?,
    val episode: Int?,
    val episodeTitle: String?,
)

fun nextRegularEpisode(
    videos: List<Video>,
    currentVideoId: String,
    now: Long,
): Video? {
    val regular = regularEpisodesInOrder(videos)
    val current = regular.firstOrNull { it.video.id == currentVideoId } ?: return null
    return firstReleasedRegularAfter(regular, current.orderKey, now)
}

class NextEpisodeFinder
    @Inject
    constructor(
        private val metaRepository: MetaRepository,
    ) {
        internal var clock: () -> Long = System::currentTimeMillis

        constructor(
            metaRepository: MetaRepository,
            clock: () -> Long,
        ) : this(metaRepository) {
            this.clock = clock
        }

        suspend fun find(
            type: String,
            metaId: String,
            currentVideoId: String,
        ): NextEpisode? {
            if (type.equals("movie", ignoreCase = true)) return null
            val meta =
                try {
                    (metaRepository.getMeta(type, metaId, forceRefresh = false) as? Outcome.Success)
                        ?.value
                        ?.meta
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
            val next = meta?.let { nextRegularEpisode(it.videos, currentVideoId, clock()) }
            return next?.let {
                NextEpisode(
                    videoId = it.id,
                    season = it.season,
                    episode = it.episode,
                    episodeTitle = it.title,
                )
            }
        }
    }
