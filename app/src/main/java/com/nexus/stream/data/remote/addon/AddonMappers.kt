package com.nexus.stream.data.remote.addon

import com.nexus.stream.domain.model.AddonManifest
import com.nexus.stream.domain.model.CatalogSpec
import com.nexus.stream.domain.model.ExtraSpec
import com.nexus.stream.domain.model.ManifestHints
import com.nexus.stream.domain.model.MetaDetail
import com.nexus.stream.domain.model.MetaLink
import com.nexus.stream.domain.model.MetaPreview
import com.nexus.stream.domain.model.PosterShape
import com.nexus.stream.domain.model.ResourceSpec
import com.nexus.stream.domain.model.Stream
import com.nexus.stream.domain.model.StreamSource
import com.nexus.stream.domain.model.Subtitle
import com.nexus.stream.domain.model.Video
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement

object AddonMappers {
    fun mapManifest(
        dto: ManifestDto,
        json: Json,
    ): AddonManifest {
        val resources =
            (dto.resources as? JsonArray)
                ?.mapNotNull { element ->
                    mapResourceSpec(element, json)
                }.orEmpty()

        val catalogs = dto.catalogs.orEmpty().map { mapCatalogSpec(it) }

        return AddonManifest(
            id = dto.id.orEmpty(),
            version = dto.version.orEmpty(),
            name = dto.name.orEmpty(),
            description = dto.description.orEmpty(),
            logo = dto.logo,
            background = dto.background,
            types = dto.types.orEmpty(),
            idPrefixes = dto.idPrefixes,
            resources = resources,
            catalogs = catalogs,
            behaviorHints = mapManifestHints(dto.behaviorHints),
        )
    }

    fun mapMetaPreview(dto: MetaPreviewDto): MetaPreview? {
        val id = dto.id?.takeIf { it.isNotBlank() }
        val type = dto.type?.takeIf { it.isNotBlank() }
        val name = dto.name?.takeIf { it.isNotBlank() }
        if (id == null || type == null || name == null) return null

        val shape =
            when (dto.posterShape?.lowercase()) {
                "square" -> PosterShape.SQUARE
                "landscape" -> PosterShape.LANDSCAPE
                else -> PosterShape.POSTER
            }

        return MetaPreview(
            id = id,
            type = type,
            name = name,
            poster = dto.poster,
            posterShape = shape,
            background = dto.background,
            logo = dto.logo,
            description = dto.description,
            releaseInfo = dto.releaseInfo,
            imdbRating = dto.imdbRating.asStringOrNull(),
            genres = dto.genres.orEmpty(),
        )
    }

    fun mapMetaDetail(dto: MetaDetailDto): MetaDetail? {
        val id = dto.id?.takeIf { it.isNotBlank() }
        val type = dto.type?.takeIf { it.isNotBlank() }
        val name = dto.name?.takeIf { it.isNotBlank() }
        if (id == null || type == null || name == null) return null

        val shape =
            when (dto.posterShape?.lowercase()) {
                "square" -> PosterShape.SQUARE
                "landscape" -> PosterShape.LANDSCAPE
                else -> PosterShape.POSTER
            }

        return MetaDetail(
            id = id,
            type = type,
            name = name,
            poster = dto.poster,
            posterShape = shape,
            background = dto.background,
            logo = dto.logo,
            description = dto.description,
            releaseInfo = dto.releaseInfo,
            released = dto.released,
            runtime = dto.runtime,
            imdbRating = dto.imdbRating.asStringOrNull(),
            genres = extractGenres(dto),
            cast = dto.cast.orEmpty(),
            directors = (dto.directors ?: dto.director).orEmpty(),
            country = dto.country,
            language = dto.language,
            website = dto.website,
            links = extractLinks(dto),
            trailers = extractTrailers(dto),
            videos = extractVideos(dto),
            defaultVideoId = dto.defaultVideoId,
        )
    }

    private val defaultJson =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
            explicitNulls = false
        }

    fun parseMeta(
        rawJson: String,
        json: Json = defaultJson,
    ): MetaDetail? =
        try {
            val element = json.parseToJsonElement(rawJson) as? JsonObject
            val targetObj =
                when (val rawMeta = element?.get("meta")) {
                    is JsonObject -> rawMeta.takeIf { it.isNotEmpty() }
                    is JsonArray -> rawMeta.firstOrNull() as? JsonObject
                    else -> element
                }
            if (targetObj != null) {
                val dto = json.decodeFromJsonElement<MetaDetailDto>(targetObj)
                mapMetaDetail(dto)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }

    fun mapStream(dto: StreamDto): Stream =
        Stream(
            source = resolveStreamSource(dto),
            name = dto.name,
            description = dto.description ?: dto.title,
            subtitles = dto.subtitles.orEmpty().mapNotNull { mapSubtitle(it) },
            bingeGroup = dto.behaviorHints?.bingeGroup,
            notWebReady = dto.behaviorHints?.notWebReady ?: false,
            videoSize = dto.behaviorHints?.videoSize.asLongOrNull(),
            filename = dto.behaviorHints?.filename,
            videoHash = dto.behaviorHints?.videoHash,
        )

    fun mapSubtitle(dto: SubtitleDto): Subtitle? {
        val url = dto.url?.takeIf { it.isNotBlank() }
        val lang = dto.lang?.takeIf { it.isNotBlank() }
        if (url == null || lang == null) return null
        return Subtitle(
            id = dto.id ?: url,
            url = url,
            lang = lang,
            label = dto.label,
        )
    }
}

