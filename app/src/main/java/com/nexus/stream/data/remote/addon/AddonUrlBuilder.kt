package com.nexus.stream.data.remote.addon

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object AddonUrlBuilder {
    fun normalize(transportUrl: String): HttpUrl? {
        val trimmed = transportUrl.trim()
        val converted =
            if (trimmed.startsWith("stremio://", ignoreCase = true)) {
                "https://${trimmed.substring(STREMIO_SCHEME_LENGTH)}"
            } else {
                trimmed
            }

        val httpUrl = converted.toHttpUrlOrNull()
        if (httpUrl == null || (httpUrl.scheme != "http" && httpUrl.scheme != "https")) {
            return null
        }

        val cleanedSegments = httpUrl.pathSegments.toMutableList()
        if (cleanedSegments.isNotEmpty() && cleanedSegments.last().isEmpty()) {
            cleanedSegments.removeAt(cleanedSegments.size - 1)
        }
        if (cleanedSegments.isNotEmpty() && cleanedSegments.last().equals("manifest.json", ignoreCase = true)) {
            cleanedSegments.removeAt(cleanedSegments.size - 1)
        }
        if (cleanedSegments.isNotEmpty() && cleanedSegments.last().isEmpty()) {
            cleanedSegments.removeAt(cleanedSegments.size - 1)
        }

        val builder = httpUrl.newBuilder()
        builder.encodedPath("/")
        for (segment in cleanedSegments) {
            if (segment.isNotEmpty()) {
                builder.addPathSegment(segment)
            }
        }

        return builder.build()
    }

    fun manifestUrl(transportUrl: String): String? {
        val base = normalize(transportUrl) ?: return null
        return base
            .newBuilder()
            .addPathSegment("manifest.json")
            .build()
            .toString()
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
