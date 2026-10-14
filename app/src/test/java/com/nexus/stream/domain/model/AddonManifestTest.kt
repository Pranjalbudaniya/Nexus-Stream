package com.nexus.stream.domain.model

import com.nexus.stream.data.remote.addon.AddonMappers
import com.nexus.stream.data.remote.addon.ManifestDto
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AddonManifestTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }

    private lateinit var legacyManifest: AddonManifest
    private lateinit var objectManifest: AddonManifest

    @Before
    fun setUp() {
        val legacyJson =
            javaClass.classLoader!!
                .getResourceAsStream("fixtures/manifest_legacy.json")!!
                .bufferedReader()
                .readText()
        val legacyDto = json.decodeFromString<ManifestDto>(legacyJson)
        legacyManifest = AddonMappers.mapManifest(legacyDto, json)

        val objectJson =
            javaClass.classLoader!!
                .getResourceAsStream("fixtures/manifest_object_resources.json")!!
                .bufferedReader()
                .readText()
        val objectDto = json.decodeFromString<ManifestDto>(objectJson)
        objectManifest = AddonMappers.mapManifest(objectDto, json)
    }

    @Test
    fun supports_stringResourceInheritsTypesAndPrefixes() {
        // legacyManifest resources: ["catalog", "meta", "stream"], types: ["movie", "series"], idPrefixes: ["tt"]
        assertTrue(legacyManifest.supports("meta", "movie", "tt12345"))
        assertTrue(legacyManifest.supports("meta", "series", "tt67890"))
        // Type mismatch
        assertFalse(legacyManifest.supports("meta", "channel", "tt12345"))
        // Prefix mismatch
        assertFalse(legacyManifest.supports("meta", "movie", "custom:123"))
        // Case-sensitive prefix
        assertFalse(legacyManifest.supports("meta", "movie", "TT12345"))
        // Unknown resource
        assertFalse(legacyManifest.supports("subtitles", "movie", "tt12345"))
    }

    @Test
    fun supports_objectResourceOverridesTypesAndPrefixes() {
        // objectManifest meta has types: ["movie", "series"], idPrefixes: ["custom:"]
        assertTrue(objectManifest.supports("meta", "movie", "custom:123"))
        assertFalse(objectManifest.supports("meta", "movie", "tt123"))

        // objectManifest stream has types: ["movie"], idPrefixes: null -> falls back to top-level idPrefixes ["tt"]
        assertTrue(objectManifest.supports("stream", "movie", "tt12345"))
        assertFalse(objectManifest.supports("stream", "movie", "any_id_works"))
        assertFalse(objectManifest.supports("stream", "series", "tt12345"))
    }

    @Test
    fun supports_catalogFollowsCatalogsIsNotEmpty() {
        assertTrue(legacyManifest.supports("catalog", "movie", "anything"))
        val emptyCatalogsManifest = legacyManifest.copy(catalogs = emptyList())
        assertFalse(emptyCatalogsManifest.supports("catalog", "movie", "anything"))
    }

    @Test
    fun extrasHelpers_onLegacyManifest() {
        val catalog = legacyManifest.catalogs.first()
        // Top Movies has extraSupported: ["search", "genre", "skip"], extraRequired: ["genre"]
        assertFalse(catalog.isBoardCatalog)
        assertTrue(catalog.supportsSearch)
        assertFalse(catalog.isSearchOnly)
        assertTrue(catalog.supportsSkip)
        assertTrue(catalog.genreOptions.isEmpty())
    }

    @Test
    fun extrasHelpers_onModernManifest() {
        val catalog = objectManifest.catalogs.first()
        // Featured has search with isRequired: false
        assertTrue(catalog.isBoardCatalog)
        assertTrue(catalog.supportsSearch)
        assertFalse(catalog.isSearchOnly)
        assertFalse(catalog.supportsSkip)
        assertEquals(emptyList<String>(), catalog.genreOptions)

        val searchOnlyCatalog =
            catalog.copy(
                extras = listOf(ExtraSpec("search", isRequired = true, options = emptyList())),
            )
        assertFalse(searchOnlyCatalog.isBoardCatalog)
        assertTrue(searchOnlyCatalog.isSearchOnly)

        val genreCatalog =
            catalog.copy(
                extras = listOf(ExtraSpec("genre", isRequired = false, options = listOf("Action", "Comedy"))),
            )
        assertEquals(listOf("Action", "Comedy"), genreCatalog.genreOptions)
    }
}
