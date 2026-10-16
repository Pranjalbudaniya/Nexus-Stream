package com.nexus.stream.data.local.prefs

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import com.nexus.stream.domain.repository.ContentPrefsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContentPrefsRepositoryImpl
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) : ContentPrefsRepository {
        override val familyMode: Flow<Boolean> =
            dataStore.data
                .catch { exception ->
                    if (exception is IOException || exception is CorruptionException) {
                        emit(emptyPreferences())
                    } else {
                        throw exception
                    }
                }.map { prefs ->
                    prefs[KEY_FAMILY_MODE] ?: true
                }.distinctUntilChanged()

        override suspend fun setFamilyMode(enabled: Boolean) {
            dataStore.edit { prefs ->
                prefs[KEY_FAMILY_MODE] = enabled
            }
        }

        companion object {
            val KEY_FAMILY_MODE = booleanPreferencesKey("family_mode")
        }
    }
