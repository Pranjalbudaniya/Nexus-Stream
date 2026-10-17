package com.nexus.stream.domain.usecase

import com.nexus.stream.domain.model.StreamMatch
import org.junit.Assert.assertEquals
import org.junit.Test

class StreamMatchingTest {
    @Test
    fun series_s01e01_vs_request_s1e1_is_ok() {
        val result = StreamMatching.matchOf("Show.Name.S01E01.1080p.mkv", season = 1, episode = 1, year = null)
        assertEquals(StreamMatch.OK, result)
    }

    @Test
    fun series_s01e02_vs_request_s1e1_is_wrong_episode() {
        val result = StreamMatching.matchOf("Show.Name.S01E02.1080p.mkv", season = 1, episode = 1, year = null)
        assertEquals(StreamMatch.WRONG_EPISODE, result)
    }

    @Test
    fun series_s02e01_vs_request_s1e1_is_wrong_season() {
        val result = StreamMatching.matchOf("Show.Name.S02E01.1080p.mkv", season = 1, episode = 1, year = null)
        assertEquals(StreamMatch.WRONG_SEASON, result)
    }

    @Test
    fun series_1x03_form_matches() {
        val okResult = StreamMatching.matchOf("Show.Name.1x03.720p.mkv", season = 1, episode = 3, year = null)
        assertEquals(StreamMatch.OK, okResult)

        val wrongEpResult = StreamMatching.matchOf("Show.Name.1x04.720p.mkv", season = 1, episode = 3, year = null)
        assertEquals(StreamMatch.WRONG_EPISODE, wrongEpResult)
    }

    @Test
    fun series_season_word_form_matches() {
        val result =
            StreamMatching.matchOf("Show Name Season 1 Episode 2 1080p.mkv", season = 1, episode = 2, year = null)
        assertEquals(StreamMatch.OK, result)
    }

    @Test
    fun series_season_pack_matches_right_season_and_flags_wrong_season() {
        val packOk = StreamMatching.matchOf("Show.Name.S01.Complete.1080p", season = 1, episode = 5, year = null)
        assertEquals(StreamMatch.OK, packOk)

        val packWrongSeason =
            StreamMatching.matchOf("Show.Name.S02.Complete.1080p", season = 1, episode = 5, year = null)
        assertEquals(StreamMatch.WRONG_SEASON, packWrongSeason)
    }

    @Test
    fun series_no_numbers_is_unknown() {
        val result = StreamMatching.matchOf("Show.Name.Anime.Maomao.mkv", season = 1, episode = 1, year = null)
        assertEquals(StreamMatch.UNKNOWN, result)
    }

    @Test
    fun movie_year_within_one_year_is_ok() {
        val exactMatch =
            StreamMatching.matchOf("Movie.Title.2023.1080p.mkv", season = null, episode = null, year = 2023)
        assertEquals(StreamMatch.OK, exactMatch)

        val plusOneMatch =
            StreamMatching.matchOf("Movie.Title.2024.1080p.mkv", season = null, episode = null, year = 2023)
        assertEquals(StreamMatch.OK, plusOneMatch)

        val minusOneMatch =
            StreamMatching.matchOf("Movie.Title.2022.1080p.mkv", season = null, episode = null, year = 2023)
        assertEquals(StreamMatch.OK, minusOneMatch)
    }

    @Test
    fun movie_year_2015_vs_2023_is_wrong_year() {
        val result =
            StreamMatching.matchOf("Movie.Title.2015.1080p.mkv", season = null, episode = null, year = 2023)
        assertEquals(StreamMatch.WRONG_YEAR, result)
    }

    @Test
    fun movie_several_years_in_name_ok_if_any_matches() {
        val result =
            StreamMatching.matchOf(
                fileName = "Movie.Title.2015.Remaster.2023.1080p.mkv",
                season = null,
                episode = null,
                year = 2023,
            )
        assertEquals(StreamMatch.OK, result)
    }

    @Test
    fun movie_no_year_is_unknown() {
        val result = StreamMatching.matchOf("Movie.Title.HD.mkv", season = null, episode = null, year = 2023)
        assertEquals(StreamMatch.UNKNOWN, result)
    }

    @Test
    fun null_or_blank_fileName_is_unknown() {
        assertEquals(StreamMatch.UNKNOWN, StreamMatching.matchOf(null, 1, 1, null))
        assertEquals(StreamMatch.UNKNOWN, StreamMatching.matchOf("", null, null, 2023))
    }
}
