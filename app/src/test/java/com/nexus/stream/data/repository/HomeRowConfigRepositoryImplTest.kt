package com.nexus.stream.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nexus.stream.domain.model.HomeRowConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class HomeRowConfigRepositoryImplTest {
    @get:Rule
    val tmpFolder = TemporaryFolder()

    private fun createRepository(
        testScope: TestScope,
        fileName: String = "home_rows_test.preferences_pb",
    ): Pair<HomeRowConfigRepositoryImpl, DataStore<Preferences>> {
        val testFile = File(tmpFolder.root, fileName)
        val dataStore: DataStore<Preferences> =
            PreferenceDataStoreFactory.create(
                corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
                scope = testScope.backgroundScope,
                produceFile = { testFile },
            )
        return Pair(HomeRowConfigRepositoryImpl(dataStore), dataStore)
    }

    @Test
    fun RC1_fresh_readsTheDefault() =
        runTest {
            val (repo, _) = createRepository(this)
            val config = repo.config.first()
            assertEquals(HomeRowConfig(), config)
        }

    @Test
    fun RC2_update_persistsAcrossInstances() =
        runTest {
            val file = File(tmpFolder.root, "home_rows_persist.preferences_pb")
            val scope1 = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val ds1: DataStore<Preferences> =
                PreferenceDataStoreFactory.create(
                    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
                    scope = scope1,
                    produceFile = { file },
                )
            val repo1 = HomeRowConfigRepositoryImpl(ds1)
            repo1.update { it.copy(order = listOf("b", "a"), hidden = setOf("c")) }
            scope1.cancel()

            val scope2 = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val ds2: DataStore<Preferences> =
                PreferenceDataStoreFactory.create(
                    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
                    scope = scope2,
                    produceFile = { file },
                )
            val repo2 = HomeRowConfigRepositoryImpl(ds2)
            val loaded = repo2.config.first()
            assertEquals(listOf("b", "a"), loaded.order)
            assertEquals(setOf("c"), loaded.hidden)
            scope2.cancel()
        }

    @Test
    fun RC3_corruptedStoredValue_readsTheDefault() =
        runTest {
            val (repo, ds) = createRepository(this)
            ds.edit { prefs ->
                prefs[stringPreferencesKey("home_row_config")] = "{oops"
            }
            val config = repo.config.first()
            assertEquals(HomeRowConfig(), config)
        }

    @Test
    fun RC4_reset_clearsTheStoredValue() =
        runTest {
            val (repo, _) = createRepository(this)
            repo.update { it.copy(order = listOf("x")) }
            assertEquals(listOf("x"), repo.config.first().order)

            repo.reset()
            assertEquals(HomeRowConfig(), repo.config.first())
        }

    @Test
    fun RC5_twoQuickUpdates_applyInOrder() =
        runTest {
            val (repo, _) = createRepository(this)
            repo.update { it.copy(order = listOf("row1", "row2")) }
            repo.update { it.copy(hidden = setOf("row1")) }

            val finalConfig = repo.config.first()
            assertEquals(listOf("row1", "row2"), finalConfig.order)
            assertEquals(setOf("row1"), finalConfig.hidden)
        }
}
