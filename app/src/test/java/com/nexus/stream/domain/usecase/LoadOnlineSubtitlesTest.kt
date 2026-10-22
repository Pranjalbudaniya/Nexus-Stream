package com.nexus.stream.domain.usecase

import com.nexus.stream.data.remote.addon.AddonRemoteDataSource
import com.nexus.stream.domain.model.Addon
import com.nexus.stream.domain.model.AddonManifest
import com.nexus.stream.domain.model.AddonSource
import com.nexus.stream.domain.model.AppError
import com.nexus.stream.domain.model.ManifestHints
import com.nexus.stream.domain.model.Outcome
import com.nexus.stream.domain.model.ResourceSpec
import com.nexus.stream.domain.model.Subtitle
import com.nexus.stream.domain.repository.AddonRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoadOnlineSubtitlesTest {
    private val testDispatcher = StandardTestDispatcher()
    private val addonRepository: AddonRepository = mockk()
    private val remoteDataSource: AddonRemoteDataSource = mockk()

    private data class FilterConfig(
        val types: List<String> = listOf("movie", "series"),
        val idPrefixes: List<String>? = listOf("tt"),
    )

    private fun createAddon(
        id: String,
        name: String,
        transportUrl: String,
        hasSubtitles: Boolean = true,
        filter: FilterConfig = FilterConfig(),
    ): Addon =
        Addon(
            transportUrl = transportUrl,
            manifest =
                AddonManifest(
                    id = id,
                    version = "1.0.0",
                    name = name,
                    description = "Test addon",
                    logo = null,
                    background = null,
                    types = filter.types,
                    idPrefixes = filter.idPrefixes,
                    resources =
                        if (hasSubtitles) {
                            listOf(
                                ResourceSpec(name = "subtitles", types = filter.types, idPrefixes = filter.idPrefixes),
                            )
                        } else {
                            listOf(ResourceSpec(name = "stream", types = filter.types, idPrefixes = filter.idPrefixes))
                        },
                    catalogs = emptyList(),
                    behaviorHints =
                        ManifestHints(
                            adult = false,
                            p2p = false,
                            configurable = false,
                            configurationRequired = false,
                        ),
                ),
            enabled = true,
            position = 0,
            source = AddonSource.LOCAL,
            installedAt = 0L,
        )

    @Test
    fun LO1_onlyEnabledMatchingAddonsQueried() =
        runTest(testDispatcher) {
            val matching = createAddon("a1", "OpenSubtitles", "https://opensubtitles.org", hasSubtitles = true)
            val nonMatchingType =
                createAddon(
                    "a2",
                    "AnimeSubs",
                    "https://anime.org",
                    hasSubtitles = true,
                    filter = FilterConfig(types = listOf("anime")),
                )
            val nonMatchingPrefix =
                createAddon(
                    "a3",
                    "KitsuSubs",
                    "https://kitsu.org",
                    hasSubtitles = true,
                    filter = FilterConfig(idPrefixes = listOf("kitsu")),
                )
            val noSubsResource = createAddon("a4", "StreamOnly", "https://streams.org", hasSubtitles = false)

            every { addonRepository.observeEnabled() } returns
                flowOf(listOf(matching, nonMatchingType, nonMatchingPrefix, noSubsResource))

            coEvery {
                remoteDataSource.subtitles("https://opensubtitles.org", "movie", "tt12345", any())
            } returns Outcome.Success(listOf(Subtitle("s1", "https://sub.org/1.srt", "en", "English")))

            val loader = LoadOnlineSubtitles(addonRepository, remoteDataSource)
            val result = loader("movie", "tt12345", null)

            assertEquals(1, result.size)
            assertEquals("OpenSubtitles", result.single().addonName)
            coVerify(exactly = 1) { remoteDataSource.subtitles("https://opensubtitles.org", any(), any(), any()) }
            coVerify(exactly = 0) { remoteDataSource.subtitles("https://anime.org", any(), any(), any()) }
            coVerify(exactly = 0) { remoteDataSource.subtitles("https://kitsu.org", any(), any(), any()) }
            coVerify(exactly = 0) { remoteDataSource.subtitles("https://streams.org", any(), any(), any()) }
        }

    @Test
    fun LO2_extraHasVideoHashSizeFilenameOnlyForNonNullHints() =
        runTest(testDispatcher) {
            val addon = createAddon("a1", "Addon1", "https://addon1.org")
            every { addonRepository.observeEnabled() } returns flowOf(listOf(addon))

            val recordedExtras = mutableListOf<Map<String, String>>()
            coEvery {
                remoteDataSource.subtitles(any(), any(), any(), any())
            } answers {
                recordedExtras.add(arg(3))
                Outcome.Success(emptyList())
            }

            val loader = LoadOnlineSubtitles(addonRepository, remoteDataSource)

            // Hints with only hash and size
            loader("movie", "tt12345", SubtitleHints(filename = null, videoHash = "abcd1234", videoSize = 1000L))
            assertEquals(1, recordedExtras.size)
            val extra1 = recordedExtras[0]
            assertEquals(mapOf("videoHash" to "abcd1234", "videoSize" to "1000"), extra1)

            // Hints with all fields
            loader("movie", "tt12345", SubtitleHints(filename = "movie.mkv", videoHash = "hash1", videoSize = 2000L))
            assertEquals(2, recordedExtras.size)
            val extra2 = recordedExtras[1]
            assertEquals(mapOf("videoHash" to "hash1", "videoSize" to "2000", "filename" to "movie.mkv"), extra2)

            // Null hints
            loader("movie", "tt12345", null)
            assertEquals(3, recordedExtras.size)
            assertTrue(recordedExtras[2].isEmpty())
        }

    @Test
    fun LO3_failingAndTimingOutAddonsSkipped_thirdReturned() =
        runTest(testDispatcher) {
            val a1 = createAddon("a1", "Failing", "https://fail.org")
            val a2 = createAddon("a2", "TimingOut", "https://timeout.org")
            val a3 = createAddon("a3", "Working", "https://work.org")
            every { addonRepository.observeEnabled() } returns flowOf(listOf(a1, a2, a3))

            coEvery {
                remoteDataSource.subtitles("https://fail.org", any(), any(), any())
            } returns Outcome.Failure(AppError.Network)

            coEvery {
                remoteDataSource.subtitles("https://timeout.org", any(), any(), any())
            } coAnswers {
                delay(10_000L)
                Outcome.Success(listOf(Subtitle("s_late", "https://sub.org/late.srt", "en", "Late")))
            }

            coEvery {
                remoteDataSource.subtitles("https://work.org", any(), any(), any())
            } coAnswers {
                delay(100L)
                Outcome.Success(listOf(Subtitle("s_good", "https://sub.org/good.srt", "en", "Good")))
            }

            val loader = LoadOnlineSubtitles(addonRepository, remoteDataSource)

            var result: List<com.nexus.stream.domain.model.OnlineSubtitle>? = null
            val job =
                launch {
                    result = loader("movie", "tt12345", null)
                }

            advanceTimeBy(9_000L)
            runCurrent()
            job.join()

            val subs = result.orEmpty()
            assertEquals(1, subs.size)
            assertEquals("Working", subs.single().addonName)
            assertEquals("https://sub.org/good.srt", subs.single().subtitle.url)
        }

    @Test
    fun LO4_noMatchingAddon_returnsEmptyListWithoutRequest_dedupAndCapAt60() =
        runTest(testDispatcher) {
            // Case 1: no matching addon
            every { addonRepository.observeEnabled() } returns flowOf(emptyList())
            val loader = LoadOnlineSubtitles(addonRepository, remoteDataSource)
            val result1 = loader("movie", "tt12345", null)
            assertTrue(result1.isEmpty())
            coVerify(exactly = 0) { remoteDataSource.subtitles(any(), any(), any(), any()) }

            // Case 2: duplicate URLs across 2 addons + capping at 60
            val a1 = createAddon("a1", "Addon1", "https://a1.org")
            val a2 = createAddon("a2", "Addon2", "https://a2.org")
            every { addonRepository.observeEnabled() } returns flowOf(listOf(a1, a2))

            // Addon 1 produces 50 subtitles
            val subsA1 =
                (1..50).map { i ->
                    Subtitle("a1_$i", "https://sub.org/item_$i.srt", "en", "Item $i")
                }
            // Addon 2 produces 50 subtitles, with items 1..20 having identical URLs to Addon 1
            val subsA2 =
                (1..50).map { i ->
                    Subtitle("a2_$i", "https://sub.org/item_${i + 10}.srt", "en", "Item ${i + 10}")
                }

            coEvery {
                remoteDataSource.subtitles("https://a1.org", any(), any(), any())
            } returns Outcome.Success(subsA1)
            coEvery {
                remoteDataSource.subtitles("https://a2.org", any(), any(), any())
            } returns Outcome.Success(subsA2)

            val result2 = loader("movie", "tt12345", null)

            // Results carry addonName
            assertTrue(result2.all { it.addonName == "Addon1" || it.addonName == "Addon2" })
            // Deduplicated: urls are distinct
            val distinctUrls = result2.map { it.subtitle.url }.distinct()
            assertEquals(result2.size, distinctUrls.size)
            // Capped at 60
            assertEquals(60, result2.size)
        }

    @Test
    fun LO5_cancellationOfCallerCancelsPendingRequestsAndIsNotSwallowed() =
        runTest(testDispatcher) {
            val a1 = createAddon("a1", "Addon1", "https://a1.org")
            every { addonRepository.observeEnabled() } returns flowOf(listOf(a1))

            var requestStarted = false
            var requestCancelled = false

            coEvery {
                remoteDataSource.subtitles("https://a1.org", any(), any(), any())
            } coAnswers {
                requestStarted = true
                try {
                    delay(5_000L)
                    Outcome.Success(emptyList())
                } catch (e: CancellationException) {
                    requestCancelled = true
                    throw e
                }
            }

            val loader = LoadOnlineSubtitles(addonRepository, remoteDataSource)

            var caught: Throwable? = null
            val job =
                launch {
                    try {
                        loader("movie", "tt12345", null)
                    } catch (e: Throwable) {
                        caught = e
                    }
                }

            runCurrent()
            assertTrue(requestStarted)
            job.cancel(CancellationException("Cancelled by caller"))
            job.join()

            assertTrue(requestCancelled)
            assertTrue(caught is CancellationException)
        }
}
