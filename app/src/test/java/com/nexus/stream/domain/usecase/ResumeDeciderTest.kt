package com.nexus.stream.domain.usecase

import com.nexus.stream.domain.model.ParsedStream
import com.nexus.stream.domain.model.SourceFingerprint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ResumeDeciderTest {
    private val f = fp(binge = "g")
    private val hit = cand(key = "hit", binge = "g")
    private val miss = cand(key = "miss", binge = "other", res = 720)

    @Suppress("LongParameterList")
    private fun decide(
        autoResume: Boolean = true,
        fingerprint: SourceFingerprint? = f,
        streams: List<ParsedStream> = emptyList(),
        addonAnswered: Boolean = false,
        allDone: Boolean = false,
        waitExpired: Boolean = false,
        userPicked: Boolean = false,
        handled: Boolean = false,
    ) = decideResume(autoResume, fingerprint, streams, addonAnswered, allDone, waitExpired, userPicked, handled)

    @Test
    fun DC1_noAutoResume_isNotApplicable() {
        assertEquals(ResumeStep.NotApplicable, decide(autoResume = false, streams = listOf(hit)))
    }

    @Test
    fun DC2_noFingerprint_isNotApplicable() {
        assertEquals(ResumeStep.NotApplicable, decide(fingerprint = null, streams = listOf(hit), allDone = true))
    }

    @Test
    fun DC3_matchPresent_startsAtOnce_evenBeforeTheAddonIsDone() {
        assertEquals(ResumeStep.Start(hit), decide(streams = listOf(miss, hit)))
    }

    @Test
    fun DC4_noMatch_nothingAnsweredYet_waits() {
        assertEquals(ResumeStep.Waiting, decide(streams = listOf(miss)))
        assertEquals(ResumeStep.Waiting, decide(streams = emptyList()))
    }

    @Test
    fun DC5_noMatch_addonAnswered_isNotFound() {
        assertEquals(ResumeStep.NotFound, decide(streams = listOf(miss), addonAnswered = true))
    }

    @Test
    fun DC6_noMatch_allDone_isNotFound() {
        assertEquals(ResumeStep.NotFound, decide(streams = listOf(miss), allDone = true))
    }

    @Test
    fun DC7_noMatch_waitExpired_isNotFound() {
        assertEquals(ResumeStep.NotFound, decide(streams = emptyList(), waitExpired = true))
    }

    @Test
    fun DC8_userPicked_isNotApplicable_evenWithAMatch() {
        assertEquals(ResumeStep.NotApplicable, decide(streams = listOf(hit), userPicked = true))
    }

    @Test
    fun DC9_alreadyHandled_isNotApplicable() {
        assertEquals(ResumeStep.NotApplicable, decide(streams = listOf(hit), handled = true))
    }

    @Test
    fun DC10_matchArrivesLater_firstWaits_thenStarts() {
        assertEquals(ResumeStep.Waiting, decide(streams = listOf(miss)))
        assertEquals(ResumeStep.Start(hit), decide(streams = listOf(miss, hit)))
    }

    @Test
    fun DC11_lastUsedKey_bestMatchOnly_nullWithoutFingerprintOrMatch() {
        assertEquals("hit", lastUsedKey(f, listOf(miss, hit)))
        assertNull(lastUsedKey(null, listOf(hit)))
        assertNull(lastUsedKey(f, emptyList()))
        assertNull(lastUsedKey(f, listOf(miss)))
    }

    @Test
    fun DC12_fingerprintWithoutAddonHashes_neverStarts_endsNotFound() {
        val noAddons = fp(addons = emptyList(), binge = "g")
        assertEquals(ResumeStep.NotFound, decide(fingerprint = noAddons, streams = listOf(hit), allDone = true))
    }
}
