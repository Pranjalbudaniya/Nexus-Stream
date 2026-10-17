package com.nexus.stream.domain.usecase

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object MagnetLink {
    private val HEX_40_REGEX = Regex("""^[0-9a-fA-F]{40}$""")
    private val BASE32_32_REGEX = Regex("""^[2-7a-zA-Z]{32}$""")

    fun buildMagnet(
        infoHash: String,
        displayName: String?,
        trackers: List<String>,
    ): String? {
        val trimmedHash = infoHash.trim()
        if (!HEX_40_REGEX.matches(trimmedHash) && !BASE32_32_REGEX.matches(trimmedHash)) {
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
