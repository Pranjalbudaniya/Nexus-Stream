package com.nexus.stream.data.local.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TorrentPrefsRepositoryImplTest {
    @get:Rule
    val tmpFolder = TemporaryFolder()

    private fun createRepository(
        testScope: TestScope,
        fileName: String,
    ): Pair<TorrentPrefsRepositoryImpl, DataStore<Preferences>> {
        val testFile = File(tmpFolder.root, fileName)
        val dataStore: DataStore<Preferences> =
            PreferenceDataStoreFactory.create(
                scope = testScope.backgroundScope,
                produceFile = { testFile },
            )
        return Pair(TorrentPrefsRepositoryImpl(dataStore), dataStore)
    }

    @Test
    fun TP1_defaultWifiOnlyIsTrue() =
        runTest {
            val (repo, _) = createRepository(this, "fresh_torrent_prefs.preferences_pb")
            assertTrue(repo.wifiOnly.first())
        }

    @Test
    fun TP2_setWifiOnlyPersistsValue() =
        runTest {
            val (repo, _) = createRepository(this, "persisted_torrent_prefs.preferences_pb")
            assertTrue(repo.wifiOnly.first())

            repo.setWifiOnly(false)
            assertFalse(repo.wifiOnly.first())

            repo.setWifiOnly(true)
            assertTrue(repo.wifiOnly.first())
        }

    @Test
    fun TP3_defaultCacheLimitGbIsTwo() =
        runTest {
            val (repo, _) = createRepository(this, "fresh_limit_prefs.preferences_pb")
            assertEquals(2, repo.cacheLimitGb.first())
        }

    @Test
    fun TP4_setCacheLimitGbPersistsValue() =
        runTest {
            val (repo, _) = createRepository(this, "persisted_limit_prefs.preferences_pb")
            repo.setCacheLimitGb(5)
            assertEquals(5, repo.cacheLimitGb.first())
        }

    @Test
    fun TP5_setCacheLimitGbBelowOneReadsOne() =
        runTest {
            val (repo, _) = createRepository(this, "clamped_limit_prefs.preferences_pb")
            repo.setCacheLimitGb(0)
            assertEquals(1, repo.cacheLimitGb.first())

            repo.setCacheLimitGb(-3)
            assertEquals(1, repo.cacheLimitGb.first())
        }

    @Test
    fun TP6_defaultClearCacheOnExitIsTrue() =
        runTest {
            val (repo, _) = createRepository(this, "fresh_exit_prefs.preferences_pb")
            assertTrue(repo.clearCacheOnExit.first())
        }

    @Test
    fun TP7_setClearCacheOnExitPersistsValue() =
        runTest {
            val (repo, _) = createRepository(this, "persisted_exit_prefs.preferences_pb")
            assertTrue(repo.clearCacheOnExit.first())

            repo.setClearCacheOnExit(false)
            assertFalse(repo.clearCacheOnExit.first())

            repo.setClearCacheOnExit(true)
            assertTrue(repo.clearCacheOnExit.first())
        }

    @Test
    fun corruptFileReadsAsDefaults() =
        runTest {
            val corruptFile = File(tmpFolder.root, "corrupt_torrent_prefs.preferences_pb")
            corruptFile.writeBytes(byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x7F, -1, -2))

            val dataStore: DataStore<Preferences> =
                PreferenceDataStoreFactory.create(
                    scope = backgroundScope,
                    produceFile = { corruptFile },
                )
            val repo = TorrentPrefsRepositoryImpl(dataStore)
            assertTrue(repo.wifiOnly.first())
            assertEquals(2, repo.cacheLimitGb.first())
            assertTrue(repo.clearCacheOnExit.first())
        }
}
