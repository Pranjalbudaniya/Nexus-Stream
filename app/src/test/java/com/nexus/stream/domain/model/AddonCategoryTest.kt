package com.nexus.stream.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AddonCategoryTest {
    private fun testManifest(
        catalogs: List<CatalogSpec> = emptyList(),
        resources: List<ResourceSpec> = emptyList(),
    ): AddonManifest =
        AddonManifest(
            id = "test.addon",
            version = "1.0.0",
            name = "Test Addon",
            description = "Description",
            logo = null,
            background = null,
            types = listOf("movie"),
            idPrefixes = null,
            resources = resources,
            catalogs = catalogs,
            behaviorHints =
                ManifestHints(
                    adult = false,
                    p2p = false,
                    configurable = false,
                    configurationRequired = false,
                ),
        )

    @Test
    fun `catalogs category rule`() {
        val manifest =
            testManifest(
                catalogs =
                    listOf(
                        CatalogSpec(type = "movie", id = "top", name = "Top", extras = emptyList()),
                    ),
            )
        val categories = categoriesOf(manifest)
        assertEquals(setOf(AddonCategory.CATALOGS), categories)
        assertEquals(AddonCategory.CATALOGS, primaryCategory(categories))
    }

    @Test
    fun `streams category rule`() {
        val manifest = testManifest(resources = listOf(ResourceSpec(name = "stream", types = null, idPrefixes = null)))
        val categories = categoriesOf(manifest)
        assertEquals(setOf(AddonCategory.STREAMS), categories)
        assertEquals(AddonCategory.STREAMS, primaryCategory(categories))
    }

    @Test
    fun `subtitles category rule`() {
        val manifest =
            testManifest(resources = listOf(ResourceSpec(name = "subtitles", types = null, idPrefixes = null)))
        val categories = categoriesOf(manifest)
        assertEquals(setOf(AddonCategory.SUBTITLES), categories)
        assertEquals(AddonCategory.SUBTITLES, primaryCategory(categories))
    }

    @Test
    fun `meta-only is OTHER and usable`() {
        val manifest = testManifest(resources = listOf(ResourceSpec(name = "meta", types = null, idPrefixes = null)))
        val categories = categoriesOf(manifest)
        assertEquals(setOf(AddonCategory.OTHER), categories)
        assertEquals(AddonCategory.OTHER, primaryCategory(categories))
        assertTrue(manifest.isUsable)
    }

    @Test
    fun `addon_catalog-only is OTHER and not usable`() {
        val manifest =
            testManifest(resources = listOf(ResourceSpec(name = "addon_catalog", types = null, idPrefixes = null)))
        val categories = categoriesOf(manifest)
        assertEquals(setOf(AddonCategory.OTHER), categories)
        assertEquals(AddonCategory.OTHER, primaryCategory(categories))
        assertFalse(manifest.isUsable)
    }

    @Test
    fun `catalogs and streams has both and primary CATALOGS`() {
        val manifest =
            testManifest(
                catalogs =
                    listOf(
                        CatalogSpec(type = "movie", id = "top", name = "Top", extras = emptyList()),
                    ),
                resources = listOf(ResourceSpec(name = "stream", types = null, idPrefixes = null)),
            )
        val categories = categoriesOf(manifest)
        assertEquals(setOf(AddonCategory.CATALOGS, AddonCategory.STREAMS), categories)
        assertEquals(AddonCategory.CATALOGS, primaryCategory(categories))
    }

    @Test
    fun `manifest without resources returns OTHER`() {
        val manifest = testManifest()
        val categories = categoriesOf(manifest)
        assertEquals(setOf(AddonCategory.OTHER), categories)
        assertEquals(AddonCategory.OTHER, primaryCategory(categories))
    }
}
