package com.nexus.stream.presentation.feature.discover

import app.cash.turbine.test
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
import com.nexus.stream.domain.model.ResourceSpec
import com.nexus.stream.domain.repository.AddonRepository
import com.nexus.stream.domain.repository.ContentPrefsRepository
import com.nexus.stream.domain.repository.LibraryRepository
import com.nexus.stream.domain.repository.ProgressRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DiscoverViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val addonRepository: AddonRepository = mockk()
    private val contentPrefsRepository: ContentPrefsRepository = mockk()
    private val libraryRepository: LibraryRepository = mockk(relaxed = true)
    private val progressRepository: ProgressRepository = mockk(relaxed = true)
    private val loader: DiscoverPageLoader = mockk(relaxed = true)

    private val allAddonsFlow = MutableStateFlow<List<Addon>>(emptyList())
    private val activeAddonsFlow = MutableStateFlow<List<Addon>>(emptyList())
    private val familyModeFlow = MutableStateFlow(false)
    private val listStatusesFlow = MutableStateFlow<Map<String, ListStatus>>(emptyMap())
    private val watchedMovieIdsFlow = MutableStateFlow<Set<String>>(emptySet())

    private val movieCatalog =
        CatalogSpec(
            type = "movie",
            id = "top",
            name = "Popular",
            extras =
                listOf(
                    ExtraSpec("genre", isRequired = false, options = listOf("Action", "Comedy")),
                    ExtraSpec("skip", isRequired = false, options = emptyList()),
                ),
        )
    private val seriesCatalog =
        CatalogSpec(
            type = "series",
            id = "top",
            name = "Popular",
            extras =
                listOf(
                    ExtraSpec("genre", isRequired = false, options = listOf("Drama")),
                    ExtraSpec("skip", isRequired = false, options = emptyList()),
                ),
        )
    private val movieFeaturedCatalog =
        CatalogSpec(
            type = "movie",
            id = "featured",
            name = "Featured",
            extras =
                listOf(
                    ExtraSpec("skip", isRequired = false, options = emptyList()),
                ),
        )

    private val standardAddon =
        createAddon(
            transportUrl = "https://addon.com/manifest.json",
            name = "Addon",
            catalogs = listOf(movieCatalog, seriesCatalog),
        )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { addonRepository.observeAll() } returns allAddonsFlow
        every { addonRepository.observeActive() } returns activeAddonsFlow
        every { contentPrefsRepository.familyMode } returns familyModeFlow
        every { libraryRepository.observeListStatuses() } returns listStatusesFlow
        every { progressRepository.observeWatchedMovieIds() } returns watchedMovieIdsFlow
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): DiscoverViewModel =
        DiscoverViewModel(
            addonRepository = addonRepository,
            contentPrefsRepository = contentPrefsRepository,
            libraryRepository = libraryRepository,
            progressRepository = progressRepository,
            loader = loader,
            dispatcher = testDispatcher,
        )

    @Test
    fun DV1_init_loadsFirstPageOfFirstMovieCatalog() =
        runTest(testDispatcher) {
            val items = (1..3).map { createMeta("m_$it") }
            coEvery { loader.load(any(), any()) } returns Outcome.Success(items)
            allAddonsFlow.value = listOf(standardAddon)
            activeAddonsFlow.value = listOf(standardAddon)

            val vm = createViewModel()
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals("movie", state.selectedType)
            assertEquals(standardAddon.manifest.catalogs[0].id, state.catalogs.firstOrNull()?.catalogId)
            assertEquals(DiscoverPhase.Content, state.phase)
            assertEquals(items, state.items)
            assertEquals(1, state.catalogs.size) // 1 catalog for movie
            coVerify(exactly = 1) { loader.load(any(), match { !it.containsKey("skip") }) }
        }

    @Test
    fun DV2_noAddons_showsNoAddons_andNeverCallsTheLoader() =
        runTest(testDispatcher) {
            allAddonsFlow.value = emptyList()
            activeAddonsFlow.value = emptyList()

            val vm = createViewModel()
            advanceUntilIdle()

            assertEquals(DiscoverPhase.NoAddons, vm.uiState.value.phase)
            coVerify(exactly = 0) { loader.load(any(), any()) }
        }

    @Test
    fun DV3_addonsWithoutBrowsableCatalogs_showsNoCatalogs() =
        runTest(testDispatcher) {
            val streamOnlyAddon =
                createAddon(
                    transportUrl = "https://stream.com/manifest.json",
                    name = "StreamsOnly",
                    catalogs = emptyList(),
                )
            allAddonsFlow.value = listOf(streamOnlyAddon)
            activeAddonsFlow.value = listOf(streamOnlyAddon)

            val vm = createViewModel()
            advanceUntilIdle()

            assertEquals(DiscoverPhase.NoCatalogs, vm.uiState.value.phase)
            coVerify(exactly = 0) { loader.load(any(), any()) }
        }

    @Test
    fun DV4_selectType_switchesAndReloads_andSameTypeDoesNothing() =
        runTest(testDispatcher) {
            val movieItems = listOf(createMeta("m1"))
            val seriesItems = listOf(createMeta("s1"))
            coEvery { loader.load(match { it.type == "movie" }, any()) } returns Outcome.Success(movieItems)
            coEvery { loader.load(match { it.type == "series" }, any()) } returns Outcome.Success(seriesItems)
            allAddonsFlow.value = listOf(standardAddon)
            activeAddonsFlow.value = listOf(standardAddon)

            val vm = createViewModel()
            advanceUntilIdle()

            vm.onEvent(DiscoverEvent.SelectType("series"))
            advanceUntilIdle()
            assertEquals("series", vm.uiState.value.selectedType)
            assertEquals(seriesItems, vm.uiState.value.items)
            coVerify(exactly = 1) { loader.load(match { it.type == "series" }, any()) }

            // Second event with same type does nothing
            vm.onEvent(DiscoverEvent.SelectType("series"))
            advanceUntilIdle()
            coVerify(exactly = 1) { loader.load(match { it.type == "series" }, any()) }
        }

    @Test
    fun DV5_selectType_restoresTheRememberedCatalogOfThatType() =
        runTest(testDispatcher) {
            val addonWithTwoMovieCatalogs =
                createAddon(
                    transportUrl = "https://addon.com/manifest.json",
                    name = "Addon",
                    catalogs = listOf(movieCatalog, movieFeaturedCatalog, seriesCatalog),
                )
            coEvery { loader.load(any(), any()) } returns Outcome.Success(listOf(createMeta("item")))
            allAddonsFlow.value = listOf(addonWithTwoMovieCatalogs)
            activeAddonsFlow.value = listOf(addonWithTwoMovieCatalogs)

            val vm = createViewModel()
            advanceUntilIdle()

            val featuredKey = "${addonWithTwoMovieCatalogs.transportUrl}|movie|${movieFeaturedCatalog.id}"
            vm.onEvent(DiscoverEvent.SelectCatalog(featuredKey))
            advanceUntilIdle()
            assertEquals(featuredKey, vm.uiState.value.selectedCatalogKey)

            vm.onEvent(DiscoverEvent.SelectType("series"))
            advanceUntilIdle()
            assertEquals("series", vm.uiState.value.selectedType)

            vm.onEvent(DiscoverEvent.SelectType("movie"))
            advanceUntilIdle()
            assertEquals("movie", vm.uiState.value.selectedType)
            assertEquals(featuredKey, vm.uiState.value.selectedCatalogKey)
        }

    @Test
    fun DV6_selectGenre_clearsItemsAndRestartsPaging() =
        runTest(testDispatcher) {
            coEvery { loader.load(any(), any()) } returns Outcome.Success(listOf(createMeta("1"), createMeta("2")))
            allAddonsFlow.value = listOf(standardAddon)
            activeAddonsFlow.value = listOf(standardAddon)

            val vm = createViewModel()
            advanceUntilIdle()
            assertEquals(2, vm.uiState.value.items.size)

            vm.onEvent(DiscoverEvent.SelectGenre("Action"))
            advanceUntilIdle()

            assertEquals(
                "Action",
                vm.uiState.value.genreUi
                    ?.selectedGenre,
            )
            coVerify { loader.load(any(), match { it["genre"] == "Action" && !it.containsKey("skip") }) }
        }

    @Test
    fun DV7_staleResponse_isIgnored() =
        runTest(testDispatcher) {
            val slowDeferred = CompletableDeferred<Outcome<List<MetaPreview>>>()
            val fastItems = listOf(createMeta("b1"))
            val addon =
                createAddon(
                    transportUrl = "https://addon.com/manifest.json",
                    catalogs = listOf(movieCatalog, movieFeaturedCatalog),
                )
            allAddonsFlow.value = listOf(addon)
            activeAddonsFlow.value = listOf(addon)

            coEvery { loader.load(match { it.catalogId == "top" }, any()) } coAnswers { slowDeferred.await() }
            coEvery { loader.load(match { it.catalogId == "featured" }, any()) } returns Outcome.Success(fastItems)

            val vm = createViewModel()
            advanceUntilIdle()

            val featuredKey = "${addon.transportUrl}|movie|featured"
            vm.onEvent(DiscoverEvent.SelectCatalog(featuredKey))
            advanceUntilIdle()

            assertEquals(fastItems, vm.uiState.value.items)

            // Slow top answers last
            slowDeferred.complete(Outcome.Success(listOf(createMeta("a1"))))
            advanceUntilIdle()

            assertEquals(fastItems, vm.uiState.value.items)
        }

    @Test
    fun DV8_loadMore_usesLoadedCountAsSkip_andAppendsWithoutDuplicates() =
        runTest(testDispatcher) {
            val page1 = (1..100).map { createMeta("id_$it") }
            // 97 new + 3 duplicate (id_98, id_99, id_100)
            val page2 = (98..197).map { createMeta("id_$it") }

            coEvery { loader.load(any(), match { !it.containsKey("skip") }) } returns Outcome.Success(page1)
            coEvery { loader.load(any(), match { it["skip"] == "100" }) } returns Outcome.Success(page2)
            allAddonsFlow.value = listOf(standardAddon)
            activeAddonsFlow.value = listOf(standardAddon)

            val vm = createViewModel()
            advanceUntilIdle()
            assertEquals(100, vm.uiState.value.items.size)

            vm.onEvent(DiscoverEvent.LoadMore)
            advanceUntilIdle()

            assertEquals(197, vm.uiState.value.items.size)
            coVerify(exactly = 1) { loader.load(any(), match { it["skip"] == "100" }) }
        }

    @Test
    fun DV9_loadMore_whileLoading_isIgnored() =
        runTest(testDispatcher) {
            val deferred = CompletableDeferred<Outcome<List<MetaPreview>>>()
            coEvery {
                loader.load(any(), match { !it.containsKey("skip") })
            } returns Outcome.Success(listOf(createMeta("1")))
            coEvery { loader.load(any(), match { it.containsKey("skip") }) } coAnswers { deferred.await() }
            allAddonsFlow.value = listOf(standardAddon)
            activeAddonsFlow.value = listOf(standardAddon)

            val vm = createViewModel()
            advanceUntilIdle()

            vm.onEvent(DiscoverEvent.LoadMore)
            vm.onEvent(DiscoverEvent.LoadMore) // in flight, ignored
            advanceUntilIdle()

            coVerify(exactly = 1) { loader.load(any(), match { it.containsKey("skip") }) }
            deferred.complete(Outcome.Success(listOf(createMeta("2"))))
            advanceUntilIdle()
        }

    @Test
    fun DV10_loadMore_failure_keepsItems_showsFooterError_retryAppends() =
        runTest(testDispatcher) {
            val initialItems = listOf(createMeta("1"))
            val nextItems = listOf(createMeta("2"))
            val networkError = Outcome.Failure(AppError.Network)
            val successNext = Outcome.Success(nextItems)
            coEvery { loader.load(any(), match { !it.containsKey("skip") }) } returns Outcome.Success(initialItems)
            coEvery {
                loader.load(any(), match { it.containsKey("skip") })
            } returns networkError andThen successNext
            allAddonsFlow.value = listOf(standardAddon)
            activeAddonsFlow.value = listOf(standardAddon)

            val vm = createViewModel()
            advanceUntilIdle()

            vm.onEvent(DiscoverEvent.LoadMore)
            advanceUntilIdle()

            assertEquals(initialItems, vm.uiState.value.items)
            assertTrue(vm.uiState.value.footer is DiscoverFooter.Error)

            vm.onEvent(DiscoverEvent.Retry)
            advanceUntilIdle()

            assertEquals(listOf(createMeta("1"), createMeta("2")), vm.uiState.value.items)
            assertEquals(DiscoverFooter.None, vm.uiState.value.footer)
        }

    @Test
    fun DV11_firstPageFailure_showsError_retryLoads() =
        runTest(testDispatcher) {
            val items = listOf(createMeta("1"))
            val networkError = Outcome.Failure(AppError.Network)
            val successItems = Outcome.Success(items)
            coEvery { loader.load(any(), any()) } returns networkError andThen successItems
            allAddonsFlow.value = listOf(standardAddon)
            activeAddonsFlow.value = listOf(standardAddon)

            val vm = createViewModel()
            advanceUntilIdle()

            assertTrue(vm.uiState.value.phase is DiscoverPhase.Error)

            vm.onEvent(DiscoverEvent.Retry)
            advanceUntilIdle()

            assertEquals(DiscoverPhase.Content, vm.uiState.value.phase)
            assertEquals(items, vm.uiState.value.items)
        }

    @Test
    fun DV12_emptyFirstPage_showsEmpty_evenWhenItemsWereSkipped() =
        runTest(testDispatcher) {
            coEvery { loader.load(any(), any()) } returns Outcome.Success(emptyList())
            allAddonsFlow.value = listOf(standardAddon)
            activeAddonsFlow.value = listOf(standardAddon)

            val vm = createViewModel()
            advanceUntilIdle()

            assertEquals(DiscoverPhase.Empty, vm.uiState.value.phase)
        }

    @Test
    fun DV13_refresh_keepsItemsUntilTheNewPageArrives_thenReplacesAndResetsPaging() =
        runTest(testDispatcher) {
            val initialItems = listOf(createMeta("1"), createMeta("2"))
            val refreshedItems = listOf(createMeta("10"))
            val refreshDeferred = CompletableDeferred<Outcome<List<MetaPreview>>>()
            var callCount = 0
            coEvery { loader.load(any(), match { !it.containsKey("skip") }) } coAnswers {
                callCount++
                if (callCount == 1) {
                    Outcome.Success(initialItems)
                } else {
                    refreshDeferred.await()
                }
            }
            allAddonsFlow.value = listOf(standardAddon)
            activeAddonsFlow.value = listOf(standardAddon)

            val vm = createViewModel()
            advanceUntilIdle()

            vm.onEvent(DiscoverEvent.Refresh)
            assertTrue(vm.uiState.value.isRefreshing)
            assertEquals(initialItems, vm.uiState.value.items)

            refreshDeferred.complete(Outcome.Success(refreshedItems))
            advanceUntilIdle()

            assertFalse(vm.uiState.value.isRefreshing)
            assertEquals(refreshedItems, vm.uiState.value.items)
        }

    @Test
    fun DV14_refreshFailure_keepsItems_emitsRefreshFailedOnce() =
        runTest(testDispatcher) {
            val initialItems = listOf(createMeta("1"))
            val successInitial = Outcome.Success(initialItems)
            val networkError = Outcome.Failure(AppError.Network)
            coEvery { loader.load(any(), any()) } returns successInitial andThen networkError
            allAddonsFlow.value = listOf(standardAddon)
            activeAddonsFlow.value = listOf(standardAddon)

            val vm = createViewModel()
            advanceUntilIdle()

            vm.effects.test {
                vm.onEvent(DiscoverEvent.Refresh)
                advanceUntilIdle()

                assertEquals(DiscoverEffect.RefreshFailed, awaitItem())
                assertFalse(vm.uiState.value.isRefreshing)
                assertEquals(initialItems, vm.uiState.value.items)
                expectNoEvents()
            }
        }

    @Test
    fun DV15_familyModeToggle_refiltersWithoutNetwork() =
        runTest(testDispatcher) {
            val cleanItem = createMeta("clean", name = "Family Movie")
            val adultItem = createMeta("adult", name = "Pornographic Movie")
            coEvery { loader.load(any(), any()) } returns Outcome.Success(listOf(cleanItem, adultItem))
            allAddonsFlow.value = listOf(standardAddon)
            activeAddonsFlow.value = listOf(standardAddon)

            familyModeFlow.value = false
            val vm = createViewModel()
            advanceUntilIdle()

            assertEquals(2, vm.uiState.value.items.size)
            coVerify(exactly = 1) { loader.load(any(), any()) }

            // Turn family mode ON
            familyModeFlow.value = true
            advanceUntilIdle()
            assertEquals(listOf(cleanItem), vm.uiState.value.items)
            coVerify(exactly = 1) { loader.load(any(), any()) }

            // Turn family mode OFF
            familyModeFlow.value = false
            advanceUntilIdle()
            assertEquals(2, vm.uiState.value.items.size)
            coVerify(exactly = 1) { loader.load(any(), any()) }
        }

    @Test
    fun DV16_familyFiltersWholePage_keepsPagingUntilSomethingShows() =
        runTest(testDispatcher) {
            val adultItems = listOf(createMeta("adult_1", name = "Porn Video"))
            val cleanItems = listOf(createMeta("clean_1", name = "Clean Video"))
            familyModeFlow.value = true

            coEvery { loader.load(any(), match { !it.containsKey("skip") }) } returns Outcome.Success(adultItems)
            coEvery { loader.load(any(), match { it["skip"] == "1" }) } returns Outcome.Success(cleanItems)
            allAddonsFlow.value = listOf(standardAddon)
            activeAddonsFlow.value = listOf(standardAddon)

            val vm = createViewModel()
            advanceUntilIdle()

            assertEquals(cleanItems, vm.uiState.value.items)
            assertEquals(DiscoverPhase.Content, vm.uiState.value.phase)
            coVerify(exactly = 1) { loader.load(any(), match { it["skip"] == "1" }) }
        }

    @Test
    fun DV17_selectedCatalogRemoved_movesToTheFirstAvailableAndReloads() =
        runTest(testDispatcher) {
            val addon1 =
                createAddon(
                    transportUrl = "https://addon1.com/manifest.json",
                    name = "Addon 1",
                    catalogs = listOf(movieCatalog),
                )
            val addon2 =
                createAddon(
                    transportUrl = "https://addon2.com/manifest.json",
                    name = "Addon 2",
                    catalogs = listOf(movieFeaturedCatalog),
                )
            coEvery { loader.load(any(), any()) } returns Outcome.Success(listOf(createMeta("1")))
            allAddonsFlow.value = listOf(addon1, addon2)
            activeAddonsFlow.value = listOf(addon1, addon2)

            val vm = createViewModel()
            advanceUntilIdle()
            assertEquals(
                "top",
                vm.uiState.value.catalogs
                    .firstOrNull()
                    ?.catalogId,
            )

            // addon1 removed
            activeAddonsFlow.value = listOf(addon2)
            advanceUntilIdle()

            assertEquals(
                "featured",
                vm.uiState.value.catalogs
                    .firstOrNull()
                    ?.catalogId,
            )
        }

    @Test
    fun DV18_lastAddonRemoved_showsNoAddons() =
        runTest(testDispatcher) {
            coEvery { loader.load(any(), any()) } returns Outcome.Success(listOf(createMeta("1")))
            allAddonsFlow.value = listOf(standardAddon)
            activeAddonsFlow.value = listOf(standardAddon)

            val vm = createViewModel()
            advanceUntilIdle()
            assertEquals(DiscoverPhase.Content, vm.uiState.value.phase)

            allAddonsFlow.value = emptyList()
            advanceUntilIdle()

            assertEquals(DiscoverPhase.NoAddons, vm.uiState.value.phase)
        }

    @Test
    fun DV19_posterMarks_followLibraryAndWatchedMovies() =
        runTest(testDispatcher) {
            val items = listOf(createMeta("a"), createMeta("b"), createMeta("c"))
            coEvery { loader.load(any(), any()) } returns Outcome.Success(items)
            allAddonsFlow.value = listOf(standardAddon)
            activeAddonsFlow.value = listOf(standardAddon)

            val vm = createViewModel()
            advanceUntilIdle()

            listStatusesFlow.value = mapOf("a" to ListStatus.WATCHING, "b" to ListStatus.COMPLETED)
            watchedMovieIdsFlow.value = setOf("c")
            advanceUntilIdle()

            val marks = vm.uiState.value.posterMarks
            assertEquals(PosterMark.WATCHING, marks["a"])
            assertEquals(PosterMark.WATCHED, marks["b"])
            assertEquals(PosterMark.WATCHED, marks["c"])
        }

    @Test
    fun DV20_eventsWithoutSelection_areIgnored() =
        runTest(testDispatcher) {
            allAddonsFlow.value = emptyList()
            activeAddonsFlow.value = emptyList()

            val vm = createViewModel()
            advanceUntilIdle()

            vm.onEvent(DiscoverEvent.LoadMore)
            vm.onEvent(DiscoverEvent.Retry)
            vm.onEvent(DiscoverEvent.Refresh)
            vm.onEvent(DiscoverEvent.SelectGenre("Action"))
            advanceUntilIdle()

            coVerify(exactly = 0) { loader.load(any(), any()) }
        }

    @Test
    fun DV21_requiredGenre_firstRequestCarriesTheFirstOption() =
        runTest(testDispatcher) {
            val requiredGenreCatalog =
                CatalogSpec(
                    type = "movie",
                    id = "year",
                    name = "New",
                    extras =
                        listOf(
                            ExtraSpec("genre", isRequired = true, options = listOf("2024", "2023")),
                            ExtraSpec("skip", isRequired = false, options = emptyList()),
                        ),
                )
            val addon =
                createAddon(
                    transportUrl = "https://addon.com/manifest.json",
                    catalogs = listOf(requiredGenreCatalog),
                )
            coEvery { loader.load(any(), any()) } returns Outcome.Success(listOf(createMeta("1")))
            allAddonsFlow.value = listOf(addon)
            activeAddonsFlow.value = listOf(addon)

            val vm = createViewModel()
            advanceUntilIdle()

            coVerify { loader.load(any(), match { it["genre"] == "2024" }) }
            assertEquals(
                "2024",
                vm.uiState.value.genreUi
                    ?.selectedGenre,
            )
            assertTrue(
                vm.uiState.value.genreUi
                    ?.isRequired == true,
            )
        }

    @Test
    fun DV22_noMoreItems_loadMoreIsIgnored() =
        runTest(testDispatcher) {
            // Page 1 has 1 item, catalog has no skip (or applyPage returns hasMore = false)
            val noSkipCatalog =
                CatalogSpec(
                    type = "movie",
                    id = "top",
                    name = "Popular",
                    extras = emptyList(), // no skip
                )
            val addon =
                createAddon(
                    transportUrl = "https://addon.com/manifest.json",
                    catalogs = listOf(noSkipCatalog),
                )
            coEvery { loader.load(any(), any()) } returns Outcome.Success(listOf(createMeta("1")))
            allAddonsFlow.value = listOf(addon)
            activeAddonsFlow.value = listOf(addon)

            val vm = createViewModel()
            advanceUntilIdle()

            vm.onEvent(DiscoverEvent.LoadMore)
            advanceUntilIdle()

            coVerify(exactly = 1) { loader.load(any(), any()) }
        }

    private fun createAddon(
        transportUrl: String,
        name: String = "Test Addon",
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
