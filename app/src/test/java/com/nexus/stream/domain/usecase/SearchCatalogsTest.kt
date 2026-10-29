package com.nexus.stream.domain.usecase

import com.nexus.stream.domain.model.Addon
import com.nexus.stream.domain.model.AddonManifest
import com.nexus.stream.domain.model.AddonSource
import com.nexus.stream.domain.model.CatalogSpec
import com.nexus.stream.domain.model.ExtraSpec
import com.nexus.stream.domain.model.ManifestHints
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchCatalogsTest {
    @Test
    fun SQ1_trim_andCollapseInnerWhitespace() {
        assertEquals("dune part two", normalizeSearchQuery("  dune   part  two "))
    }

    @Test
    fun SQ2_blank_becomesEmpty() {
        assertEquals("", normalizeSearchQuery("   "))
    }

    @Test
    fun SQ3_oneLatinChar_isNotSearchable() {
        assertFalse(isSearchable("a"))
    }

    @Test
    fun SQ4_twoChars_areSearchable() {
        assertTrue(isSearchable("ab"))
    }

    @Test
    fun SQ5_oneCjkCharacter_isSearchable() {
        assertTrue(isSearchable("鬼"))
        assertTrue(isSearchable("ア"))
        assertTrue(isSearchable("あ"))
        assertTrue(isSearchable("한"))
    }

    @Test
    fun SQ6_oneEmoji_isNotSearchable_codePointsAreCounted() {
        assertFalse(isSearchable("😀"))
    }

    @Test
    fun SC1_optionalSearchCatalog_isListed() {
        val addon =
            createAddon(
                transportUrl = "https://example.com/manifest.json",
                name = "Addon",
                catalogs =
                    listOf(
                        CatalogSpec(
                            type = "movie",
                            id = "top",
                            name = "Top Movies",
                            extras = listOf(ExtraSpec(name = "search", isRequired = false, options = emptyList())),
                        ),
                    ),
            )

        val catalogs = buildSearchCatalogs(listOf(addon))

        assertEquals(1, catalogs.size)
        val catalog = catalogs.first()
        assertEquals("movie", catalog.type)
        assertEquals("top", catalog.catalogId)
        assertEquals("https://example.com/manifest.json|movie|top", catalog.key)
        assertEquals("Top Movies", catalog.title)
        assertNull(catalog.genre)
        assertFalse(catalog.supportsSkip)
    }

    @Test
    fun SC2_requiredSearchCatalog_isListed() {
        val addon =
            createAddon(
                transportUrl = "https://example.com/manifest.json",
                name = "Addon",
                catalogs =
                    listOf(
                        CatalogSpec(
                            type = "movie",
                            id = "search",
                            name = "Search",
                            extras = listOf(ExtraSpec(name = "search", isRequired = true, options = emptyList())),
                        ),
                    ),
            )

        val catalogs = buildSearchCatalogs(listOf(addon))

        assertEquals(1, catalogs.size)
        val catalog = catalogs.first()
        assertEquals("movie", catalog.type)
        assertEquals("search", catalog.catalogId)
        assertEquals("Search", catalog.title)
    }

    @Test
    fun SC3_catalogWithoutSearch_isExcluded() {
        val addon =
            createAddon(
                transportUrl = "https://example.com/manifest.json",
                catalogs =
                    listOf(
                        CatalogSpec(
                            type = "movie",
                            id = "top",
                            name = "Top Movies",
                            extras =
                                listOf(
                                    ExtraSpec(name = "genre", isRequired = false, options = listOf("Action")),
                                    ExtraSpec(name = "skip", isRequired = false, options = emptyList()),
                                ),
                        ),
                    ),
            )

        val catalogs = buildSearchCatalogs(listOf(addon))

        assertTrue(catalogs.isEmpty())
    }

    @Test
    fun SC4_otherRequiredExtra_isExcluded() {
        val addon =
            createAddon(
                transportUrl = "https://example.com/manifest.json",
                catalogs =
                    listOf(
                        CatalogSpec(
                            type = "movie",
                            id = "top",
                            name = "Top Movies",
                            extras =
                                listOf(
                                    ExtraSpec(name = "search", isRequired = false, options = emptyList()),
                                    ExtraSpec(name = "lastVideosIds", isRequired = true, options = emptyList()),
                                ),
                        ),
                    ),
            )

        val catalogs = buildSearchCatalogs(listOf(addon))

        assertTrue(catalogs.isEmpty())
    }

    @Test
    fun SC5_disabledAddon_isIgnored() {
        val enabledAddon =
            createAddon(
                transportUrl = "https://enabled.com/manifest.json",
                name = "Enabled",
                enabled = true,
                catalogs =
                    listOf(
                        CatalogSpec(
                            type = "movie",
                            id = "top",
                            name = "Top Movies",
                            extras = listOf(ExtraSpec(name = "search", isRequired = false, options = emptyList())),
                        ),
                    ),
            )
        val disabledAddon =
            createAddon(
                transportUrl = "https://disabled.com/manifest.json",
                name = "Disabled",
                enabled = false,
                catalogs =
                    listOf(
                        CatalogSpec(
                            type = "movie",
                            id = "top",
                            name = "Top Movies",
                            extras = listOf(ExtraSpec(name = "search", isRequired = false, options = emptyList())),
                        ),
                    ),
            )

        val catalogs = buildSearchCatalogs(listOf(enabledAddon, disabledAddon))

        assertEquals(1, catalogs.size)
        assertEquals("https://enabled.com/manifest.json|movie|top", catalogs.first().key)
    }

    @Test
    fun SC6_sameTitleSameType_getsTheAddonNameSuffix() {
        val addonA =
            createAddon(
                transportUrl = "https://a.com/manifest.json",
                name = "A",
                catalogs =
                    listOf(
                        CatalogSpec(
                            type = "movie",
                            id = "top",
                            name = "Popular",
                            extras = listOf(ExtraSpec(name = "search", isRequired = false, options = emptyList())),
                        ),
                    ),
            )
        val addonB =
            createAddon(
                transportUrl = "https://b.com/manifest.json",
                name = "B",
                catalogs =
                    listOf(
                        CatalogSpec(
                            type = "movie",
                            id = "top",
                            name = "Popular",
                            extras = listOf(ExtraSpec(name = "search", isRequired = false, options = emptyList())),
                        ),
                    ),
            )

        val catalogs = buildSearchCatalogs(listOf(addonA, addonB))

        assertEquals(2, catalogs.size)
        assertEquals("Popular (A)", catalogs[0].title)
        assertEquals("Popular (B)", catalogs[1].title)
    }

    @Test
    fun SC7_order_isAddonPositionThenManifestOrder() {
        val addon2 =
            createAddon(
                transportUrl = "https://two.com/manifest.json",
                name = "Two",
                position = 2,
                catalogs =
                    listOf(
                        CatalogSpec(
                            type = "movie",
                            id = "two",
                            name = "Two",
                            extras = listOf(ExtraSpec(name = "search", isRequired = false, options = emptyList())),
                        ),
                    ),
            )
        val addon1 =
            createAddon(
                transportUrl = "https://one.com/manifest.json",
                name = "One",
                position = 1,
                catalogs =
                    listOf(
                        CatalogSpec(
                            type = "movie",
                            id = "one",
                            name = "One",
                            extras = listOf(ExtraSpec(name = "search", isRequired = false, options = emptyList())),
                        ),
                    ),
            )

        val catalogs = buildSearchCatalogs(listOf(addon2, addon1))

        assertEquals(2, catalogs.size)
        assertEquals("one", catalogs[0].catalogId)
        assertEquals("two", catalogs[1].catalogId)
    }

    @Test
    fun SC8_duplicateKey_isListedOnce() {
        val addon =
            createAddon(
                transportUrl = "https://example.com/manifest.json",
                name = "Addon",
                catalogs =
                    listOf(
                        CatalogSpec(
                            type = "movie",
                            id = "top",
                            name = "First Top",
                            extras = listOf(ExtraSpec(name = "search", isRequired = false, options = emptyList())),
                        ),
                        CatalogSpec(
                            type = "movie",
                            id = "top",
                            name = "Second Top",
                            extras = listOf(ExtraSpec(name = "search", isRequired = false, options = emptyList())),
                        ),
                    ),
            )

        val catalogs = buildSearchCatalogs(listOf(addon))

        assertEquals(1, catalogs.size)
        assertEquals("First Top", catalogs.first().title)
    }

    @Test
    fun SC9_genreAndSkipAreIgnored() {
        val addon =
            createAddon(
                transportUrl = "https://example.com/manifest.json",
                name = "Addon",
                catalogs =
                    listOf(
                        CatalogSpec(
                            type = "movie",
                            id = "top",
                            name = "Top",
                            extras =
                                listOf(
                                    ExtraSpec(name = "search", isRequired = false, options = emptyList()),
                                    ExtraSpec(name = "genre", isRequired = false, options = listOf("Action")),
                                    ExtraSpec(name = "skip", isRequired = false, options = emptyList()),
                                ),
                        ),
                    ),
            )

        val catalogs = buildSearchCatalogs(listOf(addon))

        assertEquals(1, catalogs.size)
        assertNull(catalogs.first().genre)
        assertFalse(catalogs.first().supportsSkip)
    }

    @Test
    fun BM1_nonBlankQuery_isSearch() {
        assertEquals(DiscoverBodyMode.Search, discoverBodyMode("dune", isFocused = false, hasHistory = false))
    }

    @Test
    fun BM2_emptyQueryFocusedWithHistory_isRecents() {
        assertEquals(DiscoverBodyMode.Recents, discoverBodyMode("", isFocused = true, hasHistory = true))
    }

    @Test
    fun BM3_emptyQueryFocusedWithoutHistory_isBrowse() {
        assertEquals(DiscoverBodyMode.Browse, discoverBodyMode("", isFocused = true, hasHistory = false))
    }

    @Test
    fun BM4_emptyQueryNotFocusedWithHistory_isBrowse() {
        assertEquals(DiscoverBodyMode.Browse, discoverBodyMode("", isFocused = false, hasHistory = true))
    }

    @Test
    fun BM5_whitespaceOnlyQuery_isTreatedAsEmpty() {
        assertEquals(DiscoverBodyMode.Recents, discoverBodyMode("   ", isFocused = true, hasHistory = true))
    }

    private fun createAddon(
        transportUrl: String,
        name: String = "Test Addon",
        catalogs: List<CatalogSpec>,
        enabled: Boolean = true,
        position: Int = 0,
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
            enabled = enabled,
            position = position,
            source = AddonSource.LOCAL,
            installedAt = 0L,
        )
}
