package com.nexus.stream.presentation.feature.player

import com.nexus.stream.domain.model.PlaybackSource
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class PlaybackSelection(
    val title: String,
    val source: PlaybackSource,
    val type: String,
    val metaId: String,
    val videoId: String,
    val logoUrl: String? = null,
    val backgroundUrl: String? = null,
) {
    override fun toString(): String = "PlaybackSelection(title=$title, type=$type, metaId=$metaId)"
}

@Singleton
class PlaybackSessionHolder
    @Inject
    constructor() {
        private val _current = MutableStateFlow<PlaybackSelection?>(null)
        val current: StateFlow<PlaybackSelection?> = _current.asStateFlow()

        private val _openRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val openRequests: SharedFlow<Unit> = _openRequests.asSharedFlow()

        fun start(selection: PlaybackSelection) {
            _current.value = selection
            _openRequests.tryEmit(Unit)
        }

        fun clear() {
            _current.value = null
        }
    }
