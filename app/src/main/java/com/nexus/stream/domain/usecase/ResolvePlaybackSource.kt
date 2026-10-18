package com.nexus.stream.domain.usecase

import com.nexus.stream.domain.model.EpisodeHint
import com.nexus.stream.domain.model.PlaybackSource
import com.nexus.stream.domain.model.StreamSource
import com.nexus.stream.domain.model.TorrentRequest
import com.nexus.stream.domain.model.UnsupportedReason

private const val MAX_URL_LENGTH = 8192
private const val MIN_PRINTABLE_ASCII_CODE = 0x20
private const val DEL_ASCII_CODE = 0x7F
private const val SCHEME_SEPARATOR = "://"
private val HEX_40_REGEX = Regex("^[0-9a-f]{40}$")

internal sealed interface UrlResult {
    data class Ok(
        val url: String,
    ) : UrlResult

    data class Bad(
        val reason: UnsupportedReason,
    ) : UrlResult
}

fun resolvePlaybackSource(
    source: StreamSource,
    hint: EpisodeHint?,
): PlaybackSource =
    when (source) {
        is StreamSource.Url -> {
            resolveUrlSource(source, hint)
        }

        is StreamSource.Torrent -> {
            resolveTorrentSource(source, hint)
        }

        is StreamSource.External -> {
            resolveExternalSource(source)
        }

        is StreamSource.YouTube, is StreamSource.Unsupported -> {
            PlaybackSource.Unsupported(UnsupportedReason.NO_SOURCE)
        }
    }

private fun resolveUrlSource(
    source: StreamSource.Url,
    hint: EpisodeHint?,
): PlaybackSource {
    val trimmed = source.url.trim()
    return when {
        trimmed.isEmpty() -> PlaybackSource.Unsupported(UnsupportedReason.NO_SOURCE)
        trimmed.startsWith("magnet:", ignoreCase = true) -> resolveMagnetUrl(trimmed, hint)
        else -> resolveDirectHttpUrl(source)
    }
}

private fun resolveMagnetUrl(
    magnetUrl: String,
    hint: EpisodeHint?,
): PlaybackSource {
    val magnet = parseMagnetLink(magnetUrl)
    return if (magnet == null) {
        PlaybackSource.Unsupported(UnsupportedReason.INVALID_HASH)
    } else {
        PlaybackSource.Torrent(
            TorrentRequest(
                infoHash = magnet.infoHash,
                trackers = magnet.trackers,
                fileIdx = null,
                episodeHint = hint,
            ),
        )
    }
}

private fun resolveDirectHttpUrl(source: StreamSource.Url): PlaybackSource =
    when (val urlResult = normalizeDirectUrl(source.url)) {
        is UrlResult.Bad -> {
            PlaybackSource.Unsupported(urlResult.reason)
        }

        is UrlResult.Ok -> {
            val headers = sanitizeRequestHeaders(source.requestHeaders)
            if (headers.isEmpty()) {
                PlaybackSource.Direct(urlResult.url)
            } else {
                PlaybackSource.DirectWithHeaders(urlResult.url, headers)
            }
        }
    }

private fun resolveTorrentSource(
    source: StreamSource.Torrent,
    hint: EpisodeHint?,
): PlaybackSource {
    val trimmedHash = source.infoHash.trim().lowercase()
    return if (!HEX_40_REGEX.matches(trimmedHash)) {
        PlaybackSource.Unsupported(UnsupportedReason.INVALID_HASH)
    } else {
        PlaybackSource.Torrent(
            TorrentRequest(
                infoHash = trimmedHash,
                trackers = source.trackers,
                fileIdx = source.fileIdx?.takeIf { it >= 0 },
                episodeHint = hint,
            ),
        )
    }
}

private fun resolveExternalSource(source: StreamSource.External): PlaybackSource {
    val trimmed = source.url.trim()
    if (trimmed.isEmpty()) return PlaybackSource.Unsupported(UnsupportedReason.NO_SOURCE)

    return when (val urlResult = normalizeDirectUrl(source.url)) {
        is UrlResult.Bad -> PlaybackSource.Unsupported(urlResult.reason)
        is UrlResult.Ok -> PlaybackSource.External(urlResult.url)
    }
}

internal fun normalizeDirectUrl(raw: String): UrlResult {
    val trimmed = raw.trim()
    val badReason = validateUrlSyntax(trimmed)
    if (badReason != null) {
        return UrlResult.Bad(badReason)
    }

    val schemeSep = trimmed.indexOf(SCHEME_SEPARATOR)
    val scheme = trimmed.substring(0, schemeSep).lowercase()
    val afterScheme = trimmed.substring(schemeSep + SCHEME_SEPARATOR.length).replace(" ", "%20")
    return UrlResult.Ok("$scheme://$afterScheme")
}

private fun validateUrlSyntax(trimmed: String): UnsupportedReason? {
    val schemeSep = trimmed.indexOf(SCHEME_SEPARATOR)
    val rawScheme = if (schemeSep > 0) trimmed.substring(0, schemeSep) else ""
    val isHttpOrHttps = rawScheme.equals("http", ignoreCase = true) || rawScheme.equals("https", ignoreCase = true)

    return when {
        trimmed.isEmpty() || trimmed.length > MAX_URL_LENGTH -> UnsupportedReason.BAD_URL
        trimmed.any { it.code < MIN_PRINTABLE_ASCII_CODE || it.code == DEL_ASCII_CODE } -> UnsupportedReason.BAD_URL
        schemeSep <= 0 -> UnsupportedReason.BAD_SCHEME
        !isHttpOrHttps -> UnsupportedReason.BAD_SCHEME
        extractHost(trimmed.substring(schemeSep + SCHEME_SEPARATOR.length)).isEmpty() -> UnsupportedReason.BAD_URL
        else -> null
    }
}

private fun extractHost(afterScheme: String): String {
    val authorityEnd = afterScheme.indexOfAny(charArrayOf('/', '?', '#'))
    val rawAuthority = if (authorityEnd >= 0) afterScheme.substring(0, authorityEnd) else afterScheme
    val atIndex = rawAuthority.lastIndexOf('@')
    val hostAndPort = if (atIndex >= 0) rawAuthority.substring(atIndex + 1) else rawAuthority

    return if (hostAndPort.startsWith('[') && hostAndPort.contains(']')) {
        hostAndPort.substring(0, hostAndPort.indexOf(']') + 1)
    } else {
        hostAndPort.substringBefore(':')
    }
}
