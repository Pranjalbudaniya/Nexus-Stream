package com.nexus.stream.data.player

import android.content.Context
import android.net.Uri
import android.view.View
import com.nexus.stream.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

private const val BUFFERING_COMPLETE_PERCENT = 100f
private const val MICROS_PER_MILLI = 1000L
private const val SLAVE_TYPE_SUBTITLE = 0

@Suppress("TooManyFunctions")
class LibVlcPlayer(
    context: Context,
    io: CoroutineDispatcher,
    options: List<String> = emptyList(),
) : PlayerController {
    private val scope = CoroutineScope(SupervisorJob() + io)
    private val libVlc = LibVLC(context, ArrayList(options))
    private val mediaPlayer = MediaPlayer(libVlc)

    private val _snapshot = MutableStateFlow(PlayerSnapshot())
    override val snapshot: StateFlow<PlayerSnapshot> = _snapshot.asStateFlow()

    private val _tracks = MutableStateFlow(PlayerTracks())
    override val tracks: StateFlow<PlayerTracks> = _tracks.asStateFlow()

    private var currentMedia: Media? = null
    private val released = AtomicBoolean(false)

    init {
        mediaPlayer.setEventListener { event ->
            when (event.type) {
                MediaPlayer.Event.Playing -> {
                    refreshTracks()
                    _snapshot.update {
                        it.copy(isPlaying = true, isBuffering = false, rate = mediaPlayer.rate)
                    }
                }

                MediaPlayer.Event.Paused -> {
                    _snapshot.update {
                        it.copy(isPlaying = false, rate = mediaPlayer.rate)
                    }
                }

                MediaPlayer.Event.Buffering -> {
                    val isBuffering = event.buffering < BUFFERING_COMPLETE_PERCENT
                    _snapshot.update {
                        it.copy(isBuffering = isBuffering, rate = mediaPlayer.rate)
                    }
                }

                MediaPlayer.Event.EncounteredError -> {
                    _snapshot.update {
                        it.copy(failed = true, isPlaying = false, isBuffering = false, rate = mediaPlayer.rate)
                    }
                }

                MediaPlayer.Event.EndReached -> {
                    _snapshot.update {
                        it.copy(ended = true, isPlaying = false, isBuffering = false, rate = mediaPlayer.rate)
                    }
                }

                MediaPlayer.Event.LengthChanged -> {
                    _snapshot.update {
                        it.copy(durationMs = event.lengthChanged, rate = mediaPlayer.rate)
                    }
                }

                MediaPlayer.Event.TimeChanged -> {
                    _snapshot.update {
                        it.copy(positionMs = event.timeChanged, rate = mediaPlayer.rate)
                    }
                }

                MediaPlayer.Event.SeekableChanged -> {
                    _snapshot.update {
                        it.copy(isSeekable = event.seekable, rate = mediaPlayer.rate)
                    }
                }

                MediaPlayer.Event.ESAdded,
                MediaPlayer.Event.ESDeleted,
                MediaPlayer.Event.ESSelected,
                -> {
                    refreshTracks()
                }
            }
        }
    }

    private fun refreshTracks() {
        val rawAudio = mediaPlayer.audioTracks ?: emptyArray()
        val audioList =
            rawAudio
                .filter { it.id != -1 }
                .map { PlayerTrack(id = it.id.toString(), name = it.name.orEmpty()) }

        val rawSpu = mediaPlayer.spuTracks ?: emptyArray()
        val spuList =
            rawSpu
                .filter { it.id != -1 }
                .map { PlayerTrack(id = it.id.toString(), name = it.name.orEmpty()) }

        val currentAudioId = mediaPlayer.audioTrack
        val selectedAudio = if (currentAudioId == -1) null else currentAudioId.toString()

        val currentSpuId = mediaPlayer.spuTrack
        val selectedSpu = if (currentSpuId == -1) null else currentSpuId.toString()

        _tracks.value =
            PlayerTracks(
                audio = audioList,
                subtitles = spuList,
                selectedAudioId = selectedAudio,
                selectedSubtitleId = selectedSpu,
            )
    }

    override fun createSurface(context: Context): View {
        val layout = VLCVideoLayout(context)
        mediaPlayer.attachViews(layout, null, true, false)
        return layout
    }

    override fun detachSurface() {
        mediaPlayer.vlcVout.detachViews()
    }

    override fun play(url: String) {
        val oldMedia = currentMedia
        val media = Media(libVlc, Uri.parse(url))
        currentMedia = media
        mediaPlayer.media = media
        oldMedia?.release()
        _snapshot.update {
            it.copy(failed = false, ended = false)
        }
        mediaPlayer.play()
    }

    override fun pause() {
        mediaPlayer.pause()
    }

    override fun resume() {
        mediaPlayer.play()
    }

    override fun seekTo(positionMs: Long) {
        mediaPlayer.time = positionMs
    }

    override fun setRate(rate: Float) {
        mediaPlayer.rate = rate
        _snapshot.update { it.copy(rate = mediaPlayer.rate) }
    }

    override fun selectAudioTrack(id: String) {
        id.toIntOrNull()?.let { intId ->
            mediaPlayer.setAudioTrack(intId)
            refreshTracks()
        }
    }

    override fun selectSubtitleTrack(id: String?) {
        if (id == null) {
            mediaPlayer.setSpuTrack(-1)
        } else {
            id.toIntOrNull()?.let { intId ->
                mediaPlayer.setSpuTrack(intId)
            }
        }
        refreshTracks()
    }

    override fun addSubtitle(uri: String) {
        mediaPlayer.addSlave(SLAVE_TYPE_SUBTITLE, Uri.parse(uri), true)
    }

    override fun setSubtitleDelay(ms: Long) {
        mediaPlayer.setSpuDelay(ms * MICROS_PER_MILLI)
    }

    override fun setVideoScale(scale: VideoScale) {
        val scaleType =
            when (scale) {
                VideoScale.FIT -> MediaPlayer.ScaleType.SURFACE_BEST_FIT
                VideoScale.CROP -> MediaPlayer.ScaleType.SURFACE_FIT_SCREEN
                VideoScale.STRETCH -> MediaPlayer.ScaleType.SURFACE_FILL
                VideoScale.RATIO_16_9 -> MediaPlayer.ScaleType.SURFACE_16_9
                VideoScale.RATIO_4_3 -> MediaPlayer.ScaleType.SURFACE_4_3
                VideoScale.ORIGINAL -> MediaPlayer.ScaleType.SURFACE_ORIGINAL
            }
        mediaPlayer.videoScale = scaleType
    }

    override fun release() {
        if (released.compareAndSet(false, true)) {
            mediaPlayer.setEventListener(null)
            mediaPlayer.vlcVout.detachViews()
            currentMedia?.release()
            currentMedia = null
            scope.launch {
                mediaPlayer.release()
                libVlc.release()
            }
        }
    }
}

@Singleton
class LibVlcPlayerFactory
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        @IoDispatcher private val io: CoroutineDispatcher,
    ) : PlayerControllerFactory {
        override fun create(style: SubtitleRenderStyle): PlayerController =
            LibVlcPlayer(context, io, buildSubtitleOptions(style))
    }
