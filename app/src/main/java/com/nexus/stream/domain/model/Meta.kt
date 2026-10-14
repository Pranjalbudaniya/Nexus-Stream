package com.nexus.stream.domain.model

enum class PosterShape {
    POSTER,
    SQUARE,
    LANDSCAPE,
}

data class MetaPreview(
    val id: String,
    val type: String,
    val name: String,
    val poster: String?,
    val posterShape: PosterShape,
    val background: String?,
    val logo: String?,
    val description: String?,
    val releaseInfo: String?,
    val imdbRating: String?,
    val genres: List<String>,
)

data class MetaDetail(
    val id: String,
    val type: String,
    val name: String,
    val poster: String?,
    val posterShape: PosterShape,
    val background: String?,
    val logo: String?,
    val description: String?,
    val releaseInfo: String?,
    val released: String?,
    val runtime: String?,
    val imdbRating: String?,
    val genres: List<String>,
    val cast: List<String>,
    val directors: List<String>,
    val country: String?,
    val language: String?,
    val website: String?,
    val links: List<MetaLink>,
    val trailers: List<String>,
    val videos: List<Video>,
    val defaultVideoId: String?,
)

data class Video(
    val id: String,
    val title: String,
    val released: String?,
    val season: Int?,
    val episode: Int?,
    val thumbnail: String?,
    val overview: String?,
)

data class MetaLink(
    val name: String,
    val category: String,
    val url: String,
)
