package com.nexus.stream.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nexus.stream.data.local.prefs.PlayerPrefsRepositoryImpl
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
class SourceMemoryRepositoryImplTest {
    @get:Rule
    val tmpFolder = TemporaryFolder()

    private var currentClock = 1000L

    private fun createRepository(
        testScope: TestScope,
        fileName: String = "source_memory_test.preferences_pb",
        clock: () -> Long = { currentClock },
    ): Pair<SourceMemoryRepositoryImpl, DataStore<Preferences>> {
        val testFile = File(tmpFolder.root, fileName)
        val dataStore: DataStore<Preferences> =
            PreferenceDataStoreFactory.create(
                corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
                scope = testScope.backgroundScope,
                produceFile = { testFile },
            )
        return Pair(SourceMemoryRepositoryImpl(dataStore, clock), dataStore)
    }

    private fun sampleFp(
        kind: String = "Torrent",
        res: Int? = 1080,
        infoHash: String? = "abc123def",
    ) = SourceFingerprint(
        addonKeyHashes = listOf("hash1", "hash2"),
        kind = kind,
        bingeGroup = "group1",
        infoHash = infoHash,
        resolution = res,
        codec = "HEVC",
        hdr = listOf("HDR"),
        sizeBytes = 2_000_000_000L,
    )

    @Test
    fun SR1_rememberThenGet_returnsTheSameFingerprint() =
        runTest {
            val (repo, _) = createRepository(this, "sr1.preferences_pb")
            val fp = sampleFp()
            repo.remember("key1", fp)

            val retrieved = repo.get("key1")
            assertEquals(fp, retrieved)
            assertNull(repo.get("other"))
        }

    @Test
    fun SR2_rememberTwice_overwrites() =
        runTest {
            val (repo, _) = createRepository(this, "sr2.preferences_pb")
            val fp1 = sampleFp(res = 720)
            val fp2 = sampleFp(res = 1080)

            repo.remember("key1", fp1)
            assertEquals(720, repo.get("key1")?.resolution)

            repo.remember("key1", fp2)
            assertEquals(1080, repo.get("key1")?.resolution)
        }

    @Test
    fun SR3_forget_removesOnlyThatKey() =
        runTest {
            val (repo, _) = createRepository(this, "sr3.preferences_pb")
            val fp1 = sampleFp(res = 720)
            val fp2 = sampleFp(res = 1080)

            repo.remember("key1", fp1)
            repo.remember("key2", fp2)

            repo.forget("key1")

            assertNull(repo.get("key1"))
            assertEquals(fp2, repo.get("key2"))
        }

    @Test
    fun SR4_prune_keepsTheNewestFourHundred() =
        runTest {
            val (repo, ds) = createRepository(this, "sr4.preferences_pb")
            for (i in 1..405) {
                currentClock = i * 1000L
                repo.remember("k$i", sampleFp(res = i))
            }

            val map = ds.data.first().asMap()
            assertEquals(400, map.size)

            for (i in 1..5) {
                assertNull(repo.get("k$i"))
            }
            for (i in 6..405) {
                assertEquals(i, repo.get("k$i")?.resolution)
            }
        }

    @Test
    fun SR5_undecodableValue_readsAsNull_andCountsAsOldestForPruning() =
        runTest {
            val (repo, ds) = createRepository(this, "sr5.preferences_pb")
            ds.edit { prefs ->
                prefs[stringPreferencesKey("src.bad")] = "not valid json {{"
            }
            assertNull(repo.get("bad"))

            for (i in 1..400) {
                currentClock = i * 1000L
                repo.remember("k$i", sampleFp(res = i))
            }

            val map = ds.data.first().asMap()
            assertEquals(400, map.size)
            assertFalse(map.containsKey(stringPreferencesKey("src.bad")))
            assertNull(repo.get("bad"))
            for (i in 1..400) {
                assertEquals(i, repo.get("k$i")?.resolution)
            }
        }

    @Test
    fun SR6_getNeverThrows_onCorruptStoreFile() =
        runTest {
            val testFile = File(tmpFolder.root, "corrupt.preferences_pb")
            testFile.writeBytes(byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08))

            val dataStore: DataStore<Preferences> =
                PreferenceDataStoreFactory.create(
                    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
                    scope = backgroundScope,
                    produceFile = { testFile },
                )
            val repo = SourceMemoryRepositoryImpl(dataStore) { currentClock }

            val result = repo.get("k")
            assertNull(result)
        }

    @Test
    fun SR7_keysAreNamespaced_notMixedWithPlayerPrefs() =
        runTest {
            val (sourceRepo, sourceDs) = createRepository(this, "source_ds.preferences_pb")
            val playerFile = File(tmpFolder.root, "player_ds.preferences_pb")
            val playerDs: DataStore<Preferences> =
                PreferenceDataStoreFactory.create(
                    scope = backgroundScope,
                    produceFile = { playerFile },
                )
            val playerRepo = PlayerPrefsRepositoryImpl(playerDs)

            sourceRepo.remember("item1", sampleFp())
            playerRepo.setQuickPlay(true)

            val sourceKeys =
                sourceDs.data
                    .first()
                    .asMap()
                    .keys
                    .map { it.name }
            val playerKeys =
                playerDs.data
                    .first()
                    .asMap()
                    .keys
                    .map { it.name }

            assertTrue(sourceKeys.all { it.startsWith("src.") })
            assertTrue(playerKeys.none { it.startsWith("src.") })
        }
}
