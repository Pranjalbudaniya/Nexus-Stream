package com.nexus.stream.data.local.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nexus.stream.data.player.VideoScale
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
            assertEquals(10, repo.seekStepSeconds.first())
            assertEquals(VideoScale.FIT, repo.videoScale.first())
        }

    @Test
    fun PP1_PP2_PP3_PP4_seekStepSecondsBehavior() =
        runTest {
            val (repo, dataStore) = createRepository(this, "seek_step_prefs.preferences_pb")
            // PP1: default 10
            assertEquals(10, repo.seekStepSeconds.first())

            // PP2: set 15 reads 15
            repo.setSeekStepSeconds(15)
            assertEquals(15, repo.seekStepSeconds.first())

            // PP3: stored 7 reads 10
            dataStore.edit { prefs ->
                prefs[intPreferencesKey("seek_step_seconds")] = 7
            }
            assertEquals(10, repo.seekStepSeconds.first())

            // PP4: setSeekStepSeconds(7) is ignored
            repo.setSeekStepSeconds(20)
            assertEquals(20, repo.seekStepSeconds.first())
            repo.setSeekStepSeconds(7)
            assertEquals(20, repo.seekStepSeconds.first())
        }

    @Test
    fun PP5_PP6_PP7_videoScaleBehavior() =
        runTest {
            val (repo, dataStore) = createRepository(this, "video_scale_prefs.preferences_pb")
            // PP5: default FIT
            assertEquals(VideoScale.FIT, repo.videoScale.first())

            // PP6: set CROP reads CROP
            repo.setVideoScale(VideoScale.CROP)
            assertEquals(VideoScale.CROP, repo.videoScale.first())

            // PP7: unknown stored value reads FIT
            dataStore.edit { prefs ->
                prefs[stringPreferencesKey("video_scale")] = "UNKNOWN_SCALE_VALUE"
            }
            assertEquals(VideoScale.FIT, repo.videoScale.first())
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
