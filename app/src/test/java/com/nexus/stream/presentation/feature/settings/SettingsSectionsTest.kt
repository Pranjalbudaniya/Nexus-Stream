package com.nexus.stream.presentation.feature.settings

import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.ui.graphics.toArgb
import com.nexus.stream.domain.model.AppFont
import com.nexus.stream.domain.model.ThemeMode
import com.nexus.stream.domain.model.ThemeSettings
import com.nexus.stream.presentation.theme.tokens.BrandAccent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSectionsTest {
    private val dummySaverScope = SaverScope { true }

    @Test
    fun EX1_toggle_opensAClosedSection() {
        val initial = emptySet<SettingsSection>()
        val updated = initial.toggle(SettingsSection.APPEARANCE)
        assertEquals(setOf(SettingsSection.APPEARANCE), updated)
    }

    @Test
    fun EX2_toggle_closesAnOpenSection() {
        val initial = setOf(SettingsSection.APPEARANCE)
        val updated = initial.toggle(SettingsSection.APPEARANCE)
        assertEquals(emptySet<SettingsSection>(), updated)
    }

    @Test
    fun EX3_toggle_keepsOtherSectionsAsTheyAre() {
        val initial = setOf(SettingsSection.PLAYER, SettingsSection.TORRENT)
        val updated = initial.toggle(SettingsSection.PLAYER)
        assertEquals(setOf(SettingsSection.TORRENT), updated)
    }

    @Test
    fun EX4_initial_isAllCollapsed() {
        assertEquals(emptySet<SettingsSection>(), initialSettingsSections())
    }

    @Test
    fun EX5_sectionOrder_isFixed() {
        val expected =
            listOf(
                SettingsSection.APPEARANCE,
                SettingsSection.CONTENT,
                SettingsSection.PLAYER,
                SettingsSection.SUBTITLES,
                SettingsSection.TORRENT,
                SettingsSection.ABOUT,
            )
        assertEquals(expected, SettingsSection.entries)
    }

    @Test
    fun EX6_saver_roundTrips_andDropsUnknownIds() {
        val original = setOf(SettingsSection.ABOUT, SettingsSection.PLAYER)
        val saved = with(SettingsSectionSaver) { dummySaverScope.save(original) }

        @Suppress("UNCHECKED_CAST")
        val restored = SettingsSectionSaver.restore(saved as List<String>)
        assertEquals(original, restored)

        val withUnknown = listOf("ABOUT", "PLAYER", "gone")
        val restoredWithUnknown = SettingsSectionSaver.restore(withUnknown)
        assertEquals(original, restoredWithUnknown)
    }

    @Test
    fun SM1_appearance_summary_modeAccentFont() {
        val violetArgb = (255L shl 24) or (BrandAccent.toArgb().toLong() and 0x00FFFFFFL)
        val settings =
            ThemeSettings(
                mode = ThemeMode.DARK,
                dynamicColor = false,
                accentColor = violetArgb,
                font = AppFont.INTER,
            )
        val summary = appearanceSummary(settings)
        assertEquals("Dark · Violet · Inter", summary)
    }

    @Test
    fun SM2_appearance_summary_dynamicColorOn() {
        val settings =
            ThemeSettings(
                mode = ThemeMode.DARK,
                dynamicColor = true,
                font = AppFont.INTER,
            )
        val summary = appearanceSummary(settings)
        assertTrue(summary.contains("Dynamic color"))
        assertEquals("Dark · Dynamic color · Inter", summary)
    }

    @Test
    fun SM3_appearance_summary_customAccent() {
        val settings =
            ThemeSettings(
                mode = ThemeMode.DARK,
                dynamicColor = false,
                accentColor = 0xFF123456L,
                font = AppFont.INTER,
            )
        val summary = appearanceSummary(settings)
        assertTrue(summary.contains("Custom"))
        assertEquals("Dark · Custom · Inter", summary)
    }

    @Test
    fun SM4_content_summary_followsFamilyMode() {
        assertEquals("Family mode on", contentSummary(familyMode = true))
        assertEquals("Family mode off", contentSummary(familyMode = false))
    }

    @Test
    fun SM5_player_summary_qualityAndSkip() {
        val formatted = playerSummary(preferredQuality = 1080, skipLengthSeconds = 10)
        assertTrue(formatted.contains("1080p"))
        assertTrue(formatted.contains("Skip 10 s"))
        assertEquals("1080p · Skip 10 s", formatted)

        val autoFormatted = playerSummary(preferredQuality = null, skipLengthSeconds = 10)
        assertEquals("Auto · Skip 10 s", autoFormatted)
    }

    @Test
    fun SM6_subtitles_summary_languagesOrNotSet() {
        val summary = subtitlesSummary(preferredAudio = "en", preferredSubtitle = null)
        assertTrue(summary.contains("Audio: English"))
        assertTrue(summary.contains("Subtitles: Not set"))
        assertEquals("Audio: English · Subtitles: Not set", summary)
    }

    @Test
    fun SM7_torrent_summary_wifiAndLimit() {
        assertEquals("Wi-Fi only · 2 GB cache", torrentSummary(wifiOnly = true, cacheLimitGb = 2))
        assertEquals("Any network · 2 GB cache", torrentSummary(wifiOnly = false, cacheLimitGb = 2))
    }

    @Test
    fun SM8_about_summary_version() {
        assertEquals("Version 1.0.0", aboutSummary(versionName = "1.0.0", versionCode = 10000L))
        assertEquals("10000", aboutSummary(versionName = null, versionCode = 10000L))
    }

    @Test
    fun SM9_summary_isOnlyUsedWhileCollapsed() {
        assertNull(sectionSummary(expanded = true, summary = "Some summary"))
        assertEquals("Some summary", sectionSummary(expanded = false, summary = "Some summary"))
    }
}
