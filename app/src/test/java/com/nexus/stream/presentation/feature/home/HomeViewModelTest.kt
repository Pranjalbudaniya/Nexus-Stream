package com.nexus.stream.presentation.feature.home

import com.nexus.stream.data.remote.addon.HomeRowLoader
import com.nexus.stream.domain.model.Addon
import com.nexus.stream.domain.model.AddonManifest
import com.nexus.stream.domain.model.AddonSource
import com.nexus.stream.domain.model.AppError
import com.nexus.stream.domain.model.CatalogSpec
import com.nexus.stream.domain.model.ContinueWatchingItem
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
import com.nexus.stream.domain.usecase.EnsureDefaultAddons
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val addonRepository: AddonRepository = mockk()
    private val ensureDefaultAddons: EnsureDefaultAddons = mockk()
    private val homeRowLoader: HomeRowLoader = mockk(relaxed = true)
    private val libraryRepository: LibraryRepository = mockk(relaxed = true)
    private val progressRepository: ProgressRepository = mockk(relaxed = true)
    private val contentPrefsRepository: ContentPrefsRepository = mockk()

    private val allAddonsFlow = MutableStateFlow<List<Addon>>(emptyList())
    private val enabledAddonsFlow = MutableStateFlow<List<Addon>>(emptyList())
    private val familyModeFlow = MutableStateFlow(true)
    private val listStatusesFlow = MutableStateFlow<Map<String, ListStatus>>(emptyMap())
    private val watchedMovieIdsFlow = MutableStateFlow<Set<String>>(emptySet())
    private val continueWatchingFlow = MutableStateFlow<List<ContinueWatchingItem>>(emptyList())

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { addonRepository.observeAll() } returns allAddonsFlow
        every { addonRepository.observeEnabled() } returns enabledAddonsFlow
        every { addonRepository.observeActive() } returns enabledAddonsFlow
        every { contentPrefsRepository.familyMode } returns familyModeFlow
        every { libraryRepository.observeListStatuses() } returns listStatusesFlow
        every { progressRepository.observeWatchedMovieIds() } returns watchedMovieIdsFlow
        every { progressRepository.observeContinueWatching() } returns continueWatchingFlow
        coEvery { libraryRepository.setStatus(any(), any()) } returns Unit
        coEvery { ensureDefaultAddons() } returns Outcome.Success(Unit)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): HomeViewModel =
        HomeViewModel(
            addonRepository = addonRepository,
            ensureDefaultAddons = ensureDefaultAddons,
            homeRowLoader = homeRowLoader,
            libraryRepository = libraryRepository,
            progressRepository = progressRepository,
            contentPrefsRepository = contentPrefsRepository,
            dispatcher = testDispatcher,
        )

    @Test
    fun init_callsSeed() =
        runTest(testDispatcher) {
            createViewModel()
            advanceUntilIdle()

            coVerify(exactly = 1) { ensureDefaultAddons() }
        }

    @Test
    fun seedFailure_setsSeedFailedTrue() =
        runTest(testDispatcher) {
            coEvery { ensureDefaultAddons() } returns Outcome.Failure(AppError.Network)

            val viewModel = createViewModel()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.seedFailed)
        }

    @Test
    fun rows_appearLoadingThenLoaded() =
        runTest(testDispatcher) {
            val addon = createAddon(name = "Addon1", type = "movie", catalogId = "cat1")
            allAddonsFlow.value = listOf(addon)
            enabledAddonsFlow.value = listOf(addon)

            val items = listOf(createMetaPreview("id1", "Title 1"))
            coEvery { homeRowLoader.load(any(), force = false) } coAnswers {
                delay(200)
                Outcome.Success(items)
            }

            val viewModel = createViewModel()
            advanceTimeBy(50)

            val loadingRows = viewModel.uiState.value.rows
            assertEquals(1, loadingRows.size)
            assertTrue(loadingRows[0].status is RowStatus.Loading)

            advanceUntilIdle()

            val loadedRows = viewModel.uiState.value.rows
            assertEquals(1, loadedRows.size)
            assertTrue(loadedRows[0].status is RowStatus.Loaded)
            assertEquals(items, (loadedRows[0].status as RowStatus.Loaded).items)
        }

    @Test
    fun togglingUnrelatedAddon_doesNotReloadUnchangedRows() =
        runTest(testDispatcher) {
            val addon1 = createAddon(name = "Addon1", type = "movie", catalogId = "cat1")
            val addonStream =
                createAddon(name = "StreamAddon", type = "movie", catalogId = "cat2", hasCatalogs = false)

            allAddonsFlow.value = listOf(addon1, addonStream)
            enabledAddonsFlow.value = listOf(addon1, addonStream)

            coEvery { homeRowLoader.load(any(), any()) } returns
                Outcome.Success(listOf(createMetaPreview("id1", "Title 1")))

            val viewModel = createViewModel()
            advanceUntilIdle()

            // Exactly 1 row was loaded
            val specKey =
                viewModel.uiState.value.rows
                    .first()
                    .spec.key
            coVerify(exactly = 1) {
                homeRowLoader.load(match { it.key == specKey }, force = false)
            }

            // Now toggle/disable the unrelated stream addon
            allAddonsFlow.value = listOf(addon1, addonStream.copy(enabled = false))
            enabledAddonsFlow.value = listOf(addon1)
            advanceUntilIdle()

            // The unchanged row must NOT be reloaded
            coVerify(exactly = 1) {
                homeRowLoader.load(match { it.key == specKey }, force = false)
            }
        }

    @Test
    fun retryKey_reloadsOnlyThatRowWithForceTrue() =
        runTest(testDispatcher) {
            val addon1 = createAddon(name = "Addon1", type = "movie", catalogId = "cat1")
            val addon2 = createAddon(name = "Addon2", type = "series", catalogId = "cat2")
            allAddonsFlow.value = listOf(addon1, addon2)
            enabledAddonsFlow.value = listOf(addon1, addon2)

            coEvery { homeRowLoader.load(any(), force = false) } returns
                Outcome.Success(emptyList())
            coEvery { homeRowLoader.load(any(), force = true) } returns
                Outcome.Success(listOf(createMetaPreview("id_new", "Reloaded")))

            val viewModel = createViewModel()
            advanceUntilIdle()

            val key1 =
                viewModel.uiState.value.rows[0]
                    .spec.key
            val key2 =
                viewModel.uiState.value.rows[1]
                    .spec.key

            viewModel.onEvent(HomeEvent.Retry(key1))
            advanceUntilIdle()

            coVerify(exactly = 1) {
                homeRowLoader.load(match { it.key == key1 }, force = true)
            }
            coVerify(exactly = 0) {
                homeRowLoader.load(match { it.key == key2 }, force = true)
            }
        }

    @Test
    fun refresh_reloadsAllWithForceTrue() =
        runTest(testDispatcher) {
            val addon1 = createAddon(name = "Addon1", type = "movie", catalogId = "cat1")
            val addon2 = createAddon(name = "Addon2", type = "series", catalogId = "cat2")
            allAddonsFlow.value = listOf(addon1, addon2)
            enabledAddonsFlow.value = listOf(addon1, addon2)

            coEvery { homeRowLoader.load(any(), any()) } returns Outcome.Success(emptyList())

            val viewModel = createViewModel()
            advanceUntilIdle()

            val key1 =
                viewModel.uiState.value.rows[0]
                    .spec.key
            val key2 =
                viewModel.uiState.value.rows[1]
                    .spec.key

            viewModel.onEvent(HomeEvent.Refresh)
            advanceUntilIdle()

            coVerify(atLeast = 1) {
                homeRowLoader.load(match { it.key == key1 }, force = true)
            }
            coVerify(atLeast = 1) {
                homeRowLoader.load(match { it.key == key2 }, force = true)
            }
            assertFalse(viewModel.uiState.value.isRefreshing)
        }

    @Test
    fun twoNetworkFailures_setsOffline() =
        runTest(testDispatcher) {
            val addon1 = createAddon(name = "Addon1", type = "movie", catalogId = "cat1")
            val addon2 = createAddon(name = "Addon2", type = "series", catalogId = "cat2")
            allAddonsFlow.value = listOf(addon1, addon2)
            enabledAddonsFlow.value = listOf(addon1, addon2)

            coEvery { homeRowLoader.load(any(), any()) } returns Outcome.Failure(AppError.Network)

            val viewModel = createViewModel()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.offline)
        }

    @Test
    fun failedRow_doesNotAffectOthers() =
        runTest(testDispatcher) {
            val addon1 = createAddon(name = "Addon1", type = "movie", catalogId = "cat1")
            val addon2 = createAddon(name = "Addon2", type = "series", catalogId = "cat2")
            allAddonsFlow.value = listOf(addon1, addon2)
            enabledAddonsFlow.value = listOf(addon1, addon2)

            val successItems = listOf(createMetaPreview("s1", "Success Item"))
            coEvery {
                homeRowLoader.load(match { it.type == "movie" }, any())
            } returns Outcome.Failure(AppError.NotFound)
            coEvery {
                homeRowLoader.load(match { it.type == "series" }, any())
            } returns Outcome.Success(successItems)

            val viewModel = createViewModel()
            advanceUntilIdle()

            val rows = viewModel.uiState.value.rows
            assertEquals(2, rows.size)
            assertTrue(rows[0].status is RowStatus.Failed)
            assertEquals(AppError.NotFound, (rows[0].status as RowStatus.Failed).error)

            assertTrue(rows[1].status is RowStatus.Loaded)
            assertEquals(successItems, (rows[1].status as RowStatus.Loaded).items)
        }

    private fun createAddon(
        name: String,
        type: String,
        catalogId: String,
        hasCatalogs: Boolean = true,
    ): Addon =
        Addon(
            transportUrl = "https://$name.com/manifest.json",
            manifest =
                AddonManifest(
                    id = "id.$name",
                    version = "1.0",
                    name = name,
                    description = "",
                    logo = null,
                    background = null,
                    types = listOf(type),
                    idPrefixes = null,
                    resources = emptyList(),
                    catalogs =
                        if (hasCatalogs) {
                            listOf(
                                CatalogSpec(
                                    type = type,
                                    id = catalogId,
                                    name = "Catalog $name",
                                    extras = emptyList(),
                                ),
                            )
                        } else {
                            emptyList()
                        },
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

    private fun createMetaPreview(
        id: String,
        name: String,
        background: String? = null,
        genres: List<String> = emptyList(),
    ): MetaPreview =
        MetaPreview(
            id = id,
            type = "movie",
            name = name,
            poster = null,
            posterShape = PosterShape.POSTER,
            background = background,
            logo = null,
            description = null,
            releaseInfo = null,
            imdbRating = null,
            genres = genres,
        )

    @Test
    fun hero_isBuiltFromFirstQualifyingLoadedRow() =
        runTest(testDispatcher) {
            val addon = createAddon(name = "addon1", type = "movie", catalogId = "top")
            val qualifyingItems =
                listOf(
                    createMetaPreview("1", "M1", background = "https://bg1.jpg"),
                    createMetaPreview("2", "M2", background = "https://bg2.jpg"),
                    createMetaPreview("3", "M3", background = "https://bg3.jpg"),
                )
            coEvery { homeRowLoader.load(any(), any()) } returns Outcome.Success(qualifyingItems)

            val viewModel = createViewModel()
            allAddonsFlow.value = listOf(addon)
            enabledAddonsFlow.value = listOf(addon)
            advanceUntilIdle()

            val hero = viewModel.uiState.value.hero
            assertEquals(3, hero.size)
            assertEquals("1", hero[0].id)
            assertEquals("2", hero[1].id)
            assertEquals("3", hero[2].id)
        }

    @Test
    fun hero_tick_adds_as_Plan_to_watch() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            val item = createMetaPreview("tt1", "Test")

            viewModel.onEvent(HomeEvent.ToggleWatchlist(item))
            advanceUntilIdle()

            coVerify(exactly = 1) { libraryRepository.setStatus(item, ListStatus.PLAN_TO_WATCH) }
        }

    @Test
    fun tick_on_a_Watching_title_asks_for_confirmation_and_Cancel_changes_nothing() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            val item = createMetaPreview("tt1", "Test")

            listStatusesFlow.value = mapOf("tt1" to ListStatus.WATCHING)
            advanceUntilIdle()

            viewModel.onEvent(HomeEvent.ToggleWatchlist(item))
            advanceUntilIdle()

            assertEquals(item to ListStatus.WATCHING, viewModel.uiState.value.confirmRemoveItem)
            coVerify(exactly = 0) { libraryRepository.setStatus(item, null) }

            viewModel.onEvent(HomeEvent.DismissRemoveDialog)
            advanceUntilIdle()

            assertEquals(null, viewModel.uiState.value.confirmRemoveItem)
            coVerify(exactly = 0) { libraryRepository.setStatus(item, null) }
        }

    @Test
    fun posterMarks_follows_the_flows() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            listStatusesFlow.value = mapOf("s1" to ListStatus.COMPLETED, "s2" to ListStatus.WATCHING)
            watchedMovieIdsFlow.value = setOf("m1")
            advanceUntilIdle()

            val marks = viewModel.uiState.value.posterMarks
            assertEquals(PosterMark.WATCHED, marks["s1"])
            assertEquals(PosterMark.WATCHING, marks["s2"])
            assertEquals(PosterMark.WATCHED, marks["m1"])
        }

    @Test
    fun adult_items_are_dropped_and_an_emptied_row_is_hidden_when_Family_mode_is_on() =
        runTest(testDispatcher) {
            val addon = createAddon("u1", type = "movie", catalogId = "top")

            val adultItem = createMetaPreview("a1", "Porn Film")
            val safeItem = createMetaPreview("s1", "Safe Movie")

            coEvery { homeRowLoader.load(any(), any()) } returns Outcome.Success(listOf(adultItem, safeItem))

            val viewModel = createViewModel()
            allAddonsFlow.value = listOf(addon)
            enabledAddonsFlow.value = listOf(addon)
            advanceUntilIdle()

            val loadedRows = viewModel.uiState.value.rows
            assertEquals(1, loadedRows.size)
            val items = (loadedRows[0].status as RowStatus.Loaded).items
            assertEquals(listOf(safeItem), items)
        }

    @Test
    fun turning_Family_mode_off_restores_adult_items_without_refetch() =
        runTest(testDispatcher) {
            val addon = createAddon("u1", type = "movie", catalogId = "top")

            val adultItem = createMetaPreview("a1", "Adult Movie", genres = listOf("Erotica"))
            val safeItem = createMetaPreview("s1", "Safe Movie")

            coEvery { homeRowLoader.load(any(), any()) } returns Outcome.Success(listOf(adultItem, safeItem))

            val viewModel = createViewModel()
            allAddonsFlow.value = listOf(addon)
            enabledAddonsFlow.value = listOf(addon)
            advanceUntilIdle()

            assertEquals(
                1,
                (
                    viewModel.uiState.value.rows[0]
                        .status as RowStatus.Loaded
                ).items.size,
            )

            familyModeFlow.value = false
            advanceUntilIdle()

            val items =
                (
                    viewModel.uiState.value.rows[0]
                        .status as RowStatus.Loaded
                ).items
            assertEquals(2, items.size)
        }

    @Test
    fun removeContinue_callsRepositoryOnce() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()

            viewModel.onEvent(HomeEvent.RemoveContinue("m1"))
            advanceUntilIdle()

            coVerify(exactly = 1) { progressRepository.removeFromContinueWatching("m1") }
        }

    @Test
    fun continueWatching_followsRepositoryFlow() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            val items =
                listOf(
                    ContinueWatchingItem(
                        metaId = "m1",
                        type = "movie",
                        name = "Movie 1",
                        poster = null,
                        videoId = "m1",
                        season = null,
                        episode = null,
                        positionMs = 100L,
                        durationMs = 200L,
                        updatedAt = 1000L,
                    ),
                )

            continueWatchingFlow.value = items
            advanceUntilIdle()

            assertEquals(items, viewModel.uiState.value.continueWatching)
            assertEquals(items, viewModel.continueWatching)
        }
}
