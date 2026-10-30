package com.nexus.stream.data.download

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import com.nexus.stream.domain.repository.DownloadPrefsRepository
import com.nexus.stream.domain.repository.MAX_CONCURRENT_DEFAULT
import com.nexus.stream.domain.repository.MAX_CONCURRENT_MAX
import com.nexus.stream.domain.repository.MAX_CONCURRENT_MIN
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DownloadDataStore

@Singleton
class DownloadPrefsRepositoryImpl
    @Inject
    constructor(
        @DownloadDataStore private val dataStore: DataStore<Preferences>,
    ) : DownloadPrefsRepository {
        private val wifiOnlyKey = booleanPreferencesKey("wifi_only")
        private val maxConcurrentKey = intPreferencesKey("max_concurrent")
        private val introSeenKey = booleanPreferencesKey("download_intro_seen")

        private val safeData: Flow<Preferences> =
            dataStore.data.catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }

        override val wifiOnly: Flow<Boolean> =
            safeData.map { prefs ->
                try {
                    prefs[wifiOnlyKey] ?: true
                } catch (_: ClassCastException) {
                    true
                }
            }

        override val maxConcurrent: Flow<Int> =
            safeData.map { prefs ->
                try {
                    val raw = prefs[maxConcurrentKey] ?: MAX_CONCURRENT_DEFAULT
                    raw.coerceIn(MAX_CONCURRENT_MIN, MAX_CONCURRENT_MAX)
                } catch (_: ClassCastException) {
                    MAX_CONCURRENT_DEFAULT
                }
            }

        override val introSeen: Flow<Boolean> =
            safeData.map { prefs ->
                try {
                    prefs[introSeenKey] ?: false
                } catch (_: ClassCastException) {
                    false
                }
            }

        override suspend fun setWifiOnly(enabled: Boolean) {
            dataStore.edit { prefs ->
                prefs[wifiOnlyKey] = enabled
            }
        }

        override suspend fun setMaxConcurrent(count: Int) {
            dataStore.edit { prefs ->
                prefs[maxConcurrentKey] = count.coerceIn(MAX_CONCURRENT_MIN, MAX_CONCURRENT_MAX)
            }
        }

        override suspend fun markIntroSeen() {
            dataStore.edit { prefs ->
                prefs[introSeenKey] = true
            }
        }
    }
