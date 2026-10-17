package com.nexus.stream.domain.usecase

import com.nexus.stream.domain.model.StreamMatch
import kotlin.math.abs

object StreamMatching {
    private val SERIES_SE_REGEX = Regex("""\bS(\d{1,2})\s*E(\d{1,3})\b""", RegexOption.IGNORE_CASE)
    private val SERIES_X_REGEX = Regex("""\b(\d{1,2})x(\d{1,3})\b""", RegexOption.IGNORE_CASE)
    private val SERIES_WORD_REGEX =
        Regex("""\bSeason\s*(\d{1,2})\b.*?\bEpisode\s*(\d{1,3})\b""", RegexOption.IGNORE_CASE)

    private val SEASON_ONLY_SE_REGEX = Regex("""\bS(\d{1,2})\b(?!\s*E\d)""", RegexOption.IGNORE_CASE)
    private val SEASON_ONLY_WORD_REGEX =
        Regex("""\bSeason\s*(\d{1,2})\b(?!\s*Episode)""", RegexOption.IGNORE_CASE)

    private val YEAR_REGEX = Regex("""\b(19\d{2}|20\d{2})\b""")

    fun matchOf(
        fileName: String?,
        season: Int?,
        episode: Int?,
        year: Int?,
    ): StreamMatch {
        if (fileName.isNullOrBlank()) return StreamMatch.UNKNOWN

        return when {
            season != null && episode != null -> matchEpisode(fileName, season, episode)
            year != null && season == null && episode == null -> matchMovie(fileName, year)
            else -> StreamMatch.OK
        }
    }

    private fun matchEpisode(
        fileName: String,
        season: Int,
        episode: Int,
    ): StreamMatch {
        val fullMatch = findSeriesMatch(fileName)
        if (fullMatch != null) {
            return evaluateSeriesMatch(fullMatch.first, fullMatch.second, season, episode)
        }
        val seasonOnly = findSeasonOnly(fileName)
        return when {
            seasonOnly != null -> if (seasonOnly == season) StreamMatch.OK else StreamMatch.WRONG_SEASON
            else -> StreamMatch.UNKNOWN
        }
    }

    private fun findSeriesMatch(fileName: String): Pair<Int, Int>? {
        val match =
            SERIES_SE_REGEX.find(fileName)
                ?: SERIES_X_REGEX.find(fileName)
                ?: SERIES_WORD_REGEX.find(fileName)
        val s = match?.groupValues?.get(1)?.toIntOrNull()
        val e = match?.groupValues?.get(2)?.toIntOrNull()
        return if (s != null && e != null) s to e else null
    }

    private fun findSeasonOnly(fileName: String): Int? {
        val match =
            SEASON_ONLY_SE_REGEX.find(fileName)
                ?: SEASON_ONLY_WORD_REGEX.find(fileName)
        return match?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun matchMovie(
        fileName: String,
        year: Int,
    ): StreamMatch {
        val years = YEAR_REGEX.findAll(fileName).mapNotNull { it.value.toIntOrNull() }.toList()
        if (years.isEmpty()) return StreamMatch.UNKNOWN
        return if (years.any { abs(it - year) <= 1 }) StreamMatch.OK else StreamMatch.WRONG_YEAR
    }

    private fun evaluateSeriesMatch(
        parsedSeason: Int,
        parsedEpisode: Int,
        targetSeason: Int,
        targetEpisode: Int,
    ): StreamMatch =
        when {
            parsedSeason != targetSeason -> StreamMatch.WRONG_SEASON
            parsedEpisode != targetEpisode -> StreamMatch.WRONG_EPISODE
            else -> StreamMatch.OK
        }
}
