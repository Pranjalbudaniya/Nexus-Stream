package com.nexus.stream.data.local.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nexus.stream.domain.model.AppFont
import com.nexus.stream.domain.model.CardStyle
import com.nexus.stream.domain.model.ContrastLevel
import com.nexus.stream.domain.model.MAX_RECENT_ACCENTS
import com.nexus.stream.domain.model.Roundness
import com.nexus.stream.domain.model.TextSize
import com.nexus.stream.domain.model.ThemeMode
import com.nexus.stream.domain.model.ThemeSettings
import com.nexus.stream.domain.model.UiSize
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
                    prefs.toThemeSettings()
                }.distinctUntilChanged()

        override suspend fun update(transform: (ThemeSettings) -> ThemeSettings) {
            dataStore.edit { prefs ->
                val current = prefs.toThemeSettings()
                val updated = transform(current)
                prefs.applyThemeSettings(updated)
            }
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
                if (argb != null) {
                    val normalized = normalizeAlpha(argb)
                    val updatedRecents =
                        if (remember) {
                            (listOf(normalized) + current.recentAccents.filter { it != normalized })
                                .take(MAX_RECENT_ACCENTS)
                        } else {
                            current.recentAccents
                        }
                    current.copy(accentColor = normalized, recentAccents = updatedRecents)
                } else {
                    current.copy(accentColor = null)
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
                prefs.remove(KEY_FONT)
                prefs.remove(KEY_UI_SIZE)
                prefs.remove(KEY_TEXT_SIZE)
                prefs.remove(KEY_ROUNDNESS)
                prefs.remove(KEY_CARD_STYLE)
                prefs.remove(KEY_CONTRAST)
            }
        }

        companion object {
            private const val OPAQUE_ALPHA = 255L
            private const val ALPHA_BIT_SHIFT = 24
            private const val RGB_HEX_MASK = 0x00FFFFFFL

            fun normalizeAlpha(argb: Long): Long = (OPAQUE_ALPHA shl ALPHA_BIT_SHIFT) or (argb and RGB_HEX_MASK)

            val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
            val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
            val KEY_AMOLED = booleanPreferencesKey("amoled")
            val KEY_ACCENT_COLOR = longPreferencesKey("accent_color")
            val KEY_RECENT_ACCENTS = stringPreferencesKey("recent_accents")
            val KEY_FONT = stringPreferencesKey("app_font")
            val KEY_UI_SIZE = stringPreferencesKey("ui_size")
            val KEY_TEXT_SIZE = stringPreferencesKey("text_size")
            val KEY_ROUNDNESS = stringPreferencesKey("roundness")
            val KEY_CARD_STYLE = stringPreferencesKey("card_style")
            val KEY_CONTRAST = stringPreferencesKey("contrast_level")

            inline fun <reified E : Enum<E>> Preferences.readEnum(
                key: Preferences.Key<String>,
                default: E,
            ): E {
                val value = this[key] ?: return default
                return try {
                    java.lang.Enum.valueOf(E::class.java, value)
                } catch (_: IllegalArgumentException) {
                    default
                }
            }

            fun Preferences.toThemeSettings(): ThemeSettings {
                val mode = readEnum(KEY_THEME_MODE, ThemeMode.SYSTEM)
                val dynamicColor = this[KEY_DYNAMIC_COLOR] ?: false
                val amoled = this[KEY_AMOLED] ?: false
                val rawAccent = this[KEY_ACCENT_COLOR]
                val accentColor = rawAccent?.let { normalizeAlpha(it) }

                val rawRecents = this[KEY_RECENT_ACCENTS]
                val recentAccents =
                    rawRecents
                        ?.split(',')
                        ?.mapNotNull { it.trim().toLongOrNull() }
                        ?.map { normalizeAlpha(it) }
                        ?.distinct()
                        ?.take(MAX_RECENT_ACCENTS)
                        .orEmpty()

                val font = readEnum(KEY_FONT, AppFont.INTER)
                val uiSize = readEnum(KEY_UI_SIZE, UiSize.NORMAL)
                val textSize = readEnum(KEY_TEXT_SIZE, TextSize.NORMAL)
                val roundness = readEnum(KEY_ROUNDNESS, Roundness.NORMAL)
                val cardStyle = readEnum(KEY_CARD_STYLE, CardStyle.FILLED)
                val contrast = readEnum(KEY_CONTRAST, ContrastLevel.STANDARD)

                return ThemeSettings(
                    mode = mode,
                    dynamicColor = dynamicColor,
                    amoled = amoled,
                    accentColor = accentColor,
                    recentAccents = recentAccents,
                    font = font,
                    uiSize = uiSize,
                    textSize = textSize,
                    roundness = roundness,
                    cardStyle = cardStyle,
                    contrast = contrast,
                )
            }

            fun MutablePreferences.applyThemeSettings(settings: ThemeSettings) {
                this[KEY_THEME_MODE] = settings.mode.name
                this[KEY_DYNAMIC_COLOR] = settings.dynamicColor
                this[KEY_AMOLED] = settings.amoled
                if (settings.accentColor != null) {
                    this[KEY_ACCENT_COLOR] = normalizeAlpha(settings.accentColor)
                } else {
                    this.remove(KEY_ACCENT_COLOR)
                }
                if (settings.recentAccents.isNotEmpty()) {
                    this[KEY_RECENT_ACCENTS] = settings.recentAccents.joinToString(",")
                } else {
                    this.remove(KEY_RECENT_ACCENTS)
                }
                this[KEY_FONT] = settings.font.name
                this[KEY_UI_SIZE] = settings.uiSize.name
                this[KEY_TEXT_SIZE] = settings.textSize.name
                this[KEY_ROUNDNESS] = settings.roundness.name
                this[KEY_CARD_STYLE] = settings.cardStyle.name
                this[KEY_CONTRAST] = settings.contrast.name
            }
        }
    }
