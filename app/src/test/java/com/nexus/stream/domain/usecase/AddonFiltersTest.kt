package com.nexus.stream.domain.usecase

import com.nexus.stream.domain.model.Addon
import com.nexus.stream.domain.model.AddonCategory
import com.nexus.stream.domain.model.AddonManifest
import com.nexus.stream.domain.model.CatalogSpec
import com.nexus.stream.domain.model.ManifestHints
import com.nexus.stream.domain.model.ResourceSpec
import com.nexus.stream.domain.model.StoreAddon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AddonFiltersTest {
    private fun testAddon(
        id: String,
        name: String,
        desc: String = "",
        url: String = "https://example.com/manifest.json",
    ): Addon =
        Addon(
            transportUrl = url,
            manifest =
                AddonManifest(
                    id = id,
                    version = "1.0.0",
                    name = name,
                    description = desc,
                    logo = null,
                    background = null,
                    types = listOf("movie"),
                    idPrefixes = null,
                    resources = emptyList(),
                    catalogs = emptyList(),
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
            source = com.nexus.stream.domain.model.AddonSource.LOCAL,
            installedAt = 0L,
        )

    private fun testCapabilitiesAddon(
        id: String,
        name: String,
        catalogs: List<CatalogSpec> = emptyList(),
        resources: List<ResourceSpec> = emptyList(),
    ): Addon =
        Addon(
            transportUrl = "https://example.com/manifest.json",
            manifest =
                AddonManifest(
                    id = id,
                    version = "1.0.0",
                    name = name,
                    description = "",
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
                ),
            enabled = true,
            position = 0,
            source = com.nexus.stream.domain.model.AddonSource.LOCAL,
            installedAt = 0L,
        )

    private fun testStoreAddon(
        url: String,
        name: String,
        desc: String = "",
        categories: Set<AddonCategory> = setOf(AddonCategory.CATALOGS),
        isAdult: Boolean = false,
    ): StoreAddon =
        StoreAddon(
            transportUrl = url,
            name = name,
            version = "1.0.0",
            description = desc,
            logo = null,
            types = listOf("movie"),
            resources = emptySet(),
            categories = categories,
            primary = categories.firstOrNull() ?: AddonCategory.OTHER,
            isAdult = isAdult,
            p2p = false,
            configurable = false,
            usable = true,
            hasDetails = true,
        )

    @Test
    fun `toDisplayHost extracts host correctly`() {
        assertEquals("cinemeta.strem.io", "https://cinemeta.strem.io/manifest.json".toDisplayHost())
        assertEquals("localhost", "http://localhost:8080/manifest.json".toDisplayHost())
        assertEquals("torrentio.strem.fun", "stremio://torrentio.strem.fun/manifest.json".toDisplayHost())
    }

    @Test
    fun `filterInstalled search matches name description and host with trim and case-insensitivity`() {
        val addons =
            listOf(
                testAddon(
                    id = "cinemeta",
                    name = "Cinemeta",
                    desc = "Official catalog addon",
                    url = "https://v3-cinemeta.strem.io/manifest.json",
                ),
                testAddon(
                    id = "torrentio",
                    name = "Torrentio",
                    desc = "Torrent streams provider",
                    url = "https://torrentio.strem.fun/manifest.json",
                ),
                testAddon(
                    id = "opensubtitles",
                    name = "OpenSubtitles",
                    desc = "Multi-language subtitles",
                    url = "https://subtitles.strem.io/manifest.json",
                ),
            )

        // Name search (case and trim)
        val byName = AddonFilters.filterInstalled(addons, "  CINE  ", null)
        assertEquals(1, byName.size)
        assertEquals("Cinemeta", byName[0].manifest.name)

        // Description search
        val byDesc = AddonFilters.filterInstalled(addons, "provider", null)
        assertEquals(1, byDesc.size)
        assertEquals("Torrentio", byDesc[0].manifest.name)

        // Host search
        val byHost = AddonFilters.filterInstalled(addons, "strem.fun", null)
        assertEquals(1, byHost.size)
        assertEquals("Torrentio", byHost[0].manifest.name)

        // No match
        val noMatch = AddonFilters.filterInstalled(addons, "nonexistent", null)
        assertTrue(noMatch.isEmpty())
    }

    @Test
    fun `filterInstalled category filter matches if addon contains category`() {
        val catalogAndStream =
            testCapabilitiesAddon(
                id = "multi",
                name = "Multi",
                catalogs = listOf(CatalogSpec("movie", "top", "Top", emptyList())),
                resources = listOf(ResourceSpec("stream", null, null)),
            )
        val subOnly =
            testCapabilitiesAddon(
                id = "sub",
                name = "Sub",
                resources = listOf(ResourceSpec("subtitles", null, null)),
            )
        val addons = listOf(catalogAndStream, subOnly)

        // Catalogs filter matches multi
        val catalogs = AddonFilters.filterInstalled(addons, "", AddonCategory.CATALOGS)
        assertEquals(listOf(catalogAndStream), catalogs)

        // Streams filter also matches multi
        val streams = AddonFilters.filterInstalled(addons, "", AddonCategory.STREAMS)
        assertEquals(listOf(catalogAndStream), streams)

        // Subtitles matches subOnly
        val subs = AddonFilters.filterInstalled(addons, "", AddonCategory.SUBTITLES)
        assertEquals(listOf(subOnly), subs)

        // Null category returns all
        val all = AddonFilters.filterInstalled(addons, "", null)
        assertEquals(addons, all)
    }

    @Test
    fun `groupBySection orders sections, places each addon once under primary, and omits empty sections`() {
        val multiCatalogStream =
            testCapabilitiesAddon(
                id = "multi",
                name = "Multi (Catalogs + Streams)",
                catalogs = listOf(CatalogSpec("movie", "top", "Top", emptyList())),
                resources = listOf(ResourceSpec("stream", null, null)),
            )
        val pureStream =
            testCapabilitiesAddon(
                id = "stream",
                name = "Pure Stream",
                resources = listOf(ResourceSpec("stream", null, null)),
            )
        val otherAddon =
            testCapabilitiesAddon(
                id = "other",
                name = "Meta Addon",
                resources = listOf(ResourceSpec("meta", null, null)),
            )
        // Note: No SUBTITLES addon in the list
        val addons = listOf(multiCatalogStream, pureStream, otherAddon)

        val sections = AddonFilters.groupBySection(addons)

        // Expected sections: CATALOGS, STREAMS, OTHER (SUBTITLES omitted)
        assertEquals(3, sections.size)
        assertEquals(AddonCategory.CATALOGS, sections[0].first)
        assertEquals(AddonCategory.STREAMS, sections[1].first)
        assertEquals(AddonCategory.OTHER, sections[2].first)

        // Multi addon is only under CATALOGS (its primary category), not under STREAMS
        assertEquals(listOf(multiCatalogStream), sections[0].second)
        assertEquals(listOf(pureStream), sections[1].second)
        assertEquals(listOf(otherAddon), sections[2].second)
    }

    @Test
    fun `categoryCounts counts an addon toward every category it has`() {
        val multi =
            testCapabilitiesAddon(
                id = "multi",
                name = "Multi",
                catalogs = listOf(CatalogSpec("movie", "top", "Top", emptyList())),
                resources = listOf(ResourceSpec("stream", null, null)),
            )
        val stream =
            testCapabilitiesAddon(
                id = "stream",
                name = "Stream",
                resources = listOf(ResourceSpec("stream", null, null)),
            )
        val addons = listOf(multi, stream)

        val counts = AddonFilters.categoryCounts(addons)
        assertEquals(1, counts[AddonCategory.CATALOGS])
        assertEquals(2, counts[AddonCategory.STREAMS])
        assertEquals(null, counts[AddonCategory.SUBTITLES])
    }

    @Test
    fun `filterStore with Family mode on and off and installed urls`() {
        val regular =
            testStoreAddon(
                url = "https://regular.com/manifest.json",
                name = "Regular Addon",
                desc = "Safe content",
                categories = setOf(AddonCategory.CATALOGS),
            )
        val adultFlagged =
            testStoreAddon(
                url = "https://adult.com/manifest.json",
                name = "Adult Addon",
                desc = "Adult streams",
                categories = setOf(AddonCategory.STREAMS),
                isAdult = true,
            )
        val adultText =
            testStoreAddon(
                url = "https://nsfw.com/manifest.json",
                name = "NSFW Videos",
                desc = "xxx movies",
                categories = setOf(AddonCategory.CATALOGS),
                isAdult = false,
            )

        val entries = listOf(regular, adultFlagged, adultText)
        val installedUrls = setOf("https://regular.com/manifest.json")

        // Family mode ON: drops adultFlagged and adultText
        val familyOn =
            AddonFilters.filterStore(
                entries = entries,
                query = "",
                category = null,
                familyMode = true,
                installedUrls = installedUrls,
            )
        assertEquals(1, familyOn.size)
        assertEquals("Regular Addon", familyOn[0].name)
        assertTrue(familyOn[0].isInstalled)

        // Family mode OFF: keeps all, sets isInstalled correctly
        val familyOff =
            AddonFilters.filterStore(
                entries = entries,
                query = "",
                category = null,
                familyMode = false,
                installedUrls = installedUrls,
            )
        assertEquals(3, familyOff.size)

        val regularResult = familyOff.find { it.name == "Regular Addon" }!!
        assertTrue(regularResult.isInstalled)

        val adultResult = familyOff.find { it.name == "Adult Addon" }!!
        assertFalse(adultResult.isInstalled)
    }

    @Test
    fun `storeCategoryCounts counts each category`() {
        val entry1 =
            testStoreAddon(
                url = "https://1.com/manifest.json",
                name = "1",
                categories = setOf(AddonCategory.CATALOGS, AddonCategory.STREAMS),
            )
        val entry2 =
            testStoreAddon(
                url = "https://2.com/manifest.json",
                name = "2",
                categories = setOf(AddonCategory.STREAMS),
            )
        val counts = AddonFilters.storeCategoryCounts(listOf(entry1, entry2))

        assertEquals(1, counts[AddonCategory.CATALOGS])
        assertEquals(2, counts[AddonCategory.STREAMS])
        assertEquals(null, counts[AddonCategory.OTHER])
    }
}
