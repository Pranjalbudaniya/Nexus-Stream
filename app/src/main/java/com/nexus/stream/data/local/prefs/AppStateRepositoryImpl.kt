package com.nexus.stream.data.local.prefs

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import com.nexus.stream.domain.repository.AppStateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppStateRepositoryImpl
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) : AppStateRepository {
        override val defaultAddonsSeeded: Flow<Boolean> =
            dataStore.data
                .catch { exception ->
                    if (exception is IOException || exception is CorruptionException) {
                        emit(emptyPreferences())
                    } else {
                        throw exception
                    }
                }.map { prefs ->
                    prefs[DEFAULT_ADDONS_SEEDED_KEY] ?: false
                }.distinctUntilChanged()

        override suspend fun markDefaultAddonsSeeded() {
            dataStore.edit { prefs ->
                prefs[DEFAULT_ADDONS_SEEDED_KEY] = true
            }
        }

        companion object {
            val DEFAULT_ADDONS_SEEDED_KEY = booleanPreferencesKey("default_addons_seeded")
        }
    }
