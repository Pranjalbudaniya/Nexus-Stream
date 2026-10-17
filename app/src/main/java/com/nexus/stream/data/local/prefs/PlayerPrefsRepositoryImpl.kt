package com.nexus.stream.data.local.prefs

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nexus.stream.domain.model.StreamSort
import com.nexus.stream.domain.repository.PlayerPrefsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
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

        companion object {
            val KEY_QUICK_PLAY = booleanPreferencesKey("quick_play")
            val KEY_STREAM_SORT = stringPreferencesKey("stream_sort")
            val KEY_PREFERRED_LANGUAGE = stringPreferencesKey("preferred_language")
            val KEY_PREFERRED_QUALITY = intPreferencesKey("preferred_quality")
            val KEY_HIDE_CAM = booleanPreferencesKey("hide_cam")
        }
    }
