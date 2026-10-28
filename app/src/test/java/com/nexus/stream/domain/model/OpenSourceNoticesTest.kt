package com.nexus.stream.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenSourceNoticesTest {
    @Test
    fun ON1_everyEntry_hasNameLicenseAndHttpsUrl() {
        assertTrue(OPEN_SOURCE_NOTICES.isNotEmpty())
        OPEN_SOURCE_NOTICES.forEach { notice ->
            assertTrue("Name should not be blank", notice.name.isNotBlank())
            assertTrue("License should not be blank for ${notice.name}", notice.license.isNotBlank())
            assertTrue("URL should start with https:// for ${notice.name}", notice.url.startsWith("https://"))
        }
    }

    @Test
    fun ON2_names_areUnique() {
        val names = OPEN_SOURCE_NOTICES.map { it.name }
        assertEquals("Notice names must be unique", names.distinct().size, names.size)
    }

    @Test
    fun ON3_list_includesTheNativeAndFontLibraries() {
        val names = OPEN_SOURCE_NOTICES.map { it.name }
        assertTrue("Must include libVLC", names.contains("libVLC"))
        assertTrue("Must include jlibtorrent", names.contains("jlibtorrent"))
        assertTrue("Must include libtorrent", names.contains("libtorrent"))
        val fontNames =
            listOf(
                "Inter",
                "Poppins",
                "Outfit",
                "DM Sans",
                "Figtree",
                "Lexend",
                "Manrope",
                "Nunito",
                "Plus Jakarta Sans",
                "Space Grotesk",
            )
        val hasFont = names.any { it in fontNames }
        assertTrue("Must include at least one font family", hasFont)
    }

    @Test
    fun ON4_list_isSortedByName() {
        val names = OPEN_SOURCE_NOTICES.map { it.name }
        val sortedNames = names.sortedWith(String.CASE_INSENSITIVE_ORDER)
        assertEquals(sortedNames, names)
    }
}
