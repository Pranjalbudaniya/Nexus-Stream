package com.nexus.stream.data.remote.addon

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object AddonUrlBuilder {
    fun normalize(transportUrl: String): HttpUrl? {
        val httpUrl = toHttpsUrl(transportUrl) ?: return null
        val cleaned = cleanSegments(httpUrl.pathSegments)
        val builder = httpUrl.newBuilder().encodedPath("/")
        for (segment in cleaned) {
            builder.addPathSegment(segment)
        }
        return builder.build()
    }

    private fun toHttpsUrl(transportUrl: String): HttpUrl? {
        val trimmed = transportUrl.trim()
        val after =
            if (trimmed.startsWith("stremio://", ignoreCase = true)) {
                val sub = trimmed.substring(STREMIO_SCHEME_LENGTH)
                if (sub.isEmpty() || sub.startsWith('/')) null else "https://$sub"
            } else {
                trimmed
            }
        val httpUrl = after?.toHttpUrlOrNull()
        return httpUrl?.takeIf { it.scheme == "http" || it.scheme == "https" }
    }

    private fun cleanSegments(segments: List<String>): List<String> {
        val cleaned = segments.filter { it.isNotEmpty() }.toMutableList()
        if (cleaned.isNotEmpty() && cleaned.last().equals("manifest.json", ignoreCase = true)) {
            cleaned.removeAt(cleaned.size - 1)
        }
        return cleaned
    }

    fun manifestUrl(transportUrl: String): String? {
        val base = normalize(transportUrl) ?: return null
        return base
            .newBuilder()
            .addPathSegment("manifest.json")
            .build()
            .toString()
    }

    fun configureUrl(transportUrl: String): String? {
        val base = normalize(transportUrl) ?: return null
        return base
            .newBuilder()
            .addPathSegment("configure")
            .build()
            .toString()
    }

    fun fromDeepLink(uri: String?): String? {
        val trimmed = uri?.trim() ?: return null
        val isValid =
            trimmed.startsWith("stremio://", ignoreCase = true) &&
                trimmed.length > STREMIO_SCHEME_LENGTH &&
                !trimmed.substring(STREMIO_SCHEME_LENGTH).startsWith('/')
        val url =
            if (isValid) {
                "https://${trimmed.substring(STREMIO_SCHEME_LENGTH)}".toHttpUrlOrNull()
            } else {
                null
            }
        return url?.takeIf { it.host.isNotEmpty() }?.toString()
    }

    fun resourceUrl(
        transportUrl: String,
        resource: String,
        type: String,
        id: String,
        extra: Map<String, String> = emptyMap(),
    ): String? {
        val base = normalize(transportUrl) ?: return null
        val builder = base.newBuilder()
        builder.addPathSegment(resource)
        builder.addPathSegment(type)

        if (extra.isEmpty()) {
            builder.addPathSegment("$id.json")
        } else {
            builder.addPathSegment(id)
            val extrasQuery =
                extra.entries.joinToString("&") { (key, value) ->
                    "${encodeExtra(key)}=${encodeExtra(value)}"
                }
            builder.addEncodedPathSegment("$extrasQuery.json")
        }

        return builder.build().toString()
    }

    private fun encodeExtra(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")

    private const val STREMIO_SCHEME_LENGTH = 10
}
