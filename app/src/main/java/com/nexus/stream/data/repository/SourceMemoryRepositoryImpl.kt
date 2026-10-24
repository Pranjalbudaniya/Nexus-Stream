package com.nexus.stream.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nexus.stream.di.SourceMemoryDataStore
import com.nexus.stream.domain.model.SourceFingerprint
import com.nexus.stream.domain.repository.SourceMemoryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class SourceMemoryEntry(
    val fp: SourceFingerprint,
    val at: Long,
)

@Singleton
class SourceMemoryRepositoryImpl(
    private val dataStore: DataStore<Preferences>,
    private val clock: () -> Long,
) : SourceMemoryRepository {
    @Inject
    constructor(
        @SourceMemoryDataStore dataStore: DataStore<Preferences>,
    ) : this(
        dataStore = dataStore,
        clock = { System.currentTimeMillis() },
    )

    private val json =
        Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }

    override suspend fun get(key: String): SourceFingerprint? {
        val prefKey = stringPreferencesKey("$KEY_PREFIX$key")
        return try {
            val prefs = dataStore.data.first()
            val raw = prefs[prefKey] ?: return null
            json.decodeFromString(SourceMemoryEntry.serializer(), raw).fp
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun remember(
        key: String,
        fingerprint: SourceFingerprint,
    ) {
        val prefKey = stringPreferencesKey("$KEY_PREFIX$key")
        val now = clock()
        val entry = SourceMemoryEntry(fp = fingerprint, at = now)
        val encoded = json.encodeToString(SourceMemoryEntry.serializer(), entry)

        dataStore.edit { prefs ->
            prefs[prefKey] = encoded

            val srcEntries = prefs.asMap().filterKeys { it.name.startsWith(KEY_PREFIX) }
            if (srcEntries.size > MAX_ENTRIES) {
                val toRemoveCount = srcEntries.size - MAX_ENTRIES
                val sorted =
                    srcEntries.entries.sortedBy { (_, rawValue) ->
                        val str = rawValue as? String
                        if (str.isNullOrBlank()) {
                            Long.MIN_VALUE
                        } else {
                            try {
                                json.decodeFromString(SourceMemoryEntry.serializer(), str).at
                            } catch (_: Exception) {
                                Long.MIN_VALUE
                            }
                        }
                    }
                for (i in 0 until toRemoveCount) {
                    prefs.remove(sorted[i].key)
                }
            }
        }
    }

    override suspend fun forget(key: String) {
        val prefKey = stringPreferencesKey("$KEY_PREFIX$key")
        dataStore.edit { prefs ->
            prefs.remove(prefKey)
        }
    }

    companion object {
        const val MAX_ENTRIES = 400
        private const val KEY_PREFIX = "src."
    }
}
