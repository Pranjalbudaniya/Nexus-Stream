package com.nexus.stream.data.local.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
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
        }
}
