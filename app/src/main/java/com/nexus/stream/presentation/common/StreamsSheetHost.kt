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
)

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
