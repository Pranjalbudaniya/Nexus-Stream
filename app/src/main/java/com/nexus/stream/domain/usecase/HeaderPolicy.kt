package com.nexus.stream.domain.usecase

private const val MAX_HEADER_COUNT = 20
private const val MAX_HEADER_VALUE_LENGTH = 4096
private const val MAX_TOTAL_HEADERS_LENGTH = 16384

private val HEADER_NAME_REGEX = Regex("^[!#$%&'*+.^_`|~0-9A-Za-z-]+$")

private val DENIED_HEADERS: Set<String> =
    setOf(
        "host",
        "connection",
        "keep-alive",
        "proxy-connection",
        "proxy-authenticate",
        "proxy-authorization",
        "te",
        "trailer",
        "transfer-encoding",
        "upgrade",
        "content-length",
        "range",
        "if-range",
        "accept-encoding",
        "expect",
    )

fun sanitizeRequestHeaders(raw: Map<String, String>): Map<String, String> {
    val result = LinkedHashMap<String, String>()
    val seenNames = mutableSetOf<String>()
    var totalLength = 0
    var shouldStop = false

    val iterator = raw.iterator()
    while (!shouldStop && iterator.hasNext() && result.size < MAX_HEADER_COUNT) {
        val (rawName, rawValue) = iterator.next()
        val name = rawName.trim()
        val lowerName = name.lowercase()
        val value = rawValue.trim()

        if (isValidEntry(name, lowerName, value, seenNames)) {
            val entryLength = name.length + value.length
            if (totalLength + entryLength <= MAX_TOTAL_HEADERS_LENGTH) {
                seenNames.add(lowerName)
                result[name] = value
                totalLength += entryLength
            } else {
                shouldStop = true
            }
        }
    }

    return result
}

private fun isValidEntry(
    name: String,
    lowerName: String,
    value: String,
    seenNames: Set<String>,
): Boolean =
    lowerName !in seenNames &&
        lowerName !in DENIED_HEADERS &&
        HEADER_NAME_REGEX.matches(name) &&
        isValidHeaderValue(value)

private fun isValidHeaderValue(value: String): Boolean =
    value.isNotEmpty() &&
        value.length <= MAX_HEADER_VALUE_LENGTH &&
        value.all { char ->
            (char in '\u0020'..'\u007E') || char == '\t'
        }
