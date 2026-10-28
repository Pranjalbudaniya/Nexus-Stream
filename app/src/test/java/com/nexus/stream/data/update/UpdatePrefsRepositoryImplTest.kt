package com.nexus.stream.data.update

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class UpdatePrefsRepositoryImplTest {
    @get:Rule
    val tmpFolder = TemporaryFolder()

    private fun createRepository(
        testScope: TestScope,
        fileName: String = "update_prefs_test.preferences_pb",
    ): Pair<UpdatePrefsRepositoryImpl, DataStore<Preferences>> {
        val testFile = File(tmpFolder.root, fileName)
        val dataStore: DataStore<Preferences> =
            PreferenceDataStoreFactory.create(
                corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
                scope = testScope.backgroundScope,
                produceFile = { testFile },
            )
        return Pair(UpdatePrefsRepositoryImpl(dataStore), dataStore)
    }

    @Test
    fun UP1_autoCheck_defaultsToTrue() =
        runTest {
            val (repo, _) = createRepository(this)
            assertTrue(repo.autoCheck.first())
        }

    @Test
    fun UP2_setAutoCheck_persists() =
        runTest {
            val file = File(tmpFolder.root, "update_prefs_persist.preferences_pb")
            val scope1 = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val ds1: DataStore<Preferences> =
                PreferenceDataStoreFactory.create(
                    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
                    scope = scope1,
                    produceFile = { file },
                )
            val repo1 = UpdatePrefsRepositoryImpl(ds1)
            repo1.setAutoCheck(false)
            scope1.cancel()

            val scope2 = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val ds2: DataStore<Preferences> =
                PreferenceDataStoreFactory.create(
                    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
                    scope = scope2,
                    produceFile = { file },
                )
            val repo2 = UpdatePrefsRepositoryImpl(ds2)
            assertFalse(repo2.autoCheck.first())
            scope2.cancel()
        }

    @Test
    fun UP3_lastCheckedAt_roundTrips() =
        runTest {
            val (repo, _) = createRepository(this)
            assertNull(repo.lastCheckedAt.first())
            repo.setLastCheckedAt(123L)
            assertEquals(123L, repo.lastCheckedAt.first())
        }

    @Test
    fun UP4_pendingInstall_roundTripsAndClears() =
        runTest {
            val (repo, _) = createRepository(this)
            repo.setPendingInstallCode(10001L)
            assertEquals(10001L, repo.pendingInstallCode.first())

            repo.clearPendingInstall()
            assertNull(repo.pendingInstallCode.first())
        }
}
