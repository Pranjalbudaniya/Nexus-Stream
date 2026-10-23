package com.nexus.stream.data.local.prefs

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nexus.stream.data.player.VideoScale
import com.nexus.stream.data.player.parseVideoScale
import com.nexus.stream.domain.model.StreamSort
import com.nexus.stream.domain.model.SubtitleBackground
import com.nexus.stream.domain.model.SubtitleEdge
import com.nexus.stream.domain.model.SubtitleSize
import com.nexus.stream.domain.model.SubtitleStyle
import com.nexus.stream.domain.repository.PlayerPrefsRepository
import com.nexus.stream.presentation.feature.player.ALLOWED_SEEK_STEPS
import com.nexus.stream.presentation.feature.player.normalizeSeekStep
import com.nexus.stream.presentation.theme.tokens.SubtitlePalette
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
// Implementation of PlayerPrefsRepository exposing 16 flows and setters for player and subtitle preferences.
@Suppress("TooManyFunctions")
class PlayerPrefsRepositoryImpl
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) : PlayerPrefsRepository {
        private val safeData: Flow<Preferences> =
            dataStore.data
                .catch { exception ->
                    if (exception is IOException || exception is CorruptionException) {
                        emit(emptyPreferences())
                    } else {
                        throw exception
                    }
                }

        override val quickPlay: Flow<Boolean> =
            safeData
                .map { prefs ->
                    prefs[KEY_QUICK_PLAY] ?: false
                }.distinctUntilChanged()

        override val sort: Flow<StreamSort> =
            safeData
                .map { prefs ->
                    val raw = prefs[KEY_STREAM_SORT]
                    raw?.let {
                        runCatching { StreamSort.valueOf(it) }.getOrNull()
                    } ?: StreamSort.BEST
                }.distinctUntilChanged()

        override val preferredLanguage: Flow<String?> =
            safeData
                .map { prefs ->
                    prefs[KEY_PREFERRED_LANGUAGE]?.ifBlank { null }
                }.distinctUntilChanged()

        override val preferredQuality: Flow<Int?> =
            safeData
                .map { prefs ->
                    val raw = prefs[KEY_PREFERRED_QUALITY]
                    if (raw != null && raw > 0) raw else null
                }.distinctUntilChanged()

        override val hideCam: Flow<Boolean> =
            safeData
                .map { prefs ->
                    prefs[KEY_HIDE_CAM] ?: false
                }.distinctUntilChanged()

        override val seekStepSeconds: Flow<Int> =
            safeData
                .map { prefs ->
                    normalizeSeekStep(prefs[KEY_SEEK_STEP_SECONDS])
                }.distinctUntilChanged()

        override val videoScale: Flow<VideoScale> =
            safeData
                .map { prefs ->
                    parseVideoScale(prefs[KEY_VIDEO_SCALE])
                }.distinctUntilChanged()

        override val preferredAudioLanguage: Flow<String?> =
            safeData
                .map { prefs ->
                    prefs[KEY_PREFERRED_AUDIO_LANGUAGE]?.ifBlank { null }
                }.distinctUntilChanged()

        override val preferredSubtitleLanguage: Flow<String?> =
            safeData
                .map { prefs ->
                    prefs[KEY_PREFERRED_SUBTITLE_LANGUAGE]?.ifBlank { null }
                }.distinctUntilChanged()

        override val subtitleStyle: Flow<SubtitleStyle> =
            safeData
                .map { prefs ->
                    val size =
                        prefs[KEY_SUBTITLE_SIZE]?.let { raw ->
                            runCatching { SubtitleSize.valueOf(raw) }.getOrNull()
                        } ?: SubtitleSize.NORMAL

                    val colorId =
                        prefs[KEY_SUBTITLE_COLOR]?.takeIf { raw ->
                            SubtitlePalette.colors.any { it.id.equals(raw, ignoreCase = true) }
                        } ?: "white"

                    val bg =
                        prefs[KEY_SUBTITLE_BACKGROUND]?.let { raw ->
                            runCatching { SubtitleBackground.valueOf(raw) }.getOrNull()
                        } ?: SubtitleBackground.NONE

                    val edge =
                        prefs[KEY_SUBTITLE_EDGE]?.let { raw ->
                            runCatching { SubtitleEdge.valueOf(raw) }.getOrNull()
                        } ?: SubtitleEdge.OUTLINE

                    val bold = prefs[KEY_SUBTITLE_BOLD] ?: false

                    SubtitleStyle(
                        size = size,
                        colorId = colorId,
                        background = bg,
                        edge = edge,
                        bold = bold,
                    )
                }.distinctUntilChanged()

        override suspend fun setQuickPlay(enabled: Boolean) {
            dataStore.edit { prefs ->
                prefs[KEY_QUICK_PLAY] = enabled
            }
        }

        override suspend fun setSort(sort: StreamSort) {
            dataStore.edit { prefs ->
                prefs[KEY_STREAM_SORT] = sort.name
            }
        }

        override suspend fun setPreferredLanguage(code: String?) {
            dataStore.edit { prefs ->
                if (code.isNullOrBlank()) {
                    prefs.remove(KEY_PREFERRED_LANGUAGE)
                } else {
                    prefs[KEY_PREFERRED_LANGUAGE] = code
                }
            }
        }

        override suspend fun setPreferredQuality(quality: Int?) {
            dataStore.edit { prefs ->
                if (quality == null || quality <= 0) {
                    prefs.remove(KEY_PREFERRED_QUALITY)
                } else {
                    prefs[KEY_PREFERRED_QUALITY] = quality
                }
            }
        }

        override suspend fun setHideCam(hideCam: Boolean) {
            dataStore.edit { prefs ->
                prefs[KEY_HIDE_CAM] = hideCam
            }
        }

        override suspend fun setSeekStepSeconds(seconds: Int) {
            if (seconds in ALLOWED_SEEK_STEPS) {
                dataStore.edit { prefs ->
                    prefs[KEY_SEEK_STEP_SECONDS] = seconds
                }
            }
        }

        override suspend fun setVideoScale(scale: VideoScale) {
            dataStore.edit { prefs ->
                prefs[KEY_VIDEO_SCALE] = scale.name
            }
        }

        override suspend fun setPreferredAudioLanguage(code: String?) {
            dataStore.edit { prefs ->
                if (code.isNullOrBlank()) {
                    prefs.remove(KEY_PREFERRED_AUDIO_LANGUAGE)
                } else {
                    prefs[KEY_PREFERRED_AUDIO_LANGUAGE] = code
                }
            }
        }

        override suspend fun setPreferredSubtitleLanguage(code: String?) {
            dataStore.edit { prefs ->
                if (code == null) {
                    prefs.remove(KEY_PREFERRED_SUBTITLE_LANGUAGE)
                } else {
                    prefs[KEY_PREFERRED_SUBTITLE_LANGUAGE] = code
                }
            }
        }

        override suspend fun setSubtitleSize(size: SubtitleSize) {
            dataStore.edit { prefs ->
                prefs[KEY_SUBTITLE_SIZE] = size.name
            }
        }

        override suspend fun setSubtitleColor(id: String) {
            if (SubtitlePalette.colors.any { it.id.equals(id, ignoreCase = true) }) {
                dataStore.edit { prefs ->
                    prefs[KEY_SUBTITLE_COLOR] = id
                }
            }
        }

        override suspend fun setSubtitleBackground(background: SubtitleBackground) {
            dataStore.edit { prefs ->
                prefs[KEY_SUBTITLE_BACKGROUND] = background.name
            }
        }

        override suspend fun setSubtitleEdge(edge: SubtitleEdge) {
            dataStore.edit { prefs ->
                prefs[KEY_SUBTITLE_EDGE] = edge.name
            }
        }

        override suspend fun setSubtitleBold(bold: Boolean) {
            dataStore.edit { prefs ->
                prefs[KEY_SUBTITLE_BOLD] = bold
            }
        }

        companion object {
            val KEY_QUICK_PLAY = booleanPreferencesKey("quick_play")
            val KEY_STREAM_SORT = stringPreferencesKey("stream_sort")
            val KEY_PREFERRED_LANGUAGE = stringPreferencesKey("preferred_language")
            val KEY_PREFERRED_QUALITY = intPreferencesKey("preferred_quality")
            val KEY_HIDE_CAM = booleanPreferencesKey("hide_cam")
            val KEY_SEEK_STEP_SECONDS = intPreferencesKey("seek_step_seconds")
            val KEY_VIDEO_SCALE = stringPreferencesKey("video_scale")
            val KEY_PREFERRED_AUDIO_LANGUAGE = stringPreferencesKey("preferred_audio_language")
            val KEY_PREFERRED_SUBTITLE_LANGUAGE = stringPreferencesKey("preferred_subtitle_language")
            val KEY_SUBTITLE_SIZE = stringPreferencesKey("subtitle_size")
            val KEY_SUBTITLE_COLOR = stringPreferencesKey("subtitle_color")
            val KEY_SUBTITLE_BACKGROUND = stringPreferencesKey("subtitle_background")
            val KEY_SUBTITLE_EDGE = stringPreferencesKey("subtitle_edge")
            val KEY_SUBTITLE_BOLD = booleanPreferencesKey("subtitle_bold")
        }
    }
