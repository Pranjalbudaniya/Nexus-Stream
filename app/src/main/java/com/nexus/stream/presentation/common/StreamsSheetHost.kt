package com.nexus.stream.presentation.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.nexus.stream.R
import com.nexus.stream.presentation.theme.NexusTheme

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreamsSheetHost(content: @Composable () -> Unit) {
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
            ModalBottomSheet(
                onDismissRequest = { currentRequest = null },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = NexusTheme.spacing.lg)
                            .padding(bottom = NexusTheme.spacing.xl),
                ) {
                    Text(
                        text = request.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(NexusTheme.spacing.xs))
                    Text(
                        text = request.videoId,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(NexusTheme.spacing.md))
                    Text(
                        text = stringResource(R.string.detail_streams_placeholder_text),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}
