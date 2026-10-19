package com.nexus.stream.data.stream

import java.net.URLDecoder

const val MAX_HEAD_BYTES: Int = 16 * 1024

private const val REQUEST_LINE_PARTS_COUNT = 3
private const val HTTP_OK = 200
private const val HTTP_PARTIAL_CONTENT = 206
private const val HTTP_BAD_REQUEST = 400
private const val HTTP_NOT_FOUND = 404
private const val HTTP_METHOD_NOT_ALLOWED = 405
private const val HTTP_RANGE_NOT_SATISFIABLE = 416
private const val HTTP_INTERNAL_ERROR = 500
private const val HTTP_BAD_GATEWAY = 502
private const val HTTP_UNAVAILABLE = 503
private const val HTTP_GATEWAY_TIMEOUT = 504

private val METHOD_REGEX = Regex("^[A-Z]+$")
private val HEADER_TOKEN_REGEX = Regex("^[!#$%&'*+.^_`|~0-9A-Za-z-]+$")

data class HttpRequest(
    val method: String,
    val path: String,
    val headers: Map<String, String>,
)

fun parseRequestHead(head: String): HttpRequest? {
    if (head.isBlank()) return null

    val lines = head.split(Regex("\r\n|\n"))
    val firstLine = lines.firstOrNull()?.takeIf { it.isNotBlank() }
    val requestLine = firstLine?.let { parseRequestLine(it) }
    val headers = if (requestLine != null) parseHeaders(lines) else null

    return if (requestLine != null && headers != null) {
        HttpRequest(requestLine.first, requestLine.second, headers)
    } else {
        null
    }
}

private fun parseRequestLine(firstLine: String): Pair<String, String>? {
    val parts = firstLine.split(' ')
    val validParts = parts.takeIf { it.size == REQUEST_LINE_PARTS_COUNT } ?: return null
    val (method, target, version) = validParts
    val isMethodValid = METHOD_REGEX.matches(method)
    val isTargetValid = target.startsWith('/')
    val isVersionValid = version == "HTTP/1.0" || version == "HTTP/1.1"

    return if (isMethodValid && isTargetValid && isVersionValid) {
        val rawPath = target.substringBefore('?').substringBefore('#')
        decodePath(rawPath)?.let { path -> method to path }
    } else {
        null
    }
}

private fun decodePath(rawPath: String): String? =
    runCatching {
        URLDecoder.decode(rawPath.replace("+", "%2B"), "UTF-8")
    }.getOrNull()

private fun parseHeaders(lines: List<String>): Map<String, String>? {
    val headers = LinkedHashMap<String, String>()
    for (i in 1 until lines.size) {
        val line = lines[i]
        if (line.isNotEmpty()) {
            val entry = parseHeaderLine(line) ?: return null
            if (!headers.containsKey(entry.first)) {
                headers[entry.first] = entry.second
            }
        }
    }
    return headers
}

private fun parseHeaderLine(line: String): Pair<String, String>? {
    val colon = line.indexOf(':')
    val isFolded = line.startsWith(' ') || line.startsWith('\t')
    if (isFolded || colon <= 0) return null
    val name = line.substring(0, colon)
    val value = line.substring(colon + 1).trim()
    return if (HEADER_TOKEN_REGEX.matches(name)) name.lowercase() to value else null
}

fun formatResponseHead(
    status: Int,
    headers: List<Pair<String, String>>,
): String {
    val reason = statusReason(status)
    val sb =
        StringBuilder("HTTP/1.1 ")
            .append(status)
            .append(' ')
            .append(reason)
            .append("\r\n")

    for ((name, value) in headers) {
        val cleanName = name.replace("\r", " ").replace("\n", " ")
        val cleanValue = value.replace("\r", " ").replace("\n", " ")
        sb
            .append(cleanName)
            .append(": ")
            .append(cleanValue)
            .append("\r\n")
    }

    sb.append("\r\n")
    return sb.toString()
}

private fun statusReason(status: Int): String =
    when (status) {
        HTTP_OK -> "OK"
        HTTP_PARTIAL_CONTENT -> "Partial Content"
        HTTP_BAD_REQUEST -> "Bad Request"
        HTTP_NOT_FOUND -> "Not Found"
        HTTP_METHOD_NOT_ALLOWED -> "Method Not Allowed"
        HTTP_RANGE_NOT_SATISFIABLE -> "Range Not Satisfiable"
        HTTP_INTERNAL_ERROR -> "Internal Server Error"
        HTTP_BAD_GATEWAY -> "Bad Gateway"
        HTTP_UNAVAILABLE -> "Service Unavailable"
        HTTP_GATEWAY_TIMEOUT -> "Gateway Timeout"
        else -> "Unknown"
    }
