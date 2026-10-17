package com.nexus.stream.domain.usecase

import com.nexus.stream.domain.model.AudioTag
import com.nexus.stream.domain.model.Codec
import com.nexus.stream.domain.model.DebridMark
import com.nexus.stream.domain.model.HdrKind
import com.nexus.stream.domain.model.ParsedStream
import com.nexus.stream.domain.model.ReleaseType
import com.nexus.stream.domain.model.Stream
import com.nexus.stream.domain.model.StreamKind
import com.nexus.stream.domain.model.StreamLanguages
import com.nexus.stream.domain.model.StreamMatch
import com.nexus.stream.domain.model.StreamSource

data class MatchTarget(
    val season: Int? = null,
    val episode: Int? = null,
    val year: Int? = null,
)

@Suppress("TooManyFunctions")
object StreamParsing {
    const val RES_2160 = 2160
    const val RES_1080 = 1080
    const val RES_720 = 720
    const val RES_480 = 480

    private const val BYTES_PER_MB = 1024L * 1024L
    private const val BYTES_PER_GB = 1024L * 1024L * 1024L

    private const val REGIONAL_INDICATOR_MIN = 0x1F1E6
    private const val REGIONAL_INDICATOR_MAX = 0x1F1FF

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

    private val SOURCE_SITE_REGEX =
        Regex("""(?:⚙\uFE0F?|(?:source|provider|site|indexer)\s*[:=])\s*([^💾👤\r\n\t]+)""", RegexOption.IGNORE_CASE)

    private val DUAL_AUDIO_REGEX = Regex("""\bdual[\s\-_.]*audio\b""", RegexOption.IGNORE_CASE)
    private val DUBBED_REGEX = Regex("""\b(dubbed|dub)\b""", RegexOption.IGNORE_CASE)
    private val MULTI_AUDIO_REGEX = Regex("""\b(multi|multi[\s\-_.]*audio)\b""", RegexOption.IGNORE_CASE)

    private val LANGUAGE_WORDS =
        listOf(
            "english",
            "japanese",
            "korean",
            "chinese",
            "mandarin",
            "cantonese",
            "hindi",
            "tamil",
            "telugu",
            "spanish",
            "latino",
            "french",
            "german",
            "italian",
            "portuguese",
            "russian",
            "arabic",
            "turkish",
            "thai",
            "polish",
            "dutch",
            "swedish",
            "norwegian",
            "danish",
            "finnish",
            "czech",
            "hungarian",
            "hebrew",
            "indonesian",
            "vietnamese",
        )

    private val ATMOS_REGEX = Regex("""\batmos\b""", RegexOption.IGNORE_CASE)
    private val TRUEHD_REGEX = Regex("""\btruehd\b""", RegexOption.IGNORE_CASE)
    private val DTS_HD_REGEX = Regex("""\bdts[\s\-_.]?hd\b""", RegexOption.IGNORE_CASE)
    private val DTS_REGEX = Regex("""\bdts\b""", RegexOption.IGNORE_CASE)
    private val DDP_REGEX = Regex("""\b(ddp|dd\+|eac3|e-ac-3)(?=[^a-z]|$)""", RegexOption.IGNORE_CASE)
    private val AC3_REGEX = Regex("""\b(ac3|ac-3)(?=[^a-z]|$)""", RegexOption.IGNORE_CASE)
    private val DD_REGEX = Regex("""\bdd(?=[^a-z]|$)""", RegexOption.IGNORE_CASE)
    private val AAC_REGEX = Regex("""\baac(?=[^a-z]|$)""", RegexOption.IGNORE_CASE)
    private val CHANNELS_REGEX = Regex("""(?<=[a-z\s._\-]|^)(2\.0|5\.1|7\.1)(?=[^0-9]|$)""", RegexOption.IGNORE_CASE)
    private val STEREO_REGEX = Regex("""\b(stereo|2ch)\b""", RegexOption.IGNORE_CASE)
    private val MONO_REGEX = Regex("""\b(mono|1ch)\b""", RegexOption.IGNORE_CASE)

