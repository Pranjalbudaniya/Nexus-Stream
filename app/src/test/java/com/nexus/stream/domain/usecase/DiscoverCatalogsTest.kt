package com.nexus.stream.domain.usecase

import com.nexus.stream.domain.model.Addon
import com.nexus.stream.domain.model.AddonManifest
import com.nexus.stream.domain.model.AddonSource
import com.nexus.stream.domain.model.CatalogSpec
import com.nexus.stream.domain.model.DiscoverCatalog
import com.nexus.stream.domain.model.DiscoverSelection
import com.nexus.stream.domain.model.ExtraSpec
import com.nexus.stream.domain.model.GenreSpec
import com.nexus.stream.domain.model.ManifestHints
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoverCatalogsTest {
    @Test
    fun DC1_boardCatalog_isListed() {
        val addon =
            createAddon(
                transportUrl = "https://example.com/manifest.json",
                name = "TestAddon",
                catalogs =
                    listOf(
                        CatalogSpec(type = "movie", id = "top", name = "Top Movies", extras = emptyList()),
                    ),
            )

        val catalogs = buildDiscoverCatalogs(listOf(addon))

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
    fun DC2_searchOnlyCatalog_isExcluded() {
        val addon =
            createAddon(
                transportUrl = "https://example.com/manifest.json",
                catalogs =
                    listOf(
                        CatalogSpec(
                            type = "movie",
                            id = "search",
                            name = "Search",
                            extras = listOf(ExtraSpec("search", isRequired = true, options = emptyList())),
                        ),
                    ),
            )

        val catalogs = buildDiscoverCatalogs(listOf(addon))

        assertTrue(catalogs.isEmpty())
    }

    @Test
    fun DC3_optionalSearchSkipGenre_isListed_withGenreAndSkip() {
        val addon =
            createAddon(
                transportUrl = "https://example.com/manifest.json",
                catalogs =
                    listOf(
                        CatalogSpec(
                            type = "movie",
                            id = "top",
                            name = "Top",
                            extras =
                                listOf(
                                    ExtraSpec("genre", isRequired = false, options = listOf("A", "B")),
                                    ExtraSpec("search", isRequired = false, options = emptyList()),
                                    ExtraSpec("skip", isRequired = false, options = emptyList()),
                                ),
                        ),
                    ),
            )

        val catalogs = buildDiscoverCatalogs(listOf(addon))

        assertEquals(1, catalogs.size)
        val catalog = catalogs.first()
        assertEquals(GenreSpec(options = listOf("A", "B"), required = false), catalog.genre)
        assertTrue(catalog.supportsSkip)
    }

    @Test
    fun DC4_requiredGenreWithOptions_isListed_asRequired() {
        val addon =
            createAddon(
                transportUrl = "https://example.com/manifest.json",
                catalogs =
                    listOf(
                        CatalogSpec(
                            type = "movie",
                            id = "year",
                            name = "New",
                            extras =
                                listOf(
                                    ExtraSpec("genre", isRequired = true, options = listOf("2024", "2023")),
                                ),
                        ),
                    ),
            )

        val catalogs = buildDiscoverCatalogs(listOf(addon))

        assertEquals(1, catalogs.size)
        val catalog = catalogs.first()
        assertNotNull(catalog.genre)
        assertTrue(catalog.genre!!.required)
        assertEquals(listOf("2024", "2023"), catalog.genre.options)
    }

    @Test
    fun DC5_requiredGenreWithoutOptions_isExcluded() {
        val addon =
            createAddon(
                transportUrl = "https://example.com/manifest.json",
                catalogs =
                    listOf(
                        CatalogSpec(
                            type = "movie",
                            id = "year",
                            name = "New",
                            extras =
                                listOf(
                                    ExtraSpec("genre", isRequired = true, options = emptyList()),
                                ),
                        ),
                    ),
            )

        val catalogs = buildDiscoverCatalogs(listOf(addon))

        assertTrue(catalogs.isEmpty())
    }

    @Test
    fun DC6_requiredOtherExtra_isExcluded() {
        val addon =
            createAddon(
                transportUrl = "https://example.com/manifest.json",
                catalogs =
                    listOf(
                        CatalogSpec(
                            type = "movie",
                            id = "last",
                            name = "Last Videos",
                            extras =
                                listOf(
                                    ExtraSpec("lastVideosIds", isRequired = true, options = emptyList()),
                                ),
                        ),
                    ),
            )

        val catalogs = buildDiscoverCatalogs(listOf(addon))

        assertTrue(catalogs.isEmpty())
    }

    @Test
    fun DC7_blankName_fallsBackToId() {
        val addon =
            createAddon(
                transportUrl = "https://example.com/manifest.json",
                catalogs =
                    listOf(
                        CatalogSpec(type = "movie", id = "top", name = "", extras = emptyList()),
                    ),
            )

        val catalogs = buildDiscoverCatalogs(listOf(addon))

        assertEquals(1, catalogs.size)
        assertEquals("top", catalogs.first().title)
    }

    @Test
    fun DC8_sameTitleSameType_getsTheAddonNameSuffix() {
        val addonA =
            createAddon(
                transportUrl = "https://a.com/manifest.json",
                name = "Addon A",
                catalogs =
                    listOf(
                        CatalogSpec(type = "movie", id = "top", name = "Popular", extras = emptyList()),
                    ),
            )
        val addonB =
            createAddon(
                transportUrl = "https://b.com/manifest.json",
                name = "Addon B",
                catalogs =
                    listOf(
                        CatalogSpec(type = "movie", id = "top", name = "Popular", extras = emptyList()),
                    ),
            )

        val catalogs = buildDiscoverCatalogs(listOf(addonA, addonB))

        assertEquals(2, catalogs.size)
        assertEquals("Popular (Addon A)", catalogs[0].title)
        assertEquals("Popular (Addon B)", catalogs[1].title)
    }

    @Test
    fun DC9_sameTitleDifferentType_hasNoSuffix() {
        val addon =
            createAddon(
                transportUrl = "https://example.com/manifest.json",
                name = "Cinemeta",
                catalogs =
                    listOf(
                        CatalogSpec(type = "movie", id = "top", name = "Popular", extras = emptyList()),
                        CatalogSpec(type = "series", id = "top", name = "Popular", extras = emptyList()),
                    ),
            )

        val catalogs = buildDiscoverCatalogs(listOf(addon))

        assertEquals(2, catalogs.size)
        assertEquals("Popular", catalogs[0].title)
        assertEquals("Popular", catalogs[1].title)
    }

    @Test
    fun DC10_disabledAddon_isIgnored() {
        val enabled =
            createAddon(
                transportUrl = "https://enabled.com/manifest.json",
                enabled = true,
                catalogs = listOf(CatalogSpec("movie", "top", "Top", emptyList())),
            )
        val disabled =
            createAddon(
                transportUrl = "https://disabled.com/manifest.json",
                enabled = false,
                catalogs = listOf(CatalogSpec("movie", "other", "Other", emptyList())),
            )

        val catalogs = buildDiscoverCatalogs(listOf(enabled, disabled))

        assertEquals(1, catalogs.size)
        assertEquals("https://enabled.com/manifest.json|movie|top", catalogs.first().key)
    }

    @Test
    fun DC11_order_isAddonPositionThenManifestOrder() {
        val addon2 =
            createAddon(
                transportUrl = "https://pos2.com/manifest.json",
                position = 2,
                catalogs =
                    listOf(
                        CatalogSpec("movie", "c21", "C21", emptyList()),
                        CatalogSpec("movie", "c22", "C22", emptyList()),
                    ),
            )
        val addon1 =
            createAddon(
                transportUrl = "https://pos1.com/manifest.json",
                position = 1,
                catalogs =
                    listOf(
                        CatalogSpec("movie", "c11", "C11", emptyList()),
                        CatalogSpec("movie", "c12", "C12", emptyList()),
                    ),
            )

        val catalogs = buildDiscoverCatalogs(listOf(addon2, addon1))

        assertEquals(listOf("c11", "c12", "c21", "c22"), catalogs.map { it.catalogId })
    }

    @Test
    fun DC12_genreOptions_areTrimmedNonBlankAndDistinct() {
        val addon =
            createAddon(
                transportUrl = "https://example.com/manifest.json",
                catalogs =
                    listOf(
                        CatalogSpec(
                            type = "movie",
                            id = "top",
                            name = "Top",
                            extras =
                                listOf(
                                    ExtraSpec(
                                        name = "genre",
                                        isRequired = false,
                                        options = listOf(" Action ", "", "Action", "Drama"),
                                    ),
                                ),
                        ),
                    ),
            )

        val catalogs = buildDiscoverCatalogs(listOf(addon))

        assertEquals(1, catalogs.size)
        assertEquals(listOf("Action", "Drama"), catalogs.first().genre?.options)
    }

    @Test
    fun DC13_duplicateKey_isListedOnce() {
        val addon =
            createAddon(
                transportUrl = "https://example.com/manifest.json",
                catalogs =
                    listOf(
                        CatalogSpec("movie", "top", "Top", emptyList()),
                        CatalogSpec("movie", "top", "Top Duplicate", emptyList()),
                    ),
            )

        val catalogs = buildDiscoverCatalogs(listOf(addon))

        assertEquals(1, catalogs.size)
        assertEquals("top", catalogs.first().catalogId)
    }

    @Test
    fun DC14_types_areMovieSeriesThenOthersAlphabetical() {
        val catalogs =
            listOf(
                createCatalog(type = "tv", id = "tv"),
                createCatalog(type = "series", id = "series"),
                createCatalog(type = "channel", id = "channel"),
                createCatalog(type = "movie", id = "movie"),
                createCatalog(type = "   ", id = "blank"),
            )

        val types = discoverTypes(catalogs)

        assertEquals(listOf("movie", "series", "channel", "tv"), types)
    }

    @Test
    fun DC15_noCatalogs_hasNoTypes() {
        val types = discoverTypes(emptyList())

        assertTrue(types.isEmpty())
    }

    @Test
    fun DS1_noCatalogs_isNull() {
        val selection = resolveDiscoverSelection(catalogs = emptyList(), wanted = null)

        assertNull(selection)
    }

    @Test
    fun DS2_noWanted_picksFirstTypeAndItsFirstCatalog() {
        val catalogs =
            listOf(
                createCatalog(type = "movie", id = "m1", genre = GenreSpec(listOf("Action"), required = false)),
                createCatalog(type = "series", id = "s1"),
            )

        val selection = resolveDiscoverSelection(catalogs = catalogs, wanted = null)

        assertEquals(
            DiscoverSelection(
                type = "movie",
                catalogKey = catalogs[0].key,
                genre = null,
            ),
            selection,
        )
    }

    @Test
    fun DS3_validWanted_isKept() {
        val catalogs =
            listOf(
                createCatalog(type = "series", id = "s1"),
                createCatalog(
                    type = "series",
                    id = "s2",
                    genre = GenreSpec(listOf("Action", "Comedy"), required = false),
                ),
            )
        val wanted =
            DiscoverSelection(
                type = "series",
                catalogKey = catalogs[1].key,
                genre = "Action",
            )

        val selection = resolveDiscoverSelection(catalogs = catalogs, wanted = wanted)

        assertEquals(wanted, selection)
    }

    @Test
    fun DS4_genreNoLongerOffered_optional_becomesNull() {
        val catalogs =
            listOf(
                createCatalog(
                    type = "movie",
                    id = "m1",
                    genre = GenreSpec(listOf("Action", "Comedy"), required = false),
                ),
            )
        val wanted =
            DiscoverSelection(
                type = "movie",
                catalogKey = catalogs[0].key,
                genre = "Western",
            )

        val selection = resolveDiscoverSelection(catalogs = catalogs, wanted = wanted)

        assertEquals(
            DiscoverSelection(
                type = "movie",
                catalogKey = catalogs[0].key,
                genre = null,
            ),
            selection,
        )
    }

    @Test
    fun DS5_genreNoLongerOffered_required_becomesFirstOption() {
        val catalogs =
            listOf(
                createCatalog(
                    type = "movie",
                    id = "year",
                    genre = GenreSpec(listOf("2024", "2023"), required = true),
                ),
            )
        val wanted =
            DiscoverSelection(
                type = "movie",
                catalogKey = catalogs[0].key,
                genre = null,
            )

        val selection = resolveDiscoverSelection(catalogs = catalogs, wanted = wanted)

        assertEquals(
            DiscoverSelection(
                type = "movie",
                catalogKey = catalogs[0].key,
                genre = "2024",
            ),
            selection,
        )
    }

    @Test
    fun DS6_catalogGone_sameType_usesRememberedElseFirst() {
        val m1 = createCatalog(type = "movie", id = "m1")
        val m2 = createCatalog(type = "movie", id = "m2")
        val catalogs = listOf(m1, m2)

        // wanted m3 is gone; remembered m2 exists
        val res1 =
            resolveDiscoverSelection(
                catalogs = catalogs,
                wanted = DiscoverSelection("movie", "goneKey", null),
                lastCatalogByType = mapOf("movie" to m2.key),
            )
        assertEquals(m2.key, res1?.catalogKey)

        // remembered is also gone; falls back to first catalog of the type
        val res2 =
            resolveDiscoverSelection(
                catalogs = catalogs,
                wanted = DiscoverSelection("movie", "goneKey", null),
                lastCatalogByType = mapOf("movie" to "alsoGone"),
            )
        assertEquals(m1.key, res2?.catalogKey)
    }

    @Test
    fun DS7_typeGone_usesFirstType() {
        val m1 = createCatalog(type = "movie", id = "m1")
        val s1 = createCatalog(type = "series", id = "s1")
        val catalogs = listOf(m1, s1)

        val wanted = DiscoverSelection(type = "anime", catalogKey = "animeKey", genre = null)
        val selection = resolveDiscoverSelection(catalogs = catalogs, wanted = wanted)

        assertEquals("movie", selection?.type)
        assertEquals(m1.key, selection?.catalogKey)
    }

    @Test
    fun DS8_blankCatalog_usesRememberedCatalogOfThatType() {
        val s1 = createCatalog(type = "series", id = "s1")
        val s2 = createCatalog(type = "series", id = "s2")
        val catalogs = listOf(s1, s2)

        val wanted = DiscoverSelection(type = "series", catalogKey = "", genre = null)
        val selection =
            resolveDiscoverSelection(
                catalogs = catalogs,
                wanted = wanted,
                lastCatalogByType = mapOf("series" to s2.key),
            )

        assertEquals(s2.key, selection?.catalogKey)
    }

    @Test
    fun DS9_changeCatalog_withNullGenre_appliesTheDefault() {
        val yearCatalog =
            createCatalog(
                type = "movie",
                id = "year",
                genre = GenreSpec(listOf("2024", "2023"), required = true),
            )
        val catalogs = listOf(yearCatalog)

        val wanted = DiscoverSelection(type = "movie", catalogKey = yearCatalog.key, genre = null)
        val selection = resolveDiscoverSelection(catalogs = catalogs, wanted = wanted)

        assertEquals("2024", selection?.genre)
    }

    private fun createAddon(
        transportUrl: String,
        name: String = "Test Addon",
        enabled: Boolean = true,
        position: Int = 0,
        catalogs: List<CatalogSpec>,
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

    private fun createCatalog(
        type: String,
        id: String,
        genre: GenreSpec? = null,
        supportsSkip: Boolean = false,
    ): DiscoverCatalog =
        DiscoverCatalog(
            key = "https://example.com/manifest.json|$type|$id",
            type = type,
            catalogId = id,
            transportUrl = "https://example.com/manifest.json",
            title = id,
            genre = genre,
            supportsSkip = supportsSkip,
        )
}
