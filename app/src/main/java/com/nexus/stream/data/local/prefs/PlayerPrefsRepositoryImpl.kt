package com.nexus.stream.data.local.prefs

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
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
        override val quickPlay: Flow<Boolean> =
            dataStore.data
                .catch { exception ->
                    if (exception is IOException || exception is CorruptionException) {
                        emit(emptyPreferences())
                    } else {
                        throw exception
                    }
                }.map { prefs ->
                    prefs[KEY_QUICK_PLAY] ?: false
                }.distinctUntilChanged()

        override suspend fun setQuickPlay(enabled: Boolean) {
            dataStore.edit { prefs ->
                prefs[KEY_QUICK_PLAY] = enabled
            }
        }

        companion object {
            val KEY_QUICK_PLAY = booleanPreferencesKey("quick_play")
        }
    }
