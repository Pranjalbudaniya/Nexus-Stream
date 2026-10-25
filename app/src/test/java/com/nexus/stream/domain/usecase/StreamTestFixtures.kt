@file:Suppress("LongParameterList")

package com.nexus.stream.domain.usecase

import com.nexus.stream.domain.model.Codec
import com.nexus.stream.domain.model.HdrKind
import com.nexus.stream.domain.model.ParsedStream
import com.nexus.stream.domain.model.SourceFingerprint
import com.nexus.stream.domain.model.Stream
import com.nexus.stream.domain.model.StreamKind
import com.nexus.stream.domain.model.StreamMatch
import com.nexus.stream.domain.model.StreamSource
import com.nexus.stream.domain.model.hashAddonKey

internal val torrentKind = StreamKind.Torrent
internal val otherKind = StreamKind.Direct
internal val codecA = Codec.HEVC
internal val codecB = Codec.AVC
internal val hdrA = HdrKind.HDR

internal fun cand(
    key: String = "c",
    addon: String = "addon-1",
    kind: Any = torrentKind,
    binge: String? = null,
    hash: String? = null,
    res: Int? = 1080,
    codec: Any? = codecA,
    hdr: Set<Any> = emptySet(),
    size: Long? = 2_000_000_000L,
    seeders: Int? = 10,
    ok: Boolean = true,
    external: Boolean = false,
): ParsedStream {
    val streamKind = kind as StreamKind
    val streamSource =
        when {
            external -> StreamSource.External("https://example.com/external")
            hash != null -> StreamSource.Torrent(hash, 3, emptyList())
            else -> StreamSource.Url("https://example.com/video.mp4", emptyMap())
        }
    val stream =
        Stream(
            source = streamSource,
            name = "Test Stream",
            description = null,
            subtitles = emptyList(),
            bingeGroup = binge,
            notWebReady = false,
            videoSize = size,
            filename = null,
            videoHash = null,
        )
    return ParsedStream(
        key = key,
        stream = stream,
        addonNames = listOf("Addon 1"),
        addonKeys = listOf(addon),
        resolution = res,
        hdr = hdr.map { it as HdrKind }.toSet(),
        codec = codec as? Codec,
        sizeBytes = size,
        seeders = seeders,
        kind = streamKind,
        needsHeaders = false,
        displayName = "Candidate Stream",
        match = if (ok) StreamMatch.OK else StreamMatch.WRONG_SEASON,
    )
}

internal fun fp(
    addons: List<String> = listOf(hashAddonKey("addon-1")),
    kind: String = torrentKind.toString(),
    binge: String? = null,
    hash: String? = null,
    res: Int? = 1080,
    codec: String? = codecA.toString(),
    hdr: List<String> = emptyList(),
    size: Long? = 2_000_000_000L,
) = SourceFingerprint(
    addonKeyHashes = addons,
    kind = kind,
    bingeGroup = binge,
    infoHash = hash,
    resolution = res,
    codec = codec,
    hdr = hdr,
    sizeBytes = size,
)
