package com.nexus.stream.data.local.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.nexus.stream.domain.model.ThemeMode
import com.nexus.stream.domain.model.ThemeSettings
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
class SettingsRepositoryImplTest {
    @get:Rule
    val tmpFolder = TemporaryFolder()

    private fun createRepository(
        testScope: TestScope,
        fileName: String = "test_settings.preferences_pb",
    ): Pair<SettingsRepositoryImpl, DataStore<Preferences>> {
        val testFile = File(tmpFolder.root, fileName)
        val dataStore: DataStore<Preferences> =
            PreferenceDataStoreFactory.create(
                scope = testScope.backgroundScope,
                produceFile = { testFile },
            )
        return Pair(SettingsRepositoryImpl(dataStore), dataStore)
    }

    @Test
    fun freshStore_returnsThemeSettingsDefaults() =
        runTest {
            val (repo, _) = createRepository(this, "fresh.preferences_pb")
            val settings = repo.themeSettings.first()

            assertEquals(ThemeMode.SYSTEM, settings.mode)
            assertFalse(settings.dynamicColor)
            assertFalse(settings.amoled)
            assertNull(settings.accentColor)
            assertTrue(settings.recentAccents.isEmpty())
        }

    @Test
    fun setters_persistIndependentlyAndAreObservable() =
        runTest {
            val (repo, _) = createRepository(this, "setters.preferences_pb")

            repo.setThemeMode(ThemeMode.DARK)
            assertEquals(ThemeMode.DARK, repo.themeSettings.first().mode)

            repo.setDynamicColor(true)
            assertTrue(repo.themeSettings.first().dynamicColor)

            repo.setAmoled(true)
            assertTrue(repo.themeSettings.first().amoled)

            val accentValue = (255L shl 24) or 0x3F51B5L
            repo.setAccentColor(accentValue, remember = false)
            assertEquals(accentValue, repo.themeSettings.first().accentColor)
        }

    @Test
    fun setAccentColor_rememberPrependsDedupesAndCapsAtFive() =
        runTest {
            val (repo, _) = createRepository(this, "accents.preferences_pb")

            val color1 = (255L shl 24) or 1L
            val color2 = (255L shl 24) or 2L
            val color3 = (255L shl 24) or 3L
            val color4 = (255L shl 24) or 4L
            val color5 = (255L shl 24) or 5L
            val color6 = (255L shl 24) or 6L

            repo.setAccentColor(color1, remember = true)
            assertEquals(listOf(color1), repo.themeSettings.first().recentAccents)

            repo.setAccentColor(color2, remember = true)
            assertEquals(listOf(color2, color1), repo.themeSettings.first().recentAccents)

            // Repeat color1 - dedupes and moves to front
            repo.setAccentColor(color1, remember = true)
            assertEquals(listOf(color1, color2), repo.themeSettings.first().recentAccents)

            // Add 3, 4, 5, 6 - caps at 5
            repo.setAccentColor(color3, remember = true)
            repo.setAccentColor(color4, remember = true)
            repo.setAccentColor(color5, remember = true)
            repo.setAccentColor(color6, remember = true)
            assertEquals(
                listOf(color6, color5, color4, color3, color1),
                repo.themeSettings.first().recentAccents,
            )

            // remember = false leaves recents alone
            val color7 = (255L shl 24) or 7L
            repo.setAccentColor(color7, remember = false)
            assertEquals(color7, repo.themeSettings.first().accentColor)
            assertEquals(
                listOf(color6, color5, color4, color3, color1),
                repo.themeSettings.first().recentAccents,
            )

            // setAccentColor(null) clears accentColor
            repo.setAccentColor(null)
            assertNull(repo.themeSettings.first().accentColor)
        }

    @Test
    fun togglingDynamicColor_doesNotChangeStoredAccent() =
        runTest {
            val (repo, _) = createRepository(this, "dynamic.preferences_pb")
            val accentValue = (255L shl 24) or 0x6D5EF5L

            repo.setAccentColor(accentValue)
            repo.setDynamicColor(true)
            assertEquals(accentValue, repo.themeSettings.first().accentColor)
            assertTrue(repo.themeSettings.first().dynamicColor)

            repo.setDynamicColor(false)
            assertEquals(accentValue, repo.themeSettings.first().accentColor)
            assertFalse(repo.themeSettings.first().dynamicColor)
        }

    @Test
    fun corruptStoredValues_fallbackToDefaults() =
        runTest {
            val (repo, dataStore) = createRepository(this, "corrupt.preferences_pb")

            val validColor = (255L shl 24) or 12L
            dataStore.edit { prefs ->
                prefs[SettingsRepositoryImpl.KEY_THEME_MODE] = "BANANA"
                prefs[SettingsRepositoryImpl.KEY_RECENT_ACCENTS] = "x,,12,abc"
            }

            val settings = repo.themeSettings.first()
            assertEquals(ThemeMode.SYSTEM, settings.mode)
            assertEquals(listOf(validColor), settings.recentAccents)
        }

    @Test
    fun resetTheme_restoresAllDefaultsAndClearsRecents() =
        runTest {
            val (repo, _) = createRepository(this, "reset.preferences_pb")

            repo.setThemeMode(ThemeMode.DARK)
            repo.setDynamicColor(true)
            repo.setAmoled(true)
            repo.setAccentColor((255L shl 24) or 0x123456L, remember = true)

            repo.resetTheme()

            val settings = repo.themeSettings.first()
            assertEquals(ThemeSettings(), settings)
        }
}
