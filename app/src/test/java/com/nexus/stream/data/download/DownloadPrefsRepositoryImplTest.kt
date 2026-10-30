package com.nexus.stream.data.download

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadPrefsRepositoryImplTest {
    @get:Rule
    val tmpFolder = TemporaryFolder()

    private fun createRepository(
        testScope: TestScope,
        fileName: String = "download_prefs_test.preferences_pb",
    ): Pair<DownloadPrefsRepositoryImpl, DataStore<Preferences>> {
        val testFile = File(tmpFolder.root, fileName)
        val dataStore: DataStore<Preferences> =
            PreferenceDataStoreFactory.create(
                corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
                scope = testScope.backgroundScope,
                produceFile = { testFile },
            )
        return Pair(DownloadPrefsRepositoryImpl(dataStore), dataStore)
    }

    @Test
    fun PR1_defaults_areWifiOnlyAndTwo() =
        runTest {
            val (repo, _) = createRepository(this)
            assertTrue(repo.wifiOnly.first())
            assertEquals(2, repo.maxConcurrent.first())
        }

    @Test
    fun PR2_set_persistsAcrossInstances() =
        runTest {
            val file = File(tmpFolder.root, "download_prefs_persist.preferences_pb")
            val scope1 = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val ds1: DataStore<Preferences> =
                PreferenceDataStoreFactory.create(
                    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
                    scope = scope1,
                    produceFile = { file },
                )
            val repo1 = DownloadPrefsRepositoryImpl(ds1)
            repo1.setWifiOnly(false)
            repo1.setMaxConcurrent(3)
            scope1.cancel()

            val scope2 = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val ds2: DataStore<Preferences> =
                PreferenceDataStoreFactory.create(
                    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
                    scope = scope2,
                    produceFile = { file },
                )
            val repo2 = DownloadPrefsRepositoryImpl(ds2)
            assertFalse(repo2.wifiOnly.first())
            assertEquals(3, repo2.maxConcurrent.first())
            scope2.cancel()
        }

    @Test
    fun PR3_maxConcurrent_isClamped() =
        runTest {
            val (repo, _) = createRepository(this)
            repo.setMaxConcurrent(0)
            assertEquals(1, repo.maxConcurrent.first())

            repo.setMaxConcurrent(7)
            assertEquals(3, repo.maxConcurrent.first())
        }

    @Test
    fun PR4_corruptedValue_readsTheDefault() =
        runTest {
            val (repo, dataStore) = createRepository(this)
            dataStore.edit { prefs ->
                prefs[stringPreferencesKey("max_concurrent")] = "corrupted_string"
            }
            assertEquals(2, repo.maxConcurrent.first())
        }
}
