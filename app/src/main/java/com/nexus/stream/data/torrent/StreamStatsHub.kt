package com.nexus.stream.data.torrent

import com.nexus.stream.domain.model.StreamStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StreamStatsHub
    @Inject
    constructor() {
        private val _stats = MutableStateFlow<StreamStats?>(null)
        val stats: StateFlow<StreamStats?> = _stats.asStateFlow()

        fun publish(s: StreamStats) {
            _stats.value = s
        }

        fun clear() {
            _stats.value = null
        }
    }
