package com.nexus.stream.domain.usecase

import com.nexus.stream.data.player.PlayerTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackLanguageRulesTest {
    @Test
    fun TL1_matchingByNameAndOwnLanguage() {
        val tracks =
            listOf(
                PlayerTrack("1", "Track 1 - [English]"),
                PlayerTrack("2", "Track 2 - [Spanish]"),
                PlayerTrack("3", "Audio - Español"),
                PlayerTrack("4", "Audio - Deutsch"),
            )

        val matchEn = matchTrackByLanguage(tracks, "en")
        assertNotNull(matchEn)
        assertEquals("1", matchEn?.id)

        val matchEs = matchTrackByLanguage(tracks, "es")
        assertNotNull(matchEs)
        assertEquals("2", matchEs?.id)

        val tracksEspanolOnly = listOf(PlayerTrack("3", "Audio - Español"))
        val matchEspanol = matchTrackByLanguage(tracksEspanolOnly, "es")
        assertNotNull(matchEspanol)
        assertEquals("3", matchEspanol?.id)

        val matchDe = matchTrackByLanguage(tracks, "de")
        assertNotNull(matchDe)
        assertEquals("4", matchDe?.id)
    }

    @Test
    fun TL2_firstMatchingTrackWins() {
        val tracks =
            listOf(
                PlayerTrack("10", "First English"),
                PlayerTrack("20", "Second English"),
            )
        val match = matchTrackByLanguage(tracks, "en")
        assertEquals("10", match?.id)
    }

    @Test
    fun TL3_wordBoundary_andEdgeCases() {
        val tracks =
            listOf(
                PlayerTrack("1", "Grand Piano"),
                PlayerTrack("2", "Nonexistent Track"),
            )
        assertNull(matchTrackByLanguage(tracks, "no"))

        val norwegianTracks = listOf(PlayerTrack("3", "Norwegian Stereo"))
        val matchNor = matchTrackByLanguage(norwegianTracks, "no")
        assertEquals("3", matchNor?.id)

        assertNull(matchTrackByLanguage(norwegianTracks, ""))
        assertNull(matchTrackByLanguage(norwegianTracks, "   "))
        assertNull(matchTrackByLanguage(emptyList(), "en"))
        assertNull(matchTrackByLanguage(tracks, "xyz_not_found"))
    }

    @Test
    fun TL4_preferredLanguageOptions_25UniqueCodesNoUnknown() {
        assertEquals(25, PREFERRED_LANGUAGE_OPTIONS.size)
        val codes = PREFERRED_LANGUAGE_OPTIONS.map { it.code }
        assertEquals(25, codes.distinct().size)
        assertTrue(PREFERRED_LANGUAGE_OPTIONS.none { it.name.equals("Unknown", ignoreCase = true) })
        assertTrue(PREFERRED_LANGUAGE_OPTIONS.none { it.name.isBlank() })
    }

    @Test
    fun TL5_threeLetterCodes_matchOnlyAsWholeWord() {
        val tracks =
            listOf(
                PlayerTrack("1", "Audio [eng]"),
                PlayerTrack("2", "Audio [fre]"),
                PlayerTrack("3", "Strength"),
            )

        val matchEng = matchTrackByLanguage(tracks, "en")
        assertEquals("1", matchEng?.id)

        val matchFre = matchTrackByLanguage(tracks, "fr")
        assertEquals("2", matchFre?.id)

        // "eng" is substring of "Strength", but should not match as whole word
        val strengthOnly = listOf(PlayerTrack("3", "Strength"))
        assertNull(matchTrackByLanguage(strengthOnly, "en"))
    }
}