private fun mapResourceSpec(
    element: JsonElement,
    json: Json,
): ResourceSpec? =
    when (element) {
        is JsonPrimitive -> {
            ResourceSpec(name = element.content, types = null, idPrefixes = null)
        }

        is JsonObject -> {
            try {
                val obj = json.decodeFromJsonElement<ResourceObjectDto>(element)
                ResourceSpec(name = obj.name.orEmpty(), types = obj.types, idPrefixes = obj.idPrefixes)
            } catch (_: kotlinx.serialization.SerializationException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
        }

        else -> {
            null
        }
    }

private fun mapCatalogSpec(dto: CatalogDto): CatalogSpec {
    val modernExtras =
        dto.extra.orEmpty().map { extraDto ->
            ExtraSpec(
                name = extraDto.name.orEmpty(),
                isRequired = extraDto.isRequired ?: false,
                options = extraDto.options.orEmpty(),
            )
        }
    val modernNames = modernExtras.map { it.name }.toSet()
    val legacyRequired = dto.extraRequired.orEmpty().toSet()
    val legacyExtras =
        dto.extraSupported
            .orEmpty()
            .filter { it !in modernNames }
            .map { name ->
                ExtraSpec(
                    name = name,
                    isRequired = name in legacyRequired,
                    options = emptyList(),
                )
            }

    return CatalogSpec(
        type = dto.type.orEmpty(),
        id = dto.id.orEmpty(),
        name = dto.name.orEmpty(),
        extras = modernExtras + legacyExtras,
    )
}

private fun mapManifestHints(hints: ManifestBehaviorHintsDto?): ManifestHints =
    ManifestHints(
        adult = hints?.adult ?: false,
        p2p = hints?.p2p ?: false,
        configurable = hints?.configurable ?: false,
        configurationRequired = hints?.configurationRequired ?: false,
    )

private fun extractGenres(dto: MetaDetailDto): List<String> =
    dto.genres
        ?: dto.links
            ?.filter { it.category?.equals("genres", ignoreCase = true) == true }
            ?.mapNotNull { it.name }
            .orEmpty()

private fun extractLinks(dto: MetaDetailDto): List<MetaLink> =
    dto.links.orEmpty().map {
        MetaLink(
            name = it.name.orEmpty(),
            category = it.category.orEmpty(),
            url = it.url.orEmpty(),
        )
    }

private fun extractVideos(dto: MetaDetailDto): List<Video> =
    dto.videos.orEmpty().map { v ->
        Video(
            id = v.id.orEmpty(),
            title = v.title ?: v.name.orEmpty(),
            released = v.released,
            season = v.season.asIntOrNull(),
            episode = v.episode.asIntOrNull(),
            thumbnail = v.thumbnail,
            overview = v.overview ?: v.description,
        )
    }

private fun extractTrailers(dto: MetaDetailDto): List<String> {
    val trailerIds = mutableListOf<String>()
    val rawTrailers = dto.trailers
    if (rawTrailers is JsonArray) {
        for (element in rawTrailers) {
            when (element) {
                is JsonObject -> {
                    val source = element["source"].asStringOrNull()
                    val ytId = element["ytId"].asStringOrNull()
                    val idCandidate = source ?: ytId
                    if (!idCandidate.isNullOrBlank()) {
                        trailerIds.add(idCandidate)
                    }
                }

                is JsonPrimitive -> {
                    if (element.content.isNotBlank()) {
                        trailerIds.add(element.content)
                    }
                }

                else -> {}
            }
        }
    }
    dto.trailerStreams?.forEach { stream ->
        if (!stream.ytId.isNullOrBlank()) {
            trailerIds.add(stream.ytId)
        }
    }
    return trailerIds.distinct()
}

private fun resolveStreamSource(dto: StreamDto): StreamSource =
    when {
        !dto.url.isNullOrBlank() -> {
            StreamSource.Url(
                url = dto.url,
                requestHeaders =
                    dto.behaviorHints
                        ?.proxyHeaders
                        ?.request
                        .orEmpty(),
            )
        }

        !dto.infoHash.isNullOrBlank() -> {
            val trackers =
                dto.sources
                    .orEmpty()
                    .filter { it.startsWith("tracker:") }
                    .map { it.removePrefix("tracker:") }
            StreamSource.Torrent(
                infoHash = dto.infoHash,
                fileIdx = dto.fileIdx.asIntOrNull(),
                trackers = trackers,
            )
        }

        !dto.externalUrl.isNullOrBlank() -> {
            StreamSource.External(url = dto.externalUrl)
        }

        !dto.ytId.isNullOrBlank() -> {
            StreamSource.YouTube(ytId = dto.ytId)
        }

        else -> {
            StreamSource.Unsupported(kind = resolveUnsupportedKind(dto))
        }
    }

private fun resolveUnsupportedKind(dto: StreamDto): String =
    when {
        !dto.nzbUrl.isNullOrBlank() -> "nzb"
        !dto.rarUrls.isNullOrEmpty() -> "rar"
        !dto.zipUrls.isNullOrEmpty() -> "zip"
        !dto.sevenZipUrls.isNullOrEmpty() -> "7zip"
        !dto.tgzUrls.isNullOrEmpty() -> "tgz"
        !dto.tarUrls.isNullOrEmpty() -> "tar"
        else -> "unknown"
    }
