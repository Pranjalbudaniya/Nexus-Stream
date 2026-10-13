package com.nexus.stream.domain.model

const val MAX_RECENT_ACCENTS = 5

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class ThemeSettings(
    val mode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val amoled: Boolean = false,
    val accentColor: Long? = null,
    val recentAccents: List<Long> = emptyList(),
)
