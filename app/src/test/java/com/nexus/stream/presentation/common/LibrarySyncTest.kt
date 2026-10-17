package com.nexus.stream.presentation.common

import androidx.lifecycle.SavedStateHandle
import com.nexus.stream.data.local.db.TransactionRunner
import com.nexus.stream.data.local.db.dao.FakeLibraryDao
import com.nexus.stream.data.local.db.dao.FakeProgressDao
import com.nexus.stream.data.remote.addon.HomeRowLoader
import com.nexus.stream.data.repository.LibraryRepositoryImpl
import com.nexus.stream.data.repository.ProgressRepositoryImpl
import com.nexus.stream.domain.model.Addon
import com.nexus.stream.domain.model.AddonManifest
import com.nexus.stream.domain.model.AddonSource
import com.nexus.stream.domain.model.CatalogSpec
import com.nexus.stream.domain.model.ListStatus
import com.nexus.stream.domain.model.ManifestHints
import com.nexus.stream.domain.model.MetaDetail
import com.nexus.stream.domain.model.Outcome
import com.nexus.stream.domain.model.PosterMark
import com.nexus.stream.domain.model.PosterShape
import com.nexus.stream.domain.model.toPreview
import com.nexus.stream.domain.repository.AddonRepository
import com.nexus.stream.domain.repository.ContentPrefsRepository
import com.nexus.stream.domain.repository.MetaRepository
import com.nexus.stream.domain.repository.MetaResult
import com.nexus.stream.domain.repository.MetaSource
import com.nexus.stream.domain.usecase.ContinueWatchingRules
import com.nexus.stream.domain.usecase.EnsureDefaultAddons
import com.nexus.stream.presentation.feature.detail.DetailEvent
import com.nexus.stream.presentation.feature.detail.DetailUiState
import com.nexus.stream.presentation.feature.detail.DetailViewModel
import com.nexus.stream.presentation.feature.home.HomeEvent
import com.nexus.stream.presentation.feature.home.HomeViewModel
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibrarySyncTest {
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private var repoScope = CoroutineScope(SupervisorJob() + testDispatcher)

    private val passThroughRunner =
        object : TransactionRunner {
            override suspend fun <T> run(block: suspend () -> T): T = block()
        }

    private lateinit var fakeLibraryDao: FakeLibraryDao
    private lateinit var fakeProgressDao: FakeProgressDao
    private lateinit var libraryRepository: LibraryRepositoryImpl
    private lateinit var progressRepository: ProgressRepositoryImpl
    private lateinit var metaRepository: MetaRepository

    private val addonRepository: AddonRepository = mockk()
    private val contentPrefsRepository: ContentPrefsRepository = mockk()
    private val ensureDefaultAddons: EnsureDefaultAddons = mockk()
    private val homeRowLoader: HomeRowLoader = mockk(relaxed = true)

    private val familyModeFlow = MutableStateFlow(true)
    private val allAddonsFlow = MutableStateFlow<List<Addon>>(emptyList())

    private val testMovie =
        MetaDetail(
            id = "tt1",
            type = "movie",
            name = "Test Movie",
            poster = "https://poster.jpg",
            posterShape = PosterShape.POSTER,
            background = null,
            logo = null,
            description = "Description",
            releaseInfo = "2024",
            released = "2024-01-01",
            runtime = "120 min",
            imdbRating = "8.0",
            genres = listOf("Action"),
            cast = listOf("Actor"),
            directors = listOf("Director"),
            country = null,
            language = null,
            website = null,
            links = emptyList(),
            trailers = emptyList(),
            videos = emptyList(),
            defaultVideoId = null,
        )

    private val testPreview = testMovie.toPreview()

    private fun createAddon(name: String = "TestAddon"): Addon =
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
                    types = listOf("movie"),
                    idPrefixes = null,
                    resources = emptyList(),
                    catalogs =
                        listOf(
                            CatalogSpec(
                                type = "movie",
                                id = "top",
                                name = "Top",
                                extras = emptyList(),
                            ),
                        ),
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
            installedAt = 1000L,
        )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        fakeLibraryDao = FakeLibraryDao()
        fakeProgressDao = FakeProgressDao()

        repoScope = CoroutineScope(SupervisorJob() + testDispatcher)
        libraryRepository =
            LibraryRepositoryImpl(
                libraryDao = fakeLibraryDao,
                transactionRunner = passThroughRunner,
                dispatcher = testDispatcher,
                scope = repoScope,
            )

        progressRepository =
            ProgressRepositoryImpl(
                progressDao = fakeProgressDao,
                libraryDao = fakeLibraryDao,
                transactionRunner = passThroughRunner,
                continueWatchingRules = ContinueWatchingRules(),
                dispatcher = testDispatcher,
            )

        metaRepository = mockk()
        coEvery { metaRepository.getMeta("movie", "tt1", any()) } returns
            Outcome.Success(MetaResult(meta = testMovie, source = MetaSource.FRESH))

        allAddonsFlow.value = listOf(createAddon())

        every { addonRepository.observeAll() } returns allAddonsFlow.asStateFlow()
        every { addonRepository.observeActive() } returns allAddonsFlow.asStateFlow()
        every { addonRepository.observeEnabled() } returns allAddonsFlow.asStateFlow()
        every { contentPrefsRepository.familyMode } returns familyModeFlow.asStateFlow()
        coEvery { ensureDefaultAddons() } returns Outcome.Success(Unit)
    }

    @After
    fun tearDown() {
        repoScope.cancel()
        Dispatchers.resetMain()
    }

    private fun createHomeViewModel(): HomeViewModel =
        HomeViewModel(
            addonRepository = addonRepository,
            ensureDefaultAddons = ensureDefaultAddons,
            homeRowLoader = homeRowLoader,
            libraryRepository = libraryRepository,
            progressRepository = progressRepository,
            contentPrefsRepository = contentPrefsRepository,
            dispatcher = testDispatcher,
        )

    private fun createDetailViewModel(): DetailViewModel =
        DetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("type" to "movie", "id" to "tt1")),
            metaRepository = metaRepository,
            libraryRepository = libraryRepository,
            progressRepository = progressRepository,
            clock = { 1_000_000L },
        )

    private fun createPosterActionsViewModel(): PosterActionsViewModel =
        PosterActionsViewModel(
            metaRepository = metaRepository,
            libraryRepository = libraryRepository,
            progressRepository = progressRepository,
        )

    @Test
    fun scenarioA_addFromHero_thenDetailShowsStatus() =
        testScope.runTest {
            val homeVm = createHomeViewModel()
            advanceUntilIdle()

            val detailVm = createDetailViewModel()
            advanceUntilIdle()

            // Initially not in watchlist
            val initialDetail = detailVm.uiState.value as DetailUiState.Content
            assertFalse(initialDetail.inWatchlist)
            assertNull(initialDetail.listStatus)

            // Add from hero
            homeVm.onEvent(HomeEvent.ToggleWatchlist(testPreview))
            advanceUntilIdle()

            // Detail shows PLAN_TO_WATCH
            val updatedDetail = detailVm.uiState.value as DetailUiState.Content
            assertTrue(updatedDetail.inWatchlist)
            assertEquals(ListStatus.PLAN_TO_WATCH, updatedDetail.listStatus)
        }

    @Test
    fun scenarioB_changeStatusInDetail_heroTickAndPosterBadgesAndSheetFollowImmediately() =
        testScope.runTest {
            val homeVm = createHomeViewModel()
            val detailVm = createDetailViewModel()
            val posterVm = createPosterActionsViewModel()
            advanceUntilIdle()

            // Change status in Detail to WATCHING
            detailVm.onEvent(DetailEvent.SetListStatus(ListStatus.WATCHING))
            advanceUntilIdle()

            // Detail reflects WATCHING
            val detailContent = detailVm.uiState.value as DetailUiState.Content
            assertEquals(ListStatus.WATCHING, detailContent.listStatus)
            assertTrue(detailContent.inWatchlist)

            // Hero tick and watchlist state
            assertTrue(
                homeVm.uiState.value.watchlistIds
                    .contains("tt1"),
            )
            assertEquals(ListStatus.WATCHING, homeVm.uiState.value.listStatuses["tt1"])

            // Poster mark badge shows WATCHING
            assertEquals(PosterMark.WATCHING, homeVm.uiState.value.posterMarks["tt1"])

            // Poster sheet receives status
            assertEquals(ListStatus.WATCHING, posterVm.listStatuses.value["tt1"])
        }

    @Test
    fun scenarioC_removeFromPosterSheet_heroReturnsToPlusAndDetailReturnsToWatchlist() =
        testScope.runTest {
            val homeVm = createHomeViewModel()
            val detailVm = createDetailViewModel()
            val posterVm = createPosterActionsViewModel()
            advanceUntilIdle()

            // First add as WATCHING
            detailVm.onEvent(DetailEvent.SetListStatus(ListStatus.WATCHING))
            advanceUntilIdle()

            // Remove from poster sheet
            posterVm.setStatus(testPreview, null)
            advanceUntilIdle()

            // Hero returns to plus
            assertFalse(
                homeVm.uiState.value.watchlistIds
                    .contains("tt1"),
            )
            assertNull(homeVm.uiState.value.listStatuses["tt1"])
            assertNull(homeVm.uiState.value.posterMarks["tt1"])

            // Detail returns to unlisted
            val detailContent = detailVm.uiState.value as DetailUiState.Content
            assertFalse(detailContent.inWatchlist)
            assertNull(detailContent.listStatus)
        }

    @Test
    fun scenarioD_addFromPosterSheet_thenDetailUpdatesWhenOpened() =
        testScope.runTest {
            val posterVm = createPosterActionsViewModel()
            advanceUntilIdle()

            // Add from poster sheet as COMPLETED
            posterVm.setStatus(testPreview, ListStatus.COMPLETED)
            advanceUntilIdle()

            // Open Detail
            val detailVm = createDetailViewModel()
            advanceUntilIdle()

            val detailContent = detailVm.uiState.value as DetailUiState.Content
            assertTrue(detailContent.inWatchlist)
            assertEquals(ListStatus.COMPLETED, detailContent.listStatus)
        }

    @Test
    fun scenarioE_sameTitleFromRowHeroAndContinue_givesSameState() =
        testScope.runTest {
            val homeVm = createHomeViewModel()
            val posterVm = createPosterActionsViewModel()
            advanceUntilIdle()

            posterVm.setStatus(testPreview, ListStatus.ON_HOLD)
            advanceUntilIdle()

            // State is consistent across hero, row poster marks, and poster actions
            assertTrue(
                homeVm.uiState.value.watchlistIds
                    .contains("tt1"),
            )
            assertEquals(ListStatus.ON_HOLD, homeVm.uiState.value.listStatuses["tt1"])
            assertEquals(ListStatus.ON_HOLD, posterVm.listStatuses.value["tt1"])
        }

    @Test
    fun scenarioF_afterProcessRecreation_stateComesFromDatabase() =
        testScope.runTest {
            // Write to database via first repository
            libraryRepository.setStatus(testPreview, ListStatus.DROPPED)
            advanceUntilIdle()

            // Simulate process recreation: new repository and viewmodels reading from fakeLibraryDao
            val recreatedScope = CoroutineScope(SupervisorJob() + testDispatcher)
            val recreatedRepo =
                LibraryRepositoryImpl(
                    libraryDao = fakeLibraryDao,
                    transactionRunner = passThroughRunner,
                    dispatcher = testDispatcher,
                    scope = recreatedScope,
                )
            advanceUntilIdle()

            val recreatedDetail =
                DetailViewModel(
                    savedStateHandle = SavedStateHandle(mapOf("type" to "movie", "id" to "tt1")),
                    metaRepository = metaRepository,
                    libraryRepository = recreatedRepo,
                    progressRepository = progressRepository,
                    clock = { 1_000_000L },
                )
            advanceUntilIdle()

            val detailContent = recreatedDetail.uiState.value as DetailUiState.Content
            assertTrue(detailContent.inWatchlist)
            assertEquals(ListStatus.DROPPED, detailContent.listStatus)
            recreatedScope.cancel()
        }
}
