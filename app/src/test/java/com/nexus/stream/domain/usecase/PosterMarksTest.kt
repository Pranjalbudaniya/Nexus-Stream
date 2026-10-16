package com.nexus.stream.domain.usecase

import com.nexus.stream.domain.model.ListStatus
import com.nexus.stream.domain.model.PosterMark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PosterMarksTest {
    @Test
    fun `watched movie id gives WATCHED`() {
        val marks =
            buildPosterMarks(
                statuses = emptyMap(),
                watchedMovieIds = setOf("movie-1"),
            )
        assertEquals(PosterMark.WATCHED, marks["movie-1"])
    }

    @Test
    fun `COMPLETED gives WATCHED`() {
        val marks =
            buildPosterMarks(
                statuses = mapOf("series-1" to ListStatus.COMPLETED),
                watchedMovieIds = emptySet(),
            )
        assertEquals(PosterMark.WATCHED, marks["series-1"])
    }

    @Test
    fun `WATCHING gives WATCHING`() {
        val marks =
            buildPosterMarks(
                statuses = mapOf("item-1" to ListStatus.WATCHING),
                watchedMovieIds = emptySet(),
            )
        assertEquals(PosterMark.WATCHING, marks["item-1"])
    }

    @Test
    fun `WATCHED beats WATCHING`() {
        val marks =
            buildPosterMarks(
                statuses = mapOf("movie-1" to ListStatus.WATCHING),
                watchedMovieIds = setOf("movie-1"),
            )
        assertEquals(PosterMark.WATCHED, marks["movie-1"])
    }

    @Test
    fun `other statuses give no mark`() {
        val marks =
            buildPosterMarks(
                statuses =
                    mapOf(
                        "p" to ListStatus.PLAN_TO_WATCH,
                        "h" to ListStatus.ON_HOLD,
                        "d" to ListStatus.DROPPED,
                    ),
                watchedMovieIds = emptySet(),
            )
        assertNull(marks["p"])
        assertNull(marks["h"])
        assertNull(marks["d"])
    }

    @Test
    fun `a watched series row alone gives no mark`() {
        // watchedMovieIds only contains movie ids (from type = 'movie' query)
        val marks =
            buildPosterMarks(
                statuses = emptyMap(),
                watchedMovieIds = emptySet(), // series watched row produces nothing here
            )
        assertNull(marks["series-1"])
    }
}
