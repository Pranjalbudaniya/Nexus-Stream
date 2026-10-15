package com.nexus.stream.data.remote.addon

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AddonUrlBuilderTest {
    @Test
    fun normalize_manifestSuffixAndTrailingSlashStripped() {
        val url = AddonUrlBuilder.normalize("https://v3-cinemeta.strem.io/manifest.json")
        assertNotNull(url)
        assertEquals("https", url!!.scheme)
        assertEquals("v3-cinemeta.strem.io", url.host)
        assertTrue(url.pathSegments.none { it == "manifest.json" })

        val urlTrailingSlash = AddonUrlBuilder.normalize("https://v3-cinemeta.strem.io/manifest.json/")
        assertNotNull(urlTrailingSlash)
        assertTrue(urlTrailingSlash!!.pathSegments.none { it == "manifest.json" })
    }

    @Test
    fun normalize_stremioSchemeBecomesHttps() {
        val url = AddonUrlBuilder.normalize("stremio://v3-cinemeta.strem.io/manifest.json")
        assertNotNull(url)
        assertEquals("https", url!!.scheme)
        assertEquals("v3-cinemeta.strem.io", url.host)
    }

    @Test
    fun normalize_whitespaceTrimmed() {
        val url = AddonUrlBuilder.normalize("   https://v3-cinemeta.strem.io/manifest.json   ")
        assertNotNull(url)
        assertEquals("v3-cinemeta.strem.io", url!!.host)
    }

    @Test
    fun normalize_httpAndPortKept() {
        val url = AddonUrlBuilder.normalize("http://192.168.1.100:7000/manifest.json")
        assertNotNull(url)
        assertEquals("http", url!!.scheme)
        assertEquals(7000, url.port)
        assertEquals("192.168.1.100", url.host)
    }

    @Test
    fun normalize_pathWithConfigSegmentKept() {
        val url = AddonUrlBuilder.normalize("https://addon.com/providers=a|b/manifest.json")
        assertNotNull(url)
        assertEquals(listOf("providers=a|b"), url!!.pathSegments)
    }

    @Test
    fun normalize_invalidInputsReturnNull() {
        assertNull(AddonUrlBuilder.normalize("ftp://addon.com/manifest.json"))
        assertNull(AddonUrlBuilder.normalize("ipfs://QmXoypizjW3WknFiJnKLwHCnL72vedxjQkDDP1mXWo6uco"))
        assertNull(AddonUrlBuilder.normalize("not a url"))
        assertNull(AddonUrlBuilder.normalize(""))
        assertNull(AddonUrlBuilder.normalize("   "))
    }

    @Test
    fun manifestUrl_returnsExpectedUrl() {
        val url = AddonUrlBuilder.manifestUrl("https://v3-cinemeta.strem.io")
        assertEquals("https://v3-cinemeta.strem.io/manifest.json", url)
    }

    @Test
    fun resourceUrl_movieStream() {
        val url = AddonUrlBuilder.resourceUrl("https://addon.com", "stream", "movie", "tt0111161")
        assertEquals("https://addon.com/stream/movie/tt0111161.json", url)
    }

    @Test
    fun resourceUrl_seriesIdDecodedLastSegment() {
        val url = AddonUrlBuilder.resourceUrl("https://addon.com", "stream", "series", "tt0944947:1:1")
        assertNotNull(url)
        val parsed = url!!.toHttpUrl()
        assertEquals("tt0944947:1:1.json", parsed.pathSegments.last())
    }

    @Test
    fun resourceUrl_extrasOrderPreserved() {
        val extras = linkedMapOf("genre" to "Action", "skip" to "100")
        val url = AddonUrlBuilder.resourceUrl("https://addon.com", "catalog", "movie", "top", extras)
        assertNotNull(url)
        val parsed = url!!.toHttpUrl()
        assertEquals("genre=Action&skip=100.json", parsed.pathSegments.last())
    }

    @Test
    fun resourceUrl_encodedLastSegmentForSearchWithSpaces() {
        val searchExtras = mapOf("search" to "game of thrones")
        val url = AddonUrlBuilder.resourceUrl("https://addon.com", "catalog", "series", "top", searchExtras)
        assertNotNull(url)
        val lastSegment = url!!.substringAfterLast("/")
        assertEquals("search=game%20of%20thrones.json", lastSegment)
    }

    @Test
    fun resourceUrl_extraValueEncodedCorrectly() {
        val specialExtras = mapOf("val" to "a&b=c/d")
        val url = AddonUrlBuilder.resourceUrl("https://addon.com", "catalog", "movie", "top", specialExtras)
        assertNotNull(url)
        assertTrue(url!!.contains("val=a%26b%3Dc%2Fd.json"))
    }

    @Test
    fun resourceUrl_idWithSlashIsEncodedAndDoesNotAddPathSegment() {
        val idWithSlash = "custom/id"
        val url = AddonUrlBuilder.resourceUrl("https://addon.com", "stream", "movie", idWithSlash)
        assertNotNull(url)
        val parsed = url!!.toHttpUrl()
        assertEquals(listOf("stream", "movie", "custom%2Fid.json"), parsed.encodedPathSegments)
        assertEquals(listOf("stream", "movie", "custom/id.json"), parsed.pathSegments)
    }

    @Test
    fun configureUrl_manifestSuffixStrippedAndConfigureAppended() {
        val url = AddonUrlBuilder.configureUrl("https://v3-cinemeta.strem.io/manifest.json")
        assertEquals("https://v3-cinemeta.strem.io/configure", url)

        val urlBare = AddonUrlBuilder.configureUrl("https://v3-cinemeta.strem.io")
        assertEquals("https://v3-cinemeta.strem.io/configure", urlBare)

        val invalid = AddonUrlBuilder.configureUrl("not a url")
        assertNull(invalid)
    }

    @Test
    fun fromDeepLink_validStremioSchemeConvertedToHttps() {
        val result = AddonUrlBuilder.fromDeepLink("stremio://v3-cinemeta.strem.io/manifest.json")
        assertEquals("https://v3-cinemeta.strem.io/manifest.json", result)
    }

    @Test
    fun fromDeepLink_configSegmentPreserved() {
        val result = AddonUrlBuilder.fromDeepLink("stremio://example.com/user_123/manifest.json")
        assertEquals("https://example.com/user_123/manifest.json", result)
    }

    @Test
    fun fromDeepLink_invalidInputsReturnNull() {
        assertNull(AddonUrlBuilder.fromDeepLink(null))
        assertNull(AddonUrlBuilder.fromDeepLink(""))
        assertNull(AddonUrlBuilder.fromDeepLink("   "))
        assertNull(AddonUrlBuilder.fromDeepLink("https://v3-cinemeta.strem.io/manifest.json"))
        assertNull(AddonUrlBuilder.fromDeepLink("http://v3-cinemeta.strem.io/manifest.json"))
        assertNull(AddonUrlBuilder.fromDeepLink("ftp://v3-cinemeta.strem.io/manifest.json"))
        assertNull(AddonUrlBuilder.fromDeepLink("stremio:///manifest.json"))
        assertNull(AddonUrlBuilder.fromDeepLink("stremio://"))
    }
}
