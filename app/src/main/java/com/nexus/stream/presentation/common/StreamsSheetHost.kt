package com.nexus.stream.presentation.common

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.nexus.stream.R
import com.nexus.stream.domain.model.EnqueueResult
import com.nexus.stream.domain.repository.DownloadPrefsRepository
import com.nexus.stream.presentation.feature.streams.StreamsSheet
import com.nexus.stream.presentation.theme.NexusTheme
import dagger.hilt.EntryPoint
import dagger.hilt.EntryPoints
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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
    val sourceHintKey: String? = null,
    val downloadMode: Boolean = false,
    val replaceDownloadId: String? = null,
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

@EntryPoint
@InstallIn(SingletonComponent::class)
interface StreamsSheetHostEntryPoint {
    fun downloadPrefsRepository(): DownloadPrefsRepository
}

@Composable
fun StreamsSheetHost(
    onNavigateToStore: (String) -> Unit = {},
    onNavigateToDownloads: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var currentRequest by remember { mutableStateOf<PlaybackRequest?>(null) }
    var showIntroDialog by remember { mutableStateOf(false) }

    val entryPoint =
        remember(context) {
            EntryPoints.get(context.applicationContext, StreamsSheetHostEntryPoint::class.java)
        }
    val downloadPrefsRepo = remember(entryPoint) { entryPoint.downloadPrefsRepository() }

    val launcher =
        remember {
            object : PlaybackLauncher {
                override fun open(request: PlaybackRequest) {
                    currentRequest = request
                }
            }
        }

    CompositionLocalProvider(LocalPlayback provides launcher) {
        Box(modifier = Modifier.fillMaxSize()) {
            content()

            SnackbarHost(
                hostState = snackbarHostState,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = NexusTheme.spacing.xxl),
            )

            currentRequest?.let { request ->
                StreamsSheet(
                    request = request,
                    onDismiss = { currentRequest = null },
                    onNavigateToStore = { category ->
                        currentRequest = null
                        onNavigateToStore(category)
                    },
                    onDownloadResult = { result ->
                        when (result) {
                            is EnqueueResult.Added -> {
                                coroutineScope.launch {
                                    if (!downloadPrefsRepo.introSeen.first()) {
                                        showIntroDialog = true
                                    }
                                    val res =
                                        snackbarHostState.showSnackbar(
                                            message = context.getString(R.string.download_added),
                                            actionLabel = context.getString(R.string.download_view),
                                        )
                                    if (res == SnackbarResult.ActionPerformed) {
                                        onNavigateToDownloads()
                                    }
                                }
                            }

                            is EnqueueResult.Duplicate -> {
                                coroutineScope.launch {
                                    val res =
                                        snackbarHostState.showSnackbar(
                                            message = context.getString(R.string.download_duplicate),
                                            actionLabel = context.getString(R.string.download_view),
                                        )
                                    if (res == SnackbarResult.ActionPerformed) {
                                        onNavigateToDownloads()
                                    }
                                }
                            }

                            is EnqueueResult.Rejected -> {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(
                                        message = context.getString(R.string.download_rejected),
                                    )
                                }
                            }
                        }
                    },
                    onDownloadMessage = { resId ->
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(context.getString(resId))
                        }
                    },
                )
            }

            if (showIntroDialog) {
                DownloadIntroDialog(
                    onDismiss = {
                        showIntroDialog = false
                        coroutineScope.launch { downloadPrefsRepo.markIntroSeen() }
                    },
                    onSettings = {
                        showIntroDialog = false
                        coroutineScope.launch { downloadPrefsRepo.markIntroSeen() }
                        onNavigateToSettings()
                    },
                )
            }
        }
    }
}

@Composable
fun DownloadIntroDialog(
    onDismiss: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.download_intro_title)) },
        text = { Text(stringResource(R.string.download_intro_body)) },
        confirmButton = {
            NexusTextButton(onClick = onDismiss) {
                Text(stringResource(R.string.download_intro_ok))
            }
        },
        dismissButton = {
            NexusTextButton(onClick = onSettings) {
                Text(stringResource(R.string.download_intro_settings))
            }
        },
        shape = NexusTheme.shapes.card,
        modifier = modifier,
    )
}

@Preview(name = "DownloadIntroDialog - Light")
@Composable
internal fun DownloadIntroDialogPreviewLight() {
    NexusTheme {
        DownloadIntroDialog(
            onDismiss = {},
            onSettings = {},
        )
    }
}

@Preview(name = "DownloadIntroDialog - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun DownloadIntroDialogPreviewDark() {
    NexusTheme {
        DownloadIntroDialog(
            onDismiss = {},
            onSettings = {},
        )
    }
}
