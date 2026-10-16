package com.nexus.stream.domain.usecase

import com.nexus.stream.domain.model.MetaPreview

private val ADULT_TEXT_REGEX =
    Regex(
        """\b(porn|porno|pornographic|xxx|hentai|adult\s+film|adult\s+video)\b""",
        RegexOption.IGNORE_CASE,
    )

private val ADULT_GENRES =
    setOf(
        "adult",
        "erotic",
        "erotica",
        "hentai",
        "porn",
    )

fun isAdultText(text: String): Boolean {
    if (text.isBlank()) return false
    return ADULT_TEXT_REGEX.containsMatchIn(text)
}

fun MetaPreview.isAdultContent(): Boolean {
    if (isAdultText(name)) return true
    return genres.any { genre ->
        val trimmed = genre.trim().lowercase()
        trimmed in ADULT_GENRES || isAdultText(genre)
    }
}
