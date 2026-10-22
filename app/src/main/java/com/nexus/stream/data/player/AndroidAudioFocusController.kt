package com.nexus.stream.data.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

private const val EVENTS_BUFFER_CAPACITY = 8

@Singleton
class AndroidAudioFocusController
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : AudioFocusController {
        private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        private val _events = MutableSharedFlow<AudioFocusEvent>(extraBufferCapacity = EVENTS_BUFFER_CAPACITY)
        override val events: SharedFlow<AudioFocusEvent> = _events.asSharedFlow()

        private val lock = Any()
        private var isNoisyRegistered = false
        private var hasFocus = false

        private val noisyReceiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context?,
                    intent: Intent?,
                ) {
                    if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                        _events.tryEmit(AudioFocusEvent.BECOMING_NOISY)
                    }
                }
            }

        private val focusChangeListener =
            AudioManager.OnAudioFocusChangeListener { focusChange ->
                when (focusChange) {
                    AudioManager.AUDIOFOCUS_LOSS -> {
                        synchronized(lock) { hasFocus = false }
                        _events.tryEmit(AudioFocusEvent.LOST)
                    }

                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                        synchronized(lock) { hasFocus = false }
                        _events.tryEmit(AudioFocusEvent.LOST_TRANSIENT)
                    }

                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                        _events.tryEmit(AudioFocusEvent.CAN_DUCK)
                    }

                    AudioManager.AUDIOFOCUS_GAIN -> {
                        synchronized(lock) { hasFocus = true }
                        _events.tryEmit(AudioFocusEvent.GAINED)
                    }
                }
            }

        private val audioAttributes =
            AudioAttributes
                .Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                .build()

        private val focusRequest =
            AudioFocusRequest
                .Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(audioAttributes)
                .setOnAudioFocusChangeListener(focusChangeListener)
                .build()

        override fun request(): Boolean {
            synchronized(lock) {
                val result = audioManager.requestAudioFocus(focusRequest)
                val granted = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
                if (granted) {
                    hasFocus = true
                    if (!isNoisyRegistered) {
                        val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
                        ContextCompat.registerReceiver(
                            context,
                            noisyReceiver,
                            filter,
                            ContextCompat.RECEIVER_NOT_EXPORTED,
                        )
                        isNoisyRegistered = true
                    }
                }
                return granted
            }
        }

        override fun abandon() {
            synchronized(lock) {
                if (hasFocus) {
                    audioManager.abandonAudioFocusRequest(focusRequest)
                    hasFocus = false
                }
                if (isNoisyRegistered) {
                    try {
                        context.unregisterReceiver(noisyReceiver)
                    } catch (_: IllegalArgumentException) {
                        // Receiver not registered
                    }
                    isNoisyRegistered = false
                }
            }
        }
    }
