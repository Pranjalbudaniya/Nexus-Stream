package com.nexus.stream.presentation.feature.discover

import com.nexus.stream.data.remote.addon.DiscoverPageLoader
import com.nexus.stream.domain.model.Addon
import com.nexus.stream.domain.model.AddonManifest
import com.nexus.stream.domain.model.AddonSource
import com.nexus.stream.domain.model.AppError
import com.nexus.stream.domain.model.CatalogSpec
import com.nexus.stream.domain.model.ExtraSpec
import com.nexus.stream.domain.model.ListStatus
import com.nexus.stream.domain.model.ManifestHints
import com.nexus.stream.domain.model.MetaPreview
import com.nexus.stream.domain.model.Outcome
import com.nexus.stream.domain.model.PosterMark
import com.nexus.stream.domain.model.PosterShape
import com.nexus.stream.domain.repository.AddonRepository
import com.nexus.stream.domain.repository.ContentPrefsRepository
import com.nexus.stream.domain.repository.LibraryRepository
import com.nexus.stream.domain.repository.ProgressRepository
import com.nexus.stream.domain.repository.SearchHistoryRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val addonRepository: AddonRepository = mockk()
    private val contentPrefsRepository: ContentPrefsRepository = mockk()
    private val libraryRepository: LibraryRepository = mockk(relaxed = true)
    private val progressRepository: ProgressRepository = mockk(relaxed = true)
    private val searchHistoryRepository: SearchHistoryRepository = mockk(relaxed = true)
    private val loader: DiscoverPageLoader = mockk(relaxed = true)

    private val allAddonsFlow = MutableStateFlow<List<Addon>>(emptyList())
    private val activeAddonsFlow = MutableStateFlow<List<Addon>>(emptyList())
    private val familyModeFlow = MutableStateFlow(false)
    private val listStatusesFlow = MutableStateFlow<Map<String, ListStatus>>(emptyMap())
    private val watchedMovieIdsFlow = MutableStateFlow<Set<String>>(emptySet())
    private val historyFlow = MutableStateFlow<List<String>>(emptyList())

    private val searchMovieCatalog =
        CatalogSpec(
            type = "movie",
            id = "top",
            name = "Popular",
            extras =
                listOf(
                    ExtraSpec("search", isRequired = false, options = emptyList()),
                ),
        )

    private val searchSeriesCatalog =
        CatalogSpec(
            type = "series",
            id = "top",
            name = "Popular Series",
            extras =
                listOf(
                    ExtraSpec("search", isRequired = false, options = emptyList()),
                ),
        )

    private val standardAddon =
        createAddon(
            transportUrl = "https://addon.com/manifest.json",
            name = "Addon",
            catalogs = listOf(searchMovieCatalog),
        )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { addonRepository.observeAll() } returns allAddonsFlow
        every { addonRepository.observeActive() } returns activeAddonsFlow
        every { contentPrefsRepository.familyMode } returns familyModeFlow
        every { libraryRepository.observeListStatuses() } returns listStatusesFlow
        every { progressRepository.observeWatchedMovieIds() } returns watchedMovieIdsFlow
        every { searchHistoryRepository.observeHistory() } returns historyFlow

        allAddonsFlow.value = listOf(standardAddon)
        activeAddonsFlow.value = listOf(standardAddon)
        coEvery { loader.load(any(), any()) } returns Outcome.Success(listOf(createMeta("1")))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): SearchViewModel =
        SearchViewModel(
            addonRepository = addonRepository,
            contentPrefsRepository = contentPrefsRepository,
            libraryRepository = libraryRepository,
            progressRepository = progressRepository,
            searchHistoryRepository = searchHistoryRepository,
            loader = loader,
            dispatcher = testDispatcher,
        )

    @Test
    fun SV1_initial_isIdle_noRequests() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            advanceUntilIdle()

            assertEquals(SearchPhase.Idle, vm.uiState.value.phase)
            coVerify(exactly = 0) { loader.load(any(), any()) }
        }

    @Test
    fun SV2_oneChar_isTooShort_noRequests() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            advanceUntilIdle()

            vm.onEvent(SearchEvent.QueryChanged("a"))
            advanceTimeBy(1000)
            advanceUntilIdle()

            assertEquals(SearchPhase.TooShort, vm.uiState.value.phase)
            coVerify(exactly = 0) { loader.load(any(), any()) }
        }

    @Test
    fun SV3_twoChars_searchesOnlyAfterTheDebounce() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            advanceUntilIdle()

            vm.onEvent(SearchEvent.QueryChanged("du"))
            advanceTimeBy(399)
            coVerify(exactly = 0) { loader.load(any(), any()) }

            advanceTimeBy(1)
            advanceUntilIdle()
            coVerify(exactly = 1) { loader.load(any(), match { it["search"] == "du" }) }
        }

    @Test
    fun SV4_fastTyping_searchesOnlyTheLastQuery() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            advanceUntilIdle()

            vm.onEvent(SearchEvent.QueryChanged("du"))
            advanceTimeBy(100)
            vm.onEvent(SearchEvent.QueryChanged("dun"))
            advanceTimeBy(100)
            vm.onEvent(SearchEvent.QueryChanged("dune"))
            advanceTimeBy(400)
            advanceUntilIdle()

            coVerify(exactly = 1) { loader.load(any(), match { it["search"] == "dune" }) }
            coVerify(exactly = 0) { loader.load(any(), match { it["search"] == "du" || it["search"] == "dun" }) }
        }

    @Test
    fun SV5_sameNormalizedQuery_doesNotSearchAgain() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            advanceUntilIdle()

            vm.onEvent(SearchEvent.QueryChanged("dune"))
            advanceTimeBy(400)
            advanceUntilIdle()
            coVerify(exactly = 1) { loader.load(any(), match { it["search"] == "dune" }) }

            vm.onEvent(SearchEvent.QueryChanged("dune "))
            advanceTimeBy(500)
            advanceUntilIdle()

            vm.onEvent(SearchEvent.QueryChanged("dune"))
            advanceTimeBy(500)
            advanceUntilIdle()

            coVerify(exactly = 1) { loader.load(any(), any()) }
        }

    @Test
    fun SV6_submit_searchesImmediately_andSavesHistory() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            advanceUntilIdle()

            vm.onEvent(SearchEvent.QueryChanged("dune"))
            vm.onEvent(SearchEvent.Submit)
            advanceUntilIdle()

            coVerify(exactly = 1) { loader.load(any(), match { it["search"] == "dune" }) }
            coVerify(exactly = 1) { searchHistoryRepository.save("dune") }
        }

    @Test
    fun SV7_queryChange_cancelsInFlight_staleRowsAreIgnored() =
        runTest(testDispatcher) {
            val duneDeferred = CompletableDeferred<Outcome<List<MetaPreview>>>()
            val alienDeferred = CompletableDeferred<Outcome<List<MetaPreview>>>()

            coEvery { loader.load(any(), match { it["search"] == "dune" }) } coAnswers {
                duneDeferred.await()
            }
            coEvery { loader.load(any(), match { it["search"] == "alien" }) } coAnswers {
                alienDeferred.await()
            }

            val vm = createViewModel()
            advanceUntilIdle()

            vm.onEvent(SearchEvent.QueryChanged("dune"))
            advanceTimeBy(400)

            vm.onEvent(SearchEvent.QueryChanged("alien"))
            advanceTimeBy(400)

            alienDeferred.complete(Outcome.Success(listOf(createMeta("alien_1"))))
            advanceUntilIdle()

            duneDeferred.complete(Outcome.Success(listOf(createMeta("dune_1"))))
            advanceUntilIdle()

            val rows = vm.uiState.value.rows
            assertEquals(1, rows.size)
            assertEquals(
                "alien_1",
                rows
                    .first()
                    .items
                    .first()
                    .id,
            )
        }

    @Test
    fun SV8_rowsArriveProgressively_inCatalogOrder() =
        runTest(testDispatcher) {
            val twoCatalogAddon =
                createAddon(
                    transportUrl = "https://addon.com/manifest.json",
                    name = "Addon",
                    catalogs = listOf(searchMovieCatalog, searchSeriesCatalog),
                )
            activeAddonsFlow.value = listOf(twoCatalogAddon)

            val cat1Deferred = CompletableDeferred<Outcome<List<MetaPreview>>>()
            val cat2Deferred = CompletableDeferred<Outcome<List<MetaPreview>>>()

            coEvery { loader.load(match { it.catalogId == "top" && it.type == "movie" }, any()) } coAnswers {
                cat1Deferred.await()
            }
            coEvery { loader.load(match { it.catalogId == "top" && it.type == "series" }, any()) } coAnswers {
                cat2Deferred.await()
            }

            val vm = createViewModel()
            advanceUntilIdle()

            vm.onEvent(SearchEvent.QueryChanged("dune"))
            advanceTimeBy(400)

            // Catalog 2 answers first
            cat2Deferred.complete(Outcome.Success(listOf(createMeta("series_1"))))
            advanceUntilIdle()

            val intermediateRows = vm.uiState.value.rows
            assertEquals(1, intermediateRows.size)
            assertEquals("series", intermediateRows[0].catalog.type)

            // Catalog 1 answers second
            cat1Deferred.complete(Outcome.Success(listOf(createMeta("movie_1"))))
            advanceUntilIdle()

            val finalRows = vm.uiState.value.rows
            assertEquals(2, finalRows.size)
            assertEquals("movie", finalRows[0].catalog.type)
            assertEquals("series", finalRows[1].catalog.type)
        }

    @Test
    fun SV9_emptyRows_areHidden_noResultsWhenAllFinishEmpty() =
        runTest(testDispatcher) {
            val deferred = CompletableDeferred<Outcome<List<MetaPreview>>>()
            coEvery { loader.load(any(), any()) } coAnswers { deferred.await() }

            val vm = createViewModel()
            advanceUntilIdle()

            vm.onEvent(SearchEvent.QueryChanged("dune"))
            advanceTimeBy(400)
            runCurrent()

            assertEquals(SearchPhase.Searching, vm.uiState.value.phase)
            assertTrue(
                vm.uiState.value.rows
                    .isEmpty(),
            )

            deferred.complete(Outcome.Success(emptyList()))
            advanceUntilIdle()

            assertEquals(SearchPhase.NoResults, vm.uiState.value.phase)
            assertTrue(
                vm.uiState.value.rows
                    .isEmpty(),
            )
        }

    @Test
    fun SV10_someFailed_keepsRows_reportsFailedCount() =
        runTest(testDispatcher) {
            val cat3 = CatalogSpec("other", "top", "Other", extras = listOf(ExtraSpec("search", false, emptyList())))
            val addon = createAddon("https://a.com", "A", listOf(searchMovieCatalog, searchSeriesCatalog, cat3))
            activeAddonsFlow.value = listOf(addon)

            coEvery { loader.load(match { it.type == "movie" }, any()) } returns
                Outcome.Success(listOf(createMeta("m1")))
            coEvery { loader.load(match { it.type == "series" }, any()) } returns
                Outcome.Success(listOf(createMeta("s1")))
            coEvery { loader.load(match { it.type == "other" }, any()) } returns Outcome.Failure(AppError.Network)

            val vm = createViewModel()
            advanceUntilIdle()

            vm.onEvent(SearchEvent.QueryChanged("dune"))
            advanceTimeBy(400)
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals(SearchPhase.Results, state.phase)
            assertEquals(2, state.rows.size)
            assertEquals(1, state.failedCount)
        }

    @Test
    fun SV11_allFailed_showsError_retryRerunsOnlyFailedRows() =
        runTest(testDispatcher) {
            coEvery { loader.load(any(), any()) } returns Outcome.Failure(AppError.Network)

            val vm = createViewModel()
            advanceUntilIdle()

            vm.onEvent(SearchEvent.QueryChanged("dune"))
            advanceTimeBy(400)
            advanceUntilIdle()

            assertEquals(SearchPhase.Error(AppError.Network), vm.uiState.value.phase)

            coEvery { loader.load(any(), any()) } returns Outcome.Success(listOf(createMeta("m1")))
            vm.onEvent(SearchEvent.Retry)
            advanceUntilIdle()

            assertEquals(SearchPhase.Results, vm.uiState.value.phase)
            assertEquals(1, vm.uiState.value.rows.size)
            assertEquals(0, vm.uiState.value.failedCount)
        }

    @Test
    fun SV12_noSearchCatalogs_showsNoSearchAddons_noRequests() =
        runTest(testDispatcher) {
            val noSearchCatalog = CatalogSpec("movie", "top", "Popular", extras = emptyList())
            val noSearchAddon = createAddon("https://a.com", "A", listOf(noSearchCatalog))
            activeAddonsFlow.value = listOf(noSearchAddon)

            val vm = createViewModel()
            advanceUntilIdle()

            vm.onEvent(SearchEvent.QueryChanged("dune"))
            advanceTimeBy(400)
            advanceUntilIdle()

            assertEquals(SearchPhase.NoSearchAddons, vm.uiState.value.phase)
            coVerify(exactly = 0) { loader.load(any(), any()) }
        }

    @Test
    fun SV13_parallelism_neverExceedsFour() =
        runTest(testDispatcher) {
            val catalogs =
                (1..8).map { i ->
                    CatalogSpec(
                        type = "movie",
                        id = "id_$i",
                        name = "Catalog $i",
                        extras = listOf(ExtraSpec("search", false, emptyList())),
                    )
                }
            val addon = createAddon("https://a.com", "A", catalogs)
            activeAddonsFlow.value = listOf(addon)

            val activeCalls = AtomicInteger(0)
            val maxConcurrent = AtomicInteger(0)

            coEvery { loader.load(any(), any()) } coAnswers {
                val current = activeCalls.incrementAndGet()
                var max = maxConcurrent.get()
                while (current > max && !maxConcurrent.compareAndSet(max, current)) {
                    max = maxConcurrent.get()
                }
                delay(50)
                activeCalls.decrementAndGet()
                Outcome.Success(listOf(createMeta("1")))
            }

            val vm = createViewModel()
            advanceUntilIdle()

            vm.onEvent(SearchEvent.QueryChanged("dune"))
            advanceTimeBy(400)
            advanceTimeBy(500)
            advanceUntilIdle()

            assertTrue(maxConcurrent.get() in 1..4)
        }

    @Test
    fun SV14_rowIsCappedAtTheLimit() =
        runTest(testDispatcher) {
            val items = (1..100).map { createMeta("m_$it") }
            coEvery { loader.load(any(), any()) } returns Outcome.Success(items)

            val vm = createViewModel()
            advanceUntilIdle()

            vm.onEvent(SearchEvent.QueryChanged("dune"))
            advanceTimeBy(400)
            advanceUntilIdle()

            val rows = vm.uiState.value.rows
            assertEquals(1, rows.size)
            assertEquals(40, rows.first().items.size)
        }

    @Test
    fun SV15_familyMode_hidesAdultItems_withoutNetwork() =
        runTest(testDispatcher) {
            val regular = createMeta("reg", "Regular")
            val adult = createMeta("adult", "Adult").copy(genres = listOf("Adult"))
            coEvery { loader.load(any(), any()) } returns Outcome.Success(listOf(regular, adult))

            val vm = createViewModel()
            advanceUntilIdle()

            vm.onEvent(SearchEvent.QueryChanged("dune"))
            advanceTimeBy(400)
            advanceUntilIdle()

            assertEquals(
                2,
                vm.uiState.value.rows
                    .first()
                    .items.size,
            )
            coVerify(exactly = 1) { loader.load(any(), any()) }

            familyModeFlow.value = true
            advanceUntilIdle()

            assertEquals(
                1,
                vm.uiState.value.rows
                    .first()
                    .items.size,
            )
            assertEquals(
                "reg",
                vm.uiState.value.rows
                    .first()
                    .items
                    .first()
                    .id,
            )
            coVerify(exactly = 1) { loader.load(any(), any()) }

            familyModeFlow.value = false
            advanceUntilIdle()

            assertEquals(
                2,
                vm.uiState.value.rows
                    .first()
                    .items.size,
            )
            coVerify(exactly = 1) { loader.load(any(), any()) }
        }

    @Test
    fun SV16_clearQuery_returnsToIdle_andCancelsRequests() =
        runTest(testDispatcher) {
            val deferred = CompletableDeferred<Outcome<List<MetaPreview>>>()
            coEvery { loader.load(any(), any()) } coAnswers { deferred.await() }

            val vm = createViewModel()
            advanceUntilIdle()

            vm.onEvent(SearchEvent.QueryChanged("dune"))
            advanceTimeBy(400)
            runCurrent()

            assertEquals(SearchPhase.Searching, vm.uiState.value.phase)

            vm.onEvent(SearchEvent.ClearQuery)
            advanceUntilIdle()

            assertEquals(SearchPhase.Idle, vm.uiState.value.phase)
            assertTrue(
                vm.uiState.value.rows
                    .isEmpty(),
            )
        }

    @Test
    fun SV17_resultOpened_savesTheQuery() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            advanceUntilIdle()

            vm.onEvent(SearchEvent.QueryChanged("dune"))
            advanceTimeBy(400)
            advanceUntilIdle()

            vm.onEvent(SearchEvent.ResultOpened("movie", "1"))
            advanceUntilIdle()

            coVerify(exactly = 1) { searchHistoryRepository.save("dune") }
        }

    @Test
    fun SV18_history_isExposedNewestFirst() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            advanceUntilIdle()

            historyFlow.value = listOf("c", "b", "a")
            advanceUntilIdle()

            assertEquals(listOf("c", "b", "a"), vm.uiState.value.history)
        }

    @Test
    fun SV19_removeAndClearHistory_callTheRepository() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            advanceUntilIdle()

            vm.onEvent(SearchEvent.RemoveRecent("a"))
            advanceUntilIdle()
            coVerify(exactly = 1) { searchHistoryRepository.remove("a") }

            vm.onEvent(SearchEvent.ClearHistory)
            advanceUntilIdle()
            coVerify(exactly = 1) { searchHistoryRepository.clear() }
        }

    @Test
    fun SV20_useRecent_searchesImmediately_andMovesItToTop() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            advanceUntilIdle()

            vm.onEvent(SearchEvent.UseRecent("dune"))
            advanceUntilIdle()

            assertEquals("dune", vm.uiState.value.rawQuery)
            coVerify(exactly = 1) { loader.load(any(), match { it["search"] == "dune" }) }
            coVerify(exactly = 1) { searchHistoryRepository.save("dune") }
        }

    @Test
    fun SV21_catalogSetChanges_rerunsTheCurrentSearch() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            advanceUntilIdle()

            vm.onEvent(SearchEvent.QueryChanged("dune"))
            advanceTimeBy(400)
            advanceUntilIdle()
            coVerify(exactly = 1) { loader.load(any(), match { it["search"] == "dune" }) }

            val secondAddon =
                createAddon(
                    transportUrl = "https://addon2.com/manifest.json",
                    name = "Addon 2",
                    catalogs = listOf(searchSeriesCatalog),
                )
            activeAddonsFlow.value = listOf(secondAddon)
            advanceUntilIdle()

            coVerify(exactly = 2) { loader.load(any(), match { it["search"] == "dune" }) }
        }

    @Test
    fun SV22_requestsCarryOnlyTheNormalizedSearchExtra() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            advanceUntilIdle()

            vm.onEvent(SearchEvent.QueryChanged("  dune  part "))
            advanceTimeBy(400)
            advanceUntilIdle()

            coVerify(exactly = 1) {
                loader.load(any(), match { it == mapOf("search" to "dune part") })
            }
        }

    @Test
    fun SV23_oneCjkCharacter_isSearched() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            advanceUntilIdle()

            vm.onEvent(SearchEvent.QueryChanged("鬼"))
            advanceTimeBy(400)
            advanceUntilIdle()

            coVerify(atLeast = 1) {
                loader.load(any(), match { it["search"] == "鬼" })
            }
        }

    @Test
    fun SV24_isSearching_isTrueUntilEveryRowFinished_timeoutsIncluded() =
        runTest(testDispatcher) {
            val twoCatalogAddon =
                createAddon(
                    transportUrl = "https://addon.com/manifest.json",
                    name = "Addon",
                    catalogs = listOf(searchMovieCatalog, searchSeriesCatalog),
                )
            activeAddonsFlow.value = listOf(twoCatalogAddon)

            val cat1Deferred = CompletableDeferred<Outcome<List<MetaPreview>>>()
            val cat2Deferred = CompletableDeferred<Outcome<List<MetaPreview>>>()

            coEvery { loader.load(match { it.type == "movie" }, any()) } coAnswers { cat1Deferred.await() }
            coEvery { loader.load(match { it.type == "series" }, any()) } coAnswers { cat2Deferred.await() }

            val vm = createViewModel()
            advanceUntilIdle()

            vm.onEvent(SearchEvent.QueryChanged("dune"))
            advanceTimeBy(400)
            runCurrent()

            assertTrue(vm.uiState.value.isSearching)

            cat1Deferred.complete(Outcome.Success(listOf(createMeta("1"))))
            advanceUntilIdle()
            assertTrue(vm.uiState.value.isSearching)

            cat2Deferred.complete(Outcome.Failure(AppError.Timeout))
            advanceUntilIdle()
            assertFalse(vm.uiState.value.isSearching)
        }

    @Test
    fun SV25_posterMarks_followLibraryAndWatchedMovies() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            advanceUntilIdle()

            listStatusesFlow.value = mapOf("a" to ListStatus.WATCHING)
            watchedMovieIdsFlow.value = setOf("b")
            advanceUntilIdle()

            assertEquals(PosterMark.WATCHING, vm.uiState.value.posterMarks["a"])
            assertEquals(PosterMark.WATCHED, vm.uiState.value.posterMarks["b"])
        }

    @Test
    fun SV26_whitespaceOnlyQuery_isIdle() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            advanceUntilIdle()

            vm.onEvent(SearchEvent.QueryChanged("    "))
            advanceTimeBy(500)
            advanceUntilIdle()

            assertEquals(SearchPhase.Idle, vm.uiState.value.phase)
            coVerify(exactly = 0) { loader.load(any(), any()) }
        }

    @Test
    fun SV27_unsearchableQuery_isNotSavedToHistory() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            advanceUntilIdle()

            vm.onEvent(SearchEvent.QueryChanged("a"))
            vm.onEvent(SearchEvent.Submit)
            advanceUntilIdle()

            assertEquals(SearchPhase.TooShort, vm.uiState.value.phase)
            coVerify(exactly = 0) { searchHistoryRepository.save(any()) }
        }

    private fun createAddon(
        transportUrl: String,
        name: String,
        catalogs: List<CatalogSpec>,
    ): Addon =
        Addon(
            transportUrl = transportUrl,
            manifest =
                AddonManifest(
                    id = "id.$name",
                    version = "1.0.0",
                    name = name,
                    description = "",
                    logo = null,
                    background = null,
                    types = catalogs.map { it.type }.distinct(),
                    idPrefixes = null,
                    resources = emptyList(),
                    catalogs = catalogs,
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

    private fun createMeta(
        id: String,
        name: String = "Title $id",
    ): MetaPreview =
        MetaPreview(
            id = id,
            type = "movie",
            name = name,
            poster = null,
            posterShape = PosterShape.POSTER,
            background = null,
            logo = null,
            description = null,
            releaseInfo = null,
            imdbRating = null,
            genres = emptyList(),
        )
}
