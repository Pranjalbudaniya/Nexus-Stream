package com.nexus.stream.domain.repository

import com.nexus.stream.domain.model.ThemeMode
import com.nexus.stream.domain.model.ThemeSettings
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val themeSettings: Flow<ThemeSettings>

    suspend fun setThemeMode(mode: ThemeMode)

    suspend fun setDynamicColor(enabled: Boolean)

    suspend fun setAmoled(enabled: Boolean)

    suspend fun setAccentColor(
        argb: Long?,
        remember: Boolean = false,
    )

    suspend fun resetTheme()
}
