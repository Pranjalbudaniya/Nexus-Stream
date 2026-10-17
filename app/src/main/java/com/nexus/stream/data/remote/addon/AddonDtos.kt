package com.nexus.stream.data.remote.addon

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ManifestDto(
    val id: String? = null,
    val version: String? = null,
    val name: String? = null,
    val description: String? = null,
    val logo: String? = null,
    val background: String? = null,
    val types: List<String>? = null,
    val idPrefixes: List<String>? = null,
    val resources: JsonElement? = null,
    val catalogs: List<CatalogDto>? = null,
    val behaviorHints: ManifestBehaviorHintsDto? = null,
    val addonCatalogs: List<CatalogDto>? = null,
)

@Serializable
data class ManifestBehaviorHintsDto(
    val adult: Boolean? = null,
    val p2p: Boolean? = null,
    val configurable: Boolean? = null,
    val configurationRequired: Boolean? = null,
)

@Serializable
data class CatalogDto(
    val type: String? = null,
    val id: String? = null,
    val name: String? = null,
    val extra: List<ExtraDto>? = null,
    val extraSupported: List<String>? = null,
    val extraRequired: List<String>? = null,
)

@Serializable
data class ExtraDto(
    val name: String? = null,
    val isRequired: Boolean? = null,
    val options: List<String>? = null,
    val optionsLimit: Int? = null,
)

@Serializable
data class ResourceObjectDto(
    val name: String? = null,
    val types: List<String>? = null,
    val idPrefixes: List<String>? = null,
)

@Serializable
data class CatalogResponseDto(
    val metas: List<JsonElement>? = null,
)

@Serializable
data class MetaPreviewDto(
    val id: String? = null,
    val type: String? = null,
    val name: String? = null,
    val poster: String? = null,
    val posterShape: String? = null,
    val background: String? = null,
    val logo: String? = null,
    val description: String? = null,
    val releaseInfo: String? = null,
    val imdbRating: JsonElement? = null,
    val genres: List<String>? = null,
)

@Serializable
data class MetaResponseDto(
    val meta: JsonElement? = null,
)

@Serializable
data class MetaDetailDto(
    val id: String? = null,
    val type: String? = null,
    val name: String? = null,
    val poster: String? = null,
    val posterShape: String? = null,
    val background: String? = null,
    val logo: String? = null,
    val description: String? = null,
    val releaseInfo: String? = null,
    val released: String? = null,
    val runtime: String? = null,
    val imdbRating: JsonElement? = null,
    val genres: List<String>? = null,
    val cast: List<String>? = null,
    val director: List<String>? = null,
    val directors: List<String>? = null,
    val country: String? = null,
    val language: String? = null,
    val website: String? = null,
    val links: List<MetaLinkDto>? = null,
    val trailers: JsonElement? = null,
    val trailerStreams: List<TrailerStreamDto>? = null,
    val videos: List<VideoDto>? = null,
    val defaultVideoId: String? = null,
)

@Serializable
data class MetaLinkDto(
    val name: String? = null,
    val category: String? = null,
    val url: String? = null,
)

@Serializable
data class TrailerStreamDto(
    val ytId: String? = null,
)

@Serializable
data class VideoDto(
    val id: String? = null,
    val title: String? = null,
    val name: String? = null,
    val description: String? = null,
    val released: String? = null,
    val season: JsonElement? = null,
    val episode: JsonElement? = null,
    val thumbnail: String? = null,
    val overview: String? = null,
)

@Serializable
data class StreamResponseDto(
    val streams: List<JsonElement>? = null,
)

@Serializable
data class StreamDto(
    val url: String? = null,
    val ytId: String? = null,
    val infoHash: String? = null,
    val fileIdx: JsonElement? = null,
    val externalUrl: String? = null,
    val nzbUrl: String? = null,
    val rarUrls: List<String>? = null,
    val zipUrls: List<String>? = null,
    @SerialName("7zipUrls")
    val sevenZipUrls: List<String>? = null,
    val tgzUrls: List<String>? = null,
    val tarUrls: List<String>? = null,
    val name: String? = null,
    val title: String? = null,
    val description: String? = null,
    val subtitles: List<SubtitleDto>? = null,
    val sources: List<String>? = null,
    val behaviorHints: StreamBehaviorHintsDto? = null,
)

@Serializable
data class StreamBehaviorHintsDto(
    val bingeGroup: String? = null,
    val notWebReady: Boolean? = null,
    val proxyHeaders: ProxyHeadersDto? = null,
    val videoHash: String? = null,
    val videoSize: JsonElement? = null,
    val filename: String? = null,
    val countryWhitelist: List<String>? = null,
)

@Serializable
data class ProxyHeadersDto(
    val request: Map<String, String>? = null,
    val response: Map<String, String>? = null,
)

@Serializable
data class SubtitleDto(
    val id: String? = null,
    val url: String? = null,
    val lang: String? = null,
    val label: String? = null,
)

@Serializable
data class SubtitleResponseDto(
    val subtitles: List<JsonElement>? = null,
)
