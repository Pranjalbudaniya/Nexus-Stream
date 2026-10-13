package com.nexus.stream.data.local.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nexus.stream.domain.model.MAX_RECENT_ACCENTS
import com.nexus.stream.domain.model.ThemeMode
import com.nexus.stream.domain.model.ThemeSettings
import com.nexus.stream.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) : SettingsRepository {
        override val themeSettings: Flow<ThemeSettings> =
            dataStore.data
                .catch { exception ->
                    if (exception is IOException) {
                        emit(emptyPreferences())
                    } else {
                        throw exception
                    }
                }.map { prefs ->
                    val modeStr = prefs[KEY_THEME_MODE]
                    val mode =
                        if (modeStr != null) {
                            try {
                                ThemeMode.valueOf(modeStr)
                            } catch (_: IllegalArgumentException) {
                                ThemeMode.SYSTEM
                            }
                        } else {
                            ThemeMode.SYSTEM
                        }

                    val dynamicColor = prefs[KEY_DYNAMIC_COLOR] ?: false
                    val amoled = prefs[KEY_AMOLED] ?: false
                    val rawAccent = prefs[KEY_ACCENT_COLOR]
                    val accentColor = rawAccent?.let { normalizeAlpha(it) }

                    val rawRecents = prefs[KEY_RECENT_ACCENTS]
                    val recentAccents =
                        rawRecents
                            ?.split(',')
                            ?.mapNotNull { it.trim().toLongOrNull() }
                            ?.map { normalizeAlpha(it) }
                            ?.distinct()
                            ?.take(MAX_RECENT_ACCENTS)
                            .orEmpty()

                    ThemeSettings(
                        mode = mode,
                        dynamicColor = dynamicColor,
                        amoled = amoled,
                        accentColor = accentColor,
                        recentAccents = recentAccents,
                    )
                }.distinctUntilChanged()

        override suspend fun setThemeMode(mode: ThemeMode) {
            dataStore.edit { prefs ->
                prefs[KEY_THEME_MODE] = mode.name
            }
        }

        override suspend fun setDynamicColor(enabled: Boolean) {
            dataStore.edit { prefs ->
                prefs[KEY_DYNAMIC_COLOR] = enabled
            }
        }

        override suspend fun setAmoled(enabled: Boolean) {
            dataStore.edit { prefs ->
                prefs[KEY_AMOLED] = enabled
            }
        }

        override suspend fun setAccentColor(
            argb: Long?,
            remember: Boolean,
        ) {
            dataStore.edit { prefs ->
                if (argb != null) {
                    val normalized = normalizeAlpha(argb)
                    prefs[KEY_ACCENT_COLOR] = normalized

                    if (remember) {
                        val rawRecents = prefs[KEY_RECENT_ACCENTS]
                        val currentRecents =
                            rawRecents
                                ?.split(',')
                                ?.mapNotNull { it.trim().toLongOrNull() }
                                ?.map { normalizeAlpha(it) }
                                ?.distinct()
                                .orEmpty()

                        val updatedRecents =
                            (listOf(normalized) + currentRecents.filter { it != normalized })
                                .take(MAX_RECENT_ACCENTS)

                        prefs[KEY_RECENT_ACCENTS] = updatedRecents.joinToString(",")
                    }
                } else {
                    prefs.remove(KEY_ACCENT_COLOR)
                }
            }
        }

        override suspend fun resetTheme() {
            dataStore.edit { prefs ->
                prefs.remove(KEY_THEME_MODE)
                prefs.remove(KEY_DYNAMIC_COLOR)
                prefs.remove(KEY_AMOLED)
                prefs.remove(KEY_ACCENT_COLOR)
                prefs.remove(KEY_RECENT_ACCENTS)
            }
        }

        private fun normalizeAlpha(argb: Long): Long = (OPAQUE_ALPHA shl ALPHA_BIT_SHIFT) or (argb and RGB_HEX_MASK)

        companion object {
            private const val OPAQUE_ALPHA = 255L
            private const val ALPHA_BIT_SHIFT = 24
            private const val RGB_HEX_MASK = 0x00FFFFFFL

            val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
            val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
            val KEY_AMOLED = booleanPreferencesKey("amoled")
            val KEY_ACCENT_COLOR = longPreferencesKey("accent_color")
            val KEY_RECENT_ACCENTS = stringPreferencesKey("recent_accents")
        }
    }
