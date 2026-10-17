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

class PlayerPrefsRepositoryImplTest {
    @get:Rule
    val tmpFolder = TemporaryFolder()

    private fun createRepository(
        testScope: TestScope,
        fileName: String,
    ): Pair<PlayerPrefsRepositoryImpl, DataStore<Preferences>> {
        val testFile = File(tmpFolder.root, fileName)
        val dataStore: DataStore<Preferences> =
            PreferenceDataStoreFactory.create(
                scope = testScope.backgroundScope,
                produceFile = { testFile },
            )
        return Pair(PlayerPrefsRepositoryImpl(dataStore), dataStore)
    }

    @Test
    fun defaultIsFalse() =
        runTest {
            val (repo, _) = createRepository(this, "fresh_player_prefs.preferences_pb")
            assertFalse(repo.quickPlay.first())
        }

    @Test
    fun setQuickPlayPersistsValue() =
        runTest {
            val (repo, _) = createRepository(this, "persisted_player_prefs.preferences_pb")
            assertFalse(repo.quickPlay.first())

            repo.setQuickPlay(true)
            assertTrue(repo.quickPlay.first())

            repo.setQuickPlay(false)
            assertFalse(repo.quickPlay.first())
        }

    @Test
    fun corruptValueReadsAsFalse() =
        runTest {
            val corruptFile = File(tmpFolder.root, "corrupt_player_prefs.preferences_pb")
            corruptFile.writeBytes(byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x7F, -1, -2))

            val dataStore: DataStore<Preferences> =
                PreferenceDataStoreFactory.create(
                    scope = backgroundScope,
                    produceFile = { corruptFile },
                )
            val repo = PlayerPrefsRepositoryImpl(dataStore)
            assertFalse(repo.quickPlay.first())
        }
}
