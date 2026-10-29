package com.nexus.stream.presentation.feature.library

import app.cash.turbine.test
import com.nexus.stream.domain.model.EpisodeLabel
import com.nexus.stream.domain.model.HISTORY_ROW_LIMIT
import com.nexus.stream.domain.model.HistoryBucket
import com.nexus.stream.domain.model.HistoryEntry
import com.nexus.stream.domain.model.ListStatus
import com.nexus.stream.domain.model.PosterMark
import com.nexus.stream.domain.model.WatchlistEntry
import com.nexus.stream.domain.model.WatchlistSort
import com.nexus.stream.domain.repository.ContentPrefsRepository
import com.nexus.stream.domain.repository.HistoryRepository
import com.nexus.stream.domain.repository.ProgressRepository
import com.nexus.stream.domain.repository.WatchlistRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val fixedInstant = Instant.parse("2026-10-28T12:00:00Z")
    private val fixedZone = ZoneId.of("UTC")
    private val fixedClock = Clock.fixed(fixedInstant, fixedZone)

    private val fakeWatchlistRepo = FakeWatchlistRepository()
    private val fakeHistoryRepo = FakeHistoryRepository()
    private val contentPrefsRepo = mockk<ContentPrefsRepository>()
    private val progressRepo = mockk<ProgressRepository>()

    private val familyModeFlow = MutableStateFlow(false)
    private val watchedMovieIdsFlow = MutableStateFlow<Set<String>>(emptySet())

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { contentPrefsRepo.familyMode } returns familyModeFlow
        every { progressRepo.observeWatchedMovieIds() } returns watchedMovieIdsFlow
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun createViewModel(): LibraryViewModel =
        LibraryViewModel(
            watchlistRepository = fakeWatchlistRepo,
            historyRepository = fakeHistoryRepo,
            contentPrefsRepository = contentPrefsRepo,
            progressRepository = progressRepo,
            ioDispatcher = testDispatcher,
            clock = fixedClock,
        )

    private fun wlEntry(
        id: String,
        name: String = "Title $id",
        type: String = "movie",
        status: ListStatus = ListStatus.PLAN_TO_WATCH,
    ) = WatchlistEntry(id, type, name, "https://example.com/$id.jpg", null, status, 1000L)

    private fun histEntry(
        id: String,
        name: String? = "Title $id",
        type: String = "movie",
        watched: Boolean = false,
        updatedAt: Long = fixedInstant.toEpochMilli(),
    ) = HistoryEntry(
        metaId = id,
        type = type,
        name = name,
        poster = "https://example.com/$id.jpg",
        season = null,
        episode = null,
        videoId = "v_$id",
        progress = null,
        watched = watched,
        updatedAt = updatedAt,
    )

    private fun seriesHistEntry(
        id: String,
        season: Int,
        episode: Int,
        progress: Float? = null,
    ) = HistoryEntry(
        metaId = id,
        type = "series",
        name = "Title $id",
        poster = "https://example.com/$id.jpg",
        season = season,
        episode = episode,
        videoId = "v_$id",
        progress = progress,
        watched = false,
        updatedAt = fixedInstant.toEpochMilli(),
    )

    @Test
    fun LV1_initial_isLoading_thenWatchlistContent() =
        testScope.runTest {
            val viewModel = createViewModel()
            viewModel.uiState.test {
                assertEquals(LibraryPhase.Loading, awaitItem().watchlist.phase)
                fakeWatchlistRepo.flow.emit(listOf(wlEntry("m1"), wlEntry("m2"), wlEntry("m3")))
                testScheduler.advanceUntilIdle()
                val content = awaitItem()
                assertEquals(LibraryPhase.Content, content.watchlist.phase)
                assertEquals(3, content.watchlist.cards.size)
            }
        }

    @Test
    fun LV2_noEntries_showsEmpty() =
        testScope.runTest {
            val viewModel = createViewModel()
            viewModel.uiState.test {
                awaitItem()
                fakeWatchlistRepo.flow.emit(emptyList())
                testScheduler.advanceUntilIdle()
                val state = awaitItem()
                assertEquals(LibraryPhase.Empty, state.watchlist.phase)
                assertTrue(state.watchlist.cards.isEmpty())
                assertTrue(state.watchlist.statusOptions.isEmpty())
            }
        }

    @Test
    fun LV3_selectStatusWithNoEntries_showsFilteredEmpty_andKeepsTheSelection() =
        testScope.runTest {
            val viewModel = createViewModel()
            viewModel.uiState.test {
                awaitItem()
                fakeWatchlistRepo.flow.emit(listOf(wlEntry("m1", status = ListStatus.PLAN_TO_WATCH)))
                testScheduler.advanceUntilIdle()
                awaitItem()
                viewModel.onEvent(LibraryEvent.SelectStatus(ListStatus.DROPPED))
                testScheduler.advanceUntilIdle()
                val state = awaitItem()
                assertEquals(LibraryPhase.FilteredEmpty, state.watchlist.phase)
                assertEquals(ListStatus.DROPPED, state.watchlist.status)
            }
        }

    @Test
    fun LV4_selectType_filtersTheList() =
        testScope.runTest {
            val viewModel = createViewModel()
            viewModel.uiState.test {
                awaitItem()
                fakeWatchlistRepo.flow.emit(listOf(wlEntry("m1", type = "movie"), wlEntry("s1", type = "series")))
                testScheduler.advanceUntilIdle()
                awaitItem()
                viewModel.onEvent(LibraryEvent.SelectWatchlistType("series"))
                testScheduler.advanceUntilIdle()
                val state = awaitItem()
                assertEquals(1, state.watchlist.cards.size)
                assertEquals(
                    "s1",
                    state.watchlist.cards
                        .first()
                        .metaId,
                )
            }
        }

    @Test
    fun LV5_selectSort_reordersTheList() =
        testScope.runTest {
            val viewModel = createViewModel()
            viewModel.uiState.test {
                awaitItem()
                fakeWatchlistRepo.flow.emit(
                    listOf(
                        wlEntry("m1", name = "Zebra"),
                        wlEntry("m2", name = "Apple"),
                        wlEntry("m3", name = "Banana"),
                    ),
                )
                testScheduler.advanceUntilIdle()
                awaitItem()
                viewModel.onEvent(LibraryEvent.SelectSort(WatchlistSort.TITLE_AZ))
                testScheduler.advanceUntilIdle()
                assertEquals(listOf("Apple", "Banana", "Zebra"), awaitItem().watchlist.cards.map { it.name })
            }
        }

    @Test
    fun LV6_clearWatchlistFilters_resetsStatusAndType_notSort() =
        testScope.runTest {
            val viewModel = createViewModel()
            viewModel.uiState.test {
                awaitItem()
                fakeWatchlistRepo.flow.emit(listOf(wlEntry("m1", status = ListStatus.WATCHING, type = "movie")))
                testScheduler.advanceUntilIdle()
                awaitItem()
                viewModel.onEvent(LibraryEvent.SelectStatus(ListStatus.WATCHING))
                viewModel.onEvent(LibraryEvent.SelectWatchlistType("movie"))
                viewModel.onEvent(LibraryEvent.SelectSort(WatchlistSort.TITLE_AZ))
                testScheduler.advanceUntilIdle()
                cancelAndIgnoreRemainingEvents()
            }
            viewModel.onEvent(LibraryEvent.ClearWatchlistFilters)
            testScheduler.advanceUntilIdle()
            val state = viewModel.uiState.value.watchlist
            assertNull(state.status)
            assertNull(state.type)
            assertEquals(WatchlistSort.TITLE_AZ, state.sort)
        }

    @Test
    fun LV7_statusOptions_carryFacetedCounts() =
        testScope.runTest {
            val viewModel = createViewModel()
            viewModel.uiState.test {
                awaitItem()
                fakeWatchlistRepo.flow.emit(
                    listOf(
                        wlEntry("m1", type = "movie", status = ListStatus.WATCHING),
                        wlEntry("m2", type = "movie", status = ListStatus.WATCHING),
                        wlEntry("m3", type = "movie", status = ListStatus.PLAN_TO_WATCH),
                        wlEntry("m4", type = "movie", status = ListStatus.PLAN_TO_WATCH),
                        wlEntry("s1", type = "series", status = ListStatus.WATCHING),
                        wlEntry("s2", type = "series", status = ListStatus.PLAN_TO_WATCH),
                    ),
                )
                testScheduler.advanceUntilIdle()
                awaitItem()
                viewModel.onEvent(LibraryEvent.SelectWatchlistType("series"))
                testScheduler.advanceUntilIdle()
                val state = awaitItem()
                val watchingOption = state.watchlist.statusOptions.firstOrNull { it.value == ListStatus.WATCHING }
                assertNotNull(watchingOption)
                assertEquals(1, watchingOption?.count)
                assertEquals(
                    5,
                    state.watchlist.statusOptions
                        .mapNotNull { it.value }
                        .size,
                )
            }
        }

    @Test
    fun LV8_selectedTypeDisappears_fallsBackToAll() =
        testScope.runTest {
            val viewModel = createViewModel()
            viewModel.uiState.test {
                awaitItem()
                fakeWatchlistRepo.flow.emit(listOf(wlEntry("m1", type = "movie"), wlEntry("s1", type = "series")))
                testScheduler.advanceUntilIdle()
                awaitItem()
                viewModel.onEvent(LibraryEvent.SelectWatchlistType("series"))
                testScheduler.advanceUntilIdle()
                awaitItem()
                fakeWatchlistRepo.flow.emit(listOf(wlEntry("m1", type = "movie")))
                testScheduler.advanceUntilIdle()
                val state = awaitItem()
                assertNull(state.watchlist.type)
                assertEquals(LibraryPhase.Content, state.watchlist.phase)
            }
        }

    @Test
    fun LV9_familyMode_hidesAdultTitles_countsFollow_noRefetch() =
        testScope.runTest {
            val viewModel = createViewModel()
            viewModel.uiState.test {
                awaitItem()
                fakeWatchlistRepo.flow.emit(
                    listOf(wlEntry("m1", name = "Dune"), wlEntry("m2", name = "Hot XXX Video")),
                )
                testScheduler.advanceUntilIdle()
                assertEquals(2, awaitItem().watchlist.cards.size)
                familyModeFlow.value = true
                testScheduler.advanceUntilIdle()
                val s2 = awaitItem()
                assertEquals(1, s2.watchlist.cards.size)
                assertEquals(
                    "m1",
                    s2.watchlist.cards
                        .first()
                        .metaId,
                )
                familyModeFlow.value = false
                testScheduler.advanceUntilIdle()
                assertEquals(2, awaitItem().watchlist.cards.size)
                assertEquals(1, fakeWatchlistRepo.collectCount)
            }
        }

    @Test
    fun LV10_posterMarks_followStatusesAndWatchedMovies() =
        testScope.runTest {
            val viewModel = createViewModel()
            viewModel.uiState.test {
                awaitItem()
                fakeWatchlistRepo.flow.emit(
                    listOf(
                        wlEntry("a", status = ListStatus.WATCHING),
                        wlEntry("b", status = ListStatus.COMPLETED),
                        wlEntry("c", status = ListStatus.PLAN_TO_WATCH),
                    ),
                )
                watchedMovieIdsFlow.value = setOf("c")
                testScheduler.advanceUntilIdle()
                val state = awaitItem()
                assertEquals(PosterMark.WATCHING, state.watchlist.posterMarks["a"])
                assertEquals(PosterMark.WATCHED, state.watchlist.posterMarks["b"])
                assertEquals(PosterMark.WATCHED, state.watchlist.posterMarks["c"])
            }
        }

    @Test
    fun LV11_history_isGroupedByBucket_newestFirst() =
        testScope.runTest {
            val viewModel = createViewModel()
            val millisInDay = 86_400_000L
            val now = fixedInstant.toEpochMilli()
            viewModel.uiState.test {
                awaitItem()
                fakeHistoryRepo.flow.emit(
                    listOf(
                        histEntry("h1", updatedAt = now),
                        histEntry("h2", updatedAt = now - millisInDay),
                        histEntry("h3", updatedAt = now - 40 * millisInDay),
                    ),
                )
                viewModel.onEvent(LibraryEvent.SelectTab(LibraryTab.HISTORY))
                testScheduler.advanceUntilIdle()
                val buckets = expectMostRecentItem().history.sections.map { it.bucket }
                assertEquals(listOf(HistoryBucket.TODAY, HistoryBucket.YESTERDAY, HistoryBucket.EARLIER), buckets)
            }
        }

    @Test
    fun LV12_noHistory_showsEmpty() =
        testScope.runTest {
            val viewModel = createViewModel()
            viewModel.uiState.test {
                awaitItem()
                fakeHistoryRepo.flow.emit(emptyList())
                viewModel.onEvent(LibraryEvent.SelectTab(LibraryTab.HISTORY))
                testScheduler.advanceUntilIdle()
                val state = expectMostRecentItem()
                assertEquals(LibraryPhase.Empty, state.history.phase)
                assertTrue(state.history.sections.isEmpty())
            }
        }

    @Test
    fun LV13_historyTypeFilter_filtersAndFacets() =
        testScope.runTest {
            val viewModel = createViewModel()
            viewModel.uiState.test {
                awaitItem()
                fakeHistoryRepo.flow.emit(listOf(histEntry("h1", type = "movie"), histEntry("h2", type = "series")))
                testScheduler.advanceUntilIdle()
                awaitItem()
                viewModel.onEvent(LibraryEvent.SelectHistoryType("movie"))
                testScheduler.advanceUntilIdle()
                val state = expectMostRecentItem()
                assertEquals("movie", state.history.type)
                assertEquals(
                    1,
                    state.history.sections
                        .flatMap { it.items }
                        .size,
                )
                assertEquals(
                    1,
                    state.history.typeOptions
                        .firstOrNull { it.value == "movie" }
                        ?.count,
                )
            }
        }

    @Test
    fun LV14_removeFromHistory_requestsConfirm_thenDeletes_andEmitsRemoved() =
        testScope.runTest {
            val viewModel = createViewModel()
            viewModel.uiState.test {
                awaitItem()
                fakeHistoryRepo.flow.emit(listOf(histEntry("tt1")))
                testScheduler.advanceUntilIdle()
                awaitItem()
                viewModel.effects.test {
                    viewModel.onEvent(LibraryEvent.RequestRemoveFromHistory("tt1"))
                    testScheduler.advanceUntilIdle()
                    assertEquals(
                        "tt1",
                        viewModel.uiState.value.pendingRemove
                            ?.metaId,
                    )
                    viewModel.onEvent(LibraryEvent.ConfirmRemoveFromHistory)
                    testScheduler.advanceUntilIdle()
                    assertEquals(LibraryEffect.HistoryRemoved, awaitItem())
                    assertNull(viewModel.uiState.value.pendingRemove)
                    assertEquals("tt1", fakeHistoryRepo.deletedMetaId)
                }
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun LV15_dismissRemove_deletesNothing() =
        testScope.runTest {
            val viewModel = createViewModel()
            viewModel.uiState.test {
                awaitItem()
                fakeHistoryRepo.flow.emit(listOf(histEntry("tt1")))
                testScheduler.advanceUntilIdle()
                awaitItem()
                viewModel.onEvent(LibraryEvent.RequestRemoveFromHistory("tt1"))
                testScheduler.advanceUntilIdle()
                assertNotNull(viewModel.uiState.value.pendingRemove)
                viewModel.onEvent(LibraryEvent.DismissRemove)
                testScheduler.advanceUntilIdle()
                assertNull(viewModel.uiState.value.pendingRemove)
                assertNull(fakeHistoryRepo.deletedMetaId)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun LV16_clearHistory_requestsConfirm_thenDeletes_andEmitsCleared() =
        testScope.runTest {
            val viewModel = createViewModel()
            viewModel.uiState.test {
                awaitItem()
                fakeHistoryRepo.flow.emit(listOf(histEntry("tt1")))
                testScheduler.advanceUntilIdle()
                awaitItem()
                viewModel.effects.test {
                    viewModel.onEvent(LibraryEvent.RequestClear)
                    testScheduler.advanceUntilIdle()
                    assertTrue(viewModel.uiState.value.pendingClear)
                    viewModel.onEvent(LibraryEvent.ConfirmClear)
                    testScheduler.advanceUntilIdle()
                    assertEquals(LibraryEffect.HistoryCleared, awaitItem())
                    assertFalse(viewModel.uiState.value.pendingClear)
                    assertEquals(1, fakeHistoryRepo.clearCount)
                }
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun LV17_selectTab_keepsEachTabsFilters() =
        testScope.runTest {
            val viewModel = createViewModel()
            viewModel.uiState.test {
                awaitItem()
                fakeWatchlistRepo.flow.emit(listOf(wlEntry("m1", type = "movie"), wlEntry("s1", type = "series")))
                fakeHistoryRepo.flow.emit(listOf(histEntry("h1", type = "movie"), histEntry("h2", type = "series")))
                testScheduler.advanceUntilIdle()
                awaitItem()
                viewModel.onEvent(LibraryEvent.SelectWatchlistType("series"))
                viewModel.onEvent(LibraryEvent.SelectHistoryType("movie"))
                testScheduler.advanceUntilIdle()
                awaitItem()
                viewModel.onEvent(LibraryEvent.SelectTab(LibraryTab.HISTORY))
                testScheduler.advanceUntilIdle()
                assertEquals("series", awaitItem().watchlist.type)
                viewModel.onEvent(LibraryEvent.SelectTab(LibraryTab.WATCHLIST))
                testScheduler.advanceUntilIdle()
                val sWatchlist = expectMostRecentItem()
                assertEquals("series", sWatchlist.watchlist.type)
                assertEquals("movie", sWatchlist.history.type)
            }
        }

    @Test
    fun LV18_flowError_showsError_otherTabStillWorks_retryRecovers() =
        testScope.runTest {
            fakeHistoryRepo.shouldThrow = true
            val viewModel = createViewModel()
            viewModel.uiState.test {
                awaitItem()
                fakeWatchlistRepo.flow.emit(listOf(wlEntry("m1")))
                testScheduler.advanceUntilIdle()
                val errorState = expectMostRecentItem()
                assertEquals(LibraryPhase.Content, errorState.watchlist.phase)
                assertTrue(errorState.history.phase is LibraryPhase.Error)

                fakeHistoryRepo.shouldThrow = false
                fakeHistoryRepo.flow.emit(listOf(histEntry("h1")))
                viewModel.onEvent(LibraryEvent.Retry)
                testScheduler.advanceUntilIdle()
                val recoveredState = expectMostRecentItem()
                assertEquals(LibraryPhase.Content, recoveredState.watchlist.phase)
                assertEquals(LibraryPhase.Content, recoveredState.history.phase)
            }
        }

    @Test
    fun LV19_historyAtTheLimit_setsTruncated() =
        testScope.runTest {
            val viewModel = createViewModel()
            val limitEntries = (1..HISTORY_ROW_LIMIT).map { histEntry("h$it") }
            val fewerEntries = (1 until HISTORY_ROW_LIMIT).map { histEntry("h$it") }
            viewModel.uiState.test {
                awaitItem()
                fakeHistoryRepo.flow.emit(limitEntries)
                testScheduler.advanceUntilIdle()
                assertTrue(expectMostRecentItem().history.truncated)
                fakeHistoryRepo.flow.emit(fewerEntries)
                testScheduler.advanceUntilIdle()
                assertFalse(expectMostRecentItem().history.truncated)
            }
        }

    @Test
    fun LV20_statusFilterAll_showsStatusSubtitle_specificStatusHidesIt() =
        testScope.runTest {
            val viewModel = createViewModel()
            viewModel.uiState.test {
                awaitItem()
                fakeWatchlistRepo.flow.emit(
                    listOf(
                        wlEntry("m1", status = ListStatus.WATCHING),
                        wlEntry("m2", status = ListStatus.COMPLETED),
                    ),
                )
                testScheduler.advanceUntilIdle()
                assertTrue(awaitItem().watchlist.cards.all { it.showStatusLabel })
                viewModel.onEvent(LibraryEvent.SelectStatus(ListStatus.WATCHING))
                testScheduler.advanceUntilIdle()
                assertFalse(awaitItem().watchlist.cards.any { it.showStatusLabel })
            }
        }

    @Test
    fun LV21_historyEntryStates_progressWatchedAndEpisodeLabel() =
        testScope.runTest {
            val viewModel = createViewModel()
            viewModel.uiState.test {
                awaitItem()
                fakeHistoryRepo.flow.emit(
                    listOf(
                        seriesHistEntry("s1", season = 2, episode = 5, progress = 0.5f),
                        histEntry("m1", type = "movie", watched = true),
                    ),
                )
                testScheduler.advanceUntilIdle()
                val cards = expectMostRecentItem().history.sections.flatMap { it.items }
                val card1 = cards.first { it.metaId == "s1" }
                val card2 = cards.first { it.metaId == "m1" }
                assertEquals(0.5f, card1.progress ?: 0f, 0.001f)
                assertEquals(EpisodeLabel.SeasonEpisode(2, 5), card1.episodeLabel)
                assertFalse(card1.watched)
                assertTrue(card2.watched)
                assertNull(card2.progress)
                assertNull(card2.episodeLabel)
            }
        }

    @Test
    fun LV22_unknownName_isFlagged() =
        testScope.runTest {
            val viewModel = createViewModel()
            viewModel.uiState.test {
                awaitItem()
                fakeWatchlistRepo.flow.emit(listOf(wlEntry("w1", name = "   ")))
                fakeHistoryRepo.flow.emit(listOf(histEntry("h1", name = null)))
                testScheduler.advanceUntilIdle()
                val state = expectMostRecentItem()
                assertTrue(
                    state.watchlist.cards
                        .first()
                        .isUnknownTitle,
                )
                assertTrue(
                    state.history.sections
                        .flatMap { it.items }
                        .first()
                        .isUnknownTitle,
                )
            }
        }

    @Test
    fun LV23_historyLoadingDoesNotBlockWatchlist() =
        testScope.runTest {
            val viewModel = createViewModel()
            viewModel.uiState.test {
                val initial = awaitItem()
                assertEquals(LibraryPhase.Loading, initial.watchlist.phase)
                assertEquals(LibraryPhase.Loading, initial.history.phase)
                fakeWatchlistRepo.flow.emit(listOf(wlEntry("m1")))
                testScheduler.advanceUntilIdle()
                val state = awaitItem()
                assertEquals(LibraryPhase.Content, state.watchlist.phase)
                assertEquals(LibraryPhase.Loading, state.history.phase)
            }
        }

    @Test
    fun LV24_confirmRemoveWithoutPending_isIgnored() =
        testScope.runTest {
            val viewModel = createViewModel()
            viewModel.uiState.test {
                awaitItem()
                viewModel.onEvent(LibraryEvent.ConfirmRemoveFromHistory)
                testScheduler.advanceUntilIdle()
                assertNull(fakeHistoryRepo.deletedMetaId)
                assertNull(viewModel.uiState.value.pendingRemove)
                cancelAndIgnoreRemainingEvents()
            }
        }
}

private class FakeWatchlistRepository : WatchlistRepository {
    val flow = MutableSharedFlow<List<WatchlistEntry>>(replay = 1)
    var collectCount = 0

    override fun observeWatchlist(): Flow<List<WatchlistEntry>> {
        collectCount++
        return flow
    }
}

private class FakeHistoryRepository : HistoryRepository {
    val flow = MutableSharedFlow<List<HistoryEntry>>(replay = 1)
    var shouldThrow = false
    var deletedMetaId: String? = null
    var clearCount = 0

    override fun observeHistory(limit: Int): Flow<List<HistoryEntry>> =
        if (shouldThrow) {
            flow { throw java.io.IOException("network fail") }
        } else {
            flow
        }

    override suspend fun remove(metaId: String) {
        deletedMetaId = metaId
    }

    override suspend fun clear() {
        clearCount++
    }
}
