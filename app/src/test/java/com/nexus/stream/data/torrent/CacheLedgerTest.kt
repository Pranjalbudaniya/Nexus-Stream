package com.nexus.stream.data.torrent

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nexus.stream.data.repository.SourceMemoryRepositoryImpl
import com.nexus.stream.domain.model.SourceFingerprint
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class CacheLedgerTest {
    @get:Rule
    val tmpFolder = TemporaryFolder()

    private fun createLedger(
        testScope: TestScope,
        fileName: String = "cache_ledger_test.preferences_pb",
    ): Pair<CacheLedgerImpl, DataStore<Preferences>> {
        val testFile = File(tmpFolder.root, fileName)
        val dataStore: DataStore<Preferences> =
            PreferenceDataStoreFactory.create(
                corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
                scope = testScope.backgroundScope,
                produceFile = { testFile },
            )
        return Pair(CacheLedgerImpl(dataStore), dataStore)
    }

    private fun sampleEntry(
        key: String = "tt1:1:1|tt1:1:1",
        hash: String = "abc123def",
        idx: Int = 0,
        state: LedgerState = LedgerState.ACTIVE,
        atMs: Long = 1000L,
    ) = LedgerEntry(
        key = key,
        infoHash = hash,
        fileIdx = idx,
        state = state,
        atMs = atMs,
    )

    @Test
    fun LG1_putThenGet_returnsTheEntry() =
        runTest {
            val (ledger, _) = createLedger(this, "lg1.preferences_pb")
            val entry = sampleEntry()
            ledger.put(entry)

            val retrieved = ledger.get(entry.key)
            assertEquals(entry, retrieved)
            assertNull(ledger.get("unknown_key"))
        }

    @Test
    fun LG2_putTwice_overwrites() =
        runTest {
            val (ledger, _) = createLedger(this, "lg2.preferences_pb")
            val entry1 = sampleEntry(state = LedgerState.ACTIVE, atMs = 1000L)
            val entry2 = sampleEntry(state = LedgerState.RETAINED, atMs = 2000L)

            ledger.put(entry1)
            assertEquals(LedgerState.ACTIVE, ledger.get(entry1.key)?.state)

            ledger.put(entry2)
            assertEquals(LedgerState.RETAINED, ledger.get(entry1.key)?.state)
            assertEquals(2000L, ledger.get(entry1.key)?.atMs)
        }

    @Test
    fun LG3_remove_removesOnlyThatKey() =
        runTest {
            val (ledger, _) = createLedger(this, "lg3.preferences_pb")
            val entry1 = sampleEntry(key = "k1")
            val entry2 = sampleEntry(key = "k2")

            ledger.put(entry1)
            ledger.put(entry2)

            ledger.remove("k1")

            assertNull(ledger.get("k1"))
            assertEquals(entry2, ledger.get("k2"))
            assertEquals(listOf(entry2), ledger.all())
        }

    @Test
    fun LG4_prune_keepsTheNewestTwoHundred() =
        runTest {
            val (ledger, ds) = createLedger(this, "lg4.preferences_pb")
            for (i in 1..205) {
                ledger.put(sampleEntry(key = "k$i", atMs = i * 1000L))
            }

            val map = ds.data.first().asMap()
            assertEquals(200, map.size)

            for (i in 1..5) {
                assertNull(ledger.get("k$i"))
            }
            for (i in 6..205) {
                assertEquals(i * 1000L, ledger.get("k$i")?.atMs)
            }
        }

    @Test
    fun LG5_undecodableValue_readsAsNull_andIsPruned() =
        runTest {
            val (ledger, ds) = createLedger(this, "lg5.preferences_pb")
            ds.edit { prefs ->
                prefs[stringPreferencesKey("led.bad")] = "{not valid json}"
            }
            assertNull(ledger.get("bad"))

            for (i in 1..200) {
                ledger.put(sampleEntry(key = "k$i", atMs = i * 1000L))
            }

            val map = ds.data.first().asMap()
            assertEquals(200, map.size)
            assertFalse(map.containsKey(stringPreferencesKey("led.bad")))
            assertNull(ledger.get("bad"))
            for (i in 1..200) {
                assertEquals(i * 1000L, ledger.get("k$i")?.atMs)
            }
        }

    @Test
    fun LG6_corruptFile_readsAsEmpty() =
        runTest {
            val testFile = File(tmpFolder.root, "corrupt_ledger.preferences_pb")
            testFile.writeBytes(byteArrayOf(0x09, 0x08, 0x07, 0x06, 0x05))

            val dataStore: DataStore<Preferences> =
                PreferenceDataStoreFactory.create(
                    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
                    scope = backgroundScope,
                    produceFile = { testFile },
                )
            val ledger = CacheLedgerImpl(dataStore)

            assertNull(ledger.get("any"))
            assertTrue(ledger.all().isEmpty())
        }

    @Test
    fun LG7_isAnotherFileThanSourceMemory() =
        runTest {
            val (ledger, ledgerDs) = createLedger(this, "ledger_ds.preferences_pb")
            val sourceFile = File(tmpFolder.root, "source_ds.preferences_pb")
            val sourceDs: DataStore<Preferences> =
                PreferenceDataStoreFactory.create(
                    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
                    scope = backgroundScope,
                    produceFile = { sourceFile },
                )
            val sourceRepo = SourceMemoryRepositoryImpl(sourceDs) { 1000L }

            val fp =
                SourceFingerprint(
                    addonKeyHashes = listOf("h1"),
                    kind = "Torrent",
                    bingeGroup = "bg",
                    infoHash = "hash1",
                    resolution = 1080,
                    codec = "h264",
                    hdr = emptyList(),
                    sizeBytes = 1000L,
                )
            sourceRepo.remember("m1", fp)
            ledger.put(sampleEntry(key = "m1"))

            val sourceKeys =
                sourceDs.data
                    .first()
                    .asMap()
                    .keys
                    .map { it.name }
            val ledgerKeys =
                ledgerDs.data
                    .first()
                    .asMap()
                    .keys
                    .map { it.name }

            assertTrue(sourceKeys.all { !it.startsWith("led.") })
            assertTrue(ledgerKeys.all { !it.startsWith("src.") })
        }
}
