package com.nexus.stream.presentation.feature.home

import com.nexus.stream.data.remote.addon.HomeRowLoader
import com.nexus.stream.domain.model.Addon
import com.nexus.stream.domain.model.AddonManifest
import com.nexus.stream.domain.model.AddonSource
import com.nexus.stream.domain.model.AppError
import com.nexus.stream.domain.model.CatalogSpec
import com.nexus.stream.domain.model.ManifestHints
import com.nexus.stream.domain.model.MetaPreview
import com.nexus.stream.domain.model.Outcome
import com.nexus.stream.domain.model.PosterShape
import com.nexus.stream.domain.repository.AddonRepository
import com.nexus.stream.domain.usecase.BuildHomeRows
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
    private val buildHomeRows = BuildHomeRows()

    private val allAddonsFlow = MutableStateFlow<List<Addon>>(emptyList())
    private val enabledAddonsFlow = MutableStateFlow<List<Addon>>(emptyList())

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { addonRepository.observeAll() } returns allAddonsFlow
        every { addonRepository.observeEnabled() } returns enabledAddonsFlow
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
            buildHomeRows = buildHomeRows,
            homeRowLoader = homeRowLoader,
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
