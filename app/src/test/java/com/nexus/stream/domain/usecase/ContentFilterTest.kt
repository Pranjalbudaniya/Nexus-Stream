package com.nexus.stream.domain.usecase

import com.nexus.stream.domain.model.MetaPreview
import com.nexus.stream.domain.model.PosterShape
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentFilterTest {
    private fun createPreview(
        name: String,
        genres: List<String> = emptyList(),
    ) = MetaPreview(
        id = "id-1",
        type = "movie",
        name = name,
        poster = null,
        posterShape = PosterShape.POSTER,
        background = null,
        logo = null,
        description = null,
        releaseInfo = null,
        imdbRating = null,
        genres = genres,
    )

    @Test
    fun `isAdultText flags adult keywords as whole words in any case`() {
        assertTrue(isAdultText("porn"))
        assertTrue(isAdultText("XXX"))
        assertTrue(isAdultText("Hentai"))
        assertTrue(isAdultText("adult film"))
        assertTrue(isAdultText("Top Adult Video 2024"))
        assertTrue(isAdultText("Some porno title"))
        assertTrue(isAdultText("Pornographic content"))
    }

    @Test
    fun `isAdultText does not flag false positives`() {
        assertFalse(isAdultText("Sex Education"))
        assertFalse(isAdultText("Sex and the City"))
        assertFalse(isAdultText("Hardcore Henry"))
        assertFalse(isAdultText("Adulthood"))
        assertFalse(isAdultText("Pornography of Silence"))
        assertFalse(isAdultText(""))
        assertFalse(isAdultText("   "))
    }

    @Test
    fun `MetaPreview isAdultContent flags adult names and genres`() {
        assertTrue(createPreview("Hentai Adventure").isAdultContent())
        assertTrue(createPreview("Safe Title", listOf("Adult")).isAdultContent())
        assertTrue(createPreview("Safe Title", listOf("erotic")).isAdultContent())
        assertTrue(createPreview("Safe Title", listOf("Erotica")).isAdultContent())
        assertTrue(createPreview("Safe Title", listOf("Hentai")).isAdultContent())
        assertTrue(createPreview("Safe Title", listOf("Porn")).isAdultContent())
        assertTrue(createPreview("Safe Title", listOf("Action", "XXX")).isAdultContent())

        assertFalse(createPreview("Sex Education", listOf("Comedy", "Drama")).isAdultContent())
        assertFalse(createPreview("Hardcore Henry", listOf("Action", "Sci-Fi")).isAdultContent())
        assertFalse(createPreview("", emptyList()).isAdultContent())
    }
}