    private val REMUX_REGEX = Regex("""\bremux\b""", RegexOption.IGNORE_CASE)
    private val BLURAY_REGEX = Regex("""\b(bluray|blu-ray|bdrip|brrip)\b""", RegexOption.IGNORE_CASE)
    private val WEB_DL_REGEX = Regex("""\b(web-dl|webdl)\b""", RegexOption.IGNORE_CASE)
    private val WEBRIP_REGEX = Regex("""\bwebrip\b""", RegexOption.IGNORE_CASE)
    private val HDRIP_REGEX = Regex("""\bhdrip\b""", RegexOption.IGNORE_CASE)
    private val HDTV_REGEX = Regex("""\bhdtv\b""", RegexOption.IGNORE_CASE)
    private val DVDRIP_REGEX = Regex("""\bdvdrip\b""", RegexOption.IGNORE_CASE)
    private val CAM_REGEX = Regex("""\b(cam|hdcam|ts|telesync|telecine)\b""", RegexOption.IGNORE_CASE)

    private val DEBRID_CACHED_REGEX = Regex("""\[([A-Z]{2,4})\+\]""")
    private val DEBRID_DOWNLOAD_REGEX = Regex("""\[([A-Z]{2,4})\s+download\]""", RegexOption.IGNORE_CASE)

    private val PACK_WORD_REGEX = Regex("""\bpack\b""", RegexOption.IGNORE_CASE)
    private val PACK_S_REGEX = Regex("""\bS(\d{1,2})\b(?!\s*E\d)""", RegexOption.IGNORE_CASE)
    private val PACK_SEASON_REGEX = Regex("""\bSeason\s*(\d{1,2})\b(?!\s*Episode)""", RegexOption.IGNORE_CASE)
    private val PACK_COMPLETE_REGEX = Regex("""\bcomplete\b""", RegexOption.IGNORE_CASE)
    private val PACK_SEASON_PACK_REGEX = Regex("""\bseason\s*pack\b""", RegexOption.IGNORE_CASE)

