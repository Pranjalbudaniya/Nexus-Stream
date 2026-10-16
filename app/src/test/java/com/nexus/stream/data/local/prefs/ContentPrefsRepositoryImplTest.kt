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

class ContentPrefsRepositoryImplTest {
    @get:Rule
    val tmpFolder = TemporaryFolder()

    private fun createRepository(
        testScope: TestScope,
        fileName: String,
    ): Pair<ContentPrefsRepositoryImpl, DataStore<Preferences>> {
        val testFile = File(tmpFolder.root, fileName)
        val dataStore: DataStore<Preferences> =
            PreferenceDataStoreFactory.create(
                scope = testScope.backgroundScope,
                produceFile = { testFile },
            )
        return Pair(ContentPrefsRepositoryImpl(dataStore), dataStore)
    }

    @Test
    fun `default is true`() =
        runTest {
            val (repo, _) = createRepository(this, "fresh_content_prefs.preferences_pb")
            assertTrue(repo.familyMode.first())
        }

    @Test
    fun `setFamilyMode persists value`() =
        runTest {
            val (repo, _) = createRepository(this, "persisted_content_prefs.preferences_pb")
            assertTrue(repo.familyMode.first())

            repo.setFamilyMode(false)
            assertFalse(repo.familyMode.first())

            repo.setFamilyMode(true)
            assertTrue(repo.familyMode.first())
        }

    @Test
    fun `corrupt value reads as true`() =
        runTest {
            val corruptFile = File(tmpFolder.root, "corrupt_content_prefs.preferences_pb")
            corruptFile.writeBytes(byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x7F, -1, -2))

            val dataStore: DataStore<Preferences> =
                PreferenceDataStoreFactory.create(
                    scope = backgroundScope,
                    produceFile = { corruptFile },
                )
            val repo = ContentPrefsRepositoryImpl(dataStore)
            assertTrue(repo.familyMode.first())
        }
}
