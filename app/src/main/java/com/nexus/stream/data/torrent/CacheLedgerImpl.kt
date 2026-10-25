package com.nexus.stream.data.torrent

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nexus.stream.di.CacheLedgerDataStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

const val MAX_ENTRIES = 200
private const val LEDGER_PREFIX = "led."

@Singleton
class CacheLedgerImpl
    @Inject
    constructor(
        @CacheLedgerDataStore private val dataStore: DataStore<Preferences>,
    ) : CacheLedger {
        private val json =
            Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
            }

        override suspend fun get(key: String): LedgerEntry? {
            val prefKey = stringPreferencesKey("$LEDGER_PREFIX$key")
            return try {
                val prefs = dataStore.data.first()
                val raw = prefs[prefKey] ?: return null
                json.decodeFromString(LedgerEntry.serializer(), raw)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        }

        override suspend fun put(entry: LedgerEntry) {
            val prefKey = stringPreferencesKey("$LEDGER_PREFIX${entry.key}")
            val encoded = json.encodeToString(LedgerEntry.serializer(), entry)
            dataStore.edit { prefs ->
                prefs[prefKey] = encoded

                val ledEntries = prefs.asMap().filterKeys { it.name.startsWith(LEDGER_PREFIX) }
                if (ledEntries.size > MAX_ENTRIES) {
                    val toRemoveCount = ledEntries.size - MAX_ENTRIES
                    val sorted =
                        ledEntries.entries.sortedBy { (_, rawValue) ->
                            val str = rawValue as? String
                            if (str.isNullOrBlank()) {
                                Long.MIN_VALUE
                            } else {
                                try {
                                    json.decodeFromString(LedgerEntry.serializer(), str).atMs
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

        override suspend fun remove(key: String) {
            val prefKey = stringPreferencesKey("$LEDGER_PREFIX$key")
            dataStore.edit { prefs ->
                prefs.remove(prefKey)
            }
        }

        override suspend fun all(): List<LedgerEntry> {
            return try {
                val prefs = dataStore.data.first()
                prefs
                    .asMap()
                    .filterKeys { it.name.startsWith(LEDGER_PREFIX) }
                    .values
                    .mapNotNull { raw ->
                        val str = raw as? String ?: return@mapNotNull null
                        try {
                            json.decodeFromString(LedgerEntry.serializer(), str)
                        } catch (_: Exception) {
                            null
                        }
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                emptyList()
            }
        }
    }
