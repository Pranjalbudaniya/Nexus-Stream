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

class StreamMatcherTest {
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
