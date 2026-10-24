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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@Suppress("LongParameterList")
class StreamMatcherTest {
    // ---- write these once, with the real values/constructors (the only free part) ----
    // The five values must print their enum name from toString() (the tests compare fingerprint text to `kind.name`);
    // if an enum overrides toString(), use `.name` in the `fp(...)` helper below and list it under Deviations.
    private val torrentKind = StreamKind.Torrent
    private val otherKind = StreamKind.Direct
    private val codecA = Codec.HEVC
    private val codecB = Codec.AVC
    private val hdrA = HdrKind.HDR

    /** A ParsedStream with these properties. `external = true` gives a StreamSource.External source.
     *  A torrent source (infoHash = hash, fileIdx = 3) when hash != null and not external, otherwise a URL source.
     *  addonKeys = listOf(addon). */
    private fun cand(
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
    ): com.nexus.stream.domain.model.ParsedStream {
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
    // ---- end of the free part ----

    private fun fp(
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

    @Test
    fun SM1_equalBingeGroup_sameAddon_matchesWithBingeTier() {
        val result =
            matchSource(
                fp(binge = "g1"),
                listOf(cand(key = "x", binge = "g2"), cand(key = "c1", binge = "g1", res = 720)),
            )
        assertEquals("c1", result?.stream?.key)
        assertEquals(MatchTier.BINGE_GROUP, result?.tier)
    }

    @Test
    fun SM2_otherAddon_neverMatches_evenWithEqualBingeGroup() {
        val result = matchSource(fp(binge = "g1"), listOf(cand(addon = "addon-2", binge = "g1")))
        assertNull(result)
    }

    @Test
    fun SM3_infoHash_ignoresCase_andFileIndex() {
        val result = matchSource(fp(hash = "abcdef0123"), listOf(cand(key = "h", hash = "ABCDEF0123")))
        assertEquals("h", result?.stream?.key)
        assertEquals(MatchTier.INFO_HASH, result?.tier)
    }

    @Test
    fun SM4_attributes_sizeToleranceIsTwentyFivePercent_inclusive() {
        val atLimit = matchSource(fp(), listOf(cand(key = "in", size = 2_500_000_000L)))
        val over = matchSource(fp(), listOf(cand(key = "out", size = 2_500_000_001L)))
        assertEquals("in", atLimit?.stream?.key)
        assertEquals(MatchTier.ATTRIBUTES, atLimit?.tier)
        assertNull(over)
    }

    @Test
    fun SM5_attributes_differentResolution_noMatch() {
        assertNull(matchSource(fp(), listOf(cand(res = 720))))
    }

    @Test
    fun SM6_attributes_codecMustEqual_bothNullIsEqual() {
        assertNull(matchSource(fp(), listOf(cand(codec = codecB))))
        val bothNull = matchSource(fp(codec = null), listOf(cand(key = "n", codec = null)))
        assertEquals("n", bothNull?.stream?.key)
    }

    @Test
    fun SM7_attributes_hdrSetsMustEqual() {
        assertNull(matchSource(fp(), listOf(cand(hdr = setOf(hdrA)))))
        val same = matchSource(fp(hdr = listOf(hdrA.toString())), listOf(cand(key = "h", hdr = setOf(hdrA))))
        assertEquals("h", same?.stream?.key)
    }

    @Test
    fun SM8_attributes_nullSizeOnEitherSide_noMatch() {
        assertNull(matchSource(fp(), listOf(cand(size = null))))
        assertNull(matchSource(fp(size = null), listOf(cand(size = 2_000_000_000L))))
    }

    @Test
    fun SM9_tieBreaks_closerSize_thenMoreSeeders_thenListOrder() {
        val closer =
            matchSource(
                fp(binge = "g"),
                listOf(
                    cand(key = "far", binge = "g", size = 2_100_000_000L, seeders = 50),
                    cand(key = "near", binge = "g", size = 2_050_000_000L, seeders = 1),
                ),
            )
        assertEquals("near", closer?.stream?.key)
        val seeders =
            matchSource(
                fp(binge = "g"),
                listOf(
                    cand(key = "few", binge = "g", size = 2_100_000_000L, seeders = 3),
                    cand(key = "many", binge = "g", size = 1_900_000_000L, seeders = 9),
                ),
            )
        assertEquals("many", seeders?.stream?.key)
        val order =
            matchSource(
                fp(binge = "g"),
                listOf(
                    cand(key = "first", binge = "g", seeders = 4),
                    cand(key = "second", binge = "g", seeders = 4),
                ),
            )
        assertEquals("first", order?.stream?.key)
    }

    @Test
    fun SM10_tierBeatsCloserSize_bingeGroupOverAttributes() {
        val result =
            matchSource(
                fp(binge = "g"),
                listOf(
                    cand(key = "attr", size = 2_000_000_000L),
                    cand(key = "binge", binge = "g", res = 720, size = 1_600_000_000L),
                ),
            )
        assertEquals("binge", result?.stream?.key)
        assertEquals(MatchTier.BINGE_GROUP, result?.tier)
    }

    @Test
    fun SM11_notOkAndExternalCandidates_areIgnored() {
        val result =
            matchSource(
                fp(binge = "g"),
                listOf(cand(key = "bad", binge = "g", ok = false), cand(key = "ext", binge = "g", external = true)),
            )
        assertNull(result)
    }

    @Test
    fun SM12_noCandidates_orNoAddonHashes_giveNull() {
        assertNull(matchSource(fp(), emptyList()))
        assertNull(matchSource(fp(addons = emptyList(), binge = "g"), listOf(cand(binge = "g"))))
    }

    @Test
    fun SM13_differentKind_noAttributeMatch() {
        assertNull(matchSource(fp(), listOf(cand(kind = otherKind))))
    }
}