    fun parse(
        stream: Stream,
        addonName: String,
        addonKey: String = "",
        target: MatchTarget? = null,
    ): ParsedStream? {
        val (kind, key) = resolveKindAndKey(stream.source) ?: return null
        val text = buildText(stream)
        val displayName = resolveDisplayName(stream)
        val fileName = resolveFileName(stream)
        val needsHeaders = (stream.source as? StreamSource.Url)?.requestHeaders?.isNotEmpty() == true
        val match = StreamMatching.matchOf(fileName, target?.season, target?.episode, target?.year)

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
            fileName = fileName,
            sourceSite = parseSourceSite(text),
            languages = parseLanguages(text),
            audio = parseAudio(text),
            channels = parseChannels(text),
            releaseType = parseReleaseType(text),
            debrid = parseDebridMark(stream.name),
            isPack = parseIsPack(text),
            infoLines = parseInfoLines(stream),
            match = match,
        )
    }

    fun resolveFileName(stream: Stream): String? {
        val fromFilename = stream.filename?.trim()?.ifBlank { null }
        val descFirst =
            stream.description
                ?.lines()
                ?.map { it.trim() }
                ?.firstOrNull { it.isNotBlank() }
        val nameLines =
            stream.name
                ?.lines()
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
        return when {
            fromFilename != null -> fromFilename
            descFirst != null -> descFirst
            nameLines.isNullOrEmpty() -> null
            nameLines.size > 1 -> nameLines[1]
            else -> nameLines[0].replace(Regex("""^\[[^\]]+\]\s*"""), "").trim().ifBlank { nameLines[0] }
        }
    }

    fun parseSourceSite(text: String): String? {
        val match = SOURCE_SITE_REGEX.find(text) ?: return null
        val raw = match.groupValues[1].trim()
        val cleaned = raw.trimEnd('/', ',', ';').trim()
        return cleaned.ifBlank { null }
    }

    fun parseLanguages(text: String): StreamLanguages {
        val flags = extractFlags(text)
        val names = mutableListOf<String>()
        for (word in LANGUAGE_WORDS) {
            val wordRegex = Regex("""\b$word\b""", RegexOption.IGNORE_CASE)
            if (wordRegex.containsMatchIn(text)) {
                names.add(word.replaceFirstChar { it.uppercase() })
            }
        }
        val dualAudio = DUAL_AUDIO_REGEX.containsMatchIn(text)
        val dubbed = DUBBED_REGEX.containsMatchIn(text)
        val multi = MULTI_AUDIO_REGEX.containsMatchIn(text)

        return StreamLanguages(
            flags = flags,
            names = names.distinct(),
            dualAudio = dualAudio,
            dubbed = dubbed,
            multi = multi,
        )
    }

    fun parseAudio(text: String): Set<AudioTag> {
        val result = mutableSetOf<AudioTag>()
        if (ATMOS_REGEX.containsMatchIn(text)) result.add(AudioTag.ATMOS)
        if (TRUEHD_REGEX.containsMatchIn(text)) result.add(AudioTag.TRUEHD)
        if (DTS_HD_REGEX.containsMatchIn(text)) result.add(AudioTag.DTS_HD)
        if (DTS_REGEX.containsMatchIn(text)) result.add(AudioTag.DTS)
        if (DDP_REGEX.containsMatchIn(text)) result.add(AudioTag.DDP)
        if (AC3_REGEX.containsMatchIn(text) || (DD_REGEX.containsMatchIn(text) && !DDP_REGEX.containsMatchIn(text))) {
            result.add(AudioTag.AC3)
        }
        if (AAC_REGEX.containsMatchIn(text)) result.add(AudioTag.AAC)
        return result
    }

    fun parseChannels(text: String): String? {
        val exact = CHANNELS_REGEX.find(text)?.groupValues?.get(1)
        return when {
            exact != null -> exact
            STEREO_REGEX.containsMatchIn(text) -> "2.0"
            MONO_REGEX.containsMatchIn(text) -> "1.0"
            else -> null
        }
    }

    fun parseReleaseType(text: String): ReleaseType? =
        when {
            REMUX_REGEX.containsMatchIn(text) -> ReleaseType.REMUX
            BLURAY_REGEX.containsMatchIn(text) -> ReleaseType.BLURAY
            WEB_DL_REGEX.containsMatchIn(text) -> ReleaseType.WEB_DL
            WEBRIP_REGEX.containsMatchIn(text) -> ReleaseType.WEBRIP
            HDRIP_REGEX.containsMatchIn(text) -> ReleaseType.HDRIP
            HDTV_REGEX.containsMatchIn(text) -> ReleaseType.HDTV
            DVDRIP_REGEX.containsMatchIn(text) -> ReleaseType.DVDRIP
            CAM_REGEX.containsMatchIn(text) -> ReleaseType.CAM
            else -> null
        }

    fun parseDebridMark(name: String?): DebridMark? {
        if (name == null) return null
        val cachedMatch = DEBRID_CACHED_REGEX.find(name)
        val dlMatch = if (cachedMatch == null) DEBRID_DOWNLOAD_REGEX.find(name) else null
        return when {
            cachedMatch != null -> DebridMark(service = cachedMatch.groupValues[1], cached = true)
            dlMatch != null -> DebridMark(service = dlMatch.groupValues[1].uppercase(), cached = false)
            else -> null
        }
    }

    fun parseIsPack(text: String): Boolean =
        PACK_WORD_REGEX.containsMatchIn(text) ||
            PACK_S_REGEX.containsMatchIn(text) ||
            PACK_SEASON_REGEX.containsMatchIn(text) ||
            PACK_COMPLETE_REGEX.containsMatchIn(text) ||
            PACK_SEASON_PACK_REGEX.containsMatchIn(text)

    fun parseInfoLines(stream: Stream): List<String> {
        val lines = mutableListOf<String>()
        stream.name
            ?.lines()
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.let { lines.addAll(it) }
        stream.description
            ?.lines()
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.let { lines.addAll(it) }
        return lines
    }

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

    private fun extractFlags(text: String): List<String> {
        val flags = mutableListOf<String>()
        var i = 0
        while (i < text.length) {
            val cp1 = text.codePointAt(i)
            if (cp1 in REGIONAL_INDICATOR_MIN..REGIONAL_INDICATOR_MAX) {
                val len1 = Character.charCount(cp1)
                val nextIdx = i + len1
                if (nextIdx < text.length) {
                    val cp2 = text.codePointAt(nextIdx)
                    if (cp2 in REGIONAL_INDICATOR_MIN..REGIONAL_INDICATOR_MAX) {
                        val len2 = Character.charCount(cp2)
                        flags.add(text.substring(i, nextIdx + len2))
                        i = nextIdx + len2
                        continue
                    }
                }
            }
            i += Character.charCount(cp1)
        }
        return flags.distinct()
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
}

private fun resolveKindAndKey(source: StreamSource): Pair<StreamKind, String>? =
    when (source) {
        is StreamSource.Url -> StreamKind.Direct to "u:${source.url}"
        is StreamSource.Torrent -> StreamKind.Torrent to "t:${source.infoHash.lowercase()}:${source.fileIdx ?: -1}"
        is StreamSource.External -> StreamKind.ExternalLink to "e:${source.url}"
        is StreamSource.YouTube, is StreamSource.Unsupported -> null
    }

private fun buildText(stream: Stream): String = "${stream.name.orEmpty()}\n${stream.description.orEmpty()}"
