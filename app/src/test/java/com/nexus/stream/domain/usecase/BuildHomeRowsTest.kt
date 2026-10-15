package com.nexus.stream.domain.usecase

import com.nexus.stream.data.remote.addon.AddonMappers
import com.nexus.stream.data.remote.addon.ManifestDto
import com.nexus.stream.domain.model.Addon
import com.nexus.stream.domain.model.AddonManifest
import com.nexus.stream.domain.model.AddonSource
import com.nexus.stream.domain.model.CatalogSpec
import com.nexus.stream.domain.model.ExtraSpec
import com.nexus.stream.domain.model.HomeRowSpec
import com.nexus.stream.domain.model.ManifestHints
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BuildHomeRowsTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    private lateinit var cinemetaManifest: AddonManifest
    private val buildHomeRows = BuildHomeRows()

    @Before
    fun setUp() {
        val cinemetaJson =
            javaClass.classLoader!!
                .getResourceAsStream("fixtures/cinemeta_manifest.json")!!
                .bufferedReader()
                .readText()
        val dto = json.decodeFromString<ManifestDto>(cinemetaJson)
        cinemetaManifest = AddonMappers.mapManifest(dto, json)
    }

    @Test
    fun cinemetaManifest_producesExactlyFourRowsInManifestOrder() {
        val addon =
            Addon(
                transportUrl = "https://v3-cinemeta.strem.io/manifest.json",
                manifest = cinemetaManifest,
                enabled = true,
                position = 0,
                source = AddonSource.LOCAL,
                installedAt = 0L,
            )

        val rows = buildHomeRows(listOf(addon))

        assertEquals(4, rows.size)
        assertEquals("Popular - Movies", rows[0].title)
        assertEquals("Popular - Series", rows[1].title)
        assertEquals("Featured - Movies", rows[2].title)
        assertEquals("Featured - Series", rows[3].title)

        assertEquals("https://v3-cinemeta.strem.io/manifest.json|movie|top", rows[0].key)
        assertEquals("https://v3-cinemeta.strem.io/manifest.json|series|top", rows[1].key)
        assertEquals("https://v3-cinemeta.strem.io/manifest.json|movie|imdbRating", rows[2].key)
        assertEquals("https://v3-cinemeta.strem.io/manifest.json|series|imdbRating", rows[3].key)
    }

    @Test
    fun nonBoardCatalogs_areExcluded() {
        val addon =
            Addon(
                transportUrl = "https://v3-cinemeta.strem.io/manifest.json",
                manifest = cinemetaManifest,
                enabled = true,
                position = 0,
                source = AddonSource.LOCAL,
                installedAt = 0L,
            )

        val rows = buildHomeRows(listOf(addon))

        assertTrue(rows.none { it.catalogId == "year" })
        assertTrue(rows.none { it.catalogId == "last-videos" })
        assertTrue(rows.none { it.catalogId == "calendar-videos" })
    }

    @Test
    fun disabledAddons_areExcluded() {
        val addon =
            Addon(
                transportUrl = "https://v3-cinemeta.strem.io/manifest.json",
                manifest = cinemetaManifest,
                enabled = false,
                position = 0,
                source = AddonSource.LOCAL,
                installedAt = 0L,
            )

        val rows = buildHomeRows(listOf(addon))
        assertTrue(rows.isEmpty())
    }

    @Test
    fun twoAddons_orderedByPosition() {
        val addon1 =
            createDummyAddon(
                transportUrl = "https://addon1.com/manifest.json",
                name = "Addon 1",
                position = 2,
                catalog = CatalogSpec("movie", "cat_a", "Catalog A", emptyList()),
            )
        val addon2 =
            createDummyAddon(
                transportUrl = "https://addon2.com/manifest.json",
                name = "Addon 2",
                position = 1,
                catalog = CatalogSpec("movie", "cat_b", "Catalog B", emptyList()),
            )

        val rows = buildHomeRows(listOf(addon1, addon2))

        assertEquals(2, rows.size)
        assertEquals("Catalog B - Movies", rows[0].title)
        assertEquals("Catalog A - Movies", rows[1].title)
    }

    @Test
    fun duplicateTitleAcrossAddons_disambiguatedWithAddonName() {
        val addon1 =
            createDummyAddon(
                transportUrl = "https://addon1.com/manifest.json",
                name = "Cinemeta",
                position = 0,
                catalog = CatalogSpec("movie", "top1", "Popular", emptyList()),
            )
        val addon2 =
            createDummyAddon(
                transportUrl = "https://addon2.com/manifest.json",
                name = "CyberFlix",
                position = 1,
                catalog = CatalogSpec("movie", "top2", "Popular", emptyList()),
            )

        val rows = buildHomeRows(listOf(addon1, addon2))

        assertEquals(2, rows.size)
        assertEquals("Popular - Movies (Cinemeta)", rows[0].title)
        assertEquals("Popular - Movies (CyberFlix)", rows[1].title)
    }

    @Test
    fun blankCatalogName_fallsBackToCatalogId() {
        val addon =
            createDummyAddon(
                transportUrl = "https://addon.com/manifest.json",
                name = "Addon",
                position = 0,
                catalog = CatalogSpec("movie", "trending_id", "   ", emptyList()),
            )

        val rows = buildHomeRows(listOf(addon))

        assertEquals(1, rows.size)
        assertEquals("trending_id - Movies", rows[0].title)
    }

    @Test
    fun unknownType_isCapitalized() {
        val addon =
            createDummyAddon(
                transportUrl = "https://addon.com/manifest.json",
                name = "AnimeAddon",
                position = 0,
                catalog = CatalogSpec("anime", "top", "Top", emptyList()),
            )

        val rows = buildHomeRows(listOf(addon))

        assertEquals(1, rows.size)
        assertEquals("Top - Anime", rows[0].title)
    }

    @Test
    fun knownTypes_areFormattedProperly() {
        val types =
            listOf(
                "channel" to "Channels",
                "tv" to "TV",
                "movie" to "Movies",
                "series" to "Series",
            )
        val catalogs =
            types.mapIndexed { index, (type, _) ->
                CatalogSpec(
                    type = type,
                    id = "id_$index",
                    name = "Catalog",
                    extras = emptyList(),
                )
            }
        val addon =
            Addon(
                transportUrl = "https://addon.com/manifest.json",
                manifest =
                    AddonManifest(
                        id = "test",
                        version = "1.0",
                        name = "Test",
                        description = "",
                        logo = null,
                        background = null,
                        types = emptyList(),
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

        val rows = buildHomeRows(listOf(addon))

        assertEquals("Catalog - Channels", rows[0].title)
        assertEquals("Catalog - TV", rows[1].title)
        assertEquals("Catalog - Movies", rows[2].title)
        assertEquals("Catalog - Series", rows[3].title)
    }

    @Test
    fun keysAreUniqueAndStableAcrossCalls() {
        val addon =
            Addon(
                transportUrl = "https://v3-cinemeta.strem.io/manifest.json",
                manifest = cinemetaManifest,
                enabled = true,
                position = 0,
                source = AddonSource.LOCAL,
                installedAt = 0L,
            )

        val rows1 = buildHomeRows(listOf(addon))
        val rows2 = buildHomeRows(listOf(addon))

        assertEquals(rows1.map { it.key }, rows2.map { it.key })
        assertEquals(rows1.map { it.key }.distinct().size, rows1.size)
    }

    private fun createDummyAddon(
        transportUrl: String,
        name: String,
        position: Int,
        catalog: CatalogSpec,
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
                    types = listOf(catalog.type),
                    idPrefixes = null,
                    resources = emptyList(),
                    catalogs = listOf(catalog),
                    behaviorHints =
                        ManifestHints(
                            adult = false,
                            p2p = false,
                            configurable = false,
                            configurationRequired = false,
                        ),
                ),
            enabled = true,
            position = position,
            source = AddonSource.LOCAL,
            installedAt = 0L,
        )
}
