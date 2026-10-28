package com.nexus.stream.data.update

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import com.nexus.stream.domain.repository.UpdatePrefsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class UpdateDataStore

@Singleton
class UpdatePrefsRepositoryImpl
    @Inject
    constructor(
        @UpdateDataStore private val dataStore: DataStore<Preferences>,
    ) : UpdatePrefsRepository {
        private val autoCheckKey = booleanPreferencesKey("auto_check")
        private val lastCheckedAtKey = longPreferencesKey("last_checked_at")
        private val pendingInstallCodeKey = longPreferencesKey("pending_install_code")

        private val safeData: Flow<Preferences> =
            dataStore.data.catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }

        override val autoCheck: Flow<Boolean> =
            safeData.map { prefs -> prefs[autoCheckKey] ?: true }

        override val lastCheckedAt: Flow<Long?> =
            safeData.map { prefs -> prefs[lastCheckedAtKey] }

        override val pendingInstallCode: Flow<Long?> =
            safeData.map { prefs -> prefs[pendingInstallCodeKey] }

        override suspend fun setAutoCheck(enabled: Boolean) {
            dataStore.edit { prefs ->
                prefs[autoCheckKey] = enabled
            }
        }

        override suspend fun setLastCheckedAt(timestamp: Long) {
            dataStore.edit { prefs ->
                prefs[lastCheckedAtKey] = timestamp
            }
        }

        override suspend fun setPendingInstallCode(versionCode: Long) {
            dataStore.edit { prefs ->
                prefs[pendingInstallCodeKey] = versionCode
            }
        }

        override suspend fun clearPendingInstall() {
            dataStore.edit { prefs ->
                prefs.remove(pendingInstallCodeKey)
            }
        }
    }
