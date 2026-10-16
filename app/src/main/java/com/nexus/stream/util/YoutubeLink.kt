package com.nexus.stream.util

private val YOUTUBE_ID_REGEX = Regex("^[A-Za-z0-9_-]{6,20}$")

fun youtubeWatchUrlOrNull(id: String?): String? =
    id?.trim()?.takeIf { YOUTUBE_ID_REGEX.matches(it) }?.let { validId ->
        "https://www.youtube.com/watch?v=$validId"
    }
