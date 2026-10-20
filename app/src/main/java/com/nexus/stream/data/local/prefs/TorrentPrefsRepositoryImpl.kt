package com.nexus.stream.data.local.prefs

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
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

        override suspend fun setWifiOnly(value: Boolean) {
            dataStore.edit { prefs ->
                prefs[KEY_WIFI_ONLY_TORRENTS] = value
            }
        }

        private companion object {
            val KEY_WIFI_ONLY_TORRENTS = booleanPreferencesKey("wifiOnlyTorrents")
        }
    }
