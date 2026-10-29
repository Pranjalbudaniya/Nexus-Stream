package com.nexus.stream.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nexus.stream.domain.model.HomeRowConfig
import com.nexus.stream.domain.model.decodeHomeRowConfig
import com.nexus.stream.domain.model.encodeHomeRowConfig
import com.nexus.stream.domain.repository.HomeRowConfigRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class HomeRowsDataStore

@Singleton
class HomeRowConfigRepositoryImpl
    @Inject
    constructor(
        @HomeRowsDataStore private val dataStore: DataStore<Preferences>,
    ) : HomeRowConfigRepository {
        private val configKey = stringPreferencesKey("home_row_config")

        override val config: Flow<HomeRowConfig> =
            dataStore.data
                .catch { exception ->
                    if (exception is IOException) {
                        emit(emptyPreferences())
                    } else {
                        throw exception
                    }
                }.map { prefs ->
                    val raw = prefs[configKey]
                    decodeHomeRowConfig(raw)
                }

        override suspend fun update(transform: (HomeRowConfig) -> HomeRowConfig) {
            dataStore.edit { prefs ->
                val current = decodeHomeRowConfig(prefs[configKey])
                val updated = transform(current)
                prefs[configKey] = encodeHomeRowConfig(updated)
            }
        }

        override suspend fun reset() {
            dataStore.edit { prefs ->
                prefs.remove(configKey)
            }
        }
    }
