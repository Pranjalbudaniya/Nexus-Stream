package com.nexus.stream.domain.usecase

import com.nexus.stream.domain.model.StreamLanguages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguageCodesTest {
    private fun flag(
        c1: Char,
        c2: Char,
    ): String =
        String(Character.toChars(0x1F1E6 + (c1 - 'A'))) +
            String(Character.toChars(0x1F1E6 + (c2 - 'A')))

    @Test
    fun `all names in 4_1 map correctly`() {
        val nameMap =
            mapOf(
                "English" to "en",
                "Japanese" to "ja",
                "Korean" to "ko",
                "Chinese" to "zh",
                "Mandarin" to "zh",
                "Cantonese" to "zh",
                "Hindi" to "hi",
                "Tamil" to "ta",
                "Telugu" to "te",
                "Spanish" to "es",
                "Latino" to "es",
                "French" to "fr",
                "German" to "de",
                "Italian" to "it",
                "Portuguese" to "pt",
                "Russian" to "ru",
                "Arabic" to "ar",
                "Turkish" to "tr",
                "Thai" to "th",
                "Polish" to "pl",
                "Dutch" to "nl",
                "Swedish" to "sv",
                "Norwegian" to "no",
                "Danish" to "da",
                "Finnish" to "fi",
                "Czech" to "cs",
                "Hungarian" to "hu",
                "Hebrew" to "he",
                "Indonesian" to "id",
                "Vietnamese" to "vi",
            )

        for ((name, expectedCode) in nameMap) {
            val languages = StreamLanguages(names = listOf(name))
            val codes = LanguageCodes.codes(languages)
            assertEquals("Expected $name to map to $expectedCode", setOf(expectedCode), codes)
        }
    }

    @Test
    fun `all flags in 4_1 map correctly`() {
        val flagMap =
            mapOf(
                flag('G', 'B') to "en",
                flag('U', 'S') to "en",
                flag('A', 'U') to "en",
                flag('C', 'A') to "en",
                flag('N', 'Z') to "en",
                flag('I', 'E') to "en",
                flag('F', 'R') to "fr",
                flag('E', 'S') to "es",
                flag('M', 'X') to "es",
                flag('C', 'O') to "es",
                flag('A', 'R') to "es",
                flag('D', 'E') to "de",
                flag('A', 'T') to "de",
                flag('I', 'T') to "it",
                flag('P', 'T') to "pt",
                flag('B', 'R') to "pt",
                flag('R', 'U') to "ru",
                flag('U', 'A') to "uk",
                flag('J', 'P') to "ja",
                flag('K', 'R') to "ko",
                flag('C', 'N') to "zh",
                flag('T', 'W') to "zh",
                flag('H', 'K') to "zh",
                flag('I', 'N') to "hi",
                flag('S', 'A') to "ar",
                flag('A', 'E') to "ar",
                flag('E', 'G') to "ar",
                flag('T', 'R') to "tr",
                flag('T', 'H') to "th",
                flag('P', 'L') to "pl",
                flag('N', 'L') to "nl",
                flag('S', 'E') to "sv",
                flag('N', 'O') to "no",
                flag('D', 'K') to "da",
                flag('F', 'I') to "fi",
                flag('C', 'Z') to "cs",
                flag('H', 'U') to "hu",
                flag('I', 'L') to "he",
                flag('I', 'D') to "id",
                flag('V', 'N') to "vi",
            )

        for ((f, expectedCode) in flagMap) {
            val languages = StreamLanguages(flags = listOf(f))
            val codes = LanguageCodes.codes(languages)
            assertEquals("Expected flag $f to map to $expectedCode", setOf(expectedCode), codes)
        }
    }

    @Test
    fun `unknown flags and names are ignored`() {
        val unknownFlag = flag('Z', 'Z')
        val languages =
            StreamLanguages(
                flags = listOf(unknownFlag),
                names = listOf("Klingon", "Elvish"),
            )
        val codes = LanguageCodes.codes(languages)
        assertTrue(codes.isEmpty())
    }

    @Test
    fun `several flags give a set of unique codes`() {
        val languages =
            StreamLanguages(
                flags = listOf(flag('U', 'S'), flag('G', 'B'), flag('F', 'R'), flag('J', 'P')),
            )
        val codes = LanguageCodes.codes(languages)
        assertEquals(setOf("en", "fr", "ja"), codes)
    }

    @Test
    fun `multi and dualAudio add nothing to languageCodes`() {
        val languages =
            StreamLanguages(
                multi = true,
                dualAudio = true,
                dubbed = true,
            )
        val codes = LanguageCodes.codes(languages)
        assertTrue(codes.isEmpty())
    }

    @Test
    fun `languageDisplayName formats known and unknown codes`() {
        assertEquals("English", LanguageCodes.languageDisplayName("en"))
        assertEquals("Hindi", LanguageCodes.languageDisplayName("hi"))
        assertEquals("XYZ", LanguageCodes.languageDisplayName("xyz"))
    }

    @Test
    fun `top-level languageCodes delegates to LanguageCodes codes`() {
        val languages = StreamLanguages(names = listOf("English"))
        assertEquals(setOf("en"), languageCodes(languages))
    }
}
