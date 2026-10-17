package com.nexus.stream.data.remote.addon

import com.nexus.stream.domain.model.AddonCategory
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StoreMappersTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
            explicitNulls = false
        }

    private fun readFixture(name: String): String =
        javaClass.classLoader!!
            .getResourceAsStream("fixtures/$name")!!
            .bufferedReader()
            .readText()

    @Test
    fun `live trimmed list parses correctly`() {
        val raw = readFixture("store_addon_catalog.json")
        val element = json.parseToJsonElement(raw)
        val addons = StoreMappers.mapStoreAddons(element, json)

        assertNotNull(addons)
        assertEquals(30, addons!!.size)
        val first = addons.first()
        assertEquals("Ruído · PT", first.name)
        assertTrue(first.hasDetails)
        assertTrue(first.usable)
        assertTrue(first.categories.contains(AddonCategory.CATALOGS))
    }

    @Test
    fun `array form and addons object form parse identically`() {
        val raw = readFixture("store_edge.json")
        val objElement = json.parseToJsonElement(raw)
        val arrayElement = objElement as kotlinx.serialization.json.JsonObject
        val rawArray = arrayElement["addons"] as JsonArray

        val fromObj = StoreMappers.mapStoreAddons(objElement, json)
        val fromArray = StoreMappers.mapStoreAddons(rawArray, json)

        assertNotNull(fromObj)
        assertNotNull(fromArray)
        assertEquals(fromObj, fromArray)
    }

    @Test
    fun `invalid top level element returns null`() {
        val primitive = JsonPrimitive("not an array or object")
        val result = StoreMappers.mapStoreAddons(primitive, json)
        assertNull(result)
    }

    @Test
    fun `store_edge cases parse with all rules enforced`() {
        val raw = readFixture("store_edge.json")
        val element = json.parseToJsonElement(raw)
        val addons = StoreMappers.mapStoreAddons(element, json)

        assertNotNull(addons)
        // store_edge.json has 10 items:
        // 1: no-manifest -> valid
        // 2: only-catalog -> valid (usable = false)
        // 3: meta-only -> valid
        // 4: subtitles-only -> valid
        // 5: insecure (http) -> valid
        // 6: adult -> valid (isAdult = true)
        // 7: duplicate meta-only -> dropped
        // 8: ftp -> dropped
        // 9: no url -> dropped
        // 10: string -> dropped
        // Total valid = 6
        assertEquals(6, addons!!.size)

        // 1: entry without manifest
        val noManifest = addons.find { it.name == "no-manifest.com" }
        assertNotNull(noManifest)
        assertEquals("https://no-manifest.com/", noManifest!!.transportUrl)
        assertFalse(noManifest.hasDetails)
        assertTrue(noManifest.usable)
        assertEquals(setOf(AddonCategory.OTHER), noManifest.categories)

        // 2: only-catalog
        val onlyCatalog = addons.find { it.name == "Only Catalog" }
        assertNotNull(onlyCatalog)
        assertEquals("https://only-catalog.com/", onlyCatalog!!.transportUrl)
        assertTrue(onlyCatalog.hasDetails)
        assertFalse(onlyCatalog.usable)
        assertEquals(AddonCategory.OTHER, onlyCatalog.primary)

        // 3: meta-only
        val metaOnly = addons.find { it.name == "Meta Only" }
        assertNotNull(metaOnly)
        assertEquals("https://meta-only.com/", metaOnly!!.transportUrl)
        assertTrue(metaOnly.usable)
        assertEquals(AddonCategory.OTHER, metaOnly.primary)

        // 4: subtitles-only
        val subOnly = addons.find { it.name == "Subtitles Only" }
        assertNotNull(subOnly)
        assertEquals("https://subtitles-only.com/", subOnly!!.transportUrl)
        assertTrue(subOnly.usable)
        assertEquals(setOf(AddonCategory.SUBTITLES), subOnly.categories)

        // 5: http url accepted
        val insecure = addons.find { it.name == "Insecure Addon" }
        assertNotNull(insecure)
        assertEquals("http://insecure.com/", insecure!!.transportUrl)

        // 6: adult flag
        val adult = addons.find { it.name == "Adult Addon" }
        assertNotNull(adult)
        assertEquals("https://adult.com/", adult!!.transportUrl)
        assertTrue(adult.isAdult)

        // Verify duplicate url was collapsed: only 1 entry for meta-only
        val metaOnlyCount = addons.count { it.transportUrl == "https://meta-only.com/" }
        assertEquals(1, metaOnlyCount)

        // Verify bad scheme, missing transportUrl, and non-object were dropped
        assertFalse(addons.any { it.transportUrl.startsWith("ftp://") })
        assertFalse(addons.any { it.name == "No URL" })
    }
}
