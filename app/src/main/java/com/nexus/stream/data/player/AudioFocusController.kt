package com.nexus.stream.data.player

import kotlinx.coroutines.flow.SharedFlow

enum class AudioFocusEvent {
    LOST,
    LOST_TRANSIENT,
    CAN_DUCK,
    GAINED,
    BECOMING_NOISY,
}

interface AudioFocusController {
    val events: SharedFlow<AudioFocusEvent>

    fun request(): Boolean

    fun abandon()
}
