package com.nexus.stream.presentation.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.nexus.stream.presentation.feature.streams.StreamsSheet

data class PlaybackRequest(
    val type: String,
    val metaId: String,
    val videoId: String,
    val title: String,
    val season: Int? = null,
    val episode: Int? = null,
    val year: Int? = null,
    val logoUrl: String? = null,
    val backgroundUrl: String? = null,
    val posterUrl: String? = null,
    val metaName: String? = null,
    val autoResume: Boolean = false,
)

fun buildPlaybackRequest(
    meta: com.nexus.stream.domain.model.MetaDetail,
    videoId: String,
    title: String,
    autoResume: Boolean = false,
): PlaybackRequest {
    val activeVideo = meta.videos.find { it.id == videoId }
    val parts = videoId.split(":")
    val fallbackSeason = if (parts.size >= 3) parts[1].toIntOrNull() else null
    val fallbackEpisode = if (parts.size >= 3) parts[2].toIntOrNull() else null
    val season = activeVideo?.season ?: fallbackSeason
    val episode = activeVideo?.episode ?: fallbackEpisode
    val year = if (meta.type != "series") meta.releaseInfo?.take(4)?.toIntOrNull() else null

    return PlaybackRequest(
        type = meta.type,
        metaId = meta.id,
        videoId = videoId,
        title = title,
        season = season,
        episode = episode,
        year = year,
        logoUrl = meta.logo,
        backgroundUrl = meta.background,
        posterUrl = meta.poster,
        metaName = meta.name,
        autoResume = autoResume,
    )
}

interface PlaybackLauncher {
    fun open(request: PlaybackRequest)
}

val LocalPlayback =
    staticCompositionLocalOf<PlaybackLauncher> {
        error("No PlaybackLauncher provided")
    }

@Composable
fun StreamsSheetHost(
    onNavigateToStore: (String) -> Unit = {},
    content: @Composable () -> Unit,
) {
    var currentRequest by remember { mutableStateOf<PlaybackRequest?>(null) }
    val launcher =
        remember {
            object : PlaybackLauncher {
                override fun open(request: PlaybackRequest) {
                    currentRequest = request
                }
            }
        }

    CompositionLocalProvider(LocalPlayback provides launcher) {
        content()

        currentRequest?.let { request ->
            StreamsSheet(
                request = request,
                onDismiss = { currentRequest = null },
                onNavigateToStore = { category ->
                    currentRequest = null
                    onNavigateToStore(category)
                },
            )
        }
    }
}
