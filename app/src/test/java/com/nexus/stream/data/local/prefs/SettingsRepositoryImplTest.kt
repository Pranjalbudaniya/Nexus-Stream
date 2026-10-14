package com.nexus.stream.data.local.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.nexus.stream.domain.model.AppFont
import com.nexus.stream.domain.model.CardStyle
import com.nexus.stream.domain.model.ContrastLevel
import com.nexus.stream.domain.model.Roundness
import com.nexus.stream.domain.model.TextSize
import com.nexus.stream.domain.model.ThemeMode
import com.nexus.stream.domain.model.ThemeSettings
import com.nexus.stream.domain.model.UiSize
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
            assertEquals(AppFont.INTER, settings.font)
            assertEquals(UiSize.NORMAL, settings.uiSize)
            assertEquals(TextSize.NORMAL, settings.textSize)
            assertEquals(Roundness.NORMAL, settings.roundness)
            assertEquals(CardStyle.FILLED, settings.cardStyle)
            assertEquals(ContrastLevel.STANDARD, settings.contrast)
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
    fun update_persistsEachFieldCorrectly() =
        runTest {
            val (repo, _) = createRepository(this, "update_fields.preferences_pb")

            repo.update {
                it.copy(
                    font = AppFont.OUTFIT,
                    uiSize = UiSize.LARGE,
                    textSize = TextSize.SMALL,
                    roundness = Roundness.EXTRA_ROUND,
                    cardStyle = CardStyle.ELEVATED,
                    contrast = ContrastLevel.HIGH,
                )
            }

            val settings = repo.themeSettings.first()
            assertEquals(AppFont.OUTFIT, settings.font)
            assertEquals(UiSize.LARGE, settings.uiSize)
            assertEquals(TextSize.SMALL, settings.textSize)
            assertEquals(Roundness.EXTRA_ROUND, settings.roundness)
            assertEquals(CardStyle.ELEVATED, settings.cardStyle)
            assertEquals(ContrastLevel.HIGH, settings.contrast)
        }

    @Test
    fun update_isAtomicUnderConcurrentCalls() =
        runTest {
            val (repo, _) = createRepository(this, "atomic.preferences_pb")

            coroutineScope {
                launch {
                    repo.update { it.copy(font = AppFont.FIGTREE) }
                }
                launch {
                    repo.update { it.copy(cardStyle = CardStyle.OUTLINED) }
                }
            }

            val settings = repo.themeSettings.first()
            assertEquals(AppFont.FIGTREE, settings.font)
            assertEquals(CardStyle.OUTLINED, settings.cardStyle)
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
    fun togglingDynamicColor_doesNotChangeOtherStoredFields() =
        runTest {
            val (repo, _) = createRepository(this, "dynamic.preferences_pb")
            val accentValue = (255L shl 24) or 0x6D5EF5L

            repo.update {
                it.copy(
                    accentColor = accentValue,
                    font = AppFont.MANROPE,
                    uiSize = UiSize.LARGEST,
                    textSize = TextSize.LARGE,
                    roundness = Roundness.SOFT,
                    cardStyle = CardStyle.OUTLINED,
                    contrast = ContrastLevel.MEDIUM,
                )
            }

            repo.setDynamicColor(true)
            var current = repo.themeSettings.first()
            assertTrue(current.dynamicColor)
            assertEquals(accentValue, current.accentColor)
            assertEquals(AppFont.MANROPE, current.font)
            assertEquals(UiSize.LARGEST, current.uiSize)
            assertEquals(TextSize.LARGE, current.textSize)
            assertEquals(Roundness.SOFT, current.roundness)
            assertEquals(CardStyle.OUTLINED, current.cardStyle)
            assertEquals(ContrastLevel.MEDIUM, current.contrast)

            repo.setDynamicColor(false)
            current = repo.themeSettings.first()
            assertFalse(current.dynamicColor)
            assertEquals(accentValue, current.accentColor)
            assertEquals(AppFont.MANROPE, current.font)
            assertEquals(UiSize.LARGEST, current.uiSize)
            assertEquals(TextSize.LARGE, current.textSize)
            assertEquals(Roundness.SOFT, current.roundness)
            assertEquals(CardStyle.OUTLINED, current.cardStyle)
            assertEquals(ContrastLevel.MEDIUM, current.contrast)
        }

    @Test
    fun corruptStoredValues_fallbackToDefaults() =
        runTest {
            val (repo, dataStore) = createRepository(this, "corrupt.preferences_pb")

            val validColor = (255L shl 24) or 12L
            dataStore.edit { prefs ->
                prefs[SettingsRepositoryImpl.KEY_THEME_MODE] = "BANANA"
                prefs[SettingsRepositoryImpl.KEY_RECENT_ACCENTS] = "x,,12,abc"
                prefs[SettingsRepositoryImpl.KEY_FONT] = "BANANA"
                prefs[SettingsRepositoryImpl.KEY_UI_SIZE] = "BANANA"
                prefs[SettingsRepositoryImpl.KEY_TEXT_SIZE] = "BANANA"
                prefs[SettingsRepositoryImpl.KEY_ROUNDNESS] = "BANANA"
                prefs[SettingsRepositoryImpl.KEY_CARD_STYLE] = "BANANA"
                prefs[SettingsRepositoryImpl.KEY_CONTRAST] = "BANANA"
            }

            val settings = repo.themeSettings.first()
            assertEquals(ThemeMode.SYSTEM, settings.mode)
            assertEquals(listOf(validColor), settings.recentAccents)
            assertEquals(AppFont.INTER, settings.font)
            assertEquals(UiSize.NORMAL, settings.uiSize)
            assertEquals(TextSize.NORMAL, settings.textSize)
            assertEquals(Roundness.NORMAL, settings.roundness)
            assertEquals(CardStyle.FILLED, settings.cardStyle)
            assertEquals(ContrastLevel.STANDARD, settings.contrast)
        }

    @Test
    fun resetTheme_restoresAllDefaultsAndClearsRecents() =
        runTest {
            val (repo, _) = createRepository(this, "reset.preferences_pb")

            repo.update {
                it.copy(
                    mode = ThemeMode.DARK,
                    dynamicColor = true,
                    amoled = true,
                    accentColor = (255L shl 24) or 0x123456L,
                    recentAccents = listOf((255L shl 24) or 0x123456L),
                    font = AppFont.SPACE_GROTESK,
                    uiSize = UiSize.SMALL,
                    textSize = TextSize.LARGER,
                    roundness = Roundness.SHARP,
                    cardStyle = CardStyle.ELEVATED,
                    contrast = ContrastLevel.HIGH,
                )
            }

            repo.resetTheme()

            val settings = repo.themeSettings.first()
            assertEquals(ThemeSettings(), settings)
        }
}
