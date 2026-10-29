package com.nexus.stream.presentation.feature.settings

import com.nexus.stream.domain.model.Addon
import com.nexus.stream.domain.model.AddonManifest
import com.nexus.stream.domain.model.AddonSource
import com.nexus.stream.domain.model.CatalogSpec
import com.nexus.stream.domain.model.HomeRowConfig
import com.nexus.stream.domain.model.HomeRowSpec
import com.nexus.stream.domain.model.ManifestHints
import com.nexus.stream.domain.repository.AddonRepository
import com.nexus.stream.domain.repository.HomeRowConfigRepository
import com.nexus.stream.domain.usecase.BuildHomeRows
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
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
class HomeRowsSettingsViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val addonRepository: AddonRepository = mockk()
    private val activeAddonsFlow = MutableStateFlow<List<Addon>>(emptyList())
    private val fakeConfigRepo = FakeHomeRowConfigRepository()

    private class FakeHomeRowConfigRepository(
        initial: HomeRowConfig = HomeRowConfig(),
    ) : HomeRowConfigRepository {
        val state = MutableStateFlow(initial)
        override val config: Flow<HomeRowConfig> = state
        var resetCalls = 0

        override suspend fun update(transform: (HomeRowConfig) -> HomeRowConfig) {
            state.value = transform(state.value)
        }

        override suspend fun reset() {
            resetCalls++
            state.value = HomeRowConfig()
        }
    }

    private fun sampleAddon(
        id: String,
        catalogs: List<String>,
    ): Addon {
        val addonCatalogs =
            catalogs.map { catId ->
                CatalogSpec(
                    type = "movie",
                    id = catId,
                    name = catId.replaceFirstChar { it.uppercase() },
                    extras = emptyList(),
                )
            }
        return Addon(
            transportUrl = "https://example.com/$id/manifest.json",
            manifest =
                AddonManifest(
                    id = id,
                    name = id.replaceFirstChar { it.uppercase() },
                    version = "1.0.0",
                    description = "desc",
                    logo = null,
                    background = null,
                    catalogs = addonCatalogs,
                    resources = emptyList(),
                    types = listOf("movie"),
                    idPrefixes = null,
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
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { addonRepository.observeActive() } returns activeAddonsFlow
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): HomeRowsSettingsViewModel =
        HomeRowsSettingsViewModel(
            addonRepository = addonRepository,
            homeRowConfigRepository = fakeConfigRepo,
            buildHomeRows = BuildHomeRows(),
        )

    @Test
    fun HV1_state_listsAllRowsInConfiguredOrder_withHiddenFlags() =
        runTest(testDispatcher) {
            val addon = sampleAddon("addon1", listOf("a", "b", "c"))
            activeAddonsFlow.value = listOf(addon)
            val specA = "https://example.com/addon1/manifest.json|movie|a"
            val specB = "https://example.com/addon1/manifest.json|movie|b"
            val specC = "https://example.com/addon1/manifest.json|movie|c"

            fakeConfigRepo.state.value =
                HomeRowConfig(
                    order = listOf(specC, specA),
                    hidden = setOf(specA),
                )

            val viewModel = createViewModel()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(3, state.totalCount)
            assertEquals(1, state.hiddenCount)
            assertEquals(listOf(specC, specA, specB), state.rows.map { it.key })
            assertFalse(state.rows[0].hidden)
            assertTrue(state.rows[1].hidden)
            assertFalse(state.rows[2].hidden)
        }

    @Test
    fun HV2_moveUp_updatesTheConfig() =
        runTest(testDispatcher) {
            val addon = sampleAddon("addon1", listOf("a", "b", "c"))
            activeAddonsFlow.value = listOf(addon)
            val specB = "https://example.com/addon1/manifest.json|movie|b"
            val specA = "https://example.com/addon1/manifest.json|movie|a"
            val specC = "https://example.com/addon1/manifest.json|movie|c"

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onEvent(HomeRowsEvent.MoveUp(specB))
            advanceUntilIdle()

            assertEquals(listOf(specB, specA, specC), fakeConfigRepo.state.value.order)
            assertEquals(
                listOf(specB, specA, specC),
                viewModel.uiState.value.rows
                    .map { it.key },
            )
        }

    @Test
    fun HV3_moveDown_updatesTheConfig() =
        runTest(testDispatcher) {
            val addon = sampleAddon("addon1", listOf("a", "b", "c"))
            activeAddonsFlow.value = listOf(addon)
            val specA = "https://example.com/addon1/manifest.json|movie|a"
            val specB = "https://example.com/addon1/manifest.json|movie|b"
            val specC = "https://example.com/addon1/manifest.json|movie|c"

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onEvent(HomeRowsEvent.MoveDown(specA))
            advanceUntilIdle()

            assertEquals(listOf(specB, specA, specC), fakeConfigRepo.state.value.order)
        }

    @Test
    fun HV4_setHidden_updatesTheConfig() =
        runTest(testDispatcher) {
            val addon = sampleAddon("addon1", listOf("a", "b", "c"))
            activeAddonsFlow.value = listOf(addon)
            val specB = "https://example.com/addon1/manifest.json|movie|b"

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onEvent(HomeRowsEvent.SetHidden(specB, true))
            advanceUntilIdle()
            assertEquals(setOf(specB), fakeConfigRepo.state.value.hidden)

            viewModel.onEvent(HomeRowsEvent.SetHidden(specB, false))
            advanceUntilIdle()
            assertTrue(
                fakeConfigRepo.state.value.hidden
                    .isEmpty(),
            )
        }

    @Test
    fun HV5_requestReset_showsConfirm_thenResets_andDefaultDisablesIt() =
        runTest(testDispatcher) {
            fakeConfigRepo.state.value = HomeRowConfig(order = listOf("x"))
            val viewModel = createViewModel()
            advanceUntilIdle()
            assertFalse(viewModel.uiState.value.isDefault)

            viewModel.onEvent(HomeRowsEvent.RequestReset)
            assertTrue(viewModel.uiState.value.showResetConfirm)

            viewModel.onEvent(HomeRowsEvent.ConfirmReset)
            advanceUntilIdle()
            assertFalse(viewModel.uiState.value.showResetConfirm)
            assertEquals(1, fakeConfigRepo.resetCalls)
            assertTrue(viewModel.uiState.value.isDefault)
        }

    @Test
    fun HV6_dismissReset_doesNothing() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            viewModel.onEvent(HomeRowsEvent.RequestReset)
            assertTrue(viewModel.uiState.value.showResetConfirm)

            viewModel.onEvent(HomeRowsEvent.DismissReset)
            assertFalse(viewModel.uiState.value.showResetConfirm)
            assertEquals(0, fakeConfigRepo.resetCalls)
        }

    @Test
    fun HV7_noRows_showsTheEmptyState() =
        runTest(testDispatcher) {
            activeAddonsFlow.value = emptyList()
            val viewModel = createViewModel()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertTrue(state.rows.isEmpty())
            assertEquals(0, state.totalCount)
        }

    @Test
    fun HV8_addonSetChanges_updatesTheList() =
        runTest(testDispatcher) {
            val addon1 = sampleAddon("addon1", listOf("a"))
            activeAddonsFlow.value = listOf(addon1)

            val viewModel = createViewModel()
            advanceUntilIdle()
            assertEquals(1, viewModel.uiState.value.totalCount)

            val addon2 = sampleAddon("addon2", listOf("b"))
            activeAddonsFlow.value = listOf(addon1, addon2)
            advanceUntilIdle()
            assertEquals(2, viewModel.uiState.value.totalCount)

            activeAddonsFlow.value = emptyList()
            advanceUntilIdle()
            assertEquals(0, viewModel.uiState.value.totalCount)
        }

    @Test
    fun HV9_firstRowCannotMoveUp_lastRowCannotMoveDown() =
        runTest(testDispatcher) {
            val addon = sampleAddon("addon1", listOf("a", "b", "c"))
            activeAddonsFlow.value = listOf(addon)

            val viewModel = createViewModel()
            advanceUntilIdle()

            val rows = viewModel.uiState.value.rows
            assertEquals(3, rows.size)
            assertFalse(rows[0].canMoveUp)
            assertTrue(rows[0].canMoveDown)
            assertTrue(rows[1].canMoveUp)
            assertTrue(rows[1].canMoveDown)
            assertTrue(rows[2].canMoveUp)
            assertFalse(rows[2].canMoveDown)
        }

    @Test
    fun HV10_moveEvents_areIgnoredForUnknownKeys() =
        runTest(testDispatcher) {
            val addon = sampleAddon("addon1", listOf("a", "b", "c"))
            activeAddonsFlow.value = listOf(addon)

            val viewModel = createViewModel()
            advanceUntilIdle()

            val initialConfig = fakeConfigRepo.state.value
            viewModel.onEvent(HomeRowsEvent.MoveUp("unknown_X"))
            advanceUntilIdle()
            assertEquals(initialConfig, fakeConfigRepo.state.value)

            viewModel.onEvent(HomeRowsEvent.SetHidden("unknown_X", true))
            advanceUntilIdle()
            assertEquals(initialConfig, fakeConfigRepo.state.value)
        }
}
