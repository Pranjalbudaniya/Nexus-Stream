package com.nexus.stream.domain.usecase

private const val DEFAULT_STREAM_NAME = "stream"
private const val MAX_FILENAME_LENGTH = 64
private const val HTTP_OK = 200
private const val HTTP_MULTIPLE_CHOICES = 300
private const val HTTP_BAD_REQUEST = 400
private const val HTTP_CLIENT_ERROR_MAX = 599
private const val HTTP_RANGE_NOT_SATISFIABLE = 416
private const val HTTP_BAD_GATEWAY = 502

private val RANGE_REGEX = Regex("^bytes=\\d*-\\d*$")
private val FILENAME_CHARS_REGEX = Regex("^[A-Za-z0-9._-]+$")
private val EXTENSION_REGEX = Regex(".*\\.[A-Za-z0-9]{1,5}$")

fun buildUpstreamHeaders(
    streamHeaders: Map<String, String>,
    playerRange: String?,
    playerUserAgent: String?,
    defaultUserAgent: String,
): List<Pair<String, String>> {
    val result = mutableListOf<Pair<String, String>>()
    var hasUserAgent = false

    for ((name, value) in streamHeaders) {
        val lower = name.lowercase()
        if (lower == "cookie" || lower == "accept-encoding" || lower == "range") {
            continue
        }
        if (lower == "user-agent") {
            hasUserAgent = true
        }
        result.add(name to value)
    }

    result.add("Accept-Encoding" to "identity")

    if (playerRange != null && playerRange != "bytes=-" && RANGE_REGEX.matches(playerRange)) {
        result.add("Range" to playerRange)
    }

    if (!hasUserAgent) {
        val ua = if (!playerUserAgent.isNullOrBlank()) playerUserAgent else defaultUserAgent
        result.add("User-Agent" to ua)
    }

    return result
}

fun parseCookieHeader(value: String): List<Pair<String, String>> {
    if (value.isBlank()) return emptyList()
    val result = mutableListOf<Pair<String, String>>()
    val tokens = value.split(';')
    for (token in tokens) {
        val trimmed = token.trim()
        val eq = trimmed.indexOf('=')
        if (eq > 0) {
            val name = trimmed.substring(0, eq).trim()
            if (name.isNotEmpty()) {
                val cookieValue = trimmed.substring(eq + 1).trim()
                result.add(name to cookieValue)
            }
        }
    }
    return result
}

fun filterResponseHeaders(
    status: Int,
    upstream: List<Pair<String, String>>,
): List<Pair<String, String>> {
    val result = mutableListOf<Pair<String, String>>()

    if (status < HTTP_BAD_REQUEST) {
        for ((name, value) in upstream) {
            when (name.lowercase()) {
                "content-type" -> result.add("Content-Type" to value)
                "content-length" -> result.add("Content-Length" to value)
                "content-range" -> result.add("Content-Range" to value)
                "accept-ranges" -> result.add("Accept-Ranges" to value)
                "last-modified" -> result.add("Last-Modified" to value)
                "etag" -> result.add("ETag" to value)
            }
        }
    } else {
        if (status == HTTP_RANGE_NOT_SATISFIABLE) {
            for ((name, value) in upstream) {
                if (name.equals("content-range", ignoreCase = true)) {
                    result.add("Content-Range" to value)
                }
            }
        }
        result.add("Content-Length" to "0")
    }

    result.add("Connection" to "close")
    result.add("Cache-Control" to "no-store")
    return result
}

fun mapUpstreamStatus(code: Int): Int =
    when (code) {
        in HTTP_OK until HTTP_MULTIPLE_CHOICES, in HTTP_BAD_REQUEST..HTTP_CLIENT_ERROR_MAX -> code
        else -> HTTP_BAD_GATEWAY
    }

fun proxyFileName(url: String): String {
    val clean = url.substringBefore('?').substringBefore('#')
    val segment = clean.substringAfterLast('/')
    val isValid =
        segment.isNotEmpty() &&
            segment.length <= MAX_FILENAME_LENGTH &&
            FILENAME_CHARS_REGEX.matches(segment) &&
            EXTENSION_REGEX.matches(segment)

    return if (isValid) segment else DEFAULT_STREAM_NAME
}
