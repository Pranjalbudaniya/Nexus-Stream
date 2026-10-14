package com.nexus.stream.data.remote.addon

import com.nexus.stream.domain.model.StreamSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AddonMappersTest {
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
    fun cinemetaManifest_parsesCorrectly() {
        val raw = readFixture("cinemeta_manifest.json")
        val dto = json.decodeFromString<ManifestDto>(raw)
        val manifest = AddonMappers.mapManifest(dto, json)

        assertEquals("com.linvo.cinemeta", manifest.id)
        assertEquals("Cinemeta", manifest.name)
        assertTrue(manifest.resources.any { it.name == "catalog" })
        assertTrue(manifest.resources.any { it.name == "meta" })
        assertTrue(manifest.catalogs.isNotEmpty())
    }

    @Test
    fun cinemetaCatalog_parsesCorrectly() {
        val raw = readFixture("cinemeta_catalog_movie_top.json")
        val dto = json.decodeFromString<CatalogResponseDto>(raw)
        val metas =
            dto.metas.orEmpty().mapNotNull {
                val previewDto = json.decodeFromJsonElement<MetaPreviewDto>(it)
                AddonMappers.mapMetaPreview(previewDto)
            }

        assertTrue(metas.isNotEmpty())
        val first = metas.first()
        assertNotNull(first.id)
        assertNotNull(first.name)
        assertNotNull(first.poster)
    }

    @Test
    fun cinemetaMetaMovie_parsesCorrectly() {
        val raw = readFixture("cinemeta_meta_movie.json")
        val rootObj = json.decodeFromString<JsonObject>(raw)
        val metaElement = rootObj["meta"]!!
        val metaDto = json.decodeFromJsonElement<MetaDetailDto>(metaElement)
        val detail = AddonMappers.mapMetaDetail(metaDto)

        assertNotNull(detail)
        assertEquals("tt0111161", detail!!.id)
        assertEquals("The Shawshank Redemption", detail.name)
        assertNotNull(detail.runtime)
        assertTrue(detail.genres.isNotEmpty())
    }

    @Test
    fun cinemetaMetaSeries_parsesVideosWithSeasonAndEpisode() {
        val raw = readFixture("cinemeta_meta_series.json")
        val rootObj = json.decodeFromString<JsonObject>(raw)
        val metaElement = rootObj["meta"]!!
        val metaDto = json.decodeFromJsonElement<MetaDetailDto>(metaElement)
        val detail = AddonMappers.mapMetaDetail(metaDto)

        assertNotNull(detail)
        assertEquals("tt0944947", detail!!.id)
        assertTrue(detail.videos.isNotEmpty())
        val firstVideo = detail.videos.first()
        assertNotNull(firstVideo.id)
        assertNotNull(firstVideo.season)
        assertNotNull(firstVideo.episode)
    }

    @Test
    fun catalogBadItem_dropsBadItem_andReadsNumericImdbRating() {
        val raw = readFixture("catalog_bad_item.json")
        val dto = json.decodeFromString<CatalogResponseDto>(raw)
        var skipped = 0
        val items = mutableListOf<com.nexus.stream.domain.model.MetaPreview>()

        for (element in dto.metas.orEmpty()) {
            try {
                val previewDto = json.decodeFromJsonElement<MetaPreviewDto>(element)
                val mapped = AddonMappers.mapMetaPreview(previewDto)
                if (mapped != null) {
                    items.add(mapped)
                } else {
                    skipped++
                }
            } catch (_: Exception) {
                skipped++
            }
        }

        assertEquals(1, skipped)
        assertEquals(1, items.size)
        val valid = items.first()
        assertEquals("tt0000002", valid.id)
        assertEquals("8.7", valid.imdbRating)
    }

    @Test
    fun metaArrayForm_takesFirstElement() {
        val raw = readFixture("meta_array_form.json")
        val rootObj = json.decodeFromString<JsonObject>(raw)
        val metaArray = rootObj["meta"] as JsonArray
        val firstObj = metaArray.first() as JsonObject
        val metaDto = json.decodeFromJsonElement<MetaDetailDto>(firstObj)
        val detail = AddonMappers.mapMetaDetail(metaDto)

        assertNotNull(detail)
        assertEquals("tt0111161", detail!!.id)
        assertEquals("The Shawshank Redemption", detail.name)
        assertEquals(listOf("NmzuHjWmXOc"), detail.trailers)
    }

    @Test
    fun emptyMeta_givesNull() {
        val emptyObj = JsonObject(emptyMap())
        val metaDto = json.decodeFromJsonElement<MetaDetailDto>(emptyObj)
        val detail = AddonMappers.mapMetaDetail(metaDto)
        assertNull(detail)
    }

    @Test
    fun streamsMixed_handlesAllSourcesAndPerItemTolerance() {
        val raw = readFixture("streams_mixed.json")
        val rootObj = json.decodeFromString<JsonObject>(raw)
        val streamElements = (rootObj["streams"] as JsonArray).toList()

        val streams = mutableListOf<com.nexus.stream.domain.model.Stream>()
        for (element in streamElements) {
            try {
                val dto = json.decodeFromJsonElement<StreamDto>(element)
                streams.add(AddonMappers.mapStream(dto))
            } catch (_: Exception) {
                // Drop corrupt element (the string in the fixture)
            }
        }

        // 6 valid streams out of 7 elements
        assertEquals(6, streams.size)

        // 1. Direct URL with headers
        val s1 = streams[0]
        assertTrue(s1.source is StreamSource.Url)
        val s1Source = s1.source as StreamSource.Url
        assertEquals("https://example.com/stream.mp4", s1Source.url)
        assertEquals("CustomAgent/1.0", s1Source.requestHeaders["User-Agent"])
        assertEquals("web-1080p", s1.bingeGroup)
        assertTrue(s1.notWebReady)
        assertEquals(1073741824L, s1.videoSize)
        assertEquals("movie.1080p.mp4", s1.filename)

        // 2. Torrent with trackers, fileIdx, and dht dropped
        val s2 = streams[1]
        assertTrue(s2.source is StreamSource.Torrent)
        val s2Source = s2.source as StreamSource.Torrent
        assertEquals("0123456789abcdef0123456789abcdef01234567", s2Source.infoHash)
        assertEquals(2, s2Source.fileIdx)
        assertEquals(2, s2Source.trackers.size)
        assertTrue(s2Source.trackers.none { it.startsWith("dht:") })
        assertEquals("udp://tracker.openbittorrent.com:80/announce", s2Source.trackers[0])

        // 3. YouTube stream
        val s3 = streams[2]
        assertTrue(s3.source is StreamSource.YouTube)
        assertEquals("dQw4w9WgXcQ", (s3.source as StreamSource.YouTube).ytId)

        // 4. External URL
        val s4 = streams[3]
        assertTrue(s4.source is StreamSource.External)

        // 5. Unsupported kind ("nzb")
        val s5 = streams[4]
        assertTrue(s5.source is StreamSource.Unsupported)
        assertEquals("nzb", (s5.source as StreamSource.Unsupported).kind)

        // 6. Title-only stream gets description
        val s6 = streams[5]
        assertEquals("Title Only Stream", s6.description)
    }

    @Test
    fun trailers_dedupesAndAcceptsLegacyStreamAndTrailerStreams() {
        val dto =
            MetaDetailDto(
                id = "tt123",
                type = "movie",
                name = "Test Movie",
                trailers =
                    json.parseToJsonElement(
                        """
                        [
                            { "source": "trailer1", "type": "Trailer" },
                            { "ytId": "trailer2" },
                            { "source": "trailer1" }
                        ]
                        """.trimIndent(),
                    ),
                trailerStreams =
                    listOf(
                        TrailerStreamDto(ytId = "trailer2"),
                        TrailerStreamDto(ytId = "trailer3"),
                    ),
            )
        val detail = AddonMappers.mapMetaDetail(dto)
        assertNotNull(detail)
        assertEquals(listOf("trailer1", "trailer2", "trailer3"), detail!!.trailers)
    }

    @Test
    fun catalogDuplicateIds_dedupesFirstWins() {
        val elements =
            listOf(
                MetaPreviewDto(id = "tt1", type = "movie", name = "First Title"),
                MetaPreviewDto(id = "tt2", type = "movie", name = "Second"),
                MetaPreviewDto(id = "tt1", type = "movie", name = "Duplicate Title"),
            )
        val seen = mutableSetOf<String>()
        val items = mutableListOf<com.nexus.stream.domain.model.MetaPreview>()
        for (dto in elements) {
            val mapped = AddonMappers.mapMetaPreview(dto)
            if (mapped != null && seen.add(mapped.id)) {
                items.add(mapped)
            }
        }
        assertEquals(2, items.size)
        assertEquals("First Title", items.first { it.id == "tt1" }.name)
    }
}
