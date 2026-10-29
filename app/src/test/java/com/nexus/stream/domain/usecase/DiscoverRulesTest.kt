package com.nexus.stream.domain.usecase

import com.nexus.stream.domain.model.FilterControl
import com.nexus.stream.domain.model.GenreKind
import com.nexus.stream.domain.model.MetaPreview
import com.nexus.stream.domain.model.PosterShape
import com.nexus.stream.domain.model.TypeDisplay
import com.nexus.stream.domain.model.filterControlOf
import org.junit.Assert.assertEquals
import org.junit.Test

class DiscoverRulesTest {
    @Test
    fun GL1_allFourDigitNumbers_isYear() {
        val result = genreKind(listOf("2024", "2023", "1999"))
        assertEquals(GenreKind.YEAR, result)
    }

    @Test
    fun GL2_wordsOrMixed_isGenre() {
        val result = genreKind(listOf("Action", "2024"))
        assertEquals(GenreKind.GENRE, result)
    }

    @Test
    fun GL3_empty_isGenre() {
        val result = genreKind(emptyList())
        assertEquals(GenreKind.GENRE, result)
    }

    @Test
    fun GC1_anyLandscape_isLandscapeGrid() {
        val items =
            listOf(
                createMeta("1", PosterShape.POSTER),
                createMeta("2", PosterShape.LANDSCAPE),
                createMeta("3", PosterShape.POSTER),
            )
        val shape = gridShapeOf(items)
        assertEquals(PosterShape.LANDSCAPE, shape)
    }

    @Test
    fun GC2_postersAndSquares_isPosterGrid() {
        val items =
            listOf(
                createMeta("1", PosterShape.POSTER),
                createMeta("2", PosterShape.SQUARE),
                createMeta("3", PosterShape.POSTER),
            )
        val shape = gridShapeOf(items)
        assertEquals(PosterShape.POSTER, shape)
    }

    @Test
    fun GC3_emptyList_isPosterGrid() {
        val shape = gridShapeOf(emptyList())
        assertEquals(PosterShape.POSTER, shape)
    }

    @Test
    fun GT1_movieAndSeries_areKnownTypes() {
        assertEquals(TypeDisplay.Movie, typeDisplay("movie"))
        assertEquals(TypeDisplay.Series, typeDisplay("series"))
        assertEquals(TypeDisplay.Movie, typeDisplay("MOVIE"))
        assertEquals(TypeDisplay.Series, typeDisplay("Series"))
    }

    @Test
    fun GT2_otherTypes_areCapitalisedText() {
        assertEquals(TypeDisplay.Custom("Tv"), typeDisplay("tv"))
        assertEquals(TypeDisplay.Custom("Channel"), typeDisplay("channel"))
    }

    @Test
    fun FC1_oneEntry_isText() {
        assertEquals(FilterControl.Text, filterControlOf(1))
    }

    @Test
    fun FC2_twoOrMoreEntries_isDropdown() {
        assertEquals(FilterControl.Dropdown, filterControlOf(2))
        assertEquals(FilterControl.Dropdown, filterControlOf(7))
    }

    @Test
    fun FC3_noEntries_isHidden() {
        assertEquals(FilterControl.Hidden, filterControlOf(0))
    }

    private fun createMeta(
        id: String,
        shape: PosterShape,
    ): MetaPreview =
        MetaPreview(
            id = id,
            type = "movie",
            name = "Title $id",
            poster = null,
            posterShape = shape,
            background = null,
            logo = null,
            description = null,
            releaseInfo = null,
            imdbRating = null,
            genres = emptyList(),
        )
}
