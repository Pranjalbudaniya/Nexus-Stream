package com.nexus.stream.domain.model

import com.nexus.stream.data.remote.addon.AddonMappers
import com.nexus.stream.data.remote.addon.ManifestDto
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AddonCapabilitiesTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }

    private lateinit var cinemetaManifest: AddonManifest

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
    fun cinemeta_hasBoardCatalogs_yearAndLastVideosDoNotCountAsBoard() {
        // Cinemeta has board catalogs (top) and non-board catalogs
        // (like year or series last-videos with required extra)
        val boardCatalogs = cinemetaManifest.catalogs.filter { it.isBoardCatalog }
        assertTrue(boardCatalogs.isNotEmpty())

        val requiredExtraCatalog =
            CatalogSpec(
                type = "movie",
                id = "year",
                name = "By Year",
                extras = listOf(ExtraSpec("year", isRequired = true, options = emptyList())),
            )
        assertFalse(requiredExtraCatalog.isBoardCatalog)
    }

    @Test
    fun capabilities_enabledOnly_disabledAddonsIgnored() {
        val cinemetaAddon =
            Addon(
                transportUrl = "https://v3-cinemeta.strem.io/manifest.json",
                manifest = cinemetaManifest,
                enabled = false,
                position = 0,
                source = AddonSource.LOCAL,
                installedAt = 1000L,
            )

        val disabledCapabilities = listOf(cinemetaAddon).capabilities()
        assertFalse(disabledCapabilities.hasCatalogs)
        assertFalse(disabledCapabilities.hasStreams)

        val enabledCapabilities = listOf(cinemetaAddon.copy(enabled = true)).capabilities()
        assertTrue(enabledCapabilities.hasCatalogs)
        assertFalse(enabledCapabilities.hasStreams)
    }

    @Test
    fun capabilities_hasStreams_requiresStreamResource() {
        val streamManifest =
            cinemetaManifest.copy(
                resources = listOf(ResourceSpec("stream", null, null)),
                catalogs = emptyList(),
            )
        val streamAddon =
            Addon(
                transportUrl = "https://torrent.addon/manifest.json",
                manifest = streamManifest,
                enabled = true,
                position = 0,
                source = AddonSource.LOCAL,
                installedAt = 1000L,
            )

        val caps = listOf(streamAddon).capabilities()
        assertFalse(caps.hasCatalogs)
        assertTrue(caps.hasStreams)
    }

    @Test
    fun isUsable_trueForStandardResources_falseForAddonCatalogOnly() {
        val catalogManifest = cinemetaManifest.copy(resources = listOf(ResourceSpec("catalog", null, null)))
        val metaManifest = cinemetaManifest.copy(resources = listOf(ResourceSpec("meta", null, null)))
        val streamManifest = cinemetaManifest.copy(resources = listOf(ResourceSpec("stream", null, null)))
        val subtitlesManifest = cinemetaManifest.copy(resources = listOf(ResourceSpec("subtitles", null, null)))
        val addonCatalogOnly = cinemetaManifest.copy(resources = listOf(ResourceSpec("addon_catalog", null, null)))
        val emptyResources = cinemetaManifest.copy(resources = emptyList())

        assertTrue(catalogManifest.isUsable)
        assertTrue(metaManifest.isUsable)
        assertTrue(streamManifest.isUsable)
        assertTrue(subtitlesManifest.isUsable)
        assertFalse(addonCatalogOnly.isUsable)
        assertFalse(emptyResources.isUsable)
    }
}
