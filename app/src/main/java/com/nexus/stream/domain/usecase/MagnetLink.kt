package com.nexus.stream.domain.usecase

import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class MagnetLink(
    val infoHash: String,
    val trackers: List<String>,
) {
    companion object {
        private val BUILD_HEX_40_REGEX = Regex("^[0-9a-fA-F]{40}$")
        private val BUILD_BASE32_32_REGEX = Regex("^[2-7a-zA-Z]{32}$")

        fun buildMagnet(
            infoHash: String,
            displayName: String?,
            trackers: List<String>,
        ): String? {
            val trimmedHash = infoHash.trim()
            if (!BUILD_HEX_40_REGEX.matches(trimmedHash) && !BUILD_BASE32_32_REGEX.matches(trimmedHash)) {
                return null
            }

            val sb = StringBuilder("magnet:?xt=urn:btih:").append(trimmedHash)

            if (!displayName.isNullOrBlank()) {
                val encodedName = encode(displayName.trim())
                sb.append("&dn=").append(encodedName)
            }

            for (tracker in trackers) {
                val trimmedTracker = tracker.trim()
                if (trimmedTracker.isNotEmpty()) {
                    val encodedTracker = encode(trimmedTracker)
                    sb.append("&tr=").append(encodedTracker)
                }
            }

            return sb.toString()
        }

        private fun encode(value: String): String =
            URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")
    }
}

private val HEX_40_REGEX = Regex("^[0-9a-fA-F]{40}$")
private val BTIH_REGEX = Regex("^urn:btih:([0-9a-fA-F]{40})$", RegexOption.IGNORE_CASE)
private const val MAGNET_PREFIX = "magnet:?"

fun parseMagnetLink(text: String): MagnetLink? {
    val clean = text.replace(Regex("\\s+"), "")
    return when {
        clean.isEmpty() -> null
        HEX_40_REGEX.matches(clean) -> MagnetLink(infoHash = clean.lowercase(), trackers = emptyList())
        else -> parseMagnetUri(clean)
    }
}

private fun parseMagnetUri(uri: String): MagnetLink? {
    val query =
        uri
            .takeIf { it.startsWith(MAGNET_PREFIX, ignoreCase = true) }
            ?.substring(MAGNET_PREFIX.length)
            ?.takeIf { it.isNotEmpty() } ?: return null

    val pairs = query.split('&').mapNotNull { parseParamPair(it) }
    val hash =
        pairs.firstNotNullOfOrNull { (k, v) ->
            if (k.equals("xt", ignoreCase = true)) {
                BTIH_REGEX
                    .matchEntire(v)
                    ?.groupValues
                    ?.get(1)
                    ?.lowercase()
            } else {
                null
            }
        }

    return hash?.let { validHash ->
        val trackers =
            pairs.mapNotNull { (k, v) ->
                if (k.equals("tr", ignoreCase = true)) decodeTrackerOrNull(v) else null
            }
        MagnetLink(infoHash = validHash, trackers = trackers)
    }
}

private fun parseParamPair(part: String): Pair<String, String>? {
    val eqIndex = part.indexOf('=')
    if (eqIndex <= 0) return null
    return part.substring(0, eqIndex) to part.substring(eqIndex + 1)
}

private fun decodeTrackerOrNull(encoded: String): String? =
    runCatching {
        URLDecoder.decode(encoded, StandardCharsets.UTF_8.name())
    }.getOrNull()
