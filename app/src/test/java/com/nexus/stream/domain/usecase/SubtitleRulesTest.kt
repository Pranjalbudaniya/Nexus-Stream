package com.nexus.stream.domain.usecase

import com.nexus.stream.domain.model.OnlineSubtitle
import com.nexus.stream.domain.model.Subtitle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleRulesTest {
    @Test
    fun SR1_languageDisplayName_formatsExpectedNames() {
        assertEquals("English", languageDisplayName("en"))
        assertEquals("English", languageDisplayName("eng"))
        assertEquals("French", languageDisplayName("fre"))
        assertEquals("German", languageDisplayName("ger"))
        assertEquals("Chinese", languageDisplayName("chi"))
        assertTrue(languageDisplayName("pt-BR").startsWith("Portuguese"))
        assertEquals("XX", languageDisplayName("xx"))
        assertEquals("Unknown", languageDisplayName(null))
        assertEquals("Unknown", languageDisplayName(""))
        assertEquals("Unknown", languageDisplayName("   "))
    }

    @Test
    fun SR2_groupByLanguage_removesDuplicates_sorts_and_capsAt60() {
        val items = mutableListOf<OnlineSubtitle>()

        // Add 40 English items (with 5 duplicates)
        for (i in 1..40) {
            val url = if (i <= 5) "http://sub.com/en/dup" else "http://sub.com/en/$i"
            items.add(
                OnlineSubtitle(
                    subtitle = Subtitle("e$i", url, "en", "Label ${40 - i}"),
                    addonName = "Addon A",
                ),
            )
        }

        // Add 40 Spanish items
        for (i in 1..40) {
            items.add(
                OnlineSubtitle(
                    subtitle = Subtitle("s$i", "http://sub.com/es/$i", "es", "Spanish $i"),
                    addonName = "Addon B",
                ),
            )
        }

        val groups = groupByLanguage(items, limit = 60)

        // Groups sorted by language name: English before Spanish
        assertEquals(2, groups.size)
        assertEquals("English", groups[0].language)
        assertEquals("Spanish", groups[1].language)

        // Duplicates removed: English had 36 unique items (1 dup used 5 times = 1 + 35 = 36)
        assertEquals(36, groups[0].items.size)

        // Total capped at 60: Spanish gets remaining 24 items
        assertEquals(24, groups[1].items.size)
        val totalItems = groups.sumOf { it.items.size }
        assertEquals(60, totalItems)

        // Items sorted by label then addon name within group
        val labels = groups[0].items.map { it.subtitle.label.orEmpty() }
        assertEquals(labels.sorted(), labels)
    }

    @Test
    fun SR3_isAllowedSubtitleFile_checksExtension() {
        assertTrue(isAllowedSubtitleFile("movie.SRT"))
        assertTrue(isAllowedSubtitleFile("a.vtt"))
        assertTrue(isAllowedSubtitleFile("b.ass"))
        assertTrue(isAllowedSubtitleFile("c.sub"))
        assertTrue(isAllowedSubtitleFile("d.smi"))
        assertTrue(isAllowedSubtitleFile("e.ssa"))

        assertFalse(isAllowedSubtitleFile("a.txt"))
        assertFalse(isAllowedSubtitleFile("a.srt.exe"))
        assertFalse(isAllowedSubtitleFile("srt"))
        assertFalse(isAllowedSubtitleFile(""))
        assertFalse(isAllowedSubtitleFile("   "))
        assertFalse(isAllowedSubtitleFile(null))
    }

    @Test
    fun SR4_formatSubtitleDelay_formatsWithSignAndDecimals() {
        assertEquals("+0.3 s", formatSubtitleDelay(300L))
        assertEquals("-1.5 s", formatSubtitleDelay(-1500L))
        assertEquals("0.0 s", formatSubtitleDelay(0L))
    }

    @Test
    fun SR5_clampSubtitleDelay_clampsToValidRange() {
        assertEquals(30_000L, clampSubtitleDelay(40_000L))
        assertEquals(-30_000L, clampSubtitleDelay(-40_000L))
        assertEquals(500L, clampSubtitleDelay(500L))
    }
}
