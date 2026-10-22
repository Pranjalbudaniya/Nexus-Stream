package com.nexus.stream.data.player

import android.content.Context
import android.view.View
import kotlinx.coroutines.flow.StateFlow

data class PlayerSnapshot(
    val positionMs: Long = 0,
    val durationMs: Long = 0, // 0 = unknown
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val isSeekable: Boolean = false,
    val ended: Boolean = false,
    val failed: Boolean = false,
    val rate: Float = 1f,
)

@Suppress("TooManyFunctions")
interface PlayerController {
    val snapshot: StateFlow<PlayerSnapshot>
    val tracks: StateFlow<PlayerTracks>

    fun createSurface(context: Context): View // returns the video view and attaches it

    fun detachSurface()

    fun play(url: String)

    fun pause()

    fun resume()

    fun seekTo(positionMs: Long)

    fun setRate(rate: Float)

    fun selectAudioTrack(id: String)

    fun selectSubtitleTrack(id: String?)

    fun addSubtitle(uri: String)

    fun setSubtitleDelay(ms: Long)

    fun setVideoScale(scale: VideoScale)

    fun release() // idempotent; never blocks the main thread
}

fun interface PlayerControllerFactory {
    fun create(): PlayerController
}
