package com.nexus.stream.presentation.theme

import com.nexus.stream.domain.model.ThemeMode
import com.nexus.stream.domain.model.ThemeSettings
import com.nexus.stream.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeSettingsRepository(
    initialSettings: ThemeSettings = ThemeSettings(),
) : SettingsRepository {
    private val _themeSettings = MutableStateFlow(initialSettings)
    override val themeSettings: StateFlow<ThemeSettings> = _themeSettings.asStateFlow()

    override suspend fun update(transform: (ThemeSettings) -> ThemeSettings) {
        _themeSettings.value = transform(_themeSettings.value)
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        update { it.copy(mode = mode) }
    }

    override suspend fun setDynamicColor(enabled: Boolean) {
        update { it.copy(dynamicColor = enabled) }
    }

    override suspend fun setAmoled(enabled: Boolean) {
        update { it.copy(amoled = enabled) }
    }

    override suspend fun setAccentColor(
        argb: Long?,
        remember: Boolean,
    ) {
        update { current ->
            val newRecents =
                if (remember && argb != null) {
                    listOf(argb) + current.recentAccents.filter { it != argb }
                } else {
                    current.recentAccents
                }
            current.copy(accentColor = argb, recentAccents = newRecents)
        }
    }

    override suspend fun resetTheme() {
        _themeSettings.value = ThemeSettings()
    }
}
