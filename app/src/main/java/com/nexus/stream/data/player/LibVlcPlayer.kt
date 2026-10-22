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

class LibVlcPlayer(
    context: Context,
    io: CoroutineDispatcher,
) : PlayerController {
    private val scope = CoroutineScope(SupervisorJob() + io)
    private val libVlc = LibVLC(context, ArrayList<String>())
    private val mediaPlayer = MediaPlayer(libVlc)

    private val _snapshot = MutableStateFlow(PlayerSnapshot())
    override val snapshot: StateFlow<PlayerSnapshot> = _snapshot.asStateFlow()

    private var currentMedia: Media? = null
    private val released = AtomicBoolean(false)

    init {
        mediaPlayer.setEventListener { event ->
            when (event.type) {
                MediaPlayer.Event.Playing -> {
                    _snapshot.update {
                        it.copy(isPlaying = true, isBuffering = false)
                    }
                }

                MediaPlayer.Event.Paused -> {
                    _snapshot.update {
                        it.copy(isPlaying = false)
                    }
                }

                MediaPlayer.Event.Buffering -> {
                    val isBuffering = event.buffering < BUFFERING_COMPLETE_PERCENT
                    _snapshot.update {
                        it.copy(isBuffering = isBuffering)
                    }
                }

                MediaPlayer.Event.EncounteredError -> {
                    _snapshot.update {
                        it.copy(failed = true, isPlaying = false, isBuffering = false)
                    }
                }

                MediaPlayer.Event.EndReached -> {
                    _snapshot.update {
                        it.copy(ended = true, isPlaying = false, isBuffering = false)
                    }
                }

                MediaPlayer.Event.LengthChanged -> {
                    _snapshot.update {
                        it.copy(durationMs = event.lengthChanged)
                    }
                }

                MediaPlayer.Event.TimeChanged -> {
                    _snapshot.update {
                        it.copy(positionMs = event.timeChanged)
                    }
                }

                MediaPlayer.Event.SeekableChanged -> {
                    _snapshot.update {
                        it.copy(isSeekable = event.seekable)
                    }
                }
            }
        }
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
        override fun create(): PlayerController = LibVlcPlayer(context, io)
    }
