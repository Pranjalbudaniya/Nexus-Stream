package com.nexus.stream.domain.usecase

import com.nexus.stream.R
import com.nexus.stream.domain.model.EpisodeHint
import com.nexus.stream.domain.model.ParsedStream
import com.nexus.stream.domain.model.PlaybackCapabilities
import com.nexus.stream.domain.model.PlaybackSource
import com.nexus.stream.domain.model.UnsupportedReason
import com.nexus.stream.domain.model.fingerprintOf
import com.nexus.stream.presentation.common.PlaybackRequest
import com.nexus.stream.presentation.feature.player.PlaybackSelection
import com.nexus.stream.presentation.feature.player.PlaybackSessionHolder
import javax.inject.Inject

sealed interface StartResult {
    data class Started(
        val selection: PlaybackSelection,
    ) : StartResult

    data class External(
        val url: String,
    ) : StartResult

    data class Unsupported(
        val reason: UnsupportedReason? = null,
        val messageRes: Int = R.string.streams_torrent_engine_notice,
    ) : StartResult

    data object NeedsGate : StartResult
}

class StartPlayback
    @Inject
    constructor(
        private val playbackCapabilities: PlaybackCapabilities,
        private val playbackSessionHolder: PlaybackSessionHolder,
    ) {
        internal var gateCheck: ((ParsedStream) -> Boolean)? = null

        constructor(
            playbackCapabilities: PlaybackCapabilities,
            playbackSessionHolder: PlaybackSessionHolder,
            gateCheck: ((ParsedStream) -> Boolean)?,
        ) : this(playbackCapabilities, playbackSessionHolder) {
            this.gateCheck = gateCheck
        }

        operator fun invoke(
            request: PlaybackRequest?,
            stream: ParsedStream,
        ): StartResult {
            val hint =
                if (request?.season != null && request.episode != null) {
                    EpisodeHint(season = request.season, episode = request.episode)
                } else {
                    null
                }
            val source = resolvePlaybackSource(stream.stream.source, hint)
            val title = request?.title?.ifBlank { stream.displayName } ?: stream.displayName
            val hints =
                SubtitleHints(
                    filename = stream.stream.filename,
                    videoHash = stream.stream.videoHash,
                    videoSize = stream.stream.videoSize,
                )
            return when (source) {
                is PlaybackSource.Direct,
                is PlaybackSource.DirectWithHeaders,
                -> {
                    val selection = buildSelection(stream, title, source, request, hints)
                    playbackSessionHolder.start(selection)
                    StartResult.Started(selection)
                }

                is PlaybackSource.Torrent -> {
                    if (!playbackCapabilities.supportsTorrent) {
                        StartResult.Unsupported()
                    } else if (gateCheck?.invoke(stream) == true) {
                        StartResult.NeedsGate
                    } else {
                        val selection = buildSelection(stream, title, source, request, hints)
                        playbackSessionHolder.start(selection)
                        StartResult.Started(selection)
                    }
                }

                is PlaybackSource.External -> {
                    StartResult.External(source.url)
                }

                is PlaybackSource.Unsupported -> {
                    StartResult.Unsupported(source.reason)
                }
            }
        }

        private fun buildSelection(
            stream: ParsedStream,
            title: String,
            source: PlaybackSource,
            request: PlaybackRequest?,
            hints: SubtitleHints?,
        ): PlaybackSelection =
            PlaybackSelection(
                title = title,
                source = source,
                type = request?.type ?: "movie",
                metaId = request?.metaId.orEmpty(),
                videoId = request?.videoId.orEmpty(),
                logoUrl = request?.logoUrl,
                backgroundUrl = request?.backgroundUrl,
                subtitleHints = hints,
                season = request?.season,
                episode = request?.episode,
                posterUrl = request?.posterUrl ?: request?.logoUrl,
                metaName = request?.metaName,
                sourceFingerprint = fingerprintOf(stream),
            )
    }
