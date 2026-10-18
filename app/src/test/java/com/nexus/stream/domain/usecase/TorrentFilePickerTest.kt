package com.nexus.stream.domain.usecase

import com.nexus.stream.domain.model.EpisodeHint
import com.nexus.stream.domain.model.TorrentFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TorrentFilePickerTest {
    @Test
    fun F1_noHint_selectsLargestVideo() {
        val files =
            listOf(
                TorrentFile(index = 0, path = "a.mkv", sizeBytes = 700L * 1024 * 1024),
                TorrentFile(index = 1, path = "b.mkv", sizeBytes = 1400L * 1024 * 1024),
                TorrentFile(index = 2, path = "c.srt", sizeBytes = 1L * 1024 * 1024),
            )
        assertEquals(1, pickFile(files, hint = null))
    }

    @Test
    fun F2_noHint_equalSizeVideos_selectsLowestIndex() {
        val files =
            listOf(
                TorrentFile(index = 0, path = "a.mkv", sizeBytes = 1000L),
                TorrentFile(index = 1, path = "b.mkv", sizeBytes = 1000L),
            )
        assertEquals(0, pickFile(files, hint = null))
    }

    @Test
    fun F3_hintStandardS01E02Format_matchesCorrectFile() {
        val files =
            listOf(
                TorrentFile(index = 0, path = "Show.S01E02.1080p.mkv", sizeBytes = 1000L),
                TorrentFile(index = 1, path = "Show.S01E03.1080p.mkv", sizeBytes = 1000L),
            )
        assertEquals(0, pickFile(files, hint = EpisodeHint(season = 1, episode = 2)))
    }

    @Test
    fun F4_hintXFormat_matchesCorrectFile() {
        val files =
            listOf(
                TorrentFile(index = 0, path = "Show 1x02.mkv", sizeBytes = 1000L),
                TorrentFile(index = 1, path = "Show 1x03.mkv", sizeBytes = 1000L),
            )
        assertEquals(0, pickFile(files, hint = EpisodeHint(season = 1, episode = 2)))
    }

    @Test
    fun F5_hintAnimeDashFormat_matchesCorrectFile() {
        val files =
            listOf(
                TorrentFile(index = 0, path = "[Grp] Show - 02 [1080p].mkv", sizeBytes = 1000L),
                TorrentFile(index = 1, path = "[Grp] Show - 03 [1080p].mkv", sizeBytes = 1000L),
            )
        assertEquals(0, pickFile(files, hint = EpisodeHint(season = 1, episode = 2)))
    }

    @Test
    fun F6_packWithoutMatch_returnsNull() {
        val files =
            listOf(
                TorrentFile(index = 0, path = "Show.S01E01.mkv", sizeBytes = 1000L),
                TorrentFile(index = 1, path = "Show.S01E03.mkv", sizeBytes = 1000L),
                TorrentFile(index = 2, path = "Show.S01E04.mkv", sizeBytes = 1000L),
            )
        assertNull(pickFile(files, hint = EpisodeHint(season = 1, episode = 2)))
    }

    @Test
    fun F7_subtitleFileMatchIgnored_returnsNull() {
        val files =
            listOf(
                TorrentFile(index = 0, path = "Show.S01E02.srt", sizeBytes = 10L),
                TorrentFile(index = 1, path = "Show.S01E01.mkv", sizeBytes = 1000L),
                TorrentFile(index = 2, path = "Show.S01E03.mkv", sizeBytes = 1000L),
            )
        assertNull(pickFile(files, hint = EpisodeHint(season = 1, episode = 2)))
    }

    @Test
    fun F8_digitBoundaryEnforced_matchesOnlyExactEpisode() {
        val files =
            listOf(
                TorrentFile(index = 0, path = "Show.S01E12.mkv", sizeBytes = 1000L),
                TorrentFile(index = 1, path = "Show.S01E20.mkv", sizeBytes = 1000L),
                TorrentFile(index = 2, path = "Show.S01E02.mkv", sizeBytes = 1000L),
            )
        assertEquals(2, pickFile(files, hint = EpisodeHint(season = 1, episode = 2)))
    }

    @Test
    fun F9_nonZeroPaddedAndDottedFormat_matchesCorrectFile() {
        val files1 =
            listOf(
                TorrentFile(index = 0, path = "Show.S1E2.mkv", sizeBytes = 1000L),
                TorrentFile(index = 1, path = "Show.S1E3.mkv", sizeBytes = 1000L),
            )
        assertEquals(0, pickFile(files1, hint = EpisodeHint(season = 1, episode = 2)))

        val files2 =
            listOf(
                TorrentFile(index = 0, path = "show.s01.e02.mkv", sizeBytes = 1000L),
                TorrentFile(index = 1, path = "show.s01.e03.mkv", sizeBytes = 1000L),
            )
        assertEquals(0, pickFile(files2, hint = EpisodeHint(season = 1, episode = 2)))
    }

    @Test
    fun F10_samplesExcludedWhenRealVideosExist() {
        val files1 =
            listOf(
                TorrentFile(index = 0, path = "Show.S01E02.sample.mkv", sizeBytes = 30L * 1024 * 1024),
                TorrentFile(index = 1, path = "Show.S01E02.mkv", sizeBytes = 800L * 1024 * 1024),
            )
        assertEquals(1, pickFile(files1, hint = EpisodeHint(season = 1, episode = 2)))

        val files2 =
            listOf(
                TorrentFile(index = 0, path = "Sample/show.s01e02.mkv", sizeBytes = 800L * 1024 * 1024),
                TorrentFile(index = 1, path = "Season 1/show.s01e02.mkv", sizeBytes = 800L * 1024 * 1024),
            )
        assertEquals(1, pickFile(files2, hint = EpisodeHint(season = 1, episode = 2)))
    }

    @Test
    fun F11_onlySampleVideo_fallsBackToPool() {
        val files =
            listOf(
                TorrentFile(index = 0, path = "movie.sample.mkv", sizeBytes = 1000L),
            )
        assertEquals(0, pickFile(files, hint = null))
    }

    @Test
    fun F12_singleVideoPack_selectedEvenIfHintDoesNotMatch() {
        val files =
            listOf(
                TorrentFile(index = 0, path = "Show Complete 1080p.mkv", sizeBytes = 5000L),
                TorrentFile(index = 1, path = "info.nfo", sizeBytes = 100L),
            )
        assertEquals(0, pickFile(files, hint = EpisodeHint(season = 1, episode = 2)))
    }

    @Test
    fun F13_noVideoFiles_returnsNull() {
        val files =
            listOf(
                TorrentFile(index = 0, path = "info.nfo", sizeBytes = 100L),
                TorrentFile(index = 1, path = "readme.txt", sizeBytes = 100L),
            )
        assertNull(pickFile(files, hint = null))
    }

    @Test
    fun F14_nonVideoExtensionsIgnored() {
        val files =
            listOf(
                TorrentFile(index = 0, path = "MOVIE.MKV", sizeBytes = 1000L),
                TorrentFile(index = 1, path = "movie.mkv.part", sizeBytes = 2000L),
                TorrentFile(index = 2, path = "movie.mkv.txt", sizeBytes = 3000L),
            )
        assertEquals(0, pickFile(files, hint = null))
    }

    @Test
    fun F15_seasonMustMatchCorrectly() {
        val files =
            listOf(
                TorrentFile(index = 0, path = "Show.S02E05.mkv", sizeBytes = 1000L),
                TorrentFile(index = 1, path = "Show.S01E05.mkv", sizeBytes = 2000L),
            )
        assertEquals(0, pickFile(files, hint = EpisodeHint(season = 2, episode = 5)))
    }
}
