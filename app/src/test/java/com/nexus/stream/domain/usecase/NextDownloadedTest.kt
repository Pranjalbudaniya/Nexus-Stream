package com.nexus.stream.domain.usecase

import com.nexus.stream.domain.model.DownloadItem
import com.nexus.stream.domain.model.DownloadKind
import com.nexus.stream.domain.model.DownloadSource
import com.nexus.stream.domain.model.DownloadState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NextDownloadedTest {
    private fun createEpisode(
        id: String,
        metaId: String = "tt1",
        season: Int? = 1,
        episode: Int? = 1,
        state: DownloadState = DownloadState.COMPLETED,
    ): DownloadItem =
        DownloadItem(
            id = id,
            kind = DownloadKind.HTTP,
            source = DownloadSource.Http("https://example.com/$id.mp4"),
            metaId = metaId,
            type = "series",
            title = "Episode $episode",
            metaName = "Test Series",
            videoId = "$metaId:$season:$episode",
            season = season,
            episode = episode,
            fileName = "$id.mp4",
            state = state,
            createdAt = 100L,
            updatedAt = 100L,
        )

    @Test
    fun ND1_nextEpisode_inTheSameSeason() {
        val s1e2 = createEpisode(id = "s1e2", season = 1, episode = 2)
        val s1e3 = createEpisode(id = "s1e3", season = 1, episode = 3)

        val next = findNextDownloaded(listOf(s1e2, s1e3), metaId = "tt1", season = 1, episode = 2)
        assertEquals("s1e3", next?.id)
    }

    @Test
    fun ND2_nextSeason_whenTheSeasonEnds() {
        val s1e10 = createEpisode(id = "s1e10", season = 1, episode = 10)
        val s2e1 = createEpisode(id = "s2e1", season = 2, episode = 1)

        val next = findNextDownloaded(listOf(s1e10, s2e1), metaId = "tt1", season = 1, episode = 10)
        assertEquals("s2e1", next?.id)
    }

    @Test
    fun ND3_nextNotDownloaded_isNull() {
        val s1e2 = createEpisode(id = "s1e2", season = 1, episode = 2)

        val next = findNextDownloaded(listOf(s1e2), metaId = "tt1", season = 1, episode = 2)
        assertNull(next)
    }

    @Test
    fun ND4_ignoresOtherSeriesAndUnfinishedItems() {
        val otherSeriesS1E3 = createEpisode(id = "other", metaId = "tt2", season = 1, episode = 3)
        val downloadingS1E3 =
            createEpisode(
                id = "downloading",
                metaId = "tt1",
                season = 1,
                episode = 3,
                state = DownloadState.DOWNLOADING,
            )

        val next = findNextDownloaded(listOf(otherSeriesS1E3, downloadingS1E3), metaId = "tt1", season = 1, episode = 2)
        assertNull(next)
    }

    @Test
    fun ND5_prefersTheSmallestNextEpisode() {
        val s1e3 = createEpisode(id = "s1e3", season = 1, episode = 3)
        val s1e4 = createEpisode(id = "s1e4", season = 1, episode = 4)

        val next = findNextDownloaded(listOf(s1e3, s1e4), metaId = "tt1", season = 1, episode = 2)
        assertEquals("s1e3", next?.id)
    }
}
