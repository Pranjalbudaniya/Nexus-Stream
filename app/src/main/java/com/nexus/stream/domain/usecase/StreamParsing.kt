package com.nexus.stream.domain.usecase

import com.nexus.stream.domain.model.Codec
import com.nexus.stream.domain.model.HdrKind
import com.nexus.stream.domain.model.ParsedStream
import com.nexus.stream.domain.model.Stream
import com.nexus.stream.domain.model.StreamKind
import com.nexus.stream.domain.model.StreamSource

object StreamParsing {
    const val RES_2160 = 2160
    const val RES_1080 = 1080
    const val RES_720 = 720
    const val RES_480 = 480

    private const val BYTES_PER_MB = 1024L * 1024L
    private const val BYTES_PER_GB = 1024L * 1024L * 1024L

    private val RES_2160_REGEX = Regex("""\b(2160p|4k|uhd)\b""", RegexOption.IGNORE_CASE)
    private val RES_1080_REGEX = Regex("""\b1080p\b""", RegexOption.IGNORE_CASE)
    private val RES_720_REGEX = Regex("""\b720p\b""", RegexOption.IGNORE_CASE)
    private val RES_480_REGEX = Regex("""\b(480p|360p|sd)\b""", RegexOption.IGNORE_CASE)

    private val HDR10_PLUS_REGEX = Regex("""\bhdr10\+""", RegexOption.IGNORE_CASE)
    private val HDR10_REGEX = Regex("""\bhdr10\b""", RegexOption.IGNORE_CASE)
    private val HDR_REGEX = Regex("""\bhdr\b""", RegexOption.IGNORE_CASE)
    private val DV_REGEX = Regex("""\b(dv|dolby\s*vision)\b""", RegexOption.IGNORE_CASE)

    private val HEVC_REGEX = Regex("""\b(x265|hevc|h\.?265)\b""", RegexOption.IGNORE_CASE)
    private val AVC_REGEX = Regex("""\b(x264|h\.?264|avc)\b""", RegexOption.IGNORE_CASE)
    private val AV1_REGEX = Regex("""\bav1\b""", RegexOption.IGNORE_CASE)

    private val SEEDERS_REGEX = Regex("""(?:👤\s*|seed(?:er)?s?\s*[:=]?\s*)(\d+)""", RegexOption.IGNORE_CASE)
    private val SIZE_REGEX = Regex("""(\d+(?:[.,]\d+)?)\s*(GB|GiB|MB|MiB)\b""", RegexOption.IGNORE_CASE)

    fun parse(
        stream: Stream,
        addonName: String,
        addonKey: String = "",
    ): ParsedStream? {
        val (kind, key) = resolveKindAndKey(stream.source) ?: return null
        val text = buildText(stream)
        val displayName = resolveDisplayName(stream)
        val needsHeaders = (stream.source as? StreamSource.Url)?.requestHeaders?.isNotEmpty() == true

        return ParsedStream(
            key = key,
            stream = stream,
            addonNames = listOf(addonName),
            addonKeys = if (addonKey.isNotBlank()) listOf(addonKey) else emptyList(),
            resolution = parseResolution(text),
            hdr = parseHdr(text),
            codec = parseCodec(text),
            sizeBytes = parseSize(stream, text),
            seeders = parseSeeders(text),
            kind = kind,
            needsHeaders = needsHeaders,
            displayName = displayName,
        )
    }

    private fun resolveKindAndKey(source: StreamSource): Pair<StreamKind, String>? =
        when (source) {
            is StreamSource.Url -> StreamKind.Direct to "u:${source.url}"
            is StreamSource.Torrent -> StreamKind.Torrent to "t:${source.infoHash.lowercase()}:${source.fileIdx ?: -1}"
            is StreamSource.External -> StreamKind.ExternalLink to "e:${source.url}"
            is StreamSource.YouTube, is StreamSource.Unsupported -> null
        }

    private fun buildText(stream: Stream): String = "${stream.name.orEmpty()}\n${stream.description.orEmpty()}"

    fun parseResolution(text: String): Int? =
        when {
            RES_2160_REGEX.containsMatchIn(text) -> RES_2160
            RES_1080_REGEX.containsMatchIn(text) -> RES_1080
            RES_720_REGEX.containsMatchIn(text) -> RES_720
            RES_480_REGEX.containsMatchIn(text) -> RES_480
            else -> null
        }

    fun parseHdr(text: String): Set<HdrKind> {
        val result = mutableSetOf<HdrKind>()
        when {
            HDR10_PLUS_REGEX.containsMatchIn(text) -> result.add(HdrKind.HDR10_PLUS)
            HDR10_REGEX.containsMatchIn(text) -> result.add(HdrKind.HDR10)
            HDR_REGEX.containsMatchIn(text) -> result.add(HdrKind.HDR)
        }
        if (DV_REGEX.containsMatchIn(text)) {
            result.add(HdrKind.DOLBY_VISION)
        }
        return result
    }

    fun parseCodec(text: String): Codec? =
        when {
            HEVC_REGEX.containsMatchIn(text) -> Codec.HEVC
            AVC_REGEX.containsMatchIn(text) -> Codec.AVC
            AV1_REGEX.containsMatchIn(text) -> Codec.AV1
            else -> null
        }

    fun parseSeeders(text: String): Int? =
        SEEDERS_REGEX
            .find(text)
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()

    fun parseSize(
        stream: Stream,
        text: String,
    ): Long? {
        val videoSize = stream.videoSize
        if (videoSize != null && videoSize > 0) return videoSize
        return parseTextSize(text)
    }

    private fun parseTextSize(text: String): Long? {
        val match = SIZE_REGEX.find(text)
        val num =
            match
                ?.groupValues
                ?.get(1)
                ?.replace(',', '.')
                ?.toDoubleOrNull()
        val unit = match?.groupValues?.get(2)
        if (num == null || unit == null) return null
        val multiplier = if (unit.startsWith("G", ignoreCase = true)) BYTES_PER_GB else BYTES_PER_MB
        return (num * multiplier).toLong()
    }

    fun resolveDisplayName(stream: Stream): String {
        val fromName =
            stream.name
                ?.lines()
                ?.firstOrNull { it.isNotBlank() }
                ?.trim()
        val fromDesc =
            stream.description
                ?.lines()
                ?.firstOrNull { it.isNotBlank() }
                ?.trim()
        return fromName ?: fromDesc ?: stream.filename?.trim() ?: "Stream"
    }
}
