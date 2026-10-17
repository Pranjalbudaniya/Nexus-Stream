package com.nexus.stream.data.local.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nexus.stream.domain.model.StreamSort
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
    fun defaultsAreCorrect() =
        runTest {
            val (repo, _) = createRepository(this, "fresh_player_prefs.preferences_pb")
            assertFalse(repo.quickPlay.first())
            assertEquals(StreamSort.BEST, repo.sort.first())
            assertNull(repo.preferredLanguage.first())
            assertNull(repo.preferredQuality.first())
            assertFalse(repo.hideCam.first())
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
    fun setSortPersistsValue() =
        runTest {
            val (repo, _) = createRepository(this, "sort_player_prefs.preferences_pb")
            assertEquals(StreamSort.BEST, repo.sort.first())

            repo.setSort(StreamSort.SEEDERS)
            assertEquals(StreamSort.SEEDERS, repo.sort.first())

            repo.setSort(StreamSort.QUALITY)
            assertEquals(StreamSort.QUALITY, repo.sort.first())
        }

    @Test
    fun setPreferredLanguagePersistsValue() =
        runTest {
            val (repo, _) = createRepository(this, "lang_player_prefs.preferences_pb")
            assertNull(repo.preferredLanguage.first())

            repo.setPreferredLanguage("en")
            assertEquals("en", repo.preferredLanguage.first())

            repo.setPreferredLanguage(null)
            assertNull(repo.preferredLanguage.first())

            repo.setPreferredLanguage("fr")
            assertEquals("fr", repo.preferredLanguage.first())

            repo.setPreferredLanguage("")
            assertNull(repo.preferredLanguage.first())
        }

    @Test
    fun setPreferredQualityPersistsValue() =
        runTest {
            val (repo, _) = createRepository(this, "qual_player_prefs.preferences_pb")
            assertNull(repo.preferredQuality.first())

            repo.setPreferredQuality(1080)
            assertEquals(1080, repo.preferredQuality.first())

            repo.setPreferredQuality(null)
            assertNull(repo.preferredQuality.first())

            repo.setPreferredQuality(2160)
            assertEquals(2160, repo.preferredQuality.first())

            repo.setPreferredQuality(0)
            assertNull(repo.preferredQuality.first())
        }

    @Test
    fun setHideCamPersistsValue() =
        runTest {
            val (repo, _) = createRepository(this, "cam_player_prefs.preferences_pb")
            assertFalse(repo.hideCam.first())

            repo.setHideCam(true)
            assertTrue(repo.hideCam.first())

            repo.setHideCam(false)
            assertFalse(repo.hideCam.first())
        }

    @Test
    fun invalidSortStringDefaultsToBest() =
        runTest {
            val (repo, dataStore) = createRepository(this, "invalid_sort_prefs.preferences_pb")
            dataStore.edit { prefs ->
                prefs[stringPreferencesKey("stream_sort")] = "UNKNOWN_SORT_MODE"
            }
            assertEquals(StreamSort.BEST, repo.sort.first())
        }

    @Test
    fun corruptFileReadsAsDefaults() =
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
            assertEquals(StreamSort.BEST, repo.sort.first())
            assertNull(repo.preferredLanguage.first())
            assertNull(repo.preferredQuality.first())
            assertFalse(repo.hideCam.first())
        }
}
