package com.nexus.stream.domain.model

data class StreamLanguages(
    val flags: List<String> = emptyList(),
    val names: List<String> = emptyList(),
    val dualAudio: Boolean = false,
    val dubbed: Boolean = false,
    val multi: Boolean = false,
)
