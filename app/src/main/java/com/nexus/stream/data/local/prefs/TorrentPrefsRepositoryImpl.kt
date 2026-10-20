package com.nexus.stream.data.local.prefs

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import com.nexus.stream.domain.repository.TorrentPrefsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TorrentPrefsRepositoryImpl
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) : TorrentPrefsRepository {
        private val safeData: Flow<Preferences> =
            dataStore.data
                .catch { exception ->
                    if (exception is IOException || exception is CorruptionException) {
                        emit(emptyPreferences())
                    } else {
                        throw exception
                    }
                }

        override val wifiOnly: Flow<Boolean> =
            safeData
                .map { prefs ->
                    prefs[KEY_WIFI_ONLY_TORRENTS] ?: true
                }.distinctUntilChanged()

        override val cacheLimitGb: Flow<Int> =
            safeData
                .map { prefs ->
                    (prefs[KEY_CACHE_LIMIT_GB] ?: DEFAULT_CACHE_LIMIT_GB).coerceAtLeast(1)
                }.distinctUntilChanged()

        override val clearCacheOnExit: Flow<Boolean> =
            safeData
                .map { prefs ->
                    prefs[KEY_CLEAR_CACHE_ON_EXIT] ?: true
                }.distinctUntilChanged()

        override suspend fun setWifiOnly(value: Boolean) {
            dataStore.edit { prefs ->
                prefs[KEY_WIFI_ONLY_TORRENTS] = value
            }
        }

        override suspend fun setCacheLimitGb(value: Int) {
            dataStore.edit { prefs ->
                prefs[KEY_CACHE_LIMIT_GB] = value.coerceAtLeast(1)
            }
        }

        override suspend fun setClearCacheOnExit(value: Boolean) {
            dataStore.edit { prefs ->
                prefs[KEY_CLEAR_CACHE_ON_EXIT] = value
            }
        }

        private companion object {
            const val DEFAULT_CACHE_LIMIT_GB = 2
            val KEY_WIFI_ONLY_TORRENTS = booleanPreferencesKey("wifiOnlyTorrents")
            val KEY_CACHE_LIMIT_GB = intPreferencesKey("cacheLimitGb")
            val KEY_CLEAR_CACHE_ON_EXIT = booleanPreferencesKey("clearCacheOnExit")
        }
    }
