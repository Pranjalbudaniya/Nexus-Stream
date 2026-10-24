package com.nexus.stream.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.util.Locale

@Serializable
data class SourceFingerprint(
    val addonKeyHashes: List<String>,
    val kind: String,
    val bingeGroup: String? = null,
    val infoHash: String? = null,
    val resolution: Int? = null,
    val codec: String? = null,
    val hdr: List<String> = emptyList(),
    val sizeBytes: Long? = null,
) {
    override fun toString(): String = "SourceFingerprint(kind=$kind, resolution=$resolution)"
}

val StreamKind.name: String
    get() =
        when (this) {
            is StreamKind.Direct -> "Direct"
            is StreamKind.Torrent -> "Torrent"
            is StreamKind.ExternalLink -> "ExternalLink"
        }

private const val HASH_LENGTH = 16

fun hashAddonKey(key: String): String {
    val md = MessageDigest.getInstance("SHA-256")
    val digest = md.digest(key.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { String.format(Locale.US, "%02x", it) }.take(HASH_LENGTH)
}

fun fingerprintOf(stream: ParsedStream): SourceFingerprint? {
    val isExternal = stream.stream.source is StreamSource.External || stream.kind is StreamKind.ExternalLink
    if (stream.addonKeys.isEmpty() || isExternal || stream.match != StreamMatch.OK) {
        return null
    }

    val infoHash = (stream.stream.source as? StreamSource.Torrent)?.infoHash?.lowercase()
    return SourceFingerprint(
        addonKeyHashes =
            stream.addonKeys
                .map { hashAddonKey(it) }
                .distinct()
                .sorted(),
        kind = stream.kind.name,
        bingeGroup = stream.stream.bingeGroup,
        infoHash = infoHash,
        resolution = stream.resolution,
        codec = stream.codec?.name,
        hdr = stream.hdr.map { it.name }.sorted(),
        sizeBytes = stream.sizeBytes,
    )
}

object SourceFingerprintJson {
    private val json =
        Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }

    fun encode(fp: SourceFingerprint): String = json.encodeToString(SourceFingerprint.serializer(), fp)

    fun decode(text: String?): SourceFingerprint? {
        if (text.isNullOrBlank()) return null
        return try {
            json.decodeFromString(SourceFingerprint.serializer(), text)
        } catch (_: Exception) {
            null
        }
    }
}
